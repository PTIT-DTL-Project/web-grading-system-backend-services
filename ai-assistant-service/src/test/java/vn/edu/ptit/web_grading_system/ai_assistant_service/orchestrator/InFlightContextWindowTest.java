package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ToolResultStore;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InFlightContextWindowTest {
    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void archivesOlderMessagesAndKeepsLatestResultWithinBudget() {
        AgentProperties properties = constrainedProperties();
        ToolResultStore store = mock(ToolResultStore.class);
        UUID storedId = UUID.randomUUID();
        when(store.reference(anyString(), any())).thenReturn(storedId);
        AgentContext context = context();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("old assignment result " + "a".repeat(1800)),
                new UserMessage("old page result " + "b".repeat(1600)),
                new UserMessage("latest test plan result: planId=123")));
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator, store);

        window.fit(messages, "system".repeat(350), List.of(), "create step", context);

        assertEquals(2, messages.size());
        assertTrue(messages.getFirst().getText().contains(storedId.toString()));
        assertEquals("latest test plan result: planId=123", messages.getLast().getText());
        assertTrue(window.estimateMessages(messages) <=
                window.budget("system".repeat(350), List.of(), "create step"));
        verify(store).reference(argThat(value -> value.contains("old assignment result")
                && value.contains("old page result")), eq(context));
    }

    @Test
    void archivesSingleOversizedResultInsteadOfFailingOnLatestUserMessage() {
        AgentProperties properties = constrainedProperties();
        ToolResultStore store = mock(ToolResultStore.class);
        UUID storedId = UUID.randomUUID();
        when(store.reference(anyString(), any())).thenReturn(storedId);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("large tool result " + "x".repeat(3600))));
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator, store);

        window.fit(messages, "system".repeat(350), List.of(), "create step", context());

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().getText().contains(storedId.toString()));
        assertTrue(window.estimateMessages(messages) <=
                window.budget("system".repeat(350), List.of(), "create step"));
    }

    @Test
    void keepsLatestPlanResultWhenArchiveReferenceWouldCrowdItOut() {
        AgentProperties properties = constrainedProperties();
        properties.getContext().setMaxInputTokens(1600);
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.reference(anyString(), any())).thenReturn(UUID.randomUUID());
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("Kết quả tool listVisibleAssignments: " + "a".repeat(3000)),
                new UserMessage("Kết quả tool listTestPlans: [{\"id\":\"plan-123\",\"steps\":[1,2]}]"
                        + "\nTham số tra cứu: {\"assignmentId\":\"assignment-1\"}")));
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator, store);
        String prompt = "system".repeat(350);

        window.fit(messages, prompt, List.of(), "create step", context());

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().getText().contains("plan-123"));
        assertFalse(messages.getFirst().getText().contains("storedId"));
    }

    @Test
    void repeatedCompactionKeepsOriginalResultsInOneRetrievableArchive() {
        AgentProperties properties = constrainedProperties();
        ToolResultStore store = mock(ToolResultStore.class);
        UUID firstId = UUID.randomUUID(), secondId = UUID.randomUUID();
        when(store.reference(anyString(), any())).thenReturn(firstId, secondId);
        AgentContext context = context();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("first result " + "a".repeat(2400)),
                new UserMessage("second result " + "b".repeat(600))));
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator, store);

        window.fit(messages, "system".repeat(350), List.of(), "create step", context);
        messages.add(new UserMessage("third result " + "c".repeat(2400)));
        window.fit(messages, "system".repeat(350), List.of(), "create step", context);

        var archives = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).reference(archives.capture(), eq(context));
        assertTrue(archives.getAllValues().get(1).contains("first result"));
        assertTrue(archives.getAllValues().get(1).contains("second result"));
        assertFalse(archives.getAllValues().get(1).contains(firstId.toString()));
    }

    @Test
    void archiveStartsWithMostRecentLookupResult() {
        AgentProperties properties = constrainedProperties();
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.reference(anyString(), any())).thenReturn(UUID.randomUUID());
        AgentContext context = context();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("Kết quả tool listVisibleAssignments: " + "a".repeat(2400)),
                new UserMessage("Kết quả tool listTestPlans: [{\"id\":\"plan-123\",\"steps\":[]}]")));
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator, store);

        window.archiveAll(messages, context);

        var archived = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).reference(archived.capture(), eq(context));
        assertTrue(archived.getValue().startsWith("Kết quả tool listTestPlans: "));
        assertTrue(archived.getValue().contains("plan-123"));
    }

    @Test
    void overflowRecoveryKeepsFreshPlanResultInsteadOfArchivingItAgain() {
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.reference(anyString(), any())).thenReturn(UUID.randomUUID());
        InFlightContextWindow window = new InFlightContextWindow(constrainedProperties(), estimator, store);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("Kết quả tool listVisibleAssignments: " + "a".repeat(2400)),
                new UserMessage("Kết quả tool listTestPlans: [{\"id\":\"plan-123\",\"steps\":[1,2]}]"
                        + "\nTham số tra cứu: {\"assignmentId\":\"assignment-1\"}")));

        assertTrue(window.archiveAll(messages, context()));

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().getText().contains("plan-123"));
        assertTrue(messages.getFirst().getText().contains("assignment-1"));
        verify(store, never()).reference(anyString(), any());
    }

    @Test
    void newerLookupPrecedesOlderArchiveAfterSecondCompaction() {
        ToolResultStore store = mock(ToolResultStore.class);
        when(store.reference(anyString(), any())).thenReturn(UUID.randomUUID(), UUID.randomUUID());
        AgentContext context = context();
        InFlightContextWindow window = new InFlightContextWindow(constrainedProperties(), estimator, store);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("Kết quả tool listVisibleAssignments: many assignments"),
                new UserMessage("Kết quả tool listTestPlans: plan-123")));

        window.archiveAll(messages, context);
        messages.add(new UserMessage("Kết quả tool getTestPlan: plan-123 with no steps"));
        window.archiveAll(messages, context);

        var archives = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).reference(archives.capture(), eq(context));
        assertTrue(archives.getAllValues().get(1).startsWith("Kết quả tool getTestPlan: "));
        assertTrue(archives.getAllValues().get(1).contains("Kết quả tool listTestPlans: plan-123"));
    }

    @Test
    void pageResultStaysVisibleWithoutCreatingAnotherArchiveOrResettingItsCursor() {
        AgentProperties properties = constrainedProperties();
        properties.getContext().setMaxInputTokens(1650);
        ToolResultStore store = mock(ToolResultStore.class);
        UUID archiveId = UUID.randomUUID();
        UUID pageId = UUID.randomUUID();
        when(store.reference(anyString(), any())).thenReturn(archiveId);
        AgentContext context = context();
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator, store);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>(List.of(
                new UserMessage("old result " + "a".repeat(3000))));
        String prompt = "system".repeat(350);
        window.fit(messages, prompt, List.of(), "create step", context);

        window.recordPage(messages, "{\"storedId\":\"" + pageId
                + "\",\"content\":\"first page" + "x".repeat(90) + "\",\"nextCursor\":\"t:100\"}");
        window.fit(messages, prompt, List.of(), "create step", context);
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().getText().contains("first page"));

        window.recordPage(messages, "{\"storedId\":\"" + pageId
                + "\",\"content\":\"second page" + "y".repeat(90) + "\",\"nextCursor\":\"t:200\"}");
        window.fit(messages, prompt, List.of(), "create step", context);
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().getText().contains("t:200"));
        assertFalse(messages.getFirst().getText().contains("first page"));
        verify(store, times(1)).reference(anyString(), eq(context));
    }

    @Test
    void keepsEarlierPagesWhileTheyFitTheContext() {
        AgentProperties properties = constrainedProperties();
        InFlightContextWindow window = new InFlightContextWindow(properties, estimator,
                mock(ToolResultStore.class));
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        String prompt = "system".repeat(100);

        window.recordPage(messages, "{\"content\":\"first\",\"nextCursor\":\"t:5\"}");
        window.fit(messages, prompt, List.of(), "create step", context());
        window.recordPage(messages, "{\"content\":\"second\",\"nextCursor\":\"t:11\"}");
        window.fit(messages, prompt, List.of(), "create step", context());

        assertEquals(2, messages.size());
        assertTrue(messages.getFirst().getText().contains("first"));
        assertTrue(messages.getLast().getText().contains("second"));
    }

    private AgentProperties constrainedProperties() {
        AgentProperties properties = new AgentProperties();
        properties.getContext().setMaxInputTokens(2500);
        properties.getContext().setOutputReserve(500);
        properties.getContext().setSafetyMargin(300);
        properties.getContext().setToolMaxTokens(1200);
        return properties;
    }

    private AgentContext context() {
        return new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "");
    }
}
