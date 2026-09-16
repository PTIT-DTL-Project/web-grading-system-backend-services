package vn.edu.ptit.web_grading_system.executor_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSaga;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSagaStep;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStepStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaStepRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Best-effort saga tracking: every method swallows its own exceptions and
 * returns null on failure, so tracking can never break grading. Callers pass
 * the returned ids straight back in — null-safe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaTracker
{
    private final GradingSagaRepository sagaRepository;
    private final GradingSagaStepRepository stepRepository;

    public UUID begin(UUID jobId)
    {
        try
        {
            return sagaRepository.save(GradingSaga.builder()
                    .jobId(jobId)
                    .build()).getId();
        }
        catch (Exception e)
        {
            log.warn("Saga begin failed: {}", e.getMessage());
            return null;
        }
    }

    public UUID step(UUID sagaId, String name, UUID planId, UUID stepId)
    {
        if (sagaId == null)
        {
            return null;
        }
        try
        {
            currentStep(sagaId, name);
            return stepRepository.save(GradingSagaStep.builder()
                    .sagaId(sagaId)
                    .stepName(name)
                    .planId(planId)
                    .stepId(stepId)
                    .build()).getId();
        }
        catch (Exception e)
        {
            log.warn("Saga step failed: {}", e.getMessage());
            return null;
        }
    }

    public void finishStep(UUID stepRowId, SagaStepStatus status, String error)
    {
        if (stepRowId == null)
        {
            return;
        }
        try
        {
            GradingSagaStep row = stepRepository.findById(stepRowId).orElse(null);
            if (row == null)
            {
                return;
            }
            row.setStatus(status);
            row.setErrorMessage(error);
            row.setCompletedAt(OffsetDateTime.now());
            stepRepository.save(row);
        }
        catch (Exception e)
        {
            log.warn("Saga finishStep failed: {}", e.getMessage());
        }
    }

    public void finish(UUID sagaId, SagaStatus status)
    {
        if (sagaId == null)
        {
            return;
        }
        try
        {
            GradingSaga saga = sagaRepository.findById(sagaId).orElse(null);
            if (saga == null)
            {
                return;
            }
            saga.setStatus(status);
            saga.setCompletedAt(OffsetDateTime.now());
            sagaRepository.save(saga);
        }
        catch (Exception e)
        {
            log.warn("Saga finish failed: {}", e.getMessage());
        }
    }

    private void currentStep(UUID sagaId, String name)
    {
        try
        {
            GradingSaga saga = sagaRepository.findById(sagaId).orElse(null);
            if (saga != null)
            {
                saga.setCurrentStep(name);
                sagaRepository.save(saga);
            }
        }
        catch (Exception e)
        {
            log.warn("Saga currentStep failed: {}", e.getMessage());
        }
    }
}
