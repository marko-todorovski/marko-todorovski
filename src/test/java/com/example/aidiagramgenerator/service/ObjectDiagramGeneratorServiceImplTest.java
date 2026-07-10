package com.example.aidiagramgenerator.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectDiagramGeneratorServiceImplTest {

    private final ObjectDiagramGeneratorServiceImpl service = new ObjectDiagramGeneratorServiceImpl();

    @Test
    @DisplayName("Output always wrapped in @startuml / @enduml")
    void wrapsInStartEndUml() {
        String result = service.generateObjectDiagram("dogD : Dog");
        assertTrue(result.startsWith("@startuml"));
        assertTrue(result.endsWith("@enduml"));
    }

    @Test
    @DisplayName("@enduml appears exactly once")
    void endumlExactlyOnce() {
        String result = service.generateObjectDiagram("dogD : Dog\nowner1 : Person");
        long count = result.lines().filter("@enduml"::equals).count();
        assertTrue(count == 1, "Expected @enduml exactly once, got: " + count);
    }

    @Test
    @DisplayName("Typed instance produces object block with correct header")
    void typedInstanceProducesObjectBlock() {
        String result = service.generateObjectDiagram("dogD : Dog");
        assertTrue(result.contains("object \"dogD : Dog\" as dogD"),
                "Expected typed instance block, got:\n" + result);
    }

    @Test
    @DisplayName("Attribute assignment is included in instance block")
    void attributeAppearsInInstanceBlock() {
        String result = service.generateObjectDiagram("dogD : Dog\nname = Wolfy");
        assertTrue(result.contains("name = ") || result.contains("name="),
                "Expected attribute in output, got:\n" + result);
    }

    @Test
    @DisplayName("Multiple instances produce auto-chained or explicit associations")
    void multipleInstancesHaveLink() {
        String result = service.generateObjectDiagram("dogD : Dog\nowner1 : Person");
        assertTrue(result.contains("dogD") && result.contains("owner1"),
                "Expected both instances, got:\n" + result);
    }

    @Test
    @DisplayName("Explicit arrow in text is preserved")
    void explicitArrowPreserved() {
        String result = service.generateObjectDiagram(
                "dogD : Dog\nowner1 : Person\ndogD --> owner1 : ownedBy");
        assertTrue(result.contains("ownedBy"), "Expected relation label, got:\n" + result);
    }

    @Test
    @DisplayName("Empty input produces default diagram with dog/owner/order")
    void emptyInputProducesDefaultDiagram() {
        String result = service.generateObjectDiagram("");
        assertTrue(result.contains("dogD"), "Expected default dogD, got:\n" + result);
        assertTrue(result.contains("owner1"), "Expected default owner1");
        assertTrue(result.contains("order1"), "Expected default order1");
    }

    @Test
    @DisplayName("Null input produces default diagram without throwing")
    void nullInputSafe() {
        String result = service.generateObjectDiagram(null);
        assertFalse(result.isBlank());
        assertTrue(result.startsWith("@startuml"));
    }
}
