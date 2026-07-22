package com.example.aidiagramgenerator.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "project_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_project_members_project_user", columnNames = {"project_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_project_members_project_id", columnList = "project_id"),
                @Index(name = "idx_project_members_user_id", columnList = "user_id")
        }
)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private ApplicationUser user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private ApplicationUser invitedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProjectMember() {
    }

    public ProjectMember(Project project, ApplicationUser user, ProjectRole role) {
        this.project = project;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.joinedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean canView() {
        return role != null && role.canView();
    }

    public boolean canEdit() {
        return role != null && role.canEdit();
    }

    public boolean canManageMembers() {
        return role != null && role.canManageMembers();
    }

    public boolean canDeleteProject() {
        return role != null && role.canDeleteProject();
    }

    public void changeRole(ProjectRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Project role is required");
        }
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public ApplicationUser getUser() {
        return user;
    }

    public ProjectRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public ApplicationUser getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(ApplicationUser invitedBy) {
        this.invitedBy = invitedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectMember that = (ProjectMember) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
