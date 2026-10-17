package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiExplanationService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiExplanationController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + AiExplanationControllerTest.SECRET)
public class AiExplanationControllerTest {

    static final String SECRET = "test-secret";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private AiExplanationService explanationService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();
    }

    @Test
    void getExplanation_returns200_whenCached() throws Exception {
        UUID subId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiExplanationResponse response = AiExplanationResponse.builder()
                .id(UUID.randomUUID())
                .submissionId(subId)
                .category("FULL_ANALYSIS")
                .fromCache(true)
                .overallSummary("Test summary")
                .stepAnalyses(List.of())
                .build();

        when(explanationService.getExplanation(eq(subId), eq(userId))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/v1/ai/submissions/{submissionId}/explain", subId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.overallSummary").value("Test summary"));
    }

    @Test
    void getExplanation_returns404_whenNotCached() throws Exception {
        UUID subId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(explanationService.getExplanation(eq(subId), eq(userId))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/ai/submissions/{submissionId}/explain", subId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isNotFound());
    }

    @Test
    void explainSubmission_returns200_withResult() throws Exception {
        UUID subId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiExplanationResponse response = AiExplanationResponse.builder()
                .id(UUID.randomUUID())
                .submissionId(subId)
                .category("FULL_ANALYSIS")
                .fromCache(false)
                .overallSummary("Analysis complete")
                .stepAnalyses(List.of())
                .build();

        when(explanationService.explainSubmission(eq(subId), eq(userId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/submissions/{submissionId}/explain", subId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.overallSummary").value("Analysis complete"));
    }

    // --- audience split: each route answers to exactly one role ---------------

    @Test
    void studentRoute_forbidsLecturer() throws Exception {
        mockMvc.perform(get("/api/v1/ai/submissions/{submissionId}/explain", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ROLE_LECTURER")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isForbidden());

        verifyNoInteractions(explanationService);
    }

    @Test
    void lecturerRoute_forbidsStudent() throws Exception {
        mockMvc.perform(get("/api/v1/ai/assignments/{assignmentId}/submissions/{submissionId}/explain",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isForbidden());

        verifyNoInteractions(explanationService);
    }

    // A request with no stampable role is unauthenticated; @PreAuthorize must fail closed.
    @Test
    void lecturerRoute_forbidsRequest_withoutRole() throws Exception {
        mockMvc.perform(get("/api/v1/ai/assignments/{assignmentId}/submissions/{submissionId}/explain",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isForbidden());

        verifyNoInteractions(explanationService);
    }

    @Test
    void getExplanationForLecturer_returns200_whenCached() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();

        when(explanationService.getExplanationForLecturer(eq(assignmentId), eq(subId), eq(lecturerId)))
                .thenReturn(Optional.of(AiExplanationResponse.builder()
                        .id(UUID.randomUUID())
                        .submissionId(subId)
                        .category("FULL_ANALYSIS")
                        .fromCache(true)
                        .overallSummary("Lecturer view")
                        .stepAnalyses(List.of())
                        .build()));

        mockMvc.perform(get("/api/v1/ai/assignments/{assignmentId}/submissions/{submissionId}/explain",
                        assignmentId, subId)
                        .header("X-User-Id", lecturerId.toString())
                        .header("X-User-Roles", "ROLE_LECTURER")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.overallSummary").value("Lecturer view"));
    }

    @Test
    void getExplanationForLecturer_returns404_whenNotCached() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();

        when(explanationService.getExplanationForLecturer(eq(assignmentId), eq(subId), eq(lecturerId)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/ai/assignments/{assignmentId}/submissions/{submissionId}/explain",
                        assignmentId, subId)
                        .header("X-User-Id", lecturerId.toString())
                        .header("X-User-Roles", "ROLE_LECTURER")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isNotFound());
    }

    @Test
    void explainSubmissionForLecturer_returns200_withGeneratedResult() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();

        when(explanationService.explainSubmissionForLecturer(eq(assignmentId), eq(subId), eq(lecturerId)))
                .thenReturn(AiExplanationResponse.builder()
                        .id(UUID.randomUUID())
                        .submissionId(subId)
                        .category("FULL_ANALYSIS")
                        .fromCache(false)
                        .overallSummary("Generated for lecturer")
                        .stepAnalyses(List.of())
                        .build());

        mockMvc.perform(post("/api/v1/ai/assignments/{assignmentId}/submissions/{submissionId}/explain",
                        assignmentId, subId)
                        .header("X-User-Id", lecturerId.toString())
                        .header("X-User-Roles", "ROLE_LECTURER")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.overallSummary").value("Generated for lecturer"));
    }
}
