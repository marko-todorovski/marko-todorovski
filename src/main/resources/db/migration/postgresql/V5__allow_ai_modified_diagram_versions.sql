ALTER TABLE diagram_versions
    DROP CONSTRAINT IF EXISTS ck_diagram_versions_change_type;

ALTER TABLE diagram_versions
    ADD CONSTRAINT ck_diagram_versions_change_type
        CHECK (change_type IS NULL OR change_type IN ('GENERATED', 'EDITED', 'RESTORED', 'REPAIRED', 'AI_MODIFIED'));
