CREATE TABLE project_members (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invited_by_user_id UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project_id
        FOREIGN KEY (project_id)
        REFERENCES projects (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user_id
        FOREIGN KEY (user_id)
        REFERENCES application_users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_project_members_invited_by_user_id
        FOREIGN KEY (invited_by_user_id)
        REFERENCES application_users (id)
        ON DELETE SET NULL,
    CONSTRAINT ck_project_members_role
        CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER'))
);

CREATE INDEX idx_project_members_project_id ON project_members (project_id);
CREATE INDEX idx_project_members_user_id ON project_members (user_id);

INSERT INTO project_members
    (id, project_id, user_id, role, joined_at, invited_by_user_id, updated_at, version)
SELECT gen_random_uuid(), p.id, p.owner_id, 'OWNER',
       COALESCE(p.created_at, CURRENT_TIMESTAMP), NULL, COALESCE(p.updated_at, CURRENT_TIMESTAMP), 0
FROM projects p
WHERE p.owner_id IS NOT NULL;

CREATE TABLE project_invitations (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    invited_email VARCHAR(320) NOT NULL,
    role VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    invited_by_user_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    rejected_at TIMESTAMP WITH TIME ZONE,
    accepted_by_user_id UUID,
    version BIGINT NOT NULL,
    CONSTRAINT pk_project_invitations PRIMARY KEY (id),
    CONSTRAINT uk_project_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_project_invitations_project_id
        FOREIGN KEY (project_id)
        REFERENCES projects (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_project_invitations_invited_by_user_id
        FOREIGN KEY (invited_by_user_id)
        REFERENCES application_users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_project_invitations_accepted_by_user_id
        FOREIGN KEY (accepted_by_user_id)
        REFERENCES application_users (id)
        ON DELETE SET NULL,
    CONSTRAINT ck_project_invitations_role
        CHECK (role IN ('EDITOR', 'VIEWER')),
    CONSTRAINT ck_project_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED'))
);

CREATE INDEX idx_project_invitations_project_id ON project_invitations (project_id);
CREATE INDEX idx_project_invitations_invited_email ON project_invitations (invited_email);
CREATE INDEX idx_project_invitations_status ON project_invitations (status);
CREATE INDEX idx_project_invitations_expires_at ON project_invitations (expires_at);
