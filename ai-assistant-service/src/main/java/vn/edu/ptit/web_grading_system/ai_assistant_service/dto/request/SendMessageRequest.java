package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Message text cannot be empty")
    private String text;

    private String clientMessageId;

    private Integer expectedTaskVersion;
}
