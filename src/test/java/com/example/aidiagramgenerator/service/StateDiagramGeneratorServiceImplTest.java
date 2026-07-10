package com.example.aidiagramgenerator.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateDiagramGeneratorServiceImplTest {

    private final StateDiagramGeneratorServiceImpl service = new StateDiagramGeneratorServiceImpl();

    // ── Envelope ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Output always starts with @startuml and ends with @enduml")
    void wrapsInStartEndUml() {
        String result = service.generateStateDiagram("[*] --> Off\nOff --> On : switch on");
        assertTrue(result.startsWith("@startuml"), "Must start with @startuml");
        assertTrue(result.endsWith("@enduml"),     "Must end with @enduml");
    }

    @Test
    @DisplayName("@enduml appears exactly once")
    void endumlExactlyOnce() {
        String result = service.generateStateDiagram("[*] --> Idle\nIdle --> [*]");
        long count = result.lines().filter("@enduml"::equals).count();
        assertEquals(1, count, "@enduml must appear exactly once");
    }

    @Test
    @DisplayName("Null input produces the default diagram")
    void nullInputProducesDefault() {
        String result = service.generateStateDiagram(null);
        assertTrue(result.startsWith("@startuml"), "Default must start with @startuml");
        assertTrue(result.contains("[*]"), "Default must contain initial/final pseudo-state");
    }

    @Test
    @DisplayName("Blank input produces the default diagram")
    void blankInputProducesDefault() {
        String result = service.generateStateDiagram("   ");
        assertTrue(result.startsWith("@startuml"), "Default must start with @startuml");
        assertTrue(result.contains("[*]"), "Default must contain initial/final pseudo-state");
    }

    // ── Explicit arrow syntax ──────────────────────────────────────────────

    @Nested
    @DisplayName("Explicit PlantUML arrow syntax")
    class ExplicitArrow {

        @Test
        @DisplayName("[*] --> State is preserved as initial transition")
        void initialPseudoState() {
            String result = service.generateStateDiagram("[*] --> Off");
            assertTrue(result.contains("[*] --> Off"), "Initial pseudo-state must be present");
        }

        @Test
        @DisplayName("State --> [*] is preserved as final transition")
        void finalPseudoState() {
            String result = service.generateStateDiagram("[*] --> On\nOn --> [*]");
            assertTrue(result.contains("On --> [*]"), "Final pseudo-state must be present");
        }

        @Test
        @DisplayName("Transition with label is preserved")
        void transitionLabel() {
            String result = service.generateStateDiagram(
                    "[*] --> Off\nOff --> On : switch on\nOn --> Off : switch off");
            assertTrue(result.contains("Off --> On : switch on"), "Forward transition with label must be present");
            assertTrue(result.contains("On --> Off : switch off"), "Reverse transition with label must be present");
        }

        @Test
        @DisplayName("Multi-state chain is preserved in order")
        void multiStateChain() {
            String result = service.generateStateDiagram(
                    "[*] --> Idle\nIdle --> Processing : start\nProcessing --> Completed : finish\nCompleted --> [*]");
            assertTrue(result.contains("[*] --> Idle"));
            assertTrue(result.contains("Idle --> Processing : start"));
            assertTrue(result.contains("Processing --> Completed : finish"));
            assertTrue(result.contains("Completed --> [*]"));
        }

        @Test
        @DisplayName("CD player example from user requirements")
        void cdPlayerExample() {
            String result = service.generateStateDiagram(
                    "[*] --> Off\n" +
                    "Off --> On : switch on\n" +
                    "On --> Off : switch off\n" +
                    "On --> Paused : pause\n" +
                    "Paused --> On : resume\n" +
                    "On --> [*]");
            assertTrue(result.contains("Off --> On : switch on"));
            assertTrue(result.contains("On --> Paused : pause"));
            assertTrue(result.contains("Paused --> On : resume"));
        }
    }

    // ── Transition phrases (NLP) ───────────────────────────────────────────

    @Nested
    @DisplayName("Natural language transition phrases")
    class TransitionPhrases {

        @Test
        @DisplayName("'X transitions to Y on event' is parsed as a transition")
        void transitionsToOn() {
            String result = service.generateStateDiagram(
                    "Off transitions to On on switch on.");
            assertTrue(result.contains("-->"), "Must contain a transition arrow");
            assertTrue(result.contains("On"),  "Target state 'On' must appear");
        }

        @Test
        @DisplayName("'from X to Y' phrase is parsed as a transition")
        void fromToPhrase() {
            String result = service.generateStateDiagram(
                    "from Off to On : user presses button");
            assertTrue(result.contains("Off --> On"), "Transition arrow must be present");
            assertTrue(result.contains("user presses button"), "Label must be preserved");
        }

        @Test
        @DisplayName("'X changes to Y when event' is parsed as a transition")
        void changesToWhen() {
            String result = service.generateStateDiagram(
                    "The light changes to On when the switch is flipped.");
            assertTrue(result.contains("-->"), "Must contain a transition arrow");
        }
    }

    // ── Entry / do / exit actions ──────────────────────────────────────────

    @Nested
    @DisplayName("Entry, do, and exit actions — inline (after transition)")
    class StateActions {

        @Test
        @DisplayName("'entry: action' on line after transition is emitted inside a state block")
        void entryAction() {
            String result = service.generateStateDiagram(
                    "[*] --> Active\nActive --> [*]\nentry: initialise connection");
            assertTrue(result.contains("entry / initialise connection"),
                    "Entry action must be present in state block, got:\n" + result);
        }

        @Test
        @DisplayName("'exit: action' on line after transition is emitted inside a state block")
        void exitAction() {
            String result = service.generateStateDiagram(
                    "[*] --> Active\nActive --> [*]\nexit: close connection");
            assertTrue(result.contains("exit / close connection"),
                    "Exit action must be present in state block, got:\n" + result);
        }

        @Test
        @DisplayName("'do: activity' on line after transition is emitted inside a state block")
        void doActivity() {
            String result = service.generateStateDiagram(
                    "[*] --> Processing\nProcessing --> [*]\ndo: execute task");
            assertTrue(result.contains("do / execute task"),
                    "Do activity must be present in state block, got:\n" + result);
        }

        @Test
        @DisplayName("State block header wraps the state name correctly")
        void stateBlockHeader() {
            String result = service.generateStateDiagram(
                    "[*] --> Active\nActive --> [*]\nentry: start");
            assertTrue(result.contains("state Active {"),
                    "State block header must appear, got:\n" + result);
        }
    }

    @Nested
    @DisplayName("Entry, do, and exit actions — explicit state { } block syntax")
    class StateBlockSyntax {

        @Test
        @DisplayName("'state Playing { do / read CD }' produces a do action block")
        void doActionInBlock() {
            String result = service.generateStateDiagram(
                    "state Playing {\n  do / read CD\n}");
            assertTrue(result.contains("state Playing {"),
                    "State block header must appear, got:\n" + result);
            assertTrue(result.contains("do / read CD"),
                    "Do action must appear in block, got:\n" + result);
            assertTrue(result.endsWith("@enduml"),
                    "Must end with @enduml, got:\n" + result);
        }

        @Test
        @DisplayName("'state Paused { exit / turn green LED on }' produces an exit action block")
        void exitActionInBlock() {
            String result = service.generateStateDiagram(
                    "state Paused {\n  exit / turn green LED on\n}");
            assertTrue(result.contains("state Paused {"),
                    "State block header must appear, got:\n" + result);
            assertTrue(result.contains("exit / turn green LED on"),
                    "Exit action must appear in block, got:\n" + result);
        }

        @Test
        @DisplayName("'entry / ...' inside a state block is emitted correctly")
        void entryActionInBlock() {
            String result = service.generateStateDiagram(
                    "state Active {\n  entry / start timer\n}");
            assertTrue(result.contains("state Active {"),
                    "State block header must appear, got:\n" + result);
            assertTrue(result.contains("entry / start timer"),
                    "Entry action must appear in block, got:\n" + result);
        }

        @Test
        @DisplayName("Multiple actions in one block are all emitted")
        void multipleActionsInBlock() {
            String result = service.generateStateDiagram(
                    "state Active {\n  entry / start timer\n  do / process requests\n  exit / stop timer\n}");
            assertTrue(result.contains("entry / start timer"),   "entry action must appear");
            assertTrue(result.contains("do / process requests"), "do action must appear");
            assertTrue(result.contains("exit / stop timer"),     "exit action must appear");
        }

        @Test
        @DisplayName("State blocks and transitions can coexist in one input")
        void blocksCombinedWithTransitions() {
            String input = """
                    [*] --> Playing
                    Playing --> Paused : pause
                    Paused --> Playing : play
                    Playing --> [*]

                    state Playing {
                      do / read CD
                    }

                    state Paused {
                      exit / turn green LED on
                    }
                    """;
            String result = service.generateStateDiagram(input);
            assertTrue(result.contains("[*] --> Playing"),               "Initial transition must appear");
            assertTrue(result.contains("Playing --> Paused : pause"),    "Pause transition must appear");
            assertTrue(result.contains("Paused --> Playing : play"),     "Play transition must appear");
            assertTrue(result.contains("state Playing {"),               "Playing block must appear");
            assertTrue(result.contains("do / read CD"),                  "Do action must appear");
            assertTrue(result.contains("state Paused {"),                "Paused block must appear");
            assertTrue(result.contains("exit / turn green LED on"),      "Exit action must appear");
        }

        @Test
        @DisplayName("Two separate state blocks produce two independent state declarations")
        void twoSeparateBlocks() {
            String result = service.generateStateDiagram(
                    "state Playing {\n  do / read CD\n}\nstate Paused {\n  exit / turn green LED on\n}");
            assertTrue(result.contains("state Playing {"),  "Playing block must appear");
            assertTrue(result.contains("state Paused {"),   "Paused block must appear");
            assertTrue(result.contains("do / read CD"),     "Playing do action must appear");
            assertTrue(result.contains("exit / turn green LED on"), "Paused exit action must appear");
        }

        @Test
        @DisplayName("Block actions do not bleed into adjacent transitions")
        void blockActionsDoNotBleedIntoTransitions() {
            String input = """
                    [*] --> Idle
                    Idle --> Active : start
                    state Active {
                      do / process
                    }
                    Active --> [*]
                    """;
            String result = service.generateStateDiagram(input);
            assertTrue(result.contains("state Active {"),  "Active block must appear");
            assertTrue(result.contains("do / process"),    "Do action must appear in Active block");
            // The bare transition to [*] must also be preserved
            assertTrue(result.contains("Active --> [*]"),  "Final transition must be present");
        }
    }

    // ── Bare state list fallback ───────────────────────────────────────────

    @Nested
    @DisplayName("Bare state list fallback")
    class BareStateFallback {

        @Test
        @DisplayName("List of state names without explicit arrows produces a linear chain")
        void linearChainFromStates() {
            String result = service.generateStateDiagram("Off\nOn\nPaused");
            // The service should build a chain; at minimum it must have [*] and -->
            assertTrue(result.contains("[*]"), "Must include initial/final pseudo-state");
            assertTrue(result.contains("-->"),  "Must include at least one transition");
        }
    }

    // ── Timed transitions ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Timed transitions")
    class TimedTransitions {

        @Test
        @DisplayName("Explicit 'State --> State : after N min' label is preserved verbatim")
        void explicitTimedArrow() {
            String result = service.generateStateDiagram(
                    "Working --> Screensaving : after 15 min");
            assertTrue(result.contains("Working --> Screensaving : after 15 min"),
                    "Timed transition label must be preserved verbatim, got:\n" + result);
        }

        @Test
        @DisplayName("'X transitions to Y after N min' produces timed transition")
        void timedSuffixPhrase() {
            String result = service.generateStateDiagram(
                    "Working transitions to Screensaving after 15 min.");
            assertTrue(result.contains("Working --> Screensaving"),
                    "Transition arrow must be present, got:\n" + result);
            assertTrue(result.contains("after 15 min"),
                    "Timed label must be present, got:\n" + result);
        }

        @Test
        @DisplayName("'After N min, X transitions to Y' produces timed transition")
        void timedPrefixPhrase() {
            String result = service.generateStateDiagram(
                    "After 30 min, Stopped transitions to TurnedOff.");
            assertTrue(result.contains("Stopped --> TurnedOff"),
                    "Transition arrow must be present, got:\n" + result);
            assertTrue(result.contains("after 30 min"),
                    "Timed label must be present, got:\n" + result);
        }

        @Test
        @DisplayName("CD player screensaving example (explicit arrows)")
        void cdPlayerScreensavingExample() {
            String input = """
                    Working --> Screensaving : after 15 min
                    Screensaving --> Working : keystroke
                    Stopped --> TurnedOff : after 30 min
                    Playing --> Paused : pause button
                    """;
            String result = service.generateStateDiagram(input);
            assertTrue(result.contains("Working --> Screensaving : after 15 min"),
                    "Screensaving timed transition must appear, got:\n" + result);
            assertTrue(result.contains("Screensaving --> Working : keystroke"),
                    "Keystroke transition must appear, got:\n" + result);
            assertTrue(result.contains("Stopped --> TurnedOff : after 30 min"),
                    "TurnedOff timed transition must appear, got:\n" + result);
            assertTrue(result.contains("Playing --> Paused : pause button"),
                    "Pause button transition must appear, got:\n" + result);
        }

        @Test
        @DisplayName("'X goes to Y after N hours' — hours unit is recognised")
        void timedHoursUnit() {
            String result = service.generateStateDiagram(
                    "Idle goes to Sleeping after 2 hours.");
            assertTrue(result.contains("Idle --> Sleeping"),
                    "Transition must be present, got:\n" + result);
            assertTrue(result.contains("after 2 hours"),
                    "Hours timed label must be present, got:\n" + result);
        }
    }

    // ── Guarded transitions ────────────────────────────────────────────────

    @Nested
    @DisplayName("Guarded transitions")
    class GuardedTransitions {

        @Test
        @DisplayName("Explicit 'State --> State : [guard]' label is preserved verbatim")
        void explicitGuardLabel() {
            String result = service.generateStateDiagram(
                    "[*] --> On\nOn --> Off : [power cut]");
            assertTrue(result.contains("On --> Off : [power cut]"),
                    "Guard label must be preserved verbatim, got:\n" + result);
        }

        @Test
        @DisplayName("'X transitions to Y if condition' wraps condition in [brackets]")
        void guardedTransitionPhrase() {
            String result = service.generateStateDiagram(
                    "Off transitions to On if button pressed.");
            assertTrue(result.contains("Off --> On"),
                    "Transition must be present, got:\n" + result);
            assertTrue(result.contains("[button pressed]"),
                    "Guard must appear in brackets, got:\n" + result);
        }

        @Test
        @DisplayName("'X transitions to Y only if condition' also produces [guard]")
        void onlyIfGuardPhrase() {
            String result = service.generateStateDiagram(
                    "Locked transitions to Unlocked only if PIN correct.");
            assertTrue(result.contains("Locked --> Unlocked"),
                    "Transition must be present, got:\n" + result);
            assertTrue(result.contains("[PIN correct]"),
                    "Guard must appear in brackets, got:\n" + result);
        }

        @Test
        @DisplayName("'X transitions to Y on event if guard' produces 'event [guard]' label")
        void eventAndGuardPhrase() {
            String result = service.generateStateDiagram(
                    "Off transitions to On on button press if user logged in.");
            assertTrue(result.contains("Off --> On"),
                    "Transition must be present, got:\n" + result);
            assertTrue(result.contains("button press"),
                    "Event must appear in label, got:\n" + result);
            assertTrue(result.contains("[user logged in]"),
                    "Guard must appear in brackets, got:\n" + result);
        }
    }

    // ── Default diagram guard ──────────────────────────────────────────────

    @Nested
    @DisplayName("Default diagram")
    class DefaultDiagram {

        @Test
        @DisplayName("Default diagram contains Idle and Processing states")
        void defaultContainsKnownStates() {
            String result = service.generateStateDiagram("");
            assertTrue(result.contains("Idle"),       "Default must contain Idle state");
            assertTrue(result.contains("Processing"), "Default must contain Processing state");
        }

        @Test
        @DisplayName("Default diagram has both initial and final pseudo-states")
        void defaultHasInitialAndFinal() {
            String result = service.generateStateDiagram("");
            long count = result.lines()
                    .filter(l -> l.strip().contains("[*]"))
                    .count();
            assertTrue(count >= 2, "Default must have at least two [*] references (initial + final)");
        }
    }
}
