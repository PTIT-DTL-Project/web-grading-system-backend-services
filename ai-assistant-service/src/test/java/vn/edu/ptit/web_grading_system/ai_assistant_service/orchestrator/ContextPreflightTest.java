package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.ConversationSummary;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationSummarizer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContextPreflightTest {
    private final UUID conversationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void keepsCompleteMessagesAndSelectsByCount() {
        MessageRepository messages = mock(MessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        ConversationSummarizer summarizer = mock(ConversationSummarizer.class);
        AgentProperties props = new AgentProperties();
        props.getContext().setHistoryWindowSize(2);
        props.getContext().setHistoryMaxTokens(100);
        props.getContext().setToolMaxTokens(100);
        when(messages.findScopedRecent(eq(conversationId), eq("ROLE_STUDENT"), any()))
                .thenReturn(new PageImpl<>(List.of(message(3, "third whole message"), message(2, "second whole message"))));
        when(summaries.findTopByConversationIdAndRoleScopeOrderByCreatedAtDesc(conversationId, "ROLE_STUDENT"))
                .thenReturn(Optional.empty());
        ContextPreflight context = new ContextPreflight(messages, summaries, summarizer, props, new TokenEstimator());

        var result = context.prepare(conversationId, ownerId, Set.of("ROLE_STUDENT"), "system", List.of(), List.of());

        assertEquals(List.of("second whole message", "third whole message"),
                result.messages().stream().map(org.springframework.ai.chat.messages.Message::getText).toList());
        verify(messages).findScopedRecent(eq(conversationId), eq("ROLE_STUDENT"), eq(PageRequest.of(0, 2)));
    }

    @Test
    void oversizedLatestMessageFailsWithoutClipping() {
        MessageRepository messages = mock(MessageRepository.class);
        AgentProperties props = new AgentProperties();
        props.getContext().setHistoryMaxTokens(2);
        when(messages.findScopedRecent(eq(conversationId), eq("ROLE_STUDENT"), any()))
                .thenReturn(new PageImpl<>(List.of(message(1, "a whole message that cannot fit"))));
        ContextPreflight context = new ContextPreflight(messages, mock(ConversationSummaryRepository.class),
                mock(ConversationSummarizer.class), props, new TokenEstimator());
        assertThrows(ContextPreflight.InputTooLargeException.class,
                () -> context.prepare(conversationId, ownerId, Set.of("ROLE_STUDENT"), "system", List.of(), List.of()));
    }

    @Test
    void messageEvictedAfterSummaryGrowthIsAlsoSummarized() {
        MessageRepository messages = mock(MessageRepository.class);
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        ConversationSummarizer summarizer = mock(ConversationSummarizer.class);
        AgentProperties props = new AgentProperties();
        props.getContext().setHistoryWindowSize(2);
        props.getContext().setHistoryMaxTokens(35);
        Message older = message(1, "old choice");
        Message middle = message(2, "middle important decision");
        Message latest = message(3, "latest request");
        when(messages.findScopedRecent(eq(conversationId), eq("ROLE_STUDENT"), any()))
                .thenReturn(new PageImpl<>(List.of(latest, middle), PageRequest.of(0, 2), 3));
        when(messages.findScopedBefore(eq(conversationId), eq("ROLE_STUDENT"), eq(2), any()))
                .thenReturn(new PageImpl<>(List.of(older)));
        when(messages.findScopedBefore(eq(conversationId), eq("ROLE_STUDENT"), eq(3), any()))
                .thenReturn(new PageImpl<>(List.of(middle, older)));
        when(summaries.findTopByConversationIdAndRoleScopeOrderByCreatedAtDesc(conversationId, "ROLE_STUDENT"))
                .thenReturn(Optional.empty());
        when(summarizer.summarize(eq(""), anyList(), eq(17))).thenReturn("a summary that uses many tokens");

        new ContextPreflight(messages, summaries, summarizer, props, new TokenEstimator())
                .prepare(conversationId, ownerId, Set.of("ROLE_STUDENT"), "system", List.of(), List.of());

        verify(summarizer).summarize(eq(""), anyList(), eq(17));
        verify(summaries).save(argThat(saved -> saved.getThroughMessageId().equals(middle.getId())));
    }

    private Message message(int sequence, String content) {
        return Message.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .sequenceNo(sequence).role(sequence % 2 == 0 ? "assistant" : "user").content(content).build();
    }
}
