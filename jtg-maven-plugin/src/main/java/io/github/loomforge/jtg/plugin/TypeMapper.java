package io.github.loomforge.jtg.plugin;

import java.util.Map;

/**
 * Maps Java type names (as they appear in source) to TypeScript type equivalents.
 *
 * <p>This is intentionally kept simple for the initial release. Future versions
 * will support generics, arrays, enums, nested records, and custom mappings.</p>
 */
public final class TypeMapper {

    private static final Map<String, String> PRIMITIVES = Map.of(
            // Java primitives
            "byte", "number",
            "short", "number",
            "int", "number",
            "long", "number",
            "float", "number",
            "double", "number",
            "boolean", "boolean",
            "char", "string");

    private static final Map<String, String> BOXED = Map.of(
            "Byte", "number",
            "Short", "number",
            "Integer", "number",
            "Long", "number",
            "Float", "number",
            "Double", "number",
            "Boolean", "boolean",
            "Character", "string",
            "String", "string");

    private static final Map<String, String> COMMON = Map.ofEntries(
            Map.entry("BigDecimal", "number"),
            Map.entry("BigInteger", "number"),
            Map.entry("UUID", "string"),
            Map.entry("LocalDate", "string"),
            Map.entry("LocalDateTime", "string"),
            Map.entry("ZonedDateTime", "string"),
            Map.entry("OffsetDateTime", "string"),
            Map.entry("Instant", "string"),
            Map.entry("Object", "unknown"),
            Map.entry("List", "[]"), // sentinel: array shorthand
            Map.entry("Set", "[]"), // sentinel: array shorthand
            Map.entry("Collection", "[]"), // sentinel: array shorthand
            Map.entry("Map", "Record<string, unknown>"),
            Map.entry("Optional", "unknown | null"));

    private TypeMapper() {}

    /**
     * Converts a Java type string (simple name as it appears in source) to its
     * TypeScript equivalent. Unknown types fall back to {@code unknown}.
     *
     * @param javaType the simple type name, e.g. {@code "String"}, {@code "int"}, {@code "UUID"}
     * @return the TypeScript type string
     */
    public static String toTypeScript(String javaType) {
        if (javaType == null || javaType.isBlank()) return "unknown";

        // Strip array brackets — handle later
        String base = javaType.trim();

        // Handle simple generics like List<String> -> string[], Map<K,V> -> Record<string,V>
        if (base.contains("<")) {
            String outer = base.substring(0, base.indexOf('<'));
            String inner = base.substring(base.indexOf('<') + 1, base.lastIndexOf('>'));
            String outerTs = resolveSimple(outer);
            if (outerTs.equals("[]")) {
                // Array shorthand: List<String> -> string[], List<TripCard> -> TripCard[]
                return toTypeScript(inner.trim()) + "[]";
            }
            if (outerTs.startsWith("Record")) {
                // Map<K, V> — split on comma at depth 0 to get value type
                int comma = findTopLevelComma(inner);
                String valueTs =
                        comma >= 0 ? toTypeScript(inner.substring(comma + 1).trim()) : "unknown";
                return "Record<string, " + valueTs + ">";
            }
            return outerTs;
        }

        // Handle Java array syntax int[] / String[]
        if (base.endsWith("[]")) {
            String element = base.substring(0, base.length() - 2);
            return toTypeScript(element) + "[]";
        }

        // Strip fully-qualified package prefix: java.util.UUID -> UUID
        if (base.contains(".")) {
            base = base.substring(base.lastIndexOf('.') + 1);
        }

        return resolveSimple(base);
    }

    /** Returns index of the first comma not nested inside angle brackets, or -1. */
    private static int findTopLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    private static String resolveSimple(String type) {
        String ts;
        ts = PRIMITIVES.get(type);
        if (ts != null) return ts;
        ts = BOXED.get(type);
        if (ts != null) return ts;
        ts = COMMON.get(type);
        if (ts != null) return ts;
        // Unknown / user-defined type — emit the name as-is; a future release
        // will resolve cross-record references.
        return type;
    }
}
