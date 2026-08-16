package vn.edu.ptit.web_grading_system.executor_service.entities;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "http_log")
public class HttpLog extends BaseEntity {

    @Column(name = "service_name", nullable = false, length = 50)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private HttpLogDirection direction;

    @Column(name = "method", nullable = false, length = 10)
    private String method;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "port")
    private Integer port;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers", columnDefinition = "jsonb")
    private String requestHeaders;

    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "status_code")
    private Integer statusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", columnDefinition = "jsonb")
    private String responseHeaders;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;
}