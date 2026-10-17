package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.RetryableException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
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
}
