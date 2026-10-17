package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.StepAnalysis;
import vn.edu.ptit.web_grading_system.ai_assistant_service.entity.AiExplanation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.AiExplanationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiExplanationService {

    private final AiExplanationRepository explanationRepository;
    private final ResultServiceClient resultServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final GatewayTrustProperties gatewayProperties;
    private final AiModelProperties aiModelProperties;
    private final Gson gson = new Gson();

    @Transactional(readOnly = true)
    public Optional<AiExplanationResponse> getExplanation(UUID submissionId, UUID studentId) {
        return explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS")
                .map(entity -> {
                    if (!studentId.equals(entity.getStudentId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Not your submission");
                    }
                    return toResponse(entity, true);
                });
    }

    // Review: 2026-10-17, Pullfrog — deliberately NOT @Transactional: the
    // Feign reads and the multi-second LLM call below must not hold one of the
    // 10 Hikari connections for the whole request. Each repository call opens
    // its own short transaction; the unique index (V2) is the atomicity backstop.
    public AiExplanationResponse explainSubmission(UUID submissionId, UUID studentId) {
        // 1. Idempotency check: return cached if already analyzed
        Optional<AiExplanation> cached = explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS");
        if (cached.isPresent()) {
            AiExplanation entity = cached.get();
            if (!studentId.equals(entity.getStudentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Not your submission");
            }
            return toResponse(entity, true);
        }

        // 2. Fetch submission results
        List<ResultDto> results = resultServiceClient.getBySubmissionId(
                submissionId, studentId.toString(), gatewayProperties.secret());
        if (results == null || results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No grading results found for submission");
        }

        ResultDto result = results.get(0);
        // Ownership verification
        if (!studentId.equals(result.getStudentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You are not the owner of this submission");
        }

        // 3. Fetch assignment metadata
        AssignmentDetailDto assignment = null;
        try {
            assignment = courseServiceClient.getAssignment(
                    result.getAssignmentId(), studentId.toString(), gatewayProperties.secret());
        } catch (Exception e) {
            log.warn("Could not fetch assignment details for id {}: {}", result.getAssignmentId(), e.getMessage());
        }

        // 4. Build prompt & call LLM
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(assignment, result);
        String rawLlmResponse = llmClient.generateCompletion(systemPrompt, userPrompt);

        // 5. Parse LLM response
        String overallSummary = "Bài nộp đã được phân tích toàn diện.";
        String diagnosis = "Phân tích tự động.";
        List<StepAnalysis> stepAnalyses = new ArrayList<>();

        try {
            JsonObject json = JsonParser.parseString(rawLlmResponse).getAsJsonObject();
            if (json.has("overallSummary")) {
                overallSummary = json.get("overallSummary").getAsString();
                diagnosis = overallSummary;
            }
            if (json.has("stepAnalyses") && json.get("stepAnalyses").isJsonArray()) {
                JsonArray array = json.getAsJsonArray("stepAnalyses");
                for (var elem : array) {
                    stepAnalyses.add(gson.fromJson(elem, StepAnalysis.class));
                }
            }
        } catch (Exception ex) {
            log.error("Failed to parse LLM structured output: {}", ex.getMessage());
        }

        // 6. Save in DB cache
        AiExplanation entity = AiExplanation.builder()
                .submissionId(submissionId)
                .studentId(studentId)
                .category("FULL_ANALYSIS")
                .diagnosis(diagnosis)
                .overallSummary(overallSummary)
                // Review: 2026-10-17, Pullfrog — a null hints/keywords list made
                // the mapper return null; Stream.flatMap(null) then threw NPE and
                // turned a valid LLM answer into a 500. Optional.ofNullable keeps
                // the inner stream non-null for absent lists.
                .actionableHints(gson.toJson(stepAnalyses.stream()
                        .flatMap(s -> Optional.ofNullable(s.getActionableHints()).orElse(List.of()).stream())
                        .toList()))
                .learningKeywords(gson.toJson(stepAnalyses.stream()
                        .flatMap(s -> Optional.ofNullable(s.getLearningKeywords()).orElse(List.of()).stream())
                        .toList()))
                .telemetryAudit(gson.toJson(stepAnalyses))
                // Review: 2026-10-17, Pullfrog — was hardcoded "gpt-4o-mini";
                // read the configured value (ai.model.model-name) instead.
                .modelName(aiModelProperties.modelName())
                .build();

        AiExplanation saved;
        try {
            saved = explanationRepository.save(entity);
        } catch (DataIntegrityViolationException conflict) {
            // Review: 2026-10-17, Pullfrog — two concurrent POSTs for one
            // submission both pass the idempotency check above; the partial
            // unique index (V2) rejects the loser. Return the winner's cached
            // row instead of surfacing a 500.
            saved = explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS")
                    .orElseThrow(() -> conflict);
            return toResponse(saved, true);
        }

        AiExplanationResponse response = toResponse(saved, false);
        response.setStepAnalyses(stepAnalyses);
        return response;
    }

    private AiExplanationResponse toResponse(AiExplanation entity, boolean fromCache) {
        List<StepAnalysis> stepAnalyses = new ArrayList<>();
        if (entity.getTelemetryAudit() != null) {
            try {
                JsonArray arr = JsonParser.parseString(entity.getTelemetryAudit()).getAsJsonArray();
                for (var el : arr) {
                    stepAnalyses.add(gson.fromJson(el, StepAnalysis.class));
                }
            } catch (Exception ignored) {
            }
        }

        return AiExplanationResponse.builder()
                .id(entity.getId())
                .submissionId(entity.getSubmissionId())
                .category(entity.getCategory())
                .fromCache(fromCache)
                .overallSummary(entity.getOverallSummary() != null ? entity.getOverallSummary() : entity.getDiagnosis())
                .stepAnalyses(stepAnalyses)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
