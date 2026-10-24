package vn.edu.ptit.web_grading_system.ai_assistant_service.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaChatMemoryTest {

    @Mock
    private MessageRepository messageRepository;

    private JpaChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        chatMemory = new JpaChatMemory(messageRepository);
    }

    @Test
    void testAddAndGetMessages() {
        UUID conversationId = UUID.randomUUID();
        when(messageRepository.findMaxSequenceNo(conversationId)).thenReturn(0);

        List<Message> newMessages = List.of(
                new UserMessage("Xin chào!"),
                new AssistantMessage("Chào bạn, tôi có thể giúp gì?")
        );

        chatMemory.add(conversationId.toString(), newMessages);

        ArgumentCaptor<vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message> captor =
                ArgumentCaptor.forClass(vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.class);
        verify(messageRepository, times(2)).save(captor.capture());

        List<vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message> saved = captor.getAllValues();
        assertEquals("user", saved.get(0).getRole());
        assertEquals("Xin chào!", saved.get(0).getContent());
        assertEquals(1, saved.get(0).getSequenceNo());

        assertEquals("assistant", saved.get(1).getRole());
        assertEquals("Chào bạn, tôi có thể giúp gì?", saved.get(1).getContent());
        assertEquals(2, saved.get(1).getSequenceNo());
    }

    @Test
    void testGetMessages() {
        UUID conversationId = UUID.randomUUID();
        vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message m1 =
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .conversationId(conversationId)
                        .role("user")
                        .content("Câu hỏi 1")
                        .sequenceNo(1)
                        .build();

        vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message m2 =
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .conversationId(conversationId)
                        .role("assistant")
                        .content("Trả lời 1")
                        .sequenceNo(2)
                        .build();

        when(messageRepository.findByConversationIdOrderBySequenceNoAsc(conversationId)).thenReturn(List.of(m1, m2));

        List<Message> messages = chatMemory.get(conversationId.toString());
        assertEquals(2, messages.size());
        assertEquals(org.springframework.ai.chat.messages.MessageType.USER, messages.get(0).getMessageType());
        assertEquals("Câu hỏi 1", messages.get(0).getText());
        assertEquals(org.springframework.ai.chat.messages.MessageType.ASSISTANT, messages.get(1).getMessageType());
        assertEquals("Trả lời 1", messages.get(1).getText());
    }

    @Test
    void assistantReplyKeepsRunIdForRoleScopedHistory() {
        UUID conversationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(messageRepository.findMaxSequenceNo(conversationId)).thenReturn(4);
        chatMemory.addForRun(conversationId.toString(), runId, List.of(new AssistantMessage("Trả lời nguyên vẹn")));
        ArgumentCaptor<vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message> captor =
                ArgumentCaptor.forClass(vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.class);
        verify(messageRepository).save(captor.capture());
        assertEquals(runId, captor.getValue().getRunId());
        assertEquals("Trả lời nguyên vẹn", captor.getValue().getContent());
    }
}
