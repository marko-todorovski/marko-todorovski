package com.example.aidiagramgenerator.migration;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class FlywaySchemaValidationContextTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private Environment environment;

    @Test
    void shouldStartApplicationContextWithFlywayAndHibernateValidation() {
        assertNotNull(flyway.info().current());
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
}
