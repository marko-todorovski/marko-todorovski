package com.example.aidiagramgenerator.service.render;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.DiagramDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Implementation of DiagramRenderingService using the PlantUML library.
 * Converts PlantUML text to PNG and SVG image formats.
 */
@Service
public class DiagramRenderingServiceImpl implements DiagramRenderingService {

    private static final Logger logger = LoggerFactory.getLogger(DiagramRenderingServiceImpl.class);

    private static final int MAX_OUTPUT_SIZE = 10 * 1024 * 1024; // 10 MB max output

    @Override
    public byte[] renderToPng(String plantUml) {
        logger.debug("Rendering PlantUML to PNG");
        validateInput(plantUml);

        try {
            byte[] result = render(plantUml, FileFormat.PNG);
            logger.info("Successfully rendered PNG image ({} bytes)", result.length);
            return result;
        } catch (DiagramRenderingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to render PNG: {}", e.getMessage(), e);
            throw new DiagramRenderingException(
                    "Failed to render diagram to PNG: " + e.getMessage(),
                    e,
                    plantUml,
                    DiagramRenderingException.RenderingErrorType.RENDERING_ERROR
            );
        }
    }

    @Override
    public byte[] renderToSvg(String plantUml) {
        logger.debug("Rendering PlantUML to SVG");
        validateInput(plantUml);

        try {
            byte[] result = render(plantUml, FileFormat.SVG);
            logger.info("Successfully rendered SVG image ({} bytes)", result.length);
            return result;
        } catch (DiagramRenderingException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to render SVG: {}", e.getMessage(), e);
            throw new DiagramRenderingException(
                    "Failed to render diagram to SVG: " + e.getMessage(),
                    e,
                    plantUml,
                    DiagramRenderingException.RenderingErrorType.RENDERING_ERROR
            );
        }
    }

    /**
     * Validates the input PlantUML code.
     *
     * @param plantUml the PlantUML code to validate
     * @throws IllegalArgumentException if plantUml is null or blank
     */
    private void validateInput(String plantUml) {
        if (plantUml == null || plantUml.isBlank()) {
            logger.error("Rendering failed: PlantUML code is null or blank");
            throw new IllegalArgumentException("PlantUML code cannot be null or blank");
        }

        // Basic syntax validation
        String trimmed = plantUml.trim();
        if (!trimmed.startsWith("@start")) {
            logger.warn("PlantUML code may be invalid: does not start with @start directive");
        }
    }

    /**
     * Renders PlantUML code to the specified format.
     *
     * @param plantUml the PlantUML code
     * @param format   the target file format
     * @return the rendered image as a byte array
     * @throws DiagramRenderingException if rendering fails
     */
    private byte[] render(String plantUml, FileFormat format) {
        logger.trace("Rendering to format: {}", format);

        SourceStringReader reader = new SourceStringReader(plantUml);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(8192)) {
            // Render the diagram
            DiagramDescription description = reader.outputImage(
                    outputStream,
                    new FileFormatOption(format)
            );

            // Check for rendering errors
            if (description == null) {
                throw new DiagramRenderingException(
                        "Rendering produced no output - possible syntax error in PlantUML code",
                        plantUml,
                        DiagramRenderingException.RenderingErrorType.INVALID_SYNTAX
                );
            }

            String descriptionText = description.getDescription();
            if (descriptionText != null && descriptionText.toLowerCase().contains("error")) {
                logger.warn("Rendering produced warning/error: {}", descriptionText);
            }

            byte[] result = outputStream.toByteArray();

            // Validate output
            if (result.length == 0) {
                throw new DiagramRenderingException(
                        "Rendering produced empty output",
                        plantUml,
                        DiagramRenderingException.RenderingErrorType.OUTPUT_ERROR
                );
            }

            if (result.length > MAX_OUTPUT_SIZE) {
                throw new DiagramRenderingException(
                        "Rendered output exceeds maximum size limit (" + MAX_OUTPUT_SIZE + " bytes)",
                        plantUml,
                        DiagramRenderingException.RenderingErrorType.OUTPUT_ERROR
                );
            }

            logger.debug("Rendering complete: {} bytes, description: {}", result.length, descriptionText);
            return result;

        } catch (IOException e) {
            logger.error("I/O error during rendering: {}", e.getMessage(), e);
            throw new DiagramRenderingException(
                    "I/O error during diagram rendering: " + e.getMessage(),
                    e,
                    plantUml,
                    DiagramRenderingException.RenderingErrorType.OUTPUT_ERROR
            );
        }
    }
}
