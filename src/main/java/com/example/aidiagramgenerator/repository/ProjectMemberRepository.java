package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.ProjectMember;
import com.example.aidiagramgenerator.domain.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    @Query("""
            SELECT m
            FROM ProjectMember m
            JOIN FETCH m.user
            WHERE m.project.id = :projectId
            ORDER BY m.joinedAt ASC
            """)
    List<ProjectMember> findAllByProjectIdOrderByJoinedAtAsc(@Param("projectId") UUID projectId);

    @Query("""
            SELECT m
            FROM ProjectMember m
            JOIN FETCH m.user
            JOIN FETCH m.project
            WHERE m.id = :memberId
            """)
    Optional<ProjectMember> findByIdWithUserAndProject(@Param("memberId") UUID memberId);

    @Query("""
            SELECT m
            FROM ProjectMember m
            JOIN FETCH m.project p
            WHERE m.user.id = :userId
            ORDER BY p.updatedAt DESC
            """)
    List<ProjectMember> findAllByUserIdOrderByProjectUpdatedAtDesc(@Param("userId") UUID userId);

    long countByProjectId(UUID projectId);

    boolean existsByProjectIdAndRole(UUID projectId, ProjectRole role);

    @Query("""
            SELECT m.project.id AS projectId, COUNT(m) AS memberCount
            FROM ProjectMember m
            WHERE m.project.id IN :projectIds
            GROUP BY m.project.id
            """)
    List<ProjectMemberCount> countMembersByProjectIds(@Param("projectIds") Collection<UUID> projectIds);

    interface ProjectMemberCount {
        UUID getProjectId();

        long getMemberCount();
    }
}
