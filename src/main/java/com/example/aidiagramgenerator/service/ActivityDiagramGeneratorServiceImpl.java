package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link ActivityDiagramGeneratorService}.
 *
 * <p>Parses natural language descriptions and generates academically correct
 * PlantUML activity diagram syntax (modern/beta style).
 *
 * <h3>Supported constructs (detected from natural language)</h3>
 * <ul>
 *   <li><b>Sequential actions</b> — any sentence or numbered step becomes an action node</li>
 *   <li><b>Decision</b> — "if X then Y [else/otherwise Z]", "when X then Y [otherwise Z]",
 *       "X, otherwise Y", "whether X or Y", "choose X or Y", "check if X"</li>
 *   <li><b>Counter loop</b> — "for i = 0 to N [do action]", "loop N times [: action]"</li>
 *   <li><b>While loop</b> — "while X do Y", "while X: body", "while X, body"</li>
 *   <li><b>Repeat-until</b> — "repeat X until Y"</li>
 *   <li><b>For-each loop</b> — "for each X do Y"</li>
 *   <li><b>Fork/join (parallel)</b> — "in parallel A, B and C", "simultaneously A and B",
 *       "concurrently A and B", "at the same time A and B", "A and B concurrently"</li>
 *   <li><b>Swimlanes/partitions</b> — "Actor: action", inline {@code |Lane|} notation,
 *       or explicit {@code partition Name { ... }} blocks</li>
 * </ul>
 *
 * <h3>NLP pre-processing</h3>
 * <p>Before structural parsing the raw text is cleaned:
 * <ul>
 *   <li>Introductory filler sentences ("the process is as follows", "here are the steps",
 *       etc.) are stripped entirely.</li>
 *   <li>Inline transition connectors ("right after that", "subsequently",
 *       "following this", "in turn", etc.) are replaced with newlines so each
 *       clause becomes its own action node.</li>
 *   <li>After splitting, steps that are pure filler words ("finally",
 *       "in conclusion", "note that", etc.) are dropped.</li>
 * </ul>
 *
 * <h3>Parsing strategy</h3>
 * <p>The pre-processed text is first checked for swimlane indicators. If found,
 * each actor's actions are grouped under its lane. Otherwise, the text is scanned
 * for structural constructs (decision → while → repeat-until → for-each → fork)
 * in priority order. The first matching construct is rendered as the central
 * structure, with any text before/after it treated as sequential action nodes.
 * If no structural construct is detected, all clauses are emitted as sequential
 * actions.
 */
@Service
public class ActivityDiagramGeneratorServiceImpl implements ActivityDiagramGeneratorService {

    // ── Structural patterns ────────────────────────────────────────────────

    /**
     * "if <condition> then <action> [else/otherwise <action>]"
     * Limit each group to 100 chars to avoid catastrophic backtracking.
     */
    private static final Pattern IF_THEN_ELSE = Pattern.compile(
            "(?i)\\bif\\s+(.{2,100}?)\\s+then\\s+(.{2,100}?)(?:\\s+(?:else|otherwise)\\s+(.{2,100}?))?[.;]?$",
            Pattern.MULTILINE);

    /**
     * "when <condition> [,] then/do <action> [otherwise/else <alternative>]"
     * e.g. "when user has money, drive with taxi otherwise drive with bus"
     */
    private static final Pattern WHEN_THEN = Pattern.compile(
            "(?i)\\bwhen\\s+(.{2,100}?)\\s*[,]?\\s+(?:then|do)\\s+(.{2,100}?)" +
            "(?:\\s+(?:otherwise|else)\\s+(.{2,100}?))?[.;]?$",
            Pattern.MULTILINE);

    /**
     * "X, otherwise Y" or "X; otherwise Y" — the left side is the action on the
     * happy path, the right side is the alternative.
     * e.g. "Pay by card, otherwise pay by cash."
     */
    private static final Pattern OTHERWISE = Pattern.compile(
            "(?i)(.{2,100}?)[,;]\\s+otherwise\\s+(.{2,100})",
            Pattern.MULTILINE);

    /**
     * "whether <option A> or <option B>"
     * e.g. "decide whether to drive by taxi or take the bus"
     */
    private static final Pattern WHETHER_OR = Pattern.compile(
            "(?i)\\b(?:decide\\s+)?whether\\s+(?:to\\s+)?(.{2,80}?)\\s+or\\s+(?:to\\s+)?(.{2,80})",
            Pattern.MULTILINE);

    /**
     * "choose <option A> or <option B>"
     * e.g. "choose to walk or take the bus"
     */
    private static final Pattern CHOOSE_OR = Pattern.compile(
            "(?i)\\bchoose\\s+(?:to\\s+)?(.{2,80}?)\\s+or\\s+(?:to\\s+)?(.{2,80})",
            Pattern.MULTILINE);

    /** "check/verify/validate/determine if/whether <condition>" */
    private static final Pattern CHECK_IF = Pattern.compile(
            "(?i)\\b(?:check|verify|validate|determine)(?:s|ing)?\\s+(?:if|whether|that)\\s+(.{2,100})",
            Pattern.MULTILINE);

    /** "while <condition> [,] do <action>" */
    private static final Pattern WHILE_LOOP = Pattern.compile(
            "(?i)\\bwhile\\s+(.{2,80}?)\\s*(?:,\\s*|\\s+)do\\s+(.{2,100})",
            Pattern.MULTILINE);

    /** "for each/every <item> [,/:] <action>" */
    private static final Pattern FOR_EACH = Pattern.compile(
            "(?i)\\bfor\\s+(?:each|every)\\s+(.{2,60}?)\\s*[,:]\\s*(.{2,100})",
            Pattern.MULTILINE);

    /** "repeat <action> until <condition>" */
    private static final Pattern REPEAT_UNTIL = Pattern.compile(
            "(?i)\\brepeat\\s+(.{2,100}?)\\s+until\\s+(.{2,100})",
            Pattern.MULTILINE);

    /**
     * "for <var> = <init> to/until <limit> [, do <action>]" — C-style counter loop.
     * e.g. "for i = 0 to 10, do print i" or "for i from 1 to 5: display result"
     */
    private static final Pattern FOR_COUNTER = Pattern.compile(
            "(?i)\\bfor\\s+([a-zA-Z_]\\w*)\\s*(?:=|from)\\s*(\\d+)\\s+(?:to|until)\\s+(\\d+)" +
            "\\s*[,;:]?\\s*(?:do\\s+|doing\\s+)?(.{2,120})?",
            Pattern.MULTILINE);

    /**
     * "loop/repeat/iterate N times [: action]" — count-bounded counter loop.
     * e.g. "loop 5 times: print the value"
     */
    private static final Pattern LOOP_N_TIMES = Pattern.compile(
            "(?i)\\b(?:loop|repeat|iterate)\\s+(\\d+)\\s+times?\\s*[,;:]?\\s*(?:doing\\s+|do\\s+)?(.{2,120})?",
            Pattern.MULTILINE);

    /**
     * "while <condition>[: body]" without explicit "do" keyword — enhanced while.
     * e.g. "while i < 10: print i, i++" or "while queue is not empty, process item"
     * Lower priority than WHILE_LOOP so that "while X do Y" is handled first.
     */
    private static final Pattern WHILE_BARE = Pattern.compile(
            "(?i)\\bwhile\\s+(.{2,80}?)\\s*[,;:]\\s*(?!do\\b)(.{2,120})",
            Pattern.MULTILINE);

    /**
     * Inline increment step: "i++", "counter++", "increment i", "increase count".
     * Used inside loop body parsers to detect existing increment steps.
     */
    private static final Pattern INLINE_INCREMENT = Pattern.compile(
            "(?i)([a-zA-Z_]\\w*)\\s*\\+\\+|(?:increment|increase)\\s+([a-zA-Z_]\\w*)",
            Pattern.MULTILINE);

    /**
     * Trigger keywords that introduce a parallel block on one line.
     * Captures everything after the keyword as a raw branch list.
     * e.g. "in parallel mix ingredients, place cup and preheat oven"
     */
    private static final Pattern FORK_PARALLEL = Pattern.compile(
            "(?i)\\b(?:in\\s+parallel|simultaneously|concurrently|at\\s+the\\s+same\\s+time|in\\s+parallel\\s+execution)" +
            "\\s*[,:]?\\s*(.{4,300})",
            Pattern.MULTILINE);

    /**
     * Multi-line parallel block:
     *   "[do] X and Y [and Z …] [at the same time / in parallel / simultaneously / concurrently]"
     * or the trigger keyword followed by a comma/semicolon list without the explicit "and".
     * e.g. "send email and update database concurrently"
     */
    private static final Pattern FORK_INLINE_AND = Pattern.compile(
            "(?i)(.{2,80}?)\\s+and\\s+(.{2,80}?)" +
            "\\s+(?:in\\s+parallel|simultaneously|concurrently|at\\s+the\\s+same\\s+time|in\\s+parallel\\s+execution)[.;]?",
            Pattern.MULTILINE);

    /**
     * Splits raw branch text produced by FORK_PARALLEL into individual branch strings.
     * Handles: "A, B, and C", "A and B", "A; B; C".
     */
    private static final Pattern BRANCH_SPLIT = Pattern.compile(
            "\\s*(?:,\\s*(?:and\\s+)?|\\s+and\\s+|;\\s*)");

    /** "Actor: action" — swimlane indicator (actor starts with uppercase) */
    private static final Pattern ACTOR_ACTION = Pattern.compile(
            "(?m)^([A-Z][a-zA-Z\\s]*):\\s+(.+)$");

    /** Inline "|Lane|" swimlane notation */
    private static final Pattern INLINE_SWIMLANE = Pattern.compile(
            "\\|([^|\\n]+)\\|");

    /** Explicit "partition LaneName {" input notation */
    private static final Pattern PARTITION_HEADER = Pattern.compile(
            "(?i)\\bpartition\\s+([^{\\n]+?)\\s*\\{");

    /**
     * Role/department keywords that trigger swimlane detection in free text.
     * e.g. "Salesperson: call client" or "Customer does X".
     */
    private static final Pattern ROLE_KEYWORD = Pattern.compile(
            "(?m)^(?:Salesperson|Consultant|Technician|Customer|Manager|Developer|Admin" +
            "|Designer|Analyst|Engineer|User|System|Client|Server|Actor):\\s+");

    // ── NLP pre-processing patterns ─────────────────────────────────────────

    /**
     * Introductory/preamble sentences that open a description with no action content.
     * Stripped entirely before structural parsing.
     * Examples: "The process is as follows:", "Here are the steps:"
     */
    private static final Pattern FILLER_INTRO = Pattern.compile(
            "(?i)" +
            "(?:the\\s+process\\s+(?:is\\s+as\\s+follows|begins?\\s+with|starts?\\s+with|consists?\\s+of)\\s*[:\\-,]?\\s*)" +
            "|(?:the\\s+steps?\\s+(?:are|is)\\s+as\\s+follows\\s*[:\\-,]?\\s*)" +
            "|(?:(?:the\\s+)?(?:following|below)\\s+steps?\\s+describe[^.\\n]*[.:]?\\s*)" +
            "|(?:this\\s+(?:process|workflow|diagram)\\s+(?:involves|describes|shows|illustrates)[^.\\n]*[.:]?\\s*)" +
            "|(?:the\\s+(?:workflow|procedure)\\s+(?:is|involves)\\s*[:\\-,]?\\s*)" +
            "|(?:here\\s+(?:are|is)\\s+the\\s+(?:steps?|activities|actions?)\\s*[:\\-,]?\\s*)" +
            "|(?:the\\s+following\\s+(?:steps?|activities|actions?)\\s*[:\\-,]?\\s*)",
            Pattern.MULTILINE);

    /**
     * Inline transition connectors between steps that carry no action meaning.
     * Replaced with {@code \n} so surrounding clauses split into separate steps.
     * Examples: "right after that", "subsequently", "following this", "in turn"
     */
    private static final Pattern INLINE_FILLER_CONNECTOR = Pattern.compile(
            "(?i)\\s*,?\\s*\\b(?:" +
            "right\\s+after\\s+that|" +
            "subsequently|" +
            "following\\s+(?:this|that)|" +
            "thereafter|" +
            "after\\s+which|" +
            "at\\s+(?:this|that)\\s+(?:point|stage)|" +
            "in\\s+(?:the\\s+)?(?:next|following)\\s+step|" +
            "in\\s+turn|" +
            "moving\\s+(?:on|forward)" +
            ")\\b\\s*,?\\s*",
            Pattern.MULTILINE);

    /**
     * Steps that, after splitting, are pure filler with no UML activity value.
     * Matched steps are dropped entirely from the output.
     * Examples: "finally", "in conclusion", "note that"
     */
    private static final Pattern PURE_FILLER_STEP = Pattern.compile(
            "(?i)^\\s*(?:" +
            "finally|lastly|in\\s+conclusion|in\\s+summary|to\\s+summarize|" +
            "note\\s+that|please\\s+note|importantly|" +
            "additionally|furthermore|moreover|" +
            "as\\s+(?:a\\s+result|mentioned(?:\\s+(?:above|before|earlier))?)" +
            ")\\s*[.,:;!?]?\\s*$");

    // ── Step splitters ─────────────────────────────────────────────────────

    /** Step/clause delimiters used for sequential parsing */
    private static final Pattern STEP_SPLIT = Pattern.compile(
            "(?<=[.!?])\\s+(?=[A-Z])" +          // sentence boundary
            "|\\n{1,2}" +                          // newline
            "|(?<=\\w)[,;]\\s*(?:then|next|and\\s+then|after\\s+that|finally" +
            "|subsequently|following\\s+(?:this|that)|thereafter|right\\s+after\\s+that" +
            "|in\\s+turn|additionally|furthermore)\\s+" +  // connectors
            "|\\b\\d+[.)]\\.?\\s+");               // numbered steps like "1. " or "2)"

    /** Leading connectors stripped from the start of individual step text */
    private static final Pattern LEADING_CONNECTOR = Pattern.compile(
            "^(?:" +
            "then|next|first(?:\\s+of\\s+all)?|finally|lastly|" +
            "after\\s+that|and\\s+then|also|and|" +
            "subsequently|following\\s+(?:this|that)|thereafter|" +
            "right\\s+after\\s+that|after\\s+which|" +
            "at\\s+(?:this|that)\\s+(?:point|stage)|" +
            "in\\s+turn|" +
            "additionally|furthermore|moreover|" +
            "now|so|thus|hence|therefore" +
            ")\\b\\s*[,;]?\\s*",
            Pattern.CASE_INSENSITIVE);

    // ── Visual style ───────────────────────────────────────────────────────

    /**
     * skinparam block prepended to every generated diagram for academic/clean visual output.
     * ActivityBorderRadius 5  — gently rounded action-node corners.
     * ActivityFontSize 12     — legible default font.
     * ShadowingEnabled false  — flat, shadow-free look used in textbook diagrams.
     */
    private static final String SKINPARAM =
            "skinparam ActivityBorderRadius 5\n" +
            "skinparam ActivityFontSize 12\n" +
            "skinparam ShadowingEnabled false\n" +
            "\n";

    // ── Public API ─────────────────────────────────────────────────────────

    @Override
    public String generateActivityDiagram(String text) {
        if (text == null || text.isBlank()) {
            return defaultDiagram();
        }

        String normalized = preprocess(text.strip());
        if (normalized.isBlank()) {
            return defaultDiagram();
        }

        if (hasSwimlanes(normalized)) {
            return buildSwimlaneDiagram(normalized);
        }

        List<String> nodes = parseNodes(normalized);
        if (nodes.isEmpty()) {
            return defaultDiagram();
        }

        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append(SKINPARAM);
        sb.append("start\n");
        for (String node : nodes) {
            sb.append(node);
        }
        sb.append("stop\n");
        sb.append("@enduml");
        return sb.toString();
    }

    // ── Node parsing ───────────────────────────────────────────────────────

    /**
     * Attempts to detect structural constructs in order of priority:
     * fork → if-then-else → when-then → otherwise → whether-or → choose-or →
     * for-counter → loop-N-times → while (with do) → while (bare) →
     * repeat-until → for-each → check-if → sequential.
     * The first matching construct is placed in the middle; text before and after
     * is parsed as sequential actions.
     */
    private List<String> parseNodes(String text) {
        List<String> nodes = new ArrayList<>();

        // Priority 1a: "A and B [simultaneously/in parallel/...]" — trigger AFTER branches
        Matcher forkInline = FORK_INLINE_AND.matcher(text);
        if (forkInline.find()) {
            addSequential(nodes, text.substring(0, forkInline.start()));
            List<String> branches = new ArrayList<>();
            branches.add(forkInline.group(1).trim());
            branches.add(forkInline.group(2).trim());
            nodes.add(renderForkBranches(branches));
            addSequential(nodes, text.substring(forkInline.end()));
            return nodes;
        }

        // Priority 1b: "[in parallel/...] A, B and C" — trigger BEFORE branches
        Matcher fork = FORK_PARALLEL.matcher(text);
        if (fork.find()) {
            addSequential(nodes, text.substring(0, fork.start()));
            String rawBranches = fork.group(1).replaceAll("[.;]$", "").trim();
            List<String> branches = Arrays.stream(BRANCH_SPLIT.split(rawBranches))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(java.util.stream.Collectors.toList());
            if (branches.size() < 2) {
                // couldn't split cleanly — treat whole thing as two halves
                int midAnd = rawBranches.indexOf(" and ");
                if (midAnd > 0) {
                    branches = List.of(rawBranches.substring(0, midAnd).trim(),
                                       rawBranches.substring(midAnd + 5).trim());
                } else {
                    branches = List.of(rawBranches);
                }
            }
            nodes.add(renderForkBranches(branches));
            addSequential(nodes, text.substring(fork.end()));
            return nodes;
        }

        // Priority 2: if-then-else / if-then-otherwise
        Matcher ifm = IF_THEN_ELSE.matcher(text);
        if (ifm.find()) {
            addSequential(nodes, text.substring(0, ifm.start()));
            nodes.add(renderDecision(ifm.group(1).trim(), ifm.group(2).trim(),
                    ifm.group(3) != null ? ifm.group(3).trim() : null));
            addSequential(nodes, text.substring(ifm.end()));
            return nodes;
        }

        // Priority 3: when-then [-otherwise]
        Matcher whenm = WHEN_THEN.matcher(text);
        if (whenm.find()) {
            addSequential(nodes, text.substring(0, whenm.start()));
            nodes.add(renderDecision(whenm.group(1).trim(), whenm.group(2).trim(),
                    whenm.group(3) != null ? whenm.group(3).trim() : null));
            addSequential(nodes, text.substring(whenm.end()));
            return nodes;
        }

        // Priority 4: "X, otherwise Y"
        Matcher otherwisem = OTHERWISE.matcher(text);
        if (otherwisem.find()) {
            addSequential(nodes, text.substring(0, otherwisem.start()));
            // The text before "otherwise" is the yes-branch condition summary;
            // render it as a decision: condition = the happy-path action.
            nodes.add(renderDecision(otherwisem.group(1).trim(),
                    otherwisem.group(1).trim(),
                    otherwisem.group(2).trim()));
            addSequential(nodes, text.substring(otherwisem.end()));
            return nodes;
        }

        // Priority 5: "whether A or B"
        Matcher whetherm = WHETHER_OR.matcher(text);
        if (whetherm.find()) {
            addSequential(nodes, text.substring(0, whetherm.start()));
            nodes.add(renderDecision(
                    whetherm.group(1).trim() + " or " + whetherm.group(2).trim(),
                    whetherm.group(1).trim(),
                    whetherm.group(2).trim()));
            addSequential(nodes, text.substring(whetherm.end()));
            return nodes;
        }

        // Priority 6: "choose A or B"
        Matcher choosem = CHOOSE_OR.matcher(text);
        if (choosem.find()) {
            addSequential(nodes, text.substring(0, choosem.start()));
            nodes.add(renderDecision(
                    choosem.group(1).trim() + " or " + choosem.group(2).trim(),
                    choosem.group(1).trim(),
                    choosem.group(2).trim()));
            addSequential(nodes, text.substring(choosem.end()));
            return nodes;
        }

        // Priority 7: Counter loop "for i = 0 to N [: action]"
        Matcher counterLoop = FOR_COUNTER.matcher(text);
        if (counterLoop.find()) {
            addSequential(nodes, text.substring(0, counterLoop.start()));
            nodes.add(renderCounterLoop(
                    counterLoop.group(1).trim(),
                    counterLoop.group(2).trim(),
                    counterLoop.group(3).trim(),
                    counterLoop.group(4)));
            addSequential(nodes, text.substring(counterLoop.end()));
            return nodes;
        }

        // Priority 8: Count-bounded loop "loop N times [: action]"
        Matcher loopN = LOOP_N_TIMES.matcher(text);
        if (loopN.find()) {
            addSequential(nodes, text.substring(0, loopN.start()));
            nodes.add(renderLoopNTimes(loopN.group(1).trim(), loopN.group(2)));
            addSequential(nodes, text.substring(loopN.end()));
            return nodes;
        }

        // Priority 9: While loop (requires "do" keyword)
        Matcher whilem = WHILE_LOOP.matcher(text);
        if (whilem.find()) {
            addSequential(nodes, text.substring(0, whilem.start()));
            nodes.add(renderWhile(whilem.group(1).trim(), whilem.group(2).trim()));
            addSequential(nodes, text.substring(whilem.end()));
            return nodes;
        }

        // Priority 10: While loop (bare — "while X: body" or "while X, body")
        Matcher whileBare = WHILE_BARE.matcher(text);
        if (whileBare.find()) {
            addSequential(nodes, text.substring(0, whileBare.start()));
            nodes.add(renderWhileEnhanced(whileBare.group(1).trim(), whileBare.group(2).trim()));
            addSequential(nodes, text.substring(whileBare.end()));
            return nodes;
        }

        // Priority 11: Repeat-until
        Matcher repeatm = REPEAT_UNTIL.matcher(text);
        if (repeatm.find()) {
            addSequential(nodes, text.substring(0, repeatm.start()));
            nodes.add(renderRepeatUntil(repeatm.group(1).trim(), repeatm.group(2).trim()));
            addSequential(nodes, text.substring(repeatm.end()));
            return nodes;
        }

        // Priority 12: For-each (rendered as repeat-while)
        Matcher forEach = FOR_EACH.matcher(text);
        if (forEach.find()) {
            addSequential(nodes, text.substring(0, forEach.start()));
            nodes.add(renderForEach(forEach.group(1).trim(), forEach.group(2).trim()));
            addSequential(nodes, text.substring(forEach.end()));
            return nodes;
        }

        // Priority 13: Check-if (decision without explicit branches)
        Matcher check = CHECK_IF.matcher(text);
        if (check.find()) {
            addSequential(nodes, text.substring(0, check.start()));
            nodes.add(renderCheckIf(check.group(1).trim()));
            addSequential(nodes, text.substring(check.end()));
            return nodes;
        }

        // Fallback: sequential steps
        addSequential(nodes, text);
        return nodes;
    }

    private void addSequential(List<String> nodes, String text) {
        nodes.addAll(parseSequentialSteps(text));
    }

    // ── Construct renderers ────────────────────────────────────────────────

    private String renderDecision(String condition, String thenAction, String elseAction) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (").append(sanitize(condition)).append("?) then (yes)\n");
        for (String step : splitBranchActions(thenAction)) {
            sb.append("  :").append(capitalize(sanitize(step))).append(";\n");
        }
        if (elseAction != null && !elseAction.isBlank()) {
            sb.append("else (no)\n");
            for (String step : splitBranchActions(elseAction)) {
                sb.append("  :").append(capitalize(sanitize(step))).append(";\n");
            }
        }
        sb.append("endif\n");
        return sb.toString();
    }

    private String renderCheckIf(String condition) {
        return "if (" + sanitize(condition) + "?) then (yes)\n" +
               "  :Proceed;\n" +
               "else (no)\n" +
               "  :Handle error;\n" +
               "endif\n";
    }

    private String renderWhile(String condition, String action) {
        return "while (" + sanitize(condition) + "?) is (yes)\n" +
               "  :" + capitalize(sanitize(action)) + ";\n" +
               "endwhile (no)\n";
    }

    private String renderRepeatUntil(String action, String condition) {
        // "repeat until X" exits when X becomes true; the loop-back edge is labeled (no).
        return "repeat\n" +
               "  :" + capitalize(sanitize(action)) + ";\n" +
               "repeat while (" + sanitize(condition) + "?) is (no)\n";
    }

    private String renderForEach(String item, String action) {
        return "repeat\n" +
               "  :" + capitalize(sanitize(action)) + ";\n" +
               "repeat while (more " + sanitize(item) + "?) is (yes)\n";
    }

    private String renderFork(String branchA, String branchB) {
        return renderForkBranches(List.of(branchA, branchB));
    }

    /**
     * Renders N parallel branches as a PlantUML fork/join block.
     * Each branch becomes a single action node.
     */
    private String renderForkBranches(List<String> branches) {
        if (branches.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < branches.size(); i++) {
            sb.append(i == 0 ? "fork\n" : "fork again\n");
            sb.append("  :").append(capitalize(sanitize(branches.get(i)))).append(";\n");
        }
        sb.append("end fork\n");
        return sb.toString();
    }

    /**
     * Counter loop: emits an init node, a while-guard with an auto-increment step.
     * If {@code action} already contains an increment for {@code var}, the
     * auto-appended increment is suppressed to avoid duplication.
     */
    private String renderCounterLoop(String var, String init, String limit, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append(":").append(var).append(" = ").append(init).append(";\n");
        sb.append("while (").append(var).append(" <= ").append(limit).append("?) is (yes)\n");
        boolean hasIncrement = false;
        if (action != null && !action.isBlank()) {
            for (String step : splitBodySteps(action)) {
                sb.append("  :").append(capitalize(sanitize(step))).append(";\n");
                if (INLINE_INCREMENT.matcher(step).find()) {
                    hasIncrement = true;
                }
            }
        }
        if (!hasIncrement) {
            sb.append("  :").append(var).append("++;\n");
        }
        sb.append("endwhile (no)\n");
        return sb.toString();
    }

    /**
     * Count-bounded loop: "loop N times".  Always uses {@code i} as the implicit
     * counter and appends {@code :i++;} as the last step in the body.
     */
    private String renderLoopNTimes(String n, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append(":i = 0;\n");
        sb.append("while (i < ").append(n).append("?) is (yes)\n");
        if (action != null && !action.isBlank()) {
            for (String step : splitBodySteps(action)) {
                sb.append("  :").append(capitalize(sanitize(step))).append(";\n");
            }
        }
        sb.append("  :i++;\n");
        sb.append("endwhile (no)\n");
        return sb.toString();
    }

    /**
     * Enhanced while renderer that supports a multi-step body (comma/semicolon
     * separated).  Used by WHILE_BARE — the existing {@link #renderWhile} is kept
     * for WHILE_LOOP to avoid regression.
     */
    private String renderWhileEnhanced(String condition, String action) {
        StringBuilder sb = new StringBuilder();
        sb.append("while (").append(sanitize(condition)).append("?) is (yes)\n");
        for (String step : splitBodySteps(action)) {
            sb.append("  :").append(capitalize(sanitize(step))).append(";\n");
        }
        sb.append("endwhile (no)\n");
        return sb.toString();
    }

    /**
     * Splits a loop body string on commas and semicolons into individual steps,
     * stripping trailing punctuation from each one.
     */
    private List<String> splitBodySteps(String body) {
        if (body == null || body.isBlank()) return List.of();
        List<String> steps = new ArrayList<>();
        for (String part : body.split("\\s*[,;]\\s*")) {
            String cleaned = part.trim().replaceAll("[.!?]+$", "").trim();
            if (!cleaned.isBlank()) {
                steps.add(cleaned);
            }
        }
        return steps.isEmpty() ? List.of(body.trim()) : steps;
    }

    /**
     * Splits a decision branch action on {@code " and "} to support compound actions
     * (e.g. "create account and send confirmation email" → two separate action nodes).
     * Only splits when both resulting parts are non-blank.
     */
    private List<String> splitBranchActions(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] parts = text.split("\\s+and\\s+", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return List.of(parts[0].trim(), parts[1].trim());
        }
        return List.of(text);
    }

    // ── Swimlane rendering ─────────────────────────────────────────────────

    private boolean hasSwimlanes(String text) {
        return ACTOR_ACTION.matcher(text).find()
                || INLINE_SWIMLANE.matcher(text).find()
                || PARTITION_HEADER.matcher(text).find()
                || ROLE_KEYWORD.matcher(text).find();
    }

    /**
     * Builds a swimlane diagram using PlantUML {@code partition} blocks.
     * Supports three input formats:
     * <ol>
     *   <li>Explicit {@code partition Name { :action; }} blocks — passed through verbatim</li>
     *   <li>{@code |Lane|} horizontal swimlane notation — converted to partition blocks</li>
     *   <li>{@code Actor: action} per-line notation — grouped into partition blocks</li>
     * </ol>
     */
    private String buildSwimlaneDiagram(String text) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        sb.append(SKINPARAM);
        sb.append("start\n");

        if (PARTITION_HEADER.matcher(text).find()) {
            // Already uses partition syntax — normalise action lines inside each block
            for (String line : text.split("\\r?\\n")) {
                String trimmed = line.trim();
                Matcher pm = PARTITION_HEADER.matcher(trimmed);
                if (pm.find()) {
                    sb.append("partition ").append(pm.group(1).trim()).append(" {\n");
                } else if (trimmed.equals("}")) {
                    sb.append("}\n");
                } else if (trimmed.startsWith(":") && trimmed.endsWith(";")) {
                    sb.append("  ").append(trimmed).append("\n");
                } else if (!trimmed.isBlank()
                        && !trimmed.equalsIgnoreCase("start")
                        && !trimmed.equalsIgnoreCase("stop")) {
                    String cleaned = trimmed.replaceAll("^[:\\s]+|[;.!?\\s]+$", "").trim();
                    if (!cleaned.isBlank()) {
                        sb.append("  :").append(capitalize(sanitize(cleaned))).append(";\n");
                    }
                }
            }
        } else if (INLINE_SWIMLANE.matcher(text).find()) {
            // Inline |Lane| notation — convert to partition blocks
            String currentLane = null;
            for (String line : text.split("\\r?\\n")) {
                Matcher lm = INLINE_SWIMLANE.matcher(line.trim());
                if (lm.find()) {
                    if (currentLane != null) sb.append("}\n");
                    currentLane = lm.group(1).trim();
                    sb.append("partition ").append(currentLane).append(" {\n");
                    String rest = line.substring(line.indexOf('|', line.indexOf('|') + 1) + 1).trim();
                    if (!rest.isBlank()) {
                        sb.append("  :").append(capitalize(sanitize(rest))).append(";\n");
                    }
                } else if (!line.isBlank()) {
                    String cleaned = line.trim().replaceAll("^[:\\s]+|[;\\s]+$", "").trim();
                    if (!cleaned.isBlank()) {
                        String prefix = currentLane != null ? "  " : "";
                        sb.append(prefix).append(":").append(capitalize(sanitize(cleaned))).append(";\n");
                    }
                }
            }
            if (currentLane != null) sb.append("}\n");
        } else {
            // "Actor: action" notation — group consecutive actions by actor into partition blocks
            java.util.LinkedHashMap<String, java.util.List<String>> lanes = new java.util.LinkedHashMap<>();
            String lastActor = null;
            int groupCounter = 0;
            for (String line : text.split("\\r?\\n")) {
                Matcher am = ACTOR_ACTION.matcher(line.trim());
                if (am.matches()) {
                    String actor  = am.group(1).trim();
                    String action = sanitize(am.group(2).trim());
                    if (!actor.equals(lastActor)) {
                        groupCounter++;
                        lastActor = actor;
                    }
                    String key = actor + "__" + groupCounter;
                    lanes.computeIfAbsent(key, k -> new ArrayList<>()).add(action);
                } else if (!line.isBlank()) {
                    String cleaned = sanitize(line.trim().replaceAll("^[:\\s]+|[;.!?\\s]+$", "").trim());
                    if (!cleaned.isBlank()) {
                        if (lastActor != null) {
                            String key = lastActor + "__" + groupCounter;
                            lanes.computeIfAbsent(key, k -> new ArrayList<>()).add(cleaned);
                        } else {
                            sb.append(":").append(capitalize(cleaned)).append(";\n");
                        }
                    }
                }
            }
            for (java.util.Map.Entry<String, java.util.List<String>> entry : lanes.entrySet()) {
                String displayName = entry.getKey().replaceAll("__\\d+$", "");
                sb.append("partition ").append(displayName).append(" {\n");
                for (String action : entry.getValue()) {
                    sb.append("  :").append(capitalize(action)).append(";\n");
                }
                sb.append("}\n");
            }
        }

        sb.append("stop\n");
        sb.append("@enduml");
        return sb.toString();
    }

    // ── Sequential step parser ─────────────────────────────────────────────
    /**
     * Pre-processes raw input text before structural parsing.
     * <ol>
     *   <li>Strips introductory preamble sentences that contain no actions.</li>
     *   <li>Replaces inline transition filler connectors with newlines so
     *       {@link #STEP_SPLIT} divides them into separate action steps.</li>
     *   <li>Collapses consecutive blank lines introduced by stripping.</li>
     * </ol>
     */
    private String preprocess(String text) {
        // 1. Remove introductory filler phrases
        String result = FILLER_INTRO.matcher(text).replaceAll("");
        // 2. Convert inline transition connectors to newlines
        result = INLINE_FILLER_CONNECTOR.matcher(result).replaceAll("\n");
        // 3. Collapse extra blank lines created by stripping
        result = result.replaceAll("\n{2,}", "\n");
        return result.strip();
    }
    private List<String> parseSequentialSteps(String text) {
        List<String> steps = new ArrayList<>();
        if (text == null || text.isBlank()) return steps;

        String[] parts = STEP_SPLIT.split(text);
        for (String part : parts) {
            String cleaned = LEADING_CONNECTOR.matcher(part).replaceFirst("")
                    .replaceAll("[.;!?]+$", "")
                    .trim();
            if (cleaned.isBlank() || cleaned.length() <= 2
                    || PURE_FILLER_STEP.matcher(cleaned).matches()) {
                continue;
            }
            steps.add(":" + capitalize(sanitize(cleaned)) + ";\n");
        }
        return steps;
    }

    // ── Default diagram ────────────────────────────────────────────────────

    /**
     * Returns a rich default activity diagram demonstrating all supported
     * PlantUML constructs: actions, decision, fork/join, and loop.
     */
    String defaultDiagram() {
        return "@startuml\n" +
               SKINPARAM +
               "start\n" +
               ":Receive request;\n" +
               "if (Input valid?) then (yes)\n" +
               "  fork\n" +
               "    :Log event;\n" +
               "  fork again\n" +
               "    :Notify user;\n" +
               "  end fork\n" +
               "  while (Items remaining?) is (yes)\n" +
               "    :Process item;\n" +
               "  endwhile (no)\n" +
               "  :Return result;\n" +
               "else (no)\n" +
               "  :Return error;\n" +
               "endif\n" +
               "stop\n" +
               "@enduml";
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String sanitize(String s) {
        // Remove characters that break PlantUML activity syntax and strip trailing punctuation
        return s.replaceAll("[;|{}]", " ")
                .replaceAll("[.!?]+$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
