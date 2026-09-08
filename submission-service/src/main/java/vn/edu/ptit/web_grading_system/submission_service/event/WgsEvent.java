package vn.edu.ptit.web_grading_system.submission_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Envelope for every message on the shared wgs-events topic.
 * Producers serialize with the default Jackson JsonSerializer of spring-kafka —
 * no custom annotations, plain field names are the wire format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WgsEvent<T> {

    private WgsEventAction action;

    @Builder.Default
    private int version = 1;

    @Builder.Default
    private String timestamp = Instant.now().toString();

    private String traceId;

    private T payload;

    public static <T> WgsEvent<T> of(WgsEventAction action, T payload) {
        return WgsEvent.<T>builder()
                .action(action)
                .payload(payload)
                .timestamp(Instant.now().toString())
                .build();
    }
}
