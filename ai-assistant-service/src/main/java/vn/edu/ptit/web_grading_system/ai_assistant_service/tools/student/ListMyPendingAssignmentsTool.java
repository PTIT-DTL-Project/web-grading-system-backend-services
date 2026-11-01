package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.student;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.SubmissionServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.SubmissionSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListMyPendingAssignmentsTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;
    private final SubmissionServiceClient submissionServiceClient;

    @Override
    public String getName() {
        return "listMyPendingAssignments";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các bài tập mà sinh viên chưa nộp hoặc chưa hoàn thành đạt yêu cầu.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listMyPendingAssignments",
                getDescription(),
                "1.0",
                "student",
                "read",
                "assignment",
                EffectType.READ,
                Set.of("ROLE_STUDENT"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        if (context.roles() == null || !context.roles().contains("ROLE_STUDENT")) {
            return ToolResult.error("FORBIDDEN", "Chỉ sinh viên mới có quyền sử dụng công cụ này", false);
        }

        try {
            PageDto<AssignmentSummaryDto> assPage = courseServiceClient.listStudentAssignments(
                    context.userId().toString(),
                    context.email(),
                    null,
                    null,
                    0,
                    50,
                    context.gatewaySecret()
            );

            PageDto<SubmissionSummaryDto> subPage = submissionServiceClient.listMySubmissions(
                    context.userId().toString(),
                    0,
                    100,
                    context.gatewaySecret()
            );

            Set<UUID> completedAssignmentIds = new HashSet<>();
            if (subPage != null && subPage.getContent() != null) {
                for (SubmissionSummaryDto sub : subPage.getContent()) {
                    if (sub.getScore() != null && sub.getScore() >= 8.0) {
                        completedAssignmentIds.add(sub.getAssignmentId());
                    }
                }
            }

            List<AssignmentSummaryDto> pending = Collections.emptyList();
            if (assPage != null && assPage.getContent() != null) {
                pending = assPage.getContent().stream()
                        .filter(a -> !completedAssignmentIds.contains(a.getId()))
                        .collect(Collectors.toList());
            }

            return ToolResult.ok(pending);
        } catch (Exception e) {
            log.error("Failed to list pending assignments for student {}", context.userId(), e);
            return ToolResult.error("PENDING_ASSIGNMENTS_ERROR", "Không thể lấy bài tập chờ làm: " + e.getMessage(), true);
        }
    }
}
