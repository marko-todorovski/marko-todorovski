package com.example.aidiagramgenerator.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Jackson 2.x {@link ObjectMapper} bean used by services that depend on
 * {@code com.fasterxml.jackson.databind.ObjectMapper} directly (e.g. OpenAiDiagramService).
 *
 * <p>Spring Boot 4.0 auto-configures a {@code tools.jackson.databind.ObjectMapper} (Jackson 3.x)
 * but does not register a {@code com.fasterxml.jackson.databind.ObjectMapper} bean.
 * This configuration bridges that gap while respecting the global Jackson settings in
 * {@code application.properties} (indent-output, default-property-inclusion).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Preserve Java 8 date/time types (LocalDateTime, Instant, etc.)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Pretty-print output (mirrors spring.jackson.serialization.indent-output=true)
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Tolerate unknown properties coming from external APIs (e.g. OpenAI responses)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}
