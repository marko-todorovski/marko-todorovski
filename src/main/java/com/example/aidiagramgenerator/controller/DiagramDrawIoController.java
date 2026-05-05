package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.entity.Diagram;
import com.example.aidiagramgenerator.exception.DiagramNotFoundException;
import com.example.aidiagramgenerator.repository.DiagramRepository;
import com.example.aidiagramgenerator.repository.DomainDiagramRepository;
import com.example.aidiagramgenerator.service.export.DrawIoExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for exporting diagrams in Draw.io (diagrams.net) format.
 *
 * <p>Base path: {@code /api/diagram}
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/diagram/{id}/drawio}          — returns XML as a downloadable {@code diagram-{id}.xml} attachment</li>
 *   <li>{@code GET /api/diagram/{id}/drawio/download} — alias, returns the XML as a downloadable .drawio file</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/diagram")
@Tag(name = "Draw.io Export", description = "Export stored diagrams as diagrams.net compatible XML")
public class DiagramDrawIoController {

    private static final Logger logger = LoggerFactory.getLogger(DiagramDrawIoController.class);

    private final DiagramRepository diagramRepository;
    private final DomainDiagramRepository domainDiagramRepository;
    private final DrawIoExportService drawIoExportService;

    public DiagramDrawIoController(DiagramRepository diagramRepository,
                                   DomainDiagramRepository domainDiagramRepository,
                                   DrawIoExportService drawIoExportService) {
        this.diagramRepository = diagramRepository;
        this.domainDiagramRepository = domainDiagramRepository;
        this.drawIoExportService = drawIoExportService;
    }

    // ── Downloadable XML attachment ───────────────────────────────────────────

    /**
     * Returns the Draw.io XML representation of the diagram as a downloadable file.
     *
     * @param id the UUID of the stored diagram
     * @return 200 with {@code Content-Disposition: attachment; filename=diagram-{id}.xml}, or 404 if not found
     */
    @GetMapping(value = "/{id}/drawio", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(
            summary = "Download diagram as Draw.io XML",
            description = "Returns the diagram as a downloadable Draw.io / diagrams.net compatible XML file.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draw.io XML file returned",
                    content = @Content(mediaType = "application/xml")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<byte[]> getDrawIoXml(@PathVariable UUID id) {
        logger.info("GET /api/diagram/{}/drawio", id);

        // Resolve the diagram — 404 only if absent from both tables
        Optional<Diagram> legacyOpt = diagramRepository.findById(id);
        Optional<com.example.aidiagramgenerator.domain.Diagram> domainOpt = legacyOpt.isPresent()
                ? Optional.empty() : domainDiagramRepository.findById(id);

        if (legacyOpt.isEmpty() && domainOpt.isEmpty()) {
            throw new DiagramNotFoundException("Diagram not found with ID: " + id);
        }

        String diagramTypeLabel = legacyOpt.isPresent()
                ? String.valueOf(legacyOpt.get().getDiagramType())
                : String.valueOf(domainOpt.get().getDiagramType());
        String rawCode = legacyOpt.isPresent()
                ? legacyOpt.get().getMermaidCode()
                : domainOpt.get().getPlantUmlCode();
        logger.debug("Diagram {} type={} codeLength={}", id, diagramTypeLabel, rawCode == null ? 0 : rawCode.length());

        // Convert; on any parse error fall back to a simple structure — never 500 if diagram exists
        String xml;
        try {
            if (legacyOpt.isPresent()) {
                xml = drawIoExportService.convertToDrawIoXml(legacyOpt.get());
            } else {
                xml = drawIoExportService.convertPlantUmlToDrawIoXml(
                        domainOpt.get().getPlantUmlCode(), domainOpt.get().getDiagramType());
            }
        } catch (Exception e) {
            logger.error("Draw.io conversion failed for {} (type={}): {} — using fallback XML",
                    id, diagramTypeLabel, e.getMessage(), e);
            xml = drawIoExportService.buildFallbackXml(diagramTypeLabel, rawCode);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diagram.drawio\"");
        return ResponseEntity.ok().headers(headers).body(xml.getBytes(StandardCharsets.UTF_8));
    }

    // ── Downloadable file ─────────────────────────────────────────────────────

    /**
     * Returns the Draw.io XML representation of the diagram as a downloadable file.
     *
     * <p>The {@code Content-Disposition} header is set to {@code attachment} so that
     * browsers and HTTP clients trigger a file-save dialog.
     *
     * @param id the UUID of the stored diagram
     * @return 200 with a {@code .drawio} file attachment, or 404 if not found
     */
    @GetMapping(value = "/{id}/drawio/download", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(
            summary = "Download diagram as Draw.io file",
            description = "Exports a stored diagram as a downloadable .drawio XML file "
                    + "compatible with diagrams.net / Draw.io desktop.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Draw.io file returned",
                    content = @Content(mediaType = "application/xml")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Diagram not found")
    })
    public ResponseEntity<byte[]> downloadDrawIo(@PathVariable UUID id) {
        logger.info("GET /api/diagram/{}/drawio/download — returning file attachment", id);

        String xml;
        String filename = drawIoExportService.generateFilename(id);

        Optional<Diagram> legacyOpt2 = diagramRepository.findById(id);
        Optional<com.example.aidiagramgenerator.domain.Diagram> domainOpt2 = legacyOpt2.isPresent()
                ? Optional.empty() : domainDiagramRepository.findById(id);

        if (legacyOpt2.isEmpty() && domainOpt2.isEmpty()) {
            throw new DiagramNotFoundException("Diagram not found with ID: " + id);
        }

        String diagramTypeLabel2 = legacyOpt2.isPresent()
                ? String.valueOf(legacyOpt2.get().getDiagramType())
                : String.valueOf(domainOpt2.get().getDiagramType());
        String rawCode2 = legacyOpt2.isPresent()
                ? legacyOpt2.get().getMermaidCode()
                : domainOpt2.get().getPlantUmlCode();

        if (legacyOpt2.isPresent()) {
            filename = drawIoExportService.generateFilename(legacyOpt2.get());
        }

        try {
            if (legacyOpt2.isPresent()) {
                xml = drawIoExportService.convertToDrawIoXml(legacyOpt2.get());
            } else {
                xml = drawIoExportService.convertPlantUmlToDrawIoXml(
                        domainOpt2.get().getPlantUmlCode(), domainOpt2.get().getDiagramType());
            }
        } catch (Exception e) {
            logger.error("Draw.io download conversion failed for {} (type={}): {} — using fallback XML",
                    id, diagramTypeLabel2, e.getMessage(), e);
            xml = drawIoExportService.buildFallbackXml(diagramTypeLabel2, rawCode2);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }
}

