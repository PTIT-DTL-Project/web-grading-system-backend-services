package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.StepResultDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PromptBuilder {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public String buildSystemPrompt() {
        return """
            Bạn là Trợ giảng AI Thông minh (Pedagogical AI Tutor) chuyên ngành Công nghệ Thông tin tại Học viện Công nghệ Bưu chính Viễn thông (PTIT).
            Nhiệm vụ của bạn là đánh giá và phân tích bài nộp của sinh viên nhằm nâng cao kỹ năng lập trình và thiết kế phần mềm.

            QUY TẮC CỐT LÕI:
            1. PHƯƠNG PHÁP SƯ PHẠM: TUYỆT ĐỐI KHÔNG viết sẵn toàn bộ code sửa lỗi cho sinh viên chép. Hãy đóng vai người hướng dẫn: chỉ rõ nguyên nhân gốc rễ, vị trí nghi vấn trong code, đưa ra các gợi ý từng bước (Hints) và từ khóa tự học (Learning Keywords) để sinh viên tự sửa và hiểu sâu.
            2. VỚI CÁC STEP THẤT BẠI (FAIL):
               - category: "DEBUG_FAIL"
               - Chẩn đoán chính xác nguyên nhân (dựa trên status code, response body, error message, stack trace).
               - Đưa ra vị trí nghi ngờ (suspected location, ví dụ: Controller thiếu @Valid, Service thiếu check null, Entity thiếu annotation).
               - Đưa ra danh sách gợi ý hành động cụ thể (actionable hints).
            3. VỚI CÁC STEP THÀNH CÔNG (PASS):
               - category: "OPTIMIZE_PASS"
               - Không chỉ khen ngợi, hãy rà soát 4 tiêu chí tối ưu:
                 a) N+1 Query: Kiểm tra dbQueryCount và dbQueryLogs. Nếu API lấy danh sách mà có quá nhiều câu SELECT đơn lẻ -> cảnh báo N+1 Query.
                 b) Over-fetching / Bảo mật: Kiểm tra response JSON có để lộ trường nhạy cảm hoặc thừa thãi không.
                 c) Chuẩn RESTful: Đánh giá mã HTTP phản hồi (ví dụ: POST tạo mới nên trả về 201 Created thay vì 200 OK).
                 d) Hiệu năng & Bộ nhớ: Đối chiếu duration_ms và peak_memory_mb.
            4. ĐỊNH DẠNG ĐẦU RA:
               - Bạn BẮT BUỘC chỉ trả về định dạng JSON hợp lệ, KHÔNG thêm markdown backticks (```json ... ```) ở đầu/cuối, tuân thủ đúng cấu trúc sau:
               {
                 "overallSummary": "Tóm tắt tổng quan bài nộp...",
                 "stepAnalyses": [
                   {
                     "stepId": "<uuid>",
                     "stepName": "<tên step>",
                     "stepType": "<loại step>",
                     "passed": true/false,
                     "category": "DEBUG_FAIL" hoặc "OPTIMIZE_PASS",
                     "diagnosis": "Chẩn đoán chi tiết...",
                     "suspectedLocation": "Vị trí nghi vấn...",
                     "actionableHints": ["Gợi ý 1", "Gợi ý 2"],
                     "learningKeywords": ["Từ khóa 1", "Từ khóa 2"],
                     "telemetryAudit": {
                       "durationMs": 120,
                       "expectedThresholdMs": 200,
                       "nPlusOneDetected": false,
                       "queryCount": 1,
                       "sensitiveFieldsExposed": [],
                       "peakMemoryMb": 64
                     }
                   }
                 ]
               }
            5. NGÔN NGỮ: Tiếng Việt sư phạm, chuyên nghiệp, khích lệ tinh thần học tập của sinh viên.
            """;
    }

    public String buildUserPrompt(AssignmentDetailDto assignment, ResultDto result) {
        Map<String, Object> context = new HashMap<>();
        context.put("assignmentTitle", assignment != null ? assignment.getTitle() : "N/A");
        context.put("assignmentDescription", assignment != null ? assignment.getDescription() : "N/A");
        context.put("maxMemoryMb", assignment != null ? assignment.getMaxMemoryMb() : null);
        context.put("executionTimeoutMs", assignment != null ? assignment.getExecutionTimeoutMs() : null);

        context.put("submissionScore", result.getScore());
        context.put("maxScore", result.getMaxScore());
        context.put("status", result.getStatus());
        context.put("summaryLog", result.getSummaryLog());

        List<StepResultDto> steps = result.getSteps() != null ? result.getSteps() : List.of();
        List<Map<String, Object>> sanitizedSteps = steps.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("stepId", s.getStepId());
            map.put("stepName", s.getStepName());
            map.put("stepType", s.getStepType());
            map.put("passed", s.getPassed());
            map.put("skipped", s.getSkipped());
            map.put("durationMs", s.getDurationMs());
            map.put("responseStatus", s.getResponseStatus());
            map.put("expectedValue", s.getExpectedValue());
            map.put("actualValue", DataSanitizer.sanitizeBody(s.getActualValue()));
            map.put("errorMessage", s.getErrorMessage());
            map.put("dbQueryCount", s.getDbQueryCount());
            map.put("dbQueryLogs", s.getDbQueryLogs());
            map.put("stackTrace", s.getStackTrace());
            map.put("peakMemoryMb", s.getPeakMemoryMb());
            return map;
        }).toList();

        context.put("steps", sanitizedSteps);

        return "Hãy phân tích toàn diện bài nộp dưới đây theo các chỉ dẫn và xuất ra JSON:\n" + gson.toJson(context);
    }

    public org.springframework.ai.chat.prompt.Prompt buildPrompt(AssignmentDetailDto assignment, ResultDto result) {
        return new org.springframework.ai.chat.prompt.Prompt(java.util.List.of(
                new org.springframework.ai.chat.messages.SystemMessage(buildSystemPrompt()),
                new org.springframework.ai.chat.messages.UserMessage(buildUserPrompt(assignment, result))
        ));
    }
}
