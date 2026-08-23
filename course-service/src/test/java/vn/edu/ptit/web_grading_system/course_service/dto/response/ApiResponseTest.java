package vn.edu.ptit.web_grading_system.course_service.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ok_hasNoErrorField() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(ApiResponse.ok("x", "done")));
        assertEquals(200, json.get("status").asInt());
        assertEquals("done", json.get("message").asText());
        assertEquals("x", json.get("data").asText());
        assertFalse(json.has("error"));
    }

    @Test
    void resultPagination_fromPage() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(2, 10), 42);
        ResultPaginationDTO<String> dto = ResultPaginationDTO.from(page);
        assertEquals(2, dto.getMeta().getPage());
        assertEquals(10, dto.getMeta().getPageSize());
        assertEquals(5, dto.getMeta().getPages()); // ceil(42/10)
        assertEquals(42, dto.getMeta().getTotal());
        assertEquals(2, dto.getResult().size());
    }

    @Test
    void error_hasDataNull() {
        ApiResponse<Void> response = ApiResponse.error(404, "Not found", "Class not found: 1");
        assertNull(response.getData());
        assertEquals(404, response.getStatus());
        assertEquals("Class not found: 1", response.getError());
    }
}