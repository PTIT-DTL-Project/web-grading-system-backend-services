package vn.edu.ptit.web_grading_system.course_service.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerImageResponse {

    private UUID id;
    private String name;
    private String imageUrl;
    private String description;
}
