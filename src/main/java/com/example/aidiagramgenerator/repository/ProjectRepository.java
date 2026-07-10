package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user-owned projects.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Project> findByIdAndOwnerId(UUID projectId, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID projectId, UUID ownerId);
}
