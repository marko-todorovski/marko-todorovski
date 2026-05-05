package com.example.aidiagramgenerator.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DiagramType} JSON deserialization via {@code @JsonCreator}.
 */
class DiagramTypeDeserializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ── Wrapper used by parameterised tests ──────────────────────────────────

    record DiagramTypeWrapper(DiagramType diagramType) {}

    private DiagramType deserialize(String json) throws Exception {
        return mapper.readValue("{\"diagramType\":\"" + json + "\"}", DiagramTypeWrapper.class)
                .diagramType();
    }

    // ── Lowercase (value string) ─────────────────────────────────────────────

    @Nested
    @DisplayName("Lowercase value strings")
    class LowercaseValues {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({
            "class,       CLASS",
            "sequence,    SEQUENCE",
            "er,          ER",
            "architecture, ARCHITECTURE",
            "c4,          C4"
        })
        void shouldDeserializeLowercaseValues(String input, String expectedName) throws Exception {
            DiagramType result = deserialize(input.trim());
            assertThat(result.name()).isEqualTo(expectedName);
        }
    }

    // ── Uppercase (enum constant name) ───────────────────────────────────────

    @Nested
    @DisplayName("Uppercase enum constant names")
    class UppercaseNames {

        @ParameterizedTest(name = "\"{0}\" → {0}")
        @ValueSource(strings = {"CLASS", "SEQUENCE", "ER", "ARCHITECTURE", "C4"})
        void shouldDeserializeUppercaseNames(String input) throws Exception {
            DiagramType result = deserialize(input);
            assertThat(result.name()).isEqualTo(input);
        }
    }

    // ── Mixed case ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mixed-case inputs")
    class MixedCase {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"Class", "cLaSs", "Sequence", "Er", "Architecture", "C4"})
        void shouldDeserializeMixedCaseValues(String input) throws Exception {
            DiagramType result = deserialize(input);
            assertThat(result).isNotNull();
        }
    }

    // ── Serialization ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Serialization (@JsonValue)")
    class Serialization {

        @ParameterizedTest(name = "{0} → \"{1}\"")
        @CsvSource({
            "CLASS,        class",
            "SEQUENCE,     sequence",
            "ER,           er",
            "ARCHITECTURE, architecture",
            "C4,           c4"
        })
        void shouldSerializeToLowercaseValue(String enumName, String expectedJson) throws Exception {
            DiagramType type = DiagramType.valueOf(enumName.trim());
            String json = mapper.writeValueAsString(type);
            assertThat(json).isEqualTo("\"" + expectedJson.trim() + "\"");
        }
    }

    // ── Round-trip ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Round-trip serialize → deserialize")
    class RoundTrip {

        @Test
        void shouldRoundTripAllValues() throws Exception {
            for (DiagramType type : DiagramType.values()) {
                String serialized = mapper.writeValueAsString(type);
                // strip surrounding quotes
                String rawValue = serialized.replace("\"", "");
                DiagramType deserialized = deserialize(rawValue);
                assertThat(deserialized).isEqualTo(type);
            }
        }
    }

    // ── Invalid values ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid inputs → InvalidFormatException")
    class InvalidInputs {

        @ParameterizedTest(name = "\"{0}\" should fail")
        @ValueSource(strings = {"unknown", "UML", "flowchart", "pie", "gantt", ""})
        void shouldThrowForUnknownValues(String input) {
            assertThatThrownBy(() -> deserialize(input))
                    .isInstanceOf(ValueInstantiationException.class);
        }

        @Test
        void fromValueShouldThrowIllegalArgumentExceptionForNull() {
            assertThatThrownBy(() -> DiagramType.fromValue(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        void fromValueShouldThrowWithDescriptiveMessage() {
            assertThatThrownBy(() -> DiagramType.fromValue("invalid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown diagram type")
                    .hasMessageContaining("invalid");
        }
    }
}
