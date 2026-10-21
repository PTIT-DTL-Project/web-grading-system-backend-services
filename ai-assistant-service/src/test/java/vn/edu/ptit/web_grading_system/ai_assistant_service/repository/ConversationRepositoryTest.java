package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

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
}
