package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContextBuilderTest {

    @Test
    void testBuildPromptContainsRoleAndRules() {
        ContextBuilder builder = new ContextBuilder();

        String prompt = builder.buildSystemPrompt("ROLE_STUDENT", null);
        assertTrue(prompt.contains("WGS"), "Prompt should mention WGS");
        assertTrue(prompt.contains("ROLE_STUDENT"), "Prompt should mention role");
    }

    @Test
    void testBuildPromptWithTask() {
        ContextBuilder builder = new ContextBuilder();
        Task task = Task.builder()
                .stateJson("{\"step\":\"SELECT_ASSIGNMENT\"}")
                .build();

        String prompt = builder.buildSystemPrompt("ROLE_LECTURER", task);
        assertTrue(prompt.contains("ROLE_LECTURER"));
        assertTrue(prompt.contains("SELECT_ASSIGNMENT"));
    }

    @Test
    void testTokenEstimator() {
        TokenEstimator estimator = new TokenEstimator();
        assertEquals(0, estimator.estimateTokens(""));
        assertEquals(0, estimator.estimateTokens(null));
        assertTrue(estimator.estimateTokens("Hello world") > 0);
    }

    @Test
    void formatsDynamicToolCatalogForLecturerRole() {
        vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry registry =
                org.mockito.Mockito.mock(vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry.class);
        vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool createPlan =
                org.mockito.Mockito.mock(vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool.class);
        org.mockito.Mockito.when(createPlan.getName()).thenReturn("createTestQuestion");
        org.mockito.Mockito.when(createPlan.getDescription()).thenReturn("Tạo câu kiểm tra kèm bước chấm.");
        org.mockito.Mockito.when(createPlan.getDefinition()).thenReturn(
                new vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolDefinition(
                        "createTestQuestion", "Tạo câu kiểm tra kèm bước chấm.", "1.0", "lecturer", "write", "domain",
                        vn.edu.ptit.web_grading_system.ai_assistant_service.tools.EffectType.DOMAIN_WRITE,
                        java.util.Set.of("ROLE_LECTURER"), java.time.Duration.ofSeconds(5), 1000));

        vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool listAssign =
                org.mockito.Mockito.mock(vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool.class);
        org.mockito.Mockito.when(listAssign.getName()).thenReturn("listVisibleAssignments");
        org.mockito.Mockito.when(listAssign.getDescription()).thenReturn("Liệt kê bài tập trong lớp.");
        org.mockito.Mockito.when(listAssign.getDefinition()).thenReturn(
                new vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolDefinition(
                        "listVisibleAssignments", "Liệt kê bài tập trong lớp.", "1.0", "course", "read", "course",
                        vn.edu.ptit.web_grading_system.ai_assistant_service.tools.EffectType.READ,
                        java.util.Set.of("ROLE_LECTURER"), java.time.Duration.ofSeconds(5), 1000));

        org.mockito.Mockito.when(registry.getToolsForRoles(java.util.Set.of("ROLE_LECTURER")))
                .thenReturn(java.util.List.of(createPlan, listAssign));

        ContextBuilder builder = new ContextBuilder(registry);
        String catalog = builder.formatToolCatalog(java.util.Set.of("ROLE_LECTURER"));

        assertTrue(catalog.contains("createTestQuestion"));
        assertTrue(catalog.contains("listVisibleAssignments"));
        assertTrue(catalog.contains("Kiểm thử & Bước chấm"));
        assertTrue(catalog.contains("Quản lý Bài tập"));

        String prompt = builder.buildSystemPrompt("ROLE_LECTURER", null);
        assertTrue(prompt.contains("DANH MỤC CÔNG CỤ KHẢ DỤNG CHO VAI TRÒ"));
        assertTrue(prompt.contains("createTestQuestion"));
        assertTrue(prompt.contains("search_tools"));
    }

    @Test
    void handlesNullRegistryGracefully() {
        ContextBuilder builder = new ContextBuilder();
        assertEquals("", builder.formatToolCatalog(java.util.Set.of("ROLE_LECTURER")));
        String prompt = builder.buildSystemPrompt("ROLE_LECTURER", null);
        org.junit.jupiter.api.Assertions.assertNotNull(prompt);
        assertTrue(prompt.contains("ROLE_LECTURER"));
    }
}
