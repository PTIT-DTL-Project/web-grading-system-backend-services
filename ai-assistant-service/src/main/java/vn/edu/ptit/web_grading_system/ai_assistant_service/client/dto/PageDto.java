package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Universal pagination DTO for Feign clients.
 * Supports:
 * 1) Enveloped ApiResponse<ResultPaginationDTO<T>>: {"status": 200, "data": {"meta": {...}, "result": [...]}}
 * 2) ResultPaginationDTO<T>: {"meta": {...}, "result": [...]}
 * 3) Spring Data standard Page: {"content": [...], "totalPages": 1, ...}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageDto<T> {

    private List<T> content;
    private List<T> result;
    private PageData<T> data;
    private Meta meta;
    private Integer totalPages;
    private Long totalElements;
    private Integer size;
    private Integer number;

    public PageDto(List<T> content, int totalPages, long totalElements, int size, int number) {
        this.content = content;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.size = size;
        this.number = number;
    }

    public List<T> getContent() {
        if (data != null) {
            if (data.getResult() != null) return data.getResult();
            if (data.getContent() != null) return data.getContent();
        }
        if (result != null) {
            return result;
        }
        if (content != null) {
            return content;
        }
        return Collections.emptyList();
    }

    public int getTotalPages() {
        if (totalPages != null) return totalPages;
        if (data != null && data.getMeta() != null) return data.getMeta().getPages();
        if (meta != null) return meta.getPages();
        return 1;
    }

    public long getTotalElements() {
        if (totalElements != null) return totalElements;
        if (data != null && data.getMeta() != null) return data.getMeta().getTotal();
        if (meta != null) return meta.getTotal();
        return getContent().size();
    }

    public int getSize() {
        if (size != null) return size;
        if (data != null && data.getMeta() != null) return data.getMeta().getPageSize();
        if (meta != null) return meta.getPageSize();
        return getContent().size();
    }

    public int getNumber() {
        if (number != null) return number;
        if (data != null && data.getMeta() != null) return data.getMeta().getPage();
        if (meta != null) return meta.getPage();
        return 0;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageData<T> {
        private Meta meta;
        private List<T> result;
        private List<T> content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private int page;
        private int pageSize;
        private int pages;
        private long total;
    }
}
