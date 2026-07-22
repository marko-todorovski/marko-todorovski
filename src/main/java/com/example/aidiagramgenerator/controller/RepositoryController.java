package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.dto.request.CreateRepositoryRequest;
import com.example.aidiagramgenerator.dto.response.RepositoryResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.RepositoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {

    private final CurrentUser currentUser;
    private final RepositoryService repositoryService;

    public RepositoryController(CurrentUser currentUser, RepositoryService repositoryService) {
        this.currentUser = currentUser;
        this.repositoryService = repositoryService;
    }

    @PostMapping("/github")
    public ResponseEntity<RepositoryResponse> importFromGithub(@Valid @RequestBody CreateRepositoryRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Repository repository = repositoryService.createFromGithubUrl(ownerId, request.githubUrl());
        return ResponseEntity.created(URI.create("/api/repositories/" + repository.getId()))
                .body(RepositoryResponse.from(repository));
    }

    @PostMapping(path = "/zip")
    public ResponseEntity<RepositoryResponse> importFromZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "name", required = false) String name) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Repository repository = repositoryService.createFromZipUpload(ownerId, name, file);
        return ResponseEntity.created(URI.create("/api/repositories/" + repository.getId()))
                .body(RepositoryResponse.from(repository));
    }

    @GetMapping
    public List<RepositoryResponse> listRepositories() {
        UUID ownerId = currentUser.requireCurrentUserId();
        return repositoryService.listForOwner(ownerId).stream()
                .map(RepositoryResponse::from)
                .toList();
    }

    @GetMapping("/{repositoryId}")
    public RepositoryResponse getRepository(@PathVariable UUID repositoryId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return RepositoryResponse.from(repositoryService.getForOwner(repositoryId, ownerId));
    }

    @DeleteMapping("/{repositoryId}")
    public ResponseEntity<Void> deleteRepository(@PathVariable UUID repositoryId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        repositoryService.deleteForOwner(repositoryId, ownerId);
        return ResponseEntity.noContent().build();
    }
}
