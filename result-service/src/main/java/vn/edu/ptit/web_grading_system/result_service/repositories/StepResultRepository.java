package vn.edu.ptit.web_grading_system.result_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.result_service.entities.StepResult;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepResultRepository extends JpaRepository<StepResult, UUID> {

    List<StepResult> findByResultId(UUID resultId);
}
