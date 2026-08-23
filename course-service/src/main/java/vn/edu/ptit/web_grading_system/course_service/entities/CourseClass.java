package vn.edu.ptit.web_grading_system.course_service.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "classes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CourseClass extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String semester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClassStatus status = ClassStatus.ACTIVE;
}