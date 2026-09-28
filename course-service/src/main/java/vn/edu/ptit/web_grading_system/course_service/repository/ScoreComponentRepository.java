package vn.edu.ptit.web_grading_system.course_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entity.ScoreComponent;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScoreComponentRepository extends JpaRepository<ScoreComponent, UUID> {

    List<ScoreComponent> findAllByClassId(UUID classId);
}