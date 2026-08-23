package vn.edu.ptit.web_grading_system.course_service.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStudent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, UUID> {

    Page<ClassStudent> findAllByClassId(UUID classId, Pageable pageable);

    List<ClassStudent> findAllByClassId(UUID classId);

    Optional<ClassStudent> findByClassIdAndStudentCode(UUID classId, String studentCode);

    boolean existsByClassIdAndStudentCode(UUID classId, String studentCode);
}