package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.Diagram;
import com.example.aidiagramgenerator.domain.DiagramType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for domain Diagram entities (PlantUML-based diagrams).
 */
@Repository
public interface DomainDiagramRepository extends JpaRepository<Diagram, UUID> {

    interface ProjectDiagramCount {
        UUID getProjectId();

        long getDiagramCount();
    }

    /**
     * Find all diagrams of a specific type.
     *
     * @param diagramType the diagram type
     * @return list of diagrams
     */
    List<Diagram> findByDiagramType(DiagramType diagramType);

    /**
     * Find all diagrams created after a specific date.
     *
     * @param dateTime the date threshold
     * @return list of diagrams
     */
    List<Diagram> findByCreatedAtAfter(LocalDateTime dateTime);

    /**
     * Find all diagrams created between two dates.
     *
     * @param start the start date
     * @param end   the end date
     * @return list of diagrams
     */
    List<Diagram> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find diagrams containing specific text in the input.
     *
     * @param searchText the text to search for
     * @return list of matching diagrams
     */
    @Query("SELECT d FROM DomainDiagram d WHERE LOWER(d.inputText) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<Diagram> findByInputTextContaining(@Param("searchText") String searchText);

    /**
     * Count diagrams by type.
     *
     * @param diagramType the diagram type
     * @return count of diagrams
     */
    long countByDiagramType(DiagramType diagramType);

    /**
     * Find the most recent diagrams.
     *
     * @return list of recent diagrams (limited to 10)
     */
    @Query("SELECT d FROM DomainDiagram d ORDER BY d.createdAt DESC LIMIT 10")
    List<Diagram> findRecentDiagrams();

    List<Diagram> findAllByProjectIdAndOwnerIdOrderByUpdatedAtDesc(UUID projectId, UUID ownerId);

    long countByProjectIdAndOwnerId(UUID projectId, UUID ownerId);

    @Query("""
            SELECT d.project.id AS projectId, COUNT(d) AS diagramCount
            FROM DomainDiagram d
            WHERE d.owner.id = :ownerId
              AND d.project IS NOT NULL
            GROUP BY d.project.id
            """)
    List<ProjectDiagramCount> countDiagramsByProjectForOwner(@Param("ownerId") UUID ownerId);

    boolean existsByProjectIdAndOwnerId(UUID projectId, UUID ownerId);

    Optional<Diagram> findByIdAndOwnerId(UUID diagramId, UUID ownerId);

    Optional<Diagram> findByIdAndProjectIdAndOwnerId(UUID diagramId, UUID projectId, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT d FROM DomainDiagram d WHERE d.id = :diagramId")
    Optional<Diagram> findByIdForUpdate(@Param("diagramId") UUID diagramId);
}
