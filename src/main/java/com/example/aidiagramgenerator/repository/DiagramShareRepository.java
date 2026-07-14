package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.DiagramShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiagramShareRepository extends JpaRepository<DiagramShare, UUID> {

    @Query("""
            SELECT s
            FROM DiagramShare s
            JOIN FETCH s.diagram d
            JOIN FETCH s.diagramVersion v
            WHERE s.tokenHash = :tokenHash
            """)
    Optional<DiagramShare> findByTokenHash(@Param("tokenHash") String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    @Query("""
            SELECT s
            FROM DiagramShare s
            JOIN FETCH s.diagramVersion v
            WHERE s.diagram.id = :diagramId
            ORDER BY s.createdAt DESC
            """)
    List<DiagramShare> findAllByDiagramIdOrderByCreatedAtDesc(@Param("diagramId") UUID diagramId);

    @Query("""
            SELECT s
            FROM DiagramShare s
            JOIN FETCH s.diagramVersion v
            WHERE s.id = :shareId
              AND s.diagram.id = :diagramId
              AND s.diagram.owner.id = :ownerId
            """)
    Optional<DiagramShare> findOwnedShare(
            @Param("diagramId") UUID diagramId,
            @Param("shareId") UUID shareId,
            @Param("ownerId") UUID ownerId);
}
