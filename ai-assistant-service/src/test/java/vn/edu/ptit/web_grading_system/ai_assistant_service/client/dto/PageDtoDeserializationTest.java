package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PageDtoDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Test
    void testEnvelopedResponse() throws Exception {
        String json = "{\n" +
                "  \"status\": 200,\n" +
                "  \"message\": \"Success\",\n" +
                "  \"data\": {\n" +
                "    \"meta\": {\n" +
                "      \"page\": 0,\n" +
                "      \"pageSize\": 20,\n" +
                "      \"pages\": 1,\n" +
                "      \"total\": 1\n" +
                "    },\n" +
                "    \"result\": [\n" +
                "      {\n" +
                "        \"id\": \"3fb08dbb-c203-40e5-a67f-ada553962db0\",\n" +
                "        \"ownerId\": \"49f0abd4-35df-4d38-ae35-ede87f97f409\",\n" +
                "        \"name\": \"Test\",\n" +
                "        \"semester\": \"20261\",\n" +
                "        \"status\": \"ACTIVE\",\n" +
                "        \"createdAt\": \"2026-10-04T11:04:43.825678Z\"\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        PageDto<ClassSummaryDto> result = objectMapper.readValue(json, new TypeReference<PageDto<ClassSummaryDto>>() {});
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        ClassSummaryDto first = result.getContent().get(0);
        assertEquals("Test", first.getName());
        assertEquals("ACTIVE", first.getStatus());
        assertEquals(1, result.getTotalPages());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testStandardSpringDataPage() throws Exception {
        String json = "{\n" +
                "  \"content\": [\n" +
                "    {\n" +
                "      \"id\": \"3fb08dbb-c203-40e5-a67f-ada553962db0\",\n" +
                "      \"name\": \"Test Standard\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"totalPages\": 2,\n" +
                "  \"totalElements\": 25,\n" +
                "  \"size\": 20,\n" +
                "  \"number\": 0\n" +
                "}";

        PageDto<ClassSummaryDto> result = objectMapper.readValue(json, new TypeReference<PageDto<ClassSummaryDto>>() {});
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test Standard", result.getContent().get(0).getName());
        assertEquals(2, result.getTotalPages());
        assertEquals(25, result.getTotalElements());
    }
}
