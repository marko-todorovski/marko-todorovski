package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.DiagramType;
import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.ObjectDiagramGeneratorService;
import com.example.aidiagramgenerator.service.ObjectDiagramGeneratorServiceImpl;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectDiagramGeneratorTest {

    private final ObjectDiagramGeneratorService service   = new ObjectDiagramGeneratorServiceImpl();
    private final ObjectDiagramGenerator        generator = new ObjectDiagramGenerator(service);

    @Test
    @DisplayName("supports() returns OBJECT")
    void supportsObjectDiagramType() {
        assertEquals(DiagramType.OBJECT, generator.supports());
    }

    @Test
    @DisplayName("Default diagram is generated when input has no entities")
    void generatesDefaultDiagramForEmptyInput() {
        ParsedInput input = new ParsedInput("", InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.startsWith("@startuml"), "Must start with @startuml");
        assertTrue(uml.endsWith("@enduml"), "Must end with @enduml");
        assertTrue(uml.contains("object"), "Must contain at least one object declaration");
        assertTrue(uml.contains("-->"), "Must contain at least one association");
    }

    @Test
    @DisplayName("Typed instances (var : ClassName) produce quoted object declarations with 'as' alias")
    void typedInstanceProducesQuotedObjectWithAlias() {
        ParsedInput input = new ParsedInput("""
                dogD : Dog
                owner1 : Person
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("object \"dogD : Dog\" as dogD"), "Must use typed label with alias");
        assertTrue(uml.contains("object \"owner1 : Person\" as owner1"), "Must declare owner1 instance");
    }

    @Test
    @DisplayName("Attribute assignments are written inside object blocks")
    void attributesAreWrittenInsideObjectBlock() {
        ParsedInput input = new ParsedInput("""
                dogD : Dog
                name = "Wolfy"
                pedigree = true
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("name = \"Wolfy\"") || uml.contains("name = Wolfy"),
                "Attribute 'name' must appear inside object block");
        assertTrue(uml.contains("pedigree = true"), "Attribute 'pedigree' must appear inside object block");
    }

    @Test
    @DisplayName("Associations between instances are rendered with -->")
    void associationsUsedoubleArrow() {
        ParsedInput input = new ParsedInput("""
                dogD : Dog
                owner1 : Person
                dogD --> owner1 : ownedBy
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("dogD --> owner1"), "Association must use --> notation");
    }

    @Test
    @DisplayName("Entities fall back to instanceName1 : ClassName format when no typed notation found")
    void fallsBackToEntityListWithNumericSuffix() {
        ParsedInput input = new ParsedInput("Show Dog and Person objects", InputType.TEXT);
        input.addEntity("Dog");
        input.addEntity("Person");

        String uml = generator.generate(input);

        assertTrue(uml.contains("object \"dog1 : Dog\" as dog1"), "Dog must become dog1 instance");
        assertTrue(uml.contains("object \"person1 : Person\" as person1"), "Person must become person1 instance");
    }

    @Test
    @DisplayName("Single-entity input declares typed instance header")
    void singleEntityHasTypedHeader() {
        ParsedInput input = new ParsedInput("order1 : Order", InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("object \"order1 : Order\" as order1"), "Must declare typed instance");
        assertTrue(uml.startsWith("@startuml"), "Must start with @startuml");
        assertTrue(uml.endsWith("@enduml"), "Must end with @enduml");
    }

    @Test
    @DisplayName("Output does not contain @enduml before final line")
    void endumlAppearsOnlyAtEnd() {
        ParsedInput input = new ParsedInput("""
                dogD : Dog
                name = "Wolfy"
                owner1 : Person
                dogD --> owner1 : ownedBy
                """, InputType.TEXT);

        String uml = generator.generate(input);

        int firstEnduml = uml.indexOf("@enduml");
        int lastEnduml = uml.lastIndexOf("@enduml");
        assertEquals(firstEnduml, lastEnduml, "@enduml must appear exactly once");
        assertTrue(uml.trim().endsWith("@enduml"), "@enduml must be the last line");
    }

    @Test
    @DisplayName("values assigned keyword triggers OBJECT detection in raw content")
    void valuesAssignedKeywordInRawContent() {
        ParsedInput input = new ParsedInput("""
                Show object diagram with values assigned to the following instances.
                customer1 : Customer
                age = 30
                email = "test@example.com"
                """, InputType.TEXT);

        String uml = generator.generate(input);

        assertTrue(uml.contains("object"), "Must contain object declarations");
        assertFalse(uml.isBlank(), "Output must not be blank");
    }
}
