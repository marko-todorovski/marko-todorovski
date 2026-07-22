package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.exception.InvalidProjectException;
import com.example.aidiagramgenerator.exception.ProjectNotFoundException;
import com.example.aidiagramgenerator.exception.ProjectPermissionException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectAccessServiceImpl implements ProjectAccessService {

    private final ProjectMemberRepository memberRepository;
    private final ApplicationUserRepository userRepository;

    public ProjectAccessServiceImpl(ProjectMemberRepository memberRepository, ApplicationUserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMember requireProjectViewer(UUID projectId, UUID userId) {
        ProjectMember member = requireMembership(projectId, userId);
        if (!member.canView()) {
            throw denied();
        }
        return member;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMember requireProjectEditor(UUID projectId, UUID userId) {
        ProjectMember member = requireMembership(projectId, userId);
        if (!member.canEdit()) {
            throw denied();
        }
        return member;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMember requireProjectOwner(UUID projectId, UUID userId) {
        ProjectMember member = requireMembership(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw denied();
        }
        return member;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMember requireDiagramViewer(Diagram diagram, UUID userId) {
        ProjectMember member = requireDiagramMembership(diagram, userId);
        if (!member.canView()) {
            throw denied();
        }
        return member;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMember requireDiagramEditor(Diagram diagram, UUID userId) {
        ProjectMember member = requireDiagramMembership(diagram, userId);
        if (!member.canEdit()) {
            throw denied();
        }
        return member;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectMember requireDiagramOwner(Diagram diagram, UUID userId) {
        ProjectMember member = requireDiagramMembership(diagram, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw denied();
        }
        return member;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectRole> getRole(UUID projectId, UUID userId) {
        return memberRepository.findByProjectIdAndUserId(requireId(projectId, "Project ID"), requireId(userId, "User ID"))
                .map(ProjectMember::getRole);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManageShares(UUID projectId, UUID userId) {
        return getRole(projectId, userId).map(ProjectRole::canManageShares).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> listAccessibleProjects(UUID userId) {
        UUID requiredUserId = requireId(userId, "User ID");
        if (!userRepository.existsById(requiredUserId)) {
            throw new ProjectNotFoundException("Project not found");
        }
        return memberRepository.findAllByUserIdOrderByProjectUpdatedAtDesc(requiredUserId).stream()
                .map(ProjectMember::getProject)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ProjectRole> rolesForProjects(List<Project> projects, UUID userId) {
        UUID requiredUserId = requireId(userId, "User ID");
        return projects.stream()
                .map(Project::getId)
                .collect(Collectors.toMap(
                        id -> id,
                        id -> getRole(id, requiredUserId).orElse(ProjectRole.VIEWER)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> memberCountsForProjects(List<Project> projects) {
        List<UUID> ids = projects.stream().map(Project::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberRepository.countMembersByProjectIds(ids).stream()
                .collect(Collectors.toMap(
                        ProjectMemberRepository.ProjectMemberCount::getProjectId,
                        ProjectMemberRepository.ProjectMemberCount::getMemberCount));
    }

    private ProjectMember requireDiagramMembership(Diagram diagram, UUID userId) {
        if (diagram == null || diagram.getId() == null) {
            throw new DiagramNotFoundException("Diagram not found");
        }
        if (diagram.getProject() == null || diagram.getProject().getId() == null) {
            if (diagram.getOwner() != null && requireId(userId, "User ID").equals(diagram.getOwner().getId())) {
                return new ProjectMember(null, diagram.getOwner(), ProjectRole.OWNER);
            }
            throw new DiagramNotFoundException("Diagram not found");
        }
        try {
            return requireMembership(diagram.getProject().getId(), userId);
        } catch (ProjectNotFoundException e) {
            throw new DiagramNotFoundException("Diagram not found");
        }
    }

    private ProjectMember requireMembership(UUID projectId, UUID userId) {
        return memberRepository.findByProjectIdAndUserId(requireId(projectId, "Project ID"), requireId(userId, "User ID"))
                .orElseThrow(() -> new ProjectNotFoundException("Project not found"));
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new InvalidProjectException(fieldName + " is required");
        }
        return id;
    }

    private static ProjectPermissionException denied() {
        return new ProjectPermissionException("You do not have permission to perform this action.");
    }
}
