package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.StoredToolResult;

import java.time.Instant;
import java.util.UUID;

public interface StoredToolResultRepository extends JpaRepository<StoredToolResult, UUID> {
    long deleteByExpiresAtBefore(Instant cutoff);
}
