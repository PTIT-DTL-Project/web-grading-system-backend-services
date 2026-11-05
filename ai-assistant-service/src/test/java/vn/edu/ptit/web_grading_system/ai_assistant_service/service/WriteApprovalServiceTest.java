package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.PendingAction;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.PendingActionRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WriteApprovalServiceTest {
    @Test
    void failedWriteDoesNotAdvanceOrderedPlan() {
        UUID userId = UUID.randomUUID(), conversationId = UUID.randomUUID(), runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        PendingAction action = PendingAction.builder().id(UUID.randomUUID()).runId(runId).taskId(taskId)
                .questionJson("{}").optionsJson("{\"toolName\":\"publishAssignment\"}")
                .status("PENDING").expiresAt(Instant.now().plusSeconds(600)).build();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.findLockedById(action.getId())).thenReturn(java.util.Optional.of(action));
        RunRepository runs = Mockito.mock(RunRepository.class);
        Mockito.when(runs.findById(runId)).thenReturn(java.util.Optional.of(
                Run.builder().id(runId).conversationId(conversationId).build()));
        ConversationRepository conversations = Mockito.mock(ConversationRepository.class);
        Mockito.when(conversations.findByIdAndOwnerId(conversationId, userId)).thenReturn(java.util.Optional.of(
                Conversation.builder().id(conversationId).ownerId(userId).build()));
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.getName()).thenReturn("publishAssignment");
        Mockito.when(tool.getDefinition()).thenReturn(new ToolDefinition("publishAssignment", "", "1", "lecturer",
                "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        Mockito.when(tool.execute(Mockito.any(), Mockito.any()))
                .thenReturn(ToolResult.error("BACKEND_FAILED", "failed", false));
        org.springframework.transaction.PlatformTransactionManager transactions =
                Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        Mockito.when(transactions.getTransaction(Mockito.any())).thenAnswer(call ->
                new org.springframework.transaction.support.SimpleTransactionStatus());
        WriteApprovalService service = new WriteApprovalService(pending, runs, conversations, List.of(tool),
                new ObjectMapper(), new GatewayTrustProperties("secret"), transactions);
        OrderedWritePlanService plans = Mockito.mock(OrderedWritePlanService.class);
        service.setOrderedPlans(plans);

        Object result = service.confirm(action.getId(), new vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal(
                userId, "", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_LECTURER"))));

        assertEquals("FAILED", ((java.util.Map<?, ?>) result).get("status"));
        assertEquals("FAILED", action.getStatus());
        Mockito.verify(plans, Mockito.never()).advance(Mockito.any(), Mockito.anyString(), Mockito.any());
    }

    @Test
    void proposalForOrderedPlanIsLinkedOnlyToCurrentWriter() throws Exception {
        UUID conversationId = UUID.randomUUID();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        AgentTool publish = Mockito.mock(AgentTool.class);
        Mockito.when(publish.getName()).thenReturn("publishAssignment");
        Mockito.when(publish.getDefinition()).thenReturn(new ToolDefinition("publishAssignment", "", "1", "lecturer",
                "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        WriteApprovalService service = new WriteApprovalService(pending, Mockito.mock(RunRepository.class),
                Mockito.mock(ConversationRepository.class), List.of(publish), new ObjectMapper(),
                new GatewayTrustProperties("secret"), null);
        OrderedWritePlanService plans = Mockito.mock(OrderedWritePlanService.class);
        UUID taskId = UUID.randomUUID();
        Mockito.when(plans.active(Mockito.any())).thenReturn(java.util.Optional.of(
                new OrderedWritePlanService.Plan(taskId, "publish then create",
                        List.of("publishAssignment", "createTestPlan"), 0)));
        service.setOrderedPlans(plans);
        Mockito.when(pending.save(Mockito.any(PendingAction.class))).thenAnswer(call -> {
            PendingAction action = call.getArgument(0);
            action.setId(UUID.randomUUID());
            return action;
        });
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                conversationId, UUID.randomUUID(), "secret");

        ToolResult result = service.propose(publish, new ObjectMapper().readTree("{\"assignmentId\":\"a\"}"), context);

        assertTrue(result.isSuccess());
        org.mockito.ArgumentCaptor<PendingAction> saved = org.mockito.ArgumentCaptor.forClass(PendingAction.class);
        Mockito.verify(pending).save(saved.capture());
        assertEquals(taskId, saved.getValue().getTaskId());
    }

    @Test
    void doesNotCreateASecondPendingWriteBeforeTheFirstIsResolved() throws Exception {
        UUID conversationId = UUID.randomUUID();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.findPendingByConversationId(conversationId)).thenReturn(List.of(
                PendingAction.builder().status("PENDING").expiresAt(Instant.now().plusSeconds(600)).build()));
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.getDefinition()).thenReturn(new ToolDefinition("createTestPlan", "", "1", "lecturer",
                "write", "plan", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        WriteApprovalService service = new WriteApprovalService(pending, Mockito.mock(RunRepository.class),
                Mockito.mock(ConversationRepository.class), List.of(tool), new ObjectMapper(),
                new GatewayTrustProperties("secret"), null);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                conversationId, UUID.randomUUID(), "secret");

        ToolResult result = service.propose(tool, new ObjectMapper().readTree("{}"), context);

        assertFalse(result.isSuccess());
        assertEquals("PENDING_ACTION_EXISTS", result.getCode());
        Mockito.verify(pending, Mockito.never()).save(Mockito.any());
    }

    @Test
    void chatConfirmationExecutesOnlyAnEarlierPendingActionOwnedByTheCaller() throws Exception {
        UUID userId = UUID.randomUUID(), conversationId = UUID.randomUUID();
        UUID proposalRunId = UUID.randomUUID(), replyRunId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        PendingAction action = PendingAction.builder().id(UUID.randomUUID()).runId(proposalRunId).taskId(taskId)
                .questionJson("{\"name\":\"Q1\"}").optionsJson("{\"toolName\":\"createTestPlan\"}")
                .status("PENDING").expiresAt(Instant.now().plusSeconds(600)).build();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.findPendingByConversationId(conversationId)).thenReturn(List.of(action));
        Mockito.when(pending.findLockedById(action.getId())).thenReturn(java.util.Optional.of(action));
        RunRepository runs = Mockito.mock(RunRepository.class);
        Mockito.when(runs.findById(proposalRunId)).thenReturn(java.util.Optional.of(
                Run.builder().id(proposalRunId).conversationId(conversationId).build()));
        ConversationRepository conversations = Mockito.mock(ConversationRepository.class);
        Mockito.when(conversations.findByIdAndOwnerId(conversationId, userId)).thenReturn(java.util.Optional.of(
                Conversation.builder().id(conversationId).ownerId(userId).build()));
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.getName()).thenReturn("createTestPlan");
        Mockito.when(tool.getDescription()).thenReturn("Tạo câu kiểm tra");
        Mockito.when(tool.getDefinition()).thenReturn(new ToolDefinition("createTestPlan", "", "1", "lecturer",
                "write", "plan", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        Mockito.when(tool.execute(Mockito.any(), Mockito.any())).thenReturn(ToolResult.ok("created"));
        org.springframework.transaction.PlatformTransactionManager transactions =
                Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        Mockito.when(transactions.getTransaction(Mockito.any())).thenAnswer(invocation ->
                new org.springframework.transaction.support.SimpleTransactionStatus());
        WriteApprovalService service = new WriteApprovalService(pending, runs, conversations, List.of(tool),
                new ObjectMapper(), new GatewayTrustProperties("secret"), transactions);
        OrderedWritePlanService plans = Mockito.mock(OrderedWritePlanService.class);
        service.setOrderedPlans(plans);
        AgentContext context = new AgentContext(userId, "lecturer@ptit.edu.vn", Set.of("ROLE_LECTURER"),
                conversationId, replyRunId, "secret");

        var pendingForChat = service.pendingForChat(context).orElseThrow();
        assertEquals(action.getId(), pendingForChat.id());
        Object result = service.confirmFromChat(pendingForChat.id(), context);

        assertEquals("COMPLETED", ((java.util.Map<?, ?>) result).get("status"));
        assertEquals("COMPLETED", action.getStatus());
        Mockito.verify(plans).advance(taskId, "createTestPlan", "created");
        Mockito.verify(tool).execute(Mockito.any(), Mockito.any());
        org.mockito.ArgumentCaptor<org.springframework.transaction.TransactionDefinition> definitions =
                org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        Mockito.verify(transactions, Mockito.times(2)).getTransaction(definitions.capture());
        assertTrue(definitions.getAllValues().stream().allMatch(definition ->
                definition.getPropagationBehavior() == org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void chatCannotConfirmAnActionProposedInTheSameRun() {
        UUID userId = UUID.randomUUID(), conversationId = UUID.randomUUID(), runId = UUID.randomUUID();
        PendingAction action = PendingAction.builder().id(UUID.randomUUID()).runId(runId)
                .questionJson("{}").optionsJson("{\"toolName\":\"createTestPlan\"}")
                .status("PENDING").expiresAt(Instant.now().plusSeconds(600)).build();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.findPendingByConversationId(conversationId)).thenReturn(List.of(action));
        ConversationRepository conversations = Mockito.mock(ConversationRepository.class);
        Mockito.when(conversations.findByIdAndOwnerId(conversationId, userId)).thenReturn(java.util.Optional.of(
                Conversation.builder().id(conversationId).ownerId(userId).build()));
        WriteApprovalService service = new WriteApprovalService(pending, Mockito.mock(RunRepository.class),
                conversations, List.of(), new ObjectMapper(), new GatewayTrustProperties("secret"), null);
        AgentContext context = new AgentContext(userId, "", Set.of("ROLE_LECTURER"), conversationId, runId, "secret");

        assertTrue(service.pendingForChat(context).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.confirmFromChat(action.getId(), context));
    }

    @Test
    void rejectingInChatClosesThePendingAction() {
        UUID userId = UUID.randomUUID(), conversationId = UUID.randomUUID(), oldRunId = UUID.randomUUID();
        PendingAction action = PendingAction.builder().id(UUID.randomUUID()).runId(oldRunId).taskId(UUID.randomUUID())
                .questionJson("{}").optionsJson("{\"toolName\":\"createTestPlan\"}")
                .status("PENDING").expiresAt(Instant.now().plusSeconds(600)).build();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.findPendingByConversationId(conversationId)).thenReturn(List.of(action));
        Mockito.when(pending.findLockedById(action.getId())).thenReturn(java.util.Optional.of(action));
        RunRepository runs = Mockito.mock(RunRepository.class);
        Mockito.when(runs.findById(oldRunId)).thenReturn(java.util.Optional.of(
                Run.builder().id(oldRunId).conversationId(conversationId).build()));
        ConversationRepository conversations = Mockito.mock(ConversationRepository.class);
        Mockito.when(conversations.findByIdAndOwnerId(conversationId, userId)).thenReturn(java.util.Optional.of(
                Conversation.builder().id(conversationId).ownerId(userId).build()));
        org.springframework.transaction.PlatformTransactionManager transactions =
                Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        Mockito.when(transactions.getTransaction(Mockito.any())).thenReturn(
                new org.springframework.transaction.support.SimpleTransactionStatus());
        WriteApprovalService service = new WriteApprovalService(pending, runs, conversations, List.of(),
                new ObjectMapper(), new GatewayTrustProperties("secret"), transactions);
        OrderedWritePlanService plans = Mockito.mock(OrderedWritePlanService.class);
        service.setOrderedPlans(plans);
        AgentContext context = new AgentContext(userId, "", Set.of("ROLE_LECTURER"),
                conversationId, UUID.randomUUID(), "secret");

        service.rejectFromChat(action.getId(), context);

        assertEquals("REJECTED", action.getStatus());
        Mockito.verify(plans, Mockito.never()).advance(Mockito.any(), Mockito.anyString(), Mockito.any());
        org.mockito.ArgumentCaptor<org.springframework.transaction.TransactionDefinition> definition =
                org.mockito.ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        Mockito.verify(transactions).getTransaction(definition.capture());
        assertEquals(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior());
    }

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

    @Test
    void supersedesPendingActionFromPriorRunWhenNewWriteIsProposed() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID oldRunId = UUID.randomUUID();
        UUID newRunId = UUID.randomUUID();
        PendingAction oldAction = PendingAction.builder().id(UUID.randomUUID()).runId(oldRunId)
                .status("PENDING").expiresAt(Instant.now().plusSeconds(600)).build();
        PendingActionRepository pending = Mockito.mock(PendingActionRepository.class);
        Mockito.when(pending.findPendingByConversationId(conversationId)).thenReturn(List.of(oldAction));
        Mockito.when(pending.save(Mockito.any())).thenAnswer(invocation -> {
            PendingAction a = invocation.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        AgentTool tool = Mockito.mock(AgentTool.class);
        Mockito.when(tool.getName()).thenReturn("createTestQuestion");
        Mockito.when(tool.getDefinition()).thenReturn(new ToolDefinition("createTestQuestion", "", "1", "lecturer",
                "write", "plan", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        WriteApprovalService service = new WriteApprovalService(pending,
                Mockito.mock(RunRepository.class), Mockito.mock(ConversationRepository.class), List.of(tool),
                new ObjectMapper(), new GatewayTrustProperties("secret"), null);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                conversationId, newRunId, "secret");

        ToolResult result = service.propose(tool, new ObjectMapper().readTree("{}"), context);

        assertTrue(result.isSuccess());
        assertEquals("AWAITING_CONFIRMATION", ((java.util.Map<?, ?>) result.getData()).get("status"));
        assertEquals("SUPERSEDED", oldAction.getStatus());
        Mockito.verify(pending).save(oldAction);
    }
}
