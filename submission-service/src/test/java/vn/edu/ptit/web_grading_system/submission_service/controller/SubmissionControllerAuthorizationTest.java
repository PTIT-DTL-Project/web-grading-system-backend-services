package vn.edu.ptit.web_grading_system.submission_service.controller;

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
import vn.edu.ptit.web_grading_system.submission_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.submission_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.submission_service.service.HttpLogService;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization matrix for {@link SubmissionController} (Review: 2026-09-30, Pullfrog
 * review feat/DAT-8; rework: 2026-09-30 role-split slice, plan role-split-result-apis-v1.0).
 *
 * <p>Two properties are locked in here:
 * <ul>
 *   <li>the per-assignment list is NO LONGER public — it moved to
 *       {@code /api/v1/internal/submissions/assignment/{id}} and is served by course-service's
 *       owner-scoped {@code GET /api/v1/assignments/{id}/submissions} instead, because a
 *       role check alone let any lecturer list any class;</li>
 *   <li>reading one submission by id enforces ownership for EVERY role — the lecturer bypass
 *       is gone for the same reason.</li>
 * </ul>
 *
 * <p>An ownership miss answers 404 rather than 403 so an existing id stays indistinguishable
 * from a missing one. The trust-header setup and the chain-only MockMvc rationale are the
 * same as in the course-service {@code ClassAuthorizationTest}.
 */
@WebMvcTest(controllers = SubmissionController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + SubmissionControllerAuthorizationTest.SECRET)
class SubmissionControllerAuthorizationTest {

    static final String SECRET = "test-gateway-secret";

    private static final UUID CALLER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID OTHER_STUDENT = UUID.fromString("3e0b0d0b-0000-4000-8000-000000000006");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000007");
    private static final UUID SUBMISSION_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000008");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private SubmissionService submissionService;

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
                .header("X-User-Id", CALLER)
                .header("X-Gateway-Secret", SECRET);
        return roles != null ? stamped.header("X-User-Roles", roles) : stamped;
    }

    private void stubSubmissionOwnedBy(UUID studentId) {
        when(submissionService.getById(eq(SUBMISSION_ID)))
                .thenReturn(SubmissionResponse.builder()
                        .id(SUBMISSION_ID)
                        .assignmentId(ASSIGNMENT_ID)
                        .studentId(studentId)
                        .build());
    }

    // --- per-assignment list (moved off the public surface) -------------------

    @Test
    void thePublicPerAssignmentListRouteIsGoneForEveryRole() throws Exception {
        // Review: 2026-09-30, role-split slice — this used to be role-gated (200/403/403).
        // Role alone never scoped it to a class, so the route itself moved internal and only
        // course-service's owner-scoped facade reaches it now. 404 for a lecturer too: the
        // assertion fails if anyone re-adds a public route here without an owner check.
        mockMvc.perform(asCaller(
                        get("/api/v1/submissions/assignment/{assignmentId}", ASSIGNMENT_ID), "LECTURER"))
                .andExpect(status().isNotFound());

        mockMvc.perform(asCaller(
                        get("/api/v1/submissions/assignment/{assignmentId}", ASSIGNMENT_ID), "STUDENT"))
                .andExpect(status().isNotFound());
    }

    // --- read one submission (ownership) --------------------------------------

    @Test
    void studentMayReadTheirOwnSubmission() throws Exception {
        stubSubmissionOwnedBy(CALLER);

        mockMvc.perform(asCaller(get("/api/v1/submissions/{id}", SUBMISSION_ID), "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void studentMayNotReadAnotherStudentsSubmission() throws Exception {
        // Indistinguishable from a missing id — no 403 to confirm the id exists.
        stubSubmissionOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/submissions/{id}", SUBMISSION_ID), "STUDENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void callerWithNoRolesMayNotReadAnotherStudentsSubmission() throws Exception {
        stubSubmissionOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/submissions/{id}", SUBMISSION_ID), null))
                .andExpect(status().isNotFound());
    }

    @Test
    void lecturerMayNotReadAnotherStudentsSubmission() throws Exception {
        // Review: 2026-09-30, role-split slice — the old expectation here was 200. The
        // LECTURER bypass had no class-ownership check, so any lecturer could read any
        // submission. Lecturers now go through course-service's owner-scoped
        // GET /api/v1/assignments/{id}/submissions.
        stubSubmissionOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/submissions/{id}", SUBMISSION_ID), "LECTURER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutTrustSecretIsRejectedBeforeAnyOwnershipRule() throws Exception {
        mockMvc.perform(get("/api/v1/submissions/{id}", SUBMISSION_ID).header("X-User-Id", CALLER))
                .andExpect(status().isUnauthorized());
    }
}
