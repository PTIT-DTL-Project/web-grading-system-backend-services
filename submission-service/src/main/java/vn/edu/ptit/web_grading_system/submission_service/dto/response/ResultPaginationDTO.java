package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultPaginationDTO<T> {
    private Meta meta;
    private List<T> result;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private int page;
        private int pageSize;
        private int pages;
        private long total;
    }

    public static <T> ResultPaginationDTO<T> from(Page<T> page) {
        return ResultPaginationDTO.<T>builder()
                .meta(Meta.builder()
                        .page(page.getNumber())
                        .pageSize(page.getSize())
                        .pages(page.getTotalPages())
                        .total(page.getTotalElements())
                        .build())
                .result(page.getContent())
                .build();
    }
}