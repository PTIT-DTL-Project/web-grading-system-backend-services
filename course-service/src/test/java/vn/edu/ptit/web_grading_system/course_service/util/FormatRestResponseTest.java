package vn.edu.ptit.web_grading_system.course_service.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ResultPaginationDTO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatRestResponseTest {

    @Test
    void wrap_plainBody() {
        Object body = new Object();
        ApiResponse<?> result = (ApiResponse<?>) FormatRestResponse.wrap(body, 201, "Class created");
        assertEquals(201, result.getStatus());
        assertEquals("Class created", result.getMessage());
        assertSame(body, result.getData());
    }

    @Test
    void wrap_page_mapsMetaInsideData() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(2, 10), 42);
        ApiResponse<?> result = (ApiResponse<?>) FormatRestResponse.wrap(page, 200, "Success");
        ResultPaginationDTO<?> dto = (ResultPaginationDTO<?>) result.getData();
        assertEquals(2, dto.getMeta().getPage());
        assertEquals(10, dto.getMeta().getPageSize());
        assertEquals(5, dto.getMeta().getPages());
        assertEquals(42, dto.getMeta().getTotal());
        assertEquals(2, dto.getResult().size());
    }

    @Test
    void wrap_passthrough_cases() {
        ApiResponse<Object> enveloped = ApiResponse.error(404, "Not found", "x");
        assertSame(enveloped, FormatRestResponse.wrap(enveloped, 404, "msg"));
        assertSame("raw", FormatRestResponse.wrap("raw", 200, "msg"));
        Object errorBody = new Object();
        assertSame(errorBody, FormatRestResponse.wrap(errorBody, 500, "msg"));
    }

    @Test
    void isExcluded_paths() {
        assertTrue(FormatRestResponse.isExcluded("/api/v1/internal/results/average"));
        assertTrue(FormatRestResponse.isExcluded("/api/v1/submissions/webhook/upload-complete"));
        assertTrue(FormatRestResponse.isExcluded("/api/v1/submissions/health"));
        assertTrue(FormatRestResponse.isExcluded("/v3/api-docs"));
        assertTrue(FormatRestResponse.isExcluded("/v3/api-docs/springdoc.json"));
        assertTrue(FormatRestResponse.isExcluded("/swagger-ui/index.html"));
        assertFalse(FormatRestResponse.isExcluded("/api/v1/classes"));
        assertFalse(FormatRestResponse.isExcluded("/api/v1/submissions"));
    }
}