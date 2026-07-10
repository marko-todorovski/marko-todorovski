package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.ai.AiModelService;
import com.example.aidiagramgenerator.ai.AiServiceException;
import com.example.aidiagramgenerator.domain.EntityNode;
import com.example.aidiagramgenerator.domain.Relationship;
import com.example.aidiagramgenerator.domain.SemanticModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SemanticExtractionServiceImplSequenceTest {

    private final SemanticExtractionServiceImpl service = new SemanticExtractionServiceImpl(new AiModelService() {
        @Override
        public String getModelName() {
            return "fallback-test";
        }

        @Override
        public String generateStructuredResponse(String prompt) {
            throw new AiServiceException("force heuristic fallback");
        }
    });

    @Test
    void extractsSimpleCallAndReturn() {
        String text = "User sends searchMessage() to WebServer. WebServer returns resultData() to User.";

        SemanticModel model = service.extract(text);

        List<String> participantNames = model.getEntities().stream()
                .map(e -> e.getName().toLowerCase()).toList();
        assertTrue(participantNames.stream().anyMatch(n -> n.contains("user")),
                "Should detect User participant");
        assertTrue(participantNames.stream().anyMatch(n -> n.contains("webserver") || n.contains("web")),
                "Should detect WebServer participant");

        List<Relationship> msgs = model.getRelationships();
        assertFalse(msgs.isEmpty(), "Should extract at least one message");

        boolean hasForwardCall = msgs.stream().anyMatch(r -> r.getType().equals("sends"));
        assertTrue(hasForwardCall, "Should have a 'sends' forward message");

        boolean hasReturn = msgs.stream().anyMatch(r -> r.getType().equals("returns"));
        assertTrue(hasReturn, "Should have a 'returns' message");
    }

    @Test
    void extractsMethodCallLabels() {
        String text = "User sends searchMessage() to WebServer. WebServer returns resultData() to User.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        boolean hasSearchLabel = msgs.stream()
                .anyMatch(r -> r.getSrcMultiplicity() != null && r.getSrcMultiplicity().contains("searchMessage"));
        boolean hasResultLabel = msgs.stream()
                .anyMatch(r -> r.getSrcMultiplicity() != null && r.getSrcMultiplicity().contains("resultData"));
        assertTrue(hasSearchLabel || hasResultLabel, "Should extract method call labels from message flow");
    }

    @Test
    void extractsAtmPinRequestFlow() {
        String text = "ATM asks User for PIN. User sends PIN to ATM. ATM requests validation to Bank. Bank returns authResult() to ATM.";

        SemanticModel model = service.extract(text);

        List<String> names = model.getEntities().stream().map(e -> e.getName().toLowerCase()).toList();
        assertTrue(names.stream().anyMatch(n -> n.contains("atm")), "Should detect ATM participant");
        assertTrue(names.stream().anyMatch(n -> n.contains("user")), "Should detect User participant");
        assertTrue(names.stream().anyMatch(n -> n.contains("bank")), "Should detect Bank participant");

        List<Relationship> msgs = model.getRelationships();
        assertTrue(msgs.size() >= 2, "Should extract at least 2 messages from multi-step flow");
    }

    @Test
    void distinguishesActorFromParticipant() {
        String text = "User sends loginRequest() to AuthService. AuthService returns token() to User.";

        SemanticModel model = service.extract(text);

        List<String> names = model.getEntities().stream().map(e -> e.getName().toLowerCase()).toList();
        assertTrue(names.stream().anyMatch(n -> n.contains("user")), "User should be detected");
        assertTrue(names.stream().anyMatch(n -> n.contains("authservice") || n.contains("auth")),
                "AuthService should be detected");
    }

    @Test
    void extractsMessageOrderPreserved() {
        String text = """
                Client sends request() to Server.
                Server calls processData() to Database.
                Database returns queryResult() to Server.
                Server returns response() to Client.
                """;

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        assertTrue(msgs.size() >= 3, "Should extract at least 3 ordered messages");

        // First message should be from Client to Server
        Relationship first = msgs.get(0);
        assertTrue(first.getSource().toLowerCase().contains("client") ||
                   first.getTarget().toLowerCase().contains("server"),
                "First message should involve Client->Server");

        // Last message should be a return
        Relationship last = msgs.get(msgs.size() - 1);
        assertEquals("returns", last.getType(), "Last message should be a return");
    }

    @Test
    void isNotTriggeredForUseCaseText() {
        String text = "Create a use case diagram. The User can login and view grades. Student can submit assignments.";

        SemanticModel model = service.extract(text);

        // For use-case text, no 'sends'/'returns' relationships should appear
        boolean hasSequenceRel = model.getRelationships().stream()
                .anyMatch(r -> r.getType().equals("sends") || r.getType().equals("returns"));
        assertFalse(hasSequenceRel, "Use-case text should not produce sequence message relationships");
    }

    // ─── ALT fragment tests ───────────────────────────────────────────────────

    @Test
    void extractsAltFragmentFromIfOtherwise() {
        String text = "If amount > 1000, ATM sends requestConfirmation() to Bank. " +
                      "Otherwise, ATM sends dispenseCash() to User.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_start")),
                "Should produce alt_start marker");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_else")),
                "Should produce alt_else marker");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_end")),
                "Should produce alt_end marker");
    }

    @Test
    void altStartStoresConditionText() {
        String text = "If amount > 1000, ATM sends requestConfirmation() to Bank. " +
                      "Otherwise, ATM sends dispenseCash() to User.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertEquals("amount > 1000", altStart.get().getSrcMultiplicity(),
                "Should store the condition in srcMultiplicity");
    }

    @Test
    void normalizeExceedsToGreaterThan() {
        String text = "If balance exceeds 500, Bank sends approval() to ATM. " +
                      "Otherwise, Bank sends rejected() to ATM.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertTrue(altStart.get().getSrcMultiplicity().contains(">"),
                "Should normalize 'exceeds' to '>'");
    }

    @Test
    void normalizeGreaterThanPhraseToSymbol() {
        String text = "If amount is greater than 1000, Server sends premium() to Client. " +
                      "Otherwise, Server sends basic() to Client.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertTrue(altStart.get().getSrcMultiplicity().contains(">"),
                "Should normalize 'greater than' to '>'");
    }

    @Test
    void normalizeLessThanPhraseToSymbol() {
        String text = "If balance is less than amount, ATM sends error() to User. " +
                      "Otherwise, ATM sends cash() to User.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertTrue(altStart.get().getSrcMultiplicity().contains("<"),
                "Should normalize 'less than' to '<'");
    }

    @Test
    void ifOnlyWithoutElse() {
        String text = "User sends login() to Server. If credentials are valid, Server sends dashboard() to User.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_start")),
                "Should have alt_start");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_end")),
                "Should auto-close alt block");
        assertFalse(msgs.stream().anyMatch(r -> r.getType().equals("alt_else")),
                "Should not have alt_else when no else/otherwise present");
    }

    @Test
    void altMarkersDontCreateParticipants() {
        String text = "If amount > 1000, ATM sends requestConfirmation() to Bank. " +
                      "Otherwise, ATM sends dispenseCash() to User.";

        SemanticModel model = service.extract(text);

        boolean hasAltParticipant = model.getEntities().stream()
                .anyMatch(e -> e.getName().startsWith("__"));
        assertFalse(hasAltParticipant, "ALT marker names should not appear as participants");
    }

    // ─── PAR fragment tests ───────────────────────────────────────────────

    @Test
    void extractsParFragmentFromSimultaneousPhrase() {
        String text = "WebServer sends sendData() to SQLServer simultaneously. " +
                      "WebServer sends sendData() to TS simultaneously.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_start")),
                "Should produce par_start marker");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_else")),
                "Should produce par_else marker for second parallel branch");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_end")),
                "Should produce par_end marker");
    }

    @Test
    void inParallelTriggersPar() {
        String text = "Client sends request() to Server in parallel. " +
                      "Client sends backup() to BackupServer in parallel.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_start")),
                "'in parallel' should open a PAR block");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_end")),
                "Should close the PAR block");
    }

    @Test
    void atTheSameTimeTriggersPar() {
        String text = "Server sends data() to NodeA at the same time. " +
                      "Server sends data() to NodeB at the same time.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_start")),
                "'at the same time' should open a PAR block");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("par_end")),
                "Should close the PAR block");
    }

    @Test
    void parStartHasNullCondition() {
        String text = "WebServer sends sendData() to SQLServer simultaneously. " +
                      "WebServer sends sendData() to TS simultaneously.";

        SemanticModel model = service.extract(text);
        Optional<Relationship> parStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("par_start"))
                .findFirst();

        assertTrue(parStart.isPresent(), "Should have par_start");
        assertNull(parStart.get().getSrcMultiplicity(),
                "PAR block has no condition, srcMultiplicity should be null");
    }

    @Test
    void parMarkersDontCreateParticipants() {
        String text = "WebServer sends sendData() to SQLServer simultaneously. " +
                      "WebServer sends sendData() to TS simultaneously.";

        SemanticModel model = service.extract(text);

        boolean hasParParticipant = model.getEntities().stream()
                .anyMatch(e -> e.getName().startsWith("__"));
        assertFalse(hasParParticipant, "PAR marker names should not appear as participants");
    }

    // ─── Multi-word participant detection tests ───────────────────────────────

    @Test
    void extractsWebServerAndSqlServerAsParticipants() {
        String text = "Web Server sends query() to SQL Server. SQL Server returns result() to Web Server.";

        SemanticModel model = service.extract(text);

        List<String> names = model.getEntities().stream().map(e -> e.getName()).toList();
        assertTrue(names.stream().anyMatch(n -> n.equals("Web Server")),
                "Should detect 'Web Server' as a single participant");
        assertTrue(names.stream().anyMatch(n -> n.equals("SQL Server")),
                "Should detect 'SQL Server' as a single participant");
    }

    @Test
    void extractsTransactionServerAsParticipant() {
        String text = "User sends pay() to Transaction Server. Transaction Server returns receipt() to User.";

        SemanticModel model = service.extract(text);

        List<String> names = model.getEntities().stream().map(e -> e.getName()).toList();
        assertTrue(names.stream().anyMatch(n -> n.equals("Transaction Server")),
                "Should detect 'Transaction Server' as a single participant");
    }

    @Test
    void multiWordParticipantAppearsInMessageSourceAndTarget() {
        String text = "Web Server sends query() to SQL Server. SQL Server returns result() to Web Server.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships().stream()
                .filter(r -> r.getType().equals("sends") || r.getType().equals("returns"))
                .toList();
        assertFalse(msgs.isEmpty(), "Should extract messages");
        // Sources and targets should be the full display names, not partial tokens
        boolean hasWebServerMsg = msgs.stream()
                .anyMatch(r -> r.getSource().equals("Web Server") || r.getTarget().equals("Web Server"));
        boolean hasSqlServerMsg = msgs.stream()
                .anyMatch(r -> r.getSource().equals("SQL Server") || r.getTarget().equals("SQL Server"));
        assertTrue(hasWebServerMsg, "Messages should reference 'Web Server' (full display name)");
        assertTrue(hasSqlServerMsg, "Messages should reference 'SQL Server' (full display name)");
    }

    @Test
    void multiWordDetectionIsCaseInsensitive() {
        String text = "web server sends data() to sql server. sql server returns rows() to web server.";

        SemanticModel model = service.extract(text);

        List<String> names = model.getEntities().stream().map(e -> e.getName()).toList();
        assertTrue(names.stream().anyMatch(n -> n.equals("Web Server")),
                "Case-insensitive detection should normalize to canonical 'Web Server'");
        assertTrue(names.stream().anyMatch(n -> n.equals("SQL Server")),
                "Case-insensitive detection should normalize to canonical 'SQL Server'");
    }

    // ─── Message direction tests ──────────────────────────────────────────────

    @Test
    void sendsVerbProducesTypeOfSends() {
        String text = "User sends loginRequest() to AuthServer. AuthServer returns token() to User.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("sends")),
                "'sends' verb should produce relationship type 'sends'");
    }

    @Test
    void returnsVerbProducesTypeOfReturns() {
        String text = "User sends loginRequest() to AuthServer. AuthServer returns token() to User.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("returns")),
                "'returns' verb should produce relationship type 'returns'");
    }

    @Test
    void confirmsVerbProducesReturnTypeMessage() {
        String text = "ATM sends verifyPin() to Bank. Bank confirms approval() to ATM.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        boolean hasConfirmAsReturn = msgs.stream()
                .anyMatch(r -> r.getType().equals("returns") && r.getSource().equals("Bank"));
        assertTrue(hasConfirmAsReturn,
                "'confirms' verb should produce a return-type relationship from Bank");
    }

    @Test
    void respondsVerbProducesReturnTypeMessage() {
        String text = "Client sends getData() to Server. Server responds result() to Client.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        boolean hasReturnFromServer = msgs.stream()
                .anyMatch(r -> r.getType().equals("returns") && r.getSource().equals("Server"));
        assertTrue(hasReturnFromServer,
                "'responds' verb should produce a return-type relationship");
    }

    // ─── ATM withdrawal scenario tests ───────────────────────────────────────

    @Test
    void altBodyIncludesMultipleSentencesAfterIf() {
        // Sentences after "If..." should stay inside the alt block until "Otherwise/Else"
        String text = "User sends requestCash() to ATM. " +
                      "If balance is sufficient, ATM sends dispenseCash() to Dispenser. " +
                      "Dispenser returns cashDispensed() to ATM. " +
                      "ATM returns receipt() to User. " +
                      "Otherwise ATM returns insufficientFunds() to User.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        // Must have alt structure
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_start")),
                "Should have alt_start");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_else")),
                "Should have alt_else (Otherwise branch)");
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_end")),
                "Should have alt_end");

        // Verify order: alt_start before alt_else before alt_end
        int startIdx = -1, elseIdx = -1, endIdx = -1;
        for (int i = 0; i < msgs.size(); i++) {
            String t = msgs.get(i).getType();
            if (t.equals("alt_start") && startIdx < 0) startIdx = i;
            if (t.equals("alt_else")  && elseIdx  < 0) elseIdx  = i;
            if (t.equals("alt_end")   && endIdx   < 0) endIdx   = i;
        }
        assertTrue(startIdx < elseIdx && elseIdx < endIdx,
                "alt markers must appear in order: alt_start < alt_else < alt_end");

        // dispenseCash, cashDispensed, receipt should all be BEFORE alt_else (i.e. inside the if-body)
        int dispenseIdx    = indexOfLabel(msgs, "dispenseCash()");
        int cashReturnIdx  = indexOfLabel(msgs, "cashDispensed()");
        int receiptIdx     = indexOfLabel(msgs, "receipt()");
        assertTrue(dispenseIdx  >= 0 && dispenseIdx  < elseIdx, "dispenseCash() must be inside if-body");
        assertTrue(cashReturnIdx >= 0 && cashReturnIdx < elseIdx, "cashDispensed() must be inside if-body");
        assertTrue(receiptIdx    >= 0 && receiptIdx    < elseIdx, "receipt() must be inside if-body");

        // insufficientFunds should be AFTER alt_else (i.e. in else-body)
        int insuffIdx = indexOfLabel(msgs, "insufficientFunds()");
        assertTrue(insuffIdx >= 0 && insuffIdx > elseIdx, "insufficientFunds() must be in else-body");
    }

    @Test
    void bankApprovesIsExtractedAsReturnType() {
        String text = "ATM sends verifyAmount() to Bank. Bank approves transaction() to ATM.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        boolean bankReturns = msgs.stream()
                .anyMatch(r -> r.getType().equals("returns") && r.getSource().equals("Bank"));
        assertTrue(bankReturns, "'approves' verb should produce a return-type relationship from Bank");
    }

    @Test
    void bankRejectsIsExtractedAsReturnType() {
        String text = "ATM sends verifyAmount() to Bank. Bank rejects transaction() to ATM.";

        SemanticModel model = service.extract(text);

        List<Relationship> msgs = model.getRelationships();
        boolean bankReturns = msgs.stream()
                .anyMatch(r -> r.getType().equals("returns") && r.getSource().equals("Bank"));
        assertTrue(bankReturns, "'rejects' verb should produce a return-type relationship from Bank");
    }

    @Test
    void conditionConfirmationRequiredNormalized() {
        String text = "If confirmation is required, ATM sends requestApproval() to Bank. " +
                      "Otherwise, ATM sends dispenseCash() to Dispenser.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertEquals("confirmation required", altStart.get().getSrcMultiplicity(),
                "Should normalize 'confirmation is required' to 'confirmation required'");
    }

    @Test
    void conditionBalanceSufficientNormalized() {
        String text = "If balance is sufficient, ATM sends dispenseCash() to Dispenser. " +
                      "Otherwise ATM returns error() to User.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertEquals("balance sufficient", altStart.get().getSrcMultiplicity(),
                "Should normalize 'balance is sufficient' to 'balance sufficient'");
    }

    @Test
    void conditionAmountExceeds1000Normalized() {
        String text = "If the amount exceeds 1000, ATM sends requestApproval() to Bank. " +
                      "Otherwise, ATM sends dispenseCash() to Dispenser.";

        SemanticModel model = service.extract(text);

        Optional<Relationship> altStart = model.getRelationships().stream()
                .filter(r -> r.getType().equals("alt_start"))
                .findFirst();
        assertTrue(altStart.isPresent());
        assertEquals("amount > 1000", altStart.get().getSrcMultiplicity(),
                "Should strip leading 'the' and normalize 'exceeds' to '>'");
    }

    // ─── Component-based (Register / Dispenser / Front) ─────────────────────

    @Test
    void extractsRegisterAndDispenserAsParticipants() {
        String text = "User sends insertCoin() to Register. Register sends checkStock() to Dispenser. " +
                      "Dispenser returns sodaAvailable() to Register.";

        SemanticModel model = service.extract(text);
        List<String> names = model.getEntities().stream()
                .map(EntityNode::getName).toList();

        assertTrue(names.contains("Register"), "Should detect 'Register' as participant");
        assertTrue(names.contains("Dispenser"), "Should detect 'Dispenser' as participant");
        assertTrue(names.contains("User"),      "Should detect 'User' as actor");
    }

    @Test
    void checksVerbProducesTypeSends() {
        String text = "Register checks inventory() to Dispenser.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships().stream()
                .filter(r -> r.getType().equals("sends")).toList();

        assertFalse(msgs.isEmpty(), "Should extract at least one 'sends' message");
        assertTrue(msgs.stream().anyMatch(r -> r.getSource().equals("Register")
                && r.getTarget().equals("Dispenser")),
                "Register -> Dispenser message expected");
    }

    @Test
    void chainedSendsExtractsBothTargets() {
        String text = "Register sends updateReserve() to Front and releaseSoda() to Dispenser.";

        SemanticModel model = service.extract(text);
        List<Relationship> sends = model.getRelationships().stream()
                .filter(r -> r.getType().equals("sends")).toList();

        assertTrue(sends.stream().anyMatch(r -> r.getSource().equals("Register")
                && r.getTarget().equals("Front")
                && r.getSrcMultiplicity().equals("updateReserve()")),
                "Should extract Register -> Front: updateReserve()");
        assertTrue(sends.stream().anyMatch(r -> r.getSource().equals("Register")
                && r.getTarget().equals("Dispenser")
                && r.getSrcMultiplicity().equals("releaseSoda()")),
                "Should extract Register -> Dispenser: releaseSoda()");
    }

    @Test
    void sodaMachineFullFlowExtractesAllMessages() {
        String text =
                "User sends insertCoin() to Register. " +
                "Register returns coinAccepted() to User. " +
                "User sends selectSoda() to Register. " +
                "Register sends checkStock() to Dispenser. " +
                "If soda is available, Dispenser returns sodaAvailable() to Register. " +
                "Register sends dispenseSoda() to Dispenser. " +
                "Dispenser returns sodaDispensed() to Register. " +
                "Register returns change() to User. " +
                "Otherwise Dispenser returns outOfStock() to Register. " +
                "Register returns refundCoin() to User.";

        SemanticModel model = service.extract(text);
        List<Relationship> msgs = model.getRelationships();

        // Participants
        List<String> names = model.getEntities().stream().map(EntityNode::getName).toList();
        assertTrue(names.contains("User"),      "User expected");
        assertTrue(names.contains("Register"),  "Register expected");
        assertTrue(names.contains("Dispenser"), "Dispenser expected");

        // Contains alt_start with "soda is available"
        Optional<Relationship> altStart = msgs.stream()
                .filter(r -> r.getType().equals("alt_start")).findFirst();
        assertTrue(altStart.isPresent(), "ALT start expected");
        assertTrue(altStart.get().getSrcMultiplicity().contains("soda"),
                "Condition should mention 'soda'");

        // Contains alt_else
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("alt_else")),
                "ALT else expected");

        // checkStock sends from Register to Dispenser
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("sends")
                && r.getSource().equals("Register")
                && r.getTarget().equals("Dispenser")
                && "checkStock()".equals(r.getSrcMultiplicity())),
                "Register -> Dispenser: checkStock() expected");

        // coinAccepted returns from Register to User
        assertTrue(msgs.stream().anyMatch(r -> r.getType().equals("returns")
                && r.getSource().equals("Register")
                && r.getTarget().equals("User")
                && "coinAccepted()".equals(r.getSrcMultiplicity())),
                "Register -> User: coinAccepted() expected");
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private static int indexOfLabel(List<Relationship> msgs, String label) {
        for (int i = 0; i < msgs.size(); i++) {
            String lbl = msgs.get(i).getSrcMultiplicity();
            if (label.equals(lbl)) return i;
        }
        return -1;
    }
}
