package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderBySequenceNoAsc(UUID conversationId);
    Page<Message> findByConversationIdOrderBySequenceNoAsc(UUID conversationId, Pageable pageable);
    Optional<Message> findByConversationIdAndClientMessageId(UUID conversationId, String clientMessageId);

    @Query("SELECT COALESCE(MAX(m.sequenceNo), 0) FROM Message m WHERE m.conversationId = :conversationId")
    int findMaxSequenceNo(@Param("conversationId") UUID conversationId);

    @Query("SELECT m FROM Message m JOIN Run r ON r.id = m.runId WHERE m.conversationId = :conversationId AND r.callerRoles = :roleScope AND m.role IN ('user', 'assistant') ORDER BY m.sequenceNo DESC")
    Page<Message> findScopedRecent(@Param("conversationId") UUID conversationId,
                                   @Param("roleScope") String roleScope, Pageable pageable);

    @Query("SELECT m FROM Message m JOIN Run r ON r.id = m.runId WHERE m.conversationId = :conversationId AND r.callerRoles = :roleScope AND m.role IN ('user', 'assistant') AND m.sequenceNo < :beforeSequence ORDER BY m.sequenceNo DESC")
    Page<Message> findScopedBefore(@Param("conversationId") UUID conversationId,
                                   @Param("roleScope") String roleScope,
                                   @Param("beforeSequence") int beforeSequence, Pageable pageable);
}
