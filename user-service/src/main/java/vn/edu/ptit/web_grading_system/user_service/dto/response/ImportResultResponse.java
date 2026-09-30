package vn.edu.ptit.web_grading_system.user_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportResultResponse {
    private int total;
    private int success;
    private int failed;
    private List<ImportError> errors;

    @Data
    @Builder
    public static class ImportError {
        private int row;
        private String reason;
    }
}
