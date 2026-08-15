package vn.edu.ptit.web_grading_system.submission_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustFsBucket {
    private String name;
    private String arn;
}
