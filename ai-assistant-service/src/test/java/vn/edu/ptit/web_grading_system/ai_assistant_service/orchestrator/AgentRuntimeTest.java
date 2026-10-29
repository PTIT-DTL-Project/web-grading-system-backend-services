package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiProviderException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ToolResultStore;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.WriteApprovalService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentRuntimeTest {
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
                .role("assistant").content("Mình sẽ tạo bước chấm GET /status. Bạn có đồng ý không?")
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
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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

        String reply = runtime.execute(context, "system", "có", llm,
                delta -> {}, (tool, result) -> {});

        assertTrue(reply.contains("HTTP_REQUEST: missing or empty 'path'"));
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
            if (calls.incrementAndGet() == 2) {
                List<org.springframework.ai.chat.messages.Message> history = call.getArgument(1);
                assertTrue(history.getLast().getText().contains("Tham số không hợp lệ"));
                assertFalse(history.getLast().getText().contains("integer"));
            }
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
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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
    void canDiscoverAgainAfterAnIntermediateTool() {
        var mapper = new JacksonConfig().objectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(tool("listMyClasses", "Liệt kê lớp học"),
                tool("listMySubmissions", "Liệt kê bài nộp"),
                tool("getMyClass", "Xem chi tiết lớp học")), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenReturn(new ContextPreflight.PreparedContext(List.of(new UserMessage("request")), "", 10, 10, 30));
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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
        assertTrue(passedPrompt.get().contains("BẮT BUỘC"));
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
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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
        when(store.offer(anyString(), any(), anyInt())).thenAnswer(call ->
                new ToolResultStore.ToolResultDelivery(call.getArgument(0), true, null, null, "full", 0));
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
}
