package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContextBuilderTest {

    @Test
    void testBuildPromptContainsRoleAndRules() {
        MessageRepository messageRepo = mock(MessageRepository.class);
        ConversationSummaryRepository summaryRepo = mock(ConversationSummaryRepository.class);
        AgentProperties properties = new AgentProperties();

        ContextBuilder builder = new ContextBuilder(messageRepo, summaryRepo, properties);

        Conversation conv = Conversation.builder().id(UUID.randomUUID()).build();
        when(messageRepo.findByConversationIdOrderBySequenceNoAsc(conv.getId())).thenReturn(List.of(
                Message.builder().role("user").content("Tìm lớp của tôi").sequenceNo(1).build()
        ));

        String prompt = builder.buildSystemPrompt("ROLE_STUDENT", null);
        assertTrue(prompt.contains("WGS"), "Prompt should mention WGS");
        assertTrue(prompt.contains("ROLE_STUDENT"), "Prompt should mention role");

        List<Message> recent = builder.getRecentMessages(conv);
        assertEquals(1, recent.size());
    }

    @Test
    void testBuildSpringAiMessages() {
        MessageRepository messageRepo = mock(MessageRepository.class);
        ConversationSummaryRepository summaryRepo = mock(ConversationSummaryRepository.class);
        AgentProperties properties = new AgentProperties();
        ContextBuilder builder = new ContextBuilder(messageRepo, summaryRepo, properties);

        List<Message> history = List.of(
                Message.builder().role("user").content("Hello").build(),
                Message.builder().role("assistant").content("Hi there!").build()
        );

        List<org.springframework.ai.chat.messages.Message> springAiMessages =
                builder.buildSpringAiMessages("System rule", history);

        assertEquals(3, springAiMessages.size());
        assertEquals(org.springframework.ai.chat.messages.MessageType.SYSTEM, springAiMessages.get(0).getMessageType());
        assertEquals(org.springframework.ai.chat.messages.MessageType.USER, springAiMessages.get(1).getMessageType());
        assertEquals(org.springframework.ai.chat.messages.MessageType.ASSISTANT, springAiMessages.get(2).getMessageType());
    }

    @Test
    void testTokenEstimator() {
        TokenEstimator estimator = new TokenEstimator();
        assertEquals(0, estimator.estimateTokens(""));
        assertEquals(0, estimator.estimateTokens(null));
        assertTrue(estimator.estimateTokens("Hello world") > 0);
    }
}
