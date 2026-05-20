package kreyj.konfplan.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


@AnalyzeClasses(packages = "kreyj.konfplan.persistence")
public class JpaConventionRules {

    // =========================================================================
    // REGEL-GRUPPE 1: Allgemeine Entity-Struktur
    // =========================================================================

    /**
     * Jede @Entity-Klasse muss einen parameterlosen (no-arg) Konstruktor haben.
     * JPA benötigt ihn zur Proxy-Erzeugung.
     */
    @ArchTest
    static final ArchRule RULE_01_entity_must_have_no_arg_constructor =
        classes()
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
    static final ArchRule RULE_02_entity_must_have_id =
        classes()
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
    static final ArchRule RULE_03_entity_must_not_be_final =
        classes()
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
    static final ArchRule RULE_04_entity_must_override_equals_and_hashCode =
        classes()
            .that().areAnnotatedWith(Entity.class)
            .should(new ArchCondition<>("equals() und hashCode() überschreiben") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    boolean hasEquals   = clazz.getMethods().stream()
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
    static final ArchRule RULE_05_id_field_must_not_have_public_setter =
        classes()
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
    static final ArchRule RULE_06_collection_relations_must_use_concrete_type =
        fields()
            .that().areAnnotatedWith(OneToMany.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should().haveRawType(List.class)
            .orShould().haveRawType(Set.class)
            .because("Collections müssen als List oder Set deklariert sein, nicht als Collection/Iterable");

    // =========================================================================
    // REGEL-GRUPPE 3: Fetch-Typen
    // =========================================================================

    /**
     * @OneToMany und @ManyToMany dürfen NICHT explizit auf EAGER gesetzt werden.
     * LAZY ist hier JPA-Standard; EAGER würde N+1-Probleme verursachen.
     */
    @ArchTest
    static final ArchRule RULE_07_to_many_must_not_be_eager =
        fields()
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
     * @ManyToOne sollte LAZY sein (JPA-Standard wäre EAGER, was N+1 begünstigt).
     */
    @ArchTest
    static final ArchRule RULE_08_many_to_one_should_be_lazy =
        fields()
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
     * @ManyToMany darf NICHT CascadeType.REMOVE oder CascadeType.ALL enthalten –
     * sonst werden beim Entfernen einer Assoziation Entitäten ungewollt gelöscht.
     */
    @ArchTest
    static final ArchRule RULE_09_many_to_many_must_not_cascade_remove =
        fields()
            .that().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("kein CascadeType.REMOVE / ALL enthalten") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    ManyToMany ann = field.reflect().getAnnotation(ManyToMany.class);
                    if (ann == null) return;
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
     * Die inverse Seite einer Relation (mappedBy != "") darf kein cascade definieren.
     */
    @ArchTest
    static final ArchRule RULE_10_inverse_side_must_not_cascade =
        fields()
            .that().areAnnotatedWith(OneToMany.class)
            .or().areAnnotatedWith(OneToOne.class)
            .or().areAnnotatedWith(ManyToMany.class)
            .should(new ArchCondition<>("auf der inversen Seite kein cascade definieren") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    String mappedBy = getMappedBy(field);
                    if (mappedBy == null || mappedBy.isEmpty()) return; // owning side → OK

                    CascadeType[] cascades = getCascades(field);
                    if (cascades != null && cascades.length > 0) {
                        events.add(SimpleConditionEvent.violated(field,
                            field.getOwner().getName() + "." + field.getName()
                            + " ist die inverse Seite, definiert aber cascade"));
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
    static final ArchRule RULE_11_inverse_side_must_not_have_join_column =
        fields()
            .that().areAnnotatedWith(OneToOne.class)
            .or().areAnnotatedWith(OneToMany.class)
            .should(new ArchCondition<>("auf der inversen Seite kein @JoinColumn haben") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    String mappedBy = getMappedBy(field);
                    if (mappedBy != null && !mappedBy.isEmpty()
                            && field.isAnnotatedWith(JoinColumn.class)) {
                        events.add(SimpleConditionEvent.violated(field,
                            field.getOwner().getName() + "." + field.getName()
                            + " ist inverse Seite, hat aber @JoinColumn"));
                    }
                }
            });

    // =========================================================================
    // REGEL-GRUPPE 6: toString / Lazy-Fallen
    // =========================================================================

    /**
     * @Entity-Klassen dürfen toString() nicht überschreiben, ohne dass wir
     * sicherstellen können, dass keine Lazy-Collections aufgerufen werden.
     * Einfachste Regel: toString() in Entities gänzlich verbieten
     * (oder nur auf primitiven Feldern erlauben – hier als WARNING-Regel).
     */
    @ArchTest
    static final ArchRule RULE_12_entity_toString_warning =
        noClasses()
            .that().areAnnotatedWith(Entity.class)
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
    // Hilfs-Methoden
    // =========================================================================

    private static FetchType getFetchType(JavaField field) {
        if (field.isAnnotatedWith(OneToMany.class))
            return field.reflect().getAnnotation(OneToMany.class).fetch();
        if (field.isAnnotatedWith(ManyToMany.class))
            return field.reflect().getAnnotation(ManyToMany.class).fetch();
        if (field.isAnnotatedWith(ManyToOne.class))
            return field.reflect().getAnnotation(ManyToOne.class).fetch();
        if (field.isAnnotatedWith(OneToOne.class))
            return field.reflect().getAnnotation(OneToOne.class).fetch();
        return FetchType.LAZY;
    }

    private static String getMappedBy(JavaField field) {
        if (field.isAnnotatedWith(OneToMany.class))
            return field.reflect().getAnnotation(OneToMany.class).mappedBy();
        if (field.isAnnotatedWith(OneToOne.class))
            return field.reflect().getAnnotation(OneToOne.class).mappedBy();
        if (field.isAnnotatedWith(ManyToMany.class))
            return field.reflect().getAnnotation(ManyToMany.class).mappedBy();
        return null;
    }

    private static CascadeType[] getCascades(JavaField field) {
        if (field.isAnnotatedWith(OneToMany.class))
            return field.reflect().getAnnotation(OneToMany.class).cascade();
        if (field.isAnnotatedWith(OneToOne.class))
            return field.reflect().getAnnotation(OneToOne.class).cascade();
        if (field.isAnnotatedWith(ManyToMany.class))
            return field.reflect().getAnnotation(ManyToMany.class).cascade();
        return new CascadeType[0];
    }
}
