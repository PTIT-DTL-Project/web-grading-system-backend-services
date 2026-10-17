package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.entity.AiExplanation;

import java.util.Optional;
import java.util.UUID;

public interface AiExplanationRepository extends JpaRepository<AiExplanation, UUID> {
    Optional<AiExplanation> findBySubmissionIdAndCategory(UUID submissionId, String category);
}
