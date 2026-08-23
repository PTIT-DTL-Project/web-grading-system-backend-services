package vn.edu.ptit.web_grading_system.course_service.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "score_components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ScoreComponent extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScoreComponentType type;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;
}