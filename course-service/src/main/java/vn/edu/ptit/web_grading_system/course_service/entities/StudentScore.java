package vn.edu.ptit.web_grading_system.course_service.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "student_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StudentScore extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "student_code", nullable = false)
    private String studentCode;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal score;
}