package io.github.loomforge.jtg.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Java source files to extract {@code @TsRecord}-annotated record declarations
 * and their components, without requiring a full compilation.
 *
 * <p>Uses targeted regex parsing on the source text. This keeps the plugin
 * dependency-light and avoids needing {@code tools.jar} at runtime. A future
 * release may switch to the Java Compiler Tree API for more robustness.</p>
 */
public final class RecordParser {

    /**
     * Represents a single record component (field).
     */
    public record Component(String name, String type) {}

    /**
     * Represents a parsed record ready for TypeScript emission.
     *
     * @param dependencies  other records in the same source file that this record
     *                      references in its components — emitted into the same .ts file
     */
    public record RecordDefinition(
        String recordName,
        String exportName,   // may differ if @TsRecord(exportName="...")
        boolean asType,
        List<Component> components,
        Path sourceFile,
        List<RecordDefinition> dependencies
    ) {
        /** Convenience constructor — no dependencies (backwards compat). */
        public RecordDefinition(String recordName, String exportName, boolean asType,
                                List<Component> components, Path sourceFile) {
            this(recordName, exportName, asType, components, sourceFile, List.of());
        }
    }

    // Matches @TsRecord with optional attributes on the line(s) before the record declaration
    private static final Pattern TS_RECORD_ANNOTATION =
        Pattern.compile("@TsRecord(?:\\s*\\(([^)]*)\\))?");

    // Matches:  public record Foo(String name, int age, ...)
    private static final Pattern RECORD_DECL =
        Pattern.compile("(?:public\\s+|private\\s+|protected\\s+)?record\\s+(\\w+)\\s*\\(([^)]*)\\)");

    private RecordParser() {}

    /**
     * Scans a single {@code .java} source file for {@code @TsRecord}-annotated records,
     * and resolves any sibling/nested records they reference as dependencies.
     *
     * @param sourceFile path to the Java source file
     * @return list of record definitions found (usually 0 or 1 per file)
     * @throws IOException if the file cannot be read
     */
    public static List<RecordDefinition> parse(Path sourceFile) throws IOException {
        String source = Files.readString(sourceFile);

        // First pass: collect ALL record declarations in this file (annotated or not)
        Map<String, RecordDefinition> allRecordsInFile = parseAllRecords(source, sourceFile);

        // Second pass: find @TsRecord-annotated ones and attach their dependencies
        List<RecordDefinition> results = new ArrayList<>();
        String[] lines = source.split("\n");
        String pendingExportName = null;
        boolean pendingAsType = false;
        boolean annotationSeen = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            Matcher annMatcher = TS_RECORD_ANNOTATION.matcher(line);
            if (annMatcher.find()) {
                annotationSeen = true;
                pendingExportName = null;
                pendingAsType = false;
                String attrs = annMatcher.group(1);
                if (attrs != null && !attrs.isBlank()) {
                    pendingExportName = extractStringAttribute(attrs, "exportName");
                    pendingAsType     = extractBooleanAttribute(attrs, "asType");
                }
                continue;
            }

            if (annotationSeen) {
                if (line.isBlank() || (line.startsWith("@") && !line.startsWith("@TsRecord"))) {
                    continue;
                }

                String window = buildWindow(lines, i, 3);
                Matcher recMatcher = RECORD_DECL.matcher(window);
                if (recMatcher.find()) {
                    String recordName = recMatcher.group(1);
                    String paramsRaw  = recMatcher.group(2);
                    String exportName = (pendingExportName != null && !pendingExportName.isBlank())
                        ? pendingExportName : recordName;

                    List<Component> components = parseComponents(paramsRaw);

                    // Resolve dependencies: other records in this file referenced by components
                    List<RecordDefinition> deps = resolveDependencies(
                        components, recordName, allRecordsInFile);

                    results.add(new RecordDefinition(
                        recordName, exportName, pendingAsType, components, sourceFile, deps));
                }
                annotationSeen = false;
                pendingExportName = null;
                pendingAsType = false;
            }
        }

        return results;
    }

    /**
     * Parses ALL record declarations in the source (regardless of annotation),
     * keyed by simple record name. Used for dependency resolution.
     */
    static Map<String, RecordDefinition> parseAllRecords(String source, Path sourceFile) {
        Map<String, RecordDefinition> found = new LinkedHashMap<>();
        Matcher m = RECORD_DECL.matcher(source);
        while (m.find()) {
            String name = m.group(1);
            String params = m.group(2);
            List<Component> components = parseComponents(params);
            // No dependencies resolved at this stage — just bare definitions
            found.put(name, new RecordDefinition(name, name, false, components, sourceFile));
        }
        return found;
    }

    /**
     * Given a list of components, finds which ones reference records that exist
     * in the same file (by simple type name), and returns those as dependencies
     * in declaration order. Transitive deps are resolved recursively (cycle-safe).
     */
    static List<RecordDefinition> resolveDependencies(
            List<Component> components,
            String selfName,
            Map<String, RecordDefinition> allRecords) {
        return resolveDependencies(components, selfName, allRecords, new LinkedHashSet<>());
    }

    private static List<RecordDefinition> resolveDependencies(
            List<Component> components,
            String selfName,
            Map<String, RecordDefinition> allRecords,
            Set<String> visited) {

        List<RecordDefinition> deps = new ArrayList<>();
        for (Component c : components) {
            // Extract bare type name(s) from generic wrappers: List<TripCard> -> TripCard
            for (String typeName : extractSimpleTypeNames(c.type())) {
                if (typeName.equals(selfName)) continue;       // skip self-reference
                if (visited.contains(typeName)) continue;      // skip already-visited
                RecordDefinition dep = allRecords.get(typeName);
                if (dep == null) continue;                     // not a sibling record

                visited.add(typeName);

                // Recursively resolve the dependency's own deps first (DFS pre-order)
                List<RecordDefinition> transitive = resolveDependencies(
                    dep.components(), typeName, allRecords, visited);

                deps.addAll(transitive);
                deps.add(dep);
            }
        }
        return deps;
    }

    /**
     * Extracts all simple (unqualified) type names from a possibly generic type string.
     * e.g. "List&lt;TripCard&gt;" -> ["TripCard"], "Map&lt;String,BookingItem&gt;" -> ["String","BookingItem"]
     */
    static List<String> extractSimpleTypeNames(String type) {
        List<String> names = new ArrayList<>();
        // Strip generics recursively by grabbing all word tokens
        Matcher m = Pattern.compile("\\b([A-Z][\\w]*)\\b").matcher(type);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildWindow(String[] lines, int start, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < Math.min(start + count, lines.length); i++) {
            sb.append(lines[i]).append(" ");
        }
        return sb.toString();
    }

    /**
     * Parses "String name, int age, boolean active" into Component list.
     * Handles multi-word types like "Map<String, Integer>" by tracking angle-bracket depth.
     */
    static List<Component> parseComponents(String raw) {
        List<Component> list = new ArrayList<>();
        if (raw == null || raw.isBlank()) return list;

        // Split on commas that are NOT inside angle brackets
        List<String> params = splitRespectingGenerics(raw.trim());
        for (String param : params) {
            param = param.trim();
            if (param.isBlank()) continue;

            // Strip annotations on individual components: e.g. "@NotNull String name"
            param = param.replaceAll("@\\w+(?:\\([^)]*\\))?\\s+", "").trim();

            // Last token is the name; everything before is the type
            int lastSpace = param.lastIndexOf(' ');
            if (lastSpace < 0) continue; // malformed

            String type = param.substring(0, lastSpace).trim();
            String name = param.substring(lastSpace + 1).trim();

            // Clean up: remove 'final' modifier occasionally added to components
            type = type.replaceFirst("^final\\s+", "").trim();

            list.add(new Component(name, type));
        }
        return list;
    }

    private static List<String> splitRespectingGenerics(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    private static String extractStringAttribute(String attrs, String key) {
        Pattern p = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private static boolean extractBooleanAttribute(String attrs, String key) {
        Pattern p = Pattern.compile(key + "\\s*=\\s*(true|false)");
        Matcher m = p.matcher(attrs);
        return m.find() && "true".equalsIgnoreCase(m.group(1));
    }
}
