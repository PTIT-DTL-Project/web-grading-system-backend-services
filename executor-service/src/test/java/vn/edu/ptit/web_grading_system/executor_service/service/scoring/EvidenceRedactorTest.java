package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase A4 sanitize: one redactor for every evidence output path.
 */
class EvidenceRedactorTest {

    @Test
    void nestedJsonMaskedStructureIntact() {
        String out = EvidenceRedactor.redact(
                "{\"user\":{\"password\":\"x\"},\"tokens\":[\"a\",{\"authorization\":\"b\"}]}");

        assertTrue(out.contains("\"password\":\"***\""));
        assertTrue(out.contains("\"authorization\":\"***\""));
        assertFalse(out.contains("\"password\":\"x\""));
    }

    @Test
    void legitPluralFieldNamesUntouched() {
        // Exact key match only: "tokens" must not trip the "token" rule.
        String out = EvidenceRedactor.redact(
                "{\"tokens\":[\"a\"],\"accessToken\":\"s3cret\"}");

        assertTrue(out.contains("\"tokens\":[\"a\"]"));
        assertTrue(out.contains("\"accessToken\":\"***\""));
    }

    @Test
    void cleanJsonByteIdentical() {
        String clean = "{\n  \"id\": 1,\n  \"name\": \"book\"\n}";

        assertEquals(clean, EvidenceRedactor.redact(clean));
    }

    @Test
    void bearerInPlainTextMasked() {
        assertEquals("call with Bearer *** failed",
                EvidenceRedactor.redact("call with Bearer abc.def-ghi failed"));
    }

    @Test
    void keyValuePairsMaskedCaseInsensitively() {
        assertEquals("login PASSWORD=*** ok",
                EvidenceRedactor.redact("login PASSWORD: s3cret ok"));
    }

    @Test
    void jdbcCredentialsMaskedHostKept() {
        String out = EvidenceRedactor.redact(
                "connect failed jdbc:postgresql://app:P@ss@db:5432/x");

        assertTrue(out.contains("jdbc:postgresql://app:***@db:5432/x"));
        assertFalse(out.contains("P@ss"));
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertNull(EvidenceRedactor.redact(null));
        assertEquals("", EvidenceRedactor.redact(""));
    }

    @Test
    void nonJsonUnchanged() {
        assertEquals("not json at all", EvidenceRedactor.redact("not json at all"));
    }

    @Test
    void bearerInsideJsonStringMasked() {
        String out = EvidenceRedactor.redact("{\"msg\":\"upstream said Bearer abc123\"}");

        assertTrue(out.contains("Bearer ***"));
        assertFalse(out.contains("abc123"));
    }

    @Test
    void nestedJsonStringDescends() {
        // Pullfrog probe: escaped JSON inside a verdict array — the sweep
        // cannot see through escaping, so the walk must descend.
        String out = EvidenceRedactor.redact(
                "[{\"kind\":\"contains\",\"actual\":\"{\\\"access_token\\\":"
                        + "\\\"SUPERSECRET2\\\",\\\"client_secret\\\":\\\"CSECRET\\\"}\","
                        + "\"passed\":false}]");

        assertTrue(out.contains("\\\"access_token\\\":\\\"***\\\""));
        assertTrue(out.contains("\\\"client_secret\\\":\\\"***\\\""));
        assertFalse(out.contains("SUPERSECRET2"));
        assertFalse(out.contains("CSECRET"));
    }

    @Test
    void deepNestingMasksWithoutDepthCap() throws Exception {
        tools.jackson.databind.ObjectMapper om = new tools.jackson.databind.ObjectMapper();
        String level = om.writeValueAsString(java.util.Map.of("token", "deep"));
        for (int i = 0; i < 4; i++) {
            level = om.writeValueAsString(java.util.Map.of("n", level));
        }

        String out = EvidenceRedactor.redact(level);

        assertFalse(out.contains("deep"));
        // Escaping compounds per ancestor level, so assert the mask marker
        // rather than an exact escaped shape.
        assertTrue(out.contains("***"));
    }

    @Test
    void cleanNestedJsonStringByteIdentical() {
        String clean = "{\"outer\":\"{\\\"id\\\":42}\"}";

        assertEquals(clean, EvidenceRedactor.redact(clean));
    }
}
