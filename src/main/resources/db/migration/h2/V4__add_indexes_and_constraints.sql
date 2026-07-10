CREATE INDEX IF NOT EXISTS idx_projects_owner_id ON projects (owner_id);
CREATE INDEX IF NOT EXISTS idx_domain_diagrams_project_id ON domain_diagrams (project_id);
CREATE INDEX IF NOT EXISTS idx_domain_diagrams_owner_id ON domain_diagrams (owner_id);
CREATE INDEX IF NOT EXISTS idx_domain_diagrams_project_owner ON domain_diagrams (project_id, owner_id);
CREATE INDEX IF NOT EXISTS idx_diagram_versions_diagram_id ON diagram_versions (diagram_id);

ALTER TABLE projects
    ADD CONSTRAINT IF NOT EXISTS fk_projects_owner_id
    FOREIGN KEY (owner_id)
    REFERENCES application_users (id)
    ON DELETE RESTRICT;

ALTER TABLE domain_diagrams
    ADD CONSTRAINT IF NOT EXISTS fk_domain_diagrams_project_id
    FOREIGN KEY (project_id)
    REFERENCES projects (id)
    ON DELETE RESTRICT;

ALTER TABLE domain_diagrams
    ADD CONSTRAINT IF NOT EXISTS fk_domain_diagrams_owner_id
    FOREIGN KEY (owner_id)
    REFERENCES application_users (id)
    ON DELETE SET NULL;

ALTER TABLE diagram_versions
    ADD CONSTRAINT IF NOT EXISTS fk_diagram_versions_diagram_id
    FOREIGN KEY (diagram_id)
    REFERENCES domain_diagrams (id)
    ON DELETE CASCADE;

ALTER TABLE diagram_versions
    ADD CONSTRAINT IF NOT EXISTS fk_diagram_versions_created_by_id
    FOREIGN KEY (created_by_id)
    REFERENCES application_users (id)
    ON DELETE SET NULL;

ALTER TABLE diagram_versions
    ADD CONSTRAINT IF NOT EXISTS ck_diagram_versions_version_number_positive
    CHECK (version_number > 0);
