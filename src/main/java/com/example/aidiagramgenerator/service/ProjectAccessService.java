package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ProjectAccessService {

    ProjectMember requireProjectViewer(UUID projectId, UUID userId);

    ProjectMember requireProjectEditor(UUID projectId, UUID userId);

    ProjectMember requireProjectOwner(UUID projectId, UUID userId);

    ProjectMember requireDiagramViewer(Diagram diagram, UUID userId);

    ProjectMember requireDiagramEditor(Diagram diagram, UUID userId);

    ProjectMember requireDiagramOwner(Diagram diagram, UUID userId);

    Optional<ProjectRole> getRole(UUID projectId, UUID userId);

    boolean canManageShares(UUID projectId, UUID userId);

    List<Project> listAccessibleProjects(UUID userId);

    Map<UUID, ProjectRole> rolesForProjects(List<Project> projects, UUID userId);

    Map<UUID, Long> memberCountsForProjects(List<Project> projects);
}
