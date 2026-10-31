package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface RunRepository extends JpaRepository<Run, UUID> {
    List<Run> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    List<Run> findByStatusIn(List<RunStatus> statuses);

    @Modifying
    @Transactional
    @Query("update Run r set r.status = :cancelled, r.finishedAt = :finishedAt "
            + "where r.id = :id and r.status in :activeStatuses")
    int cancelActiveRun(@Param("id") UUID id, @Param("cancelled") RunStatus cancelled,
                        @Param("finishedAt") Instant finishedAt,
                        @Param("activeStatuses") List<RunStatus> activeStatuses);
}
