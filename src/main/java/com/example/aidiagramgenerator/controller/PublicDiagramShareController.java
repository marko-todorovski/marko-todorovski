package com.example.aidiagramgenerator.controller;

import com.example.aidiagramgenerator.domain.DiagramShare;
import com.example.aidiagramgenerator.domain.DiagramSourceFormat;
import com.example.aidiagramgenerator.dto.response.PublicDiagramShareResponse;
import com.example.aidiagramgenerator.exception.DiagramShareException;
import com.example.aidiagramgenerator.service.DiagramShareService;
import com.example.aidiagramgenerator.service.export.DrawIoExportService;
import com.example.aidiagramgenerator.service.render.DiagramRenderingException;
import com.example.aidiagramgenerator.service.render.DiagramRenderingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RestController
@RequestMapping("/api/public/shares/{token}")
public class PublicDiagramShareController {

    private final DiagramShareService shareService;
    private final DiagramRenderingService renderingService;
    private final DrawIoExportService drawIoExportService;

    public PublicDiagramShareController(
            DiagramShareService shareService,
            DiagramRenderingService renderingService,
            DrawIoExportService drawIoExportService) {
        this.shareService = shareService;
        this.renderingService = renderingService;
        this.drawIoExportService = drawIoExportService;
    }

    @GetMapping
    public ResponseEntity<PublicDiagramShareResponse> getShare(
            @PathVariable String token,
            HttpServletRequest request) {
        DiagramShare share = shareService.resolvePublicShare(token, request.getRemoteAddr(), true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(PublicDiagramShareResponse.from(share));
    }

    @GetMapping(value = "/preview", produces = "image/svg+xml")
    public ResponseEntity<byte[]> preview(
            @PathVariable String token,
            HttpServletRequest request) {
        DiagramShare share = shareService.resolvePublicShare(token, request.getRemoteAddr(), false);
        requirePlantUml(share);
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(renderingService.renderToSvg(share.getDiagramVersion().getSourceCode()));
        } catch (IllegalArgumentException | DiagramRenderingException e) {
            throw new DiagramShareException(HttpStatus.UNPROCESSABLE_ENTITY, "SHARE_PREVIEW_UNAVAILABLE", "Shared diagram preview is unavailable");
        }
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String token,
            @RequestParam(defaultValue = "png") String format,
            HttpServletRequest request) {
        DiagramShare share = shareService.resolvePublicShareForDownload(token, request.getRemoteAddr());
        requirePlantUml(share);
        String normalized = format == null ? "png" : format.trim().toLowerCase(Locale.ROOT);
        String filenameBase = sanitizeFilename(PublicDiagramShareResponse.from(share).title()) + "-v" + share.getDiagramVersion().getVersionNumber();
        return switch (normalized) {
            case "png" -> bytes(
                    renderingService.renderToPng(share.getDiagramVersion().getSourceCode()),
                    MediaType.IMAGE_PNG,
                    filenameBase + ".png");
            case "svg" -> bytes(
                    renderingService.renderToSvg(share.getDiagramVersion().getSourceCode()),
                    MediaType.valueOf("image/svg+xml"),
                    filenameBase + ".svg");
            case "drawio" -> bytes(
                    drawIoExportService.convertPlantUmlToDrawIoXml(
                            share.getDiagramVersion().getSourceCode(),
                            share.getDiagram().getDiagramType()).getBytes(StandardCharsets.UTF_8),
                    MediaType.APPLICATION_XML,
                    filenameBase + ".drawio");
            default -> throw new DiagramShareException(HttpStatus.BAD_REQUEST, "INVALID_DOWNLOAD_FORMAT", "Unsupported download format");
        };
    }

    private static void requirePlantUml(DiagramShare share) {
        if (share.getDiagramVersion().getSourceFormat() != DiagramSourceFormat.PLANTUML) {
            throw new DiagramShareException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_SOURCE_FORMAT", "Shared diagram format is not supported");
        }
    }

    private static ResponseEntity<byte[]> bytes(byte[] body, MediaType contentType, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(contentType)
                .body(body);
    }

    private static String sanitizeFilename(String value) {
        String normalized = value == null ? "shared-diagram" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "shared-diagram" : normalized;
    }
}
