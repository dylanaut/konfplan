package kreyj.konfplan.util;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import kreyj.konfplan.adapter.in.web.AdminResource;
import kreyj.konfplan.adapter.rest.KalenderResource;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Standalone main() statt @Test - kein Teil des normalen Build-/Testlaufs, muss bei Bedarf (z.B.
// nach dem Hinzufuegen/Entfernen eines REST-Endpunkts) manuell neu ausgefuehrt werden:
//
//   cd backend
//   ../mvnw -q test-compile -DskipTests
//   ../mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
//   java -cp "target/classes:target/test-classes:$(cat /tmp/cp.txt)" kreyj.konfplan.util.UseCaseScanner
//
// Ueberschreibt src/main/asciidoc/USE_CASES.adoc komplett - keine manuellen Ergaenzungen
// direkt in dieser Datei vornehmen, die gehen beim naechsten Lauf verloren.
class UseCaseScanner {

    // KalenderResource liegt bewusst/versehentlich in einem eigenen Package (adapter.rest statt
    // adapter.in.web wie alle anderen Resource-Klassen) - ohne diesen zweiten Eintrag fehlt sie
    // in der generierten Dokumentation komplett (live verifiziert: mit nur AdminResource.class
    // als Package-Anker tauchten ihre 3 Endpunkte im Output nicht auf).
    private static final List<String> BASE_PACKAGES = List.of(
            AdminResource.class.getPackage().getName(),
            KalenderResource.class.getPackage().getName()
    );

    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0 ? args[0] : "src/main/asciidoc/USE_CASES.adoc";

        Set<Class<?>> classes = new HashSet<>();
        for (String basePackage : BASE_PACKAGES) {
            System.out.println("Scanning package: " + basePackage);
            classes.addAll(findClasses(basePackage));
        }

        StringBuilder adoc = new StringBuilder();
        adoc.append("= Automatisch generierte UseCase Dokumentation\n");
        adoc.append(":toc: left\n:sectnums:\n\n");
        adoc.append("Per Reflection aus den `@Path`/`@GET`/.../`@RolesAllowed`-Annotationen der REST-Resource-\n");
        adoc.append("Klassen erzeugt (siehe `UseCaseScanner.java`) - jeder Endpunkt ist ein UseCase-Knoten im\n");
        adoc.append("Diagramm unten. Manuell gepflegte Querverweise, welches Benutzerhandbuch welchen UseCase\n");
        adoc.append("beschreibt, finden sich in den Benutzerhandbüchern selbst (Admin/Referent/Teilnehmer) -\n");
        adoc.append("diese Datei wird bei jedem Lauf komplett überschrieben, daher hier bewusst keine manuell\n");
        adoc.append("gepflegten Inhalte. Neu erzeugen: siehe Kommentar am Kopf von `UseCaseScanner.java`.\n\n");

        Map<String, List<String>> roleToUCs = new TreeMap<>();
        Map<String, List<EndpointInfo>> classToEndpoints = new LinkedHashMap<>();

        for (Class<?> clazz : classes.stream().sorted(Comparator.comparing(Class::getName)).toList()) {
            Path classPathAnno = clazz.getAnnotation(Path.class);
            if (null == classPathAnno) {
                continue;
            }

            String basePath = classPathAnno.value();
            RolesAllowed classRoles = clazz.getAnnotation(RolesAllowed.class);

            List<EndpointInfo> endpoints = new ArrayList<>();
            for (Method method : clazz.getDeclaredMethods()) {
                String httpMethod = getHttpMethod(method);
                if (null == httpMethod) {
                    continue;
                }

                Path methodPath = method.getAnnotation(Path.class);
                String fullPath = basePath + (methodPath != null ? methodPath.value() : "");
                RolesAllowed methodRoles = method.getAnnotation(RolesAllowed.class);

                String[] roles = methodRoles != null ? methodRoles.value() : (classRoles != null ? classRoles.value() : new String[]{"PUBLIC"});

                // Qualifiziert mit dem Klassennamen, statt nur dem Methodennamen: mehrere
                // Resource-Klassen haben gleich benannte Methoden (z.B. "getAll", "update",
                // "create" in GebaeudeResource UND VeranstaltungResource) - ohne Qualifizierung
                // wuerden diese im PlantUML-Diagramm faelschlich zu einem einzigen Knoten
                // verschmelzen (live verifiziert: unqualifiziert blieben von 110 Endpunkten nur
                // rund 90 eindeutige Knoten uebrig).
                String qualifiedId = clazz.getSimpleName() + "_" + method.getName();
                EndpointInfo info = new EndpointInfo(method.getName(), qualifiedId, httpMethod, fullPath, roles);
                endpoints.add(info);

                for (String role : roles) {
                    roleToUCs.computeIfAbsent(role, k -> new ArrayList<>()).add(qualifiedId);
                }
            }
            classToEndpoints.put(clazz.getSimpleName(), endpoints);
        }

        // Use Case Diagram
        adoc.append("== UseCase Diagramm (PlantUML)\n\n");
        adoc.append("[plantuml, usecase-gen, svg]\n----\n@startuml\nleft to right direction\n");
        for (String role : roleToUCs.keySet()) {
            adoc.append("actor \"").append(role).append("\" as ").append(role.replace("-", "_")).append("\n");
        }
        adoc.append("package \"KonfPlan API\" {\n");
        Set<String> addedUCs = new HashSet<>();
        for (List<EndpointInfo> eps : classToEndpoints.values()) {
            for (EndpointInfo ep : eps) {
                if (addedUCs.add(ep.qualifiedId)) {
                    adoc.append("  (").append(ep.name).append(") as ").append(ep.qualifiedId).append("\n");
                }
            }
        }
        adoc.append("}\n");
        for (Map.Entry<String, List<String>> entry : roleToUCs.entrySet()) {
            String roleId = entry.getKey().replace("-", "_");
            for (String uc : entry.getValue()) {
                adoc.append(roleId).append(" --> ").append(uc).append("\n");
            }
        }
        adoc.append("@enduml\n----\n\n");

        // Details
        adoc.append("== API Endpunkte & Rollen\n\n");
        for (Map.Entry<String, List<EndpointInfo>> entry : classToEndpoints.entrySet()) {
            adoc.append("=== ").append(entry.getKey()).append("\n\n");
            adoc.append("[cols=\"2,1,3,2\", options=\"header\"]\n|===\n");
            adoc.append("| Aktion | Method | Pfad | Rollen\n");
            for (EndpointInfo ep : entry.getValue()) {
                adoc.append("| ").append(ep.name).append("\n");
                adoc.append("| ").append(ep.httpMethod).append("\n");
                adoc.append("| `").append(ep.path).append("`\n");
                adoc.append("| ").append(String.join(", ", ep.roles)).append("\n");
            }
            adoc.append("|===\n\n");
        }

        File outFile = new File(outputPath);
        outFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write(adoc.toString());
        }
        System.out.println("Documentation generated at: " + outFile.getAbsolutePath());
    }

    private static String getHttpMethod(Method m) {
        if (m.isAnnotationPresent(GET.class)) {
            return "GET";
        }
        if (m.isAnnotationPresent(POST.class)) {
            return "POST";
        }
        if (m.isAnnotationPresent(PUT.class)) {
            return "PUT";
        }
        if (m.isAnnotationPresent(DELETE.class)) {
            return "DELETE";
        }
        if (m.isAnnotationPresent(PATCH.class)) {
            return "PATCH";
        }
        return null;
    }

    private static Set<Class<?>> findClasses(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        // Simple scanner for the specific project structure
        File root = new File("src/main/java/" + path);
        if (!root.exists()) {
            return Collections.emptySet();
        }

        File[] listedFiles = root.listFiles();
        return null == listedFiles ? Collections.emptySet()
                : Arrays.stream(listedFiles)
                .filter(f -> f.getName().endsWith(".java"))
                .map(f -> {
                    try {
                        return Class.forName(packageName + "." + f.getName().replace(".java", ""));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    static class EndpointInfo {
        String name, qualifiedId, httpMethod, path;
        String[] roles;

        EndpointInfo(String name, String qualifiedId, String httpMethod, String path, String[] roles) {
            this.name = name;
            this.qualifiedId = qualifiedId;
            this.httpMethod = httpMethod;
            this.path = path;
            this.roles = roles;
        }
    }
}
