package vn.edu.ptit.web_grading_system.executor_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Envelope for every message on the shared wgs-events topic. Wire format =
 * plain field names (JSON, tools.jackson serializer on the producer side).
 * Deserializer tolerant: missing action field maps to GRADE_SUBMISSION for
 * backward compatibility with pre-envelope messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WgsEvent<T> {
    private String action;

    @Builder.Default
    private int version = 1;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private String traceId;

    private T payload;
}
