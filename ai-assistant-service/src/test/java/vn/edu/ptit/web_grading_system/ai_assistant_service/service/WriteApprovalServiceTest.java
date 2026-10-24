package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.PendingAction;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.PendingActionRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WriteApprovalServiceTest {
    @Test
    void proposingWriteOnlyPersistsSnapshotAndNeverCallsDomainTool() throws Exception {
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.save(Mockito.any())).thenAnswer(invocation -> {
            PendingAction action = invocation.getArgument(0);
            action.setId(UUID.randomUUID());
            return action;
        });
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.getName()).thenReturn("createTestPlan");
        Mockito.when(tool.getDefinition()).thenReturn(new ToolDefinition("createTestPlan", "", "1", "lecturer",
                "write", "plan", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        WriteApprovalService service = new WriteApprovalService(pending,
                Mockito.mock(RunRepository.class), Mockito.mock(ConversationRepository.class), List.of(tool),
                new ObjectMapper(), new GatewayTrustProperties("secret"), null);
        UUID userId = UUID.randomUUID();
        var context = new AgentContext(userId, "lecturer@ptit.edu.vn", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");

        ToolResult result = service.propose(tool, new ObjectMapper().readTree("{\"name\":\"Q1\"}"), context);

        assertTrue(result.isSuccess());
        assertEquals("AWAITING_CONFIRMATION", ((java.util.Map<?, ?>) result.getData()).get("status"));
        Mockito.verify(tool, Mockito.never()).execute(Mockito.any(), Mockito.any());
        Mockito.verify(pending).save(Mockito.argThat(action -> action.getQuestionJson().contains("Q1")
                && action.getStatus().equals("PENDING")));
    }
}
