package vn.edu.ptit.web_grading_system.ai_assistant_service.domain;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.ConversationSummary;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.Operation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.OperationStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunEvent;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.ToolExecution;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.PendingAction;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EntityMappingTest {

    @Test
    void testEntityInstantiationAndAccessors() {
        UUID convId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Conversation conv = Conversation.builder()
                .id(convId)
                .ownerId(ownerId)
                .title("Test Conversation")
                .build();
        assertNotNull(conv);
        assertEquals(convId, conv.getId());
        assertEquals("Test Conversation", conv.getTitle());

        Message msg = Message.builder()
                .conversationId(convId)
                .role("user")
                .content("Xin chao")
                .sequenceNo(1)
                .clientMessageId("client-1")
                .build();
        assertEquals("user", msg.getRole());
        assertEquals(1, msg.getSequenceNo());

        Run run = Run.builder()
                .conversationId(convId)
                .status(RunStatus.QUEUED)
                .version(1)
                .build();
        assertEquals(RunStatus.QUEUED, run.getStatus());

        Task task = Task.builder()
                .conversationId(convId)
                .ownerId(ownerId)
                .type("CREATE_ASSIGNMENT")
                .status("ACTIVE")
                .build();
        assertEquals("CREATE_ASSIGNMENT", task.getType());

        Operation op = Operation.builder()
                .idempotencyKey("idem-123")
                .commandType("CREATE_ASSIGNMENT")
                .payloadJson("{\"title\":\"Assignment 1\"}")
                .status(OperationStatus.PENDING)
                .build();
        assertEquals("idem-123", op.getIdempotencyKey());
        assertEquals(OperationStatus.PENDING, op.getStatus());

        RunEvent event = RunEvent.builder()
                .runId(run.getId())
                .sequence(1)
                .type("assistant.delta")
                .payloadJson("{\"text\":\"hello\"}")
                .build();
        assertEquals("assistant.delta", event.getType());

        ToolExecution toolExec = ToolExecution.builder()
                .toolName("listMyClasses")
                .status("COMPLETED")
                .latencyMs(150)
                .build();
        assertEquals("listMyClasses", toolExec.getToolName());

        PendingAction pendingAction = PendingAction.builder()
                .type("QUESTION")
                .status("PENDING")
                .build();
        assertEquals("QUESTION", pendingAction.getType());

        ConversationSummary summary = ConversationSummary.builder()
                .conversationId(convId)
                .throughMessageId(msg.getId())
                .summary("Tom tat hoi thoai")
                .build();
        assertEquals("Tom tat hoi thoai", summary.getSummary());
    }
}
