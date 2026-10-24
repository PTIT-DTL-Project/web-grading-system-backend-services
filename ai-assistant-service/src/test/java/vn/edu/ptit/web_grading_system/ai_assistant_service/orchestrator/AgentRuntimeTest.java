package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ToolResultStore;
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
    void startsWithCappedToolsThenExposesDiscoveredToolOnNextRequest() {
        var mapper = new JacksonConfig().objectMapper();
        AgentTool classes = tool("listMyClasses", "Liệt kê lớp học");
        AgentTool submissions = tool("listMySubmissions", "Liệt kê bài nộp");
        ToolRegistry registry = new ToolRegistry(List.of(classes, submissions), mapper);
        ContextPreflight preflight = mock(ContextPreflight.class);
        when(preflight.prepare(any(), any(), anySet(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> new ContextPreflight.PreparedContext(
                        List.of(new UserMessage("Xem lớp học rồi tìm bài nộp")), "", 100, 100, 300));
        AgentProperties props = new AgentProperties();
        props.getContext().setInitialToolLimit(1);
        LlmClient llm = mock(LlmClient.class);
        AtomicInteger calls = new AtomicInteger();
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
        props.getContext().setInitialToolLimit(1);
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
        props.getContext().setInitialToolLimit(1);
        AgentRuntime runtime = new AgentRuntime(registry, new ToolSelector(registry), preflight, store,
                mock(ConversationRepository.class), mock(MessageRepository.class), mapper, props);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "");
        assertEquals("done", runtime.execute(context, "system", "Xem lớp học", llm,
                delta -> {}, (tool, result) -> {}));
        assertEquals(4, calls.get());
        verify(preflight, times(4)).prepare(any(), any(), anySet(), anyString(), anyList(), anyList());
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
}
