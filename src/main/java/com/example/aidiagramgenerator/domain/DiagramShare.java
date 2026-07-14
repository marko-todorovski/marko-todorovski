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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "diagram_shares",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_diagram_shares_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_diagram_shares_diagram_id", columnList = "diagram_id"),
                @Index(name = "idx_diagram_shares_diagram_version_id", columnList = "diagram_version_id"),
                @Index(name = "idx_diagram_shares_status", columnList = "status"),
                @Index(name = "idx_diagram_shares_expires_at", columnList = "expires_at"),
                @Index(name = "idx_diagram_shares_diagram_status_created", columnList = "diagram_id, status, created_at")
        }
)
public class DiagramShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_id", nullable = false)
    private Diagram diagram;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagram_version_id", nullable = false)
    private DiagramVersion diagramVersion;

    @JsonIgnore
    @NotBlank
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DiagramShareStatus status = DiagramShareStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "access_count", nullable = false)
    private long accessCount;

    @Column(name = "allow_downloads", nullable = false)
    private boolean allowDownloads = true;

    @Size(max = 150)
    @Column(name = "title_override", length = 150)
    private String titleOverride;

    @Size(max = 1000)
    @Column(name = "description_override", columnDefinition = "TEXT")
    private String descriptionOverride;

    @Version
    @Column(nullable = false)
    private Long version;

    protected DiagramShare() {
    }

    public DiagramShare(Diagram diagram, DiagramVersion diagramVersion, String tokenHash) {
        this.diagram = diagram;
        this.diagramVersion = diagramVersion;
        this.tokenHash = tokenHash;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = DiagramShareStatus.ACTIVE;
        }
    }

    public boolean isRevoked() {
        return status == DiagramShareStatus.REVOKED;
    }

    public boolean isExpired(Clock clock) {
        return expiresAt != null && !expiresAt.isAfter(Instant.now(clock));
    }

    public boolean isAccessible(Clock clock) {
        return status == DiagramShareStatus.ACTIVE && !isExpired(clock);
    }

    public void revoke(Instant now) {
        if (status != DiagramShareStatus.REVOKED) {
            status = DiagramShareStatus.REVOKED;
            revokedAt = now;
        }
    }

    public void recordAccess(Instant now) {
        accessCount++;
        lastAccessedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    public void setDiagram(Diagram diagram) {
        this.diagram = diagram;
    }

    public DiagramVersion getDiagramVersion() {
        return diagramVersion;
    }

    public void setDiagramVersion(DiagramVersion diagramVersion) {
        this.diagramVersion = diagramVersion;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public DiagramShareStatus getStatus() {
        return status;
    }

    public void setStatus(DiagramShareStatus status) {
        this.status = status == null ? DiagramShareStatus.ACTIVE : status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public long getAccessCount() {
        return accessCount;
    }

    public boolean isAllowDownloads() {
        return allowDownloads;
    }

    public void setAllowDownloads(boolean allowDownloads) {
        this.allowDownloads = allowDownloads;
    }

    public String getTitleOverride() {
        return titleOverride;
    }

    public void setTitleOverride(String titleOverride) {
        this.titleOverride = trimToNull(titleOverride);
    }

    public String getDescriptionOverride() {
        return descriptionOverride;
    }

    public void setDescriptionOverride(String descriptionOverride) {
        this.descriptionOverride = trimToNull(descriptionOverride);
    }

    public Long getVersion() {
        return version;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiagramShare that = (DiagramShare) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
