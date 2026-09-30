package vn.edu.ptit.web_grading_system.result_service.controller;

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
import vn.edu.ptit.web_grading_system.result_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.result_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.result_service.service.HttpLogService;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ownership matrix for {@link ResultController} (Review: 2026-09-30, Pullfrog review
 * feat/DAT-8; rework: 2026-09-30 role-split slice, plan role-split-result-apis-v1.0 D3).
 *
 * <p>This route is the STUDENT's own-results endpoint and answers only for its owner. The
 * {@code hasRole("LECTURER")} bypass that used to live here is deliberately gone: it skipped
 * ownership for every lecturer in the system without checking that the assignment belonged
 * to a class they own. A lecturer reading a class's results now goes through
 * {@code GET /api/v1/assignments/{id}/results} in course-service, which is role-gated AND
 * owner-scoped — see course-service {@code AssignmentGradingAuthorizationTest}.
 *
 * <p>The trust-header setup and the chain-only MockMvc rationale are the same as in the
 * course-service {@code ClassAuthorizationTest}.
 */
@WebMvcTest(controllers = ResultController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + ResultControllerAuthorizationTest.SECRET)
class ResultControllerAuthorizationTest {

    static final String SECRET = "test-gateway-secret";

    private static final UUID CALLER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID OTHER_STUDENT = UUID.fromString("3e0b0d0b-0000-4000-8000-000000000006");
    private static final UUID SUBMISSION_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000009");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private ResultService resultService;

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

    private void stubResultsOwnedBy(UUID studentId) {
        when(resultService.getBySubmissionId(eq(SUBMISSION_ID)))
                .thenReturn(List.of(ResultResponse.builder()
                        .submissionId(SUBMISSION_ID)
                        .studentId(studentId)
                        .score(new BigDecimal("8.5"))
                        .build()));
    }

    @Test
    void ownerMayReadTheirOwnResults() throws Exception {
        stubResultsOwnedBy(CALLER);

        mockMvc.perform(asCaller(get("/api/v1/results/{submissionId}", SUBMISSION_ID), "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void studentMayNotReadAnotherStudentsResults() throws Exception {
        stubResultsOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/results/{submissionId}", SUBMISSION_ID), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void callerWithNoRolesMayNotReadAnotherStudentsResults() throws Exception {
        // Fail closed: a request carrying no roles inherits the ownership rule, not a bypass.
        stubResultsOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/results/{submissionId}", SUBMISSION_ID), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerMayNotReadOtherStudentsResultsThroughTheStudentEndpoint() throws Exception {
        // Review: 2026-09-30, role-split slice — the old expectation here was 200. The
        // LECTURER bypass it asserted had no class-ownership check, so any lecturer could
        // read any student's results. The role now buys nothing on this route: the lecturer
        // grading view moved to course-service's owner-scoped endpoint.
        stubResultsOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/results/{submissionId}", SUBMISSION_ID), "LECTURER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTrustSecretIsRejectedBeforeAnyOwnershipRule() throws Exception {
        stubResultsOwnedBy(CALLER);

        mockMvc.perform(get("/api/v1/results/{submissionId}", SUBMISSION_ID)
                        .header("X-User-Id", CALLER))
                .andExpect(status().isUnauthorized());
    }
}
