package kreyj.konfplan.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@Tag("architecture")
@AnalyzeClasses(
        packages = "kreyj.konfplan",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureRulesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SCHICHTENARCHITEKTUR – Abhängigkeiten nur nach unten
    //    Paketstruktur-Konvention:
    //      presentation  → kreyj.konfplan.presentation
    //      application   → kreyj.konfplan.application
    //      domain        → kreyj.konfplan.domain
    //      infrastructure→ kreyj.konfplan.infrastructure
    //      persistence   → kreyj.konfplan.persistence
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule LAYERED_ARCHITECTURE =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Presentation").definedBy("kreyj.konfplan.presentation..")
                    .layer("Application").definedBy("kreyj.konfplan.application..")
                    .layer("Domain").definedBy("kreyj.konfplan.domain..")
                    .layer("Infrastructure").definedBy("kreyj.konfplan.infrastructure..")
                    .layer("Persistence").definedBy("kreyj.konfplan.persistence..")

                    .whereLayer("Presentation").mayOnlyAccessLayers("Application", "Domain")
                    .whereLayer("Application").mayOnlyAccessLayers("Domain")
                    .whereLayer("Domain").mayNotAccessAnyLayer()
                    .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain")
                    .whereLayer("Persistence").mayOnlyAccessLayers("Domain");

    // ─────────────────────────────────────────────────────────────────────────
    // 2. DOMAIN-ISOLATION – Domain darf kein CDI, JAX-RS oder Quarkus kennen
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule DOMAIN_FREE_OF_CDI_AND_FRAMEWORKS =
            noClasses()
                    .that().resideInAPackage("kreyj.konfplan.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "jakarta.enterprise.context..",   // CDI Scopes (@ApplicationScoped etc.)
                            "jakarta.inject..",               // @Inject, @Named
                            "jakarta.ws.rs..",                // JAX-RS (@Path etc.)
                            "jakarta.persistence..",          // JPA / Hibernate
                            "io.quarkus..",                   // Quarkus-spezifische APIs
                            "io.smallrye..",                  // SmallRye (MicroProfile Impl.)
                            "org.eclipse.microprofile.."      // MicroProfile APIs
                    )
                    .because("Die Domain-Schicht muss framework-agnostisch bleiben (Clean / Hexagonal Architecture).");

    @ArchTest
    static final ArchRule DOMAIN_FREE_OF_OTHER_LAYERS =
            noClasses()
                    .that().resideInAPackage("kreyj.konfplan.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "kreyj.konfplan.infrastructure..",
                            "kreyj.konfplan.persistence..",
                            "kreyj.konfplan.presentation.."
                    )
                    .because("Die Domain darf keine Abhängigkeiten auf andere Schichten haben.");

    // ─────────────────────────────────────────────────────────────────────────
    // 3. PRESENTATION – JAX-RS Resource-Klassen
    //    In Quarkus sind REST-Endpunkte @Path-annotierte Klassen (JAX-RS Resources)
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule JAX_RS_RESOURCES_IN_PRESENTATION =
            classes()
                    .that().areAnnotatedWith("jakarta.ws.rs.Path")
                    .should().resideInAPackage("kreyj.konfplan.presentation..")
                    .because("JAX-RS Resource-Klassen (@Path) gehören ausschließlich in die Presentation-Schicht.");

    @ArchTest
    static final ArchRule RESOURCES_NAMING_CONVENTION =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.presentation..")
                    .and().areAnnotatedWith("jakarta.ws.rs.Path")
                    .should().haveSimpleNameEndingWith("Resource")
                    .because("JAX-RS Ressourcen sollen auf 'Resource' enden (z. B. OrderResource).");

    @ArchTest
    static final ArchRule PRESENTATION_NO_PERSISTENCE_ACCESS =
            noClasses()
                    .that().resideInAPackage("kreyj.konfplan.presentation..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("kreyj.konfplan.persistence..")
                    .because("Resource-Klassen dürfen nicht direkt auf Repositories oder Panache-Entities zugreifen.");

    @ArchTest
    static final ArchRule PRESENTATION_NO_DOMAIN_MODEL_ACCESS =
            noClasses()
                    .that().resideInAPackage("kreyj.konfplan.presentation..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("kreyj.konfplan.domain.model..")
                    .because("Resource-Klassen kommunizieren über DTOs/Records, nicht über Domain-Entities.");

    // ─────────────────────────────────────────────────────────────────────────
    // 4. APPLICATION SERVICES – CDI-Beans in der Application-Schicht
    //    Quarkus: @ApplicationScoped statt @Service
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule APPLICATION_SCOPED_IN_CORRECT_LAYERS =
            classes()
                    .that().areAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
                    .should().resideInAnyPackage(
                            "kreyj.konfplan.application..",
                            "kreyj.konfplan.infrastructure..",
                            "kreyj.konfplan.persistence.."
                    )
                    .because("@ApplicationScoped-Beans gehören in Application-, Infrastructure- oder Persistence-Schicht, nicht in Domain oder Presentation.");

    @ArchTest
    static final ArchRule APPLICATION_SERVICES_NAMING =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.application..")
                    .and().areAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
                    .should().haveSimpleNameEndingWith("Service")
                    .because("Application Services müssen auf 'Service' enden.");

    // ─────────────────────────────────────────────────────────────────────────
    // 5. PERSISTENCE – Panache Repositories und JPA Entities
    //    Quarkus bietet zwei Patterns: Active Record (PanacheEntity)
    //    und Repository (PanacheRepository / PanacheRepositoryBase)
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule PANACHE_REPOSITORIES_IN_PERSISTENCE =
            classes()
                    .that().implement("io.quarkus.hibernate.orm.panache.PanacheRepository")
                    .or().implement("io.quarkus.hibernate.orm.panache.PanacheRepositoryBase")
                    .should().resideInAPackage("kreyj.konfplan.persistence..")
                    .because("Panache Repositories gehören ausschließlich in die Persistence-Schicht.");

    @ArchTest
    static final ArchRule PANACHE_ENTITIES_IN_PERSISTENCE =
            classes()
                    .that().areAssignableTo("io.quarkus.hibernate.orm.panache.PanacheEntity")
                    .or().areAssignableTo("io.quarkus.hibernate.orm.panache.PanacheEntityBase")
                    .should().resideInAPackage("kreyj.konfplan.persistence..")
                    .because("Panache Active-Record-Entities (technische DB-Repräsentation) gehören in die Persistence-Schicht.");

    @ArchTest
    static final ArchRule JPA_ENTITIES_IN_PERSISTENCE =
            classes()
                    .that().areAnnotatedWith("jakarta.persistence.Entity")
                    .should().resideInAPackage("kreyj.konfplan.persistence..")
                    .because("JPA @Entity-Klassen sind technische Persistence-Artefakte und gehören nicht ins Domain-Modell.");

    @ArchTest
    static final ArchRule REPOSITORIES_NAMING_CONVENTION =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.persistence..")
                    .and().implement("io.quarkus.hibernate.orm.panache.PanacheRepository")
                    .should().haveSimpleNameEndingWith("Repository")
                    .orShould().haveSimpleNameEndingWith("Adapter")
                    .because("Persistence-Klassen müssen auf 'Repository' oder 'Adapter' enden.");

    // ─────────────────────────────────────────────────────────────────────────
    // 6. DOMAIN-PORTS – Hexagonal Architecture
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule DOMAIN_PORTS_ARE_INTERFACES =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.domain.port..")
                    .should().beInterfaces()
                    .because("Domain Ports (Eingangs- und Ausgangsports der Hexagonal Architecture) müssen Interfaces sein.");

    @ArchTest
    static final ArchRule INBOUND_PORTS_NAMING =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.domain.port.in..")
                    .should().haveSimpleNameEndingWith("UseCase")
                    .because("Inbound-Ports repräsentieren Use Cases und sollen auf 'UseCase' enden.");

    @ArchTest
    static final ArchRule OUTBOUND_PORTS_NAMING =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.domain.port.out..")
                    .should().haveSimpleNameEndingWith("Port")
                    .because("Outbound-Ports sollen auf 'Port' enden (z. B. LoadOrderPort, SaveOrderPort).");

    // ─────────────────────────────────────────────────────────────────────────
    // 7. TRANSAKTIONEN – jakarta.transaction.Transactional nur in Application
    //    Quarkus nutzt jakarta.transaction.Transactional (nicht Spring!)
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule TRANSACTIONAL_ONLY_IN_APPLICATION =
            noClasses()
                    .that().resideInAnyPackage(
                            "kreyj.konfplan.domain..",
                            "kreyj.konfplan.presentation.."
                    )
                    .should().beAnnotatedWith("jakarta.transaction.Transactional")
                    .because("Transaktionssteuerung gehört in die Application-Schicht. " +
                            "In Quarkus: jakarta.transaction.Transactional, nicht io.quarkus.narayana.");

    // ─────────────────────────────────────────────────────────────────────────
    // 8. INJECTION – Kein Field-Injection mit @Inject
    //    In Quarkus ist @Inject auf Fields technisch möglich, aber schlechte Praxis
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION =
            noFields()
                    .that().areDeclaredInClassesThat()
                    .resideInAPackage("kreyj.konfplan..")
                    .should().beAnnotatedWith("jakarta.inject.Inject")
                    .because("Field-Injection (@Inject auf Felder) erschwert Unit-Tests. " +
                            "Stattdessen Constructor-Injection verwenden. " +
                            "In Quarkus generiert @Inject auf dem Konstruktor CDI-konforme Beans.");

    // ─────────────────────────────────────────────────────────────────────────
    // 9. REST-CLIENT – MicroProfile RestClient nur in Infrastructure
    //    @RegisterRestClient-Interfaces kapseln externe HTTP-Aufrufe
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule REST_CLIENTS_IN_INFRASTRUCTURE =
            classes()
                    .that().areAnnotatedWith("org.eclipse.microprofile.rest.client.inject.RegisterRestClient")
                    .should().resideInAPackage("kreyj.konfplan.infrastructure..")
                    .because("MicroProfile Rest-Clients (@RegisterRestClient) kapseln externe HTTP-Abhängigkeiten " +
                            "und gehören in die Infrastructure-Schicht.");

    // ─────────────────────────────────────────────────────────────────────────
    // 10. KONFIGURATION – @ConfigMapping nur in Infrastructure
    //     Quarkus: @ConfigMapping statt @Configuration
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule CONFIG_MAPPING_IN_INFRASTRUCTURE =
            classes()
                    .that().areAnnotatedWith("io.smallrye.config.ConfigMapping")
                    .should().resideInAPackage("kreyj.konfplan.infrastructure.config..")
                    .because("Konfigurationsklassen (@ConfigMapping) gehören in das Config-Subpaket der Infrastructure-Schicht.");

    // ─────────────────────────────────────────────────────────────────────────
    // 11. EVENTS – CDI Events / Domain Events
    //     Domain Events sind POJOs in der Domain.
    //     CDI Event-Firing (@Observes) ist Infrastrukturdetail.
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule DOMAIN_EVENTS_ARE_POJOS =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.domain.event..")
                    .should().notBeAnnotatedWith("jakarta.enterprise.context.ApplicationScoped")
                    .andShould().notBeAnnotatedWith("jakarta.enterprise.context.RequestScoped")
                    .because("Domain Events sind einfache Datentransfer-Objekte (Records oder finale Klassen) " +
                            "ohne CDI-Scope-Annotation.");

    @ArchTest
    static final ArchRule CDI_OBSERVERS_NOT_IN_DOMAIN =
            noClasses()
                    .that().resideInAPackage("kreyj.konfplan.domain..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("jakarta.enterprise.event.Observes")
                    .because("CDI @Observes ist ein Framework-Konstrukt und gehört nicht in die Domain-Schicht.");

    // ─────────────────────────────────────────────────────────────────────────
    // 12. EXCEPTIONS – Domain Exceptions als RuntimeExceptions in der Domain
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule DOMAIN_EXCEPTIONS_IN_DOMAIN =
            classes()
                    .that().haveSimpleNameEndingWith("DomainException")
                    .or().haveSimpleNameEndingWith("BusinessException")
                    .should().resideInAPackage("kreyj.konfplan.domain.exception..")
                    .because("Domain- und Business-Exceptions gehören in die Domain-Schicht.");

    @ArchTest
    static final ArchRule DOMAIN_EXCEPTIONS_EXTEND_RUNTIME =
            classes()
                    .that().resideInAPackage("kreyj.konfplan.domain.exception..")
                    .should().beAssignableTo(RuntimeException.class)
                    .because("Domain-Exceptions sollten ungeprüfte Exceptions sein, " +
                            "um Checked-Exception-Leakage in die Domain-API zu vermeiden.");

    // ─────────────────────────────────────────────────────────────────────────
    // 13. ZYKLISCHE ABHÄNGIGKEITEN – Verboten auf jeder Ebene
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule NO_CYCLES_IN_APPLICATION =
            slices()
                    .matching("kreyj.konfplan.application.(*)..")
                    .should().beFreeOfCycles()
                    .because("Zyklische Abhängigkeiten zwischen Application-Subpaketen sind verboten.");

    @ArchTest
    static final ArchRule NO_CYCLES_IN_DOMAIN =
            slices()
                    .matching("kreyj.konfplan.domain.(*)..")
                    .should().beFreeOfCycles()
                    .because("Zyklische Abhängigkeiten im Domain-Modell sind ein Designproblem.");

    @ArchTest
    static final ArchRule NO_CYCLES_GLOBALLY =
            slices()
                    .matching("kreyj.konfplan.(*)..")
                    .should().beFreeOfCycles()
                    .because("Globale Zyklen zwischen Schichten sind grundsätzlich verboten.");

    // ─────────────────────────────────────────────────────────────────────────
    // 14. INTERFACES – Keine 'I'-Präfix-Konvention (Anti-Pattern)
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule NO_INTERFACE_I_PREFIX =
            noClasses()
                    .that().areInterfaces()
                    .should().haveSimpleNameStartingWith("I")
                    .because("Das 'I'-Präfix für Interfaces ist ein Anti-Pattern in Java " +
                            "(z. B. IOrderService → OrderService).");

    // ─────────────────────────────────────────────────────────────────────────
    // 15. UTILITY-KLASSEN – Müssen final sein
    // ─────────────────────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule UTILITY_CLASSES_ARE_FINAL =
            classes()
                    .that().haveSimpleNameEndingWith("Util")
                    .or().haveSimpleNameEndingWith("Utils")
                    .or().haveSimpleNameEndingWith("Helper")
                    .should().haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                    .because("Utility-Klassen sollten final sein und nicht erweitert werden können.");

    // ─────────────────────────────────────────────────────────────────────────
    // BONUS: Programmatische Prüfung (ohne JUnit-Runner, z. B. in CI-Pipeline)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void verifyAllRulesProgrammatically() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("kreyj.konfplan");

        LAYERED_ARCHITECTURE.check(classes);
        DOMAIN_FREE_OF_CDI_AND_FRAMEWORKS.check(classes);
        DOMAIN_FREE_OF_OTHER_LAYERS.check(classes);

        JAX_RS_RESOURCES_IN_PRESENTATION.check(classes);
        RESOURCES_NAMING_CONVENTION.check(classes);
        PRESENTATION_NO_PERSISTENCE_ACCESS.check(classes);

        PANACHE_REPOSITORIES_IN_PERSISTENCE.check(classes);
        JPA_ENTITIES_IN_PERSISTENCE.check(classes);
        DOMAIN_PORTS_ARE_INTERFACES.check(classes);

        TRANSACTIONAL_ONLY_IN_APPLICATION.check(classes);
        NO_FIELD_INJECTION.check(classes);
        REST_CLIENTS_IN_INFRASTRUCTURE.check(classes);

        DOMAIN_EXCEPTIONS_EXTEND_RUNTIME.check(classes);
        NO_CYCLES_GLOBALLY.check(classes);
        NO_INTERFACE_I_PREFIX.check(classes);
    }
}
