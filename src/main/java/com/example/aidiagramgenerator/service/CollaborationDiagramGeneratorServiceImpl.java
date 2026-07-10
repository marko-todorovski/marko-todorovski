package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.service.generation.model.CollaborationConnection;
import com.example.aidiagramgenerator.service.generation.model.CollaborationMessage;
import com.example.aidiagramgenerator.service.generation.model.CollaborationParticipant;
import com.example.aidiagramgenerator.service.generation.model.CollaborationParticipant.ParticipantType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link CollaborationDiagramGeneratorService}.
 *
 * <p>Parses natural language descriptions of object interactions and generates
 * academically correct PlantUML collaboration (communication) diagram syntax.
 *
 * <h3>Supported input styles</h3>
 * <ul>
 *   <li><b>Explicit arrow:</b> {@code ObjectA --> ObjectB : action} — passed through directly</li>
 *   <li><b>Verb-based interaction:</b> {@code "Client sends request to Server"} — subject,
 *       verb, and target are extracted; the verb becomes the message label</li>
 *   <li><b>Numbered step lists:</b> {@code "1. Client requests auth from Server"} — step
 *       numbers are preserved as message sequence numbers</li>
 *   <li><b>Plain participant list:</b> comma- or newline-separated names without explicit
 *       interactions — objects are chained left-to-right with generic message labels</li>
 * </ul>
 *
 * <h3>Fallback behaviour</h3>
 * <p>When the input does not yield any participants, a canonical
 * Client → Server → Database example is emitted so the output is always valid PlantUML.
 */
@Service
public class CollaborationDiagramGeneratorServiceImpl implements CollaborationDiagramGeneratorService {

    // ── Interaction patterns ───────────────────────────────────────────────

    /** Explicit PlantUML arrow: {@code ObjectA -> ObjectB : label} or {@code ObjectA --> ObjectB : label} */
    private static final Pattern EXPLICIT_ARROW = Pattern.compile(
            "([A-Za-z]\\w+)\\s*--?>\\s*([A-Za-z]\\w+)\\s*:\\s*(.+)");

    /**
     * Arrow combined with a hierarchical sequence number in the label:
     * {@code User -> WebServer : 1.1 searchMessage()} or with {@code -->}
     * Groups: (source, target, sequenceNumber, label)
     */
    private static final Pattern ARROW_NUMBERED_MESSAGE = Pattern.compile(
            "([A-Za-z]\\w+)\\s*--?>\\s*([A-Za-z]\\w+)\\s*:\\s*(\\d+(?:\\.\\d+)*)\\s*[:.\\s]\\s*(.+)");

    /**
     * Bare hierarchical sequence label without explicit participants:
     * {@code 1: searchMessage()} or {@code 1.1: createSQLQuery()}
     * Groups: (sequenceNumber, label)
     * Uses a colon as separator to avoid false matches with sentence-form steps.
     */
    private static final Pattern BARE_NUMBERED_MESSAGE = Pattern.compile(
            "^(\\d+(?:\\.\\d+)*)\\s*:\\s*(.+)$",
            Pattern.MULTILINE);

    /**
     * Numbered step: {@code "1. Client sends request to Server"}
     * Groups: (stepNum, subject, verb, object, target)
     */
    private static final Pattern NUMBERED_STEP = Pattern.compile(
            "^(\\d+)[.)\\s]+([A-Z]\\w+)\\s+(\\w+(?:\\s+\\w+)?)\\s+(?:to|from)\\s+([A-Z]\\w+)",
            Pattern.MULTILINE);

    /**
     * Verb-based interaction: {@code "Subject verb(s) [the] object to/from Target"}
     * e.g. "Client sends request to Server", "Server returns result to Client"
     */
    private static final Pattern VERB_INTERACTION = Pattern.compile(
            "\\b([A-Z]\\w+)\\s+(sends?|calls?|returns?|requests?|responds?|invokes?|notifies?" +
            "|forwards?|delegates?|acknowledges?|queries?|fetches?|posts?|gets?)" +
            "(?:\\s+(?:a|an|the))?" +
            "(?:\\s+(\\w+(?:\\s+\\w+)?))?\\s+(?:to|from)\\s+([A-Z]\\w+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Bidirectional description: {@code "X and Y communicate"} or {@code "X interacts with Y"}
     */
    private static final Pattern BIDIRECTIONAL = Pattern.compile(
            "\\b([A-Z]\\w+)\\s+(?:and\\s+([A-Z]\\w+)\\s+)?(?:communicate|interact|exchange|connect)",
            Pattern.CASE_INSENSITIVE);

    /** A comma/newline/semicolon-separated list of capitalised names */
    private static final Pattern PARTICIPANT_LIST = Pattern.compile(
            "\\b([A-Z][a-zA-Z0-9]*)\\b");

    // ── Self-call patterns ─────────────────────────────────────────────────

    /**
     * Detects a method-call-like label: a word followed immediately by parentheses,
     * with optional arguments.  Examples: {@code createSQLQuery()}, {@code load(id)}.
     */
    private static final Pattern METHOD_CALL_LABEL = Pattern.compile(
            "^[a-zA-Z_]\\w*\\s*\\([^)]*\\)$");

    /**
     * Explicit dot-notation self-call: {@code WebServer.createSQLQuery()}
     * Groups: (objectName, methodCall)
     */
    private static final Pattern EXPLICIT_DOT_CALL = Pattern.compile(
            "\\b([A-Z]\\w+)\\.([a-zA-Z_]\\w*\\s*\\([^)]*\\))");

    /**
     * A standalone method call on its own line with no sequence number and no object prefix.
     * Example: {@code createSQLQuery()} or {@code executeSQLQuery(id)}.
     * Group 1: the full method call string.
     */
    private static final Pattern STANDALONE_METHOD_LINE = Pattern.compile(
            "^\\s*([a-zA-Z_]\\w*\\s*\\([^)]*\\))\\s*$",
            Pattern.MULTILINE);

    /**
     * Extracts a UML guard condition from the start of a raw label string.
     * Matches: {@code [condition] label}
     * Groups: (1=condition text, 2=remaining label)
     */
    private static final Pattern CONDITION_BRACKET = Pattern.compile(
            "^\\[([^\\]]+)\\]\\s+(.+)$");

    /**
     * Bare numbered message that uses a space separator and leads with a guard condition.
     * Matches: {@code 5.1 [amount > 1000] askForConfirmation()}
     * Groups: (1=sequenceNumber, 2=condition, 3=label)
     */
    private static final Pattern BARE_SPACE_SEQ_CONDITIONAL = Pattern.compile(
            "^(\\d+(?:\\.\\d+)*)\\s+\\[([^\\]]+)\\]\\s+(.+)$",
            Pattern.MULTILINE);

    // ── Sanitisation ──────────────────────────────────────────────────────

    /** Strips characters not safe in PlantUML identifiers */
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9_]");

    /** Splits a PascalCase / camelCase word into its constituent segments. */
    private static final Pattern PASCAL_SPLIT = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    // ── Participant classification ─────────────────────────────────────────

    /**
     * Lowercase names that unambiguously identify a human actor.
     * Matched against the full lowercased participant name.
     */
    private static final Set<String> HUMAN_ACTOR_NAMES = Set.of(
            "user", "admin", "administrator", "customer", "client", "actor",
            "person", "staff", "employee", "manager", "operator", "guest",
            "member", "visitor", "buyer", "seller", "agent", "owner",
            "cashier", "teller", "banker", "merchant", "consumer", "provider"
    );

    /**
     * Lowercase segment suffixes that indicate a technical component.
     * Matched against <em>each segment</em> of the PascalCase-split name.
     */
    private static final Set<String> COMPONENT_SUFFIXES = Set.of(
            "server", "service", "database", "db", "api", "gateway", "queue",
            "cache", "proxy", "broker", "bus", "system", "module", "engine",
            "handler", "repository", "store", "registry", "pool", "cluster",
            "node", "endpoint", "interface", "controller", "adapter",
            "processor", "dispatcher", "scheduler", "monitor",
            "backend", "frontend", "middleware", "platform", "layer"
    );

    // ── Stop-words excluded from participant auto-detection ────────────────

    private static final List<String> STOP_WORDS = Arrays.asList(
            "The", "A", "An", "This", "That", "These", "Those",
            "Please", "Note", "Here", "Now", "Then", "After", "Before",
            "When", "While", "If", "Each", "All", "Any", "No",
            "Object", "Objects", "Message", "Messages", "Diagram",
            "System", "Flow", "Step", "Steps", "Result", "Results"
    );

    // ── Public API ────────────────────────────────────────────────────────

    @Override
    public String generateCollaborationDiagram(String text) {
        if (text == null || text.isBlank()) {
            return buildDefault();
        }

        // 0. Try arrow + hierarchical numbered messages (highest specificity)
        List<CollaborationMessage> numberedMessages = parseArrowNumberedMessages(text);
        if (!numberedMessages.isEmpty()) {
            return buildFromNumberedMessages(numberedMessages);
        }

        // 1. Try to find explicit PlantUML arrows first
        List<String[]> explicitMessages = parseExplicitArrows(text);
        if (!explicitMessages.isEmpty()) {
            return buildFromExplicitMessages(explicitMessages);
        }

        // 2. Try numbered steps
        List<String[]> numberedSteps = parseNumberedSteps(text);
        if (!numberedSteps.isEmpty()) {
            return buildFromMessages(numberedSteps, collectParticipantsFromMessages(numberedSteps));
        }

        // 3. Try verb-based interactions
        List<String[]> verbMessages = parseVerbInteractions(text);
        if (!verbMessages.isEmpty()) {
            return buildFromMessages(verbMessages, collectParticipantsFromMessages(verbMessages));
        }

        // 4. Try bidirectional descriptions
        List<String> bidirectional = parseBidirectional(text);
        if (!bidirectional.isEmpty()) {
            return buildChain(bidirectional, List.of("communicate"));
        }

        // 5. Fall back to structured participant extraction — chain in discovery order
        List<CollaborationParticipant> extracted = extractParticipants(text);
        if (!extracted.isEmpty()) {
            List<String> names = extracted.stream().map(CollaborationParticipant::getName).toList();
            return buildChain(names, List.of());
        }

        return buildDefault();
    }

    @Override
    public List<CollaborationParticipant> extractParticipants(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Collect candidate names from all strategies in discovery order,
        // deduplicating by name (case-insensitive).
        Map<String, String> seen = new LinkedHashMap<>(); // normalised-lower → original

        // Explicit arrows: source and target are definitive participants
        parseExplicitArrows(text).forEach(msg -> {
            seen.putIfAbsent(msg[0].toLowerCase(), msg[0]);
            seen.putIfAbsent(msg[1].toLowerCase(), msg[1]);
        });

        // Numbered steps: subject and target
        parseNumberedSteps(text).forEach(msg -> {
            seen.putIfAbsent(msg[0].toLowerCase(), msg[0]);
            seen.putIfAbsent(msg[1].toLowerCase(), msg[1]);
        });

        // Verb-based interactions: subject and target
        parseVerbInteractions(text).forEach(msg -> {
            seen.putIfAbsent(msg[0].toLowerCase(), msg[0]);
            seen.putIfAbsent(msg[1].toLowerCase(), msg[1]);
        });

        // Bidirectional mentions
        parseBidirectional(text).forEach(name ->
                seen.putIfAbsent(name.toLowerCase(), name));

        // Capitalised-word scan (lowest priority)
        parseParticipantList(text).forEach(name ->
                seen.putIfAbsent(name.toLowerCase(), name));

        // Classify every discovered name
        List<CollaborationParticipant> result = new ArrayList<>();
        for (String canonical : seen.values()) {
            result.add(classify(canonical));
        }
        return result;
    }

    @Override
    public List<CollaborationMessage> extractMessages(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Phase 1 & 2: collect all sequence-keyed messages (first occurrence wins)
        Map<String, CollaborationMessage> seen = new LinkedHashMap<>();

        // Phase 1: Arrow + sequence number — captures source, target and optional condition
        Matcher arrowMatcher = ARROW_NUMBERED_MESSAGE.matcher(text);
        while (arrowMatcher.find()) {
            String src             = sanitize(arrowMatcher.group(1));
            String tgt             = sanitize(arrowMatcher.group(2));
            String seq             = arrowMatcher.group(3).trim();
            String[] condAndLabel  = parseConditionLabel(arrowMatcher.group(4).trim());
            seen.putIfAbsent(seq, new CollaborationMessage(seq, condAndLabel[1], src, tgt, condAndLabel[0]));
        }

        // Phase 2: Bare sequence label with colon — no source/target, optional condition
        Matcher bareMatcher = BARE_NUMBERED_MESSAGE.matcher(text);
        while (bareMatcher.find()) {
            String seq            = bareMatcher.group(1).trim();
            String[] condAndLabel = parseConditionLabel(bareMatcher.group(2).trim());
            seen.putIfAbsent(seq, new CollaborationMessage(seq, condAndLabel[1], null, null, condAndLabel[0]));
        }

        // Phase 2.5: Bare space-separated conditional — "5.1 [amount > 1000] askForConfirmation()"
        Matcher spaceCondMatcher = BARE_SPACE_SEQ_CONDITIONAL.matcher(text);
        while (spaceCondMatcher.find()) {
            String seq  = spaceCondMatcher.group(1).trim();
            String cond = spaceCondMatcher.group(2).trim();
            String lbl  = spaceCondMatcher.group(3).trim();
            seen.putIfAbsent(seq, new CollaborationMessage(seq, lbl, null, null, cond));
        }

        // Phase 3: build seq → target lookup from messages that already have a known target
        Map<String, String> seqToTarget = new HashMap<>();
        for (CollaborationMessage m : seen.values()) {
            if (m.getTarget() != null) {
                seqToTarget.put(m.getSequenceNumber(), m.getTarget());
            }
        }

        // Phase 4: promote bare method-call messages to self-calls by walking up
        //          the sequence hierarchy to find the nearest ancestor's target object.
        //          Example: bare "1.1: createSQLQuery()" whose parent "1" targets WebServer
        //          becomes: WebServer -> WebServer : 1.1 [condition] createSQLQuery()
        //          Conditions are preserved on the promoted message.
        Map<String, CollaborationMessage> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, CollaborationMessage> entry : seen.entrySet()) {
            CollaborationMessage msg = entry.getValue();
            if (msg.getSource() == null && isMethodCallLabel(msg.getLabel())) {
                String contextObj = resolveContextObject(msg.getSequenceNumber(), seqToTarget);
                if (contextObj != null) {
                    resolved.put(entry.getKey(), new CollaborationMessage(
                            msg.getSequenceNumber(), msg.getLabel(), contextObj, contextObj,
                            msg.getCondition()));
                    continue;
                }
            }
            resolved.put(entry.getKey(), msg);
        }

        // Phase 5: explicit dot-call self-calls — WebServer.createSQLQuery()
        int autoSeq = 100;
        Matcher dotMatcher = EXPLICIT_DOT_CALL.matcher(text);
        while (dotMatcher.find()) {
            String obj    = sanitize(dotMatcher.group(1));
            String method = dotMatcher.group(2).trim();
            String key    = String.valueOf(autoSeq++);
            resolved.putIfAbsent(key, new CollaborationMessage(key, method, obj, obj));
        }

        return resolved.values().stream()
                .sorted(CollaborationMessage.SEQUENCE_ORDER)
                .toList();
    }

    @Override
    public List<CollaborationMessage> extractSelfCalls(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Keyed by "object|method" to deduplicate across all detection strategies.
        Map<String, CollaborationMessage> selfCalls = new LinkedHashMap<>();

        // Phase 1: self-calls already resolved by extractMessages
        //          (covers parent-resolved bare method messages + explicit dot calls)
        for (CollaborationMessage msg : extractMessages(text)) {
            if (msg.isSelfCall()) {
                selfCalls.putIfAbsent(msg.getSource() + "|" + msg.getLabel(), msg);
            }
        }

        // Phase 2: standalone method lines — un-numbered, no dot prefix.
        //          Track the "current object" (target of the most recent arrow) so that
        //          a bare line like "createSQLQuery()" can be attributed to that object.
        String currentObject = null;
        int autoIndex = 200;
        for (String line : text.split("\\r?\\n")) {
            Matcher arrowM = ARROW_NUMBERED_MESSAGE.matcher(line);
            if (arrowM.find()) {
                currentObject = sanitize(arrowM.group(2));
                continue;
            }
            Matcher explicitM = EXPLICIT_ARROW.matcher(line);
            if (explicitM.find()) {
                currentObject = sanitize(explicitM.group(2));
                continue;
            }
            Matcher standaloneM = STANDALONE_METHOD_LINE.matcher(line);
            if (standaloneM.find() && currentObject != null) {
                String method    = standaloneM.group(1).trim();
                String dedupeKey = currentObject + "|" + method;
                if (!selfCalls.containsKey(dedupeKey)) {
                    String syntheticSeq = String.valueOf(autoIndex++);
                    selfCalls.put(dedupeKey,
                            new CollaborationMessage(syntheticSeq, method, currentObject, currentObject));
                }
            }
        }

        return selfCalls.values().stream()
                .sorted(CollaborationMessage.SEQUENCE_ORDER)
                .toList();
    }

    @Override
    public List<CollaborationConnection> extractConnections(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Use a LinkedHashMap keyed by "source|target" to preserve insertion order
        // and ensure (source, target) uniqueness. The first label found wins.
        Map<String, CollaborationConnection> seen = new LinkedHashMap<>();

        // 1. Explicit PlantUML arrows — highest confidence, keep label as-is
        for (String[] msg : parseExplicitArrows(text)) {
            String key = msg[0] + "|" + msg[1];
            seen.putIfAbsent(key, new CollaborationConnection(msg[0], msg[1], msg[2]));
        }

        // 2. Numbered steps — label is "<step>. <verb>"
        for (String[] msg : parseNumberedSteps(text)) {
            String key = msg[0] + "|" + msg[1];
            seen.putIfAbsent(key, new CollaborationConnection(msg[0], msg[1], msg[2]));
        }

        // 3. Verb-based interactions — label is the detected action phrase
        for (String[] msg : parseVerbInteractions(text)) {
            String key = msg[0] + "|" + msg[1];
            seen.putIfAbsent(key, new CollaborationConnection(msg[0], msg[1], msg[2]));
        }

        // 4. Bidirectional descriptions — emit one unlabelled edge per adjacent pair
        List<String> bidir = parseBidirectional(text);
        for (int i = 0; i < bidir.size() - 1; i++) {
            String src = bidir.get(i);
            String tgt = bidir.get(i + 1);
            seen.putIfAbsent(src + "|" + tgt, new CollaborationConnection(src, tgt));
        }

        return new ArrayList<>(seen.values());
    }

    // ── Parsers ───────────────────────────────────────────────────────────

    private List<String[]> parseExplicitArrows(String text) {
        List<String[]> results = new ArrayList<>();
        Matcher m = EXPLICIT_ARROW.matcher(text);
        while (m.find()) {
            results.add(new String[]{sanitize(m.group(1)), sanitize(m.group(2)), m.group(3).trim()});
        }
        return results;
    }

    private List<String[]> parseNumberedSteps(String text) {
        List<String[]> results = new ArrayList<>();
        Matcher m = NUMBERED_STEP.matcher(text);
        while (m.find()) {
            String stepNum = m.group(1);
            String subject = sanitize(m.group(2));
            String verb    = m.group(3).trim();
            String target  = sanitize(m.group(4));
            results.add(new String[]{subject, target, stepNum + " " + verb});
        }
        return results;
    }

    private List<String[]> parseVerbInteractions(String text) {
        List<String[]> results = new ArrayList<>();
        Matcher m = VERB_INTERACTION.matcher(text);
        int msgNum = 1;
        while (m.find()) {
            String subject = sanitize(m.group(1));
            String verb    = m.group(2).toLowerCase();
            String object  = m.group(3) != null ? m.group(3).trim() : verb;
            String target  = sanitize(m.group(4));
            results.add(new String[]{subject, target, msgNum + " " + object});
            msgNum++;
        }
        return results;
    }

    private List<String> parseBidirectional(String text) {
        List<String> participants = new ArrayList<>();
        Matcher m = BIDIRECTIONAL.matcher(text);
        while (m.find()) {
            String a = sanitize(m.group(1));
            if (!participants.contains(a)) participants.add(a);
            if (m.group(2) != null) {
                String b = sanitize(m.group(2));
                if (!participants.contains(b)) participants.add(b);
            }
        }
        return participants;
    }

    private List<String> parseParticipantList(String text) {
        List<String> participants = new ArrayList<>();
        Matcher m = PARTICIPANT_LIST.matcher(text);
        while (m.find()) {
            String word = m.group(1);
            if (!STOP_WORDS.contains(word) && !participants.contains(word)) {
                participants.add(word);
            }
        }
        return participants;
    }

    // ── Builders ──────────────────────────────────────────────────────────

    /**
     * Returns the shared PlantUML skin directives applied to every generated diagram.
     * Centralised here so all builders stay consistent without duplication.
     */
    private static String skinParams() {
        return "skinparam object {\n" +
               "  BackgroundColor #F8F9FA\n" +
               "  BorderColor #495057\n" +
               "  FontSize 13\n" +
               "  FontName Arial\n" +
               "}\n" +
               "skinparam ArrowColor #343A40\n" +
               "skinparam ArrowFontSize 12\n" +
               "skinparam ArrowFontColor #212529\n" +
               "skinparam Padding 8\n" +
               "skinparam ObjectSpacing 60\n" +
               "skinparam linetype ortho\n";
    }

    /**
     * Builds PlantUML from hierarchically numbered messages that carry source/target.
     * Participants are declared in the order they first appear across the message list.
     * Messages are emitted in their sorted sequence order.
     */
    private String buildFromNumberedMessages(List<CollaborationMessage> messages) {
        Map<String, Boolean> participantsSeen = new LinkedHashMap<>();
        for (CollaborationMessage msg : messages) {
            if (msg.getSource() != null) participantsSeen.put(msg.getSource(), true);
            if (msg.getTarget() != null) participantsSeen.put(msg.getTarget(), true);
        }
        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append(skinParams());
        sb.append("\n");
        for (String p : participantsSeen.keySet()) {
            sb.append("object ").append(p).append("\n");
        }
        sb.append("\n");
        for (CollaborationMessage msg : messages) {
            sb.append(msg.toPlantUml()).append("\n");
        }
        sb.append("@enduml");
        return sb.toString();
    }

    private String buildFromExplicitMessages(List<String[]> messages) {
        List<String> participants = collectParticipantsFromMessages(messages);
        return buildFromMessages(messages, participants);
    }

    private String buildFromMessages(List<String[]> messages, List<String> participants) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append(skinParams());
        sb.append("\n");
        for (String p : participants) {
            sb.append("object ").append(p).append("\n");
        }
        sb.append("\n");
        for (String[] msg : messages) {
            sb.append(msg[0]).append(" -> ").append(msg[1]).append(" : ").append(msg[2]).append("\n");
        }
        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Chains a list of participants left-to-right with numbered generic message labels.
     * Actions, when provided, are used as message labels in order.
     */
    private String buildChain(List<String> participants, List<String> actions) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append(skinParams());
        sb.append("\n");
        for (String p : participants) {
            sb.append("object ").append(p).append("\n");
        }
        sb.append("\n");
        for (int i = 0; i < participants.size() - 1; i++) {
            String label = (i < actions.size() && !actions.get(i).isBlank())
                    ? actions.get(i)
                    : "message";
            sb.append(participants.get(i))
              .append(" -> ")
              .append(participants.get(i + 1))
              .append(" : ").append(i + 1).append(" ").append(label)
              .append("\n");
        }
        sb.append("@enduml");
        return sb.toString();
    }

    private String buildDefault() {
        return "@startuml\n" +
               skinParams() +
               "\n" +
               "object User\n" +
               "object WebServer\n" +
               "object SQLServer\n" +
               "object TransactionServer\n" +
               "\n" +
               "User -> WebServer : 1 initiatePayment()\n" +
               "WebServer -> WebServer : 1.1 validateInput()\n" +
               "WebServer -> SQLServer : 1.2 getUserAccount()\n" +
               "SQLServer -> WebServer : 1.3 accountDetails()\n" +
               "WebServer -> TransactionServer : 2 processPayment()\n" +
               "TransactionServer -> TransactionServer : 2.1 [amount > 1000] requestConfirmation()\n" +
               "TransactionServer -> SQLServer : 2.2 debitAccount()\n" +
               "SQLServer -> TransactionServer : 2.3 debitConfirmed()\n" +
               "TransactionServer -> SQLServer : 2.4 creditRecipient()\n" +
               "SQLServer -> TransactionServer : 2.5 creditConfirmed()\n" +
               "TransactionServer -> WebServer : 3 paymentResult()\n" +
               "WebServer -> SQLServer : 3.1 logTransaction()\n" +
               "WebServer -> User : 4 paymentConfirmation()\n" +
               "@enduml";
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Parses all arrow+numbered messages from the text and returns them sorted
     * by {@link CollaborationMessage#SEQUENCE_ORDER}.
     */
    private List<CollaborationMessage> parseArrowNumberedMessages(String text) {
        List<CollaborationMessage> results = new ArrayList<>();
        Matcher m = ARROW_NUMBERED_MESSAGE.matcher(text);
        while (m.find()) {
            String src = sanitize(m.group(1));
            String tgt             = sanitize(m.group(2));
            String seq             = m.group(3).trim();
            String[] condAndLabel  = parseConditionLabel(m.group(4).trim());
            results.add(new CollaborationMessage(seq, condAndLabel[1], src, tgt, condAndLabel[0]));
        }
        results.sort(CollaborationMessage.SEQUENCE_ORDER);
        return results;
    }

    private List<String> collectParticipantsFromMessages(List<String[]> messages) {
        // Use LinkedHashMap to preserve insertion order and deduplicate
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String[] msg : messages) {
            seen.put(msg[0], true);
            seen.put(msg[1], true);
        }
        return new ArrayList<>(seen.keySet());
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "Unknown";
        return UNSAFE_CHARS.matcher(name.trim()).replaceAll("_");
    }

    /**
     * Returns {@code true} when the label looks like a method call:
     * {@code methodName()}, {@code doSomething(arg)}.
     */
    private static boolean isMethodCallLabel(String label) {
        return METHOD_CALL_LABEL.matcher(label.trim()).matches();
    }

    /**
     * Splits a raw label string into a {@code [condition, cleanLabel]} pair.
     *
     * <p>If the label starts with {@code [condition] label}, the guard condition is extracted
     * and returned separately; otherwise the condition element is {@code null}.
     *
     * <p>Examples:
     * <pre>
     *   "[amount > 1000] askForConfirmation()" → ["amount > 1000", "askForConfirmation()"]
     *   "searchMessage()"                      → [null,            "searchMessage()"]
     * </pre>
     *
     * @param raw the raw label string as captured from any parsing pattern
     * @return two-element array where index 0 is the condition (or {@code null}) and
     *         index 1 is the clean label text
     */
    private static String[] parseConditionLabel(String raw) {
        Matcher m = CONDITION_BRACKET.matcher(raw.trim());
        if (m.matches()) {
            return new String[]{m.group(1).trim(), m.group(2).trim()};
        }
        return new String[]{null, raw.trim()};
    }

    /**
     * Walks up the sequence-number hierarchy to find the nearest ancestor whose
     * target object is known in {@code seqToTarget}.
     *
     * <p>For {@code "1.2.3"} the walk order is {@code "1.2"} → {@code "1"} → {@code null}.
     *
     * @param seq         the sequence number of the message to resolve
     * @param seqToTarget map of sequence number → target object name (from arrow messages)
     * @return the target object of the nearest ancestor, or {@code null} if none is found
     */
    private static String resolveContextObject(String seq, Map<String, String> seqToTarget) {
        String current = seq;
        while (true) {
            int lastDot = current.lastIndexOf('.');
            if (lastDot < 0) return null; // top-level: no parent exists
            current = current.substring(0, lastDot);
            String target = seqToTarget.get(current);
            if (target != null) return target;
        }
    }

    /**
     * Classifies a participant name into {@link ParticipantType}.
     *
     * <ol>
     *   <li><b>PARTICIPANT</b> – the lowercased name exactly matches a known human-actor word.</li>
     *   <li><b>COMPONENT</b> – at least one PascalCase segment (lowercased) matches a
     *       technical-component suffix, or the all-uppercase abbreviation matches one.</li>
     *   <li><b>OBJECT</b> – everything else.</li>
     * </ol>
     *
     * Confidence is highest for exact actor-name matches (0.95), slightly lower for
     * component-suffix matches (0.85), and lower still for the OBJECT catch-all (0.70).
     */
    private CollaborationParticipant classify(String name) {
        String lower = name.toLowerCase();

        // 1. Known human actor?
        if (HUMAN_ACTOR_NAMES.contains(lower)) {
            return new CollaborationParticipant(name, ParticipantType.PARTICIPANT, 0.95);
        }

        // 2. Any PascalCase segment matches a component suffix?
        //    e.g. "WebServer" → ["Web","Server"] → "server" ∈ COMPONENT_SUFFIXES → COMPONENT
        //    Also handles all-caps abbreviations like "API", "DB".
        String[] segments = PASCAL_SPLIT.split(name);
        for (String seg : segments) {
            if (COMPONENT_SUFFIXES.contains(seg.toLowerCase())) {
                return new CollaborationParticipant(name, ParticipantType.COMPONENT, 0.85);
            }
        }
        // Direct lowercase check covers single-word technical names (e.g. "database", "server")
        if (COMPONENT_SUFFIXES.contains(lower)) {
            return new CollaborationParticipant(name, ParticipantType.COMPONENT, 0.85);
        }

        // 3. Domain object (default)
        return new CollaborationParticipant(name, ParticipantType.OBJECT, 0.70);
    }
}
