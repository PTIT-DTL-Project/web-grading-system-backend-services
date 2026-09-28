package vn.edu.ptit.web_grading_system.course_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.SQLRestriction;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "docker_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DockerImage extends BaseEntity {

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    private String description;
}