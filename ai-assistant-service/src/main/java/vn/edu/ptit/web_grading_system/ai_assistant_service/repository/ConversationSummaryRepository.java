package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.ConversationSummary;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationSummaryRepository extends JpaRepository<ConversationSummary, UUID> {
    Optional<ConversationSummary> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    Optional<ConversationSummary> findTopByConversationIdAndRoleScopeOrderByCreatedAtDesc(UUID conversationId, String roleScope);
}
