package com.example.aidiagramgenerator.service.generation.generator;

import com.example.aidiagramgenerator.enums.InputType;
import com.example.aidiagramgenerator.service.generation.model.ParsedInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassDiagramGeneratorTest {

    private final ClassDiagramGenerator generator = new ClassDiagramGenerator();

    private ParsedInput input(String raw, String... entities) {
        ParsedInput p = new ParsedInput(raw, InputType.NATURAL_LANGUAGE);
        for (String e : entities) p.addEntity(e);
        return p;
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should start with classDiagram keyword")
    void shouldStartWithClassDiagram() {
        String result = generator.generate(input("", "User"));
        assertTrue(result.startsWith("classDiagram"), "Output must start with 'classDiagram'");
    }

    @Test
    @DisplayName("Empty input should produce a default fallback diagram")
    void emptyInputFallback() {
        ParsedInput empty = new ParsedInput("", InputType.NATURAL_LANGUAGE);
        String result = generator.generate(empty);
        assertTrue(result.startsWith("classDiagram"));
        assertTrue(result.contains("class User"), "Fallback must include a User class");
        assertTrue(result.contains("..>"), "Fallback must include dependency arrows");
    }

    // ── Inheritance ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Inheritance detection")
    class Inheritance {

        @Test
        @DisplayName("'Student inherits from Person' should produce Person <|-- Student")
        void inheritsFrom() {
            String result = generator.generate(
                input("Student inherits from Person.", "Student", "Person"));
            assertTrue(result.contains("Person <|-- Student"),
                "Expected 'Person <|-- Student' but got:\n" + result);
        }

        @Test
        @DisplayName("'Dog extends Animal' should produce Animal <|-- Dog")
        void extends_keyword() {
            String result = generator.generate(
                input("Dog extends Animal.", "Dog", "Animal"));
            assertTrue(result.contains("Animal <|-- Dog"),
                "Expected 'Animal <|-- Dog' but got:\n" + result);
        }

        @Test
        @DisplayName("'Manager is a Employee' should produce Employee <|-- Manager")
        void isA() {
            String result = generator.generate(
                input("Manager is a Employee.", "Manager", "Employee"));
            assertTrue(result.contains("Employee <|-- Manager"),
                "Expected 'Employee <|-- Manager' but got:\n" + result);
        }

        @Test
        @DisplayName("'Service implements Interface' should produce Interface <|-- Service")
        void implements_keyword() {
            String result = generator.generate(
                input("Service implements Interface.", "Service", "Interface"));
            assertTrue(result.contains("Interface <|-- Service"),
                "Expected 'Interface <|-- Service' but got:\n" + result);
        }

        @Test
        @DisplayName("'Cat is a type of Animal' should produce Animal <|-- Cat")
        void isATypeOf() {
            String result = generator.generate(
                input("Cat is a type of Animal.", "Cat", "Animal"));
            assertTrue(result.contains("Animal <|-- Cat"),
                "Expected 'Animal <|-- Cat' but got:\n" + result);
        }
    }

    // ── Composition ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Composition detection")
    class Composition {

        @Test
        @DisplayName("'House contains a Room' should produce House *-- Room")
        void contains() {
            String result = generator.generate(
                input("House contains a Room.", "House", "Room"));
            assertTrue(result.contains("House *-- Room"),
                "Expected 'House *-- Room' but got:\n" + result);
        }

        @Test
        @DisplayName("'Engine is part of Car' should produce Car *-- Engine (composition)")
        void partOf() {
            String result = generator.generate(
                input("Engine is part of Car.", "Engine", "Car"));
            assertTrue(result.contains("*--"),
                "Expected composition '*--' for 'is part of' but got:\n" + result);
        }

        @Test
        @DisplayName("'User is composed of Profile' should produce composition *--")
        void isComposedOf() {
            String result = generator.generate(
                input("User is composed of Profile.", "User", "Profile"));
            assertTrue(result.contains("User *-- Profile"),
                "Expected 'User *-- Profile' but got:\n" + result);
        }
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Aggregation detection")
    class Aggregation {

        @Test
        @DisplayName("'University aggregates Department' should produce University o-- Department")
        void aggregates() {
            String result = generator.generate(
                input("University aggregates Department.", "University", "Department"));
            assertTrue(result.contains("University o-- Department"),
                "Expected 'University o-- Department' but got:\n" + result);
        }

        @Test
        @DisplayName("'Person has a Brain' should produce aggregation o--")
        void hasA() {
            String result = generator.generate(
                input("Person has a Brain.", "Person", "Brain"));
            assertTrue(result.contains("Person o-- Brain"),
                "Expected 'Person o-- Brain' for 'has a' but got:\n" + result);
        }

        @Test
        @DisplayName("'Person has an Address' should produce aggregation o--")
        void hasAn() {
            String result = generator.generate(
                input("Person has an Address.", "Person", "Address"));
            assertTrue(result.contains("o--"),
                "Expected aggregation 'o--' for 'has an' but got:\n" + result);
        }
    }

    // ── Association ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Association detection")
    class Association {

        @Test
        @DisplayName("'Brain owned by Person' should produce plain -- arrow")
        void ownedBy() {
            String result = generator.generate(
                input("Brain owned by Person.", "Brain", "Person"));
            assertTrue(result.contains("Brain -- Person"),
                "Expected 'Brain -- Person' for 'owned by' but got:\n" + result);
        }

        @Test
        @DisplayName("Association arrow should be plain -- not directed -->")
        void associationIsUndirected() {
            String result = generator.generate(
                input("Human owned by Animal.", "Human", "Animal"));
            assertTrue(result.contains(" -- "),
                "Expected undirected ' -- ' for association but got:\n" + result);
            assertFalse(result.contains("-->"),
                "Association arrow should not be directed '-->' but got:\n" + result);
        }
    }

    // ── Dependency ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Dependency detection")
    class Dependency {

        @Test
        @DisplayName("'OrderService depends on PaymentGateway' should produce ..> arrow")
        void dependsOn() {
            String result = generator.generate(
                input("OrderService depends on PaymentGateway.", "OrderService", "PaymentGateway"));
            assertTrue(result.contains("OrderService ..> PaymentGateway"),
                "Expected 'OrderService ..> PaymentGateway' but got:\n" + result);
        }

        @Test
        @DisplayName("'Controller uses Service' should produce ..> arrow")
        void uses() {
            String result = generator.generate(
                input("Controller uses Service.", "Controller", "Service"));
            assertTrue(result.contains("Controller ..> Service"),
                "Expected 'Controller ..> Service' but got:\n" + result);
        }
    }

    // ── Multiplicity ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multiplicity detection")
    class Multiplicity {

        @Test
        @DisplayName("'One student can take many modules' should produce 1 to 0..* multiplicity")
        void oneToMany() {
            String result = generator.generate(
                input("One student can take many modules.", "Student", "Module"));
            assertTrue(result.contains("\"1\"") && result.contains("\"0..*\""),
                "Expected multiplicity '\"1\"' and '\"0..*\"' but got:\n" + result);
            assertTrue(result.contains("Student") && result.contains("Module"),
                "Expected Student and Module in output");
        }

        @Test
        @DisplayName("'Student one-to-many Module' should produce 1 to 0..* multiplicity")
        void phraseOneToMany() {
            String result = generator.generate(
                input("Student one-to-many Module.", "Student", "Module"));
            assertTrue(result.contains("\"1\"") && result.contains("\"0..*\""),
                "Expected '\"1\"' and '\"0..*\"' for one-to-many phrase but got:\n" + result);
        }

        @Test
        @DisplayName("'Student one to many Module' (space-separated) should produce 1 to 0..*")
        void phraseOneToManySpaced() {
            String result = generator.generate(
                input("Student one to many Module.", "Student", "Module"));
            assertTrue(result.contains("\"1\"") && result.contains("\"0..*\""),
                "Expected '\"1\"' and '\"0..*\"' for 'one to many' phrase but got:\n" + result);
        }

        @Test
        @DisplayName("'Student many-to-many Module' should produce 0..* on both ends")
        void phraseManyToMany() {
            String result = generator.generate(
                input("Student many-to-many Module.", "Student", "Module"));
            long count = result.chars().filter(c -> c == '*').count();
            assertTrue(count >= 2,
                "Expected '0..*' on both ends for many-to-many but got:\n" + result);
        }

        @Test
        @DisplayName("'Student one-to-one Module' should produce 1 on both ends")
        void phraseOneToOne() {
            String result = generator.generate(
                input("Student one-to-one Module.", "Student", "Module"));
            assertTrue(result.contains("\"1\""),
                "Expected '\"1\"' for one-to-one but got:\n" + result);
        }

        @Test
        @DisplayName("'Course has multiple Students' should produce aggregation with 0..*")
        void hasMultiple() {
            String result = generator.generate(
                input("Course has multiple Students.", "Course", "Student"));
            assertTrue(result.contains("\"0..*\""),
                "Expected '\"0..*\"' for 'has multiple' but got:\n" + result);
            assertTrue(result.contains("o--"),
                "Expected aggregation arrow for 'has multiple' but got:\n" + result);
        }

        @Test
        @DisplayName("Inline UML notation 'Student 1..* Module' should produce 1..* multiplicity")
        void inlineUmlNotation() {
            String result = generator.generate(
                input("Student 1..* Module.", "Student", "Module"));
            assertTrue(result.contains("\"1..*\""),
                "Expected '\"1..*\"' for inline UML notation but got:\n" + result);
        }

        @Test
        @DisplayName("'many students belong to one Course' should produce 0..* to 1")
        void manyToOne() {
            String result = generator.generate(
                input("Many students belong to one Course.", "Student", "Course"));
            assertTrue(result.contains("\"0..*\"") && result.contains("\"1\""),
                "Expected '\"0..*\"' and '\"1\"' for many-to-one but got:\n" + result);
        }
    }

    // ── Class members ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Class member visibility")
    class Members {

        @Test
        @DisplayName("'id' attribute should get private visibility")
        void privateId() {
            String result = generator.generate(
                input("Student with id, name.", "Student"));
            assertTrue(result.contains("-String id") || result.contains("-int id") || result.contains("-id"),
                "Attribute 'id' should be private but got:\n" + result);
        }

        @Test
        @DisplayName("Regular attribute should get public visibility")
        void publicAttr() {
            String result = generator.generate(
                input("Course with title, credits.", "Course"));
            assertTrue(result.contains("+String title") || result.contains("+title"),
                "Attribute 'title' should be public but got:\n" + result);
        }

        @Test
        @DisplayName("Typed method signature should be included")
        void typedMethod() {
            String result = generator.generate(
                input("Student class with String getName() method.", "Student"));
            assertTrue(result.contains("getName()"),
                "Method getName() should appear in class body but got:\n" + result);
        }
    }

    // ── Entity block rendering ────────────────────────────────────────────────

    @Test
    @DisplayName("All extracted entities should appear as class declarations")
    void allEntitiesAreClasses() {
        String result = generator.generate(
            input("System has Order and Product.", "Order", "Product", "System"));
        assertTrue(result.contains("class Order"), "Order class missing");
        assertTrue(result.contains("class Product"), "Product class missing");
        assertTrue(result.contains("class System"), "System class missing");
    }
}
