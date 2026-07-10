package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.service.generation.model.CollaborationConnection;
import com.example.aidiagramgenerator.service.generation.model.CollaborationMessage;
import com.example.aidiagramgenerator.service.generation.model.CollaborationParticipant;

import java.util.List;

/**
 * Service for generating PlantUML collaboration (communication) diagrams from
 * natural language or semi-structured text.
 *
 * <p>A collaboration diagram shows objects interacting via numbered messages:
 * <ul>
 *   <li><b>Objects:</b> each detected participant becomes an {@code object} block</li>
 *   <li><b>Numbered messages:</b> interactions are annotated with sequential numbers
 *       (e.g. {@code 1. request}, {@code 2. query})</li>
 *   <li><b>Links:</b> directional arrows ({@code -->}) connect participating objects</li>
 * </ul>
 *
 * <p>Example output:
 * <pre>
 * {@code
 * @startuml
 * object Client
 * object Server
 * object Database
 *
 * Client --> Server : 1. request
 * Server --> Database : 2. query
 * Database --> Server : 3. result
 * Server --> Client : 4. response
 * @enduml
 * }
 * </pre>
 */
public interface CollaborationDiagramGeneratorService {

    /**
     * Generate a PlantUML collaboration diagram from the given text.
     *
     * @param text natural language description of the object interactions
     * @return valid PlantUML source starting with {@code @startuml} and ending with {@code @enduml}
     */
    String generateCollaborationDiagram(String text);

    /**
     * Extracts the structured participants — objects, actors, and components — from a
     * natural language description of object interactions.
     *
     * <p>Each returned {@link CollaborationParticipant} carries:
     * <ul>
     *   <li>{@code name} — PascalCase identifier (safe for use in PlantUML)</li>
     *   <li>{@code type} — one of {@code PARTICIPANT}, {@code COMPONENT}, or {@code OBJECT}</li>
     *   <li>{@code confidence} — classifier confidence in the type assignment ([0.0, 1.0])</li>
     * </ul>
     *
     * <p>Classification rules:
     * <ul>
     *   <li><b>PARTICIPANT</b> — known human-actor words: {@code User}, {@code Admin},
     *       {@code Customer}, {@code Client}, {@code Operator}, etc.</li>
     *   <li><b>COMPONENT</b> — names whose PascalCase segments end with a technical suffix:
     *       {@code Server}, {@code Service}, {@code Database}, {@code Gateway},
     *       {@code Api}, {@code Cache}, {@code Queue}, {@code Broker}, etc.</li>
     *   <li><b>OBJECT</b> — everything else (e.g. {@code ATM}, {@code Bank}, {@code Order})</li>
     * </ul>
     *
     * @param text natural language description; may be {@code null} or blank
     * @return an ordered, deduplicated list of discovered participants; never {@code null}
     */
    List<CollaborationParticipant> extractParticipants(String text);

    /**
     * Extracts the directed connections — edges — between objects detected in the text.
     *
     * <p>A connection represents a single directed interaction between two participants:
     * {@code source --> target}. Connections are deduplicated by the
     * {@code (source, target)} pair so that repeated mentions of the same edge produce
     * exactly one entry. The first label found for a pair is kept.
     *
     * <p>Detection order (highest to lowest priority):
     * <ol>
     *   <li>Explicit PlantUML arrows: {@code User --> WebServer : login}</li>
     *   <li>Numbered steps: {@code 1. User sends request to WebServer}</li>
     *   <li>Verb-based sentences: {@code WebServer queries SQLServer}</li>
     *   <li>Bidirectional descriptions: {@code A and B communicate} — emits one edge per pair</li>
     * </ol>
     *
     * @param text natural language or semi-structured description; may be {@code null} or blank
     * @return an ordered, deduplicated list of directed connections; never {@code null}
     */
    List<CollaborationConnection> extractConnections(String text);

    /**
     * Extracts hierarchically numbered messages from the text, preserving sequence order.
     *
     * <p>Supports two input styles:
     * <ul>
     *   <li><b>Arrow + number:</b> {@code User --> WebServer : 1.1: searchMessage()} —
     *       source, target, sequence number, and label are all captured.</li>
     *   <li><b>Bare number:</b> {@code 1.1: createSQLQuery()} — sequence number and label
     *       are captured; source/target are {@code null}.</li>
     * </ul>
     *
     * <p>Messages are sorted by their hierarchical sequence number so that
     * {@code "1" < "1.1" < "1.2" < "2" < "2.1"} regardless of the order they appear
     * in the input. Duplicate sequence numbers retain only the first occurrence.
     *
     * @param text natural language or semi-structured description; may be {@code null} or blank
     * @return an ordered, deduplicated list of messages; never {@code null}
     */
    List<CollaborationMessage> extractMessages(String text);

    /**
     * Extracts only the self-call messages — interactions where an object invokes a method
     * on itself — from the text.
     *
     * <p>Self-calls are detected from three input styles (in priority order):
     * <ol>
     *   <li><b>Bare numbered method messages</b> resolved via parent-sequence lookup:
     *       if {@code 1.1: createSQLQuery()} is a sub-message of
     *       {@code User --> WebServer : 1: searchMessage()}, it becomes
     *       {@code WebServer --> WebServer : 1.1: createSQLQuery()}</li>
     *   <li><b>Explicit dot-call notation</b>: {@code WebServer.createSQLQuery()} →
     *       {@code WebServer --> WebServer : createSQLQuery()}</li>
     *   <li><b>Standalone method lines</b> (no sequence number, no dot prefix):
     *       {@code createSQLQuery()} on a line after an arrow to {@code WebServer} →
     *       {@code WebServer --> WebServer : createSQLQuery()}</li>
     * </ol>
     *
     * <p>Results are deduplicated by {@code (object, method)} pair and sorted by
     * {@link CollaborationMessage#SEQUENCE_ORDER}.
     *
     * @param text natural language or semi-structured description; may be {@code null} or blank
     * @return an ordered, deduplicated list of self-call messages; never {@code null}
     */
    List<CollaborationMessage> extractSelfCalls(String text);
}
