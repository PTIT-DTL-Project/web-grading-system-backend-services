package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunEvent;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunEventRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RunTimelineServiceTest {
    @Test
    void persistsMaskedReasoningBeforeBroadcast() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RunEventRepository repository = mock(RunEventRepository.class);
        SseBroadcaster broadcaster = mock(SseBroadcaster.class);
        UUID runId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(runId))).thenReturn(2);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RunTimelineService service = new RunTimelineService(jdbc, repository, broadcaster, mapper);

        RunTimelineEvent event = service.append(runId, "reasoning.delta", 1, "turn-1",
                mapper.readTree("{\"text\":\"Bearer abc123 and password=secret\"}"));

        assertEquals(2, event.sequence());
        assertFalse(event.data().toString().contains("abc123"));
        assertFalse(event.data().toString().contains("secret"));
        RunTimelineEvent keyEvent = service.append(runId, "tool.completed", 1, "tool-1",
                mapper.readTree("{\"result\":\"gsk_abcdef123456789\"}"));
        assertFalse(keyEvent.data().toString().contains("gsk_abcdef123456789"));
        var order = inOrder(repository, broadcaster);
        order.verify(repository).save(any(RunEvent.class));
        order.verify(broadcaster).sendTimelineEvent(eq(runId), eq(event));
    }
}
