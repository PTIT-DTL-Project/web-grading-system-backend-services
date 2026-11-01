package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.RunTimelineEvent;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.RunTimelineService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.SseBroadcaster;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.HeaderAuthenticationFilter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(controllers = RunController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class, SseBroadcaster.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=test-secret")
class RunEventsReplayTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filters;
    @MockitoBean RunRepository runs;
    @MockitoBean ConversationRepository conversations;
    @MockitoBean RunTimelineService timeline;

    @Test
    void replaysOnlyEventsAfterCursorInOrder() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(runs.findById(runId)).thenReturn(Optional.of(Run.builder().id(runId).conversationId(conversationId).build()));
        when(conversations.findByIdAndOwnerId(conversationId, owner)).thenReturn(Optional.of(
                Conversation.builder().id(conversationId).ownerId(owner).build()));
        ObjectMapper mapper = new ObjectMapper();
        when(timeline.listAfter(runId, 1)).thenReturn(List.of(
                new RunTimelineEvent(1, runId, 2, "reasoning.delta", 1, "reasoning-1-1", Instant.now(),
                        mapper.readTree("{\"text\":\"thinking\"}")),
                new RunTimelineEvent(1, runId, 3, "run.completed", null, null, Instant.now(),
                        mapper.readTree("{\"status\":\"SUCCEEDED\"}"))));
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
        var result = mvc.perform(get("/api/v1/ai/runs/{id}/events", runId)
                .param("afterSequence", "1").header("X-Gateway-Secret", "test-secret")
                .header("X-User-Id", owner.toString())).andReturn();
        String body = mvc.perform(asyncDispatch(result)).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("id:2"));
        assertTrue(body.contains("id:3"));
        assertTrue(body.indexOf("id:2") < body.indexOf("id:3"));
    }
}
