package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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
    private final Resource systemPromptResource;

    public PromptBuilder(@Value("classpath:/prompts/pedagogical-tutor-system.st") Resource systemPromptResource) {
        this.systemPromptResource = systemPromptResource != null ? systemPromptResource : new ClassPathResource("prompts/pedagogical-tutor-system.st");
    }

    public PromptBuilder() {
        this(new ClassPathResource("prompts/pedagogical-tutor-system.st"));
    }

    public String buildSystemPrompt() {
        return new SystemPromptTemplate(systemPromptResource).render();
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
