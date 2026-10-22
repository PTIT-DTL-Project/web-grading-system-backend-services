package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.NoArgsConstructor;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.Map;

@Component
@NoArgsConstructor
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

    public ContextBuilder(MessageRepository messageRepository, ConversationSummaryRepository summaryRepository, AgentProperties agentProperties) {
    }

    public String buildSystemPrompt(String role, Task task) {
        String taskSection = "";
        if (task != null && task.getStateJson() != null) {
            taskSection = "\nTRẠNG THÁI NHIỆM VỤ HIỆN TẠI:\n" + task.getStateJson() + "\n";
        }
        SystemPromptTemplate template = new SystemPromptTemplate(SYSTEM_TEMPLATE);
        return template.render(Map.of("role", role != null ? role : "ROLE_USER", "taskSection", taskSection));
    }
}
