package io.github.loomforge.jtg.plugin;

import io.github.loomforge.jtg.plugin.RecordParser.Component;
import io.github.loomforge.jtg.plugin.RecordParser.RecordDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JTGTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // TypeMapper
    // ------------------------------------------------------------------

    @Test
    void primitivesMappedCorrectly() {
        assertEquals("number",  TypeMapper.toTypeScript("int"));
        assertEquals("number",  TypeMapper.toTypeScript("long"));
        assertEquals("number",  TypeMapper.toTypeScript("double"));
        assertEquals("boolean", TypeMapper.toTypeScript("boolean"));
        assertEquals("string",  TypeMapper.toTypeScript("char"));
    }

    @Test
    void boxedTypesMappedCorrectly() {
        assertEquals("number",  TypeMapper.toTypeScript("Integer"));
        assertEquals("number",  TypeMapper.toTypeScript("BigDecimal"));
        assertEquals("boolean", TypeMapper.toTypeScript("Boolean"));
        assertEquals("string",  TypeMapper.toTypeScript("String"));
        assertEquals("string",  TypeMapper.toTypeScript("UUID"));
    }

    @Test
    void collectionTypesMappedCorrectly() {
        assertEquals("string[]",          TypeMapper.toTypeScript("List<String>"));
        assertEquals("number[]",          TypeMapper.toTypeScript("Set<Integer>"));
        assertEquals("Record<string, number>", TypeMapper.toTypeScript("Map<String, Integer>"));
    }

    @Test
    void arrayTypesMappedCorrectly() {
        assertEquals("string[]", TypeMapper.toTypeScript("String[]"));
        assertEquals("number[]", TypeMapper.toTypeScript("int[]"));
    }

    @Test
    void unknownTypeFallsBackToTypeName() {
        assertEquals("MyCustomType", TypeMapper.toTypeScript("MyCustomType"));
    }

    // ------------------------------------------------------------------
    // RecordParser — component parsing
    // ------------------------------------------------------------------

    @Test
    void parsesSimpleComponents() {
        List<Component> components = RecordParser.parseComponents("String name, int age, boolean active");
        assertEquals(3, components.size());
        assertEquals("name",   components.get(0).name());
        assertEquals("String", components.get(0).type());
        assertEquals("age",    components.get(1).name());
        assertEquals("int",    components.get(1).type());
        assertEquals("active", components.get(2).name());
        assertEquals("boolean",components.get(2).type());
    }

    @Test
    void parsesGenericComponents() {
        List<Component> components = RecordParser.parseComponents("List<String> tags, Map<String, Integer> scores");
        assertEquals(2, components.size());
        assertEquals("tags",          components.get(0).name());
        assertEquals("List<String>",  components.get(0).type());
        assertEquals("scores",        components.get(1).name());
        assertEquals("Map<String, Integer>", components.get(1).type());
    }

    @Test
    void handlesEmptyComponents() {
        assertTrue(RecordParser.parseComponents("").isEmpty());
        assertTrue(RecordParser.parseComponents(null).isEmpty());
    }

    // ------------------------------------------------------------------
    // RecordParser — full file parsing
    // ------------------------------------------------------------------

    @Test
    void parsesSimpleAnnotatedRecord() throws IOException {
        Path javaFile = writeJava("User.java", """
            package com.example;

            import io.github.loomforge.jtg.annotation.TsRecord;

            @TsRecord
            public record User(String name, int age, boolean active) {}
            """);

        List<RecordDefinition> defs = RecordParser.parse(javaFile);
        assertEquals(1, defs.size());

        RecordDefinition def = defs.get(0);
        assertEquals("User", def.recordName());
        assertEquals("User", def.exportName());
        assertFalse(def.asType());
        assertEquals(3, def.components().size());
    }

    @Test
    void respectsCustomExportName() throws IOException {
        Path javaFile = writeJava("Product.java", """
            @TsRecord(exportName = "ProductDTO")
            public record Product(String title, double price) {}
            """);

        List<RecordDefinition> defs = RecordParser.parse(javaFile);
        assertEquals(1, defs.size());
        assertEquals("ProductDTO", defs.get(0).exportName());
    }

    @Test
    void respectsAsTypeFlag() throws IOException {
        Path javaFile = writeJava("Address.java", """
            @TsRecord(asType = true)
            public record Address(String street, String city) {}
            """);

        List<RecordDefinition> defs = RecordParser.parse(javaFile);
        assertEquals(1, defs.size());
        assertTrue(defs.get(0).asType());
    }

    @Test
    void ignoresNonAnnotatedRecords() throws IOException {
        Path javaFile = writeJava("Plain.java", """
            public record Plain(String value) {}
            """);

        List<RecordDefinition> defs = RecordParser.parse(javaFile);
        assertTrue(defs.isEmpty());
    }

    // ------------------------------------------------------------------
    // TsEmitter — output rendering
    // ------------------------------------------------------------------

    @Test
    void emitsInterface() throws IOException {
        Path javaFile = writeJava("User.java", "");
        RecordDefinition def = new RecordDefinition(
                "User", "User", false,
                List.of(new Component("name", "String"), new Component("age", "int")),
                javaFile
        );

        String ts = TsEmitter.render(def);
        assertTrue(ts.contains("export interface User {"));
        assertTrue(ts.contains("  name: string;"));
        assertTrue(ts.contains("  age: number;"));
    }

    @Test
    void emitsTypeAlias() throws IOException {
        Path javaFile = writeJava("Config.java", "");
        RecordDefinition def = new RecordDefinition(
                "Config", "ConfigDTO", true,
                List.of(new Component("timeout", "int")),
                javaFile
        );

        String ts = TsEmitter.render(def);
        assertTrue(ts.contains("export type ConfigDTO = {"));
        assertTrue(ts.contains("  timeout: number;"));
        assertTrue(ts.endsWith("};\n"));
    }

    @Test
    void writesFileSiblingToSource() throws IOException {
        Path javaFile = writeJava("Order.java", """
            @TsRecord
            public record Order(String id, double total) {}
            """);

        List<RecordDefinition> defs = RecordParser.parse(javaFile);
        assertEquals(1, defs.size());

        Path tsFile = TsEmitter.emit(defs.get(0));
        assertTrue(Files.exists(tsFile));
        assertEquals("Order.ts", tsFile.getFileName().toString());
        assertEquals(javaFile.getParent(), tsFile.getParent()); // same directory!

        String content = Files.readString(tsFile);
        assertTrue(content.contains("export interface Order"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Path writeJava(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}