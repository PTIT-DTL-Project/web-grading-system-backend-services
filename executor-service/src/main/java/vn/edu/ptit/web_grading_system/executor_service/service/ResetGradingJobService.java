package vn.edu.ptit.web_grading_system.executor_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSaga;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaStepRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingStepResultRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetGradingJobService {

    private final GradingJobRepository gradingJobRepository;
    private final GradingSagaRepository gradingSagaRepository;
    private final GradingSagaStepRepository gradingSagaStepRepository;
    private final GradingStepResultRepository gradingStepResultRepository;

    @Transactional
    public ResetResult reset(UUID submissionId) {
        Optional<GradingJob> existing = gradingJobRepository.findBySubmissionId(submissionId);
        if (existing.isEmpty()) {
            log.warn(Constant.Message.RESET_NOT_FOUND_PREFIX, submissionId);
            return new ResetResult(false, "No grading job found for submission", null, null);
        }
        GradingJob job = existing.get();
        if (job.getStatus() == GradingJobStatus.DONE) {
            log.warn(Constant.Message.RESET_FAILED_PREFIX, submissionId, job.getStatus());
            return new ResetResult(false, "Job is already DONE, current status: " + job.getStatus(), job.getId(), job);
        }
        UUID jobId = job.getId();

        gradingStepResultRepository.deleteByJobId(jobId);
        List<GradingSaga> sagas = gradingSagaRepository.findByJobId(jobId);
        for (GradingSaga saga : sagas) {
            UUID sagaId = saga.getId();
            gradingSagaStepRepository.deleteBySagaId(sagaId);
            log.info("Deleted saga steps for saga={}, reset saga to STARTED for job={}", sagaId, jobId);
        }
        gradingSagaRepository.resetByJobId(jobId, SagaStatus.STARTED);

        job.setStatus(GradingJobStatus.PENDING);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setRetryCount(job.getRetryCount() + 1);
        gradingJobRepository.save(job);

        log.info(Constant.Message.RESET_PREFIX, jobId, submissionId, "PENDING");
        log.info(Constant.Message.RESET_SUCCESS_PREFIX, jobId, submissionId);
        return new ResetResult(true, "Job reset to PENDING", jobId, job);
    }

    public record ResetResult(boolean success, String message, UUID jobId, GradingJob job) {}
}
