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
}
