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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "project_invitations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_project_invitations_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_project_invitations_project_id", columnList = "project_id"),
                @Index(name = "idx_project_invitations_invited_email", columnList = "invited_email"),
                @Index(name = "idx_project_invitations_status", columnList = "status"),
                @Index(name = "idx_project_invitations_expires_at", columnList = "expires_at")
        }
)
public class ProjectInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Email
    @Size(max = 320)
    @Column(name = "invited_email", nullable = false, length = 320)
    private String invitedEmail;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectRole role;

    @JsonIgnore
    @NotBlank
    @Size(max = 64)
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProjectInvitationStatus status = ProjectInvitationStatus.PENDING;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private ApplicationUser invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by_user_id")
    private ApplicationUser acceptedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProjectInvitation() {
    }

    public ProjectInvitation(Project project, String invitedEmail, ProjectRole role, String tokenHash,
                             ApplicationUser invitedBy, Instant expiresAt) {
        this.project = project;
        this.invitedEmail = normalizeEmail(invitedEmail);
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        this.invitedEmail = normalizeEmail(invitedEmail);
        this.createdAt = Instant.now();
        if (status == null) {
            status = ProjectInvitationStatus.PENDING;
        }
    }

    public boolean isPending() {
        return status == ProjectInvitationStatus.PENDING;
    }

    public boolean isExpired(Clock clock) {
        return expiresAt != null && !expiresAt.isAfter(Instant.now(clock));
    }

    public boolean isAcceptable(Clock clock) {
        return isPending() && !isExpired(clock);
    }

    public void accept(ApplicationUser user, Instant now) {
        this.status = ProjectInvitationStatus.ACCEPTED;
        this.acceptedBy = user;
        this.acceptedAt = now;
    }

    public void reject(Instant now) {
        this.status = ProjectInvitationStatus.REJECTED;
        this.rejectedAt = now;
    }

    public void revoke(Instant now) {
        this.status = ProjectInvitationStatus.REVOKED;
        this.rejectedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getInvitedEmail() {
        return invitedEmail;
    }

    public ProjectRole getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public ProjectInvitationStatus getStatus() {
        return status;
    }

    public ApplicationUser getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public ApplicationUser getAcceptedBy() {
        return acceptedBy;
    }

    public Long getVersion() {
        return version;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectInvitation that = (ProjectInvitation) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
