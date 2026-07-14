package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.exception.InvalidProjectException;
import com.example.aidiagramgenerator.exception.ProjectNotEmptyException;
import com.example.aidiagramgenerator.exception.ProjectNotFoundException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final int MAX_PROJECT_NAME_LENGTH = 150;
    private static final int MAX_PROJECT_DESCRIPTION_LENGTH = 1000;

    private final ApplicationUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DomainDiagramRepository diagramRepository;

    public ProjectServiceImpl(
            ApplicationUserRepository userRepository,
            ProjectRepository projectRepository,
            DomainDiagramRepository diagramRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.diagramRepository = diagramRepository;
    }

    @Override
    @Transactional
    public Project createProject(UUID ownerId, String name, String description) {
        ApplicationUser owner = userRepository.findById(requireId(ownerId, "Owner ID"))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + ownerId));
        Project project = new Project(owner, normalizeName(name));
        project.setDescription(normalizeOptionalText(description, MAX_PROJECT_DESCRIPTION_LENGTH, "Project description"));
        return projectRepository.save(project);
    }

    @Override
    @Transactional(readOnly = true)
    public Project getProjectForOwner(UUID projectId, UUID ownerId) {
        return projectRepository.findByIdAndOwnerId(
                        requireId(projectId, "Project ID"),
                        requireId(ownerId, "Owner ID"))
                .orElseThrow(() -> new ProjectNotFoundException("Project not found for owner"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> getProjectsForOwner(UUID ownerId) {
        UUID requiredOwnerId = requireId(ownerId, "Owner ID");
        if (!userRepository.existsById(requiredOwnerId)) {
            throw new UserNotFoundException("User not found: " + ownerId);
        }
        return projectRepository.findAllByOwnerIdOrderByUpdatedAtDesc(requiredOwnerId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countProjectDiagrams(UUID projectId, UUID ownerId) {
        getProjectForOwner(projectId, ownerId);
        return diagramRepository.countByProjectIdAndOwnerId(projectId, ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> countProjectDiagramsForOwner(UUID ownerId) {
        UUID requiredOwnerId = requireId(ownerId, "Owner ID");
        if (!userRepository.existsById(requiredOwnerId)) {
            throw new UserNotFoundException("User not found: " + ownerId);
        }
        return diagramRepository.countDiagramsByProjectForOwner(requiredOwnerId).stream()
                .collect(Collectors.toMap(
                        DomainDiagramRepository.ProjectDiagramCount::getProjectId,
                        DomainDiagramRepository.ProjectDiagramCount::getDiagramCount));
    }

    @Override
    @Transactional
    public Project updateProject(UUID projectId, UUID ownerId, String name, String description) {
        Project project = getProjectForOwner(projectId, ownerId);
        project.setName(normalizeName(name));
        project.setDescription(normalizeOptionalText(description, MAX_PROJECT_DESCRIPTION_LENGTH, "Project description"));
        return projectRepository.save(project);
    }

    @Override
    @Transactional
    public void deleteProject(UUID projectId, UUID ownerId) {
        Project project = getProjectForOwner(projectId, ownerId);
        if (diagramRepository.existsByProjectIdAndOwnerId(project.getId(), ownerId)) {
            throw new ProjectNotEmptyException("Project contains diagrams and cannot be deleted");
        }
        projectRepository.delete(project);
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new InvalidProjectException(fieldName + " is required");
        }
        return id;
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidProjectException("Project name is required");
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_PROJECT_NAME_LENGTH) {
            throw new InvalidProjectException("Project name must be 150 characters or fewer");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new InvalidProjectException(fieldName + " is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
