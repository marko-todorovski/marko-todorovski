package com.example.aidiagramgenerator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link StateDiagramGeneratorService}.
 *
 * <p>Parses both structured ({@code State --> State : label}) and natural language
 * ({@code "When the switch is turned on, the light goes to On state"}) state
 * descriptions, then renders academically correct PlantUML state diagram syntax.
 *
 * <h3>Supported input styles</h3>
 * <ul>
 *   <li><b>Explicit arrow</b> — {@code StateA --> StateB : event}</li>
 *   <li><b>Initial/final shorthand</b> — {@code [*] --> State} and {@code State --> [*]}</li>
 *   <li><b>State block</b> — {@code state Playing \{} / {@code do / read CD} / {@code \}}
 *       — the preferred way to attach entry/do/exit actions to a named state</li>
 *   <li><b>Timed transition phrase</b> — {@code "X transitions to Y after 15 min"},
 *       {@code "After 15 min, X transitions to Y"} — label becomes {@code after 15 min}</li>
 *   <li><b>Guarded transition phrase</b> — {@code "X transitions to Y if condition"} → label
 *       {@code [condition]}; {@code "X transitions to Y on event if condition"} → {@code event [condition]}</li>
 *   <li><b>Transition phrase</b> — {@code "X transitions to Y on event"},
 *       {@code "X changes to Y when event"}, {@code "from X to Y"}</li>
 *   <li><b>State lifecycle phrase</b> — {@code "X is turned on/off"},
 *       {@code "X paused/playing/working/shutting down"}</li>
 *   <li><b>Inline entry/do/exit</b> — {@code "entry / action"}, {@code "do / action"},
 *       {@code "exit / action"} (or with {@code :}) on lines immediately after a transition;
 *       associated with the last active state</li>
 * </ul>
 *
 * <h3>Output format</h3>
 * <pre>
 * {@code
 * @startuml
 * [*] --> Off
 * Off --> On : switch on
 * On --> Off : switch off
 * On --> [*]
 * @enduml
 * }
 * </pre>
 */
@Service
public class StateDiagramGeneratorServiceImpl implements StateDiagramGeneratorService {

    // ── Explicit PlantUML-style arrow ──────────────────────────────────────

    /** {@code [*] --> StateName} or {@code StateName --> [*]} or {@code A --> B : label} */
    private static final Pattern EXPLICIT_ARROW = Pattern.compile(
            "(?i)^\\s*(\\[\\*\\]|[A-Za-z][\\w ]*)\\s*-->\\s*(\\[\\*\\]|[A-Za-z][\\w ]*)(?:\\s*:\\s*(.+))?\\s*$");

    // ── Transition / change phrases ────────────────────────────────────────

    /**
     * "X transitions to Y [on/when event]"
     * "X changes to Y [when event]"
     * "X moves to Y [on event]"
     * "X goes to Y [when event]"
     */
    private static final Pattern TRANSITION_PHRASE = Pattern.compile(
            "(?i)\\b([A-Za-z][\\w ]*)(?:\\s+state)?\\s+(?:transitions?|changes?|moves?|goes?)\\s+to\\s+([A-Za-z][\\w ]*)(?:\\s+(?:on|when|after|upon)\\s+(.+?))?[.!]?$");

    /**
     * "from X to Y [: label]" / "from X state to Y state [when event]"
     */
    private static final Pattern FROM_TO_PHRASE = Pattern.compile(
            "(?i)\\bfrom\\s+([A-Za-z][\\w ]*)(?:\\s+state)?\\s+to\\s+([A-Za-z][\\w ]*)(?:\\s+state)?(?:\\s*(?::|when|on|after)\\s*(.+?))?[.!]?$");

    /**
     * "When/If X, [the system] enters/reaches Y [state]"
     */
    private static final Pattern ENTER_STATE_PHRASE = Pattern.compile(
            "(?i)\\b(?:when|if)\\s+(.+?)[,.]\\s+(?:the\\s+\\w+\\s+)?(?:enters?|reaches?|becomes?)\\s+(?:the\\s+)?([A-Za-z][\\w ]*)(?:\\s+state)?[.!]?$");

    // ── Timed and guarded transition phrases ──────────────────────────────

    /** Time unit alternatives shared by timed patterns. */
    private static final String TIME_UNIT_RE =
            "(?:min(?:utes?)?|sec(?:onds?)?|h(?:ours?)?|ms|milliseconds?|days?|hours?)";

    /**
     * {@code "X transitions/goes/switches to Y after N min"}
     * — captures (1) from-state, (2) to-state, (3) duration token.
     */
    private static final Pattern TIMED_SUFFIX_PHRASE = Pattern.compile(
            "(?i)\\b([A-Za-z]\\w*)\\s+(?:transitions?|changes?|moves?|goes?|switches?)\\s+to\\s+([A-Za-z]\\w*)" +
            "\\s+after\\s+(\\d[\\d.]*\\s*" + TIME_UNIT_RE + ")");

    /**
     * {@code "After N min[utes][, context,] X transitions/goes to Y"}
     * — captures (1) duration token, (2) from-state, (3) to-state.
     */
    private static final Pattern TIMED_PREFIX_PHRASE = Pattern.compile(
            "(?i)^after\\s+(\\d[\\d.]*\\s*" + TIME_UNIT_RE + ")(?:[^,.]*)?" +
            "[,.]\\s*([A-Za-z]\\w*)\\s+(?:transitions?|changes?|moves?|goes?|switches?)\\s+to\\s+([A-Za-z]\\w*)");

    /**
     * {@code "X transitions to Y on/upon event if/when/provided guard"}
     * — captures (1) from-state, (2) to-state, (3) event, (4) guard condition.
     * Guard is wrapped in {@code [brackets]} on output.
     */
    private static final Pattern EVENT_AND_GUARD_PHRASE = Pattern.compile(
            "(?i)\\b([A-Za-z]\\w*)\\s+(?:transitions?|changes?|moves?|goes?|switches?)\\s+to\\s+([A-Za-z]\\w*)" +
            "\\s+(?:on|upon)\\s+(.+?)\\s+(?:if|when|provided)\\s+(.+?)[.!]?$");

    /**
     * {@code "X transitions to Y if/only if/provided [that] condition"}
     * — captures (1) from-state, (2) to-state, (3) guard condition.
     * Guard is wrapped in {@code [brackets]} on output.
     */
    private static final Pattern GUARDED_TRANSITION = Pattern.compile(
            "(?i)\\b([A-Za-z]\\w*)\\s+(?:transitions?|changes?|moves?|goes?|switches?)\\s+to\\s+([A-Za-z]\\w*)" +
            "\\s+(?:if|only\\s+if|provided(?:\\s+that)?)\\s+(.+?)[.!]?$");

    // ── Entry/do/exit action lines ─────────────────────────────────────────

    /**
     * Lines like: "  entry: do something" / "on entry: action" / "on entry action"
     */
    private static final Pattern ENTRY_ACTION = Pattern.compile(
            "(?i)(?:on\\s+)?entry\\s*(?::|/)\\s*(.+)");

    /** "  exit: do something" / "on exit: action" */
    private static final Pattern EXIT_ACTION = Pattern.compile(
            "(?i)(?:on\\s+)?exit\\s*(?::|/)\\s*(.+)");

    /** "  do: something" / "do activity: something" */
    private static final Pattern DO_ACTION = Pattern.compile(
            "(?i)do(?:\\s+activity)?\\s*(?::|/)\\s*(.+)");

    // ── State block delimiters ─────────────────────────────────────────────

    /**
     * {@code state Playing {} — opens a named state block.
     * Also matches quoted alias form: {@code state "Playing CD" as Playing {}.
     */
    private static final Pattern STATE_BLOCK_OPEN = Pattern.compile(
            "(?i)^\\s*state\\s+(?:\"([^\"]+)\"(?:\\s+as\\s+(\\w+))?|([A-Za-z]\\w*))\\s*\\{\\s*$");

    /** Closing brace of a state block. */
    private static final Pattern BLOCK_CLOSE = Pattern.compile("^\\s*\\}\\s*$");

    // ── State declaration lines ────────────────────────────────────────────

    /**
     * {@code state X} or {@code state X as Y} — bare state declaration (no block).
     */
    private static final Pattern STATE_DECL = Pattern.compile(
            "(?i)^\\s*state\\s+(\\w+)(?:\\s+as\\s+(\\w+))?\\s*$");

    // ── Natural language lifecycle (fallback vocabulary) ───────────────────

    private static final Pattern LIFECYCLE_PHRASE = Pattern.compile(
            "(?i)\\b([A-Za-z][\\w ]*)\\s+(?:is\\s+)?(?:turned?\\s+on|turned?\\s+off|powered\\s+on|powered\\s+off|" +
            "(?:is\\s+)?(?:paused?|playing|working|idle|active|inactive|" +
            "shutting\\s+down|shut\\s+down|starting\\s+up|screen\\s+sav(?:ing|er)|" +
            "locked|sleeping|standby|rebooting|crashed|terminated|stopped|running|processing))");

    // ── State name cleanup ─────────────────────────────────────────────────

    private static final Pattern STATE_NAME_CLEAN = Pattern.compile("[^A-Za-z0-9_ ]");

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public String generateStateDiagram(String text) {
        if (text == null || text.isBlank()) {
            return buildDefault();
        }

        // Ordered collections preserve authoring order
        List<String[]> transitions = new ArrayList<>();  // [from, to, label]
        Set<String>    declaredStates = new LinkedHashSet<>();
        Map<String, StateActions> stateActions = new LinkedHashMap<>();

        String[] lines = text.split("\\r?\\n|(?<=\\.)[\\s]+(?=[A-Z\\[])");

        // Tracks the named state while the parser is inside a `state X { }` block.
        // When non-null, entry/do/exit actions are pinned to this state.
        String currentBlockState = null;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            Matcher m;

            // ── State block opener: "state Playing {" ─────────────────────
            if ((m = STATE_BLOCK_OPEN.matcher(line)).matches()) {
                // Group layout: (1) quoted display name, (2) alias, (3) plain identifier
                String rawName = m.group(3) != null ? m.group(3)
                        : (m.group(2) != null ? m.group(2) : m.group(1));
                currentBlockState = normalize(rawName);
                declaredStates.add(currentBlockState);
                continue;
            }

            // ── Block closer ───────────────────────────────────────────────
            if (BLOCK_CLOSE.matcher(line).matches()) {
                currentBlockState = null;
                continue;
            }

            // ── Resolve the state to attach entry/do/exit actions to ───────
            // Priority: explicit block context > last real transition state.
            String actionTarget;
            if (currentBlockState != null) {
                actionTarget = currentBlockState;
            } else if (transitions.isEmpty()) {
                actionTarget = null;
            } else {
                String[] last = transitions.get(transitions.size() - 1);
                actionTarget = "[*]".equals(last[1]) ? last[0] : last[1];
                if ("[*]".equals(actionTarget)) {
                    actionTarget = resolveLastRealState(transitions);
                }
            }

            if ((m = ENTRY_ACTION.matcher(line)).find()) {
                if (actionTarget != null) {
                    stateActions.computeIfAbsent(normalize(actionTarget), k -> new StateActions())
                                .entry = m.group(1).strip();
                }
                continue;
            }
            if ((m = EXIT_ACTION.matcher(line)).find()) {
                if (actionTarget != null) {
                    stateActions.computeIfAbsent(normalize(actionTarget), k -> new StateActions())
                                .exit = m.group(1).strip();
                }
                continue;
            }
            if ((m = DO_ACTION.matcher(line)).find()) {
                if (actionTarget != null) {
                    stateActions.computeIfAbsent(normalize(actionTarget), k -> new StateActions())
                                .doActivity = m.group(1).strip();
                }
                continue;
            }

            // State declaration (no transition, no block)
            if ((m = STATE_DECL.matcher(line)).matches()) {
                declaredStates.add(normalize(m.group(1)));
                continue;
            }

            // Explicit arrow  (highest structural precedence)
            if ((m = EXPLICIT_ARROW.matcher(line)).matches()) {
                String from  = m.group(1).strip();
                String to    = m.group(2).strip();
                String label = m.group(3) != null ? m.group(3).strip() : null;
                transitions.add(new String[]{from, to, label});
                if (!"[*]".equals(from)) declaredStates.add(normalize(from));
                if (!"[*]".equals(to))   declaredStates.add(normalize(to));
                continue;
            }

            // Timed suffix: "X goes to Y after N min"
            if ((m = TIMED_SUFFIX_PHRASE.matcher(line)).find()) {
                String from  = normalize(m.group(1));
                String to    = normalize(m.group(2));
                String label = "after " + m.group(3).strip();
                transitions.add(new String[]{from, to, label});
                declaredStates.add(from);
                declaredStates.add(to);
                continue;
            }

            // Timed prefix: "After N min, X goes to Y"
            if ((m = TIMED_PREFIX_PHRASE.matcher(line)).find()) {
                String label = "after " + m.group(1).strip();
                String from  = normalize(m.group(2));
                String to    = normalize(m.group(3));
                transitions.add(new String[]{from, to, label});
                declaredStates.add(from);
                declaredStates.add(to);
                continue;
            }

            // Event + guard: "X goes to Y on event if/when condition"
            if ((m = EVENT_AND_GUARD_PHRASE.matcher(line)).find()) {
                String from  = normalize(m.group(1));
                String to    = normalize(m.group(2));
                String label = m.group(3).strip() + " [" + m.group(4).strip() + "]";
                transitions.add(new String[]{from, to, label});
                declaredStates.add(from);
                declaredStates.add(to);
                continue;
            }

            // Guard only: "X goes to Y if/only if condition"
            if ((m = GUARDED_TRANSITION.matcher(line)).find()) {
                String from  = normalize(m.group(1));
                String to    = normalize(m.group(2));
                String label = "[" + m.group(3).strip() + "]";
                transitions.add(new String[]{from, to, label});
                declaredStates.add(from);
                declaredStates.add(to);
                continue;
            }

            // Generic transition phrase
            if ((m = TRANSITION_PHRASE.matcher(line)).find()) {
                String from  = normalize(m.group(1));
                String to    = normalize(m.group(2));
                String label = m.group(3) != null ? m.group(3).strip() : null;
                transitions.add(new String[]{from, to, label});
                declaredStates.add(from);
                declaredStates.add(to);
                continue;
            }

            // "from X to Y" phrase
            if ((m = FROM_TO_PHRASE.matcher(line)).find()) {
                String from  = normalize(m.group(1));
                String to    = normalize(m.group(2));
                String label = m.group(3) != null ? m.group(3).strip() : null;
                transitions.add(new String[]{from, to, label});
                declaredStates.add(from);
                declaredStates.add(to);
                continue;
            }

            // "When condition, enters State"
            if ((m = ENTER_STATE_PHRASE.matcher(line)).find()) {
                String label = m.group(1).strip();
                String to    = normalize(m.group(2));
                // Use last known state as "from", or [*] if none
                String from  = transitions.isEmpty() ? "[*]"
                        : transitions.get(transitions.size() - 1)[1];
                if ("[*]".equals(from)) from = resolveLastRealState(transitions);
                transitions.add(new String[]{from, to, label});
                declaredStates.add(to);
                continue;
            }

            // Lifecycle vocabulary fallback — collect state names but no transition yet
            if ((m = LIFECYCLE_PHRASE.matcher(line)).find()) {
                declaredStates.add(normalize(m.group(1)));
            }
        }

        // If no transitions were extracted, build a chain from declared states
        // (still passes stateActions so entry/do/exit blocks are preserved).
        if (transitions.isEmpty()) {
            return buildFromStates(new ArrayList<>(declaredStates), stateActions);
        }

        return render(transitions, stateActions);
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    private String render(List<String[]> transitions, Map<String, StateActions> stateActions) {
        StringBuilder sb = new StringBuilder("@startuml\n");

        // ── Layout and skinparam directives ────────────────────────────────
        sb.append("top to bottom direction\n");
        sb.append("\n");
        sb.append("skinparam state {\n");
        sb.append("  BackgroundColor #FAFAFA\n");
        sb.append("  BorderColor #555555\n");
        sb.append("  FontName Arial\n");
        sb.append("  FontSize 13\n");
        sb.append("  ArrowColor #333333\n");
        sb.append("  ArrowFontName Arial\n");
        sb.append("  ArrowFontSize 11\n");
        sb.append("  ArrowFontColor #444444\n");
        sb.append("  StartColor #333333\n");
        sb.append("  EndColor #333333\n");
        sb.append("}\n");
        sb.append("skinparam nodesep 50\n");
        sb.append("skinparam ranksep 60\n");
        sb.append("\n");

        // Emit state blocks for any state that has entry/do/exit actions
        for (Map.Entry<String, StateActions> entry : stateActions.entrySet()) {
            String name = entry.getKey();
            StateActions sa = entry.getValue();
            sb.append("state ").append(name).append(" {\n");
            if (sa.entry != null)      sb.append("  entry / ").append(sa.entry).append("\n");
            if (sa.doActivity != null) sb.append("  do / ").append(sa.doActivity).append("\n");
            if (sa.exit != null)       sb.append("  exit / ").append(sa.exit).append("\n");
            sb.append("}\n");
        }

        // Emit transitions
        for (String[] t : transitions) {
            String from  = t[0];
            String to    = t[1];
            String label = t[2];
            sb.append(from).append(" --> ").append(to);
            if (label != null && !label.isBlank()) {
                sb.append(" : ").append(label);
            }
            sb.append("\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Builds a linear state chain from a list of bare state names.
     * Adds {@code [*]} at both ends. Preserves any collected entry/do/exit actions.
     */
    private String buildFromStates(List<String> states, Map<String, StateActions> stateActions) {
        if (states.isEmpty()) {
            return buildDefault();
        }

        List<String[]> transitions = new ArrayList<>();
        transitions.add(new String[]{"[*]", states.get(0), null});

        for (int i = 0; i < states.size() - 1; i++) {
            transitions.add(new String[]{states.get(i), states.get(i + 1), null});
        }

        transitions.add(new String[]{states.get(states.size() - 1), "[*]", null});
        return render(transitions, stateActions);
    }

    /** Returns the default diagram when no parseable content is found. */
    private String buildDefault() {
        return "@startuml\n" +
               "[*] --> Idle\n" +
               "Idle --> Processing : start\n" +
               "Processing --> Completed : finish\n" +
               "Processing --> Failed : error\n" +
               "Completed --> [*]\n" +
               "Failed --> [*]\n" +
               "@enduml";
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Cleans a raw state name to a valid PlantUML identifier.
     * Multi-word names are CamelCased; special chars are stripped.
     */
    private String normalize(String raw) {
        if (raw == null) return "Unknown";
        raw = STATE_NAME_CLEAN.matcher(raw.strip()).replaceAll("");
        String[] words = raw.split("\\s+");
        if (words.length == 1) {
            String w = words[0];
            // Capitalise first letter
            return w.isEmpty() ? "Unknown" : Character.toUpperCase(w.charAt(0)) + w.substring(1);
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * Finds the last non-{@code [*]} "to" state in the transition list.
     */
    private String resolveLastRealState(List<String[]> transitions) {
        for (int i = transitions.size() - 1; i >= 0; i--) {
            String to = transitions.get(i)[1];
            if (!"[*]".equals(to)) return to;
        }
        return "[*]";
    }

    // ── Inner value holder ─────────────────────────────────────────────────

    private static final class StateActions {
        String entry;
        String doActivity;
        String exit;
    }
}
