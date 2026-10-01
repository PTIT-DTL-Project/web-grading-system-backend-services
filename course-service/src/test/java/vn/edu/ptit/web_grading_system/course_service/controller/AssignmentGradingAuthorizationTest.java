package vn.edu.ptit.web_grading_system.course_service.controller;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.ptit.web_grading_system.course_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.course_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentResultResponse;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.course_service.service.AssignmentGradingService;
import vn.edu.ptit.web_grading_system.course_service.service.HttpLogService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for {@link AssignmentGradingController} (plan role-split-result-apis-v1.0).
 *
 * <p>These two endpoints are the ones that replaced the LECTURER bypasses in
 * result-service and submission-service, so the pairing of guards is what this class locks
 * down: {@code @PreAuthorize} answers 403 <em>before</em> the handler runs, and the service's
 * ownership check answers 404 <em>after</em> it decides this lecturer does not own the
 * assignment. A role alone must never be enough — that was the hole being closed.
 *
 * <p>Requests carry the real trust headers rather than {@code @WithMockUser}, so this runs
 * the production chain end to end. Chain-only MockMvc and the {@code @EnableConfigurationProperties}
 * rationale are documented on {@code ClassAuthorizationTest}.
 */
@WebMvcTest(controllers = AssignmentGradingController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + AssignmentGradingAuthorizationTest.SECRET)
class AssignmentGradingAuthorizationTest {

    static final String SECRET = "test-gateway-secret";

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID STUDENT = UUID.fromString("3e0b0d0b-0000-4000-8000-000000000006");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000007");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private AssignmentGradingService assignmentGradingService;

    @MockitoBean
    private HttpLogService httpLogService;

    @BeforeEach
    void buildMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();
    }

    /** Trust headers as the gateway would stamp them for a caller holding {@code roles}. */
    private static MockHttpServletRequestBuilder asCaller(MockHttpServletRequestBuilder request, String roles) {
        MockHttpServletRequestBuilder stamped = request
                .header("X-User-Id", OWNER)
                .header("X-Gateway-Secret", SECRET);
        return roles != null ? stamped.header("X-User-Roles", roles) : stamped;
    }

    /** Stands in for the service's ownership check failing on an assignment someone else owns. */
    private void ownerCheckFails() {
        when(assignmentGradingService.results(any(UUID.class), any(UUID.class), any(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Assignment not found: " + ASSIGNMENT_ID));
        when(assignmentGradingService.submissions(any(UUID.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Assignment not found: " + ASSIGNMENT_ID));
    }

    // --- GET /api/v1/assignments/{id}/results --------------------------------

    @Test
    void lecturerMayReadTheResultsOfAnAssignmentTheyOwn() throws Exception {
        when(assignmentGradingService.results(eq(ASSIGNMENT_ID), eq(OWNER), isNull(), eq(false)))
                .thenReturn(List.of(StudentResultResponse.builder()
                        .studentUserId(STUDENT)
                        .studentCode("SV0001")
                        .exerciseScore(new BigDecimal("8.50"))
                        .results(List.of())
                        .build()));

        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID), "LECTURER"))
                .andExpect(status().isOk());

        verify(assignmentGradingService).results(ASSIGNMENT_ID, OWNER, null, false);
    }

    @Test
    void lecturerOfAnotherClassGetsNotFoundOnResults() throws Exception {
        // 404, not 403: an assignment that exists but is not the caller's must stay
        // indistinguishable from one that does not exist at all.
        ownerCheckFails();

        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID), "LECTURER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentMayNotReadAssignmentResults() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID), "STUDENT"))
                .andExpect(status().isForbidden());

        // The gate runs before the handler — the service must not even be consulted.
        verify(assignmentGradingService, never())
                .results(any(UUID.class), any(UUID.class), any(), anyBoolean());
    }

    @Test
    void requestWithNoRolesAtAllMayNotReadAssignmentResults() throws Exception {
        // Fail closed: absent X-User-Roles must mean no role, never "unrestricted".
        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID), null))
                .andExpect(status().isForbidden());

        verify(assignmentGradingService, never())
                .results(any(UUID.class), any(UUID.class), any(), anyBoolean());
    }

    // --- GET /api/v1/assignments/{id}/submissions -----------------------------

    @Test
    void lecturerMayListTheSubmissionsOfAnAssignmentTheyOwn() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID), "LECTURER"))
                .andExpect(status().isOk());

        verify(assignmentGradingService).submissions(ASSIGNMENT_ID, OWNER);
    }

    @Test
    void lecturerOfAnotherClassGetsNotFoundOnSubmissions() throws Exception {
        ownerCheckFails();

        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID), "LECTURER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentMayNotListAssignmentSubmissions() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID), "STUDENT"))
                .andExpect(status().isForbidden());

        verify(assignmentGradingService, never()).submissions(any(UUID.class), any(UUID.class));
    }

    @Test
    void requestWithNoRolesAtAllMayNotListAssignmentSubmissions() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID), null))
                .andExpect(status().isForbidden());

        verify(assignmentGradingService, never()).submissions(any(UUID.class), any(UUID.class));
    }

    // --- trust boundary -------------------------------------------------------

    @Test
    void requestWithoutTrustSecretIsRejectedBeforeAnyRoleRule() throws Exception {
        mockMvc.perform(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID)
                        .header("X-User-Id", OWNER)
                        .header("X-User-Roles", "LECTURER"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID)
                        .header("X-User-Id", OWNER)
                        .header("X-User-Roles", "LECTURER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSecretWithAbsentUserIdRejectsBeforeHandlerRuns() throws Exception {
        mockMvc.perform(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID)
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID)
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSecretWithBlankUserIdRejectsBeforeHandlerRuns() throws Exception {
        mockMvc.perform(get("/api/v1/assignments/{id}/results", ASSIGNMENT_ID)
                        .header("X-User-Id", "   ")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/assignments/{id}/submissions", ASSIGNMENT_ID)
                        .header("X-User-Id", "")
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isUnauthorized());
    }
}
