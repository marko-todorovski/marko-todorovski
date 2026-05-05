package com.example.aidiagramgenerator.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service for extracting plain text from uploaded PDF files.
 */
public interface PdfExtractionService {

    /**
     * Extracts plain text from the given PDF file.
     *
     * @param file the uploaded PDF
     * @return extracted plain text (never blank)
     * @throws com.example.aidiagramgenerator.exception.DiagramGenerationException
     *         if the file is empty, unreadable, or contains no extractable text
     */
    String extractText(MultipartFile file);
}
