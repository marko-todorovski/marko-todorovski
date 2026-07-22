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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single scan attempt of a {@link Repository}, capturing best-effort structural metadata only.
 */
@Entity
@Table(
        name = "repository_scans",
        indexes = {
                @Index(name = "idx_repository_scans_repository_id", columnList = "repository_id"),
                @Index(name = "idx_repository_scans_repository_started", columnList = "repository_id, started_at")
        }
)
public class RepositoryScan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScanStatus status;

    @Size(max = 255)
    @Column(name = "project_name", length = 255)
    private String projectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_language", length = 32)
    private RepositoryLanguage primaryLanguage;

    @Size(max = 100)
    @Column(length = 100)
    private String framework;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "folder_count")
    private Integer folderCount;

    @Size(max = 4000)
    @Column(name = "top_level_folders", length = 4000)
    private String topLevelFolders;

    @Size(max = 255)
    @Column(length = 255)
    private String branch;

    @Size(max = 64)
    @Column(name = "commit_hash", length = 64)
    private String commitHash;

    @Size(max = 2000)
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected RepositoryScan() {
    }

    public RepositoryScan(Repository repository) {
        this.repository = repository;
        this.status = ScanStatus.IN_PROGRESS;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.startedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void complete(
            String projectName,
            RepositoryLanguage primaryLanguage,
            String framework,
            int fileCount,
            int folderCount,
            String topLevelFolders,
            String branch,
            String commitHash) {
        this.status = ScanStatus.COMPLETED;
        this.projectName = projectName;
        this.primaryLanguage = primaryLanguage;
        this.framework = framework;
        this.fileCount = fileCount;
        this.folderCount = folderCount;
        this.topLevelFolders = topLevelFolders;
        this.branch = branch;
        this.commitHash = commitHash;
        this.completedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = ScanStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Repository getRepository() {
        return repository;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public String getProjectName() {
        return projectName;
    }

    public RepositoryLanguage getPrimaryLanguage() {
        return primaryLanguage;
    }

    public String getFramework() {
        return framework;
    }

    public Integer getFileCount() {
        return fileCount;
    }

    public Integer getFolderCount() {
        return folderCount;
    }

    public String getTopLevelFolders() {
        return topLevelFolders;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
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
        RepositoryScan that = (RepositoryScan) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
