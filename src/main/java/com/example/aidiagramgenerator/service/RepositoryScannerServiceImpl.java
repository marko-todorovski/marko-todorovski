package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.domain.RepositoryScan;
import com.example.aidiagramgenerator.repository.RepositoryRepository;
import com.example.aidiagramgenerator.repository.RepositoryScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RepositoryScannerServiceImpl implements RepositoryScannerService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryScannerServiceImpl.class);
    private static final int MAX_TOP_LEVEL_FOLDERS_STORED = 200;

    private final RepositoryRepository repositoryRepository;
    private final RepositoryScanRepository repositoryScanRepository;
    private final GitHubDownloadService gitHubDownloadService;
    private final ZipExtractionService zipExtractionService;
    private final LanguageDetectionService languageDetectionService;

    public RepositoryScannerServiceImpl(
            RepositoryRepository repositoryRepository,
            RepositoryScanRepository repositoryScanRepository,
            GitHubDownloadService gitHubDownloadService,
            ZipExtractionService zipExtractionService,
            LanguageDetectionService languageDetectionService) {
        this.repositoryRepository = repositoryRepository;
        this.repositoryScanRepository = repositoryScanRepository;
        this.gitHubDownloadService = gitHubDownloadService;
        this.zipExtractionService = zipExtractionService;
        this.languageDetectionService = languageDetectionService;
    }

    @Override
    @Transactional
    public RepositoryScan scanGithub(Repository repository) {
        RepositoryScan scan = beginScan(repository);
        try {
            GitHubDownloadService.DownloadResult download = gitHubDownloadService.download(repository.getSourceUrl());
            ZipExtractionService.ScanResult result;
            try (InputStream zipStream = download.zipStream()) {
                result = zipExtractionService.scanStream(zipStream);
            }
            LanguageDetectionService.DetectionResult detection =
                    languageDetectionService.detect(result.extensionCounts(), result.markerFiles());
            completeScan(repository, scan, result, detection, download.branch(), download.commitHash());
        } catch (Exception e) {
            failScan(repository, scan, e);
        }
        return repositoryScanRepository.save(scan);
    }

    @Override
    @Transactional
    public RepositoryScan scanZip(Repository repository, InputStream zipStream) {
        RepositoryScan scan = beginScan(repository);
        try {
            ZipExtractionService.ScanResult result = zipExtractionService.scanStream(zipStream);
            LanguageDetectionService.DetectionResult detection =
                    languageDetectionService.detect(result.extensionCounts(), result.markerFiles());
            completeScan(repository, scan, result, detection, null, null);
        } catch (Exception e) {
            failScan(repository, scan, e);
        }
        return repositoryScanRepository.save(scan);
    }

    private RepositoryScan beginScan(Repository repository) {
        repository.markScanning();
        repositoryRepository.save(repository);
        RepositoryScan scan = new RepositoryScan(repository);
        return repositoryScanRepository.save(scan);
    }

    private void completeScan(
            Repository repository,
            RepositoryScan scan,
            ZipExtractionService.ScanResult result,
            LanguageDetectionService.DetectionResult detection,
            String branch,
            String commitHash) {
        String projectName = detection.projectName() != null ? detection.projectName() : repository.getName();
        String topLevelFolders = joinTopLevelFolders(result.topLevelFolders());
        scan.complete(
                projectName,
                detection.primaryLanguage(),
                detection.framework(),
                result.fileCount(),
                result.folderCount(),
                topLevelFolders,
                branch,
                commitHash);
        Instant now = Instant.now();
        repository.markReady(now);
        repositoryRepository.save(repository);
    }

    private void failScan(Repository repository, RepositoryScan scan, Exception e) {
        log.warn("Repository scan failed for repository {}: {}", repository.getId(), e.getMessage());
        scan.fail(e.getMessage() != null ? e.getMessage() : "Scan failed");
        repository.markFailed(Instant.now());
        repositoryRepository.save(repository);
    }

    private static String joinTopLevelFolders(java.util.Set<String> folders) {
        List<String> limited = folders.stream().limit(MAX_TOP_LEVEL_FOLDERS_STORED).collect(Collectors.toList());
        String joined = String.join(",", limited);
        return joined.length() > 4000 ? joined.substring(0, 4000) : joined;
    }
}
