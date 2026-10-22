package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final String SYSTEM_TEMPLATE = """
            Bạn là AI Assistant thông minh của hệ thống Web Grading System (WGS).
            Vai trò của người dùng hiện tại: {role}.

            QUY TẮC BẮT BUỘC:
            1. Trả lời bằng tiếng Việt, thân thiện, súc tích và chính xác.
            2. Sử dụng tool được cung cấp để tra cứu dữ liệu. Tuyệt đối không tự bịa đặt thông tin database.
            3. Không bao giờ tiết lộ mã nguồn ẩn, test case ẩn hoặc đáp án chấm cho sinh viên.
            4. Đối với giảng viên tạo bài tập: hỏi lại các tham số cần thiết, đề xuất cấu hình bài tập trong text và chỉ thực hiện tạo khi người dùng đồng ý.
            5. Không tuyên bố thao tác đã hoàn thành khi chưa nhận được kết quả thành công từ tool.
            {taskSection}""";

    private final MessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final AgentProperties agentProperties;

    public String buildSystemPrompt(String role, Task task) {
        String taskSection = "";
        if (task != null && task.getStateJson() != null) {
            taskSection = "\nTRẠNG THÁI NHIỆM VỤ HIỆN TẠI:\n" + task.getStateJson() + "\n";
        }
        SystemPromptTemplate template = new SystemPromptTemplate(SYSTEM_TEMPLATE);
        return template.render(Map.of("role", role != null ? role : "ROLE_USER", "taskSection", taskSection));
    }

    public List<org.springframework.ai.chat.messages.Message> buildSpringAiMessages(String systemPrompt, List<Message> history) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        if (history != null) {
            for (Message msg : history) {
                String content = msg.getContent() != null ? msg.getContent() : "";
                if ("user".equalsIgnoreCase(msg.getRole())) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equalsIgnoreCase(msg.getRole())) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }
        return messages;
    }

    public List<Message> getRecentMessages(Conversation conversation) {
        int windowSize = agentProperties.getContext().getHistoryWindowSize();
        List<Message> allMessages = messageRepository.findByConversationIdOrderBySequenceNoAsc(conversation.getId());
        if (allMessages.size() <= windowSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - windowSize, allMessages.size());
    }

    public String buildUserPromptWithHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent() != null ? msg.getContent() : "").append("\n");
        }
        return sb.toString().trim();
    }
}
