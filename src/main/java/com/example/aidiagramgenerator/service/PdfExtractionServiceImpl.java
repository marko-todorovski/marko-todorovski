package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.DiagramGenerationException;
import com.example.aidiagramgenerator.exception.InvalidDiagramRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Implementation of {@link PdfExtractionService} using Apache PDFBox.
 */
@Service
public class PdfExtractionServiceImpl implements PdfExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(PdfExtractionServiceImpl.class);

    /** Maximum number of characters returned to the generation pipeline. */
    private static final int MAX_TEXT_LENGTH = 2_000;

    @Override
    public String extractText(MultipartFile file) {
        // Guard: null or zero-byte upload
        if (file == null || file.isEmpty()) {
            logger.warn("PDF extraction rejected: file is null or empty");
            throw new InvalidDiagramRequestException("No file was uploaded or the file is empty");
        }

        // Guard: wrong content type
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")) {
            logger.warn("PDF extraction rejected: unexpected content type '{}' for file '{}'",
                    contentType, file.getOriginalFilename());
            throw new InvalidDiagramRequestException("Uploaded file is not a valid PDF");
        }

        logger.info("Starting PDF text extraction — file: '{}', size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        try {
            byte[] bytes = file.getBytes();

            try (PDDocument document = Loader.loadPDF(bytes)) {
                int pageCount = document.getNumberOfPages();
                logger.debug("PDF loaded successfully — pages: {}", pageCount);

                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                if (text == null || text.isBlank()) {
                    logger.warn("PDF '{}' contains no extractable text (possibly image-only or encrypted)",
                            file.getOriginalFilename());
                    throw new InvalidDiagramRequestException("No readable text found in PDF");
                }

                String cleaned = text
                        .replaceAll("\\r\\n|\\r|\\n", " ")  // collapse line breaks
                        .replaceAll("[ \\t]{2,}", " ")       // collapse whitespace runs
                        .trim();

                String result = cleaned.length() > MAX_TEXT_LENGTH
                        ? cleaned.substring(0, MAX_TEXT_LENGTH)
                        : cleaned;

                logger.info("PDF extraction complete — extracted {} chars (truncated: {})",
                        result.length(), cleaned.length() > MAX_TEXT_LENGTH);
                return result;
            }

        } catch (DiagramGenerationException | InvalidDiagramRequestException e) {
            throw e;
        } catch (IOException e) {
            logger.error("I/O error reading PDF '{}': {}", file.getOriginalFilename(), e.getMessage());
            throw new DiagramGenerationException("Could not read the uploaded PDF file", e);
        } catch (Exception e) {
            logger.error("Unexpected error processing PDF '{}': {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new DiagramGenerationException("Failed to process the PDF — ensure the file is not encrypted or corrupted", e);
        }
    }
}
