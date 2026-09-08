package vn.edu.ptit.web_grading_system.submission_service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer for the shared wgs-events topic. All cross-service events for the
 * grading system go through here with an action discriminator — we never add
 * a second topic (5-topic Aiven free tier).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WgsEventsProducer {

    @Value("${wgs.events.topic:wgs-events}")
    private String topic;

    private final KafkaTemplate<String, WgsEvent<?>> kafkaTemplate;

    /**
     * Publish a grading job for a completed upload. Keyed by submissionId (idempotent
     * ordering per submission).
     */
    public void publishGradeSubmission(GradeSubmissionPayload payload) {
        WgsEvent<GradeSubmissionPayload> event = WgsEvent.of(WgsEventAction.GRADE_SUBMISSION, payload);
        kafkaTemplate.send(topic, payload.submissionId().toString(), event);
        log.info("Published GRADE_SUBMISSION: submissionId={}, topic={}", payload.submissionId(), topic);
    }
}
