package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

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
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.ConversationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + ChatControllerTest.SECRET)
public class ChatControllerTest {

    static final String SECRET = "test-secret";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();
    }

    @Test
    void createConversation_returns201() throws Exception {
        UUID convId = UUID.randomUUID();
        when(conversationService.createConversation(any(), any()))
                .thenReturn(new ConversationResponse(convId, UUID.fromString(USER_ID), "New Chat", null, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Chat\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(convId.toString()));
    }

    @Test
    void sendMessage_returns202Accepted() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        when(conversationService.sendMessage(eq(convId), any(), any()))
                .thenReturn(new SendMessageResponse(convId, runId, msgId));

        mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", convId)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Tìm bài tập cho tôi\",\"clientMessageId\":\"c1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(runId.toString()));
    }
}
