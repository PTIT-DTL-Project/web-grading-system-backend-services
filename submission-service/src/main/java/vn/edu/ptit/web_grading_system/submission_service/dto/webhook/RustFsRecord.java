package vn.edu.ptit.web_grading_system.submission_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustFsRecord {
    private String eventName;
    private RustFsS3 s3;
}
