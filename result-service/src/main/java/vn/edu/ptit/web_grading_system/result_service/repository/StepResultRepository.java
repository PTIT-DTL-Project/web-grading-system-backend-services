package vn.edu.ptit.web_grading_system.result_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.result_service.entity.StepResult;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StepResultRepository extends JpaRepository<StepResult, UUID> {

    List<StepResult> findByResultId(UUID resultId);

    /** Batched alternative to {@link #findByResultId} for the class-wide results view. */
    List<StepResult> findByResultIdIn(Collection<UUID> resultIds);
}
