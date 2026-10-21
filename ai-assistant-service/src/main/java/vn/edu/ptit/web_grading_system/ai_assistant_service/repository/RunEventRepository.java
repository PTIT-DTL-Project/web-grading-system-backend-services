package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunEvent;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunEventRepository extends JpaRepository<RunEvent, Long> {
    List<RunEvent> findByRunIdOrderBySequenceAsc(UUID runId);
}
