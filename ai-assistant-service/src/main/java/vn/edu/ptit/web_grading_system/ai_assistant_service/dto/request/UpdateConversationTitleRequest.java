package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationTitleRequest(@NotBlank @Size(max = 255) String title) {
}
