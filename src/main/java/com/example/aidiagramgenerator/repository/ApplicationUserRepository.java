package com.example.aidiagramgenerator.repository;

import com.example.aidiagramgenerator.domain.ApplicationUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for application users.
 */
@Repository
public interface ApplicationUserRepository extends JpaRepository<ApplicationUser, UUID> {

    Optional<ApplicationUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
