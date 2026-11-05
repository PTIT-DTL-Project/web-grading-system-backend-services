package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.PageImpl;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationSummarizer;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiProviderException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ToolResultStore;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.WriteApprovalService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRuntimeTest {
    @Test
    void assignmentCreatedMostRecentlyIsPublishTargetNotNewCreation() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool publish = mock(AgentTool.class), create = mock(AgentTool.class);
        for (var entry : java.util.Map.of(publish, "publishAssignment", create, "createAssignment").entrySet()) {
            AgentTool tool = entry.getKey();
            String name = entry.getValue();
            when(tool.getName()).thenReturn(name);
            when(tool.getDefinition()).thenReturn(new ToolDefinition(name, name, "1", "lecturer",
                    "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
            when(tool.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name(name).description(name).inputSchema("{\"type\":\"object\"}").build());
        }
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.propose(eq(publish), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "publishAssignment", "arguments", mapper.createObjectNode())));
        ToolRegistry registry = new ToolRegistry(List.of(publish, create), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("công bố bài mới nhất")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("publishAssignment"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            List<ToolCallback> callbacks = call.getArgument(2);
            assertFalse(prompt.contains("YÊU CẦU HIỆN TẠI: tạo BÀI TẬP"));
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("publishAssignment")));
            assertFalse(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createAssignment")));
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("publishAssignment"))
                    .findFirst().orElseThrow().call("{\"assignmentId\":\"abc\"}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "công bố cho t bài tập tạo gần nhất cửa lớp test", llm,
                delta -> {}, (tool, result) -> {});

        verify(approvals).propose(eq(publish), any(), eq(context));
        verify(approvals, never()).propose(eq(create), any(), any());
    }

    @Test
    void publishingClarificationDoesNotResumeOlderAssignmentCreation() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool publish = mock(AgentTool.class), create = mock(AgentTool.class);
        for (var entry : java.util.Map.of(publish, "publishAssignment", create, "createAssignment").entrySet()) {
            AgentTool tool = entry.getKey();
            String name = entry.getValue();
            when(tool.getName()).thenReturn(name);
            when(tool.getDescription()).thenReturn(name);
            when(tool.getDefinition()).thenReturn(new ToolDefinition(name, name, "1", "lecturer",
                    "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
            when(tool.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name(name).description(name).inputSchema("{\"type\":\"object\"}").build());
        }
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.propose(eq(publish), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "publishAssignment", "arguments", mapper.createObjectNode())));
        ToolRegistry registry = new ToolRegistry(List.of(publish, create), mapper, approvals);
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.findScopedRecent(any(), anyString(), any())).thenReturn(new PageImpl<>(List.of(
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("tạo bài tập Quản lý Sinh viên").sequenceNo(1).build(),
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("công bố bài tập tạo gần nhất của lớp Test").sequenceNo(2).build(),
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("assistant").content("Bạn nói rõ tên lớp nhé.").sequenceNo(3).build(),
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("lớp test ấy").sequenceNo(4).build())));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("lớp test ấy")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyCreationContinuation(anyString(), anyString(), anyString())).thenReturn(true);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("publishAssignment"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            List<ToolCallback> callbacks = call.getArgument(2);
            assertFalse(prompt.contains("YÊU CẦU HIỆN TẠI: tạo BÀI TẬP"));
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("publishAssignment")));
            assertFalse(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createAssignment")));
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("publishAssignment"))
                    .findFirst().orElseThrow().call("{\"assignmentId\":\"abc\"}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), messages, mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "lớp test ấy", llm, delta -> {}, (tool, result) -> {});

        verify(approvals).propose(eq(publish), any(), eq(context));
        verify(approvals, never()).propose(eq(create), any(), any());
    }

    @Test
    void confirmedOrderedStepOffersNextWriterDespiteOldCreationIntent() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool publish = mock(AgentTool.class), create = mock(AgentTool.class);
        for (var entry : java.util.Map.of(publish, "publishAssignment", create, "createTestPlan").entrySet()) {
            AgentTool tool = entry.getKey();
            String name = entry.getValue();
            when(tool.getName()).thenReturn(name);
            when(tool.getDefinition()).thenReturn(new ToolDefinition(name, name, "1", "lecturer",
                    "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
            when(tool.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name(name).description(name).inputSchema("{\"type\":\"object\"}").build());
        }
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        UUID taskId = UUID.randomUUID(), actionId = UUID.randomUUID();
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(
                new WriteApprovalService.ChatPendingAction(actionId, UUID.randomUUID(), taskId,
                        "publishAssignment", "Công bố bài tập", mapper.createObjectNode())));
        when(approvals.confirmFromChat(eq(actionId), any())).thenReturn(java.util.Map.of(
                "status", "COMPLETED", "result", ToolResult.ok(java.util.Map.of(
                        "published", true, "id", "assignment-from-previous-step"))));
        when(approvals.activeWritePlan(any())).thenReturn(java.util.Optional.of(
                new vn.edu.ptit.web_grading_system.ai_assistant_service.service.OrderedWritePlanService.Plan(
                        taskId, "công bố rồi tạo câu kiểm tra", List.of("publishAssignment", "createTestPlan"), 1,
                        List.of(mapper.valueToTree(java.util.Map.of("tool", "publishAssignment",
                                "data", java.util.Map.of("id", "stored-first-result")))))));
        when(approvals.propose(eq(create), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "createTestPlan", "arguments", mapper.createObjectNode())));
        ToolRegistry registry = new ToolRegistry(List.of(publish, create), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("ok")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("publishAssignment", "createTestPlan"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            assertTrue(((String) call.getArgument(0)).contains("assignment-from-previous-step"));
            assertTrue(((String) call.getArgument(0)).contains("stored-first-result"));
            List<ToolCallback> callbacks = call.getArgument(2);
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createTestPlan")));
            assertFalse(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("publishAssignment")));
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("createTestPlan"))
                    .findFirst().orElseThrow().call("{\"name\":\"Q1\"}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "ok", llm, delta -> {}, (tool, result) -> {});

        verify(approvals).confirmFromChat(eq(actionId), eq(context));
        verify(approvals).propose(eq(create), any(), eq(context));
    }

    @Test
    void orderedPlanOffersOnlyItsFirstWriterForGenericMixedRequest() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool first = mock(AgentTool.class), second = mock(AgentTool.class);
        for (var entry : java.util.Map.of(first, "publishAssignment", second, "createTestPlan").entrySet()) {
            AgentTool tool = entry.getKey();
            String name = entry.getValue();
            when(tool.getName()).thenReturn(name);
            when(tool.getDefinition()).thenReturn(new ToolDefinition(name, name, "1", "lecturer",
                    "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
            when(tool.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name(name).description(name).inputSchema("{\"type\":\"object\"}").build());
        }
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        UUID planId = UUID.randomUUID();
        when(approvals.startWritePlan(any(), anyString(), anyList())).thenReturn(java.util.Optional.of(
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task.builder().id(planId).build()));
        when(approvals.activeWritePlan(any())).thenReturn(java.util.Optional.empty(), java.util.Optional.of(
                new vn.edu.ptit.web_grading_system.ai_assistant_service.service.OrderedWritePlanService.Plan(
                        planId, "công bố rồi tạo câu", List.of("publishAssignment", "createTestPlan"), 0)));
        when(approvals.propose(eq(first), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "publishAssignment", "arguments", mapper.createObjectNode())));
        ToolRegistry registry = new ToolRegistry(List.of(first, second), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("công bố rồi tạo câu")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("publishAssignment", "createTestPlan"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("publishAssignment")));
            assertFalse(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createTestPlan")));
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("publishAssignment"))
                    .findFirst().orElseThrow().call("{\"assignmentId\":\"abc\"}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "công bố rồi tạo câu kiểm tra", llm,
                delta -> {}, (tool, result) -> {});

        verify(approvals).startWritePlan(eq(context), anyString(), eq(List.of("publishAssignment", "createTestPlan")));
    }

    @Test
    void newQuestionDoesNotResumeEarlierCreationTask() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.findScopedRecent(any(), anyString(), any())).thenReturn(new PageImpl<>(List.of(
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("Tạo bước chấm cho câu kiểm tra Q1").sequenceNo(1).build(),
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("bạn có thể làm gì cho tôi?").sequenceNo(2).build())));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("bạn có thể làm gì cho tôi?")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            assertFalse(prompt.contains("YÊU CẦU ĐANG TIẾP NỐI"));
            assertFalse(prompt.contains("YÊU CẦU HIỆN TẠI: tạo BƯỚC CHẤM"));
            return Flux.just("Tôi có thể giúp bạn quản lý bài tập và bước chấm.");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages, mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Tôi có thể giúp bạn quản lý bài tập và bước chấm.", runtime.execute(context,
                "system", "bạn có thể làm gì cho tôi?", llm, delta -> {}, (tool, result) -> {}));
        verify(llm).classifyCreationContinuation(eq("bạn có thể làm gì cho tôi?"),
                eq("Tạo bước chấm cho câu kiểm tra Q1"), contains("Tạo bước chấm"));
    }

    @Test
    void explicitContinuationResumesEarlierCreationTaskWhenModelChoosesIt() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.findScopedRecent(any(), anyString(), any())).thenReturn(new PageImpl<>(List.of(
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("Tạo bước chấm cho câu kiểm tra Q1").sequenceNo(1).build(),
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("làm tiếp").sequenceNo(2).build())));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("làm tiếp")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyCreationContinuation(anyString(), anyString(), anyString())).thenReturn(true);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            assertTrue(prompt.contains("YÊU CẦU ĐANG TIẾP NỐI"));
            assertTrue(prompt.contains("YÊU CẦU HIỆN TẠI: tạo BƯỚC CHẤM"));
            return Flux.just("Đang tiếp tục tạo bước chấm.");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages, mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đang tiếp tục tạo bước chấm.", runtime.execute(context,
                "system", "làm tiếp", llm, delta -> {}, (tool, result) -> {}));
    }

    @Test
    void liveRunEmitsReasoningTurnAroundProviderChunks() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of());
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), nullable(String.class), any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<String> reasoning = call.getArgument(4);
                    return Flux.defer(() -> {
                        reasoning.accept("Check the data.");
                        return Flux.just("Final answer.");
                    });
                });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        List<AgentRuntime.RuntimeEvent> events = new java.util.ArrayList<>();

        String answer = runtime.execute(context, "system", "question", llm, delta -> {},
                (tool, result) -> {}, events::add, () -> false);

        assertEquals("Final answer.", answer);
        assertEquals(List.of("reasoning.started", "reasoning.delta", "reasoning.completed"),
                events.stream().map(AgentRuntime.RuntimeEvent::type).toList());
        assertEquals("Check the data.", events.get(1).data().get("text"));
    }

    @Test
    void ordinaryWordContainingIdDoesNotTriggerReadPlanner() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                readWithResult("listVisibleAssignments", ToolResult.ok(java.util.Map.of("items", List.of())))), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listVisibleAssignments"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.just("Cấu hình valid và cần kiểm tra thêm."));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "kiểm tra cấu hình", llm, delta -> {}, (tool, result) -> {});

        verify(llm, never()).generateStrictCompletion(anyString(), anyString());
    }

    @Test
    void initialCallbacksFollowModelSelectionWithoutFixedReadSequence() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("listVisibleAssignments", EffectType.READ, Set.of("assignmentId"), Set.of()),
                lecturerTool("listTestPlans", EffectType.READ, Set.of("planId"), Set.of("assignmentId")),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("tạo step")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenAnswer(call -> {
            assertTrue(((String) call.getArgument(0)).startsWith("Current user request:"));
            return List.of("createTestStep");
        });
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            Set<String> names = ((List<ToolCallback>) call.getArgument(2)).stream()
                    .map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("createTestStep"));
            assertFalse(names.contains("listVisibleAssignments"));
            assertFalse(names.contains("listTestPlans"));
            return Flux.just("Bạn cho mình biết path API nhé.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "tạo step cho câu kiểm tra", llm,
                delta -> {}, (tool, result) -> {});
    }

    @Test
    void shortFollowUpContinuesLastExplicitStepCreationRequest() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("listVisibleAssignments", EffectType.READ, Set.of("assignmentId"), Set.of()),
                lecturerTool("listTestPlans", EffectType.READ, Set.of("planId"), Set.of("assignmentId")),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("follow up")), "", 10, 10, 30));
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        MessageRepository messages = mock(MessageRepository.class);
        var previous = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .sequenceNo(1).role("user")
                .content("tạo step kiểm tra của câu Kiểm tra API thêm sách của bài Quản lý Thư viện").build();
        var latest = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .sequenceNo(2).role("user").content("bạn tự tìm id và tạo giúp t").build();
        when(messages.findScopedRecent(eq(context.conversationId()), eq("ROLE_LECTURER"), any()))
                .thenReturn(new PageImpl<>(List.of(latest, previous)));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyCreationContinuation(eq("bạn tự tìm id và tạo giúp t"),
                eq("tạo step kiểm tra của câu Kiểm tra API thêm sách của bài Quản lý Thư viện"), anyString()))
                .thenReturn(true);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("listVisibleAssignments", "listTestPlans", "createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            assertTrue(((String) call.getArgument(0)).contains("YÊU CẦU HIỆN TẠI: tạo BƯỚC CHẤM"));
            assertTrue(((String) call.getArgument(0)).contains("chỉ chuẩn bị một bước kế tiếp"));
            return Flux.just("Cần path API để tạo bước chấm.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages, mapper, props);

        assertEquals("Cần path API để tạo bước chấm.", runtime.execute(context, "system",
                "bạn tự tìm id và tạo giúp t", llm, delta -> {}, (tool, result) -> {}));
    }

    @Test
    void pagedReadRecoversPageZeroBeforeModelContinues() {
        var mapper = new JacksonConfig().objectMapper();
        AtomicInteger reads = new AtomicInteger();
        AgentTool assignments = new AgentTool() {
            public String getName() { return "listVisibleAssignments"; }
            public String getDescription() { return "List assignments by title"; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(getName(), getDescription(), "1", "lecturer", "read", "assignment",
                        EffectType.READ, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) {
                reads.incrementAndGet();
                int page = args.path("page").asInt();
                return ToolResult.ok(java.util.Map.of("page", page, "totalPages", 1, "totalElements", 2,
                        "items", page == 0 ? List.of(
                                java.util.Map.of("id", "a1", "title", "Quản lý Thư viện"),
                                java.util.Map.of("id", "a2", "title", "Quản lý Thư viện – Ứng dụng Web"))
                                : List.of()));
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(assignments), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.copyOf(call.getArgument(5, List.class)), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listVisibleAssignments"));
        AtomicInteger turns = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            if (turns.getAndIncrement() == 0) {
                List<ToolCallback> callbacks = call.getArgument(2);
                return Flux.just(callbacks.stream()
                        .filter(cb -> cb.getToolDefinition().name().equals("listVisibleAssignments"))
                        .findFirst().orElseThrow().call("{\"page\":1,\"size\":10,\"search\":\"Quản lý Thư viện\"}"));
            }
            List<org.springframework.ai.chat.messages.Message> supplied = call.getArgument(1);
            assertTrue(supplied.stream().anyMatch(m -> m.getText().contains("\"requestedPage\":1")));
            assertTrue(supplied.stream().anyMatch(m -> m.getText().contains("\"title\":\"Quản lý Thư viện\"")));
            return Flux.just("Đã tìm thấy bài tập đúng tên.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đã tìm thấy bài tập đúng tên.", runtime.execute(context, "system",
                "Tìm bài tập Quản lý Thư viện", llm, delta -> {}, (tool, result) -> {}));
        assertEquals(2, reads.get());
    }

    @Test
    void emptyFinalModelResponseGetsOneTextOnlyRetry() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 0, 20));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of());
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.empty(), Flux.just("Chưa tạo được bước vì thiếu path API."));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(0);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Chưa tạo được bước vì thiếu path API.", runtime.execute(context,
                "system", "tạo step", llm, delta -> {}, (tool, result) -> {}));
        verify(llm, times(2)).streamAgentTurn(anyString(), anyList(), anyList());
    }

    @Test
    void twoEmptyFinalResponsesFailWithoutClaimingWriteSucceeded() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("request")), "", 10, 0, 20));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of());
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(Flux.empty());
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(0);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        java.util.List<String> deltas = new java.util.ArrayList<>();

        assertThrows(AgentRuntime.EmptyModelResponseException.class,
                () -> runtime.execute(context, "system", "tạo step", llm, deltas::add, (tool, result) -> {}));
        assertTrue(deltas.isEmpty());
        verify(llm, times(2)).streamAgentTurn(anyString(), anyList(), anyList());
    }

    @Test
    void nextReadToolIsChosenFromPreviousToolResult() {
        var mapper = new JacksonConfig().objectMapper();
        UUID assignmentId = UUID.randomUUID();
        ToolRegistry registry = new ToolRegistry(List.of(
                readWithResult("listVisibleAssignments", ToolResult.ok(java.util.Map.of("items", List.of(
                        java.util.Map.of("id", assignmentId, "title", "Quản lý Thư viện"))))),
                readWithResult("listTestPlans", ToolResult.ok(List.of(
                        java.util.Map.of("id", UUID.randomUUID(), "name", "Kiểm tra API thêm sách")))),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.copyOf(call.getArgument(5, List.class)), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(
                Flux.just("Cần ID của bài tập."), Flux.just("Cần ID của câu kiểm tra."),
                Flux.just("Bạn cho mình biết path API cần kiểm tra nhé."));
        AtomicInteger decisions = new AtomicInteger();
        when(llm.generateStrictCompletion(anyString(), anyString())).thenAnswer(call -> {
            if (decisions.getAndIncrement() == 0) {
                return "{\"tool\":\"listVisibleAssignments\",\"arguments\":{\"search\":\"Quản lý Thư viện\"}}";
            }
            assertTrue(((String) call.getArgument(1)).contains(assignmentId.toString()));
            return "{\"tool\":\"listTestPlans\",\"arguments\":{\"assignmentId\":\""
                    + assignmentId + "\"}}";
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(3);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        List<String> called = new java.util.ArrayList<>();

        String reply = runtime.execute(context, "system", "tạo step cho câu kiểm tra trong bài thư viện", llm,
                delta -> {}, (tool, result) -> called.add(tool.getName()));

        assertEquals(List.of("listVisibleAssignments", "listTestPlans"), called);
        assertEquals("Bạn cho mình biết path API cần kiểm tra nhé.", reply);
        verify(preflight, atLeastOnce()).prepare(any(), any(), anySet(), anyString(), anyList(),
                argThat(inFlight -> inFlight.stream().anyMatch(message ->
                        message.getText().startsWith("Kết quả tool listTestPlans:")
                                && message.getText().contains(assignmentId.toString()))));
    }

    @Test
    void modelChoosesAndExecutesReadToolAfterDraftAsksForId() {
        var mapper = new JacksonConfig().objectMapper();
        UUID assignmentId = UUID.randomUUID();
        ToolRegistry registry = new ToolRegistry(List.of(
                readWithResult("listVisibleAssignments", ToolResult.ok(java.util.Map.of("items", List.of(
                        java.util.Map.of("id", assignmentId, "title", "Quản lý Thư viện"))))),
                readWithResult("listTestPlans", ToolResult.ok(List.of())),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.copyOf(call.getArgument(5, List.class)), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.just("Bạn cung cấp ID của câu kiểm tra được không?"),
                        Flux.just("Bạn cho mình biết path API cần kiểm tra nhé."));
        when(llm.generateStrictCompletion(anyString(), anyString())).thenReturn(
                "{\"tool\":\"listVisibleAssignments\",\"arguments\":{\"search\":\"Quản lý Thư viện\"}}");
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(2);
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                store, mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        List<String> called = new java.util.ArrayList<>();

        String reply = runtime.execute(context, "system",
                "tạo step của câu Kiểm tra API thêm sách của bài Quản lý Thư viện",
                llm, delta -> {}, (tool, result) -> called.add(tool.getName()));

        assertEquals(List.of("listVisibleAssignments"), called);
        assertEquals("Bạn cho mình biết path API cần kiểm tra nhé.", reply);
        verify(llm).generateStrictCompletion(anyString(), contains("Quản lý Thư viện"));
    }

    private AgentTool readWithResult(String name, ToolResult result) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return name; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(name, name, "1", "lecturer", "read", "assignment",
                        EffectType.READ, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) { return result; }
        };
    }

    @Test
    void repairsNaturalLanguagePlanIdQuestionForStepCreation() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.copyOf(call.getArgument(5, List.class)), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(
                Flux.just("ID của câu kiểm tra — bạn có thể cung cấp hoặc mình sẽ tra cứu nếu bạn cho phép."),
                Flux.just("Bạn cho mình biết path API của bước chấm nhé."));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "tạo bước chấm cho Kiểm tra API thêm sách", llm,
                delta -> {}, (tool, result) -> {});
        assertEquals("Bạn cho mình biết path API của bước chấm nhé.", reply);
        verify(llm, times(2)).streamAgentTurn(anyString(), anyList(), anyList());
    }

    @Test
    void completedPlanMentionDoesNotOverrideCurrentStepRequest() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage(
                        "Đã tạo câu kiểm tra rồi, giờ tạo bước chấm cho câu đó")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createTestPlan", "createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            Set<String> names = ((List<ToolCallback>) call.getArgument(2)).stream()
                    .map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("createTestStep"));
            assertFalse(names.contains("createTestPlan"));
            return Flux.just("Đang chuẩn bị bước chấm.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "Đã tạo câu kiểm tra rồi, giờ tạo bước chấm cho câu đó", llm,
                delta -> {}, (tool, result) -> {});
    }

    @Test
    void acceptedPlanSuggestionKeepsPlanIntentAndSuggestedValues() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        MessageRepository messages = mock(MessageRepository.class);
        UUID conversationId = UUID.randomUUID();
        var suggestion = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .sequenceNo(30).role("assistant")
                .content("Gợi ý tạo câu kiểm tra Kiểm tra API thêm sách, sequenceOrder 1. Bạn có thể đồng ý theo mẫu.").build();
        var acceptance = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .sequenceNo(31).role("user").content("ok cứ tạo theo mẫu bạn gợi ý").build();
        when(messages.findScopedRecent(eq(conversationId), eq("ROLE_LECTURER"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(acceptance, suggestion)));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("ok cứ tạo theo mẫu bạn gợi ý")),
                        "Chờ tạo bước chấm", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createTestStep", "createTestPlan"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            Set<String> names = ((List<ToolCallback>) call.getArgument(2)).stream()
                    .map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("createTestPlan"));
            assertFalse(names.contains("createTestStep"));
            assertTrue(prompt.contains("sequenceOrder 1"));
            assertFalse(prompt.contains("Chờ tạo bước chấm"));
            return Flux.just("Đang chuẩn bị câu kiểm tra.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages, mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                conversationId, UUID.randomUUID(), "");

        runtime.execute(context, "system", "ok cứ tạo theo mẫu bạn gợi ý", llm,
                delta -> {}, (tool, result) -> {});
    }

    @Test
    void creatingAssignmentKeepsAssignmentWriterWhenSelectorChoosesPlan() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createAssignment", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Tạo bài tập Quản lý Thư viện")),
                        "Chờ tạo câu kiểm tra", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createAssignment", "createTestPlan"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            Set<String> names = ((List<ToolCallback>) call.getArgument(2)).stream()
                    .map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("createAssignment"));
            assertFalse(names.contains("createTestPlan"));
            assertFalse(names.contains("createTestStep"));
            assertFalse(((String) call.getArgument(0)).contains("Chờ tạo câu kiểm tra"));
            return Flux.just("Cần chủ đề bài tập.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "Tạo bài tập Quản lý Thư viện", llm,
                delta -> {}, (tool, result) -> {});
    }

    @Test
    void creatingPlanKeepsPlanWriterAndAssignmentLookupsWhenSelectorChoosesStep() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("listVisibleAssignments", EffectType.READ, Set.of("assignmentId"), Set.of()),
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestQuestion", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Tạo câu kiểm tra API thêm sách")),
                        "Chờ tạo bước chấm", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createTestStep", "createTestPlan", "listVisibleAssignments"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            Set<String> names = ((List<ToolCallback>) call.getArgument(2)).stream()
                    .map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.containsAll(Set.of("listVisibleAssignments", "createTestPlan")));
            assertFalse(names.contains("createTestStep"));
            assertFalse(names.contains("createTestQuestion"));
            assertFalse(((String) call.getArgument(0)).contains("Chờ tạo bước chấm"));
            return Flux.just("Đang chuẩn bị câu kiểm tra.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "Tạo câu kiểm tra API thêm sách", llm,
                delta -> {}, (tool, result) -> {});
    }

    @Test
    void creatingPlanWithStepsKeepsCombinedWriter() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestQuestion", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Tạo câu kiểm tra và các bước chấm")),
                        "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createTestPlan", "createTestQuestion"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            Set<String> names = ((List<ToolCallback>) call.getArgument(2)).stream()
                    .map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("createTestQuestion"));
            assertFalse(names.contains("createTestPlan"));
            assertFalse(names.contains("createTestStep"));
            return Flux.just("Đang chuẩn bị câu kiểm tra và bước chấm.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "Tạo câu kiểm tra và các bước chấm", llm,
                delta -> {}, (tool, result) -> {});
    }

    @Test
    void repairsPlanClarificationOnceForExplicitStepRequest() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.copyOf(call.getArgument(5, List.class)), "Chờ tạo câu kiểm tra", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestPlan"));
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            if (calls.getAndIncrement() == 0) return Flux.just("Để tạo câu kiểm tra, hãy cho biết name và sequenceOrder.");
            List<org.springframework.ai.chat.messages.Message> history = call.getArgument(1);
            assertTrue(history.stream().anyMatch(message -> message.getText().contains("bước chấm")));
            return Flux.just("Bạn cho mình biết path API của bước chấm nhé.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(3);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "tạo bước chấm cho Kiểm tra API thêm sách", llm,
                delta -> {}, (tool, result) -> {});
        assertEquals("Bạn cho mình biết path API của bước chấm nhé.", reply);
        assertEquals(2, calls.get());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void toolSelectorProviderFailureIsReportedInsteadOfGuessingTools(CapturedOutput logOutput) {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("tạo bước chấm")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenThrow(new AiProviderException(new RuntimeException("selector unavailable")));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createTestStep")));
            return Flux.just("Cần thông tin bước chấm.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(AiProviderException.class, () -> runtime.execute(context, "system", "tạo bước chấm", llm,
                delta -> {}, (tool, result) -> {}));
        assertTrue(logOutput.getOut().contains("phase=toolSelection errorType=AiProviderException"));
        assertFalse(logOutput.getOut().contains("selector unavailable"));
    }

    @Test
    void explicitStepRequestOverridesStalePlanSelectionAndSummary() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("listVisibleAssignments", EffectType.READ, Set.of("assignmentId"), Set.of()),
                lecturerTool("listTestPlans", EffectType.READ, Set.of("planId"), Set.of("assignmentId")),
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestQuestion", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("tạo bước chấm cho Kiểm tra API thêm sách")),
                        "Đang chờ name và sequenceOrder để tạo câu kiểm tra", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createTestPlan", "createTestQuestion", "createTestStep",
                        "listVisibleAssignments", "listTestPlans"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("required"))).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            assertEquals(1, callbacks.size());
            return Flux.just(callbacks.getFirst().call("{}"));
        });
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            List<ToolCallback> callbacks = call.getArgument(2);
            Set<String> names = callbacks.stream().map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.containsAll(Set.of("listVisibleAssignments", "listTestPlans", "createTestStep")));
            assertFalse(names.contains("createTestPlan"));
            assertFalse(names.contains("createTestQuestion"));
            assertFalse(prompt.contains("Đang chờ name và sequenceOrder"));
            return Flux.just("Đang chuẩn bị bước chấm.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(3);
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                store, mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "tạo bước chấm cho Kiểm tra API thêm sách", llm,
                delta -> {}, (tool, result) -> {});
        verify(llm).streamAgentTurn(anyString(), anyList(), anyList());
    }

    @Test
    void acceptanceOfSuggestedStepKeepsStepIntentInsteadOfCreatingPlan() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                lecturerTool("createTestPlan", EffectType.DOMAIN_WRITE, Set.of(), Set.of()),
                lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of())), mapper);
        MessageRepository messages = mock(MessageRepository.class);
        UUID conversationId = UUID.randomUUID();
        var suggestion = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .sequenceNo(30).role("assistant")
                .content("Để tạo bước chấm cho Kiểm tra API thêm sách: POST /books, expectedStatusCode 201. Bạn chỉ cần cho biết name và config.").build();
        var acceptance = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .sequenceNo(31).role("user").content("ok cứ tạo theo mẫu bạn gợi ý").build();
        when(messages.findScopedRecent(eq(conversationId), eq("ROLE_LECTURER"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(acceptance, suggestion)));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("ok cứ tạo theo mẫu bạn gợi ý")),
                        "Chờ tạo câu kiểm tra", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(List.of("createTestPlan", "createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            List<ToolCallback> callbacks = call.getArgument(2);
            Set<String> names = callbacks.stream().map(cb -> cb.getToolDefinition().name()).collect(java.util.stream.Collectors.toSet());
            assertTrue(names.contains("createTestStep"));
            assertFalse(names.contains("createTestPlan"));
            assertTrue(prompt.contains("expected_status"));
            return Flux.just("Đang chuẩn bị bước chấm.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages, mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                conversationId, UUID.randomUUID(), "");

        runtime.execute(context, "system", "ok cứ tạo theo mẫu bạn gợi ý", llm,
                delta -> {}, (tool, result) -> {});
        verify(llm).streamAgentTurn(anyString(), anyList(), anyList());
    }

    private AgentTool lecturerTool(String name, EffectType effect, Set<String> resolves, Set<String> requires) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return name; }
            public Set<String> resolvedIdentifiers() { return resolves; }
            public Set<String> lookupPrerequisites() { return requires; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(name, name, "1", "lecturer", "write", "assignment", effect,
                        Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok("unused"); }
        };
    }

    @Test
    void reportsUnstoredStepDraftWithoutFalseConfirmation() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = mock(AgentTool.class);
        when(writer.getName()).thenReturn("createTestStep");
        when(writer.getDescription()).thenReturn("Tạo bước chấm");
        when(writer.getDefinition()).thenReturn(new ToolDefinition("createTestStep", "Tạo bước chấm",
                "1", "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        when(writer.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("createTestStep").description("Tạo bước chấm")
                .inputSchema("{\"type\":\"object\"}").build());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.empty());
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage(
                        "tạo giúp tôi step kiểm tra của bài tập 3 câu Kiểm tra mẫu")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.just("Mình sẽ tạo step kiểm tra. Bạn xác nhận nhé?"));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props, approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system",
                "tạo giúp tôi step kiểm tra của bài tập 3 câu Kiểm tra mẫu", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("chưa được lưu"));
        assertTrue(reply.contains("Mình sẽ tạo step kiểm tra"));
        assertFalse(reply.contains("Bạn xác nhận nhé"));
        verify(approvals, never()).propose(any(), any(), any());
    }

    @Test
    void preservesConcreteStepProposalWhenRunEndsWithoutPendingAction() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.empty());
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("thêm bước tiếp")),
                        "", 10, 10, 30));
        String proposal = "Đề xuất bước 3: GET /books, kỳ vọng 200. " + "Chi tiết kiểm tra. ".repeat(40)
                + "Bạn xác nhận để tôi tạo bước này nhé?";
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(Flux.just(proposal));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props, approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "thêm bước tiếp", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("GET /books"));
        assertTrue(reply.contains("chưa được lưu"));
        assertFalse(reply.contains("Bạn xác nhận để tôi tạo"));
        assertFalse(reply.contains("Bạn cho biết bước này cần kiểm tra điều gì"));
        verify(approvals, never()).propose(any(), any(), any());
    }

    @Test
    void preservesShortConcreteStepProposalWithoutPendingAction() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.empty());
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("thêm bước tiếp")),
                        "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.just("Bước 3: chạy SELECT COUNT(*) FROM books, mong đợi 1. Bạn xác nhận để tôi tạo nhé?"));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props, approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "thêm bước tiếp", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("SELECT COUNT(*) FROM books"));
        assertTrue(reply.contains("chưa được lưu"));
        assertFalse(reply.contains("Bạn xác nhận để tôi tạo"));
    }

    @Test
    void keepsSpecificClarificationWhenIdentifierLookupCannotContinue() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool assignments = new AgentTool() {
            public String getName() { return "listVisibleAssignments"; }
            public String getDescription() { return "Liệt kê bài tập"; }
            public Set<String> resolvedIdentifiers() { return Set.of("assignmentId"); }
            public ToolDefinition getDefinition() { return new ToolDefinition(getName(), getDescription(), "1",
                    "lecturer", "read", "assignment", EffectType.READ, Set.of("ROLE_LECTURER"),
                    Duration.ofSeconds(5), 1000); }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok(List.of()); }
        };
        ToolRegistry registry = new ToolRegistry(List.of(assignments), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("bài tập 4")),
                        "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.just("Mình chưa tìm thấy bài tập 4 (assignmentId). Bạn cho biết tên đầy đủ hoặc lớp của bài tập 4 nhé?"));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "bài tập 4", llm, delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("bài tập 4"));
        assertTrue(reply.contains("lớp"));
        assertFalse(reply.contains("assignmentId"));
    }

    @Test
    void resolvesNamedClassForOtherActionsInsteadOfRequestingClassId() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool classes = new AgentTool() {
            public String getName() { return "listMyClasses"; }
            public String getDescription() { return "Liệt kê lớp học"; }
            public Set<String> resolvedIdentifiers() { return Set.of("classId"); }
            public ToolDefinition getDefinition() { return new ToolDefinition(getName(), getDescription(), "1",
                    "student", "read", "class", EffectType.READ, Set.of("ROLE_STUDENT"),
                    Duration.ofSeconds(5), 1000); }
            public ToolResult execute(JsonNode args, AgentContext context) {
                return ToolResult.ok(List.of(java.util.Map.of("id", "class-1", "name", "Lớp mẫu")));
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(classes), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("xem điểm Lớp mẫu")),
                        "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            return switch (calls.getAndIncrement()) {
                case 0 -> Flux.just("Bạn cung cấp classId của Lớp mẫu?");
                case 1 -> Flux.just(callbacks.stream()
                        .filter(cb -> cb.getToolDefinition().name().equals("listMyClasses"))
                        .findFirst().orElseThrow().call("{}"));
                default -> Flux.just("Đã tìm thấy Lớp mẫu.");
            };
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đã tìm thấy Lớp mẫu.", runtime.execute(context, "system", "xem điểm Lớp mẫu",
                llm, delta -> {}, (tool, result) -> {}));
        assertEquals(3, calls.get());
    }

    @Test
    void resolvesNamedAssignmentAndPlanInsteadOfRequestingTheirUuids() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool assignments = new AgentTool() {
            public String getName() { return "listVisibleAssignments"; }
            public String getDescription() { return "Liệt kê bài tập"; }
            public Set<String> resolvedIdentifiers() { return Set.of("assignmentId"); }
            public ToolDefinition getDefinition() { return new ToolDefinition(getName(), getDescription(), "1",
                    "lecturer", "read", "assignment", EffectType.READ, Set.of("ROLE_LECTURER"),
                    Duration.ofSeconds(5), 1000); }
            public ToolResult execute(JsonNode args, AgentContext context) {
                return ToolResult.ok(List.of(java.util.Map.of("id", "assignment-4", "title", "Bài tập 4")));
            }
        };
        AgentTool plans = new AgentTool() {
            public String getName() { return "listTestPlans"; }
            public String getDescription() { return "Liệt kê câu kiểm tra"; }
            public Set<String> resolvedIdentifiers() { return Set.of("planId"); }
            public Set<String> lookupPrerequisites() { return Set.of("assignmentId"); }
            public ToolDefinition getDefinition() { return new ToolDefinition(getName(), getDescription(), "1",
                    "lecturer", "read", "assignment", EffectType.READ, Set.of("ROLE_LECTURER"),
                    Duration.ofSeconds(5), 1000); }
            public ToolResult execute(JsonNode args, AgentContext context) {
                assertEquals("assignment-4", args.path("assignmentId").asText());
                return ToolResult.ok(List.of(java.util.Map.of("id", "plan-sample", "name", "Kiểm tra mẫu")));
            }
        };
        AgentTool stepWriter = new AgentTool() {
            public String getName() { return "createTestStep"; }
            public String getDescription() { return "Tạo bước chấm"; }
            public ToolDefinition getDefinition() { return new ToolDefinition(getName(), getDescription(), "1",
                    "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"),
                    Duration.ofSeconds(5), 1000); }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok("unused"); }
        };
        ToolRegistry registry = new ToolRegistry(List.of(assignments, plans, stepWriter), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage(
                        "http request và m tự suy luận các thông tin cần thiết")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            int step = calls.getAndIncrement();
            List<ToolCallback> callbacks = call.getArgument(2);
            if (step == 0) return Flux.just("Để tạo step, bạn cung cấp assignmentId và planId được không?");
            if (step == 1) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("listVisibleAssignments"))
                    .findFirst().orElseThrow().call("{}"));
            if (step == 2) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("listTestPlans"))
                    .findFirst().orElseThrow().call("{\"assignmentId\":\"assignment-4\"}"));
            return Flux.just("Đã xác định Bài tập 4 và Kiểm tra mẫu; có thể tạo bước HTTP_REQUEST.");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "http request và m tự suy luận các thông tin cần thiết",
                llm, delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("Đã xác định"));
        assertEquals(4, calls.get());
    }

    @Test
    void cancelledRunDoesNotStartAnotherModelStep() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper, null);
        LlmClient llm = mock(LlmClient.class);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry),
                mock(ContextPreflight.class), mock(ToolResultStore.class),
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper,
                new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(RunCancelledException.class, () -> runtime.execute(context, "system", "question",
                llm, delta -> {}, (tool, result) -> {}, () -> true));
        verifyNoInteractions(llm);
    }

    @Test
    void cancellationStopsAnInFlightModelStream() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper, null);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        AtomicBoolean cancelled = new AtomicBoolean();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(Flux.defer(() -> {
            cancelled.set(true);
            return Flux.never();
        }));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class),
                mock(MessageRepository.class), mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(RunCancelledException.class, () -> runtime.execute(context, "system", "question",
                llm, delta -> {}, (tool, result) -> {}, cancelled::get));
    }

    @Test
    void shortYesToUnstoredStepProposalPreparesThatStepInsteadOfOldTestPlan() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = mock(AgentTool.class);
        when(writer.getName()).thenReturn("createTestStep");
        when(writer.getDescription()).thenReturn("Tạo bước chấm");
        when(writer.getDefinition()).thenReturn(new ToolDefinition("createTestStep", "Tạo bước chấm",
                "1", "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        when(writer.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("createTestStep").description("Tạo bước chấm")
                .inputSchema("{\"type\":\"object\"}").build());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.empty());
        when(approvals.propose(eq(writer), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "createTestStep", "arguments", mapper.createObjectNode().put("name", "GET /status"))));
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        MessageRepository messages = mock(MessageRepository.class);
        var currentUser = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .role("user").content("có").sequenceNo(11).build();
        var previousAssistant = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .role("assistant").content("Đề xuất dưới đây chưa được lưu thành thao tác chờ xác nhận:\n\n"
                        + "Bước chấm GET /status, expected 200.")
                .sequenceNo(10).build();
        when(messages.findScopedRecent(any(), anyString(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(currentUser, previousAssistant)));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("có")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(contains("GET /status"), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            assertTrue(((String) call.getArgument(0)).contains("chưa có pending action"));
            List<ToolCallback> callbacks = call.getArgument(2);
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("createTestStep"))
                    .findFirst().orElseThrow().call("{\"name\":\"GET /status\"}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), messages, mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "có", llm, delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("GET /status"));
        verify(approvals).propose(eq(writer), any(), eq(context));
        verify(approvals, never()).confirmFromChat(any(), any());
    }

    @Test
    void doesNotAskForApprovalUntilWriteProposalIsStored() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = mock(AgentTool.class);
        when(writer.getName()).thenReturn("createTestStep");
        when(writer.getDescription()).thenReturn("Tạo bước chấm");
        when(writer.getDefinition()).thenReturn(new ToolDefinition("createTestStep", "Tạo bước chấm",
                "1", "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        when(writer.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("createTestStep").description("Tạo bước chấm")
                .inputSchema("{\"type\":\"object\"}").build());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.propose(eq(writer), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "createTestStep", "arguments", mapper.createObjectNode().put("name", "GET /status"))));
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Tạo bước chấm")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            if (calls.getAndIncrement() == 0) return Flux.just("Mình sẽ tạo bước chấm GET /status. Bạn có đồng ý không?");
            List<ToolCallback> callbacks = call.getArgument(2);
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("createTestStep"))
                    .findFirst().orElseThrow().call("{\"name\":\"GET /status\"}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "Tạo bước chấm", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("GET /status"));
        assertTrue(reply.contains("đồng ý"));
        assertEquals(2, calls.get());
        verify(approvals).propose(eq(writer), any(), eq(context));
    }

    @Test
    void unlistedToolRecoveryLeavesSearchPhaseSoToolBecomesCallable() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool assignments = new AgentTool() {
            public String getName() { return "listVisibleAssignments"; }
            public String getDescription() { return "Liệt kê bài tập"; }
            public String referenceLookupKeywords() { return "bài tập assignmentId"; }
            public ToolDefinition getDefinition() { return new ToolDefinition(getName(), getDescription(),
                    "1", "student", "read", "assignment", EffectType.READ,
                    Set.of("ROLE_STUDENT"), Duration.ofSeconds(5), 1000); }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok(List.of()); }
        };
        ToolRegistry registry = new ToolRegistry(List.of(assignments, tool("createTestStep", "Tạo bước chấm")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("bài tập 4")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        AtomicInteger normalCalls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            if (normalCalls.getAndIncrement() == 0) return Flux.just("Bạn cho biết bài tập 4 được không?");
            List<ToolCallback> callbacks = call.getArgument(2);
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createTestStep")));
            return Flux.just("Đã tìm thấy công cụ");
        });
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("search_tools")))
                .thenReturn(Flux.error(new AiProviderException(new RuntimeException(
                        "attempted to call tool 'createTestStep' which was not in request.tools"))));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đã tìm thấy công cụ", runtime.execute(context, "system", "bài tập 4", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(2, normalCalls.get());
    }

    @Test
    void synthesizesAnswerAfterLastAllowedToolStep() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMyClasses", "List classes")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("List my classes")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listMyClasses"));
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            if (calls.getAndIncrement() == 0) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("listMyClasses"))
                    .findFirst().orElseThrow().call("{}"));
            assertTrue(callbacks.isEmpty());
            return Flux.just("Here are your classes.");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Here are your classes.", runtime.execute(context, "system", "List my classes", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(2, calls.get());
    }

    @Test
    void streamsFinalNoToolAnswerAsProviderChunks() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 0, 20));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), isNull(), any()))
                .thenReturn(Flux.just("a".repeat(120), "b".repeat(20)));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(0);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        List<String> deltas = new ArrayList<>();
        List<AgentRuntime.RuntimeEvent> events = new ArrayList<>();

        String answer = runtime.execute(context, "system", "question", llm, deltas::add,
                (tool, result) -> {}, events::add, () -> false);

        assertEquals("a".repeat(120) + "b".repeat(20), answer);
        assertTrue(deltas.size() > 1);
        assertEquals(answer, String.join("", deltas));
        assertTrue(events.stream().noneMatch(event -> event.type().equals("assistant.replace")));
    }

    @Test
    void replacesStreamedDraftWhenFinalApprovalGuardChangesIt() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = lecturerTool("createTestStep", EffectType.DOMAIN_WRITE, Set.of(), Set.of());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.empty());
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("thêm bước")), "", 10, 0, 20));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), isNull(), any()))
                .thenReturn(Flux.just("Đề xuất bước 3: GET /books. " + "Chi tiết kiểm tra. ".repeat(12)
                        + "Bạn xá", "c nhận để tôi tạo nhé?"));
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(0);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props, approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        List<String> deltas = new ArrayList<>();
        List<AgentRuntime.RuntimeEvent> events = new ArrayList<>();

        String answer = runtime.execute(context, "system", "thêm bước", llm, deltas::add,
                (tool, result) -> {}, events::add, () -> false);

        assertFalse(deltas.isEmpty());
        assertFalse(String.join("", deltas).contains("Bạn xác nhận"));
        assertFalse(String.join("", deltas).contains("Bạn xá"));
        assertTrue(answer.contains("chưa được lưu"));
        assertTrue(events.stream().anyMatch(event -> event.type().equals("assistant.replace")
                && answer.equals(event.data().get("text"))));
    }

    @Test
    void initialToolChoiceUsesRecentConversationAndCanExposeMoreThanFourTools() {
        var mapper = new JacksonConfig().objectMapper();
        List<AgentTool> tools = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(index -> tool("readTool" + index, "Read assignment data " + index)).toList();
        ToolRegistry registry = new ToolRegistry(tools, mapper);
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.findScopedRecent(any(), anyString(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("Tạo câu kiểm tra").sequenceNo(1).build(),
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                        .role("user").content("của bài tập 4 ấy").sequenceNo(2).build())));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("của bài tập 4 ấy")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList()))
                .thenReturn(tools.stream().map(AgentTool::getName).toList());
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            assertEquals(5, callbacks.stream().filter(cb -> cb.getToolDefinition().name().startsWith("readTool")).count());
            return Flux.just("Đã hiểu yêu cầu");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages, mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đã hiểu yêu cầu", runtime.execute(context, "system", "của bài tập 4 ấy", llm,
                delta -> {}, (tool, result) -> {}));
        org.mockito.ArgumentCaptor<String> query = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llm).chooseRelevantTools(query.capture(), anyList());
        assertTrue(query.getValue().contains("Tạo câu kiểm tra"));
        assertTrue(query.getValue().contains("của bài tập 4 ấy"));
    }

    @Test
    void proposingAWriteAsksForConsentInChat() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = mock(AgentTool.class);
        when(writer.getName()).thenReturn("createTestPlan");
        when(writer.getDescription()).thenReturn("Tạo câu kiểm tra Q1");
        when(writer.getDefinition()).thenReturn(new ToolDefinition("createTestPlan", "Tạo câu kiểm tra Q1", "1",
                "lecturer", "write", "plan", EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"),
                Duration.ofSeconds(5), 1000));
        when(writer.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("createTestPlan").description("Tạo câu kiểm tra Q1")
                .inputSchema("{\"type\":\"object\"}").build());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.propose(eq(writer), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "createTestPlan", "arguments", mapper.createObjectNode().put("name", "Q1"))));
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Tạo câu kiểm tra")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestPlan"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            calls.incrementAndGet();
            List<ToolCallback> callbacks = call.getArgument(2);
            String proposal = callbacks.stream().filter(callback ->
                    callback.getToolDefinition().name().equals("createTestPlan"))
                    .findFirst().orElseThrow().call("{\"name\":\"Q1\"}");
            return Flux.just(proposal);
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "Tạo câu kiểm tra", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("Q1"));
        assertTrue(reply.contains("đồng ý"));
        assertEquals(1, calls.get());
    }

    @Test
    void confirmedChatReplyExecutesThePendingActionWithoutAButton() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        UUID actionId = UUID.randomUUID();
        UUID originRunId = UUID.randomUUID();
        var pending = new WriteApprovalService.ChatPendingAction(actionId, originRunId, "createTestQuestion",
                "Tạo câu kiểm tra", mapper.createObjectNode().put("name", "Q1"));
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(pending));
        ToolResult executedResult = ToolResult.ok(java.util.Map.of("id", "plan-1", "name", "Q1"));
        when(approvals.confirmFromChat(eq(actionId), any())).thenReturn(java.util.Map.of(
                "status", "COMPLETED", "result", executedResult));
        MessageRepository messages = mock(MessageRepository.class);
        when(messages.findFirstByRunIdAndRoleOrderBySequenceNoAsc(originRunId, "user"))
                .thenReturn(java.util.Optional.of(vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message
                        .builder().content("Tạo câu kiểm tra Q1 cho bài tập 4").build()));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyPendingActionReply(eq("đồng ý, tạo đi"), contains("Q1")))
                .thenReturn(LlmClient.ApprovalDecision.CONFIRM);
        when(llm.generateConfirmedActionReply(eq("Tạo câu kiểm tra Q1 cho bài tập 4"),
                eq("createTestQuestion"), contains("Q1"), same(executedResult)))
                .thenReturn("Đã tạo câu kiểm tra Q1 cho bài tập 4 rồi nhé.");
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), mock(ContextPreflight.class),
                mock(ToolResultStore.class), mock(ConversationRepository.class), messages,
                mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "đồng ý, tạo đi", llm,
                delta -> {}, (tool, result) -> {});

        assertEquals("Đã tạo câu kiểm tra Q1 cho bài tập 4 rồi nhé.", reply);
        verify(approvals).confirmFromChat(actionId, context);
        verify(llm, never()).streamAgentTurn(anyString(), anyList(), anyList());
    }

    @Test
    void failedConfirmedActionShowsTheToolErrorInsteadOfOnlyFailedStatus() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        UUID actionId = UUID.randomUUID();
        var pending = new WriteApprovalService.ChatPendingAction(actionId, "createTestQuestion",
                "Tạo câu kiểm tra", mapper.createObjectNode());
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(pending));
        when(approvals.confirmFromChat(eq(actionId), any())).thenReturn(java.util.Map.of(
                "status", "FAILED", "result", ToolResult.error("DOMAIN_REJECTED",
                        "API từ chối (400 BAD_REQUEST): HTTP_REQUEST: missing or empty 'path'", false)));
        LlmClient llm = mock(LlmClient.class);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), mock(ContextPreflight.class),
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        List<AgentRuntime.RuntimeEvent> events = new java.util.ArrayList<>();
        String reply = runtime.execute(context, "system", "có", llm,
                delta -> {}, (tool, result) -> {}, events::add, () -> false);

        assertTrue(reply.contains("HTTP_REQUEST: missing or empty 'path'"));
        assertEquals(List.of("tool.started", "tool.failed"),
                events.stream().map(AgentRuntime.RuntimeEvent::type).toList());
        verify(llm, never()).classifyPendingActionReply(anyString(), anyString());
        assertFalse(reply.contains("Trạng thái: FAILED"));
    }

    @Test
    void rejectedChatReplyCancelsThePendingAction() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        UUID actionId = UUID.randomUUID();
        var pending = new WriteApprovalService.ChatPendingAction(actionId, "createTestQuestion",
                "Tạo câu kiểm tra", mapper.createObjectNode());
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(pending));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyPendingActionReply(anyString(), anyString())).thenReturn(LlmClient.ApprovalDecision.REJECT);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), mock(ContextPreflight.class),
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "không, hủy đi", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("Đã hủy"));
        verify(approvals).rejectFromChat(actionId, context);
        verify(approvals, never()).confirmFromChat(any(), any());
    }

    @Test
    void unclearChatReplyDoesNotExecutePendingWrite() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        var pending = new WriteApprovalService.ChatPendingAction(UUID.randomUUID(), "createTestQuestion",
                "Tạo câu kiểm tra", mapper.createObjectNode());
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(pending));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyPendingActionReply(anyString(), anyString())).thenReturn(LlmClient.ApprovalDecision.OTHER);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(Flux.just("Mình có thể giải thích thêm"));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("xem lại")), "", 10, 10, 30));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        runtime.execute(context, "system", "xem lại", llm, delta -> {}, (tool, result) -> {});

        verify(approvals, never()).confirmFromChat(any(), any());
        verify(approvals, never()).rejectFromChat(any(), any());
    }

    @Test
    void existingPendingActionCanBeAskedAboutAgain() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        var pending = new WriteApprovalService.ChatPendingAction(UUID.randomUUID(), "createTestStep",
                "Tạo bước chấm", mapper.createObjectNode());
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(pending));
        LlmClient llm = mock(LlmClient.class);
        when(llm.classifyPendingActionReply(anyString(), anyString())).thenReturn(LlmClient.ApprovalDecision.OTHER);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList()))
                .thenReturn(Flux.just("Bạn có đồng ý tạo bước chấm này không?"));
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("xem lại")), "", 10, 10, 30));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Bạn có đồng ý tạo bước chấm này không?",
                runtime.execute(context, "system", "xem lại", llm, delta -> {}, (tool, result) -> {}));
    }

    @Test
    void sendsRejectedToolCallDetailsBackToModelForCorrection() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("createTestQuestion", "Tạo câu kiểm tra")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> {
                    List<org.springframework.ai.chat.messages.Message> history = new java.util.ArrayList<>();
                    history.add(new UserMessage("Tạo câu kiểm tra"));
                    history.addAll(call.getArgument(5));
                    return new ContextPreflight.PreparedContext(history, "", 10, 10, 30);
                });
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestQuestion"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<org.springframework.ai.chat.messages.Message> history = call.getArgument(1);
            if (calls.getAndIncrement() == 0) return Flux.error(new AiProviderException(
                    new RuntimeException("200: Tool call validation failed: parameters for tool createTestQuestion "
                            + "did not match schema: errors: [`/payload/steps/0/weight`: expected integer, "
                            + "but got number]; token=private-value")));
            String feedback = history.getLast().getText();
            assertTrue(feedback.contains("createTestQuestion"));
            assertTrue(feedback.contains("/payload/steps/0/weight"));
            assertTrue(feedback.contains("integer"));
            assertFalse(feedback.contains("private-value"));
            return Flux.just("Đã sửa tham số");
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxValidationRepairs(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đã sửa tham số", runtime.execute(context, "system", "Tạo câu kiểm tra", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(2, calls.get());
    }

    @Test
    void stopsRepairingRejectedToolCallAtConfiguredLimit() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("createTestQuestion", "Tạo câu kiểm tra")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> {
                    List<org.springframework.ai.chat.messages.Message> history = new java.util.ArrayList<>();
                    history.add(new UserMessage("Tạo câu kiểm tra"));
                    history.addAll(call.getArgument(5));
                    return new ContextPreflight.PreparedContext(history, "", 10, 10, 30);
                });
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            calls.incrementAndGet();
            return Flux.error(new AiProviderException(new RuntimeException(
                    "Tool call validation failed: invalid arguments for tool createTestQuestion")));
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxValidationRepairs(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(AiProviderException.class, () -> runtime.execute(context, "system", "Tạo câu kiểm tra", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(2, calls.get());
    }

    @Test
    void promptDescribesCapabilitiesWhileOnlySelectedToolsAreCallable() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("listMyClasses", "Xem lớp học"),
                tool("createTestPlan", "Tạo câu kiểm tra")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Xem lớp học")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listMyClasses"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            String prompt = call.getArgument(0);
            List<ToolCallback> callbacks = call.getArgument(2);
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("listMyClasses")));
            assertFalse(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createTestPlan")));
            assertFalse(prompt.contains("listMyClasses"));
            assertFalse(prompt.contains("createTestPlan"));
            assertTrue(prompt.contains("Xem lớp học"));
            assertTrue(prompt.contains("Tạo câu kiểm tra"));
            return Flux.just("Tôi có thể xem lớp học và tạo câu kiểm tra.");
        });
        AgentProperties props = new AgentProperties();
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        String catalog = new ContextBuilder(registry).buildSystemPrompt("ROLE_STUDENT", null);

        java.util.concurrent.atomic.AtomicReference<String> delivered = new java.util.concurrent.atomic.AtomicReference<>();
        String reply = runtime.execute(context, catalog, "Xem lớp học", llm,
                delivered::set, (tool, result) -> {});
        assertEquals(reply, delivered.get());
        assertEquals("Tôi có thể xem lớp học và tạo câu kiểm tra.", reply);
    }

    @Test
    void loadsAuthorizedToolAfterProviderRejectsUnlistedToolCall() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("createTestPlan", "Tạo câu kiểm tra")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("tiếp tục")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            boolean listed = callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("createTestPlan"));
            if (calls.getAndIncrement() == 0) {
                assertFalse(listed);
                return Flux.error(new AiProviderException(new RuntimeException(
                        "200: Tool call validation failed: attempted to call tool 'createTestPlan' "
                                + "which was not in request.tools")));
            }
            assertTrue(listed);
            return Flux.just("Đã tìm thấy công cụ");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("Đã tìm thấy công cụ", runtime.execute(context, "system", "tiếp tục", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotLoadToolOutsideCallerRoleAfterProviderRejection() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("createTestPlan", "Tạo câu kiểm tra")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("tiếp tục")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(Flux.error(
                new AiProviderException(new RuntimeException(
                        "attempted to call tool 'createTestPlan' which was not in request.tools"))));
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(AiProviderException.class, () -> runtime.execute(context, "system", "tiếp tục", llm,
                delta -> {}, (tool, result) -> {}));
        verify(llm).streamAgentTurn(anyString(), anyList(), anyList());
    }

    @Test
    void retriesTransientProviderFailureUsingConfiguredLimit() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call ->
                calls.incrementAndGet() == 1
                        ? Flux.error(new AiProviderException(new RuntimeException("temporary outage")))
                        : Flux.just("answer"));
        AgentProperties props = new AgentProperties();
        props.getRetry().setModelMaxRetries(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertEquals("answer", runtime.execute(context, "system", "question", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(2, calls.get());
        verify(preflight).prepare(any(), any(), anySet(), anyString(), anyList(), anyList());
    }

    @Test
    void doesNotRetryOversizedProviderRequest() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            calls.incrementAndGet();
            return Flux.error(new AiProviderException(new java.util.concurrent.CompletionException(
                    new RuntimeException("413: Request too large for model; Limit 8000, Requested 8315"))));
        });
        AgentProperties props = new AgentProperties();
        props.getRetry().setModelMaxRetries(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(AiProviderException.class, () -> runtime.execute(context, "system", "question", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(1, calls.get());
    }

    @Test
    void stopsAfterConfiguredProviderRetriesAreExhausted() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            calls.incrementAndGet();
            return Flux.error(new AiProviderException(new RuntimeException("temporary outage")));
        });
        AgentProperties props = new AgentProperties();
        props.getRetry().setModelMaxRetries(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(AiProviderException.class, () -> runtime.execute(context, "system", "question", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(3, calls.get());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void doesNotRetryProviderFailureAfterToolExecuted(CapturedOutput logOutput) {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMyClasses", "Liệt kê lớp học")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listMyClasses"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            calls.incrementAndGet();
            List<ToolCallback> callbacks = call.getArgument(2);
            ToolCallback classes = callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("listMyClasses"))
                    .findFirst().orElseThrow();
            return Flux.concat(Flux.just(classes.call("{}")),
                    Flux.error(new AiProviderException(new RuntimeException("connection lost"))));
        });
        AgentProperties props = new AgentProperties();
        props.getRetry().setModelMaxRetries(2);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertThrows(AiProviderException.class, () -> runtime.execute(context, "system", "Xem lớp học", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(1, calls.get());
        assertTrue(logOutput.getOut().contains("runId=" + context.runId() + " modelSteps=1 modelCalls=1 toolCalls=1"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void exposesModelDiscoveredToolOnNextRequest(CapturedOutput logOutput) {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool classes = tool("listMyClasses", "Liệt kê lớp học");
        AgentTool submissions = tool("listMySubmissions", "Liệt kê bài nộp");
        ToolRegistry registry = new ToolRegistry(List.of(classes, submissions), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.of(new UserMessage("Xem lớp học rồi tìm bài nộp")), "", 100, 100, 300));
        AgentProperties props = new AgentProperties();
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenAnswer(call ->
                ((String) call.getArgument(0)).contains("bài nộp")
                        ? List.of("listMySubmissions") : List.of("listMyClasses"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            Set<String> names = callbacks.stream().map(cb -> cb.getToolDefinition().name())
                    .collect(java.util.stream.Collectors.toSet());
            if (calls.getAndIncrement() == 0) {
                assertTrue(names.contains("search_tools"));
                assertFalse(names.contains("listMySubmissions"));
                String result = callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                        .findFirst().orElseThrow().call("{\"query\":\"bài nộp\"}");
                return Flux.just(result);
            }
            assertTrue(names.contains("listMySubmissions"));
            return Flux.just("Đã tìm thấy bài nộp");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String result = runtime.execute(context, "system", "Xem lớp học", llm, delta -> {}, (tool, output) -> {});

        assertEquals("Đã tìm thấy bài nộp", result);
        assertEquals(2, calls.get());
        verify(preflight, times(2)).prepare(any(), any(), anySet(), anyString(), anyList(), anyList());
        assertTrue(logOutput.getOut().contains("runId=" + context.runId() + " modelStep=1"));
        assertTrue(logOutput.getOut().contains("tool=search_tools"));
        assertTrue(logOutput.getOut().contains("runId=" + context.runId() + " modelSteps=2 modelCalls=2 toolCalls=1"));
    }

    @Test
    void intermediateToolReturnsToPreflightBeforeLaterDiscovery() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(
                tool("listMyClasses", "Liệt kê lớp học"), tool("listMySubmissions", "Liệt kê bài nộp")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.of(new UserMessage("Xem lớp học rồi bài nộp")), "", 100, 100, 300));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenAnswer(call ->
                ((String) call.getArgument(0)).contains("bài nộp")
                        ? List.of("listMySubmissions") : List.of("listMyClasses"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            int step = calls.getAndIncrement();
            if (step == 0) {
                ToolCallback classes = callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("listMyClasses"))
                        .findFirst().orElseThrow();
                assertTrue(classes.getToolMetadata().returnDirect());
                return Flux.just(classes.call("{}"));
            }
            if (step == 1) {
                String found = callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                        .findFirst().orElseThrow().call("{\"query\":\"bài nộp\"}");
                return Flux.just(found);
            }
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("listMySubmissions")));
            return Flux.just("Hoàn tất");
        });
        AgentProperties props = new AgentProperties();
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        assertEquals("Hoàn tất", runtime.execute(context, "system", "Xem lớp học", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(3, calls.get());
        verify(preflight, times(3)).prepare(any(), any(), anySet(), anyString(), anyList(), anyList());
    }

    @Test
    void storesEarlierCompleteToolOutputWhenLaterResultsFillToolBudget() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMyClasses", "Liệt kê lớp học")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<org.springframework.ai.chat.messages.Message> inFlight = call.getArgument(5);
            if (inFlight.size() > 1 && inFlight.stream().noneMatch(m -> m.getText().contains("get_tool_result_page"))) {
                throw new ContextPreflight.InputTooLargeException("Tool context exceeds tool budget");
            }
            return new ContextPreflight.PreparedContext(List.of(new UserMessage("question")), "", 100, 100, 300);
        });
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        UUID stored = UUID.randomUUID();
        when(store.reference(anyString(), any())).thenReturn(stored);
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listMyClasses"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            int step = calls.getAndIncrement();
            List<ToolCallback> callbacks = call.getArgument(2);
            if (step < 2) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("listMyClasses"))
                    .findFirst().orElseThrow().call("{}"));
            return Flux.just("finished");
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties());
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        assertEquals("finished", runtime.execute(context, "system", "Xem lớp học", llm,
                delta -> {}, (tool, result) -> {}));
        verify(store, atLeastOnce()).reference(anyString(), eq(context));
    }

    @Test
    void longPromptAndSuccessiveToolResultsDoNotExhaustHistoryBudget() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(readWithResult("listMyClasses",
                ToolResult.ok("x".repeat(1800)))), mapper);
        AgentProperties props = new AgentProperties();
        props.getContext().setMaxInputTokens(6500);
        props.getContext().setOutputReserve(1536);
        props.getContext().setSafetyMargin(1000);
        props.getContext().setHistoryMaxTokens(3500);
        props.getContext().setToolMaxTokens(2500);
        props.getLimits().setMaxModelSteps(3);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        MessageRepository messages = mock(MessageRepository.class);
        var latest = vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                .id(UUID.randomUUID()).conversationId(context.conversationId()).sequenceNo(1)
                .role("user").content("Xem lớp học").build();
        when(messages.findScopedRecent(eq(context.conversationId()), eq("ROLE_LECTURER"), any()))
                .thenReturn(new PageImpl<>(List.of(latest)));
        ConversationSummaryRepository summaries = mock(ConversationSummaryRepository.class);
        when(summaries.findTopByConversationIdAndRoleScopeOrderByCreatedAtDesc(
                context.conversationId(), "ROLE_LECTURER")).thenReturn(java.util.Optional.empty());
        ContextPreflight preflight = new ContextPreflight(messages, summaries,
                mock(ConversationSummarizer.class), props, new TokenEstimator());
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        UUID storedId = UUID.randomUUID();
        when(store.reference(anyString(), eq(context))).thenReturn(storedId);
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("listMyClasses"));
        AtomicInteger turns = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            int turn = turns.getAndIncrement();
            if (turn == 2) {
                List<org.springframework.ai.chat.messages.Message> supplied = call.getArgument(1);
                assertTrue(supplied.stream().anyMatch(message -> message.getText().contains(storedId.toString())));
                assertTrue(supplied.stream().anyMatch(message -> message.getText().contains("x".repeat(100))));
                return Flux.just("Đã xem lớp học");
            }
            List<ToolCallback> callbacks = call.getArgument(2);
            return Flux.just(callbacks.stream()
                    .filter(callback -> callback.getToolDefinition().name().equals("listMyClasses"))
                    .findFirst().orElseThrow().call("{}"));
        });
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), messages, mapper, props);

        assertEquals("Đã xem lớp học", runtime.execute(context, "p".repeat(7400), "Xem lớp học",
                llm, delta -> {}, (tool, result) -> {}));
        assertEquals(3, turns.get());
        verify(store, atLeastOnce()).reference(anyString(), eq(context));
    }

    @Test
    void canDiscoverAgainAfterAnIntermediateTool() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMyClasses", "Liệt kê lớp học"),
                tool("listMySubmissions", "Liệt kê bài nộp"),
                tool("getMyClass", "Xem chi tiết lớp học")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("request")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(llm.chooseRelevantTools(anyString(), anyList())).thenAnswer(call -> {
            String query = call.getArgument(0);
            if (query.contains("chi tiết lớp học")) return List.of("getMyClass");
            if (query.contains("bài nộp")) return List.of("listMySubmissions");
            return List.of("listMyClasses");
        });
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            int step = calls.getAndIncrement();
            if (step == 0) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                    .findFirst().orElseThrow().call("{\"query\":\"bài nộp\"}"));
            if (step == 1) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("listMySubmissions"))
                    .findFirst().orElseThrow().call("{}"));
            if (step == 2) return Flux.just(callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                    .findFirst().orElseThrow().call("{\"query\":\"chi tiết lớp học\"}"));
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("getMyClass")));
            return Flux.just("done");
        });
        AgentProperties props = new AgentProperties();
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        assertEquals("done", runtime.execute(context, "system", "Xem lớp học", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(4, calls.get());
        verify(preflight, times(4)).prepare(any(), any(), anySet(), anyString(), anyList(), anyList());
    }

    @Test
    void promptContainsEnforcedSearchToolsInstructions() {
        var mapper = new vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMyClasses", "Liệt kê lớp học")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.of(new UserMessage("Xem lớp")), "", 100, 100, 300));
        ToolResultStore store = mock(ToolResultStore.class);
        LlmClient llm = mock(LlmClient.class);
        java.util.concurrent.atomic.AtomicReference<String> passedPrompt = new java.util.concurrent.atomic.AtomicReference<>();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            passedPrompt.set(call.getArgument(0));
            return Flux.just("done");
        });

        AgentProperties properties = new AgentProperties();
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, properties);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        runtime.execute(context, "System prompt", "Xem lớp", llm, delta -> {}, (tool, result) -> {});

        assertNotNull(passedPrompt.get());
        assertTrue(passedPrompt.get().contains("search_tools"));
    }

    private AgentTool tool(String name, String description) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return description; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(name, description, "1", "student", "read", "course",
                        EffectType.READ, Set.of("ROLE_STUDENT"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok("ok"); }
        };
    }

    @Test
    void looksUpAssignmentBeforeAskingUserForItsId() {
        var mapper = new JacksonConfig().objectMapper();
        AtomicInteger reads = new AtomicInteger();
        AgentTool assignments = new AgentTool() {
            public String getName() { return "listVisibleAssignments"; }
            public String getDescription() { return "Lấy danh sách bài tập người dùng được xem."; }
            public String referenceLookupKeywords() { return "bài tập assignmentId"; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(getName(), getDescription(), "1", "common", "read", "assignment",
                        EffectType.READ, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) {
                reads.incrementAndGet();
                return ToolResult.ok(List.of(java.util.Map.of("id", UUID.randomUUID(), "title", "Bài tập 4")));
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(assignments), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("của bài tập 4 ấy")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(
                Flux.just("Bạn cung cấp assignmentId của bài tập 4 được không?"),
                Flux.just("Mình đã tìm thấy Bài tập 4 và có thể tạo câu kiểm tra."));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("search_tools"))).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            String result = callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                    .findFirst().orElseThrow().call("{\"query\":\"danh sách bài tập\"}");
            return Flux.just(result);
        });
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("required"))).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            assertTrue(callbacks.stream().anyMatch(cb -> cb.getToolDefinition().name().equals("search_tools")),
                    "The READ prompt mentions search_tools, so it must remain callable");
            String result = callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("listVisibleAssignments"))
                    .findFirst().orElseThrow().call("{}");
            return Flux.just(result);
        });
        AgentProperties props = new AgentProperties();
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        List<String> delivered = new java.util.ArrayList<>();

        String reply = runtime.execute(context, "system", "của bài tập 4 ấy", llm,
                delivered::add, (tool, result) -> {});

        assertEquals("Mình đã tìm thấy Bài tập 4 và có thể tạo câu kiểm tra.", reply);
        assertEquals(List.of(reply), delivered);
        assertEquals(1, reads.get());
    }

    @Test
    void forcedLookupToolChoiceFailureFallsBackWithoutRepeatingTheSameRequest() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool assignments = new AgentTool() {
            public String getName() { return "listVisibleAssignments"; }
            public String getDescription() { return "Lấy danh sách bài tập người dùng được xem."; }
            public String referenceLookupKeywords() { return "bài tập assignmentId"; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(getName(), getDescription(), "1", "common", "read", "assignment",
                        EffectType.READ, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) { return ToolResult.ok(List.of()); }
        };
        ToolRegistry registry = new ToolRegistry(List.of(assignments), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("bài tập 4")), "", 10, 10, 30));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(
                Flux.just("Bạn cung cấp assignmentId của bài tập 4 được không?"),
                Flux.just("Mình chưa xác định được bài tập 4. Bạn cho mình tên bài tập đầy đủ nhé?"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("search_tools"))).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                    .findFirst().orElseThrow().call("{\"query\":\"danh sách bài tập\"}"));
        });
        AtomicInteger forcedReads = new AtomicInteger();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("required"))).thenAnswer(call -> {
            forcedReads.incrementAndGet();
            return Flux.error(new AiProviderException(new RuntimeException(
                    "200: Tool choice is required, but model did not call a tool")));
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxLookupRounds(1);
        props.getLimits().setMaxModelSteps(3);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight,
                mock(ToolResultStore.class), mock(ConversationRepository.class), mock(MessageRepository.class),
                mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "bài tập 4", llm, delta -> {}, (tool, result) -> {});

        assertEquals("Mình chưa xác định được bài tập 4. Bạn cho mình tên bài tập đầy đủ nhé?", reply);
        assertEquals(1, forcedReads.get());
    }

    @Test
    void asksOnlyAfterConfiguredLookupRoundsAreExhausted() {
        var mapper = new JacksonConfig().objectMapper();
        AtomicInteger reads = new AtomicInteger();
        AgentTool classes = new AgentTool() {
            public String getName() { return "listMyClasses"; }
            public String getDescription() { return "Lấy danh sách lớp học của người dùng."; }
            public String referenceLookupKeywords() { return "lớp học classId"; }
            public ToolDefinition getDefinition() {
                return new ToolDefinition(getName(), getDescription(), "1", "common", "read", "class",
                        EffectType.READ, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000);
            }
            public ToolResult execute(JsonNode args, AgentContext context) {
                reads.incrementAndGet();
                return ToolResult.ok(List.of());
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(classes), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("lớp 4")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt()))
                .thenReturn(new ToolResultStore.ToolResultDelivery("ok", true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenReturn(
                Flux.just("Bạn muốn làm việc với lớp nào?"),
                Flux.just("Mình chưa xác định được lớp 4. Bạn cho mình tên lớp đầy đủ nhé?"));
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("search_tools"))).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("search_tools"))
                    .findFirst().orElseThrow().call("{\"query\":\"danh sách lớp học\"}"));
        });
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), eq("required"))).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            return Flux.just(callbacks.stream().filter(cb -> cb.getToolDefinition().name().equals("listMyClasses"))
                    .findFirst().orElseThrow().call("{}"));
        });
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxLookupRounds(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "lớp 4", llm, delta -> {}, (tool, result) -> {});

        assertEquals("Mình chưa xác định được lớp 4. Bạn cho mình tên lớp đầy đủ nhé?", reply);
        assertEquals(1, reads.get());
    }

    @Test
    void guardsAgainstMultipleWritesInSingleModelTurn() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = mock(AgentTool.class);
        when(writer.getName()).thenReturn("createTestStep");
        when(writer.getDescription()).thenReturn("Tạo bước chấm");
        when(writer.getDefinition()).thenReturn(new ToolDefinition("createTestStep", "Tạo bước chấm",
                "1", "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        when(writer.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("createTestStep").description("Tạo bước chấm")
                .inputSchema("{\"type\":\"object\"}").build());
        WriteApprovalService approvals = mock(WriteApprovalService.class);
        when(approvals.propose(eq(writer), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "createTestStep", "arguments", mapper.createObjectNode().put("name", "GET /status-1"))));
        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("Tạo 2 bước")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));

        AtomicReference<String> secondCallResult = new AtomicReference<>();
        when(llm.streamAgentTurn(anyString(), anyList(), anyList())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            ToolCallback callback = callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("createTestStep"))
                    .findFirst().orElseThrow();
            callback.call("{\"name\":\"GET /status-1\"}");
            String res2 = callback.call("{\"name\":\"GET /status-2\"}");
            secondCallResult.set(res2);
            return Flux.just("Final answer");
        });

        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, new AgentProperties(), approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        String reply = runtime.execute(context, "system", "Tạo 2 bước", llm, delta -> {}, (tool, result) -> {});

        assertNotNull(secondCallResult.get());
        assertTrue(secondCallResult.get().contains("SEQUENTIAL_WRITE_REQUIRED"));
        assertTrue(secondCallResult.get().contains("Đã chuẩn bị một thao tác ghi trong lượt này"));
        verify(approvals, times(1)).propose(eq(writer), any(), eq(context));
        assertNotNull(reply);
        assertTrue(reply.contains("GET /status-1"));
    }

    @Test
    void confirmPendingActionContinuesAgentLoopWhenMultiStepPlanExists() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool writer = mock(AgentTool.class);
        when(writer.getName()).thenReturn("createTestStep");
        when(writer.getDescription()).thenReturn("Tạo bước chấm");
        when(writer.getDefinition()).thenReturn(new ToolDefinition("createTestStep", "Tạo bước chấm",
                "1", "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 1000));
        when(writer.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("createTestStep").description("Tạo bước chấm")
                .inputSchema("{\"type\":\"object\"}").build());

        WriteApprovalService approvals = mock(WriteApprovalService.class);
        UUID actionId = UUID.randomUUID();
        UUID originRunId = UUID.randomUUID();
        var pending = new WriteApprovalService.ChatPendingAction(actionId, originRunId, "updateTestStep",
                "Sửa bước 1", mapper.createObjectNode().put("name", "Bước 1"));
        when(approvals.pendingForChat(any())).thenReturn(java.util.Optional.of(pending));
        ToolResult executedResult = ToolResult.ok(java.util.Map.of("id", "step-1", "name", "Bước 1"));
        when(approvals.confirmFromChat(eq(actionId), any())).thenReturn(java.util.Map.of(
                "status", "COMPLETED", "result", executedResult));
        when(approvals.propose(eq(writer), any(), any())).thenReturn(ToolResult.ok(java.util.Map.of(
                "status", "AWAITING_CONFIRMATION", "pendingActionId", UUID.randomUUID(),
                "toolName", "createTestStep", "arguments", mapper.createObjectNode().put("name", "Thêm bước 3"))));

        ToolRegistry registry = new ToolRegistry(List.of(writer), mapper, approvals);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("ok")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));

        MessageRepository messages = mock(MessageRepository.class);
        when(messages.findFirstByRunIdAndRoleOrderBySequenceNoAsc(originRunId, "user"))
                .thenReturn(java.util.Optional.of(vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message
                        .builder().content("Sửa bài tập theo kế hoạch").build()));
        String multiStepPlan = "Kế hoạch thực hiện:\n1. Sửa bước 1: Đổi endpoint /status\n2. Thêm bước 3: Kiểm tra payload";
        when(messages.findScopedRecent(any(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(
                        vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message
                                .builder().role("assistant").content(multiStepPlan).sequenceNo(2).build()
                )));

        LlmClient llm = mock(LlmClient.class);
        when(llm.chooseRelevantTools(anyString(), anyList())).thenReturn(List.of("createTestStep"));
        when(llm.generateConfirmedActionReply(any(), any(), any(), any()))
                .thenReturn("Đã cập nhật bước 1 thành công.");
        when(llm.streamAgentTurn(anyString(), anyList(), anyList(), any(), any())).thenAnswer(call -> {
            List<ToolCallback> callbacks = call.getArgument(2);
            ToolCallback callback = callbacks.stream()
                    .filter(cb -> cb.getToolDefinition().name().equals("createTestStep"))
                    .findFirst().orElseThrow();
            callback.call("{\"name\":\"Thêm bước 3\"}");
            return Flux.just("Final answer");
        });

        AgentProperties props = new AgentProperties();
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), messages, mapper, props, approvals);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");

        List<AgentRuntime.RuntimeEvent> events = new ArrayList<>();
        String reply = runtime.execute(context, "system", "ok", llm,
                delta -> {}, (tool, result) -> {}, events::add, () -> false);

        verify(approvals).confirmFromChat(eq(actionId), eq(context));
        verify(llm).streamAgentTurn(anyString(), anyList(), anyList(), any(), any());
        verify(approvals).propose(eq(writer), any(), eq(context));
        assertNotNull(reply);
        assertTrue(reply.contains("Thêm bước 3"));
        assertTrue(events.stream().anyMatch(e -> "assistant.delta".equals(e.type())
                && String.valueOf(e.data().get("content")).contains("Tiếp tục thực hiện bước tiếp theo")));
    }
}
