package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final MessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final AgentProperties agentProperties;

    public String buildSystemPrompt(String role, Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là AI Assistant thông minh của hệ thống Web Grading System (WGS).\n");
        sb.append("Vai trò của người dùng hiện tại: ").append(role).append(".\n\n");
        sb.append("QUY TẮC BẮT BUỘC:\n");
        sb.append("1. Trả lời bằng tiếng Việt, thân thiện, súc tích và chính xác.\n");
        sb.append("2. Sử dụng tool được cung cấp để tra cứu dữ liệu. Tuyệt đối không tự bịa đặt thông tin database.\n");
        sb.append("3. Không bao giờ tiết lộ mã nguồn ẩn, test case ẩn hoặc đáp án chấm cho sinh viên.\n");
        sb.append("4. Đối với giảng viên tạo bài tập: hỏi lại các tham số cần thiết, đề xuất cấu hình bài tập trong text và chỉ thực hiện tạo khi người dùng đồng ý.\n");
        sb.append("5. Không tuyên bố thao tác đã hoàn thành khi chưa nhận được kết quả thành công từ tool.\n");

        if (task != null && task.getStateJson() != null) {
            sb.append("\nTRẠNG THÁI NHIỆM VỤ HIỆN TẠI:\n").append(task.getStateJson()).append("\n");
        }
        return sb.toString();
    }

    public List<Message> getRecentMessages(Conversation conversation) {
        int windowSize = agentProperties.getContext().getHistoryWindowSize();
        List<Message> allMessages = messageRepository.findByConversationIdOrderBySequenceNoAsc(conversation.getId());
        if (allMessages.size() <= windowSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - windowSize, allMessages.size());
    }
}
