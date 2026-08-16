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
@Table(name = "class_students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ClassStudent extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "student_code", nullable = false)
    private String studentCode;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    private String email;
}