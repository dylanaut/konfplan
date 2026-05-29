package kreyj.konfplan.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import jakarta.persistence.CascadeType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Tag("architecture")
@AnalyzeClasses(packages = "kreyj.konfplan.persistence")
class JpaManyToManyRulesTest {

    /**
     * Regel 1: Kein CascadeType.ALL oder CascadeType.REMOVE bei ManyToMany
     */
    @ArchTest
    static final ArchRule no_remove_cascade_on_many_to_many = ArchRuleDefinition
            .noFields()
            .that().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("kein REMOVE oder ALL Cascade haben") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    ManyToMany annotation = field.getAnnotationOfType(ManyToMany.class);
                    List<CascadeType> cascades = Arrays.asList(annotation.cascade());

                    if (cascades.contains(CascadeType.REMOVE)
                            || cascades.contains(CascadeType.ALL)) {
                        events.add(SimpleConditionEvent.violated(field,
                                "Feld %s.%s hat @ManyToMany mit CascadeType.REMOVE oder ALL – das kann fremde Entitäten löschen!"
                                        .formatted(field.getOwner().getName(), field.getName())
                        ));
                    }
                }
            })
            .because("CascadeType.REMOVE/ALL bei ManyToMany kann Entitäten löschen die noch anderen gehören");
    /**
     * Regel 2: ManyToMany Collections müssen Set sein (nicht List)
     */
    @ArchTest
    static final ArchRule many_to_many_must_be_set = ArchRuleDefinition
            .noFields()
            .that().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("vom Typ Set sein") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    String typeName = field.getRawType().getName();
                    if (!typeName.equals("java.util.Set")) {
                        events.add(SimpleConditionEvent.violated(field,
                                "Feld %s.%s ist @ManyToMany aber kein Set – List verursacht Duplikate bei Joins!"
                                        .formatted(field.getOwner().getName(), field.getName())
                        ));
                    }
                }
            })
            .because("ManyToMany Collections sollten Set sein um Duplikate bei Joins zu vermeiden");

    /**
     * Regel 3: Inverse Seite (mappedBy) darf keine Pflegemethoden haben, die die Collection direkt modifizieren.
     */
    @ArchTest
    static final ArchRule no_modify_methods_on_mapped_M2M_side = ArchRuleDefinition.fields()
            .that(areMappedManyToMany())
            .should(notBeModifiedByMethodsInTheirOwnClass())
            .because("Die inverse Seite ('mappedBy') einer @ManyToMany-Beziehung sollte die Collection nicht selbst verwalten. " +
                    "Alle Modifikationen (add/remove) müssen über die 'owning' Seite der Beziehung erfolgen, um die Synchronisation sicherzustellen.");


    private static DescribedPredicate<JavaField> areMappedManyToMany() {
        return new DescribedPredicate<>("are the inverse side of a @ManyToMany relationship") {
            @Override
            public boolean test(JavaField field) {
                return hasMappedM2M(field);
            }
        };
    }

    private static ArchCondition<JavaField> notBeModifiedByMethodsInTheirOwnClass() {
        return new ArchCondition<>("not be modified by methods in their own class") {
            @Override
            public void check(JavaField mappedField, ConditionEvents events) {
                Set<String> modificationMethodNames = Set.of("add", "addAll", "remove", "removeAll", "clear", "retainAll");

                for (JavaFieldAccess access : mappedField.getAccessesToSelf()) {
                    // We only care about accesses from within the same class
                    if (!access.getOriginOwner().equals(mappedField.getOwner())) {
                        continue;
                    }

                    JavaCodeUnit origin = access.getOrigin();
                    int accessLineNumber = access.getLineNumber();

                    // Check for modification calls on the same line as the field access
                    for (JavaMethodCall call : origin.getMethodCallsFromSelf()) {
                        if (call.getLineNumber() == accessLineNumber && modificationMethodNames.contains(call.getName())) {
                            // This heuristic is strong: a field access and a modification call on the same line
                            // strongly implies `field.method()`.
                            String message = String.format(
                                    "Methode '%s' in Klasse '%s' modifiziert die 'mappedBy' Collection '%s' direkt. " +
                                            "Die Pflege der Beziehung darf nur von der 'owning' Seite (%s) erfolgen.",
                                    origin.getName(), mappedField.getOwner().getName(), mappedField.getName(),
                                    call.getOrigin().getRawParameterTypes().getFirst().getName());
                            events.add(SimpleConditionEvent.violated(origin, message));
                        }
                    }
                }
            }
        };
    }


    private static boolean hasMappedM2M(JavaField f) {
        if (f.isAnnotatedWith(ManyToMany.class)) {
            String mappedByFieldName = f.getAnnotationOfType(ManyToMany.class).mappedBy();
            return mappedByFieldName != null && !mappedByFieldName.isEmpty();
        } else {
            return false;
        }
    }

    /**
     * Regel 4: OneToMany muss orphanRemoval=true haben
     */
    @ArchTest
    static final ArchRule one_to_many_must_have_orphan_removal = ArchRuleDefinition
            .noFields()
            .that().areAnnotatedWith(OneToMany.class)
            .should(new ArchCondition<>("orphanRemoval=true haben") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    OneToMany annotation = field.getAnnotationOfType(OneToMany.class);
                    if (!annotation.orphanRemoval()) {
                        events.add(SimpleConditionEvent.violated(field,
                                "Feld %s.%s hat @OneToMany ohne orphanRemoval=true"
                                        .formatted(field.getOwner().getName(), field.getName())
                        ));
                    }
                }
            })
            .because("OneToMany Beziehungen sollten orphanRemoval=true haben");

    /**
     * Regel 5: ManyToMany Getter müssen unmodifiable zurückgeben
     */
    @ArchTest
    static final ArchRule many_to_many_getter_must_be_unmodifiable = ArchRuleDefinition
            .methods()
            .that().haveNameStartingWith("get")
            .and().areDeclaredInClassesThat(new DescribedPredicate<JavaClass>(
                    "ein ManyToMany Feld besitzen") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getAllFields().stream()
                            .anyMatch(f -> f.isAnnotatedWith(ManyToMany.class));
                }
            })
            .should(new ArchCondition<JavaMethod>(
                    "Collections.unmodifiable* aufrufen") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean callsUnmodifiable = method.getMethodCallsFromSelf()
                            .stream()
                            .anyMatch(call ->
                                    call.getTargetOwner().getName().equals("java.util.Collections")
                                            && call.getName().startsWith("unmodifiable")
                            );

                    String fieldName = method.getName()
                            .replaceFirst("get", "")
                            .substring(0, 1).toLowerCase()
                            + method.getName().replaceFirst("get", "").substring(1);

                    boolean hasManyToMany = method.getOwner().getAllFields()
                            .stream()
                            .anyMatch(f -> f.getName().equals(fieldName)
                                    && f.isAnnotatedWith(ManyToMany.class));

                    if (hasManyToMany && !callsUnmodifiable) {
                        events.add(SimpleConditionEvent.violated(method,
                                "Getter %s.%s gibt ManyToMany Collection ohne unmodifiable-Wrapper zurück"
                                        .formatted(method.getOwner().getName(), method.getName())
                        ));
                    }
                }
            })
            .allowEmptyShould(true)
            .because("ManyToMany Getter sollen Collections.unmodifiableSet/List zurückgeben");


    @ArchTest
    static final ArchRule inverse_mapping_fields_must_be_package_private = ArchRuleDefinition
            .noFields()
            .that(new DescribedPredicate<>(
                    "eine mapped OneToMany oder ManyToMany Relation ist") {
                @Override
                public boolean test(JavaField field) {
                    if (field.isAnnotatedWith(ManyToMany.class)) {
                        String mappedBy = field.getAnnotationOfType(ManyToMany.class).mappedBy();
                        return mappedBy != null && !mappedBy.isEmpty();
                    }

                    if (field.isAnnotatedWith(OneToMany.class)) {
                        String mappedBy = field.getAnnotationOfType(OneToMany.class).mappedBy();
                        return mappedBy != null && !mappedBy.isEmpty();
                    }

                    return false;
                }
            })
            .should().bePublic()
            .orShould().bePrivate()
            .because("Inverse ManyToMany/OneToMany Felder müssen package-private oder protected sein " +
                    "– direkter Zugriff nur durch den Owner erlaubt");
}