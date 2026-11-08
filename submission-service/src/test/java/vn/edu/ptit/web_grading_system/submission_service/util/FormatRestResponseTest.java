package vn.edu.ptit.web_grading_system.submission_service.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatRestResponseTest {

    @Test
    void supports_rejectsByteArrayConverter() {
        FormatRestResponse advice = new FormatRestResponse();
        assertFalse(advice.supports(null, ByteArrayHttpMessageConverter.class));
    }

    @Test
    void supports_acceptsOtherConverters() {
        FormatRestResponse advice = new FormatRestResponse();
        assertTrue(advice.supports(null, MappingJackson2HttpMessageConverter.class));
    }
}
