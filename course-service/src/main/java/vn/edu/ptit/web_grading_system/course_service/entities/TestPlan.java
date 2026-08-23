package vn.edu.ptit.web_grading_system.course_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.SQLRestriction;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "test_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TestPlan extends BaseEntity {

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "sequence_order", nullable = false)
    @Builder.Default
    private Integer sequenceOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer weight = 1;
}