package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.RepositoryScan;
import com.example.aidiagramgenerator.dto.response.RepositoryScanResponse;
import com.example.aidiagramgenerator.dto.response.ScanProgressResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.RepositoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/scans")
public class RepositoryScanController {

    private final CurrentUser currentUser;
    private final RepositoryService repositoryService;

    public RepositoryScanController(CurrentUser currentUser, RepositoryService repositoryService) {
        this.currentUser = currentUser;
        this.repositoryService = repositoryService;
    }

    @GetMapping
    public List<RepositoryScanResponse> listScans(@PathVariable UUID repositoryId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return repositoryService.listScans(repositoryId, ownerId).stream()
                .map(scan -> RepositoryScanResponse.from(scan, repositoryId))
                .toList();
    }

    @GetMapping("/latest")
    public ScanProgressResponse getLatestScan(@PathVariable UUID repositoryId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        RepositoryScan scan = repositoryService.getLatestScan(repositoryId, ownerId);
        return ScanProgressResponse.from(scan, repositoryId);
    }

    @PostMapping
    public RepositoryScanResponse refreshScan(@PathVariable UUID repositoryId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        RepositoryScan scan = repositoryService.rescan(repositoryId, ownerId);
        return RepositoryScanResponse.from(scan, repositoryId);
    }
}
