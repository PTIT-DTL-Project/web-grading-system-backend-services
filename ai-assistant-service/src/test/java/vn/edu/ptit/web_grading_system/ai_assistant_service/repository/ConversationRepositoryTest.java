package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testrepo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@Transactional
public class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testConversationAndMessageQueries() {
        UUID ownerId = UUID.randomUUID();
        Conversation conv = conversationRepository.save(Conversation.builder()
                .ownerId(ownerId)
                .title("My Test Chat")
                .build());

        assertNotNull(conv.getId());
        Page<Conversation> page = conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());

        Message msg1 = messageRepository.save(Message.builder()
                .conversationId(conv.getId())
                .role("user")
                .content("Prompt 1")
                .sequenceNo(1)
                .clientMessageId("client-123")
                .build());

        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNoAsc(conv.getId());
        assertEquals(1, messages.size());

        Optional<Message> byClient = messageRepository.findByConversationIdAndClientMessageId(conv.getId(), "client-123");
        assertTrue(byClient.isPresent());

        int maxSeq = messageRepository.findMaxSequenceNo(conv.getId());
        assertEquals(1, maxSeq);

        Run run = runRepository.save(Run.builder()
                .conversationId(conv.getId())
                .status(RunStatus.QUEUED)
                .version(1)
                .build());
        assertNotNull(run.getId());

        List<Run> queuedRuns = runRepository.findByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
        assertEquals(1, queuedRuns.size());
    }

    @Test
    void generatedTitleIsOnlySavedWhileConversationIsUntitled() {
        Conversation conv = conversationRepository.saveAndFlush(Conversation.builder()
                .ownerId(UUID.randomUUID())
                .build());

        assertEquals(1, conversationRepository.setGeneratedTitleIfUntitled(conv.getId(), "Tên AI"));
        entityManager.clear();
        assertEquals("Tên AI", conversationRepository.findById(conv.getId()).orElseThrow().getTitle());

        Conversation renamed = conversationRepository.findById(conv.getId()).orElseThrow();
        renamed.setTitle("Tên người dùng");
        conversationRepository.saveAndFlush(renamed);

        assertEquals(0, conversationRepository.setGeneratedTitleIfUntitled(conv.getId(), "Tên AI khác"));
        entityManager.clear();
        assertEquals("Tên người dùng", conversationRepository.findById(conv.getId()).orElseThrow().getTitle());
    }

    @Test
    void scopedHistoryAndSearchExcludeEarlierLecturerRun() {
        Conversation conv = conversationRepository.save(Conversation.builder().ownerId(UUID.randomUUID()).build());
        Run lecturer = runRepository.save(Run.builder().conversationId(conv.getId())
                .callerRoles("ROLE_LECTURER").build());
        Run student = runRepository.save(Run.builder().conversationId(conv.getId())
                .callerRoles("ROLE_STUDENT").build());
        messageRepository.save(Message.builder().conversationId(conv.getId()).runId(lecturer.getId())
                .role("assistant").content("Quyết định về đáp án ẩn").sequenceNo(1).build());
        messageRepository.save(Message.builder().conversationId(conv.getId()).runId(student.getId())
                .role("user").content("Quyết định hạn nộp thứ Sáu").sequenceNo(2).build());
        messageRepository.flush();

        assertEquals(List.of("Quyết định hạn nộp thứ Sáu"),
                messageRepository.findScopedRecent(conv.getId(), "ROLE_STUDENT", PageRequest.of(0, 10))
                        .getContent().stream().map(Message::getContent).toList());
        assertTrue(messageRepository.searchScoped(conv.getId(), "ROLE_STUDENT", "đáp án", Integer.MAX_VALUE,
                PageRequest.of(0, 5)).isEmpty());
        assertEquals(1, messageRepository.searchScoped(conv.getId(), "ROLE_STUDENT", "hạn nộp", Integer.MAX_VALUE,
                PageRequest.of(0, 5)).getTotalElements());
    }
}
