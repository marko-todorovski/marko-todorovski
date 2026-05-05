package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.enums.InputType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramRepository extends JpaRepository<Diagram, UUID> {

    /**
     * Find the original (root) diagram for the same input content and type.
     * Returns the diagram with the lowest version number (the original).
     */
    @Query("SELECT d FROM Diagram d WHERE d.inputContent = :inputContent AND d.inputType = :inputType " +
           "AND d.parentDiagramId IS NULL ORDER BY d.versionNumber ASC")
    Optional<Diagram> findOriginalByInputContentAndInputType(
            @Param("inputContent") String inputContent,
            @Param("inputType") InputType inputType);

    /**
     * Find the latest version number for a given input content and type.
     */
    @Query("SELECT MAX(d.versionNumber) FROM Diagram d WHERE d.inputContent = :inputContent AND d.inputType = :inputType")
    Optional<Integer> findMaxVersionByInputContentAndInputType(
            @Param("inputContent") String inputContent,
            @Param("inputType") InputType inputType);

    /**
     * Find all versions of a diagram (including the original and all its versions).
     * Ordered by version number ascending.
     */
    @Query("SELECT d FROM Diagram d WHERE d.id = :diagramId " +
           "OR d.parentDiagramId = :diagramId " +
           "OR d.id = (SELECT d2.parentDiagramId FROM Diagram d2 WHERE d2.id = :diagramId) " +
           "OR d.parentDiagramId = (SELECT d2.parentDiagramId FROM Diagram d2 WHERE d2.id = :diagramId) " +
           "ORDER BY d.versionNumber ASC")
    List<Diagram> findAllVersionsByDiagramId(@Param("diagramId") UUID diagramId);
}
