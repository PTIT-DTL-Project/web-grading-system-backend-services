package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.CreateConversationRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.SendMessageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.*;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void testSendMessageRequestAndResponse() throws Exception {
        String json = """
                {"text":"Tạo bài tập REST API","clientMessageId":"msg-1"}
                """;
        SendMessageRequest req = objectMapper.readValue(json, SendMessageRequest.class);
        assertEquals("Tạo bài tập REST API", req.getText());
        assertEquals("msg-1", req.getClientMessageId());

        UUID convId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        SendMessageResponse resp = new SendMessageResponse(convId, runId, msgId);
        String out = objectMapper.writeValueAsString(resp);
        assertNotNull(out);

        ConversationResponse convResp = new ConversationResponse(convId, UUID.randomUUID(), "Title", null, Instant.now(), Instant.now());
        assertNotNull(objectMapper.writeValueAsString(convResp));

        RunResponse runResp = new RunResponse(runId, convId, RunStatus.QUEUED, null, null, null, null);
        assertNotNull(objectMapper.writeValueAsString(runResp));

        CreateConversationRequest createReq = new CreateConversationRequest("New Title");
        assertEquals("New Title", createReq.getTitle());
    }
}
