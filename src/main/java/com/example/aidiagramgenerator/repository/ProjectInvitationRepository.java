package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.ProjectInvitation;
import com.example.aidiagramgenerator.domain.ProjectInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, UUID> {

    @Query("""
            SELECT i
            FROM ProjectInvitation i
            JOIN FETCH i.project
            WHERE i.tokenHash = :tokenHash
            """)
    Optional<ProjectInvitation> findByTokenHash(@Param("tokenHash") String tokenHash);

    boolean existsByTokenHash(String tokenHash);

    boolean existsByProjectIdAndInvitedEmailAndStatus(UUID projectId, String invitedEmail, ProjectInvitationStatus status);

    List<ProjectInvitation> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectInvitation> findByIdAndProjectId(UUID invitationId, UUID projectId);
}
