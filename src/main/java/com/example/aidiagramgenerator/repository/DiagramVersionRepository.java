package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.DiagramVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persisted diagram source-code versions.
 */
@Repository
public interface DiagramVersionRepository extends JpaRepository<DiagramVersion, UUID> {

    List<DiagramVersion> findAllByDiagramIdOrderByVersionNumberDesc(UUID diagramId);

    Optional<DiagramVersion> findByDiagramIdAndVersionNumber(UUID diagramId, int versionNumber);

    boolean existsByDiagramIdAndVersionNumber(UUID diagramId, int versionNumber);

    @Query("SELECT MAX(v.versionNumber) FROM DiagramVersion v WHERE v.diagram.id = :diagramId")
    Optional<Integer> findMaximumVersionNumber(@Param("diagramId") UUID diagramId);
}
