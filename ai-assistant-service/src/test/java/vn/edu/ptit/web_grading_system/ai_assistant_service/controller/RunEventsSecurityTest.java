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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.SseBroadcaster;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.HeaderAuthenticationFilter;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RunController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=test-secret")
class RunEventsSecurityTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private FilterChainProxy securityFilterChain;
    @MockitoBean private RunRepository runRepository;
    @MockitoBean private SseBroadcaster sseBroadcaster;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();
    }

    @Test
    void initialRequestRequiresAuthenticationButAsyncCompletionKeepsItsSuccessStatus() throws Exception {
        UUID runId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/ai/runs/{id}/events", runId))
                .andExpect(status().isUnauthorized());

        SseEmitter emitter = new SseEmitter();
        when(sseBroadcaster.subscribe(runId)).thenReturn(emitter);
        MvcResult result = mockMvc.perform(get("/api/v1/ai/runs/{id}/events", runId)
                        .header("X-Gateway-Secret", "test-secret")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.send(SseEmitter.event().name("assistant.delta").data(" Xin chào"));
        emitter.complete();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());
    }
}
