package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.PendingAction;

import java.util.List;
import java.util.UUID;

@Repository
public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {
    List<PendingAction> findByTaskIdAndStatus(UUID taskId, String status);
    List<PendingAction> findByRunId(UUID runId);
}
