package vn.edu.ptit.web_grading_system.executor_service.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.event.EventHandler;
import vn.edu.ptit.web_grading_system.executor_service.event.WgsEventAction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WgsEventsConsumerTest {

    @Test
    void consume_routesToHandlerByAction() {
        EventHandler handler = Mockito.mock(EventHandler.class);
        Mockito.when(handler.action()).thenReturn(WgsEventAction.GRADE_SUBMISSION);
        WgsEventsConsumer consumer = new WgsEventsConsumer(new ObjectMapper(), List.of(handler));

        consumer.consume(new ConsumerRecord<>("wgs-events", 0, 0L, "k",
                "{\"action\":\"GRADE_SUBMISSION\",\"payload\":{\"submissionId\":\"x\"}}"));

        Mockito.verify(handler).handle(Mockito.any(), Mockito.isNull());
    }

    @Test
    void consume_unknownAction_skipsWithoutThrowing() {
        WgsEventsConsumer consumer = new WgsEventsConsumer(new ObjectMapper(), List.of());
        assertDoesNotThrow(() -> consumer.consume(new ConsumerRecord<>("wgs-events", 0, 0L, "k",
                "{\"action\":\"FUTURE_THING\",\"payload\":{}}")));
    }

    @Test
    void consume_noAction_defaultsToGradeSubmission() {
        EventHandler handler = Mockito.mock(EventHandler.class);
        Mockito.when(handler.action()).thenReturn(WgsEventAction.GRADE_SUBMISSION);
        WgsEventsConsumer consumer = new WgsEventsConsumer(new ObjectMapper(), List.of(handler));

        consumer.consume(new ConsumerRecord<>("wgs-events", 0, 0L, "k", "{\"payload\":{}}"));

        Mockito.verify(handler).handle(Mockito.any(), Mockito.isNull());
    }
}