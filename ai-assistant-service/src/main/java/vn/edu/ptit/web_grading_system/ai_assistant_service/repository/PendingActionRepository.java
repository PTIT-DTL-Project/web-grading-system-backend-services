package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.PendingAction;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface PendingActionRepository extends JpaRepository<PendingAction, UUID> {
    List<PendingAction> findByTaskIdAndStatus(UUID taskId, String status);
    List<PendingAction> findByRunId(UUID runId);
    @org.springframework.data.jpa.repository.Query("select p from PendingAction p, Run r where p.runId = r.id and r.conversationId = :conversationId and p.status = 'PENDING' order by p.createdAt desc")
    List<PendingAction> findPendingByConversationId(@org.springframework.data.repository.query.Param("conversationId") UUID conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PendingAction p where p.id = :id")
    Optional<PendingAction> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
}
