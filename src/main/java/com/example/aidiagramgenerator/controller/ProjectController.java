package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.domain.ProjectRole;
import com.example.aidiagramgenerator.dto.request.AttachDiagramRequest;
import com.example.aidiagramgenerator.dto.request.CreateProjectRequest;
import com.example.aidiagramgenerator.dto.request.SaveDiagramRequest;
import com.example.aidiagramgenerator.dto.request.UpdateProjectRequest;
import com.example.aidiagramgenerator.dto.response.DiagramSummaryResponse;
import com.example.aidiagramgenerator.dto.response.ProjectResponse;
import com.example.aidiagramgenerator.dto.response.ProjectSummaryResponse;
import com.example.aidiagramgenerator.dto.response.WorkspaceDiagramResponse;
import com.example.aidiagramgenerator.security.CurrentUser;
import com.example.aidiagramgenerator.service.ProjectAccessService;
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
    private final ProjectAccessService projectAccessService;

    public ProjectController(
            CurrentUser currentUser,
            ProjectService projectService,
            SavedDiagramService savedDiagramService,
            ProjectAccessService projectAccessService) {
        this.currentUser = currentUser;
        this.projectService = projectService;
        this.savedDiagramService = savedDiagramService;
        this.projectAccessService = projectAccessService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Project project = projectService.createProject(ownerId, request.name(), request.description());
        ProjectResponse response = ProjectResponse.from(project, 0, ProjectRole.OWNER, 1);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId())).body(response);
    }

    @GetMapping
    public List<ProjectSummaryResponse> listProjects() {
        UUID ownerId = currentUser.requireCurrentUserId();
        List<Project> projects = projectService.getAccessibleProjects(ownerId);
        Map<UUID, Long> diagramCounts = projectService.countProjectDiagramsForProjects(projects);
        Map<UUID, ProjectRole> roles = projectAccessService.rolesForProjects(projects, ownerId);
        Map<UUID, Long> memberCounts = projectAccessService.memberCountsForProjects(projects);
        return projects.stream()
                .map(project -> ProjectSummaryResponse.from(
                        project,
                        diagramCounts.getOrDefault(project.getId(), 0L),
                        roles.getOrDefault(project.getId(), ProjectRole.VIEWER),
                        memberCounts.getOrDefault(project.getId(), 1L)))
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Project project = projectService.getProjectForViewer(projectId, ownerId);
        return ProjectResponse.from(
                project,
                projectService.countProjectDiagrams(projectId, ownerId),
                projectAccessService.getRole(projectId, ownerId).orElse(ProjectRole.VIEWER),
                projectAccessService.memberCountsForProjects(List.of(project)).getOrDefault(projectId, 1L));
    }

    @PutMapping("/{projectId}")
    public ProjectResponse updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        UUID ownerId = currentUser.requireCurrentUserId();
        Project project = projectService.updateProject(projectId, ownerId, request.name(), request.description());
        return ProjectResponse.from(
                project,
                projectService.countProjectDiagrams(projectId, ownerId),
                ProjectRole.OWNER,
                projectAccessService.memberCountsForProjects(List.of(project)).getOrDefault(projectId, 1L));
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
