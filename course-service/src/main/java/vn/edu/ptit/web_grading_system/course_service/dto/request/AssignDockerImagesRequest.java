package vn.edu.ptit.web_grading_system.course_service.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDockerImagesRequest {

    @NotNull
    private List<UUID> dockerImageIds;
}
