package com.example.aidiagramgenerator.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActivityDiagramGeneratorServiceImplTest {

    private final ActivityDiagramGeneratorServiceImpl service =
            new ActivityDiagramGeneratorServiceImpl();

    // ── Envelope ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Output always starts with @startuml and ends with @enduml")
    void wrapsInStartEndUml() {
        String result = service.generateActivityDiagram("Put clothes on. Drive to college.");
        assertTrue(result.startsWith("@startuml"), "Must start with @startuml");
        assertTrue(result.endsWith("@enduml"),     "Must end with @enduml");
    }

    @Test
    @DisplayName("Output always contains start and stop nodes")
    void containsStartStop() {
        String result = service.generateActivityDiagram("Log in. Submit form.");
        assertTrue(result.contains("start"), "Must contain start node");
        assertTrue(result.contains("stop"),  "Must contain stop node");
    }

    @Test
    @DisplayName("@enduml appears exactly once")
    void endumlExactlyOnce() {
        String result = service.generateActivityDiagram("Step one. Step two.");
        long count = result.lines().filter("@enduml"::equals).count();
        assertEquals(1, count, "Expected @enduml exactly once");
    }

    // ── Sequential steps ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Sequential actions")
    class Sequential {

        @Test
        @DisplayName("Single sentence becomes one action node")
        void singleSentence() {
            String result = service.generateActivityDiagram("Put clothes on.");
            assertTrue(result.contains(":Put clothes on;"),
                    "Expected action node, got:\n" + result);
        }

        @Test
        @DisplayName("Two sentences become two action nodes")
        void twoSentences() {
            String result = service.generateActivityDiagram(
                    "Put clothes on. Drive to college.");
            assertTrue(result.contains(":Put clothes on;"),
                    "Expected first action, got:\n" + result);
            assertTrue(result.contains(":Drive to college;"),
                    "Expected second action, got:\n" + result);
        }

        @Test
        @DisplayName("Numbered steps are parsed as sequential actions")
        void numberedSteps() {
            String result = service.generateActivityDiagram(
                    "1. Register user\n2. Send confirmation email\n3. Activate account");
            assertTrue(result.contains("Register user") || result.contains("register user"),
                    "Expected first step, got:\n" + result);
            assertTrue(result.contains("Send confirmation email") ||
                       result.contains("send confirmation email"),
                    "Expected second step, got:\n" + result);
        }
    }

    // ── Decisions ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Decision nodes")
    class Decisions {

        @Test
        @DisplayName("if-then-else produces correct PlantUML decision block")
        void ifThenElse() {
            String result = service.generateActivityDiagram(
                    "if user is authenticated then show dashboard else show login page.");
            assertTrue(result.contains("if ("),        "Expected if block");
            assertTrue(result.contains("then (yes)"),  "Expected yes branch");
            assertTrue(result.contains("else (no)"),   "Expected no branch");
            assertTrue(result.contains("endif"),       "Expected endif");
        }

        @Test
        @DisplayName("if-then without else produces if block without else branch")
        void ifThenOnly() {
            String result = service.generateActivityDiagram(
                    "if order is valid then process payment.");
            assertTrue(result.contains("if ("),       "Expected if block");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("if-then-otherwise is treated as else branch")
        void ifThenOtherwise() {
            String result = service.generateActivityDiagram(
                    "if user has money then drive with taxi otherwise drive with bus.");
            assertTrue(result.contains("if ("),       "Expected if block");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("else (no)"),  "Expected otherwise as else");
            assertTrue(result.contains("Drive with bus") || result.contains("drive with bus"),
                    "Expected otherwise branch action, got:\n" + result);
        }

        @Test
        @DisplayName("'when X then Y otherwise Z' produces decision block")
        void whenThenOtherwise() {
            String result = service.generateActivityDiagram(
                    "when user is authenticated, do show dashboard otherwise redirect to login.");
            assertTrue(result.contains("if ("),       "Expected if block from when-then");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("else (no)"),  "Expected otherwise branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("'when X then Y' without otherwise produces if block")
        void whenThenOnly() {
            String result = service.generateActivityDiagram(
                    "when order is complete, do send confirmation email.");
            assertTrue(result.contains("if ("),       "Expected if block from when-then");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("'X, otherwise Y' produces decision block")
        void otherwiseAlone() {
            String result = service.generateActivityDiagram(
                    "Pay by card, otherwise pay by cash.");
            assertTrue(result.contains("if ("),       "Expected if block from otherwise");
            assertTrue(result.contains("else (no)"),  "Expected otherwise as else branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("'whether A or B' produces decision block")
        void whetherOr() {
            String result = service.generateActivityDiagram(
                    "Decide whether to take the taxi or take the bus.");
            assertTrue(result.contains("if ("),       "Expected if block from whether-or");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("else (no)"),  "Expected no branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("'whether A or B' branches appear as actions")
        void whetherOrBranchText() {
            String result = service.generateActivityDiagram(
                    "Decide whether to take the taxi or take the bus.");
            assertTrue(result.contains("taxi") || result.contains("Taxi"),
                    "Expected first option in yes branch, got:\n" + result);
            assertTrue(result.contains("bus") || result.contains("Bus"),
                    "Expected second option in no branch, got:\n" + result);
        }

        @Test
        @DisplayName("'choose A or B' produces decision block")
        void chooseOr() {
            String result = service.generateActivityDiagram(
                    "Choose to walk or take the subway.");
            assertTrue(result.contains("if ("),       "Expected if block from choose-or");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("else (no)"),  "Expected no branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("'check if' pattern produces a decision node")
        void checkIf() {
            String result = service.generateActivityDiagram(
                    "check if credentials are correct.");
            assertTrue(result.contains("if ("),       "Expected if block from check-if");
            assertTrue(result.contains("then (yes)"), "Expected yes branch");
            assertTrue(result.contains("else (no)"),  "Expected no branch");
            assertTrue(result.contains("endif"),      "Expected endif");
        }

        @Test
        @DisplayName("Decision condition text appears inside if()?")
        void conditionTextPreserved() {
            String result = service.generateActivityDiagram(
                    "if payment succeeds then confirm order else cancel order.");
            assertTrue(result.contains("payment succeeds?") ||
                       result.contains("payment succeeds"),
                    "Expected condition in if clause, got:\n" + result);
        }
    }

    // ── Loops ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Loop constructs")
    class Loops {

        @Test
        @DisplayName("while-do produces while...endwhile block")
        void whileLoop() {
            String result = service.generateActivityDiagram(
                    "while items remain in queue, do process next item.");
            assertTrue(result.contains("while ("),   "Expected while block");
            assertTrue(result.contains("endwhile"), "Expected endwhile");
        }

        @Test
        @DisplayName("repeat-until produces repeat...repeat while block")
        void repeatUntil() {
            String result = service.generateActivityDiagram(
                    "repeat send ping until acknowledgement received.");
            assertTrue(result.contains("repeat"),       "Expected repeat");
            assertTrue(result.contains("repeat while"), "Expected repeat while");
        }

        @Test
        @DisplayName("for-each produces repeat...repeat while block")
        void forEach() {
            String result = service.generateActivityDiagram(
                    "for each order: validate and ship.");
            assertTrue(result.contains("repeat"),       "Expected repeat from for-each");
            assertTrue(result.contains("repeat while"), "Expected repeat while from for-each");
        }

        @Test
        @DisplayName("'for i = 0 to N' counter loop emits init + while + increment")
        void forCounterLoop() {
            String result = service.generateActivityDiagram(
                    "for i = 0 to 10, do print i.");
            assertTrue(result.contains(":i = 0;"),          "Expected init node");
            assertTrue(result.contains("while (i <= 10?)"), "Expected while guard");
            assertTrue(result.contains(":i++;"),            "Expected increment step");
            assertTrue(result.contains("endwhile"),         "Expected endwhile");
        }

        @Test
        @DisplayName("'for i from 1 to 5' counter loop with body action")
        void forCounterLoopWithBody() {
            String result = service.generateActivityDiagram(
                    "for i from 1 to 5: display the result.");
            assertTrue(result.contains(":i = 1;"),         "Expected init node");
            assertTrue(result.contains("while (i <= 5?)"), "Expected while guard");
            assertTrue(result.contains("endwhile"),        "Expected endwhile");
        }

        @Test
        @DisplayName("'loop N times' emits i=0 + while(i<N) + i++")
        void loopNTimes() {
            String result = service.generateActivityDiagram(
                    "loop 5 times: print the value.");
            assertTrue(result.contains(":i = 0;"),        "Expected i init node");
            assertTrue(result.contains("while (i < 5?)"), "Expected while guard");
            assertTrue(result.contains(":i++;"),          "Expected increment step");
            assertTrue(result.contains("endwhile"),       "Expected endwhile");
        }

        @Test
        @DisplayName("'loop N times' without explicit action still has increment")
        void loopNTimesNoBody() {
            String result = service.generateActivityDiagram("loop 3 times.");
            assertTrue(result.contains(":i = 0;"),        "Expected i init");
            assertTrue(result.contains("while (i < 3?)"), "Expected while guard");
            assertTrue(result.contains(":i++;"),          "Expected increment");
        }

        @Test
        @DisplayName("'iterate N times' is equivalent to loop N times")
        void iterateNTimes() {
            String result = service.generateActivityDiagram(
                    "iterate 7 times: update the counter.");
            assertTrue(result.contains("while (i < 7?)"), "Expected while guard");
            assertTrue(result.contains(":i++;"),          "Expected increment step");
        }

        @Test
        @DisplayName("'while cond: body' bare while without do keyword")
        void whileBareColon() {
            String result = service.generateActivityDiagram(
                    "while i < 10: print i, i++.");
            assertTrue(result.contains("while ("),   "Expected while block");
            assertTrue(result.contains("i < 10"),    "Expected condition");
            assertTrue(result.contains("endwhile"),  "Expected endwhile");
        }

        @Test
        @DisplayName("'while cond, body' bare while with comma separator")
        void whileBareComma() {
            String result = service.generateActivityDiagram(
                    "while queue is not empty, process the next message.");
            assertTrue(result.contains("while ("),            "Expected while block");
            assertTrue(result.contains("queue is not empty"), "Expected condition");
            assertTrue(result.contains("endwhile"),           "Expected endwhile");
        }

        @Test
        @DisplayName("multi-step loop body is split into separate action nodes")
        void loopMultiStepBody() {
            String result = service.generateActivityDiagram(
                    "loop 4 times: read input, validate input.");
            assertTrue(result.contains("Read input"),     "Expected first step");
            assertTrue(result.contains("Validate input"), "Expected second step");
            assertTrue(result.contains(":i++;"),          "Expected increment");
        }
    }

    // ── Fork/join ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fork/join parallel execution")
    class ForkJoin {

        @Test
        @DisplayName("'in parallel A and B' produces fork...end fork block")
        void inParallel() {
            String result = service.generateActivityDiagram(
                    "in parallel send email and update database.");
            assertTrue(result.contains("fork"),     "Expected fork");
            assertTrue(result.contains("fork again"), "Expected fork again");
            assertTrue(result.contains("end fork"), "Expected end fork");
        }

        @Test
        @DisplayName("'simultaneously A and B' produces fork block")
        void simultaneously() {
            String result = service.generateActivityDiagram(
                    "simultaneously log the event and notify the user.");
            assertTrue(result.contains("fork"),     "Expected fork");
            assertTrue(result.contains("end fork"), "Expected end fork");
        }

        @Test
        @DisplayName("Both branches appear as action nodes inside fork")
        void branchesPresent() {
            String result = service.generateActivityDiagram(
                    "in parallel log event and send notification.");
            assertTrue(result.contains(":Log event;") || result.contains(":log event;"),
                    "Expected first branch, got:\n" + result);
            assertTrue(result.contains(":Send notification;") ||
                       result.contains(":send notification;"),
                    "Expected second branch, got:\n" + result);
        }

        @Test
        @DisplayName("'concurrently A and B' produces fork block")
        void concurrently() {
            String result = service.generateActivityDiagram(
                    "concurrently mix ingredients and preheat oven.");
            assertTrue(result.contains("fork"),       "Expected fork");
            assertTrue(result.contains("fork again"), "Expected fork again");
            assertTrue(result.contains("end fork"),   "Expected end fork");
        }

        @Test
        @DisplayName("'at the same time A and B' produces fork block")
        void atTheSameTime() {
            String result = service.generateActivityDiagram(
                    "at the same time mix ingredients and place plastic cup.");
            assertTrue(result.contains("fork"),       "Expected fork");
            assertTrue(result.contains("end fork"),   "Expected end fork");
            assertTrue(result.contains("mix ingredients") || result.contains("Mix ingredients"),
                    "Expected first branch in fork");
            assertTrue(result.contains("place plastic cup") || result.contains("Place plastic cup"),
                    "Expected second branch in fork");
        }

        @Test
        @DisplayName("Three-branch 'in parallel A, B and C' produces three fork blocks")
        void threeBranches() {
            String result = service.generateActivityDiagram(
                    "in parallel send email, update database and log audit trail.");
            long forkAgainCount = result.lines()
                    .filter(l -> l.trim().equals("fork again"))
                    .count();
            assertEquals(2, forkAgainCount,
                    "Expected 2 'fork again' lines for 3 branches, got:\n" + result);
            assertTrue(result.contains("end fork"), "Expected end fork");
        }

        @Test
        @DisplayName("'A and B concurrently' (trigger at end) produces fork block")
        void triggerAtEnd() {
            String result = service.generateActivityDiagram(
                    "send email and update database concurrently.");
            assertTrue(result.contains("fork"),       "Expected fork");
            assertTrue(result.contains("fork again"), "Expected fork again");
            assertTrue(result.contains("end fork"),   "Expected end fork");
        }

        @Test
        @DisplayName("'A and B simultaneously' (trigger at end) includes both branches")
        void triggerAtEndBranches() {
            String result = service.generateActivityDiagram(
                    "compress the file and upload the file simultaneously.");
            assertTrue(result.contains("compress") || result.contains("Compress"),
                    "Expected first branch, got:\n" + result);
            assertTrue(result.contains("upload") || result.contains("Upload"),
                    "Expected second branch, got:\n" + result);
        }
    }

    // ── Swimlanes / Partitions ─────────────────────────────────────────────

    @Nested
    @DisplayName("Swimlane / partition rendering")
    class Swimlanes {

        @Test
        @DisplayName("'Actor: action' notation creates partition blocks")
        void actorActionNotation() {
            String result = service.generateActivityDiagram("""
                    Customer: Place order
                    System: Validate order
                    Warehouse: Pack items""");
            assertTrue(result.contains("partition Customer"),  "Expected Customer partition");
            assertTrue(result.contains("partition System"),    "Expected System partition");
            assertTrue(result.contains("partition Warehouse"), "Expected Warehouse partition");
        }

        @Test
        @DisplayName("Actor actions appear as action nodes inside partitions")
        void actorActionsInPartition() {
            String result = service.generateActivityDiagram("""
                    Salesperson: Call client
                    Consultant: Prepare presentation""");
            assertTrue(result.contains(":Call client;") || result.contains(":call client;"),
                    "Expected Call client action, got:\n" + result);
            assertTrue(result.contains(":Prepare presentation;") ||
                       result.contains(":prepare presentation;"),
                    "Expected Prepare presentation action, got:\n" + result);
        }

        @Test
        @DisplayName("Each partition block is wrapped in braces")
        void partitionBraces() {
            String result = service.generateActivityDiagram("""
                    Salesperson: Call client
                    Technician: Install equipment""");
            assertTrue(result.contains("partition Salesperson {"), "Expected opening brace");
            assertTrue(result.contains("}"), "Expected closing brace");
        }

        @Test
        @DisplayName("Roles: Salesperson, Consultant, Technician, Customer")
        void roleKeywords() {
            String result = service.generateActivityDiagram("""
                    Salesperson: Call client
                    Consultant: Prepare presentation
                    Technician: Install equipment
                    Customer: Sign contract""");
            assertTrue(result.contains("partition Salesperson"), "Expected Salesperson");
            assertTrue(result.contains("partition Consultant"),  "Expected Consultant");
            assertTrue(result.contains("partition Technician"),  "Expected Technician");
            assertTrue(result.contains("partition Customer"),    "Expected Customer");
        }

        @Test
        @DisplayName("Multiple actions per actor go into same partition block")
        void multipleActionsPerActor() {
            String result = service.generateActivityDiagram("""
                    Salesperson: Call client
                    Salesperson: Send proposal""");
            long partitionCount = result.lines()
                    .filter(l -> l.trim().startsWith("partition Salesperson"))
                    .count();
            assertEquals(1, partitionCount, "Consecutive same-actor actions should share one partition");
        }

        @Test
        @DisplayName("Inline |Lane| notation is converted to partition blocks")
        void inlineLaneNotation() {
            String result = service.generateActivityDiagram("""
                    |Frontend| Submit form
                    |Backend| Process request""");
            assertTrue(result.contains("partition Frontend"), "Expected Frontend partition");
            assertTrue(result.contains("partition Backend"),  "Expected Backend partition");
        }

        @Test
        @DisplayName("Explicit partition{} input is preserved and normalised")
        void explicitPartitionInput() {
            String result = service.generateActivityDiagram("""
                    partition Salesperson {
                    Call client
                    Send quote
                    }
                    partition Customer {
                    Review quote
                    }""");
            assertTrue(result.contains("partition Salesperson {"), "Expected Salesperson block");
            assertTrue(result.contains("partition Customer {"),    "Expected Customer block");
            assertTrue(result.contains(":Call client;") || result.contains(":call client;"),
                    "Expected action inside partition");
        }

        @Test
        @DisplayName("Output is valid PlantUML structure")
        void validPlantUmlStructure() {
            String result = service.generateActivityDiagram("""
                    Salesperson: Call client
                    Customer: Review offer""");
            assertTrue(result.startsWith("@startuml"), "Expected @startuml");
            assertTrue(result.endsWith("@enduml"),     "Expected @enduml");
            assertTrue(result.contains("start"),       "Expected start");
            assertTrue(result.contains("stop"),        "Expected stop");
        }
    }

    // ── NLP pre-processing ─────────────────────────────────────────────────

    @Nested
    @DisplayName("NLP pre-processing and filler removal")
    class NlpPreprocessing {

        // ── Filler intros ──────────────────────────────────────────────────

        @Test
        @DisplayName("'The process is as follows' is stripped from output")
        void fillerIntroProcessIsAsFollows() {
            String result = service.generateActivityDiagram(
                    "The process is as follows. Login. Process request. Logout.");
            assertFalse(result.toLowerCase().contains("as follows"),
                    "Intro filler phrase should be removed, got:\n" + result);
            assertTrue(result.contains(":Login;") || result.contains(":login;"),
                    "Expected Login action node");
        }

        @Test
        @DisplayName("'Here are the steps' is stripped from output")
        void fillerIntroHereAreTheSteps() {
            String result = service.generateActivityDiagram(
                    "Here are the steps: Authenticate. Authorize. Respond.");
            assertFalse(result.toLowerCase().contains("here are the steps"),
                    "Intro phrase should be removed, got:\n" + result);
            long actionCount = result.lines()
                    .filter(l -> l.trim().startsWith(":") && l.trim().endsWith(";"))
                    .count();
            assertTrue(actionCount >= 1, "Expected at least one action node");
        }

        @Test
        @DisplayName("'The following steps' is stripped from output")
        void fillerIntroTheFollowingSteps() {
            String result = service.generateActivityDiagram(
                    "The following steps: Validate input. Save record. Return response.");
            assertFalse(result.toLowerCase().contains("the following steps"),
                    "Intro phrase should be removed, got:\n" + result);
        }

        @Test
        @DisplayName("'This workflow describes' sentence is stripped from output")
        void fillerIntroWorkflowDescribes() {
            String result = service.generateActivityDiagram(
                    "This workflow describes the login flow. Submit credentials. Validate token. Grant access.");
            assertFalse(result.toLowerCase().contains("this workflow describes"),
                    "Intro sentence should be removed, got:\n" + result);
            assertTrue(result.contains(":Submit credentials;") ||
                       result.contains(":submit credentials;"),
                    "Expected Submit credentials action");
        }

        // ── Inline filler connectors ───────────────────────────────────────

        @Test
        @DisplayName("'right after that' connector is not present in output")
        void rightAfterThatStripped() {
            String result = service.generateActivityDiagram(
                    "Login to system. Right after that, validate credentials.");
            assertFalse(result.toLowerCase().contains("right after that"),
                    "Filler connector should not appear in output, got:\n" + result);
            assertTrue(result.contains(":Login") || result.contains(":login"),
                    "Expected Login action");
            assertTrue(result.contains(":Validate") || result.contains(":validate"),
                    "Expected Validate action");
        }

        @Test
        @DisplayName("'subsequently' connector produces separate action nodes")
        void subsequentlyProducesSeparateNodes() {
            String result = service.generateActivityDiagram(
                    "Submit the form. Subsequently, display a confirmation message.");
            assertFalse(result.toLowerCase().contains("subsequently"),
                    "Subsequently should not appear in output, got:\n" + result);
            long actionCount = result.lines()
                    .filter(l -> l.trim().startsWith(":") && l.trim().endsWith(";"))
                    .count();
            assertTrue(actionCount >= 2, "Expected at least 2 action nodes, got:\n" + result);
        }

        @Test
        @DisplayName("'following this' connector is removed from output")
        void followingThisStripped() {
            String result = service.generateActivityDiagram(
                    "Authenticate user. Following this, load the dashboard.");
            assertFalse(result.toLowerCase().contains("following this"),
                    "Filler connector should not appear in output, got:\n" + result);
        }

        @Test
        @DisplayName("'thereafter' connector is removed from output")
        void thereafterStripped() {
            String result = service.generateActivityDiagram(
                    "Collect data. Thereafter, process the data.");
            assertFalse(result.toLowerCase().contains("thereafter"),
                    "Thereafter should not appear in output, got:\n" + result);
        }

        @Test
        @DisplayName("'in turn' connector is removed from output")
        void inTurnStripped() {
            String result = service.generateActivityDiagram(
                    "Receive request. In turn, process the request.");
            assertFalse(result.toLowerCase().contains("in turn"),
                    "In turn should not appear in output, got:\n" + result);
        }

        @Test
        @DisplayName("Multiple filler connectors produce clean separate actions")
        void multipleFillerConnectors() {
            String result = service.generateActivityDiagram(
                    "Open application. Right after that, enter credentials. Subsequently, click login.");
            assertFalse(result.toLowerCase().contains("right after that"),
                    "First connector should be stripped");
            assertFalse(result.toLowerCase().contains("subsequently"),
                    "Second connector should be stripped");
            long actionCount = result.lines()
                    .filter(l -> l.trim().startsWith(":") && l.trim().endsWith(";"))
                    .count();
            assertTrue(actionCount >= 3, "Expected at least 3 action nodes, got:\n" + result);
        }

        // ── Pure-filler step removal ───────────────────────────────────────

        @Test
        @DisplayName("Standalone 'finally' step does not become an action node")
        void standaloneFillyStepDropped() {
            String result = service.generateActivityDiagram(
                    "Login. Process request. Finally.");
            assertFalse(result.contains(":Finally;") || result.contains(":finally;"),
                    "Pure-filler step 'Finally' should not produce an action node, got:\n" + result);
        }

        @Test
        @DisplayName("'in conclusion' step is dropped")
        void inConclusionDropped() {
            String result = service.generateActivityDiagram(
                    "Collect data. Process data. In conclusion.");
            assertFalse(result.toLowerCase().contains(":in conclusion;"),
                    "Pure-filler step should be dropped, got:\n" + result);
        }

        @Test
        @DisplayName("'note that' step is dropped")
        void noteThatDropped() {
            String result = service.generateActivityDiagram(
                    "Validate input. Note that. Save record.");
            assertFalse(result.toLowerCase().contains(":note that;"),
                    "'note that' should not produce an action node, got:\n" + result);
        }

        // ── Leading connector stripping ────────────────────────────────────

        @Test
        @DisplayName("'subsequently' as leading connector is stripped from action text")
        void subsequentlyAsLeadingConnector() {
            String result = service.generateActivityDiagram(
                    "Login.\nSubsequently validate token.");
            assertFalse(result.toLowerCase().contains(":subsequently"),
                    "Leading 'subsequently' should be stripped from action text, got:\n" + result);
        }

        @Test
        @DisplayName("'therefore' as leading connector is stripped from action text")
        void thereforeAsLeadingConnector() {
            String result = service.generateActivityDiagram(
                    "Check balance.\nTherefore approve payment.");
            assertFalse(result.toLowerCase().contains(":therefore"),
                    "Leading 'therefore' should be stripped, got:\n" + result);
        }

        // ── Numbered steps ─────────────────────────────────────────────────

        @Test
        @DisplayName("Numbered steps '1. 2. 3.' are split into separate action nodes")
        void numberedStepsSplit() {
            String result = service.generateActivityDiagram(
                    "1. Login 2. Process request 3. Return response");
            long actionCount = result.lines()
                    .filter(l -> l.trim().startsWith(":") && l.trim().endsWith(";"))
                    .count();
            assertEquals(3, actionCount,
                    "Expected 3 action nodes from numbered steps, got:\n" + result);
        }

        // ── Output structure ──────────────────────────────────────────────

        @Test
        @DisplayName("Pre-processed diagram is still valid PlantUML structure")
        void preprocessedOutputIsValid() {
            String result = service.generateActivityDiagram(
                    "The process is as follows. Login. Right after that, validate. Logout.");
            assertTrue(result.startsWith("@startuml"), "Must start with @startuml");
            assertTrue(result.endsWith("@enduml"),     "Must end with @enduml");
            assertTrue(result.contains("start"),       "Must contain start");
            assertTrue(result.contains("stop"),        "Must contain stop");
        }

        @Test
        @DisplayName("All-filler input returns default diagram (not blank)")
        void allFillerReturnsDefault() {
            // Only filler — after preprocessing nothing actionable remains
            String result = service.generateActivityDiagram(
                    "The process is as follows. Finally. In conclusion.");
            assertFalse(result.isBlank(), "Should return default diagram, not blank");
            assertTrue(result.startsWith("@startuml"), "Must be valid PlantUML");
        }
    }

    // ── Default diagram ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Default diagram")
    class DefaultDiagram {

        @Test
        @DisplayName("Null input returns default diagram without throwing")
        void nullInput() {
            String result = service.generateActivityDiagram(null);
            assertFalse(result.isBlank());
            assertTrue(result.startsWith("@startuml"));
            assertTrue(result.endsWith("@enduml"));
        }

        @Test
        @DisplayName("Blank input returns default diagram")
        void blankInput() {
            String result = service.generateActivityDiagram("   ");
            assertTrue(result.startsWith("@startuml"));
            assertTrue(result.contains("start"));
        }

        @Test
        @DisplayName("Default diagram showcases all constructs")
        void defaultContainsAllConstructs() {
            String result = service.defaultDiagram();
            assertTrue(result.contains("if ("),        "Default should have decision");
            assertTrue(result.contains("fork"),        "Default should have fork");
            assertTrue(result.contains("while ("),     "Default should have loop");
            assertTrue(result.contains("endif"),       "Default should close decision");
            assertTrue(result.contains("end fork"),    "Default should close fork");
            assertTrue(result.contains("endwhile"),    "Default should close loop");
        }
    }
}
