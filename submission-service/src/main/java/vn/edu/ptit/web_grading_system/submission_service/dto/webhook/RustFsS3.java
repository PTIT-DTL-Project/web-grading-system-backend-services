package vn.edu.ptit.web_grading_system.submission_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustFsS3 {
    private RustFsBucket bucket;
    private RustFsObject object;
}
