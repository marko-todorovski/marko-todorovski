package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UseCaseDiagramGeneratorTest {

    private final UseCaseDiagramGenerator generator = new UseCaseDiagramGenerator();

    @Test
    @DisplayName("Single-word actor should use plain notation without quotes")
    void singleWordActorUsesPlainNotation() {
        ParsedInput input = new ParsedInput("""
                Actors include Student.
                The student can login and view grades.
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("actor Student"), "Single-word actor must use plain notation");
        assertFalse(uml.contains("actor \"Student\""), "Single-word actor must not use quoted notation");
    }

    @Test
    @DisplayName("Multi-word actor should use quoted notation with alias")
    void multiWordActorUsesQuotedNotation() {
        ParsedInput input = new ParsedInput("""
                Actors include Bank Customer.
                The bank customer can withdraw cash.
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("actor \"Bank Customer\" as"), "Multi-word actor must use quoted notation with alias");
    }

    @Test
    @DisplayName("Use cases should be declared with parentheses inside the system rectangle")
    void useCasesDeclaredWithParentheses() {
        ParsedInput input = new ParsedInput("""
                Actors include Student.
                The student can download materials and view grades.
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("rectangle \"System\" {"), "Must have a system boundary rectangle");
        assertTrue(uml.contains("  (Download Materials)"), "Use case must use parenthesis notation inside rectangle");
        assertTrue(uml.contains("  (View Grades)"), "Use case must use parenthesis notation inside rectangle");
        assertFalse(uml.contains("usecase \""), "Must not use 'usecase' keyword — parenthesis notation required");
    }

    @Test
    @DisplayName("Associations should connect actor to parenthesized use case")
    void associationsUseParenthesizedUseCases() {
        ParsedInput input = new ParsedInput("""
                Actors include Student.
                The student can download materials and view grades.
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("Student --> (Download Materials)"), "Association must reference use case with parentheses");
        assertTrue(uml.contains("Student --> (View Grades)"), "Association must reference use case with parentheses");
    }

    @Test
    @DisplayName("Modal phrases map to <<include>> and <<extend>> dependencies")
    void mapsModalPhrasesToIncludeAndExtendDependencies() {
        ParsedInput input = new ParsedInput("""
                Actors include Student.
                The student can download materials and view grades.
                Download materials must login first.
                View grades might send notification.
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("(Download Materials) ..> (Login) : <<include>>"));
        assertTrue(uml.contains("(View Grades) ..> (Send Notification) : <<extend>>"));
    }

    @Test
    @DisplayName("Multiple actors each connect to their own use cases")
    void multipleActorsConnectToOwnUseCases() {
        ParsedInput input = new ParsedInput("""
                Actors include Student and Administrator.
                The student can view grades and download materials.
                The administrator can manage courses.
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("actor Student"), "Student actor must be declared");
        assertTrue(uml.contains("actor Administrator"), "Administrator actor must be declared");
        assertTrue(uml.contains("Student --> (View Grades)"), "Student must be associated to view grades");
        assertTrue(uml.contains("Administrator --> (Manage Courses)"), "Administrator must be associated to manage courses");
    }
}
