package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.Operation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.OperationStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.OperationRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationService {

    private final OperationRepository operationRepository;

    @Transactional(readOnly = true)
    public Optional<Operation> findByIdempotencyKey(String idempotencyKey) {
        return operationRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional
    public Operation recordOperation(UUID taskId, String idempotencyKey, String commandType, String payloadJson) {
        Optional<Operation> existing = operationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Operation op = Operation.builder()
                .taskId(taskId)
                .idempotencyKey(idempotencyKey)
                .commandType(commandType)
                .payloadJson(payloadJson)
                .status(OperationStatus.PENDING)
                .attempts(1)
                .build();
        return operationRepository.save(op);
    }

    @Transactional
    public void markSuccess(String idempotencyKey, String resultRefJson) {
        operationRepository.findByIdempotencyKey(idempotencyKey).ifPresent(op -> {
            op.setStatus(OperationStatus.SUCCEEDED);
            op.setResultRef(resultRefJson);
            op.setUpdatedAt(Instant.now());
            operationRepository.save(op);
        });
    }

    @Transactional
    public void markFailure(String idempotencyKey, String errorMessage) {
        operationRepository.findByIdempotencyKey(idempotencyKey).ifPresent(op -> {
            op.setStatus(OperationStatus.FAILED);
            op.setUpdatedAt(Instant.now());
            operationRepository.save(op);
        });
    }
}
