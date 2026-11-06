package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class ConversationSummarizerTest {
    @Test
    void laterCompletedPlanReplacesEarlierPendingPlanTask() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.generateStrictCompletion(anyString(), anyString()))
                .thenReturn("Đã tạo câu kiểm tra; đang tạo bước chấm.");
        Message completed = Message.builder().sequenceNo(28).role("assistant")
                .content("Đã tạo thành công câu kiểm tra Kiểm tra API thêm sách.").build();
        Message current = Message.builder().sequenceNo(29).role("user")
                .content("Tạo bước chấm cho câu kiểm tra này.").build();

        new ConversationSummarizer(llm).summarize(
                "Chờ name và sequenceOrder để tạo câu kiểm tra", List.of(completed, current), 200);

        ArgumentCaptor<String> instructions = ArgumentCaptor.forClass(String.class);
        verify(llm).generateStrictCompletion(instructions.capture(), anyString());
        assertTrue(instructions.getValue().contains("hoàn tất"));
        assertTrue(instructions.getValue().contains("mới nhất"));
    }
}
