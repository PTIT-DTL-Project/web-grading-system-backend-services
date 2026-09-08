package vn.edu.ptit.web_grading_system.executor_service.service.step;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.HttpLogService;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpStepExecutorTest {

    private final HttpLogService httpLogService = Mockito.mock(HttpLogService.class);
    private final HttpClient httpClient = Mockito.mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpStepExecutor executor =
            new HttpStepExecutor(httpLogService, new AssertionEngine(), httpClient);

    private HttpStepExecutor.StepContext ctx(String configJson, VariableContext vars) {
        return new HttpStepExecutor.StepContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, "step", mapper.readTree(configJson), vars, 5000);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> r = Mockito.mock(HttpResponse.class);
        Mockito.when(r.statusCode()).thenReturn(status);
        Mockito.when(r.body()).thenReturn(body);
        Mockito.when(r.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
        return r;
    }

    @Test
    void happyPath_passesAndExtractsVariable() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(inv -> mockResponse(201, "{\"id\":\"abc-123\"}"));

        VariableContext vars = new VariableContext();
        vars.put("app_port", 23456);
        var result = executor.execute(ctx("""
                {"method":"POST","path":"/api/v1/books","expected_status":201,
                 "extract":[{"name":"bookId","from":"response_body","expression":"$.id"}]}""", vars));

        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertEquals(201, result.getActualStatusCode());
        assertEquals("abc-123", vars.get("bookId"));
        assertNotNull(result.getExtractedVariables());
        assertTrue(result.getRequestUrl().contains("23456"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void failingStatusAssertion_marksFailed() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                .thenAnswer(inv -> mockResponse(404, "{}"));

        VariableContext vars = new VariableContext();
        vars.put("app_port", 23456);
        var result = executor.execute(ctx("""
                {"method":"GET","path":"/api/v1/books","expected_status":200}""", vars));

        assertEquals(StepResultStatus.FAILED, result.getStatus());
        assertNotNull(result.getAssertionResult());
    }

    @Test
    void httpError_marksErrorAndPersistsLog() throws IOException, InterruptedException {
        Mockito.when(httpClient.send(Mockito.any(), Mockito.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new IOException("Connection refused"));

        VariableContext vars = new VariableContext();
        vars.put("app_port", 23456);
        var result = executor.execute(ctx("{\"method\":\"GET\",\"path\":\"/x\"}", vars));

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals("Connection refused", result.getErrorMessage());
        Mockito.verify(httpLogService).save(Mockito.any());
    }
}