package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.AiDiagramGeneratorApplication;
import com.example.aidiagramgenerator.domain.ApplicationUser;
import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramChangeType;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.domain.DiagramType;
import com.example.aidiagramgenerator.domain.DiagramVersion;
import com.example.aidiagramgenerator.domain.Project;
import com.example.aidiagramgenerator.exception.DiagramAccessDeniedException;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.exception.InvalidDiagramException;
import com.example.aidiagramgenerator.exception.InvalidDiagramVersionException;
import com.example.aidiagramgenerator.exception.InvalidProjectException;
import com.example.aidiagramgenerator.exception.ProjectNotEmptyException;
import com.example.aidiagramgenerator.exception.ProjectNotFoundException;
import com.example.aidiagramgenerator.exception.UserNotFoundException;
import com.example.aidiagramgenerator.repository.ApplicationUserRepository;
import com.example.aidiagramgenerator.repository.DiagramVersionRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = AiDiagramGeneratorApplication.class)
@ActiveProfiles("dev")
class Stage4PersistenceServiceTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private SavedDiagramService savedDiagramService;

    @Autowired
    private DiagramVersionService diagramVersionService;

    @Autowired
    private ApplicationUserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private DomainDiagramRepository diagramRepository;

    @Autowired
    private DiagramVersionRepository versionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateTrimRetrieveUpdateAndDeleteProjectsForOwner() {
        ApplicationUser owner = saveUser("project-owner");

        Project project = projectService.createProject(owner.getId(), "  Project One  ", "  Description  ");

        assertEquals("Project One", project.getName());
        assertEquals("Description", project.getDescription());
        assertEquals(owner.getId(), project.getOwner().getId());
        assertEquals(project.getId(), projectService.getProjectForOwner(project.getId(), owner.getId()).getId());
        assertEquals(List.of(project.getId()),
                projectService.getProjectsForOwner(owner.getId()).stream().map(Project::getId).toList());

        Project updated = projectService.updateProject(project.getId(), owner.getId(), "  Project Two  ", null);
        assertEquals("Project Two", updated.getName());
        assertEquals(owner.getId(), updated.getOwner().getId());

        projectService.deleteProject(project.getId(), owner.getId());
        assertFalse(projectRepository.existsById(project.getId()));
    }

    @Test
    void shouldRejectInvalidProjectInputsAndCrossOwnerAccess() {
        ApplicationUser owner = saveUser("project-owner-access");
        ApplicationUser otherOwner = saveUser("project-other-access");
        Project project = projectService.createProject(owner.getId(), "Owned Project", null);

        assertThrows(UserNotFoundException.class,
                () -> projectService.createProject(UUID.randomUUID(), "Missing User", null));
        assertThrows(InvalidProjectException.class,
                () -> projectService.createProject(owner.getId(), "   ", null));
        assertThrows(ProjectNotFoundException.class,
                () -> projectService.getProjectForOwner(project.getId(), otherOwner.getId()));
    }

    @Test
    void shouldRejectDeletingNonEmptyProject() {
        ApplicationUser owner = saveUser("project-not-empty");
        Project project = projectService.createProject(owner.getId(), "Non Empty", null);
        savedDiagramService.saveGeneratedDiagram(
                owner.getId(),
                project.getId(),
                "Diagram",
                null,
                "Prompt",
                DiagramType.CLASS,
                DiagramSourceFormat.PLANTUML,
                "@startuml\nclass A\n@enduml",
                "test-model");

        assertThrows(ProjectNotEmptyException.class,
                () -> projectService.deleteProject(project.getId(), owner.getId()));
    }

    @Test
    void shouldSaveNewPlantUmlDiagramAndCreateInitialVersion() {
        ApplicationUser owner = saveUser("diagram-save");
        Project project = projectService.createProject(owner.getId(), "Diagram Project", null);
        String source = "@startuml\nclass Saved\n@enduml";

        Diagram diagram = savedDiagramService.saveGeneratedDiagram(
                owner.getId(),
                project.getId(),
                "  Saved Diagram  ",
                "  Useful description  ",
                "  Original prompt  ",
                DiagramType.CLASS,
                DiagramSourceFormat.PLANTUML,
                source,
                "test-model");

        assertEquals(owner.getId(), diagram.getOwner().getId());
        assertEquals(project.getId(), diagram.getProject().getId());
        assertEquals("Saved Diagram", diagram.getName());
        assertEquals("Useful description", diagram.getDescription());
        assertEquals("Original prompt", diagram.getInputText());
        assertEquals(source, diagram.getPlantUmlCode());
        assertEquals(source, diagram.getCurrentSourceCode());
        assertEquals(DiagramSourceFormat.PLANTUML, diagram.getSourceFormat());
        assertEquals(1, diagram.getCurrentVersionNumber());

        List<DiagramVersion> versions = versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagram.getId());
        assertEquals(1, versions.size());
        assertEquals(1, versions.get(0).getVersionNumber());
        assertEquals(DiagramChangeType.GENERATED, versions.get(0).getChangeType());
        assertEquals(source, versions.get(0).getSourceCode());
    }

    @Test
    void shouldRejectSavingDiagramIntoAnotherUsersProject() {
        ApplicationUser owner = saveUser("diagram-cross-owner");
        ApplicationUser otherOwner = saveUser("diagram-cross-other");
        Project otherProject = projectService.createProject(otherOwner.getId(), "Other Project", null);

        assertThrows(ProjectNotFoundException.class,
                () -> savedDiagramService.saveGeneratedDiagram(
                        owner.getId(),
                        otherProject.getId(),
                        "Diagram",
                        null,
                        "Prompt",
                        DiagramType.CLASS,
                        DiagramSourceFormat.PLANTUML,
                        "@startuml\nclass A\n@enduml",
                        null));
    }

    @Test
    void shouldRejectNewMermaidDiagramUntilPlantUmlCodeIsNullable() {
        ApplicationUser owner = saveUser("diagram-mermaid-owner");
        Project project = projectService.createProject(owner.getId(), "Mermaid Project", null);

        assertThrows(InvalidDiagramException.class,
                () -> savedDiagramService.saveGeneratedDiagram(
                        owner.getId(),
                        project.getId(),
                        "Mermaid",
                        null,
                        "Prompt",
                        DiagramType.CLASS,
                        DiagramSourceFormat.MERMAID,
                        "classDiagram\nclass A",
                        null));
    }

    @Test
    void shouldAttachUnownedDiagramAndAvoidDuplicateInitialVersion() {
        ApplicationUser owner = saveUser("attach-owner");
        Project project = projectService.createProject(owner.getId(), "Attach Project", null);
        Diagram existing = diagramRepository.saveAndFlush(
                new Diagram("Legacy prompt", DiagramType.CLASS, "@startuml\nclass Legacy\n@enduml"));

        Diagram attached = savedDiagramService.attachExistingGeneratedDiagram(
                owner.getId(),
                project.getId(),
                existing.getId(),
                "Attached",
                null);
        savedDiagramService.attachExistingGeneratedDiagram(
                owner.getId(),
                project.getId(),
                existing.getId(),
                "Attached Again",
                null);

        assertEquals(owner.getId(), attached.getOwner().getId());
        assertEquals(project.getId(), attached.getProject().getId());
        assertEquals(1, versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(existing.getId()).size());
    }

    @Test
    void shouldRejectAttachingDiagramOwnedByAnotherUser() {
        ApplicationUser owner = saveUser("attach-owner-denied");
        ApplicationUser otherOwner = saveUser("attach-other-denied");
        Project ownerProject = projectService.createProject(owner.getId(), "Owner Project", null);
        Project otherProject = projectService.createProject(otherOwner.getId(), "Other Project", null);
        Diagram otherDiagram = savedDiagramService.saveGeneratedDiagram(
                otherOwner.getId(),
                otherProject.getId(),
                "Other Diagram",
                null,
                "Prompt",
                DiagramType.CLASS,
                DiagramSourceFormat.PLANTUML,
                "@startuml\nclass Other\n@enduml",
                null);

        assertThrows(DiagramAccessDeniedException.class,
                () -> savedDiagramService.attachExistingGeneratedDiagram(
                        owner.getId(), ownerProject.getId(), otherDiagram.getId(), "Claim", null));
    }

    @Test
    void shouldListAndDeleteOnlyOwnedDiagrams() {
        ApplicationUser owner = saveUser("diagram-list-owner");
        ApplicationUser otherOwner = saveUser("diagram-list-other");
        Project project = projectService.createProject(owner.getId(), "List Project", null);
        Project otherProject = projectService.createProject(otherOwner.getId(), "Other List Project", null);
        Diagram diagram = savedDiagramService.saveGeneratedDiagram(
                owner.getId(), project.getId(), "Owned", null, "Prompt", DiagramType.CLASS,
                DiagramSourceFormat.PLANTUML, "@startuml\nclass Owned\n@enduml", null);
        savedDiagramService.saveGeneratedDiagram(
                otherOwner.getId(), otherProject.getId(), "Other", null, "Prompt", DiagramType.CLASS,
                DiagramSourceFormat.PLANTUML, "@startuml\nclass Other\n@enduml", null);

        assertEquals(List.of(diagram.getId()),
                savedDiagramService.getProjectDiagrams(project.getId(), owner.getId()).stream().map(Diagram::getId).toList());
        assertThrows(DiagramNotFoundException.class,
                () -> savedDiagramService.deleteDiagram(diagram.getId(), otherOwner.getId()));

        savedDiagramService.deleteDiagram(diagram.getId(), owner.getId());
        assertTrue(versionRepository.findAllByDiagramIdOrderByVersionNumberDesc(diagram.getId()).isEmpty());
        assertFalse(diagramRepository.existsById(diagram.getId()));
    }

    @Test
    void shouldCreateVersionsOnlyWhenMeaningfulContentChangesAndPreserveWhitespace() {
        Diagram diagram = saveOwnedDiagram("version-change");
        String sourceV2 = "@startuml\n  class A\n@enduml\n";

        DiagramVersion initial = diagramVersionService.createInitialVersion(diagram.getId(), diagram.getOwner().getId());
        Optional<DiagramVersion> duplicate = diagramVersionService.createVersionIfChanged(
                diagram.getId(), diagram.getOwner().getId(), initial.getPrompt(), initial.getSourceCode(),
                initial.getSourceFormat(), DiagramChangeType.EDITED, null);
        Optional<DiagramVersion> changedSource = diagramVersionService.createVersionIfChanged(
                diagram.getId(), diagram.getOwner().getId(), initial.getPrompt(), sourceV2,
                DiagramSourceFormat.PLANTUML, DiagramChangeType.EDITED, "model-2");
        Optional<DiagramVersion> changedPrompt = diagramVersionService.createVersionIfChanged(
                diagram.getId(), diagram.getOwner().getId(), "New prompt", sourceV2,
                DiagramSourceFormat.PLANTUML, DiagramChangeType.EDITED, "model-2");

        assertTrue(duplicate.isEmpty());
        assertTrue(changedSource.isPresent());
        assertTrue(changedPrompt.isPresent());
        assertEquals(sourceV2, changedSource.get().getSourceCode());
        assertEquals(3, changedPrompt.get().getVersionNumber());
        assertEquals(List.of(3, 2, 1), diagramVersionService
                .getVersionHistory(diagram.getId(), diagram.getOwner().getId())
                .stream()
                .map(DiagramVersion::getVersionNumber)
                .toList());
    }

    @Test
    void shouldRejectInvalidAndCrossOwnerVersionAccess() {
        Diagram diagram = saveOwnedDiagram("version-access");
        ApplicationUser otherOwner = saveUser("version-access-other");
        diagramVersionService.createInitialVersion(diagram.getId(), diagram.getOwner().getId());

        assertThrows(DiagramAccessDeniedException.class,
                () -> diagramVersionService.createVersionIfChanged(
                        diagram.getId(), otherOwner.getId(), "Prompt", "@startuml\nclass X\n@enduml",
                        DiagramSourceFormat.PLANTUML, DiagramChangeType.EDITED, null));
        assertThrows(DiagramNotFoundException.class,
                () -> diagramVersionService.getVersion(diagram.getId(), otherOwner.getId(), 1));
        assertThrows(InvalidDiagramVersionException.class,
                () -> diagramVersionService.getVersion(diagram.getId(), diagram.getOwner().getId(), 0));
    }

    @Test
    void shouldRejectInitialVersionWithEmptySource() {
        ApplicationUser owner = saveUser("empty-source-owner");
        Project project = projectService.createProject(owner.getId(), "Empty Source Project", null);
        UUID diagramId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO domain_diagrams
                            (id, input_text, diagram_type, plant_uml_code, created_at, owner_id, project_id,
                             source_format, current_source_code, original_prompt, updated_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                diagramId,
                "Prompt",
                "CLASS",
                " ",
                owner.getId(),
                project.getId(),
                "PLANTUML",
                " ",
                "Prompt");

        assertThrows(InvalidDiagramVersionException.class,
                () -> diagramVersionService.createInitialVersion(diagramId, owner.getId()));
    }

    @Test
    void shouldRestoreOlderVersionByCreatingNewCurrentVersion() {
        Diagram diagram = saveOwnedDiagram("version-restore");
        UUID ownerId = diagram.getOwner().getId();
        DiagramVersion versionOne = diagramVersionService.createInitialVersion(diagram.getId(), ownerId);
        DiagramVersion versionTwo = diagramVersionService.createVersionIfChanged(
                diagram.getId(), ownerId, "Second prompt", "@startuml\nclass Second\n@enduml",
                DiagramSourceFormat.PLANTUML, DiagramChangeType.EDITED, "model-2").orElseThrow();

        DiagramVersion restored = diagramVersionService.restoreVersion(diagram.getId(), ownerId, versionOne.getVersionNumber());
        Diagram current = savedDiagramService.getDiagramForOwner(diagram.getId(), ownerId);

        assertEquals(3, restored.getVersionNumber());
        assertEquals(DiagramChangeType.RESTORED, restored.getChangeType());
        assertEquals(versionOne.getSourceCode(), restored.getSourceCode());
        assertEquals(versionOne.getSourceCode(), current.getCurrentSourceCode());
        assertEquals(3, current.getCurrentVersionNumber());
        assertEquals(List.of(3, 2, 1), diagramVersionService
                .getVersionHistory(diagram.getId(), ownerId)
                .stream()
                .map(DiagramVersion::getVersionNumber)
                .toList());
        assertNotEquals(versionTwo.getSourceCode(), current.getCurrentSourceCode());
    }

    @Test
    void shouldCreateSequentialVersionsDuringConcurrentWrites() throws Exception {
        Diagram diagram = saveOwnedDiagram("version-concurrency");
        UUID ownerId = diagram.getOwner().getId();
        diagramVersionService.createInitialVersion(diagram.getId(), ownerId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Optional<DiagramVersion>> first = () -> diagramVersionService.createVersionIfChanged(
                    diagram.getId(), ownerId, "Prompt A", "@startuml\nclass A\n@enduml",
                    DiagramSourceFormat.PLANTUML, DiagramChangeType.EDITED, null);
            Callable<Optional<DiagramVersion>> second = () -> diagramVersionService.createVersionIfChanged(
                    diagram.getId(), ownerId, "Prompt B", "@startuml\nclass B\n@enduml",
                    DiagramSourceFormat.PLANTUML, DiagramChangeType.EDITED, null);

            Future<Optional<DiagramVersion>> firstResult = executor.submit(first);
            Future<Optional<DiagramVersion>> secondResult = executor.submit(second);
            assertTrue(firstResult.get().isPresent());
            assertTrue(secondResult.get().isPresent());
        } finally {
            executor.shutdownNow();
        }

        List<Integer> versionNumbers = diagramVersionService.getVersionHistory(diagram.getId(), ownerId)
                .stream()
                .map(DiagramVersion::getVersionNumber)
                .sorted()
                .toList();
        assertEquals(List.of(1, 2, 3), versionNumbers);
        assertEquals(Set.of(1, 2, 3), Set.copyOf(versionNumbers));
        assertEquals(3, savedDiagramService.getDiagramForOwner(diagram.getId(), ownerId).getCurrentVersionNumber());
    }

    private Diagram saveOwnedDiagram(String key) {
        ApplicationUser owner = saveUser(key + "-owner");
        Project project = projectService.createProject(owner.getId(), key + " Project", null);
        return savedDiagramService.saveGeneratedDiagram(
                owner.getId(),
                project.getId(),
                key + " Diagram",
                null,
                key + " Prompt",
                DiagramType.CLASS,
                DiagramSourceFormat.PLANTUML,
                "@startuml\nclass " + key.replace("-", "") + "\n@enduml",
                "test-model");
    }

    private ApplicationUser saveUser(String key) {
        return userRepository.saveAndFlush(new ApplicationUser(key + "-" + UUID.randomUUID() + "@example.com", "{noop}password"));
    }
}
