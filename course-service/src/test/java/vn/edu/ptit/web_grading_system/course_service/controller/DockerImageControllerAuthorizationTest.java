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
import vn.edu.ptit.web_grading_system.course_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.course_service.service.DockerImageService;
import vn.edu.ptit.web_grading_system.course_service.service.HttpLogService;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for {@link DockerImageController} (Review: 2026-09-30, Pullfrog review
 * feat/DAT-8): the runnable grading images are teacher-managed infrastructure, so a student
 * must not register, edit or remove them. Trust-header setup and the chain-only MockMvc
 * rationale are spelled out in {@link ClassAuthorizationTest}.
 */
@WebMvcTest(controllers = DockerImageController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + DockerImageControllerAuthorizationTest.SECRET)
class DockerImageControllerAuthorizationTest {

    static final String SECRET = "test-gateway-secret";

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID IMAGE_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000005");

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private DockerImageService dockerImageService;

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
    void lecturerMayListDockerImages() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/docker-images"), "LECTURER"))
                .andExpect(status().isOk());
    }

    @Test
    void studentMayNotListDockerImages() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/docker-images"), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentMayNotReadOneDockerImage() throws Exception {
        mockMvc.perform(asCaller(get("/api/v1/docker-images/{id}", IMAGE_ID), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void callerWithNoRolesMayNotListDockerImages() throws Exception {
        // Fail closed: absent X-User-Roles must mean no role, never "unrestricted".
        mockMvc.perform(asCaller(get("/api/v1/docker-images"), null))
                .andExpect(status().isForbidden());
    }

    // --- write -----------------------------------------------------------------

    @Test
    void studentMayNotDeleteADockerImage() throws Exception {
        mockMvc.perform(asCaller(delete("/api/v1/docker-images/{id}", IMAGE_ID), "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerMayDeleteADockerImage() throws Exception {
        mockMvc.perform(asCaller(delete("/api/v1/docker-images/{id}", IMAGE_ID), "LECTURER"))
                .andExpect(status().isOk());
    }
}
