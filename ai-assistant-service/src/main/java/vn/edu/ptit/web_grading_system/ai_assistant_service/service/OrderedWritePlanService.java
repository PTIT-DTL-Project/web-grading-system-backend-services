package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.TaskRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Durable, ordered sequence of independently approved domain writes. */
@Service
@RequiredArgsConstructor
public class OrderedWritePlanService {
    private static final String TYPE = "ORDERED_WRITES";
    private final TaskRepository tasks;
    private final ObjectMapper mapper;

    public record Plan(UUID id, String request, List<String> tools, int index,
                       List<JsonNode> completedResults) {
        public Plan(UUID id, String request, List<String> tools, int index) {
            this(id, request, tools, index, List.of());
        }
        public String currentTool() { return tools.get(index); }
        public int remaining() { return tools.size() - index; }
    }

    @Transactional
    public Optional<Task> start(AgentContext context, String request, List<String> writers) {
        if (writers == null || writers.size() < 2) return Optional.empty();
        try {
            Task task = Task.builder()
                    .conversationId(context.conversationId()).ownerId(context.userId())
                    .type(TYPE).status("ACTIVE")
                    .goalJson(mapper.writeValueAsString(Map.of("request", request, "tools", writers)))
                    .stateJson("{\"index\":0,\"completed\":[]}").build();
            return Optional.of(tasks.save(task));
        } catch (Exception error) {
            throw new IllegalStateException("Không lưu được kế hoạch thao tác ghi", error);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Plan> active(AgentContext context) {
        return tasks.findByConversationIdOrderByCreatedAtDesc(context.conversationId()).stream()
                .filter(task -> TYPE.equals(task.getType()) && "ACTIVE".equals(task.getStatus())
                        && context.userId().equals(task.getOwnerId()))
                .findFirst().map(this::parse);
    }

    public Plan parse(Task task) {
        try {
            JsonNode goal = mapper.readTree(task.getGoalJson());
            List<String> tools = mapper.convertValue(goal.path("tools"),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
            JsonNode state = mapper.readTree(task.getStateJson());
            int index = state.path("index").asInt(-1);
            List<JsonNode> completed = new java.util.ArrayList<>();
            state.path("completed").forEach(completed::add);
            if (tools.size() < 2 || index < 0 || index >= tools.size()) {
                throw new IllegalStateException("Kế hoạch thao tác ghi không hợp lệ");
            }
            return new Plan(task.getId(), goal.path("request").asText(), List.copyOf(tools), index,
                    List.copyOf(completed));
        } catch (Exception error) {
            throw new IllegalStateException("Kế hoạch thao tác ghi không hợp lệ", error);
        }
    }

    @Transactional
    public void advance(UUID taskId, String completedTool) {
        advance(taskId, completedTool, null);
    }

    @Transactional
    public void advance(UUID taskId, String completedTool, Object resultData) {
        Task task = tasks.findById(taskId).orElseThrow();
        if (!TYPE.equals(task.getType()) || !"ACTIVE".equals(task.getStatus())) {
            throw new IllegalStateException("Kế hoạch không còn hoạt động");
        }
        Plan plan = parse(task);
        if (!plan.currentTool().equals(completedTool)) {
            throw new IllegalStateException("Thao tác không đúng thứ tự kế hoạch");
        }
        int next = plan.index() + 1;
        if (next == plan.tools().size()) task.setStatus("COMPLETED");
        try {
            var state = mapper.createObjectNode();
            state.put("index", next);
            var completed = state.putArray("completed");
            plan.completedResults().forEach(completed::add);
            var entry = completed.addObject();
            entry.put("tool", completedTool);
            entry.set("data", mapper.valueToTree(resultData));
            task.setStateJson(mapper.writeValueAsString(state));
        } catch (Exception error) {
            throw new IllegalStateException("Không lưu được kết quả bước trong kế hoạch", error);
        }
        tasks.save(task);
    }

    @Transactional
    public void supersede(AgentContext context) {
        tasks.findByConversationIdOrderByCreatedAtDesc(context.conversationId()).stream()
                .filter(task -> TYPE.equals(task.getType()) && "ACTIVE".equals(task.getStatus())
                        && context.userId().equals(task.getOwnerId()))
                .forEach(task -> { task.setStatus("SUPERSEDED"); tasks.save(task); });
    }
}
