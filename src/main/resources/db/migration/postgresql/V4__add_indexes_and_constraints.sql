CREATE INDEX IF NOT EXISTS idx_projects_owner_id ON projects (owner_id);
CREATE INDEX IF NOT EXISTS idx_domain_diagrams_project_id ON domain_diagrams (project_id);
CREATE INDEX IF NOT EXISTS idx_domain_diagrams_owner_id ON domain_diagrams (owner_id);
CREATE INDEX IF NOT EXISTS idx_domain_diagrams_project_owner ON domain_diagrams (project_id, owner_id);
CREATE INDEX IF NOT EXISTS idx_diagram_versions_diagram_id ON diagram_versions (diagram_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'projects'
          AND constraint_name = 'fk_projects_owner_id'
    ) THEN
        ALTER TABLE projects
            ADD CONSTRAINT fk_projects_owner_id
            FOREIGN KEY (owner_id)
            REFERENCES application_users (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'domain_diagrams'
          AND constraint_name = 'fk_domain_diagrams_project_id'
    ) THEN
        ALTER TABLE domain_diagrams
            ADD CONSTRAINT fk_domain_diagrams_project_id
            FOREIGN KEY (project_id)
            REFERENCES projects (id)
            ON DELETE RESTRICT;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'domain_diagrams'
          AND constraint_name = 'fk_domain_diagrams_owner_id'
    ) THEN
        ALTER TABLE domain_diagrams
            ADD CONSTRAINT fk_domain_diagrams_owner_id
            FOREIGN KEY (owner_id)
            REFERENCES application_users (id)
            ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'diagram_versions'
          AND constraint_name = 'fk_diagram_versions_diagram_id'
    ) THEN
        ALTER TABLE diagram_versions
            ADD CONSTRAINT fk_diagram_versions_diagram_id
            FOREIGN KEY (diagram_id)
            REFERENCES domain_diagrams (id)
            ON DELETE CASCADE;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'diagram_versions'
          AND constraint_name = 'fk_diagram_versions_created_by_id'
    ) THEN
        ALTER TABLE diagram_versions
            ADD CONSTRAINT fk_diagram_versions_created_by_id
            FOREIGN KEY (created_by_id)
            REFERENCES application_users (id)
            ON DELETE SET NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'diagram_versions'
          AND constraint_name = 'ck_diagram_versions_version_number_positive'
    ) THEN
        ALTER TABLE diagram_versions
            ADD CONSTRAINT ck_diagram_versions_version_number_positive
            CHECK (version_number > 0);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'diagram_versions'
          AND constraint_name = 'ck_diagram_versions_source_format'
    ) THEN
        ALTER TABLE diagram_versions
            ADD CONSTRAINT ck_diagram_versions_source_format
            CHECK (source_format IN ('PLANTUML', 'MERMAID'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'diagram_versions'
          AND constraint_name = 'ck_diagram_versions_change_type'
    ) THEN
        ALTER TABLE diagram_versions
            ADD CONSTRAINT ck_diagram_versions_change_type
            CHECK (change_type IS NULL OR change_type IN ('GENERATED', 'EDITED', 'RESTORED', 'REPAIRED'));
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'domain_diagrams'
          AND constraint_name = 'ck_domain_diagrams_source_format'
    ) THEN
        ALTER TABLE domain_diagrams
            ADD CONSTRAINT ck_domain_diagrams_source_format
            CHECK (source_format IS NULL OR source_format IN ('PLANTUML', 'MERMAID'));
    END IF;
END $$;
