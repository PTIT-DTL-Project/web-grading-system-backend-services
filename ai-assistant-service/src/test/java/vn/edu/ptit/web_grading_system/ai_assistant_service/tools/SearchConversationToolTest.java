package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchConversationToolTest {
    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    @Test
    void findsOldDecisionOnlyInOwnedConversationAndCurrentRoleScope() throws Exception {
        UUID userId = UUID.randomUUID(), conversationId = UUID.randomUUID();
        ConversationRepository conversations = mock(ConversationRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        when(conversations.findByIdAndOwnerId(conversationId, userId))
                .thenReturn(Optional.of(Conversation.builder().id(conversationId).ownerId(userId).build()));
        Message decision = Message.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .role("user").sequenceNo(2).content("Đã chốt hạn nộp là thứ Sáu").build();
        when(messages.searchScoped(eq(conversationId), eq("ROLE_STUDENT"), eq("hạn nộp"), eq(Integer.MAX_VALUE), any()))
                .thenReturn(new PageImpl<>(List.of(decision)));
        AgentContext context = new AgentContext(userId, "", Set.of("ROLE_STUDENT"), conversationId, UUID.randomUUID(), "");
        String output = new SearchConversationTool(conversations, messages, mapper).callback(context)
                .call("{\"query\":\"hạn nộp\"}");
        assertEquals("Đã chốt hạn nộp là thứ Sáu", mapper.readTree(output).get("matches").get(0).get("excerpt").asText());
        verify(messages).searchScoped(eq(conversationId), eq("ROLE_STUDENT"), eq("hạn nộp"), eq(Integer.MAX_VALUE), any());
    }

    @Test
    void rejectsForeignConversationBeforeQueryingMessages() throws Exception {
        ConversationRepository conversations = mock(ConversationRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "");
        String output = new SearchConversationTool(conversations, messages, mapper).callback(context)
                .call("{\"query\":\"decision\"}");
        assertEquals("FORBIDDEN", mapper.readTree(output).get("error").asText());
        verifyNoInteractions(messages);
    }

    @Test
    void emptyQueryDoesNotSearch() throws Exception {
        ConversationRepository conversations = mock(ConversationRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "");
        String output = new SearchConversationTool(conversations, messages, mapper).callback(context).call("{\"query\":\"\"}");
        assertEquals("INVALID_QUERY", mapper.readTree(output).get("error").asText());
        verifyNoInteractions(messages);
    }

    @Test
    void excerptContainsMatchNearEndOfLongMessage() throws Exception {
        UUID userId = UUID.randomUUID(), conversationId = UUID.randomUUID();
        ConversationRepository conversations = mock(ConversationRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        when(conversations.findByIdAndOwnerId(conversationId, userId))
                .thenReturn(Optional.of(Conversation.builder().id(conversationId).ownerId(userId).build()));
        Message decision = Message.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .role("user").sequenceNo(9).content("a".repeat(900) + " chốt hạn nộp thứ Sáu").build();
        when(messages.searchScoped(eq(conversationId), eq("ROLE_STUDENT"), eq("hạn nộp"), anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(decision)));
        AgentContext context = new AgentContext(userId, "", Set.of("ROLE_STUDENT"), conversationId, UUID.randomUUID(), "");
        String output = new SearchConversationTool(conversations, messages, mapper).callback(context)
                .call("{\"query\":\"hạn nộp\"}");
        assertTrue(mapper.readTree(output).get("matches").get(0).get("excerpt").asText().contains("hạn nộp thứ Sáu"));
    }
}
