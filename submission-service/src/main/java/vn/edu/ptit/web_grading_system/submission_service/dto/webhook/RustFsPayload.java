package vn.edu.ptit.web_grading_system.submission_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RustFsPayload {
    @JsonProperty("EventName")
    private String rootEventName;

    @JsonProperty("Key")
    private String rootKey;

    @JsonProperty("Records")
    private List<RustFsRecord> records;
}
