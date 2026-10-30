package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RetryableException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.entity.AiExplanation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.AiExplanationRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiExplanationServiceTest {

    @Mock
    private AiExplanationRepository explanationRepository;

    @Mock
    private ResultServiceClient resultServiceClient;

    @Mock
    private CourseServiceClient courseServiceClient;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private LlmClient llmClient;

    @Mock
    private GatewayTrustProperties gatewayProperties;

    @Mock
    private AiModelProperties aiModelProperties;

    @InjectMocks
    private AiExplanationService explanationService;

    private UUID submissionId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        submissionId = UUID.randomUUID();
        studentId = UUID.randomUUID();
    }

    @Test
    void getExplanation_returnsFromCache_whenFound() {
        AiExplanation cached = AiExplanation.builder()
                .id(UUID.randomUUID())
                .submissionId(submissionId)
                .studentId(studentId)
                .category("FULL_ANALYSIS")
                .diagnosis("Cached diagnosis")
                .overallSummary("Summary from cache")
                .telemetryAudit("[]")
                .build();

        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.of(cached));

        Optional<AiExplanationResponse> res = explanationService.getExplanation(submissionId, studentId);
        assertThat(res).isPresent();
        assertThat(res.get().getFromCache()).isTrue();
        assertThat(res.get().getOverallSummary()).isEqualTo("Summary from cache");
    }

    @Test
    void explainSubmission_returnsCached_withoutCallingLlm_whenAlreadyAnalyzed() {
        AiExplanation cached = AiExplanation.builder()
                .id(UUID.randomUUID())
                .submissionId(submissionId)
                .studentId(studentId)
                .category("FULL_ANALYSIS")
                .diagnosis("Cached diagnosis")
                .overallSummary("Summary from cache")
                .telemetryAudit("[]")
                .build();

        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.of(cached));

        AiExplanationResponse res = explanationService.explainSubmission(submissionId, studentId);
        assertThat(res.getFromCache()).isTrue();
        verifyNoInteractions(llmClient);
    }

    @Test
    void explainSubmission_throwsForbidden_whenNotOwner() {
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.empty());

        UUID otherStudentId = UUID.randomUUID();
        ResultDto result = ResultDto.builder()
                .id(UUID.randomUUID())
                .submissionId(submissionId)
                .studentId(otherStudentId)
                .assignmentId(UUID.randomUUID())
                .build();

        when(resultServiceClient.getBySubmissionId(any(), any(), any()))
                .thenReturn(ApiEnvelope.<List<ResultDto>>builder().status(200).data(List.of(result)).build());

        assertThatThrownBy(() -> explanationService.explainSubmission(submissionId, studentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Forbidden");

        verifyNoInteractions(llmClient);
    }

    // Review: 2026-10-17, Pullfrog — regression for the 500 when the LLM omits
    // actionableHints/learningKeywords (Stream.flatMap(null) NPE).
    @Test
    void explainSubmission_doesNotThrow_whenStepHintsAreNull() {
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.empty());

        ResultDto result = ResultDto.builder()
                .id(UUID.randomUUID())
                .submissionId(submissionId)
                .studentId(studentId)
                .assignmentId(UUID.randomUUID())
                .build();
        when(resultServiceClient.getBySubmissionId(any(), any(), any()))
                .thenReturn(ApiEnvelope.<List<ResultDto>>builder().status(200).data(List.of(result)).build());
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any(), any())).thenReturn("user");
        when(llmClient.generateCompletion("system", "user")).thenReturn(
                "{\"overallSummary\":\"ok\",\"stepAnalyses\":[{\"stepId\":\""
                        + UUID.randomUUID()
                        + "\",\"stepName\":\"s\",\"actionableHints\":null,\"learningKeywords\":null}]}");
        when(explanationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiExplanationResponse res = explanationService.explainSubmission(submissionId, studentId);

        assertThat(res.getFromCache()).isFalse();
        assertThat(res.getOverallSummary()).isEqualTo("ok");
        assertThat(res.getStepAnalyses()).hasSize(1);
    }

    // Regression: LLM returns truncated/invalid JSON (UnexpectedEndOfInputException).
    // generateEntity returns null instead of throwing; explainSubmission falls back to a
    // default summary and still returns a 200 response.
    @Test
    void explainSubmission_doesNotThrow_whenLlmFailsCompletely() {
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.empty());

        ResultDto result = ResultDto.builder()
                .id(UUID.randomUUID())
                .submissionId(submissionId)
                .studentId(studentId)
                .assignmentId(UUID.randomUUID())
                .build();
        when(resultServiceClient.getBySubmissionId(any(), any(), any()))
                .thenReturn(ApiEnvelope.<List<ResultDto>>builder().status(200).data(List.of(result)).build());
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any(), any())).thenReturn("user");
        when(llmClient.generateEntity(any(), any(), any())).thenReturn(null);
        when(llmClient.generateCompletion(any(), any())).thenReturn("{ truncated json ");
        when(explanationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiExplanationResponse res = explanationService.explainSubmission(submissionId, studentId);

        assertThat(res.getFromCache()).isFalse();
        assertThat(res.getOverallSummary()).isEqualTo("Bài nộp đã được phân tích toàn diện.");
        assertThat(res.getStepAnalyses()).isEmpty();
    }

    // 2026-10-17 — regression: a result-service read timeout (RetryableException)
    // escaped unhandled and surfaced as a raw 500. It must map to 502 Bad Gateway.
    @Test
    void explainSubmission_returnsBadGateway_whenResultServiceTimesOut() {
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.empty());
        Request request = Request.create(HttpMethod.GET,
                "http://grading-result-service:8084/api/v1/results/" + submissionId,
                Map.of(), (byte[]) null, null, null);
        when(resultServiceClient.getBySubmissionId(any(), any(), any()))
                .thenThrow(new RetryableException(0, "Read timed out", HttpMethod.GET, 0L, request));

        assertThatThrownBy(() -> explanationService.explainSubmission(submissionId, studentId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        verifyNoInteractions(llmClient);
    }

    // --- lecturer audience ------------------------------------------------------

    // The ordering contract: an assignment the caller does not own never reaches result-service,
    // so a foreign submission id cannot be probed through this route.
    @Test
    void getExplanationForLecturer_throws404_andReadsNoResults_whenAssignmentNotOwned() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        when(courseServiceClient.getOwnedAssignment(eq(assignmentId), eq(lecturerId.toString()), eq("ROLE_LECTURER"), any()))
                .thenThrow(courseServiceNotFound("GET"));

        assertThatThrownBy(() -> explanationService.getExplanationForLecturer(assignmentId, submissionId, lecturerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(resultServiceClient);
    }

    // Regression for a real bug in the first cut: the ownership call omitted X-User-Roles, and
    // course-service's role-gated endpoint answers 403 without it — every lecturer request would
    // have surfaced as a 502.
    @Test
    void authorizeLecturer_passesTheRoleHeader_courseServiceRequires() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(gatewayProperties.secret()).thenReturn("shared-gateway-secret");
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenReturn(List.of(resultFor(assignmentId, ownerId)));
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.empty());

        explanationService.getExplanationForLecturer(assignmentId, submissionId, lecturerId);

        verify(courseServiceClient)
                .getOwnedAssignment(assignmentId, lecturerId.toString(), "ROLE_LECTURER", "shared-gateway-secret");
    }

    // The id-pair guard: the lecturer's own assignmentId combined with a submission from another
    // class must not resolve to that submission.
    @Test
    void getExplanationForLecturer_throws404_whenSubmissionBelongsToAnotherAssignment() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenReturn(List.of(resultFor(UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> explanationService.getExplanationForLecturer(assignmentId, submissionId, lecturerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(explanationRepository, never()).findBySubmissionIdAndCategory(any(), any());
    }

    @Test
    void getExplanationForLecturer_throws404_whenSubmissionHasNoResults() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId)).thenReturn(List.of());

        assertThatThrownBy(() -> explanationService.getExplanationForLecturer(assignmentId, submissionId, lecturerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // 2026-10-17, Pullfrog PR #48 — regression: the lecturer path's internal result read was the
    // only result-service call without a FeignException guard, so a read timeout escaped as a raw
    // 500 instead of the retryable 502 the student path answers.
    @Test
    void explainSubmissionForLecturer_returnsBadGateway_whenResultServiceTimesOut() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        Request request = Request.create(HttpMethod.GET,
                "http://grading-result-service:8084/api/v1/internal/results/submission/" + submissionId,
                Map.of(), (byte[]) null, null, null);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenThrow(new RetryableException(0, "Read timed out", HttpMethod.GET, 0L, request));

        assertThatThrownBy(() -> explanationService.explainSubmissionForLecturer(assignmentId, submissionId, lecturerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        verifyNoInteractions(llmClient);
    }

    @Test
    void getExplanationForLecturer_returnsCachedRow_ofTheResolvedOwner() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenReturn(List.of(resultFor(assignmentId, ownerId)));
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.of(AiExplanation.builder()
                        .id(UUID.randomUUID())
                        .submissionId(submissionId)
                        .studentId(ownerId)
                        .category("FULL_ANALYSIS")
                        .overallSummary("Student's summary")
                        .telemetryAudit("[]")
                        .build()));

        Optional<AiExplanationResponse> res =
                explanationService.getExplanationForLecturer(assignmentId, submissionId, lecturerId);

        assertThat(res).isPresent();
        assertThat(res.get().getFromCache()).isTrue();
        verifyNoInteractions(llmClient);
    }

    // A cached row owned by anyone other than the submission's true student is not this
    // submission's row; answer 404 rather than serve another student's analysis.
    @Test
    void getExplanationForLecturer_throws404_whenCachedRowOwnerMismatches() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenReturn(List.of(resultFor(assignmentId, ownerId)));
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.of(AiExplanation.builder()
                        .id(UUID.randomUUID())
                        .submissionId(submissionId)
                        .studentId(UUID.randomUUID())
                        .category("FULL_ANALYSIS")
                        .telemetryAudit("[]")
                        .build()));

        assertThatThrownBy(() -> explanationService.getExplanationForLecturer(assignmentId, submissionId, lecturerId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void explainSubmissionForLecturer_returnsCached_withoutCallingLlm() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenReturn(List.of(resultFor(assignmentId, ownerId)));
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.of(AiExplanation.builder()
                        .id(UUID.randomUUID())
                        .submissionId(submissionId)
                        .studentId(ownerId)
                        .category("FULL_ANALYSIS")
                        .overallSummary("Already analyzed")
                        .telemetryAudit("[]")
                        .build()));

        AiExplanationResponse res =
                explanationService.explainSubmissionForLecturer(assignmentId, submissionId, lecturerId);

        assertThat(res.getFromCache()).isTrue();
        verifyNoInteractions(llmClient);
    }

    // The row is written under the student's id, never the lecturer's, so the generate warms the
    // cache the student reads next.
    @Test
    void explainSubmissionForLecturer_generates_andCachesUnderTheStudentId() {
        UUID assignmentId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedAssignment(assignmentId, lecturerId);
        when(resultServiceClient.getBySubmissionIdInternal(submissionId))
                .thenReturn(List.of(resultFor(assignmentId, ownerId)));
        when(explanationRepository.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS"))
                .thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserPrompt(any(), any())).thenReturn("user");
        when(llmClient.generateCompletion("system", "user"))
                .thenReturn("{\"overallSummary\":\"ok\",\"stepAnalyses\":[]}");
        when(explanationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AiExplanationResponse res =
                explanationService.explainSubmissionForLecturer(assignmentId, submissionId, lecturerId);

        assertThat(res.getFromCache()).isFalse();
        assertThat(res.getOverallSummary()).isEqualTo("ok");
        ArgumentCaptor<AiExplanation> saved = ArgumentCaptor.forClass(AiExplanation.class);
        verify(explanationRepository).save(saved.capture());
        assertThat(saved.getValue().getSubmissionId()).isEqualTo(submissionId);
        assertThat(saved.getValue().getStudentId()).isEqualTo(ownerId);
    }

    private void stubOwnedAssignment(UUID assignmentId, UUID lecturerId) {
        when(courseServiceClient.getOwnedAssignment(eq(assignmentId), eq(lecturerId.toString()), eq("ROLE_LECTURER"), any()))
                .thenReturn(ApiEnvelope.<AssignmentDetailDto>builder()
                        .status(200)
                        .data(AssignmentDetailDto.builder().id(assignmentId).title("BT").build())
                        .build());
    }

    private ResultDto resultFor(UUID assignmentId, UUID ownerId) {
        return ResultDto.builder()
                .id(UUID.randomUUID())
                .submissionId(submissionId)
                .assignmentId(assignmentId)
                .studentId(ownerId)
                .build();
    }

    private static FeignException courseServiceNotFound(String method) {
        Request request = Request.create(HttpMethod.GET,
                "http://grading-course-service:8081/api/v1/assignments/x",
                Map.of(), (byte[]) null, null, null);
        return FeignException.errorStatus(method,
                feign.Response.builder().status(404).reason("Not Found").request(request).build());
    }
}
