package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private String code;
    private boolean retryable;
    private String message;
    private Object data;
    private String correlationId;

    public static ToolResult ok(Object data) {
        return ToolResult.builder().success(true).code("OK").retryable(false).data(data).build();
    }

    public static ToolResult error(String code, String message, boolean retryable) {
        return ToolResult.builder().success(false).code(code).message(message).retryable(retryable).build();
    }
}
