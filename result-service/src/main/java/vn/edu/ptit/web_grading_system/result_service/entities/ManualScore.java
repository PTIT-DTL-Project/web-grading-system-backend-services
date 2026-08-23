package vn.edu.ptit.web_grading_system.result_service.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.SQLRestriction;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "manual_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ManualScore extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "student_code", nullable = false)
    private String studentCode;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    private String comment;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}