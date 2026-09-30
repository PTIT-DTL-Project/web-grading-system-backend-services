package vn.edu.ptit.web_grading_system.course_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.ptit.web_grading_system.course_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.course_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.course_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.course_service.service.HttpLogService;
import vn.edu.ptit.web_grading_system.course_service.service.ScoreService;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for {@link ScoreController} (Review: 2026-09-30, Pullfrog review feat/DAT-8).
 * Every endpoint on this controller grades coursework, so all of them are lecturer-only.
 * Trust-header setup and the chain-only MockMvc rationale are spelled out in
 * {@link ClassAuthorizationTest}.
 */
@WebMvcTest(controllers = ScoreController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + ScoreControllerAuthorizationTest.SECRET)
class ScoreControllerAuthorizationTest {

    static final String SECRET = "test-gateway-secret";

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID CLASS_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000001");
    private static final String STUDENT_CODE = "SV0001";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private ScoreService scoreService;

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

    // --- read ------------------------------------------------------------------

    @Test
    void lecturerMayReadScoreComponents() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/classes/{id}/score-components", CLASS_ID), "LECTURER"))
                .andExpect(status().isOk());
    }

    @Test
    void studentMayNotReadScoreComponents() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/classes/{id}/score-components", CLASS_ID), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void callerWithNoRolesMayNotReadScoreComponents() throws Exception {
        // Fail closed: absent X-User-Roles must mean no role, never "unrestricted".
        mockMvc.perform(asCaller(get("/api/v1/classes/{id}/score-components", CLASS_ID), null))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentMayNotReadASingleTranscript() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/classes/{id}/transcript", CLASS_ID), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentMayNotReadASingleStudentsScores() throws Exception {
        mockMvc.perform(asCaller(
                        get("/api/v1/classes/{id}/students/{studentCode}/scores", CLASS_ID, STUDENT_CODE), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    // --- write -----------------------------------------------------------------

    @Test
    void studentMayNotReplaceScoreComponents() throws Exception {
        mockMvc.perform(asCaller(put("/api/v1/classes/{id}/score-components", CLASS_ID), "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"type\":\"ATTENDANCE\",\"weight\":0.2}]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerMayReplaceScoreComponents() throws Exception {
        mockMvc.perform(asCaller(put("/api/v1/classes/{id}/score-components", CLASS_ID), "LECTURER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"type\":\"ATTENDANCE\",\"weight\":0.2}]"))
                .andExpect(status().isOk());
    }
}
