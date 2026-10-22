package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import java.util.List;

public record AiExplanationResultDto(
        String overallSummary,
        List<StepAnalysis> stepAnalyses
) {}
