package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import java.util.regex.Pattern;

public class DataSanitizer {

    private static final Pattern SENSITIVE_PATTERN =
            Pattern.compile("(?i)\"(password|secret|token|credentials|access_token|refresh_token)\"\\s*:\\s*\"[^\"]+\"");

    public static String sanitizeBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        return SENSITIVE_PATTERN.matcher(body).replaceAll("\"$1\": \"[REDACTED]\"");
    }
}
