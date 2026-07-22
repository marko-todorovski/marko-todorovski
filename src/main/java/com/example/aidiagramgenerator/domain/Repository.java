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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A repository imported for structural analysis (GitHub URL or ZIP upload).
 */
@Entity
@Table(
        name = "repositories",
        indexes = {
                @Index(name = "idx_repositories_owner_id", columnList = "owner_id")
        }
)
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private ApplicationUser owner;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private RepositorySourceType sourceType;

    @Size(max = 2048)
    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Size(max = 512)
    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepositoryStatus status;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Repository() {
    }

    public Repository(ApplicationUser owner, String name, RepositorySourceType sourceType) {
        this.owner = owner;
        this.name = name;
        this.sourceType = sourceType;
        this.status = RepositoryStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markScanning() {
        this.status = RepositoryStatus.SCANNING;
    }

    public void markReady(Instant scannedAt) {
        this.status = RepositoryStatus.READY;
        this.lastScannedAt = scannedAt;
    }

    public void markFailed(Instant scannedAt) {
        this.status = RepositoryStatus.FAILED;
        this.lastScannedAt = scannedAt;
    }

    public UUID getId() {
        return id;
    }

    public ApplicationUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public RepositorySourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public RepositoryStatus getStatus() {
        return status;
    }

    public Instant getLastScannedAt() {
        return lastScannedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
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
        Repository that = (Repository) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
