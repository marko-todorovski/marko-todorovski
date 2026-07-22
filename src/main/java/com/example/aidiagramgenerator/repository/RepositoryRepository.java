package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    List<Repository> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Repository> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
