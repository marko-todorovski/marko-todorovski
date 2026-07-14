package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.dto.request.AttachDiagramRequest;
import com.example.aidiagramgenerator.dto.request.CreateProjectRequest;
import com.example.aidiagramgenerator.dto.request.SaveDiagramRequest;
import com.example.aidiagramgenerator.dto.request.UpdateProjectRequest;
import com.example.aidiagramgenerator.dto.response.DiagramSummaryResponse;
import com.example.aidiagramgenerator.dto.response.ProjectResponse;
import com.example.aidiagramgenerator.dto.response.ProjectSummaryResponse;
import com.example.aidiagramgenerator.dto.response.WorkspaceDiagramResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.ProjectService;
import com.example.aidiagramgenerator.service.SavedDiagramService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final CurrentUser currentUser;
    private final ProjectService projectService;
    private final SavedDiagramService savedDiagramService;

    public ProjectController(
            CurrentUser currentUser,
            ProjectService projectService,
            SavedDiagramService savedDiagramService) {
        this.currentUser = currentUser;
        this.projectService = projectService;
        this.savedDiagramService = savedDiagramService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Project project = projectService.createProject(ownerId, request.name(), request.description());
        ProjectResponse response = ProjectResponse.from(project, 0);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId())).body(response);
    }

    @GetMapping
    public List<ProjectSummaryResponse> listProjects() {
        UUID ownerId = currentUser.requireCurrentUserId();
        List<Project> projects = projectService.getProjectsForOwner(ownerId);
        Map<UUID, Long> diagramCounts = projectService.countProjectDiagramsForOwner(ownerId);
        return projects.stream()
                .map(project -> ProjectSummaryResponse.from(project, diagramCounts.getOrDefault(project.getId(), 0L)))
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Project project = projectService.getProjectForOwner(projectId, ownerId);
        return ProjectResponse.from(project, projectService.countProjectDiagrams(projectId, ownerId));
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Project project = projectService.updateProject(projectId, ownerId, request.name(), request.description());
        return ProjectResponse.from(project, projectService.countProjectDiagrams(projectId, ownerId));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        projectService.deleteProject(projectId, ownerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/diagrams")
    public List<DiagramSummaryResponse> listProjectDiagrams(@PathVariable UUID projectId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return savedDiagramService.getProjectDiagrams(projectId, ownerId).stream()
                .map(DiagramSummaryResponse::from)
                .toList();
    }

    @PostMapping("/{projectId}/diagrams")
    public ResponseEntity<WorkspaceDiagramResponse> saveGeneratedDiagram(
            @PathVariable UUID projectId,
            @Valid @RequestBody SaveDiagramRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Diagram diagram = savedDiagramService.saveGeneratedDiagram(
                ownerId,
                projectId,
                request.name(),
                request.description(),
                request.originalPrompt(),
                request.diagramType(),
                request.sourceFormat(),
                request.sourceCode(),
                request.modelUsed());
        return ResponseEntity.created(URI.create("/api/workspace/diagrams/" + diagram.getId()))
                .body(WorkspaceDiagramResponse.from(diagram));
    }

    @PostMapping("/{projectId}/diagrams/{diagramId}/attach")
    public WorkspaceDiagramResponse attachExistingGeneratedDiagram(
            @PathVariable UUID projectId,
            @PathVariable UUID diagramId,
            @Valid @RequestBody AttachDiagramRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        return WorkspaceDiagramResponse.from(savedDiagramService.attachExistingGeneratedDiagram(
                ownerId,
                projectId,
                diagramId,
                request.name(),
                request.description()));
    }
}
