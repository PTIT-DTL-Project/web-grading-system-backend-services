package vn.edu.ptit.web_grading_system.executor_service.entities;

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
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "docker_image_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DockerImageState extends BaseEntity {

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "pod_id", nullable = false, length = 255)
    private String podId;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageScanStatus status;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "last_pulled_at")
    private OffsetDateTime lastPulledAt;

    @Column(name = "error_message")
    private String errorMessage;
}
