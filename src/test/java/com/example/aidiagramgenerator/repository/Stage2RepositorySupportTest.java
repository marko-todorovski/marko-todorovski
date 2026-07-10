package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.domain.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused repository tests for Stage 2 persistence support.
 */
@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
@Transactional
class Stage2RepositorySupportTest {

    @Autowired
    private ApplicationUserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DomainDiagramRepository diagramRepository;

    @Autowired
    private DiagramVersionRepository diagramVersionRepository;

    @Test
    @DisplayName("find user by email regardless of case")
    void shouldFindUserByEmailIgnoringCase() {
        ApplicationUser user = saveUser("Owner@Example.COM");

        Optional<ApplicationUser> found = userRepository.findByEmailIgnoreCase("owner@example.com");

        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getId());
    }

    @Test
    @DisplayName("check duplicate email existence regardless of case")
    void shouldCheckDuplicateEmailIgnoringCase() {
        saveUser("Duplicate@Example.COM");

        assertTrue(userRepository.existsByEmailIgnoreCase("duplicate@example.com"));
        assertTrue(userRepository.existsByEmailIgnoreCase("DUPLICATE@EXAMPLE.COM"));
        assertFalse(userRepository.existsByEmailIgnoreCase("missing@example.com"));
    }

    @Test
    @DisplayName("list projects only for requested owner")
    void shouldListProjectsOnlyForOwner() {
        ApplicationUser owner = saveUser("owner-projects@example.com");
        ApplicationUser otherOwner = saveUser("other-projects@example.com");

        Project first = saveProject(owner, "First Project");
        sleepBriefly();
        Project second = saveProject(owner, "Second Project");
        saveProject(otherOwner, "Other Project");

        List<Project> projects = projectRepository.findAllByOwnerIdOrderByUpdatedAtDesc(owner.getId());

        assertEquals(List.of(second.getId(), first.getId()),
                projects.stream().map(Project::getId).toList());
    }

    @Test
    @DisplayName("reject another user's project through owner-aware lookup")
    void shouldRejectAnotherUsersProject() {
        ApplicationUser owner = saveUser("owner-project-lookup@example.com");
        ApplicationUser otherOwner = saveUser("other-project-lookup@example.com");
        Project project = saveProject(owner, "Private Project");

        assertTrue(projectRepository.findByIdAndOwnerId(project.getId(), owner.getId()).isPresent());
        assertTrue(projectRepository.existsByIdAndOwnerId(project.getId(), owner.getId()));
        assertTrue(projectRepository.findByIdAndOwnerId(project.getId(), otherOwner.getId()).isEmpty());
        assertFalse(projectRepository.existsByIdAndOwnerId(project.getId(), otherOwner.getId()));
    }

    @Test
    @DisplayName("list diagrams only for requested project and owner")
    void shouldListDiagramsOnlyForProjectAndOwner() {
        ApplicationUser owner = saveUser("owner-diagrams@example.com");
        ApplicationUser otherOwner = saveUser("other-diagrams@example.com");
        Project project = saveProject(owner, "Target Project");
        Project otherProject = saveProject(owner, "Other Project");
        Project otherOwnersProject = saveProject(otherOwner, "Other Owner Project");

        Diagram first = saveDiagram(owner, project, "First Diagram");
        sleepBriefly();
        Diagram second = saveDiagram(owner, project, "Second Diagram");
        saveDiagram(owner, otherProject, "Different Project Diagram");
        saveDiagram(otherOwner, otherOwnersProject, "Different Owner Diagram");

        List<Diagram> diagrams = diagramRepository
                .findAllByProjectIdAndOwnerIdOrderByUpdatedAtDesc(project.getId(), owner.getId());

        assertEquals(List.of(second.getId(), first.getId()),
                diagrams.stream().map(Diagram::getId).toList());
    }

    @Test
    @DisplayName("reject another user's diagram through ownership-aware lookup")
    void shouldRejectAnotherUsersDiagram() {
        ApplicationUser owner = saveUser("owner-diagram-lookup@example.com");
        ApplicationUser otherOwner = saveUser("other-diagram-lookup@example.com");
        Project project = saveProject(owner, "Diagram Lookup Project");
        Project otherProject = saveProject(owner, "Wrong Project");
        Diagram diagram = saveDiagram(owner, project, "Owned Diagram");

        assertTrue(diagramRepository.findByIdAndOwnerId(diagram.getId(), owner.getId()).isPresent());
        assertTrue(diagramRepository.findByIdAndProjectIdAndOwnerId(
                diagram.getId(), project.getId(), owner.getId()).isPresent());
        assertTrue(diagramRepository.findByIdAndOwnerId(diagram.getId(), otherOwner.getId()).isEmpty());
        assertTrue(diagramRepository.findByIdAndProjectIdAndOwnerId(
                diagram.getId(), otherProject.getId(), owner.getId()).isEmpty());
    }

    @Test
    @DisplayName("retrieve versions ordered newest first")
    void shouldRetrieveVersionsNewestFirst() {
        ApplicationUser owner = saveUser("owner-version-list@example.com");
        Project project = saveProject(owner, "Version List Project");
        Diagram diagram = saveDiagram(owner, project, "Versioned Diagram");

        DiagramVersion versionOne = saveVersion(diagram, owner, 1);
        DiagramVersion versionTwo = saveVersion(diagram, owner, 2);
        DiagramVersion versionThree = saveVersion(diagram, owner, 3);

        List<DiagramVersion> versions = diagramVersionRepository
                .findAllByDiagramIdOrderByVersionNumberDesc(diagram.getId());

        assertEquals(List.of(versionThree.getId(), versionTwo.getId(), versionOne.getId()),
                versions.stream().map(DiagramVersion::getId).toList());
    }

    @Test
    @DisplayName("find a specific diagram version")
    void shouldFindSpecificVersion() {
        ApplicationUser owner = saveUser("owner-version-find@example.com");
        Project project = saveProject(owner, "Version Find Project");
        Diagram diagram = saveDiagram(owner, project, "Specific Version Diagram");
        DiagramVersion versionTwo = saveVersion(diagram, owner, 2);

        Optional<DiagramVersion> found = diagramVersionRepository
                .findByDiagramIdAndVersionNumber(diagram.getId(), 2);

        assertTrue(found.isPresent());
        assertEquals(versionTwo.getId(), found.get().getId());
        assertTrue(diagramVersionRepository.existsByDiagramIdAndVersionNumber(diagram.getId(), 2));
        assertFalse(diagramVersionRepository.existsByDiagramIdAndVersionNumber(diagram.getId(), 99));
    }

    @Test
    @DisplayName("find maximum version number without loading all versions")
    void shouldFindMaximumVersionNumber() {
        ApplicationUser owner = saveUser("owner-max-version@example.com");
        Project project = saveProject(owner, "Max Version Project");
        Diagram diagram = saveDiagram(owner, project, "Max Version Diagram");
        saveVersion(diagram, owner, 1);
        saveVersion(diagram, owner, 5);
        saveVersion(diagram, owner, 3);

        Optional<Integer> maximum = diagramVersionRepository.findMaximumVersionNumber(diagram.getId());

        assertEquals(Optional.of(5), maximum);
    }

    @Test
    @DisplayName("maximum version query returns empty when no versions exist")
    void shouldReturnEmptyMaximumWhenNoVersionsExist() {
        ApplicationUser owner = saveUser("owner-no-version@example.com");
        Project project = saveProject(owner, "No Version Project");
        Diagram diagram = saveDiagram(owner, project, "No Version Diagram");

        Optional<Integer> maximum = diagramVersionRepository.findMaximumVersionNumber(diagram.getId());

        assertTrue(maximum.isEmpty());
    }

    @Test
    @DisplayName("acquire pessimistic diagram lock inside transaction")
    void shouldAcquirePessimisticLock() {
        ApplicationUser owner = saveUser("owner-lock@example.com");
        Project project = saveProject(owner, "Lock Project");
        Diagram diagram = saveDiagram(owner, project, "Locked Diagram");

        Optional<Diagram> locked = diagramRepository.findByIdForUpdate(diagram.getId());

        assertTrue(locked.isPresent());
        assertEquals(diagram.getId(), locked.get().getId());
    }

    private ApplicationUser saveUser(String email) {
        ApplicationUser user = new ApplicationUser(email, "{noop}password");
        return userRepository.saveAndFlush(user);
    }

    private Project saveProject(ApplicationUser owner, String name) {
        Project project = new Project(owner, name);
        return projectRepository.saveAndFlush(project);
    }

    private Diagram saveDiagram(ApplicationUser owner, Project project, String name) {
        Diagram diagram = new Diagram(
                "Prompt for " + name,
                DiagramType.CLASS,
                "@startuml\nclass " + name.replaceAll("\\W+", "") + "\n@enduml");
        diagram.setOwner(owner);
        diagram.setProject(project);
        diagram.setName(name);
        return diagramRepository.saveAndFlush(diagram);
    }

    private DiagramVersion saveVersion(Diagram diagram, ApplicationUser owner, int versionNumber) {
        DiagramVersion version = new DiagramVersion(
                diagram,
                versionNumber,
                "@startuml\nclass Version" + versionNumber + "\n@enduml",
                DiagramSourceFormat.PLANTUML);
        version.setCreatedBy(owner);
        version.setPrompt("Prompt version " + versionNumber);
        version.setChangeType(versionNumber == 1 ? DiagramChangeType.GENERATED : DiagramChangeType.EDITED);
        version.setModelUsed("test-model");
        return diagramVersionRepository.saveAndFlush(version);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while creating distinct timestamps", e);
        }
    }
}
