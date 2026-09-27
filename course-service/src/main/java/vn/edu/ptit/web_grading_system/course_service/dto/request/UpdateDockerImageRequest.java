package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDockerImageRequest {

    @Size(max = 255)
    private String name;

    @Size(max = 500)
    @Pattern(regexp = "^(?!.*:latest$).*[A-Za-z0-9][A-Za-z0-9._/-]*:[A-Za-z0-9._-]+$",
            message = "image_url must be a registry/repo:tag with an explicit tag (':latest' is forbidden)")
    private String imageUrl;

    private String description;
}
