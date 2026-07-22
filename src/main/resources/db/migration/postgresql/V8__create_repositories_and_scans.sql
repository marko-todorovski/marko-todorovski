CREATE TABLE repositories (
    id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_url VARCHAR(2048),
    original_filename VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    last_scanned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_repositories PRIMARY KEY (id),
    CONSTRAINT fk_repositories_owner_id
        FOREIGN KEY (owner_id)
        REFERENCES application_users (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_repositories_source_type
        CHECK (source_type IN ('GITHUB_URL', 'ZIP_UPLOAD')),
    CONSTRAINT ck_repositories_status
        CHECK (status IN ('PENDING', 'SCANNING', 'READY', 'FAILED'))
);

CREATE INDEX idx_repositories_owner_id ON repositories (owner_id);

CREATE TABLE repository_scans (
    id UUID NOT NULL,
    repository_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    project_name VARCHAR(255),
    primary_language VARCHAR(32),
    framework VARCHAR(100),
    file_count INTEGER,
    folder_count INTEGER,
    top_level_folders VARCHAR(4000),
    branch VARCHAR(255),
    commit_hash VARCHAR(64),
    error_message VARCHAR(2000),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_repository_scans PRIMARY KEY (id),
    CONSTRAINT fk_repository_scans_repository_id
        FOREIGN KEY (repository_id)
        REFERENCES repositories (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_repository_scans_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_repository_scans_primary_language
        CHECK (primary_language IS NULL OR primary_language IN (
            'JAVA', 'KOTLIN', 'TYPESCRIPT', 'JAVASCRIPT', 'PYTHON', 'CSHARP', 'GO', 'RUBY', 'PHP', 'CPP', 'C', 'UNKNOWN'))
);

CREATE INDEX idx_repository_scans_repository_id ON repository_scans (repository_id);
CREATE INDEX idx_repository_scans_repository_started ON repository_scans (repository_id, started_at);
