package com.example.aidiagramgenerator.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationTest {

    @Test
    void shouldRunFlywayOnCleanDatabaseAndCreateExpectedSchema() throws Exception {
        String url = h2Url("clean_schema");

        migrate(url);

        try (Connection connection = connect(url)) {
            assertTableExists(connection, "application_users");
            assertTableExists(connection, "projects");
            assertTableExists(connection, "domain_diagrams");
            assertTableExists(connection, "diagram_versions");
            assertTableExists(connection, "diagram_shares");
            assertTableExists(connection, "project_members");
            assertTableExists(connection, "project_invitations");

            assertColumnExists(connection, "domain_diagrams", "project_id");
            assertColumnExists(connection, "domain_diagrams", "owner_id");
            assertColumnExists(connection, "domain_diagrams", "source_format");
            assertColumnExists(connection, "domain_diagrams", "current_source_code");
            assertColumnExists(connection, "domain_diagrams", "updated_at");
            assertColumnExists(connection, "domain_diagrams", "lock_version");

            assertConstraintExists(connection, "diagram_versions", "uk_diagram_versions_diagram_version");
            assertConstraintExists(connection, "diagram_versions", "fk_diagram_versions_diagram_id");
            assertConstraintExists(connection, "diagram_versions", "fk_diagram_versions_created_by_id");
            assertConstraintExists(connection, "diagram_shares", "uk_diagram_shares_token_hash");
            assertConstraintExists(connection, "diagram_shares", "fk_diagram_shares_diagram_id");
            assertConstraintExists(connection, "diagram_shares", "fk_diagram_shares_diagram_version_id");
            assertConstraintExists(connection, "projects", "fk_projects_owner_id");
            assertConstraintExists(connection, "domain_diagrams", "fk_domain_diagrams_project_id");
            assertConstraintExists(connection, "domain_diagrams", "fk_domain_diagrams_owner_id");
            assertConstraintExists(connection, "project_members", "uk_project_members_project_user");
            assertConstraintExists(connection, "project_members", "fk_project_members_project_id");
            assertConstraintExists(connection, "project_members", "fk_project_members_user_id");
            assertConstraintExists(connection, "project_invitations", "uk_project_invitations_token_hash");
            assertConstraintExists(connection, "project_invitations", "fk_project_invitations_project_id");
        }
    }

    @Test
    void shouldBackfillProjectOwnerMemberships() throws Exception {
        String url = h2Url("membership_backfill");
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        migrateTo(url, "6");

        try (Connection connection = connect(url)) {
            try (PreparedStatement userInsert = connection.prepareStatement("""
                    INSERT INTO application_users
                        (id, email, password_hash, first_name, last_name, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """)) {
                userInsert.setObject(1, userId);
                userInsert.setString(2, "backfill@example.com");
                userInsert.setString(3, "hash");
                userInsert.setString(4, "Back");
                userInsert.setString(5, "Fill");
                userInsert.executeUpdate();
            }
            try (PreparedStatement projectInsert = connection.prepareStatement("""
                    INSERT INTO projects
                        (id, owner_id, name, description, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """)) {
                projectInsert.setObject(1, projectId);
                projectInsert.setObject(2, userId);
                projectInsert.setString(3, "Backfilled");
                projectInsert.setString(4, "");
                projectInsert.executeUpdate();
            }
        }

        migrate(url);

        try (Connection connection = connect(url);
             PreparedStatement select = connection.prepareStatement("""
                     SELECT role
                     FROM project_members
                     WHERE project_id = ? AND user_id = ?
                     """)) {
            select.setObject(1, projectId);
            select.setObject(2, userId);
            try (ResultSet resultSet = select.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("OWNER", resultSet.getString("role"));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void shouldPreserveOldDomainDiagramRowsAndBackfillNewColumns() throws Exception {
        String url = h2Url("existing_schema");
        UUID diagramId = UUID.randomUUID();
        String plantUmlCode = "@startuml\nclass Preserved\n@enduml";

        try (Connection connection = connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE domain_diagrams (
                        id UUID NOT NULL PRIMARY KEY,
                        input_text TEXT NOT NULL,
                        diagram_type VARCHAR(255) NOT NULL,
                        plant_uml_code TEXT NOT NULL,
                        model_used VARCHAR(255),
                        created_at TIMESTAMP NOT NULL
                    )
                    """);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO domain_diagrams
                        (id, input_text, diagram_type, plant_uml_code, model_used, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """)) {
                insert.setObject(1, diagramId);
                insert.setString(2, "Generate a preserved class diagram");
                insert.setString(3, "CLASS");
                insert.setString(4, plantUmlCode);
                insert.setString(5, "legacy-model");
                insert.executeUpdate();
            }
        }

        migrate(url);

        try (Connection connection = connect(url);
             PreparedStatement select = connection.prepareStatement("""
                     SELECT plant_uml_code, current_source_code, source_format, original_prompt, updated_at,
                            current_version_number
                     FROM domain_diagrams
                     WHERE id = ?
                     """)) {
            select.setObject(1, diagramId);
            try (ResultSet resultSet = select.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(plantUmlCode, resultSet.getString("plant_uml_code"));
                assertEquals(plantUmlCode, resultSet.getString("current_source_code"));
                assertEquals("PLANTUML", resultSet.getString("source_format"));
                assertEquals("Generate a preserved class diagram", resultSet.getString("original_prompt"));
                assertTrue(resultSet.getObject("updated_at") != null);
                assertEquals(0, resultSet.getInt("current_version_number"));
                assertTrue(resultSet.wasNull());
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void shouldEnforceDiagramVersionUniquenessAndPositiveVersionNumber() throws Exception {
        String url = h2Url("constraints");
        UUID diagramId = UUID.randomUUID();
        UUID versionOneId = UUID.randomUUID();
        UUID duplicateVersionId = UUID.randomUUID();
        UUID invalidVersionId = UUID.randomUUID();

        migrate(url);

        try (Connection connection = connect(url);
             PreparedStatement diagramInsert = connection.prepareStatement("""
                     INSERT INTO domain_diagrams
                         (id, input_text, diagram_type, plant_uml_code, created_at, source_format,
                          current_source_code, original_prompt, updated_at)
                     VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, CURRENT_TIMESTAMP)
                     """)) {
            diagramInsert.setObject(1, diagramId);
            diagramInsert.setString(2, "Versioned diagram");
            diagramInsert.setString(3, "CLASS");
            diagramInsert.setString(4, "@startuml\nclass Versioned\n@enduml");
            diagramInsert.setString(5, "PLANTUML");
            diagramInsert.setString(6, "@startuml\nclass Versioned\n@enduml");
            diagramInsert.setString(7, "Versioned diagram");
            diagramInsert.executeUpdate();
        }

        insertDiagramVersion(url, versionOneId, diagramId, 1);

        SQLException duplicateFailure = assertThrows(SQLException.class,
                () -> insertDiagramVersion(url, duplicateVersionId, diagramId, 1));
        assertTrue(duplicateFailure.getMessage().contains("UK_DIAGRAM_VERSIONS_DIAGRAM_VERSION")
                || duplicateFailure.getMessage().contains("uk_diagram_versions_diagram_version"));

        SQLException positiveFailure = assertThrows(SQLException.class,
                () -> insertDiagramVersion(url, invalidVersionId, diagramId, 0));
        assertTrue(positiveFailure.getMessage().contains("CK_DIAGRAM_VERSIONS_VERSION_NUMBER_POSITIVE")
                || positiveFailure.getMessage().contains("ck_diagram_versions_version_number_positive"));
    }

    private static void migrate(String url) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private static void migrateTo(String url, String target) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target(target)
                .load()
                .migrate();
    }

    private static Connection connect(String url) throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static String h2Url(String databaseName) {
        return "jdbc:h2:mem:flyway_" + databaseName
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static void assertTableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1), "Missing table " + tableName);
            }
        }
    }

    private static void assertColumnExists(Connection connection, String tableName, String columnName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1), "Missing column " + tableName + "." + columnName);
            }
        }
    }

    private static void assertConstraintExists(Connection connection, String tableName, String constraintName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND constraint_name = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1), "Missing constraint " + constraintName);
            }
        }
    }

    private static void insertDiagramVersion(String url, UUID id, UUID diagramId, int versionNumber)
            throws SQLException {
        try (Connection connection = connect(url);
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO diagram_versions
                         (id, diagram_id, version_number, source_code, source_format, created_at,
                          change_type, version)
                     VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, 0)
                     """)) {
            insert.setObject(1, id);
            insert.setObject(2, diagramId);
            insert.setInt(3, versionNumber);
            insert.setString(4, "@startuml\nclass Version" + versionNumber + "\n@enduml");
            insert.setString(5, "PLANTUML");
            insert.setString(6, "GENERATED");
            insert.executeUpdate();
        }
    }
}
