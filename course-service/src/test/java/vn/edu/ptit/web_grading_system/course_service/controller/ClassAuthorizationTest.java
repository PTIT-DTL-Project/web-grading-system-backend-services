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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.ptit.web_grading_system.course_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.course_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateClassRequest;
import vn.edu.ptit.web_grading_system.course_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.course_service.service.ClassService;
import vn.edu.ptit.web_grading_system.course_service.service.HttpLogService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role matrix for {@link ClassController} — the slice 2 authorization contract added for
 * Review: 2026-09-30, Pullfrog review (feat/DAT-8).
 *
 * <p>The requests carry the real trust headers rather than {@code @WithMockUser}, so this
 * exercises the production chain end to end: {@link HeaderAuthenticationFilter} matches
 * {@code X-Gateway-Secret}, turns {@code X-User-Roles} into {@code ROLE_} authorities, and
 * {@code @PreAuthorize} then decides. Picking the {@code user()} post-processor instead
 * would test a wiring path no request in this system ever takes.
 *
 * <p>Roles arrive from the gateway only after its own allowlist has filtered them, which is
 * why a request simply missing {@code X-User-Roles} must land on 403 rather than 200.
 */
@WebMvcTest(controllers = ClassController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
// Not @Import: a plain @Import registers the record as an ordinary bean, whose constructor
// then gets autowired instead of bound from gateway.security.*. The enable form registers
// the binding post-processor the slice does not otherwise bring in.
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + ClassAuthorizationTest.SECRET)
class ClassAuthorizationTest {

    static final String SECRET = "test-gateway-secret";

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID CLASS_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000001");

    @Autowired
    private WebApplicationContext webApplicationContext;

    /**
     * The chain, not the auto-configured {@link MockMvc}. That auto-configuration adds every
     * {@code Filter} bean ahead of {@code springSecurityFilterChain}, which inverts the
     * container's order: the servlet container runs the chain first (order -100), so the
     * chain-external copy of {@link HeaderAuthenticationFilter} only ever sees a request the
     * inner copy already marked as filtered and passes it straight through. Mocking up the
     * chain alone therefore reproduces production instead of a wiring no request can hit.
     */
    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private ClassService classService;

    /**
     * {@code HttpLoggingFilter} is a {@code Filter}, so the {@code @WebMvcTest} slice picks
     * it up alongside the security filter; its repository-backed service is a {@code @Service}
     * and therefore outside the slice.
     */
    @MockitoBean
    private HttpLogService httpLogService;

    @BeforeEach
    void buildMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();
    }

    private static String validClassJson() {
        return """
                {"name":"OOP","semester":"2025-2026"}
                """;
    }

    // --- create ---------------------------------------------------------------

    @Test
    void lecturerMayCreateClass() throws Exception {
        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClassJson())
                        .header("X-User-Id", OWNER)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Roles", "LECTURER"))
                .andExpect(status().isCreated());

        verify(classService).create(eq(OWNER), any(CreateClassRequest.class));
    }

    @Test
    void studentMayNotCreateClass() throws Exception {
        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClassJson())
                        .header("X-User-Id", OWNER)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Roles", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithNoRolesAtAllMayNotCreateClass() throws Exception {
        // Fail closed: absent X-User-Roles must mean no role, never "unrestricted".
        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClassJson())
                        .header("X-User-Id", OWNER)
                        .header("X-Gateway-Secret", SECRET))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTrustSecretIsRejectedBeforeAnyRoleRule() throws Exception {
        mockMvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validClassJson())
                        .header("X-User-Id", OWNER)
                        .header("X-User-Roles", "LECTURER"))
                .andExpect(status().isUnauthorized());
    }

    // --- read the class list --------------------------------------------------

    @Test
    void lecturerMayListOwnClasses() throws Exception {
        mockMvc.perform(get("/api/v1/classes")
                        .header("X-User-Id", OWNER)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Roles", "LECTURER"))
                .andExpect(status().isOk());
    }

    @Test
    void studentMayNotListOwnClasses() throws Exception {
        mockMvc.perform(get("/api/v1/classes")
                        .header("X-User-Id", OWNER)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Roles", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    // --- class detail ---------------------------------------------------------

    @Test
    void classDetailStaysOwnerScopedAndCarriesNoRoleGate() throws Exception {
        // Deliberate carve-out: findByIdAndOwnerId already answers 404 for a stranger, and
        // an enrolled student still needs their own class's detail. Asserted so a later
        // "gate everything" sweep does not silently close it.
        when(classService.getById(eq(CLASS_ID), eq(OWNER))).thenReturn(null);

        mockMvc.perform(get("/api/v1/classes/{id}", CLASS_ID)
                        .header("X-User-Id", OWNER)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Roles", "STUDENT"))
                .andExpect(status().isOk());

        verify(classService).getById(CLASS_ID, OWNER);
    }
}
