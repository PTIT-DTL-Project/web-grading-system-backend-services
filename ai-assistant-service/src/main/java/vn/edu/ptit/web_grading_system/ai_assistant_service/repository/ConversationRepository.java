package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Page<Conversation> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId, Pageable pageable);
    Optional<Conversation> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Modifying
    @Transactional
    @Query("UPDATE Conversation c SET c.title = :title WHERE c.id = :id AND c.title IS NULL")
    int setGeneratedTitleIfUntitled(@Param("id") UUID id, @Param("title") String title);
}
