package com.example.aidiagramgenerator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Development-only configuration.
 *
 * <p>Activated when {@code spring.profiles.active=dev}.
 * The H2 in-memory datasource properties are loaded from
 * {@code application-dev.properties} automatically by Spring Boot's
 * profile-based property resolution.</p>
 *
 * <p>Add any dev-only beans (test data loaders, mock services, etc.) here.</p>
 */
@Configuration
@Profile("dev")
public class DevDataSourceConfig {
    // H2 datasource is configured via application-dev.properties.
    // This class is a hook for future dev-only beans.
}
