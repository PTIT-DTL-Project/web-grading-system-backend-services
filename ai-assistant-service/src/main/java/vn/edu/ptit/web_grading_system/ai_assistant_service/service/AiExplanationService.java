package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.StepAnalysis;
import vn.edu.ptit.web_grading_system.ai_assistant_service.entity.AiExplanation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.AiExplanationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reads and generates one submission's AI explanation for its two audiences.
 *
 * <p>Each audience is authorized here, not by the controller's role gate alone: a student must
 * be the submission's owner, a lecturer must own the class of the submission's assignment. The
 * lecturer check runs <em>before</em> any grading row is read, and it answers 404 — an id the
 * caller may not see must stay indistinguishable from one that does not exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExplanationService {

    private static final String CATEGORY = "FULL_ANALYSIS";

    /** The authority this service asserts downstream, and only after its own request already
     *  passed {@code @PreAuthorize("hasRole('LECTURER')")}. course-service's
     *  {@code GET /api/v1/assignments/{id}} is role-gated, so omitting the header there
     *  answers 403 and this service would report it as a 502. */
    private static final String LECTURER_AUTHORITY = "ROLE_LECTURER";

    private final AiExplanationRepository explanationRepository;
    private final ResultServiceClient resultServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final GatewayTrustProperties gatewayProperties;
    private final AiModelProperties aiModelProperties;
    private final Gson gson = new Gson();

    // --- student audience -------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<AiExplanationResponse> getExplanation(UUID submissionId, UUID studentId) {
        return explanationRepository.findBySubmissionIdAndCategory(submissionId, CATEGORY)
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
        Optional<AiExplanation> cached = explanationRepository.findBySubmissionIdAndCategory(submissionId, CATEGORY);
        if (cached.isPresent()) {
            AiExplanation entity = cached.get();
            if (!studentId.equals(entity.getStudentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: Not your submission");
            }
            return toResponse(entity, true);
        }

        // 2. Fetch submission results, then verify the caller owns them
        List<ResultDto> results = fetchResultsAsStudent(submissionId, studentId);
        ResultDto result = results.get(0);
        // Ownership verification
        if (!studentId.equals(result.getStudentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: You are not the owner of this submission");
        }

        // 3. Assignment metadata, then the LLM
        return generate(submissionId, studentId,
                studentAssignmentMetadata(result.getAssignmentId(), studentId), results);
    }

    // --- lecturer audience ------------------------------------------------------

    /**
     * The cached explanation of a submission inside one of the caller's own assignments.
     *
     * <p>Not {@code @Transactional}: authorizing the caller is two HTTP round trips, and holding
     * a pooled connection across them is exactly what the student path avoids.
     */
    public Optional<AiExplanationResponse> getExplanationForLecturer(
            UUID assignmentId, UUID submissionId, UUID lecturerId) {
        LecturerTarget target = authorizeLecturer(assignmentId, submissionId, lecturerId);
        return cachedExplanationFor(submissionId, target.studentId());
    }

    /**
     * Generates the explanation for a submission inside one of the caller's own assignments, or
     * returns the cached row. The row is written under the student's id, so a lecturer who
     * generates it warms the cache the student reads next.
     */
    public AiExplanationResponse explainSubmissionForLecturer(
            UUID assignmentId, UUID submissionId, UUID lecturerId) {
        LecturerTarget target = authorizeLecturer(assignmentId, submissionId, lecturerId);

        Optional<AiExplanationResponse> cached = cachedExplanationFor(submissionId, target.studentId());
        if (cached.isPresent()) {
            return cached.get();
        }

        return generate(submissionId, target.studentId(), target.assignment(), target.results());
    }

    /**
     * The cached row of a submission the caller is already authorized to see.
     *
     * <p>The owner comparison is defensive: cache rows are keyed by submission id, so a row whose
     * {@code studentId} is not this submission's owner is not this submission's row — answer
     * {@code 404} rather than serve another student's analysis.
     */
    private Optional<AiExplanationResponse> cachedExplanationFor(UUID submissionId, UUID ownerId) {
        return explanationRepository.findBySubmissionIdAndCategory(submissionId, CATEGORY)
                .map(entity -> {
                    if (!ownerId.equals(entity.getStudentId())) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "No AI explanation for submission: " + submissionId);
                    }
                    return toResponse(entity, true);
                });
    }

    /** The resolved student id, the assignment (already fetched during the ownership check) and
     *  the grading rows the prompt is built from. */
    private record LecturerTarget(UUID studentId, AssignmentDetailDto assignment, List<ResultDto> results) {
    }

    /**
     * Proves the lecturer may see this assignment's submissions, and returns what the caller
     * needs to answer.
     *
     * <p>Ordering is the point: ownership is confirmed before a single grading row is read, so an
     * unowned assignment id never reaches result-service. The ownership call doubles as the
     * assignment-metadata read, so authorizing costs nothing extra.
     */
    private LecturerTarget authorizeLecturer(UUID assignmentId, UUID submissionId, UUID lecturerId) {
        AssignmentDetailDto assignment;
        try {
            // Called with the lecturer's own id — the one the gateway stamped on this request.
            // The endpoint is @PreAuthorize("hasRole('LECTURER')") + owner-scoped, so a 404 here
            // IS the negative authorization answer, and LECTURER_AUTHORITY is what lets the call
            // through at all.
            ApiEnvelope<AssignmentDetailDto> envelope = courseServiceClient.getOwnedAssignment(
                    assignmentId, lecturerId.toString(), LECTURER_AUTHORITY, gatewayProperties.secret());
            assignment = envelope != null ? envelope.getData() : null;
        } catch (FeignException.NotFound e) {
            // 404, never 403: an assignment outside the caller's classes must stay
            // indistinguishable from one that does not exist.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found: " + assignmentId);
        } catch (FeignException e) {
            // Ownership cannot be established, so the request fails closed — but as a 502, not a
            // generic 500: the dependency being down is retryable and is not the caller's error.
            log.warn("course-service unavailable while checking assignment {}: {}", assignmentId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Course service unavailable");
        }

        // The internal route is identity-free by design: reaching it already means the check above
        // passed, and these rows are what reveal the submission's owner.
        List<ResultDto> results = readGradingResults(submissionId,
                () -> resultServiceClient.getBySubmissionIdInternal(submissionId));
        ResultDto first = results.get(0);
        if (!assignmentId.equals(first.getAssignmentId())) {
            // Guards the id pair: the caller's own assignmentId combined with a submission id from
            // another class must never resolve to that submission.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found in this assignment");
        }
        return new LecturerTarget(first.getStudentId(), assignment, results);
    }

    // --- shared internals -------------------------------------------------------

    /**
     * The submission's grading rows, read as the student so result-service re-checks ownership
     * independently of this service's own checks.
     *
     * <p>Only the enrolment-specific parts live here: the student's identity and the envelope this
     * public route answers with. The fault mapping and the empty check are shared.
     */
    private List<ResultDto> fetchResultsAsStudent(UUID submissionId, UUID studentId) {
        // Review: 2026-10-17, Pullfrog follow-up — result-service wraps its body
        // in ApiResponse{status,message,data}; bind the envelope and take .data.
        return readGradingResults(submissionId, () -> {
            ApiEnvelope<List<ResultDto>> resultsEnvelope = resultServiceClient.getBySubmissionId(
                    submissionId, studentId.toString(), gatewayProperties.secret());
            return resultsEnvelope != null ? resultsEnvelope.getData() : null;
        });
    }

    /**
     * The submission's grading rows, read through {@code call}.
     *
     * <p>Every result-service read goes through here so both audiences fail identically: a
     * FeignException (timeout or outage) becomes a retryable 502, and an empty row set becomes a
     * 404. The two reads had already drifted apart once — the lecturer path lacked the 502 mapping
     * and answered a raw 500 — so the guard lives in one place instead of in each caller.
     */
    private List<ResultDto> readGradingResults(UUID submissionId, Supplier<List<ResultDto>> call) {
        List<ResultDto> results;
        try {
            results = call.get();
        } catch (FeignException e) {
            // Review: 2026-10-17, Pullfrog PR #48 — the lecturer path's internal read was the only
            // result-service call without this guard, so a read timeout / outage surfaced as a raw
            // 500. The upstream dependency being unavailable is not the caller's error: answer 502
            // so the client can retry.
            log.warn("result-service unavailable for submission {}: {}", submissionId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Grading result service unavailable");
        }
        if (results == null || results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No grading results found for submission");
        }
        return results;
    }

    /** Assignment title/description for the prompt, or null when it cannot be read: the metadata
     *  is context, not authorization, so a failure only degrades the prompt. */
    private AssignmentDetailDto studentAssignmentMetadata(UUID assignmentId, UUID studentId) {
        try {
            // Review: 2026-10-17, Pullfrog follow-up — unwrap the ApiResponse
            // envelope; the previous raw binding silently failed and left
            // `assignment` null on every request.
            ApiEnvelope<AssignmentDetailDto> assignmentEnvelope = courseServiceClient.getAssignment(
                    assignmentId, studentId.toString(), gatewayProperties.secret());
            return assignmentEnvelope != null ? assignmentEnvelope.getData() : null;
        } catch (Exception e) {
            log.warn("Could not fetch assignment details for id {}: {}", assignmentId, e.getMessage());
            return null;
        }
    }

    /**
     * The expensive half: build the prompt, call the LLM, parse the answer and cache it under
     * {@code studentId}. Shared by both audiences so they cannot drift apart.
     */
    private AiExplanationResponse generate(UUID submissionId, UUID studentId,
            AssignmentDetailDto assignment, List<ResultDto> results) {
        ResultDto result = results.get(0);

        // 4. Build prompt & call LLM
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(assignment, result);
        AiExplanationResultDto structuredOutput = llmClient.generateEntity(systemPrompt, userPrompt, AiExplanationResultDto.class);
        if (structuredOutput == null) {
            String rawLlmResponse = llmClient.generateCompletion(systemPrompt, userPrompt);
            if (rawLlmResponse != null && !rawLlmResponse.isBlank()) {
                try {
                    JsonObject json = JsonParser.parseString(rawLlmResponse).getAsJsonObject();
                    String summary = json.has("overallSummary") ? json.get("overallSummary").getAsString() : null;
                    List<StepAnalysis> steps = new ArrayList<>();
                    if (json.has("stepAnalyses") && json.get("stepAnalyses").isJsonArray()) {
                        for (var elem : json.getAsJsonArray("stepAnalyses")) {
                            steps.add(gson.fromJson(elem, StepAnalysis.class));
                        }
                    }
                    structuredOutput = new AiExplanationResultDto(summary, steps);
                } catch (Exception ignored) {
                }
            }
        }

        // 5. Extract structured output
        String overallSummary = (structuredOutput != null && structuredOutput.overallSummary() != null)
                ? structuredOutput.overallSummary()
                : "Bài nộp đã được phân tích toàn diện.";
        String diagnosis = overallSummary;
        List<StepAnalysis> stepAnalyses = (structuredOutput != null && structuredOutput.stepAnalyses() != null)
                ? structuredOutput.stepAnalyses()
                : new ArrayList<>();

        // 6. Save in DB cache
        AiExplanation entity = AiExplanation.builder()
                .submissionId(submissionId)
                .studentId(studentId)
                .category(CATEGORY)
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
            saved = explanationRepository.findBySubmissionIdAndCategory(submissionId, CATEGORY)
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
