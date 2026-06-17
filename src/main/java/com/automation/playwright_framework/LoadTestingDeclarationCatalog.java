package com.automation.playwright_framework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

@Component
class LoadTestingDeclarationCatalog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, DeclarationDefinition> DEFINITIONS = Map.of(
            "ipt", new DeclarationDefinition(
                    "ipt",
                    "In-Payment (IPT)",
                    "In-Payment (IPT)",
                    "/declarations/ipt",
                    "IPT",
                    WorkflowKind.IPT_STYLE,
                    List.of("Edit Declaration", "Job Info")),
            "inp", new DeclarationDefinition(
                    "inp",
                    "In-Non-Payment (INP)",
                    "In-Non-Payment (INP)",
                    "/declarations/inp",
                    "INP",
                    WorkflowKind.IPT_STYLE,
                    List.of("Edit Declaration", "Job Info")),
            "tnp", new DeclarationDefinition(
                    "tnp",
                    "Transhipment (TNP)",
                    "Transhipment (TNP)",
                    "/declarations/tnp",
                    "TNP",
                    WorkflowKind.IPT_STYLE,
                    List.of("Edit Declaration", "Job Info")),
            "out", new DeclarationDefinition(
                    "out",
                    "Out Payment (OUT)",
                    "Out Payment (OUT)",
                    "/declarations/out",
                    "OUT",
                    WorkflowKind.OUT,
                    List.of("Edit Declaration", "Job Info")),
            "coo", new DeclarationDefinition(
                    "coo",
                    "Certificate of Origin (COO)",
                    "Certificate of Origin (COO)",
                    "/declarations/coo",
                    "COO",
                    WorkflowKind.COO,
                    List.of("Edit Declaration", "Job Info", "Header & Certificate")));

    DeclarationDefinition resolveDefinition(String type) {
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Declaration type is required.");
        }
        DeclarationDefinition definition = DEFINITIONS.get(type.trim().toLowerCase());
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown declaration type: " + type);
        }
        return definition;
    }

    List<JsonOption> listJsonOptions(String type) {
        DeclarationDefinition definition = resolveDefinition(type);
        Path folder = resolveResourceFolderPath(definition);
        if (folder == null) {
            return List.of();
        }

        try (Stream<Path> files = Files.walk(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .filter(this::isUsableJsonFile)
                    .sorted()
                    .map(path -> {
                        String relative = folder.relativize(path).toString().replace('\\', '/');
                        return new JsonOption(relative, relative);
                    })
                    .toList();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to list declaration JSON files: " + exception.getMessage());
        }
    }

    Path resolveSelectedJsonPath(String type, String selectedJson) {
        DeclarationDefinition definition = resolveDefinition(type);
        if (selectedJson == null || selectedJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a declaration JSON file.");
        }

        String normalizedSelection = selectedJson.trim().replace('\\', '/');
        if (!normalizedSelection.toLowerCase().endsWith(".json")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected declaration file must be a JSON file.");
        }

        Path normalizedRelative = Paths.get(normalizedSelection).normalize();
        if (normalizedRelative.isAbsolute() || normalizedRelative.startsWith("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected declaration file is outside the allowed folder.");
        }

        Path folder = resolveResourceFolderPath(definition);
        if (folder != null) {
            Path candidate = folder.resolve(normalizedRelative).normalize();
            if (candidate.startsWith(folder) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        InputStream resourceStream = getClass().getClassLoader()
                .getResourceAsStream(definition.resourceFolder() + "/" + normalizedRelative.toString().replace('\\', '/'));
        if (resourceStream == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected declaration JSON file was not found: " + normalizedSelection);
        }

        try (InputStream inputStream = resourceStream) {
            Path outputDirectory = Paths.get("target", "load-dashboard-json", definition.type()).toAbsolutePath().normalize();
            Files.createDirectories(outputDirectory);
            Path outputPath = outputDirectory.resolve(normalizedRelative.getFileName().toString()).normalize();
            Files.write(outputPath, inputStream.readAllBytes());
            return outputPath;
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to prepare the selected declaration JSON file: " + exception.getMessage());
        }
    }

    List<JsonNode> loadDeclarationPayloads(String type, String selectedJson) {
        Path jsonPath = resolveSelectedJsonPath(type, selectedJson);
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            JsonNode root = OBJECT_MAPPER.readTree(inputStream);
            if (root == null || root.isNull()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected declaration JSON file is empty.");
            }

            if (root.isArray()) {
                List<JsonNode> payloads = new ArrayList<>();
                root.forEach(payloads::add);
                if (payloads.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected declaration JSON file does not contain any declaration entries.");
                }
                return List.copyOf(payloads);
            }
            return List.of(root);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to read the selected declaration JSON file: " + exception.getMessage());
        }
    }

    Map<String, Object> jsonOptionsPayload(String type) {
        DeclarationDefinition definition = resolveDefinition(type);
        Path folder = resolveResourceFolderPath(definition);
        List<Map<String, String>> files = new ArrayList<>();
        for (JsonOption option : listJsonOptions(type)) {
            files.add(new TreeMap<>(Map.of(
                    "name", option.name(),
                    "resourcePath", option.resourcePath())));
        }

        return Map.of(
                "type", definition.type(),
                "moduleLabel", definition.moduleLabel(),
                "route", definition.route(),
                "resourceFolder", definition.resourceFolder(),
                "resolvedFolderPath", folder != null ? folder.toString() : "",
                "folderFound", folder != null,
                "files", files);
    }

    private Path resolveResourceFolderPath(DeclarationDefinition definition) {
        return resourceFolderCandidates(definition).stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(null);
    }

    private List<Path> resourceFolderCandidates(DeclarationDefinition definition) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.add(Paths.get("src", "test", "resources", definition.resourceFolder()).toAbsolutePath().normalize());
        candidates.add(Paths.get("target", "test-classes", definition.resourceFolder()).toAbsolutePath().normalize());

        locateProjectRoot().ifPresent(projectRoot -> {
            candidates.add(projectRoot.resolve(Paths.get("src", "test", "resources", definition.resourceFolder()))
                    .toAbsolutePath()
                    .normalize());
            candidates.add(projectRoot.resolve(Paths.get("target", "test-classes", definition.resourceFolder()))
                    .toAbsolutePath()
                    .normalize());
        });

        return List.copyOf(candidates);
    }

    private Optional<Path> locateProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private boolean isUsableJsonFile(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) == 0L) {
                return false;
            }
            try (InputStream inputStream = Files.newInputStream(path)) {
                JsonNode root = OBJECT_MAPPER.readTree(inputStream);
                return root != null && !root.isNull()
                        && (!root.isArray() || root.size() > 0);
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    record DeclarationDefinition(
            String type,
            String moduleLabel,
            String menuLabel,
            String route,
            String resourceFolder,
            WorkflowKind workflowKind,
            List<String> expectedVisibleTexts) {
    }

    record JsonOption(String name, String resourcePath) {
    }

    enum WorkflowKind {
        IPT_STYLE,
        OUT,
        COO
    }
}
