package vn.edu.ptit.web_grading_system.course_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entity.StudentScore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, UUID> {

    List<StudentScore> findAllByClassIdAndStudentCode(UUID classId, String studentCode);

    List<StudentScore> findAllByClassId(UUID classId);

    Optional<StudentScore> findByClassIdAndStudentCodeAndComponentId(
            UUID classId, String studentCode, UUID componentId);
}