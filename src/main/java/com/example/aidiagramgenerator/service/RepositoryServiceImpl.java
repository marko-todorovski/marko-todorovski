package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.domain.RepositoryScan;
import com.example.aidiagramgenerator.domain.RepositorySourceType;
import com.example.aidiagramgenerator.exception.RepositoryNotFoundException;
import com.example.aidiagramgenerator.exception.RepositoryValidationException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.RepositoryRepository;
import com.example.aidiagramgenerator.repository.RepositoryScanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class RepositoryServiceImpl implements RepositoryService {

    private static final int MAX_NAME_LENGTH = 255;

    private final ApplicationUserRepository userRepository;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryScanRepository repositoryScanRepository;
    private final RepositoryScannerService repositoryScannerService;
    private final GitHubDownloadService gitHubDownloadService;
    private final long maxArchiveBytes;

    public RepositoryServiceImpl(
            ApplicationUserRepository userRepository,
            RepositoryRepository repositoryRepository,
            RepositoryScanRepository repositoryScanRepository,
            RepositoryScannerService repositoryScannerService,
            GitHubDownloadService gitHubDownloadService,
            @Value("${app.repository.max-archive-bytes:262144000}") long maxArchiveBytes) {
        this.userRepository = userRepository;
        this.repositoryRepository = repositoryRepository;
        this.repositoryScanRepository = repositoryScanRepository;
        this.repositoryScannerService = repositoryScannerService;
        this.gitHubDownloadService = gitHubDownloadService;
        this.maxArchiveBytes = maxArchiveBytes;
    }

    @Override
    @Transactional
    public Repository createFromGithubUrl(UUID ownerId, String githubUrl) {
        ApplicationUser owner = requireOwner(ownerId);
        GitHubDownloadService.RepoRef ref = gitHubDownloadService.parseRepoUrl(githubUrl);

        Repository repository = new Repository(owner, ref.repo(), RepositorySourceType.GITHUB_URL);
        repository.setSourceUrl(githubUrl.trim());
        repository = repositoryRepository.saveAndFlush(repository);

        repositoryScannerService.scanGithub(repository);
        return repositoryRepository.findById(repository.getId()).orElseThrow(
                () -> new RepositoryNotFoundException("Repository not found"));
    }

    @Override
    @Transactional
    public Repository createFromZipUpload(UUID ownerId, String displayName, MultipartFile file) {
        ApplicationUser owner = requireOwner(ownerId);
        if (file == null || file.isEmpty()) {
            throw new RepositoryValidationException("A ZIP file is required");
        }
        if (file.getSize() > maxArchiveBytes) {
            throw new RepositoryValidationException("Uploaded archive exceeds the maximum allowed size of " + maxArchiveBytes + " bytes");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new RepositoryValidationException("Only .zip archives are supported");
        }
        String name = normalizeName(displayName != null && !displayName.isBlank() ? displayName : stripZipExtension(originalFilename));

        Repository repository = new Repository(owner, name, RepositorySourceType.ZIP_UPLOAD);
        repository.setOriginalFilename(originalFilename);
        repository = repositoryRepository.saveAndFlush(repository);

        try (InputStream in = file.getInputStream()) {
            repositoryScannerService.scanZip(repository, in);
        } catch (IOException e) {
            throw new RepositoryValidationException("Uploaded file could not be read");
        }
        return repositoryRepository.findById(repository.getId()).orElseThrow(
                () -> new RepositoryNotFoundException("Repository not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Repository> listForOwner(UUID ownerId) {
        return repositoryRepository.findAllByOwnerIdOrderByCreatedAtDesc(requireId(ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Repository getForOwner(UUID repositoryId, UUID ownerId) {
        return repositoryRepository.findByIdAndOwnerId(requireId(repositoryId), requireId(ownerId))
                .orElseThrow(() -> new RepositoryNotFoundException("Repository not found"));
    }

    @Override
    @Transactional
    public void deleteForOwner(UUID repositoryId, UUID ownerId) {
        Repository repository = getForOwner(repositoryId, ownerId);
        repositoryRepository.delete(repository);
    }

    @Override
    @Transactional
    public RepositoryScan rescan(UUID repositoryId, UUID ownerId) {
        Repository repository = getForOwner(repositoryId, ownerId);
        if (repository.getSourceType() != RepositorySourceType.GITHUB_URL) {
            throw new RepositoryValidationException(
                    "Refresh is only supported for GitHub-imported repositories; re-upload a new ZIP to rescan");
        }
        return repositoryScannerService.scanGithub(repository);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryScan> listScans(UUID repositoryId, UUID ownerId) {
        getForOwner(repositoryId, ownerId);
        return repositoryScanRepository.findAllByRepository_IdOrderByStartedAtDesc(repositoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryScan getLatestScan(UUID repositoryId, UUID ownerId) {
        getForOwner(repositoryId, ownerId);
        return repositoryScanRepository.findTopByRepository_IdOrderByStartedAtDesc(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException("No scan has been run for this repository yet"));
    }

    private ApplicationUser requireOwner(UUID ownerId) {
        return userRepository.findById(requireId(ownerId))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + ownerId));
    }

    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new RepositoryValidationException("Owner ID is required");
        }
        return id;
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RepositoryValidationException("Repository name is required");
        }
        String normalized = name.trim();
        return normalized.length() > MAX_NAME_LENGTH ? normalized.substring(0, MAX_NAME_LENGTH) : normalized;
    }

    private static String stripZipExtension(String filename) {
        return filename.toLowerCase().endsWith(".zip") ? filename.substring(0, filename.length() - 4) : filename;
    }
}
