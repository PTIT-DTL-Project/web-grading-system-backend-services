package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadToolCallGuardTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new JacksonConfig().objectMapper();

    @Test
    void outOfRangePageWithMatchesReadsFirstPageOnce() throws Exception {
        ReadToolCallGuard guard = new ReadToolCallGuard(mapper);
        List<String> inputs = new ArrayList<>();
        java.util.function.Function<String, String> execute = input -> {
            inputs.add(input);
            int page = Integer.parseInt(input.replaceAll(".*\\\"page\\\":(\\d+).*", "$1"));
            return page == 0
                    ? "{\"items\":[{\"id\":\"a1\",\"title\":\"Quản lý Thư viện\"},"
                        + "{\"id\":\"a2\",\"title\":\"Quản lý Thư viện – Ứng dụng Web\"}],"
                        + "\"totalElements\":2,\"totalPages\":1,\"page\":0}"
                    : "{\"items\":[],\"totalElements\":2,\"totalPages\":1,\"page\":1}";
        };

        String result = guard.call("listVisibleAssignments",
                "{\"search\":\"Quản lý Thư viện\",\"page\":1,\"size\":10}", execute);

        assertEquals(2, inputs.size());
        assertEquals(0, mapper.readTree(inputs.get(1)).path("page").asInt());
        assertEquals(2, mapper.readTree(result).path("items").size());
        assertEquals(1, mapper.readTree(result).path("requestedPage").asInt());
        assertEquals(0, mapper.readTree(result).path("servedPage").asInt());
        assertEquals(1, mapper.readTree(result).path("exactMatches").size());
        assertEquals("a1", mapper.readTree(result).path("exactMatches").get(0).path("id").asText());
    }

    @Test
    void identicalReadAlwaysReturnsCachedResultWithoutRepeatingBackendCall() throws Exception {
        ReadToolCallGuard guard = new ReadToolCallGuard(mapper);
        List<String> inputs = new ArrayList<>();
        java.util.function.Function<String, String> execute = input -> {
            inputs.add(input);
            return "{\"items\":[],\"totalElements\":0,\"totalPages\":0,\"page\":0}";
        };
        String input = "{\"search\":\"missing\",\"page\":0}";

        String first = guard.call("listVisibleAssignments", input, execute);
        String duplicate = guard.call("listVisibleAssignments", "{\"page\":0,\"search\":\"missing\"}", execute);
        String third = guard.call("listVisibleAssignments", input, execute);

        assertEquals(1, inputs.size());
        assertEquals(first, duplicate);
        assertEquals(first, third);
        assertEquals(1, inputs.size());
    }

    @Test
    void repeatedPlanLookupKeepsOriginalArrayShapeAndPlanId() throws Exception {
        ReadToolCallGuard guard = new ReadToolCallGuard(mapper);
        int[] backendCalls = {0};
        String input = "{\"assignmentId\":\"assignment-123\"}";
        java.util.function.Function<String, String> execute = ignored -> {
            backendCalls[0]++;
            return "[{\"id\":\"plan-123\",\"name\":\"Kiểm tra API thêm sách\",\"steps\":[]}]";
        };

        guard.call("listTestPlans", input, execute);
        guard.call("listTestPlans", input, execute);
        String third = guard.call("listTestPlans", input, execute);

        assertTrue(mapper.readTree(third).isArray());
        assertEquals("plan-123", mapper.readTree(third).get(0).path("id").asText());
        assertEquals(1, backendCalls[0]);
    }

    @Test
    void emptyFirstPageDoesNotRetryOrInventItems() throws Exception {
        ReadToolCallGuard guard = new ReadToolCallGuard(mapper);
        List<String> inputs = new ArrayList<>();
        String result = guard.call("listVisibleAssignments", "{\"page\":0}", input -> {
            inputs.add(input);
            return "{\"items\":[],\"totalElements\":0,\"totalPages\":0,\"page\":0}";
        });

        assertEquals(1, inputs.size());
        assertEquals(0, mapper.readTree(result).path("items").size());
    }

    @Test
    void failedReadCanBeRetriedAfterServiceRecovers() throws Exception {
        ReadToolCallGuard guard = new ReadToolCallGuard(mapper);
        int[] calls = {0};
        java.util.function.Function<String, String> execute = input -> ++calls[0] == 1
                ? "{\"success\":false,\"code\":\"COURSE_SERVICE_ERROR\",\"retryable\":true}"
                : "{\"items\":[{\"id\":\"a1\"}],\"page\":0,\"totalPages\":1,\"totalElements\":1}";
        String input = "{\"page\":0}";

        guard.call("listVisibleAssignments", input, execute);
        String recovered = guard.call("listVisibleAssignments", input, execute);

        assertEquals(2, calls[0]);
        assertEquals("a1", mapper.readTree(recovered).path("items").get(0).path("id").asText());
    }

    @Test
    void duplicateExactNamesRemainAmbiguous() throws Exception {
        ReadToolCallGuard guard = new ReadToolCallGuard(mapper);
        String result = guard.call("listVisibleAssignments", "{\"search\":\"Quản lý Thư viện\"}",
                input -> "{\"items\":[{\"id\":\"a1\",\"title\":\"Quản lý Thư viện\"},"
                        + "{\"id\":\"a2\",\"title\":\"Quản lý Thư viện\"}],"
                        + "\"page\":0,\"totalPages\":1,\"totalElements\":2}");

        assertEquals(2, mapper.readTree(result).path("exactMatchCount").asInt());
        assertEquals(2, mapper.readTree(result).path("exactMatches").size());
    }
}
