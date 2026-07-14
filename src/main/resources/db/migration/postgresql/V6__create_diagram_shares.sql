CREATE TABLE IF NOT EXISTS diagram_shares (
    id UUID NOT NULL,
    diagram_id UUID NOT NULL,
    diagram_version_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    access_count BIGINT NOT NULL DEFAULT 0,
    allow_downloads BOOLEAN NOT NULL DEFAULT TRUE,
    title_override VARCHAR(150),
    description_override TEXT,
    version BIGINT NOT NULL,
    CONSTRAINT pk_diagram_shares PRIMARY KEY (id),
    CONSTRAINT uk_diagram_shares_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_diagram_shares_diagram_id
        FOREIGN KEY (diagram_id)
        REFERENCES domain_diagrams (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_diagram_shares_diagram_version_id
        FOREIGN KEY (diagram_version_id)
        REFERENCES diagram_versions (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_diagram_shares_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_diagram_shares_access_count_non_negative
        CHECK (access_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_diagram_shares_diagram_id ON diagram_shares (diagram_id);
CREATE INDEX IF NOT EXISTS idx_diagram_shares_diagram_version_id ON diagram_shares (diagram_version_id);
CREATE INDEX IF NOT EXISTS idx_diagram_shares_status ON diagram_shares (status);
CREATE INDEX IF NOT EXISTS idx_diagram_shares_expires_at ON diagram_shares (expires_at);
CREATE INDEX IF NOT EXISTS idx_diagram_shares_diagram_status_created ON diagram_shares (diagram_id, status, created_at);
