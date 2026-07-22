package com.example.aidiagramgenerator.domain;

public enum ProjectRole {
    OWNER,
    EDITOR,
    VIEWER;

    public boolean canView() {
        return true;
    }

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    public boolean canManageMembers() {
        return this == OWNER;
    }

    public boolean canDeleteProject() {
        return this == OWNER;
    }

    public boolean canManageShares() {
        return this == OWNER;
    }
}
