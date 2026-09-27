package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.course_service.Constant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDockerImageRequest {

    @NotBlank
    @Size(max = Constant.Image.NAME_MAX)
    private String name;

    @NotBlank
    @Size(max = Constant.Image.IMAGE_URL_MAX)
    @Pattern(regexp = Constant.Image.IMAGE_URL_REGEX,
            message = Constant.Image.INVALID_URL_MESSAGE)
    private String imageUrl;

    private String description;
}
