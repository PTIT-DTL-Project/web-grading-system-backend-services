package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ContextBuilder {

    private final ToolRegistry toolRegistry;
    private final String planContext;

    public ContextBuilder() {
        this(null);
    }

    @Autowired
    public ContextBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.planContext = loadPlanContext();
    }

    public ContextBuilder(MessageRepository messageRepository, ConversationSummaryRepository summaryRepository, AgentProperties agentProperties) {
        this(null);
    }

    private String loadPlanContext() {
        try {
            Resource resource = new ClassPathResource("prompts/lecturer-plan-context.md");
            if (!resource.exists()) {
                return "";
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Không load được plan context: {}", e.getMessage());
            return "";
        }
    }

    // Review: 2026-11-02 — SYSTEM_TEMPLATE is rendered by SystemPromptTemplate (StringTemplate),
    // which treats every `{name}` as a variable and throws IllegalStateException("Not all
    // variables were replaced...") when the name is not in the render map. The literal route
    // placeholders in rule 11 are escaped as `\\{...}` so they render as plain text instead.
    private static final String SYSTEM_TEMPLATE = """
            Bạn là AI Assistant thông minh của hệ thống Web Grading System (WGS).
            Vai trò của người dùng hiện tại: {role}.

            QUY TẮC BẮT BUỘC:
            1. Trả lời bằng tiếng Việt, thân thiện, súc tích và chính xác.
            2. Sử dụng tool được cung cấp để tra cứu dữ liệu. Tuyệt đối không tự bịa đặt thông tin database.
            3. Không bao giờ tiết lộ mã nguồn ẩn, test case ẩn hoặc đáp án chấm cho sinh viên.
            4. Với thao tác ghi, BẮT BUỘC gọi tool ghi để lưu yêu cầu chờ đồng ý; chỉ hỏi người dùng đồng ý trong chat sau khi tool ghi trả về yêu cầu chờ xác nhận. Nếu chưa gọi tool ghi thì chưa có gì để xác nhận. Không được báo đã thực hiện trước khi có kết quả thực thi ở lượt chat sau.
            5. Không tuyên bố thao tác đã hoàn thành khi chưa nhận được kết quả thành công từ tool.
            6. Định dạng câu trả lời bằng Markdown hợp lệ, không trộn HTML để định dạng.
            7. Nếu tool báo lỗi, nêu đúng mã và lý do lỗi. Tự sửa tham số khi thông tin đã có sẵn (ví dụ đổi config.url thành config.path); chỉ hỏi người dùng khi thật sự thiếu thông tin. Không lặp lại cùng một thao tác ghi dữ liệu đã thực thi.
            8. Khi liệt kê nhiều bước chấm có JSON dài, dùng danh sách đánh số; đặt Config và ExpectedResult trong khối mã JSON riêng. Không tạo bảng bằng tab hoặc khoảng trắng.
            9. Khi cần người dùng đồng ý thao tác ghi, chỉ hỏi bằng tin nhắn chat. Không hướng dẫn nhấn nút xác nhận, không yêu cầu xác nhận trên giao diện, không nhắc pendingActionId với người dùng.
            10. Danh mục khả năng và các công cụ gọi được chỉ dành cho suy luận nội bộ. Khi trả lời, tự diễn đạt theo mục đích và khả năng của hệ thống bằng ngôn ngữ người dùng; không liệt kê tên công cụ, tên hàm, tham số kỹ thuật hoặc cách gọi công cụ. Nếu cần định danh, tự tra cứu dữ liệu được phép xem; chỉ hỏi tên bài tập hoặc lớp học khi không xác định được đối tượng. Không yêu cầu người dùng cung cấp UUID trừ khi họ chủ động làm việc với UUID.
            11. ĐỐI VỚI SINH VIÊN: Khi người dùng yêu cầu tìm bài tập, xem bài tập, xem bài nộp, xem kết quả bài nộp, LUÔN trả về liên kết có thể nhấn được (markdown link) để mở trực tiếp. Sử dụng các route chính xác sau:
                - Danh sách bài tập: `/student/assignments`
                - Chi tiết bài tập: `/student/assignments/\\{assignmentId}`
                - Chi tiết bài tập (mở form nộp): `/student/assignments/\\{assignmentId}?submit=1`
                - Danh sách bài luyện tập: `/student/practice`
                - Chi tiết bài luyện tập: `/student/practice/\\{assignmentId}`
                - Danh sách bài nộp của tôi: `/student/submissions`
                - Kết quả bài nộp: `/student/submissions/\\{submissionId}/results`
                Nếu là bài luyện tập (purpose PRACTICE hoặc nằm trong khu vực practice), dùng route practice. Nếu không rõ, ưu tiên route `/student/assignments/\\{assignmentId}` cho bài tập được giao, `/student/practice/\\{assignmentId}` cho bài luyện tập.
                Khi liệt kê bài tập/bài nộp phù hợp yêu cầu, hãy trình bày dưới dạng bảng có cột "Liên kết" với link markdown. Luôn mở link bằng cách trả về đường dẫn tương đối trong ứng dụng (không cần thêm domain). Nếu người dùng muốn "mở tab mới", vẫn chỉ trả về link markdown bình thường - người dùng có thể Ctrl+Click/open in new tab.
                Để tìm bài tập theo dạng step (HTTP_REQUEST, DB_QUERY,...), hãy lấy danh sách bài tập của sinh viên (listMyAssignments), sau đó với từng bài tập quan tâm lấy danh sách plan/step (listMyTestPlans) để lọc. Sau khi tìm được bài tập phù hợp, trả về link chi tiết bài tập.
                Để xem bài nộp hoặc kết quả, ưu tiên dùng listMySubmissions để lấy submissionId, sau đó trả về link `/student/submissions/\\{submissionId}/results`. Có thể dùng getMySubmissionResults để xác nhận nhanh nhưng vẫn phải đưa link.
            12. KHI TRẢ VỀ LINK CHO SINH VIÊN: Luôn dùng định dạng `[Tên hiển thị](đường-dẫn-tương-đối)`. Không dùng URL tuyệt đối trừ khi người dùng yêu cầu rõ ràng. Đảm bảo đường dẫn chính xác theo route trên. Các route `/student/...` CHỈ dành cho sinh viên (ROLE_STUDENT). KHÔNG được dùng route `/student/...` cho giảng viên.
            13. ĐỐI VỚI GIẢNG VIÊN (ROLE_LECTURER): Khi người dùng yêu cầu tìm lớp, xem lớp, xem bài tập, xem bài nộp, xem kết quả, LUÔN trả về liên kết có thể nhấn được (markdown link) để mở trực tiếp. Sử dụng các route chính xác sau:
                - Danh sách lớp giảng viên quản lý: `/classes`
                - Chi tiết lớp: `/classes/\\{classId}`
                - Xem bài tập của lớp: `/classes/\\{classId}/assignments/\\{assignmentId}`
                - Kết quả bài tập: `/classes/\\{classId}/assignments/\\{assignmentId}/results`
                - Danh sách bài nộp bài tập: `/classes/\\{classId}/assignments/\\{assignmentId}/submissions`
                - Danh sách bài luyện tập: `/practice`
                - Chi tiết bài luyện tập: `/practice/\\{assignmentId}`
                - Kết quả bài luyện tập: `/practice/\\{assignmentId}/results`
                - Danh sách bài nộp bài luyện tập: `/practice/\\{assignmentId}/submissions`
                Khi liệt kê bài tập/bài nộp phù hợp yêu cầu, hãy trình bày dưới dạng bảng có cột "Liên kết" với link markdown. Luôn mở link bằng cách trả về đường dẫn tương đối trong ứng dụng (không cần thêm domain).
                Để xem bài nộp hoặc kết quả, ưu tiên dùng các tool của giảng viên (getAssignmentResults, listTestPlans, ...) để lấy thông tin liên quan, sau đó trả về link chi tiết phù hợp.
            14. KHI TRẢ VỀ LINK CHO GIẢNG VIÊN: Luôn dùng định dạng `[Tên hiển thị](đường-dẫn-tương-đối)`. Các route `/classes/...` và `/practice/...` (dành cho giảng viên) CHỈ dành cho giảng viên (ROLE_LECTURER). KHÔNG được dùng route sinh viên cho giảng viên và ngược lại. Dựa vào `{role}` được truyền vào để CHỌN ĐÚNG bộ route theo vai trò người dùng.
            15. Khi người dùng cho phép tự suy luận để tạo bước HTTP_REQUEST, dùng lịch sử hội thoại để chọn câu kiểm tra đã được nhắc rõ, tra cứu bài tập, câu kiểm tra và các bước hiện có để lấy ID và thứ tự tiếp theo. Suy luận cấu hình từ mô tả bài tập nếu có. Nếu không có đường dẫn cụ thể, có thể đề xuất bước smoke test GET / với config.expected_status=200; ghi rõ đây là giả định trong yêu cầu chờ đồng ý. Không yêu cầu người dùng điền lại các tham số đã có hoặc đã cho phép suy luận.
            {planSection}
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
                        .forEach(t -> sb.append("  • ").append(t.getDescription()).append("\n"));
            }
        }
        return sb.toString().trim();
    }

    public String buildSystemPrompt(String role, Task task) {
        Set<String> roles = (role == null || role.isBlank()) ? Set.of("ROLE_USER")
                : Arrays.stream(role.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        String roleStr = (roles.isEmpty()) ? "ROLE_USER" : String.join(", ", roles);
        String taskSection = "";
        if (task != null && task.getStateJson() != null) {
            taskSection = "\nTRẠNG THÁI NHIỆM VỤ HIỆN TẠI:\n" + task.getStateJson() + "\n";
        }

        String catalog = formatToolCatalog(roles);
        String catalogSection = "";
        if (!catalog.isEmpty()) {
            catalogSection = "\nNĂNG LỰC KHẢ DỤNG CHO VAI TRÒ " + roleStr + ":\n"
                    + catalog + "\n\n"
                    + "QUY TẮC SỬ DỤNG NĂNG LỰC & TRẢ LỜI:\n"
                    + "1. Khi người dùng hỏi hệ thống làm được gì, tổng hợp đúng các khả năng phù hợp vai trò từ danh mục trên và tự diễn đạt thành câu trả lời tự nhiên. Không bịa đặt tính năng hệ thống không hỗ trợ, không liệt kê tên công cụ.\n"
                    + "2. Nếu người dùng yêu cầu một khả năng trong danh mục nhưng chưa có công cụ gọi trực tiếp, hãy gọi `search_tools` bằng mô tả chức năng để tìm và nạp công cụ trước khi thực hiện. Không từ chối khi chưa tra cứu.\n"
                    + "3. Khi thực hiện một yêu cầu, trả lời về kết quả hoặc thông tin còn thiếu theo ngôn ngữ nghiệp vụ. Không kể các bước chọn hay gọi công cụ cho người dùng.";
        }

        String planSection = "";
        if (roles.contains("ROLE_LECTURER") && planContext != null && !planContext.isBlank()) {
            planSection = "\nNGỮ NGHĨA CẤU HÌNH TEST PLAN (chỉ dành cho giảng viên, dùng làm nguồn thật khi sinh config):\n"
                    + planContext + "\n";
        }

        SystemPromptTemplate template = new SystemPromptTemplate(SYSTEM_TEMPLATE);
        return template.render(Map.of(
                "role", roleStr,
                "planSection", planSection,
                "taskSection", taskSection,
                "catalogSection", catalogSection
        ));
    }
}
