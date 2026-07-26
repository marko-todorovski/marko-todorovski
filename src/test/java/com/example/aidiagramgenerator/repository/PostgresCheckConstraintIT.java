package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.domain.RepositorySourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reproduces the previously-reported blockers, ck_diagram_shares_status and
 * ck_repositories_source_type, against a REAL PostgreSQL instance (not H2), through the
 * full JPA/Hibernate stack, running the actual postgresql/ Flyway migrations.
 *
 * Requires a local Postgres reachable at localhost:5432 (docker-compose.yml's db service:
 * ai_user/devpass, db ai_diagrams). If unreachable, this test skips itself rather than
 * failing the build, since a live Postgres instance is not guaranteed in every environment.
 *
 * Result of running this against a real local PostgreSQL 16 instance: both inserts succeed.
 * Neither CHECK constraint rejects the values the application actually writes
 * (RepositorySourceType.ZIP_UPLOAD / GITHUB_URL, DiagramShareStatus.ACTIVE), confirming the
 * H2-only conclusion (transient dev-process state, not a migration/entity defect) also holds
 * against the production database engine. No migration changes were required.
 */
@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles({"dev", "postgres-test"})
class PostgresCheckConstraintIT {

    @Autowired
    private ApplicationUserRepository userRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private DomainDiagramRepository diagramRepository;

    @Autowired
    private DiagramVersionRepository diagramVersionRepository;

    @Autowired
    private DiagramShareRepository diagramShareRepository;

    @BeforeEach
    void assumePostgresReachable() {
        try (Connection ignored = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/ai_diagrams", "ai_user", "devpass")) {
            // reachable
        } catch (SQLException e) {
            assumeTrue(false, "Local PostgreSQL not reachable, skipping: " + e.getMessage());
        }
    }

    @AfterEach
    void cleanUp() {
        diagramShareRepository.deleteAll();
        diagramVersionRepository.deleteAll();
        diagramRepository.deleteAll();
        repositoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void zipUploadRepositoryPersistsAgainstRealPostgres() {
        ApplicationUser owner = userRepository.save(new ApplicationUser("pg-it-owner@example.com", "hash"));

        Repository repo = new Repository(owner, "Test Repo", RepositorySourceType.ZIP_UPLOAD);
        repo.setOriginalFilename("test.zip");
        Repository saved = repositoryRepository.save(repo);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSourceType()).isEqualTo(RepositorySourceType.ZIP_UPLOAD);
    }

    @Test
    void activeDiagramSharePersistsAgainstRealPostgres() {
        Diagram diagram = diagramRepository.save(
                new Diagram("prompt", DiagramType.CLASS, "@startuml\nclass A\n@enduml"));
        DiagramVersion version = diagramVersionRepository.save(
                new DiagramVersion(diagram, 1, "@startuml\nclass A\n@enduml", DiagramSourceFormat.PLANTUML));

        DiagramShare share = new DiagramShare(diagram, version, "token-hash-pg-it");
        DiagramShare saved = diagramShareRepository.save(share);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus().name()).isEqualTo("ACTIVE");
    }
}
