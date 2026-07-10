CREATE TABLE IF NOT EXISTS application_users (
    id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_application_users PRIMARY KEY (id),
    CONSTRAINT uk_application_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS projects (
    id UUID NOT NULL,
    owner_id UUID,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS diagram_versions (
    id UUID NOT NULL,
    diagram_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    prompt TEXT,
    source_code TEXT NOT NULL,
    source_format VARCHAR(32) NOT NULL,
    created_by_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    change_type VARCHAR(32),
    model_used VARCHAR(100),
    version BIGINT NOT NULL,
    CONSTRAINT pk_diagram_versions PRIMARY KEY (id),
    CONSTRAINT uk_diagram_versions_diagram_version UNIQUE (diagram_id, version_number)
);
