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
            4. Với thao tác ghi, BẮT BUỘC gọi tool ghi để lưu yêu cầu chờ đồng ý; chỉ hỏi người dùng đồng ý trong chat sau khi tool ghi trả về yêu cầu chờ xác nhận. Nếu chưa gọi tool ghi thì chưa có gì để xác nhận. Không được báo đã thực hiện trước khi có kết quả thực thi ở lượt chat sau. Khi có kế hoạch gồm nhiều thao tác ghi, BẮT BUỘC thực hiện tuần tự: mỗi lượt phản hồi chỉ được gọi tối đa 1 công cụ ghi; không gọi đồng thời nhiều công cụ ghi trong cùng một lượt.
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
            {planSection}
            {lecturerRules}
            {catalogSection}
            {taskSection}""";

    private static final String LECTURER_RULES = """

            QUY TẮC SOẠN BÀI VÀ BƯỚC CHẤM CHO GIẢNG VIÊN:
            1. Bài GRADED phải gắn classId của lớp giảng viên sở hữu; bài PRACTICE không gắn classId. Cả hai cần title và topic, tên bài duy nhất theo giảng viên. Không tự bịa topic, lớp, endpoint, kết quả mong đợi hoặc thông số Docker/DB từ ví dụ trong tài liệu.
            2. Mỗi plan là một câu kiểm tra trong assignment; sequenceOrder là thứ tự câu, weight là trọng số câu. Bước chấm thuộc plan, theo stepOrder. Tra cứu bài, câu và bước hiện có trước khi thêm để chọn đúng ID và thứ tự.
            3. Chọn stepType theo mục tiêu: HTTP_REQUEST, DB_QUERY, DB_SCHEMA_CHECK hoặc DB_MIGRATION. Dùng schema/capability của tool để điền config; không bắt mọi câu phải có đủ mọi assertion hay step type. HTTP_REQUEST dùng method và path bắt đầu bằng /; query_params nhận giá trị thô để executor mã hóa. Chỉ dùng biến ${...} sau khi extract ở bước trước; data[] tối đa 10 object, auto_inject_headers dùng để truyền header từ bước trước.
            4. DB connection phải khớp Docker Compose của bài: db_service là tên service DB, db_port là port DB nội bộ, không phải port HTTP ứng dụng; database/username/password lấy từ cấu hình thật. PRIMARY_KEY check cần table_name và column. DB_QUERY expected.row_count đếm hàng của result set; SELECT COUNT(*) luôn trả một hàng, nên muốn kiểm tra bản ghi đã xóa phải SELECT id lọc theo ID với row_count: 0.
            5. Ưu tiên quy tắc đã sửa sau chấm thật ở lecturer-full-e2e-plan.md mục 17.17–17.26 và hợp đồng backend hiện tại trước ví dụ DB cũ. Nếu người dùng cho phép tự suy luận, dùng lịch sử và mô tả bài tập để suy ra dữ liệu đã có; khi thiếu endpoint, expected response hoặc DB Compose không suy ra được, hỏi thông tin còn thiếu trước khi tạo. Không tự đặt GET / hay status 200 làm bước chấm của bài.
            """;

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
                "catalogSection", catalogSection,
                "lecturerRules", roles.contains("ROLE_LECTURER") ? LECTURER_RULES : ""
        ));
    }
}
