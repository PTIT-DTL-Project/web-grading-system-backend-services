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
 * Ownership/role matrix for {@link ResultController} (Review: 2026-09-30, Pullfrog review
 * feat/DAT-8).
 *
 * <p>The lecturer case is the point of this class: since the gateway started injecting
 * {@code X-User-Id} on every request, this endpoint's unconditional ownership check turned
 * every lecturer read of a student's result into 403. A lecturer now bypasses it, everyone
 * else still only sees their own rows. The trust-header setup and the chain-only MockMvc
 * rationale are the same as in the course-service {@code ClassAuthorizationTest}.
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
    void lecturerMayReadAnyStudentsResults() throws Exception {
        // The regression this slice fixes: without the bypass a lecturer gets 403 here,
        // because the gateway now sends their own X-User-Id and it never matches the row.
        stubResultsOwnedBy(OTHER_STUDENT);

        mockMvc.perform(asCaller(get("/api/v1/results/{submissionId}", SUBMISSION_ID), "LECTURER"))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithoutTrustSecretIsRejectedBeforeAnyOwnershipRule() throws Exception {
        stubResultsOwnedBy(CALLER);

        mockMvc.perform(get("/api/v1/results/{submissionId}", SUBMISSION_ID)
                        .header("X-User-Id", CALLER))
                .andExpect(status().isUnauthorized());
    }
}
