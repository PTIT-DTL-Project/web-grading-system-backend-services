package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.StoredToolResult;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.TokenEstimator;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.StoredToolResultRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.GetToolResultPageTool;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolResultStoreTest {
    private final AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
            UUID.randomUUID(), UUID.randomUUID(), "");

    @Test
    void fittingResultIsDeliveredWholeWithoutStoring() {
        StoredToolResultRepository repository = mock(StoredToolResultRepository.class);
        ToolResultStore store = new ToolResultStore(repository, new TokenEstimator(), new AgentProperties());
        var delivery = store.offer("[{\"id\":1},{\"id\":2}]", context, 100);
        assertTrue(delivery.complete());
        assertEquals("[{\"id\":1},{\"id\":2}]", delivery.content());
        verifyNoInteractions(repository);
    }

    @Test
    void oversizedArrayIsStoredWholeAndEveryRecordIsRecoverable() {
        StoredToolResultRepository repository = mock(StoredToolResultRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        String original = "[" + java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> "{\"id\":" + i + ",\"name\":\"submission-" + i + "\"}")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
        ToolResultStore store = new ToolResultStore(repository, new TokenEstimator(), new AgentProperties());
        var first = store.offer(original, context, 90);
        assertFalse(first.complete());
        assertNotNull(first.storedId());
        var capture = org.mockito.ArgumentCaptor.forClass(StoredToolResult.class);
        verify(repository).save(capture.capture());
        assertEquals(original, capture.getValue().getFullResult());
        when(repository.findById(first.storedId())).thenReturn(Optional.of(capture.getValue()));
        int records = 0;
        var current = first;
        while (current != null) {
            records += current.content().split("\\\"id\\\"", -1).length - 1;
            current = current.nextCursor() == null ? null : store.page(first.storedId(), current.nextCursor(), context, 90).delivery();
        }
        assertEquals(100, records);
    }

    @Test
    void foreignOrExpiredResultIsRejected() {
        StoredToolResultRepository repository = mock(StoredToolResultRepository.class);
        ToolResultStore store = new ToolResultStore(repository, new TokenEstimator(), new AgentProperties());
        UUID storedId = UUID.randomUUID();
        StoredToolResult saved = StoredToolResult.builder().id(storedId).ownerId(context.userId())
                .conversationId(context.conversationId()).runId(context.runId()).roleScope("ROLE_STUDENT")
                .fullResult("[]").expiresAt(Instant.now().minusSeconds(1)).build();
        when(repository.findById(storedId)).thenReturn(Optional.of(saved));
        assertThrows(SecurityException.class, () -> store.page(storedId, "i:0", context, 90));
        saved.setExpiresAt(Instant.now().plusSeconds(60));
        AgentContext foreign = new AgentContext(UUID.randomUUID(), "", context.roles(),
                context.conversationId(), context.runId(), "");
        assertThrows(SecurityException.class, () -> store.page(storedId, "i:0", foreign, 90));
    }

    @Test
    void pageCallbackReadsSavedResultWithoutDomainCall() throws Exception {
        StoredToolResultRepository repository = mock(StoredToolResultRepository.class);
        ToolResultStore store = new ToolResultStore(repository, new TokenEstimator(), new AgentProperties());
        UUID storedId = UUID.randomUUID();
        when(repository.findById(storedId)).thenReturn(Optional.of(StoredToolResult.builder().id(storedId)
                .ownerId(context.userId()).conversationId(context.conversationId()).runId(context.runId())
                .roleScope("ROLE_STUDENT").fullResult("[1,2,3]")
                .expiresAt(Instant.now().plusSeconds(60)).build()));
        String output = new GetToolResultPageTool(store, new com.fasterxml.jackson.databind.ObjectMapper(), 90)
                .callback(context).call("{\"storedId\":\"" + storedId + "\",\"cursor\":\"i:0\"}");
        assertTrue(output.contains("[1,2,3]"));
        verify(repository).findById(storedId);
    }

    @Test
    void longUnicodeScalarAndSingleJsonElementCanBeReconstructedExactly() {
        StoredToolResultRepository repository = mock(StoredToolResultRepository.class);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        ToolResultStore store = new ToolResultStore(repository, new TokenEstimator(), new AgentProperties());
        for (String original : List.of("😀 tiếng Việt ".repeat(100), "[{\"note\":\"" + "đ".repeat(1000) + "\"}]")) {
            var first = store.offer(original, context, 90);
            var capture = org.mockito.ArgumentCaptor.forClass(StoredToolResult.class);
            verify(repository, atLeastOnce()).save(capture.capture());
            when(repository.findById(first.storedId())).thenReturn(Optional.of(capture.getValue()));
            StringBuilder reconstructed = new StringBuilder();
            var current = first;
            while (current != null) {
                reconstructed.append(current.content());
                current = current.nextCursor() == null ? null : store.page(first.storedId(), current.nextCursor(), context, 90).delivery();
            }
            assertEquals(original, reconstructed.toString());
        }
    }
}
