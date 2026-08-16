package vn.edu.ptit.web_grading_system.assignment_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "assignment_docker_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AssignmentDockerImage extends BaseEntity {

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "docker_image_id", nullable = false)
    private UUID dockerImageId;
}