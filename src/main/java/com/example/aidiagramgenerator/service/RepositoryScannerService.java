package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.domain.Repository;
import com.example.aidiagramgenerator.domain.RepositoryScan;

import java.io.InputStream;

public interface RepositoryScannerService {

    /**
     * Downloads the repository's GitHub source and scans it, persisting a new
     * {@link RepositoryScan} and updating the repository's status. Never throws for scan
     * failures - failures are captured as a FAILED scan record instead.
     */
    RepositoryScan scanGithub(Repository repository);

    /**
     * Scans an uploaded ZIP archive, persisting a new {@link RepositoryScan} and updating the
     * repository's status. Never throws for scan failures - failures are captured as a FAILED
     * scan record instead.
     */
    RepositoryScan scanZip(Repository repository, InputStream zipStream);
}
