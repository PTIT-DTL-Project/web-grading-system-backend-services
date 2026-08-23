package vn.edu.ptit.web_grading_system.course_service.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ResultPaginationDTO;
import vn.edu.ptit.web_grading_system.course_service.util.annotation.ApiMessage;

@ControllerAdvice
public class FormatRestResponse implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        if (isExcluded(request.getURI().getPath())) {
            return body;
        }
        ApiMessage message = returnType.getMethodAnnotation(ApiMessage.class);
        return wrap(body, servletResponse.getStatus(),
                message != null ? message.value() : "Success");
    }

    static Object wrap(Object body, int status, String message) {
        // already enveloped (exception handler), streamed, or not JSON
        if (body instanceof ApiResponse<?> || body instanceof String || body instanceof Resource) {
            return body;
        }
        if (status >= 400) {
            return body;
        }
        if (body instanceof Page<?> page) {
            return ApiResponse.<Object>builder()
                    .status(status)
                    .message(message)
                    .data(ResultPaginationDTO.from(page))
                    .build();
        }
        return ApiResponse.<Object>builder()
                .status(status)
                .message(message)
                .data(body)
                .build();
    }

    static boolean isExcluded(String path) {
        return path.contains("/internal/") || path.contains("/webhook")
                || path.endsWith("/health") || path.endsWith("/version")
                // springdoc controllers must stay raw or swagger-ui breaks
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html");
    }
}