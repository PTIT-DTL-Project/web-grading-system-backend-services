package vn.edu.ptit.web_grading_system.user_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int statusCode;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().statusCode(200).message("Success").data(data).build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder().statusCode(201).message("Created").data(data).build();
    }

    public static <T> ApiResponse<T> noContent() {
        return ApiResponse.<T>builder().statusCode(204).message("No Content").data(null).build();
    }
}
