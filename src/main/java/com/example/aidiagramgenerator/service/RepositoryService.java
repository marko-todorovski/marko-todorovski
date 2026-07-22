package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.domain.RepositoryScan;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface RepositoryService {

    Repository createFromGithubUrl(UUID ownerId, String githubUrl);

    Repository createFromZipUpload(UUID ownerId, String displayName, MultipartFile file);

    List<Repository> listForOwner(UUID ownerId);

    Repository getForOwner(UUID repositoryId, UUID ownerId);

    void deleteForOwner(UUID repositoryId, UUID ownerId);

    RepositoryScan rescan(UUID repositoryId, UUID ownerId);

    List<RepositoryScan> listScans(UUID repositoryId, UUID ownerId);

    RepositoryScan getLatestScan(UUID repositoryId, UUID ownerId);
}
