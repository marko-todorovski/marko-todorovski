package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.RepositoryScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryScanRepository extends JpaRepository<RepositoryScan, UUID> {

    List<RepositoryScan> findAllByRepository_IdOrderByStartedAtDesc(UUID repositoryId);

    Optional<RepositoryScan> findTopByRepository_IdOrderByStartedAtDesc(UUID repositoryId);
}
