package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Project;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProjectService {

    Project createProject(UUID ownerId, String name, String description);

    Project getProjectForOwner(UUID projectId, UUID ownerId);

    Project getProjectForViewer(UUID projectId, UUID userId);

    List<Project> getProjectsForOwner(UUID ownerId);

    List<Project> getAccessibleProjects(UUID userId);

    long countProjectDiagrams(UUID projectId, UUID ownerId);

    Map<UUID, Long> countProjectDiagramsForOwner(UUID ownerId);

    Map<UUID, Long> countProjectDiagramsForProjects(List<Project> projects);

    Project updateProject(UUID projectId, UUID ownerId, String name, String description);

    void deleteProject(UUID projectId, UUID ownerId);
}
