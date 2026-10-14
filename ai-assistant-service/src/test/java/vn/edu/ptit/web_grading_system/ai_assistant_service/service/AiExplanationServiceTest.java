package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.entity.AiExplanation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.AiExplanationRepository;

import java.util.List;
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
                .thenReturn(List.of(result));

        assertThatThrownBy(() -> explanationService.explainSubmission(submissionId, studentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Forbidden");

        verifyNoInteractions(llmClient);
    }
}
