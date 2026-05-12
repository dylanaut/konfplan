package kreyj.konfplan.util;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class UseCaseScanner {

    public static void main(String[] args) throws Exception {
        String basePackage = "kreyj.konfplan.resource";
        String outputPath = args.length > 0 ? args[0] : "src/main/asciidoc/VM-Anwendungsfälle.adoc";

        System.out.println("Scanning package: " + basePackage);
        List<Class<?>> classes = findClasses(basePackage);

        StringBuilder adoc = new StringBuilder();
        adoc.append("= Automatisch generierte UseCase Dokumentation\n");
        adoc.append(":toc: left\n:sectnums:\n\n");

        Map<String, List<String>> roleToUCs = new TreeMap<>();
        Map<String, List<EndpointInfo>> classToEndpoints = new LinkedHashMap<>();

        for (Class<?> clazz : classes) {
            Path classPathAnno = clazz.getAnnotation(Path.class);
            if (classPathAnno == null) {
                continue;
            }

            String basePath = classPathAnno.value();
            RolesAllowed classRoles = clazz.getAnnotation(RolesAllowed.class);

            List<EndpointInfo> endpoints = new ArrayList<>();
            for (Method method : clazz.getDeclaredMethods()) {
                String httpMethod = getHttpMethod(method);
                if (httpMethod == null) {
                    continue;
                }

                Path methodPath = method.getAnnotation(Path.class);
                String fullPath = basePath + (methodPath != null ? methodPath.value() : "");
                RolesAllowed methodRoles = method.getAnnotation(RolesAllowed.class);

                String[] roles = methodRoles != null ? methodRoles.value() : (classRoles != null ? classRoles.value() : new String[]{"PUBLIC"});

                EndpointInfo info = new EndpointInfo(method.getName(), httpMethod, fullPath, roles);
                endpoints.add(info);

                for (String role : roles) {
                    roleToUCs.computeIfAbsent(role, k -> new ArrayList<>()).add(method.getName());
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
                if (addedUCs.add(ep.name)) {
                    adoc.append("  (").append(ep.name).append(") as ").append(ep.name).append("\n");
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

    private static List<Class<?>> findClasses(String packageName) throws Exception {
        String path = packageName.replace('.', '/');
        // Simple scanner for the specific project structure
        File root = new File("src/main/java/" + path);
        if (!root.exists()) {
            return Collections.emptyList();
        }

        return Arrays.stream(root.listFiles())
                .filter(f -> f.getName().endsWith(".java"))
                .map(f -> {
                    try {
                        return Class.forName(packageName + "." + f.getName().replace(".java", ""));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    static class EndpointInfo {
        String name, httpMethod, path;
        String[] roles;

        EndpointInfo(String name, String httpMethod, String path, String[] roles) {
            this.name = name;
            this.httpMethod = httpMethod;
            this.path = path;
            this.roles = roles;
        }
    }
}
