package vn.edu.ptit.web_grading_system.submission_service.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import vn.edu.ptit.web_grading_system.submission_service.entities.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Submission extends BaseEntity {

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "rustfs_path", nullable = false)
    private String rustfsPath;

    @Column(name = "zip_file_name")
    private String zipFileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Boolean latest = true;
}
