package kreyj.konfplan.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;


@AnalyzeClasses(packages = "kreyj.konfplan.persistence")
public class JpaConventionRulesTest {

    // =========================================================================
    // REGEL-GRUPPE 1: Allgemeine Entity-Struktur
    // =========================================================================

    /**
     * Jede @Entity-Klasse muss einen parameterlosen (no-arg) Konstruktor haben.
     * JPA benötigt ihn zur Proxy-Erzeugung.
     */
    @ArchTest
    static final ArchRule entity_must_have_no_arg_constructor = ArchRuleDefinition
            .classes()
            .that().areAnnotatedWith(Entity.class)
            .should(new ArchCondition<>("einen parameterlosen Konstruktor besitzen") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean hasNoArg = clazz.getConstructors().stream()
                            .anyMatch(c -> c.getRawParameterTypes().isEmpty());
                    if (!hasNoArg) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " hat keinen No-Arg-Konstruktor"));
                    }
                }
            });

    /**
     * Jede @Entity muss eine mit @Id annotierte Methode oder ein solches Feld haben.
     */
    @ArchTest
    static final ArchRule entity_must_have_id = ArchRuleDefinition
            .classes()
            .that().areAnnotatedWith(Entity.class)
            .should(new ArchCondition<>("ein @Id-Feld oder eine @Id-Methode besitzen") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean hasId = clazz.getAllFields().stream()
                            .anyMatch(f -> f.isAnnotatedWith(Id.class));
                    if (!hasId) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " hat kein @Id-Feld"));
                    }
                }
            });

    /**
     * Entities dürfen nicht final sein – sonst kann kein CGLIB-Proxy erzeugt werden.
     */
    @ArchTest
    static final ArchRule entity_must_not_be_final = ArchRuleDefinition
            .classes()
            .that().areAnnotatedWith(Entity.class)
            .should().notBeAssignableTo(String.class)   // Platzhalter; echter Check:
            .andShould(new ArchCondition<>("nicht final sein") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    if (Modifier.isFinal(clazz.reflect().getModifiers())) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " ist final"));
                    }
                }
            });

    /**
     * Jede @Entity muss equals() UND hashCode() überschreiben.
     */
    @ArchTest
    static final ArchRule entity_must_override_equals_and_hashCode = ArchRuleDefinition
            .classes()
            .that().areAnnotatedWith(Entity.class)
            .or().areAnnotatedWith(MappedSuperclass.class)
            .and().haveSimpleName("IdEntity")
            .should(new ArchCondition<>("equals() und hashCode() überschreiben") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean hasEquals = clazz.getMethods().stream()
                            .anyMatch(m -> m.getName().equals("equals")
                                    && m.getRawParameterTypes().size() == 1);
                    boolean hasHashCode = clazz.getMethods().stream()
                            .anyMatch(m -> m.getName().equals("hashCode")
                                    && m.getRawParameterTypes().isEmpty());
                    if (!hasEquals || !hasHashCode) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " überschreibt equals/hashCode nicht"));
                    }
                }
            });

    // =========================================================================
    // REGEL-GRUPPE 2: Getter / Setter
    // =========================================================================

    /**
     * Das @Id-Feld darf keinen public Setter haben (JPA setzt die ID intern).
     */
    @ArchTest
    static final ArchRule id_field_must_not_have_public_setter = ArchRuleDefinition
            .classes()
            .that().areAnnotatedWith(Entity.class)
            .should(new ArchCondition<>("keinen public Setter für das @Id-Feld besitzen") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    clazz.getAllFields().stream()
                            .filter(f -> f.isAnnotatedWith(Id.class))
                            .forEach(idField -> {
                                String setterName = "set"
                                        + Character.toUpperCase(idField.getName().charAt(0))
                                        + idField.getName().substring(1);
                                boolean hasPublicSetter = clazz.getMethods().stream()
                                        .anyMatch(m -> m.getName().equals(setterName)
                                                && m.getModifiers().contains(JavaModifier.PUBLIC));
                                if (hasPublicSetter) {
                                    events.add(SimpleConditionEvent.violated(clazz,
                                            clazz.getName() + " hat einen public Setter für @Id"));
                                }
                            });
                }
            });

    /**
     * Collection-Felder (@OneToMany / @ManyToMany) müssen mit einem leeren
     * Objekt (new ArrayList<> / new HashSet<>) initialisiert werden,
     * erkennbar daran, dass kein Feld dieser Typen null-initialisierbar ist
     * (ArchUnit prüft hier die Annotation, die grobe Regel reicht für CI).
     */
    @ArchTest
    static final ArchRule collection_relations_must_use_concrete_type = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToMany.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should().haveRawType(List.class)
            .orShould().haveRawType(Set.class)
            .because("Collections müssen als List oder Set deklariert sein, nicht als Collection/Iterable");

    // =========================================================================
    // REGEL-GRUPPE 3: Fetch-Typen
    // =========================================================================

    /**
     * Relationen @OneToMany und @ManyToMany dürfen NICHT explizit auf EAGER gesetzt werden.
     * LAZY ist hier JPA-Standard; EAGER würde N+1-Probleme verursachen.
     */
    @ArchTest
    static final ArchRule to_many_must_not_be_eager = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToMany.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("nicht EAGER sein") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    FetchType fetch = getFetchType(field);
                    if (fetch == FetchType.EAGER) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getOwner().getName() + "." + field.getName()
                                        + " ist EAGER – bitte LAZY verwenden"));
                    }
                }
            });

    /**
     * Relationen @ManyToOne sollte LAZY sein (JPA-Standard wäre EAGER, was N+1 begünstigt).
     */
    @ArchTest
    static final ArchRule many_to_one_should_be_lazy = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(ManyToOne.class)
            .should(new ArchCondition<>("LAZY fetch verwenden") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    FetchType fetch = getFetchType(field);
                    if (fetch == FetchType.EAGER) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getOwner().getName() + "." + field.getName()
                                        + " ist EAGER – besser LAZY(fetch = FetchType.LAZY) setzen"));
                    }
                }
            });

    // =========================================================================
    // REGEL-GRUPPE 4: Cascade-Regeln
    // =========================================================================

    /**
     * Relationen @ManyToMany darf NICHT CascadeType.REMOVE oder CascadeType.ALL enthalten –
     * sonst werden beim Entfernen einer Assoziation Entitäten ungewollt gelöscht.
     */
    @ArchTest
    static final ArchRule many_to_many_must_not_cascade_remove = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("kein CascadeType.REMOVE / ALL enthalten") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    ManyToMany ann = field.reflect().getAnnotation(ManyToMany.class);
                    if (ann == null) {
                        return;
                    }
                    for (CascadeType ct : ann.cascade()) {
                        if (ct == CascadeType.REMOVE || ct == CascadeType.ALL) {
                            events.add(SimpleConditionEvent.violated(field,
                                    field.getOwner().getName() + "." + field.getName()
                                            + " hat CascadeType." + ct + " in @ManyToMany"));
                        }
                    }
                }
            });

    /**
     * Die inverse Seite einer OneToOne oder ManyToMany Relationen (mappedBy != "") darf kein cascade definieren.
     */
    @ArchTest
    static final ArchRule mapped_side_should_not_cascade = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToOne.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("auf der inversen Seite kein cascade definieren") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (isOwnerSide(field)) {
                        return; // owning side → OK
                    }

                    CascadeType[] cascades = getCascades(field);
                    if (cascades != null && cascades.length > 0) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getOwner().getName() + "." + field.getName()
                                        + " ist die 'mapped' Seite und sollte keine CascadeType definieren"));
                    }
                }
            });

    /**
     * Die inverse Seite einer OneToMany Relation (mappedBy != "") sollte CascadeType.ALL definieren.
     */
    @ArchTest
    static final ArchRule mapped_side_of_OneToMany_should_cascade_ALL = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToMany.class)
            .should(new ArchCondition<>("auf der inversen Seite CascadeType.ALL definieren") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (isOwnerSide(field)) {
                        return; // owning side → OK
                    }

                    CascadeType[] cascades = getCascades(field);
                    if (cascades == null || cascades.length == 0 ||
                            !cascades[0].equals(CascadeType.ALL)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getOwner().getName() + "." + field.getName()
                                        + " ist die 'mapped' Seite, definiert aber nicht: cascade = CascadeType.ALL"));
                    }
                }
            });
    /**
     * Die inverse Seite einer OneToMany Relation (mappedBy != "") sollte orphanRemoval definieren.
     */
    @ArchTest
    static final ArchRule mapped_side_must_orphanRemovel = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToMany.class)
            .should(new ArchCondition<>("auf der inversen Seite orphanRemoval definieren") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (isOwnerSide(field)) {
                        return; // owning side → OK
                    }

                    OneToMany ann = field.reflect().getAnnotation(OneToMany.class);
                    if (ann == null || !ann.orphanRemoval()) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getOwner().getName() + "." + field.getName()
                                        + " ist die 'mapped' Seite, definiert aber nicht: orphanRemoval=true"));
                    }
                }
            });

    // =========================================================================
    // REGEL-GRUPPE 5: @JoinColumn / @JoinTable auf der richtigen Seite
    // =========================================================================

    /**
     * Felder mit mappedBy (inverse side) dürfen kein @JoinColumn besitzen.
     */
    @ArchTest
    static final ArchRule mapped_side_must_not_have_join_column = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToOne.class)
            .or().areAnnotatedWith(OneToMany.class)
            .should(new ArchCondition<>("auf der 'mapped' Seite kein @JoinColumn haben") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (isMappedSide(field)
                            && field.isAnnotatedWith(JoinColumn.class)) {
                        events.add(SimpleConditionEvent.violated(field,
                                field.getOwner().getName() + "." + field.getName()
                                        + " ist 'mapped' Seite, hat aber @JoinColumn"));
                    }
                }
            });

    // =========================================================================
    // REGEL-GRUPPE 6: toString / Lazy-Fallen
    // =========================================================================

    /**
     * Alle @Entity-Klassen dürfen toString() nicht überschreiben, ohne dass wir
     * sicherstellen können, dass keine Lazy-Collections aufgerufen werden.
     * Einfachste Regel: toString() in Entities gänzlich verbieten
     * (oder nur auf primitiven Feldern erlauben – hier als WARNING-Regel).
     */
    @ArchTest
    static final ArchRule entity_toString_warning = ArchRuleDefinition
            .noClasses()
            .that().areAnnotatedWith(Entity.class)
            .and().haveSimpleNameNotEndingWith("Verfuegbarkeit")
            .should(new ArchCondition<>("toString() ohne Vorsicht überschreiben") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean hasToString = clazz.getMethods().stream()
                            .anyMatch(m -> m.getName().equals("toString")
                                    && m.getRawParameterTypes().isEmpty());
                    if (hasToString) {
                        // Als WARNING eingestuft, nicht als Fehler
                        events.add(SimpleConditionEvent.satisfied(clazz,
                                clazz.getName() + " überschreibt toString() – sicherstellen, "
                                        + "dass keine Lazy-Collections referenziert werden"));
                    }
                }
            })
            .because("toString() in Entities kann unbeabsichtigt Lazy-Collections laden");


    // =========================================================================
    // REGEL-GRUPPE 7: Prüfen auf Methoden add-/remove-/"internal" Methoden für mehrwertige Beziehungen
    // =========================================================================


    @ArchTest
    static final ArchRule multivalue_field_setter_warning = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToMany.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("public collection setter für multi-value relation") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    String setterName = "set"
                            + Character.toUpperCase(field.getName().charAt(0))
                            + field.getName().substring(1);
                    JavaClass clazz = field.getRawType();
                    boolean hasPublicSetter = clazz.getMethods().stream()
                            .anyMatch(m -> m.getName().equals(setterName)
                                    && m.getModifiers().contains(JavaModifier.PUBLIC));
                    if (hasPublicSetter) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " hat einen public Setter für " + field.getName()));
                    }
                }
            })
            .because("Austausch von Kollektionen vermeiden");

    @ArchTest
    static final ArchRule multivalue_relation_requires_add_remove_on_owner_side = ArchRuleDefinition
            .fields()
            .that().areAnnotatedWith(OneToMany.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("public addX/removeX Methode auf 'Owner' Seite einer mehrwertigen Relation") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    if (isMappedSide(field)) {
                        return;
                    }

                    JavaType fieldType = field.getType();
                    if (!(fieldType instanceof JavaParameterizedType paramType)) {
                        return;
                    }

                    List<JavaType> typeParameters = paramType.getActualTypeArguments();
                    if (typeParameters.isEmpty()) {
                        return; // keine Element-Type ?
                    }
                    JavaType argType = typeParameters.getFirst();
                    JavaClass clazz = field.getOwner();

                    boolean hasPublicAdder = clazz.getMethods().stream()
                            .anyMatch(m -> m.getName().startsWith("add")
                                    && m.getRawParameterTypes().size() == 1
                                    && m.getRawParameterTypes().getFirst().equals(argType)
                                    && m.getModifiers().contains(JavaModifier.PUBLIC));
                    if (!hasPublicAdder) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " hat keine public add...("
                                        + argType.toErasure().getSimpleName() + ") "
                                        + " Methode für 'mapped' Field/Relation " + field.getName()));
                    }

                    boolean hasPublicRemover = clazz.getMethods().stream()
                            .anyMatch(m -> m.getName().startsWith("remove")
                                    && m.getRawParameterTypes().size() == 1
                                    && m.getRawParameterTypes().getFirst().equals(argType)
                                    && m.getModifiers().contains(JavaModifier.PUBLIC));
                    if (!hasPublicRemover) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " hat keine public remove...("
                                        + argType.toErasure().getSimpleName() + ") "
                                        + " Methode für 'mapped' Field/Relation " + field.getName()));
                    }
                }
            })
            .because("nur einzelne Elemente zufügen/entfernen");


    @ArchTest
    static final ArchRule no_public_setter_for_many_to_many_collections = ArchRuleDefinition
            .noMethods()
            .that().haveNameStartingWith("set")
            .and().arePublic()
            .and().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .should(new ArchCondition<>(
                    "keinen ManyToMany Collection Parameter haben") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    method.getParameters().forEach(param -> {
                        JavaClass rawType = param.getType().toErasure();
                        if (!rawType.getName().equals("java.util.Set")
                                && !rawType.getName().equals("java.util.List")
                                && !rawType.getName().equals("java.util.Collection")) {
                            return;
                        }

                        // Prüfen ob das zugehörige Feld @ManyToMany hat
                        String fieldName = method.getName()
                                .replaceFirst("set", "")
                                .substring(0, 1).toLowerCase()
                                + method.getName().replaceFirst("set", "").substring(1);

                        boolean isManyToMany = method.getOwner()
                                .getAllFields().stream()
                                .anyMatch(f -> f.getName().equals(fieldName)
                                        && f.isAnnotatedWith(ManyToMany.class));

                        if (isManyToMany) {
                            events.add(SimpleConditionEvent.violated(method,
                                    "Methode %s.%s ist ein public Setter für eine @ManyToMany Collection"
                                            .formatted(
                                                    method.getOwner().getName(),
                                                    method.getName()
                                            )
                            ));
                        }
                    });
                }
            })
            .because("ManyToMany Collections dürfen nicht über public Setter ersetzt werden " +
                    "– nur addX()/removeX() auf Owner Seite pflegen beide Seiten korrekt");

    // =========================================================================
    // Hilfs-Methoden
    // =========================================================================

    private static FetchType getFetchType(JavaField field) {
        if (field.isAnnotatedWith(OneToMany.class)) {
            return field.reflect().getAnnotation(OneToMany.class).fetch();
        }
        if (field.isAnnotatedWith(ManyToMany.class)) {
            return field.reflect().getAnnotation(ManyToMany.class).fetch();
        }
        if (field.isAnnotatedWith(ManyToOne.class)) {
            return field.reflect().getAnnotation(ManyToOne.class).fetch();
        }
        if (field.isAnnotatedWith(OneToOne.class)) {
            return field.reflect().getAnnotation(OneToOne.class).fetch();
        }
        return FetchType.LAZY;
    }

    private static String getMappedBy(JavaField field) {
        if (field.isAnnotatedWith(OneToMany.class)) {
            return field.reflect().getAnnotation(OneToMany.class).mappedBy();
        }
        if (field.isAnnotatedWith(OneToOne.class)) {
            return field.reflect().getAnnotation(OneToOne.class).mappedBy();
        }
        if (field.isAnnotatedWith(ManyToMany.class)) {
            return field.reflect().getAnnotation(ManyToMany.class).mappedBy();
        }
        return null;
    }

    private static boolean isOwnerSide(JavaField field) {
        String mappedBy = getMappedBy(field);
        return mappedBy == null || mappedBy.isEmpty();
    }

    private static boolean isMappedSide(JavaField field) {
        return !isOwnerSide(field);
    }

    private static CascadeType[] getCascades(JavaField field) {
        if (field.isAnnotatedWith(OneToMany.class)) {
            return field.reflect().getAnnotation(OneToMany.class).cascade();
        }
        if (field.isAnnotatedWith(OneToOne.class)) {
            return field.reflect().getAnnotation(OneToOne.class).cascade();
        }
        if (field.isAnnotatedWith(ManyToMany.class)) {
            return field.reflect().getAnnotation(ManyToMany.class).cascade();
        }
        return new CascadeType[0];
    }
}
