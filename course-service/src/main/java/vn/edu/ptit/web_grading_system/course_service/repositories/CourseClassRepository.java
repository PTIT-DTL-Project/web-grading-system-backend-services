package vn.edu.ptit.web_grading_system.course_service.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entities.CourseClass;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseClassRepository extends JpaRepository<CourseClass, UUID> {

    Optional<CourseClass> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<CourseClass> findAllByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByOwnerIdAndNameAndSemester(UUID ownerId, String name, String semester);
}