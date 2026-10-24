package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ContextBuilder {

    private final ToolRegistry toolRegistry;

    public ContextBuilder() {
        this(null);
    }

    @Autowired
    public ContextBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ContextBuilder(MessageRepository messageRepository, ConversationSummaryRepository summaryRepository, AgentProperties agentProperties) {
        this(null);
    }

    private static final String SYSTEM_TEMPLATE = """
            Bạn là AI Assistant thông minh của hệ thống Web Grading System (WGS).
            Vai trò của người dùng hiện tại: {role}.

            QUY TẮC BẮT BUỘC:
            1. Trả lời bằng tiếng Việt, thân thiện, súc tích và chính xác.
            2. Sử dụng tool được cung cấp để tra cứu dữ liệu. Tuyệt đối không tự bịa đặt thông tin database.
            3. Không bao giờ tiết lộ mã nguồn ẩn, test case ẩn hoặc đáp án chấm cho sinh viên.
            4. Với thao tác ghi, tool chỉ tạo yêu cầu chờ duyệt. Phải nói rõ đang chờ người dùng bấm xác nhận trên giao diện; không được báo đã thực hiện. Tin nhắn đồng ý không thay thế nút xác nhận.
            5. Không tuyên bố thao tác đã hoàn thành khi chưa nhận được kết quả thành công từ tool.
            6. Định dạng câu trả lời bằng Markdown hợp lệ, không trộn HTML để định dạng.
            7. Nếu tool báo lỗi, chỉ thử lại khi có dữ liệu mới để sửa yêu cầu; nếu thiếu thông tin thì hỏi người dùng. Không lặp lại cùng một thao tác ghi dữ liệu.
            {catalogSection}
            {taskSection}""";

    public String formatToolCatalog(Set<String> roles) {
        if (toolRegistry == null || roles == null || roles.isEmpty()) {
            return "";
        }
        List<AgentTool> tools = toolRegistry.getToolsForRoles(roles);
        if (tools == null || tools.isEmpty()) {
            return "";
        }

        Map<String, List<AgentTool>> grouped = new LinkedHashMap<>();
        grouped.put("Quản lý Bài tập", new ArrayList<>());
        grouped.put("Kiểm thử & Bước chấm", new ArrayList<>());
        grouped.put("Lớp học & Sinh viên", new ArrayList<>());
        grouped.put("Bảng điểm & Bài nộp", new ArrayList<>());
        grouped.put("Công cụ khác", new ArrayList<>());

        for (AgentTool t : tools) {
            String name = t.getName().toLowerCase();
            if (name.contains("assignment")) {
                grouped.get("Quản lý Bài tập").add(t);
            } else if (name.contains("test") || name.contains("step") || name.contains("plan")) {
                grouped.get("Kiểm thử & Bước chấm").add(t);
            } else if (name.contains("class") || name.contains("student") || name.contains("roster")) {
                grouped.get("Lớp học & Sinh viên").add(t);
            } else if (name.contains("score") || name.contains("result") || name.contains("submission") || name.contains("transcript")) {
                grouped.get("Bảng điểm & Bài nộp").add(t);
            } else {
                grouped.get("Công cụ khác").add(t);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<AgentTool>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append("- **").append(entry.getKey()).append("**:\n");
                entry.getValue().stream()
                        .sorted(Comparator.comparing(AgentTool::getName))
                        .forEach(t -> sb.append("  • `").append(t.getName()).append("`: ").append(t.getDescription()).append("\n"));
            }
        }
        return sb.toString().trim();
    }

    public String buildSystemPrompt(String role, Task task) {
        Set<String> roles = (role == null || role.isBlank()) ? Set.of("ROLE_USER")
                : Arrays.stream(role.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        return buildSystemPrompt(roles, task);
    }

    public String buildSystemPrompt(Set<String> roles, Task task) {
        String roleStr = (roles == null || roles.isEmpty()) ? "ROLE_USER" : String.join(", ", roles);
        String taskSection = "";
        if (task != null && task.getStateJson() != null) {
            taskSection = "\nTRẠNG THÁI NHIỆM VỤ HIỆN TẠI:\n" + task.getStateJson() + "\n";
        }

        String catalog = formatToolCatalog(roles);
        String catalogSection = "";
        if (!catalog.isEmpty()) {
            catalogSection = "\nDANH MỤC CÔNG CỤ KHẢ DỤNG CHO VAI TRÒ " + roleStr + ":\n"
                    + catalog + "\n\n"
                    + "QUY TẮC SỬ DỤNG CÔNG CỤ & TRẢ LỜI:\n"
                    + "1. Khi người dùng hỏi về năng lực hoặc chức năng của hệ thống (ví dụ: 'm giúp được gì cho t', 'bạn có thể làm gì'), BẮT BUỘC dựa vào danh mục công cụ trên để trả lời đầy đủ, chính xác. Tuyệt đối không tự bịa đặt tính năng hệ thống không hỗ trợ.\n"
                    + "2. Nếu người dùng yêu cầu thực hiện chức năng có trong danh mục nhưng tool đó chưa có sẵn trong danh sách gọi trực tiếp, BẮT BUỘC gọi `search_tools` với tên công cụ đó để nạp công cụ trước khi thực hiện. Tuyệt đối không từ chối hoặc nói không có công cụ khi chưa gọi `search_tools`.";
        }

        SystemPromptTemplate template = new SystemPromptTemplate(SYSTEM_TEMPLATE);
        return template.render(Map.of(
                "role", roleStr,
                "taskSection", taskSection,
                "catalogSection", catalogSection
        ));
    }
}
