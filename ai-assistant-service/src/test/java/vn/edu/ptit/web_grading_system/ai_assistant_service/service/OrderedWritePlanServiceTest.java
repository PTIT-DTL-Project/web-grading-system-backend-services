package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.TaskRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderedWritePlanServiceTest {
    private final TaskRepository repository = mock(TaskRepository.class);
    private final OrderedWritePlanService service = new OrderedWritePlanService(repository, new ObjectMapper());
    private final AgentContext context = new AgentContext(UUID.randomUUID(), "lecturer@ptit.edu.vn",
            Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");

    @Test
    void preservesOrderAndRepeatedWriterAcrossConfirmations() {
        when(repository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            if (task.getId() == null) task.setId(UUID.randomUUID());
            return task;
        });
        Task task = service.start(context, "sửa hai bài rồi lưu trữ lớp", List.of(
                "updateAssignment", "updateAssignment", "archiveClass")).orElseThrow();
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));
        when(repository.findByConversationIdOrderByCreatedAtDesc(context.conversationId())).thenReturn(List.of(task));

        assertEquals("updateAssignment", service.active(context).orElseThrow().currentTool());
        service.advance(task.getId(), "updateAssignment", java.util.Map.of("id", "first-assignment"));
        assertEquals("updateAssignment", service.active(context).orElseThrow().currentTool());
        service.advance(task.getId(), "updateAssignment", java.util.Map.of("id", "second-assignment"));
        assertEquals("archiveClass", service.active(context).orElseThrow().currentTool());
        assertEquals("first-assignment", service.active(context).orElseThrow()
                .completedResults().get(0).path("data").path("id").asText());
        service.advance(task.getId(), "archiveClass");
        assertTrue(service.active(context).isEmpty());
        assertEquals("COMPLETED", task.getStatus());
    }

    @Test
    void wrongWriterCannotAdvancePlan() {
        when(repository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });
        Task task = service.start(context, "A rồi B", List.of("publishAssignment", "createTestPlan")).orElseThrow();
        when(repository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThrows(IllegalStateException.class, () -> service.advance(task.getId(), "createTestPlan"));
        assertEquals("ACTIVE", task.getStatus());
        assertEquals(0, service.parse(task).index());
    }

    @Test
    void singleWriterDoesNotCreatePlan() {
        assertTrue(service.start(context, "công bố", List.of("publishAssignment")).isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void unrelatedRequestSupersedesOnlyCurrentUsersPlan() {
        Task own = Task.builder().id(UUID.randomUUID()).conversationId(context.conversationId())
                .ownerId(context.userId()).type("ORDERED_WRITES").status("ACTIVE").build();
        Task other = Task.builder().id(UUID.randomUUID()).conversationId(context.conversationId())
                .ownerId(UUID.randomUUID()).type("ORDERED_WRITES").status("ACTIVE").build();
        when(repository.findByConversationIdOrderByCreatedAtDesc(context.conversationId()))
                .thenReturn(List.of(own, other));

        service.supersede(context);

        assertEquals("SUPERSEDED", own.getStatus());
        assertEquals("ACTIVE", other.getStatus());
    }
}
