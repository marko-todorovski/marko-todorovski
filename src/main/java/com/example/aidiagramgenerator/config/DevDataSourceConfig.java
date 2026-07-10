package com.example.aidiagramgenerator.config;

import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
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

    @Bean
    @SuppressWarnings("unchecked")
    public ServletRegistrationBean<HttpServlet> h2ConsoleServlet() throws ReflectiveOperationException {
        Class<? extends HttpServlet> servletClass;
        try {
            servletClass = (Class<? extends HttpServlet>)
                    Class.forName("org.h2.server.web.JakartaWebServlet");
        } catch (ClassNotFoundException e) {
            servletClass = (Class<? extends HttpServlet>)
                    Class.forName("org.h2.server.web.WebServlet");
        }
        HttpServlet servlet = servletClass.getDeclaredConstructor().newInstance();
        ServletRegistrationBean<HttpServlet> registration =
                new ServletRegistrationBean<>(servlet, "/h2-console/*");
        registration.addInitParameter("-webAllowOthers", "");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
