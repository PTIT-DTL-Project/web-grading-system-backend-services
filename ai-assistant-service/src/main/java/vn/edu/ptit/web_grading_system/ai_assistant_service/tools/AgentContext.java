package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import java.util.Set;
import java.util.UUID;

public record AgentContext(
        UUID userId,
        String email,
        Set<String> roles,
        UUID conversationId,
        UUID runId,
        String gatewaySecret
) {}
