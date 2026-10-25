package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunRepository extends JpaRepository<Run, UUID> {
    List<Run> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    List<Run> findByStatusIn(List<RunStatus> statuses);
}
