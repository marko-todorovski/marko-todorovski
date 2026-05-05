package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.entity.DiagramHistory;
import com.example.aidiagramgenerator.enums.DiagramType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DiagramHistory entity
 */
@Repository
public interface DiagramHistoryRepository extends JpaRepository<DiagramHistory, String> {

    /**
     * Find all diagrams by type
     */
    List<DiagramHistory> findByDiagramType(DiagramType diagramType);

    /**
     * Find all diagrams by input type
     */
    List<DiagramHistory> findByInputType(String inputType);

    /**
     * Find recent diagrams (limit by creation date)
     */
    List<DiagramHistory> findTop10ByOrderByCreatedAtDesc();
}
