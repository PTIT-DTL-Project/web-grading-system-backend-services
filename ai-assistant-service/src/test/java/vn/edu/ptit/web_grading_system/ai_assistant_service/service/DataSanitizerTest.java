package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class DataSanitizerTest {

    @Test
    void sanitizeBody_redactsSensitiveFields() {
        String input = """
            {"username":"student1","password":"SecretPassword123","token":"jwt.token.here"}
        """;
        String sanitized = DataSanitizer.sanitizeBody(input);
        assertThat(sanitized).doesNotContain("SecretPassword123");
        assertThat(sanitized).doesNotContain("jwt.token.here");
        assertThat(sanitized).contains("\"password\": \"[REDACTED]\"");
        assertThat(sanitized).contains("\"token\": \"[REDACTED]\"");
    }
}
