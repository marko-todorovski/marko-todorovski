package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.dto.request.DiagramRequest;
import com.example.aidiagramgenerator.dto.request.TextDiagramRequest;
import com.example.aidiagramgenerator.dto.request.UrlDiagramRequest;
import com.example.aidiagramgenerator.dto.request.XmlDiagramRequest;
import com.example.aidiagramgenerator.dto.response.DiagramResponse;
import com.example.aidiagramgenerator.dto.response.OpenAiDiagramResponse;
import com.example.aidiagramgenerator.enums.DiagramType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * Implementation of {@link DiagramService} with a two-tier fallback strategy.
 *
 * <p><b>Primary</b>: {@link OpenAiDiagramService} (LLM generation).
 * <br><b>Fallback</b>: {@link RuleBasedDiagramService} (deterministic, never fails).
 *
 * <p>The service <em>never</em> propagates an exception to the caller — it always
 * returns a valid {@link DiagramResponse}.
 */
@Service
public class DiagramServiceImpl implements DiagramService {

    private static final Logger logger = LoggerFactory.getLogger(DiagramServiceImpl.class);

    private final OpenAiDiagramService openAiDiagramService;
    private final RuleBasedDiagramService ruleBasedDiagramService;
    private final ExecutorService executor;
    private final long timeoutSeconds;

    public DiagramServiceImpl(
            OpenAiDiagramService openAiDiagramService,
            RuleBasedDiagramService ruleBasedDiagramService,
            @Value("${diagram.generation.timeout-seconds:30}") long timeoutSeconds) {
        this.openAiDiagramService = openAiDiagramService;
        this.ruleBasedDiagramService = ruleBasedDiagramService;
        this.timeoutSeconds = timeoutSeconds;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "diagram-llm-worker");
            t.setDaemon(true);
            return t;
        });
        logger.info("DiagramServiceImpl initialised — LLM timeout: {}s", timeoutSeconds);
    }

    // ── DiagramService implementation ─────────────────────────────────────────

    @Override
    public DiagramResponse generateFromText(TextDiagramRequest request) {
        DiagramRequest llmRequest = new DiagramRequest(
                request.getText(),
                Collections.emptyList(),
                Collections.emptyList(),
                request.getDiagramType() != null ? request.getDiagramType().getValue() : null);
        return generateWithFallback(llmRequest);
    }

    @Override
    public DiagramResponse generateFromXml(XmlDiagramRequest request) {
        DiagramRequest llmRequest = new DiagramRequest(
                request.getXml(),
                Collections.emptyList(),
                Collections.emptyList(),
                null);
        return generateWithFallback(llmRequest);
    }

    @Override
    public DiagramResponse generateFromUrl(UrlDiagramRequest request) {
        DiagramRequest llmRequest = new DiagramRequest(
                request.getUrl(),
                Collections.emptyList(),
                Collections.emptyList(),
                null);
        return generateWithFallback(llmRequest);
    }

    // ── Core fallback logic ───────────────────────────────────────────────────

    /**
     * Attempts LLM diagram generation with a timeout.
     * Falls back to {@link RuleBasedDiagramService} when the LLM returns null,
     * an empty result, throws an exception, or exceeds the configured timeout.
     *
     * @param request the structured diagram request
     * @return a guaranteed non-null {@link DiagramResponse}
     */
    DiagramResponse generateWithFallback(DiagramRequest request) {
        Instant start = Instant.now();

        // ── 1. Try LLM generation with timeout ──────────────────────────
        try {
            Future<OpenAiDiagramResponse> future = executor.submit(
                    () -> openAiDiagramService.generateDiagram(request));

            OpenAiDiagramResponse llmResponse = future.get(timeoutSeconds, TimeUnit.SECONDS);
            long elapsed = Duration.between(start, Instant.now()).toMillis();

            // ── 2. Validate response ────────────────────────────────────
            if (llmResponse == null) {
                logger.warn("LLM returned null response after {}ms — falling back to rule-based generation",
                        elapsed);
                return executeFallback(request, "LLM returned null response");
            }

            String code = llmResponse.getPlantUmlCode();
            if (code == null || code.isBlank()) {
                logger.warn("LLM returned empty plantUmlCode after {}ms — falling back to rule-based generation",
                        elapsed);
                return executeFallback(request, "LLM returned empty diagram code");
            }

            // ── 3. LLM succeeded — if it already fell back internally, log it
            if (llmResponse.isFallbackUsed()) {
                logger.info("LLM was unavailable; OpenAiDiagramService used its internal rule-based fallback ({}ms)",
                        elapsed);
            } else {
                logger.info("LLM diagram generated successfully in {}ms (model={})",
                        elapsed, llmResponse.getModelUsed());
            }

            return toResponse(llmResponse);

        } catch (TimeoutException e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            logger.warn("LLM generation timed out after {}ms (limit={}s) — falling back to rule-based generation",
                    elapsed, timeoutSeconds);
            return executeFallback(request, "LLM timed out after " + timeoutSeconds + "s");

        } catch (ExecutionException e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.warn("LLM generation failed after {}ms: {} — falling back to rule-based generation",
                    elapsed, cause.getMessage());
            return executeFallback(request, "LLM error: " + cause.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("LLM generation interrupted — falling back to rule-based generation");
            return executeFallback(request, "LLM generation interrupted");

        } catch (Exception e) {
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            logger.error("Unexpected error during LLM generation after {}ms — falling back to rule-based generation",
                    elapsed, e);
            return executeFallback(request, "Unexpected error: " + e.getMessage());
        }
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private DiagramResponse executeFallback(DiagramRequest request, String reason) {
        logger.info("Executing rule-based fallback — reason: {}", reason);
        try {
            OpenAiDiagramResponse fallback = ruleBasedDiagramService.generate(request);
            DiagramResponse response = toResponse(fallback);
            response.setExplanation("Fallback: " + reason + ". " +
                    (fallback.getExplanation() != null ? fallback.getExplanation() : ""));
            logger.info("Rule-based fallback produced diagram type={}", response.getDiagramType());
            return response;
        } catch (Exception e) {
            // Absolute last resort — system must never fail completely
            logger.error("Rule-based fallback also failed: {} — returning emergency placeholder diagram",
                    e.getMessage(), e);
            return emergencyDiagram(reason);
        }
    }

    private DiagramResponse emergencyDiagram(String reason) {
        DiagramResponse response = new DiagramResponse();
        response.setDiagramType(DiagramType.SEQUENCE);
        response.setMermaidCode("sequenceDiagram\n    participant System\n    System->>System: diagram generation unavailable");
        response.setExplanation("Emergency fallback — both LLM and rule-based generation failed. Reason: " + reason);
        return response;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DiagramResponse toResponse(OpenAiDiagramResponse oai) {
        DiagramResponse response = new DiagramResponse();
        response.setDiagramType(mapDiagramType(oai.getDiagramType()));
        // Prefer plantUmlCode; fall through to mermaidCode if available
        String code = oai.getPlantUmlCode() != null ? oai.getPlantUmlCode() : oai.getMermaidCode();
        response.setMermaidCode(code);
        response.setExplanation(oai.getExplanation());
        return response;
    }

    private DiagramType mapDiagramType(String type) {
        if (type == null) return DiagramType.SEQUENCE;
        return switch (type.toLowerCase()) {
            case "class" -> DiagramType.CLASS;
            case "sequence" -> DiagramType.SEQUENCE;
            case "er", "entity-relationship" -> DiagramType.ER;
            case "architecture", "component" -> DiagramType.ARCHITECTURE;
            case "c4" -> DiagramType.C4;
            default -> {
                logger.debug("Unknown diagram type '{}' — defaulting to SEQUENCE", type);
                yield DiagramType.SEQUENCE;
            }
        };
    }
}
