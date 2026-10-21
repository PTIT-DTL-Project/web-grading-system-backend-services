package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import java.time.Duration;
import java.util.Set;

public record ToolDefinition(
        String name,
        String description,
        String schemaVersion,
        String capabilityGroup,
        String action,
        String resourceType,
        EffectType effectType,
        Set<String> requiredRoles,
        Duration timeout,
        int outputLimitBytes
) {}
