package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSaga;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSagaStep;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStepStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaStepRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SagaTrackerTest {

    private SagaTracker tracker(GradingSagaRepository sagas, GradingSagaStepRepository steps) {
        return new SagaTracker(sagas, steps);
    }

    @Test
    void beginAndStepAndFinish() {
        GradingSagaRepository sagas = Mockito.mock(GradingSagaRepository.class);
        GradingSagaStepRepository steps = Mockito.mock(GradingSagaStepRepository.class);
        UUID jobId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID stepRowId = UUID.randomUUID();
        GradingSaga saga = GradingSaga.builder().jobId(jobId).build();
        saga.setId(sagaId);
        GradingSagaStep row = GradingSagaStep.builder()
                .sagaId(sagaId).stepName(Constant.Saga.FETCH_CONFIG).build();
        row.setId(stepRowId);
        Mockito.when(sagas.save(Mockito.any())).thenReturn(saga);
        Mockito.when(sagas.findById(sagaId)).thenReturn(Optional.of(saga));
        Mockito.when(steps.save(Mockito.any())).thenReturn(row);
        Mockito.when(steps.findById(stepRowId)).thenReturn(Optional.of(row));

        SagaTracker tracker = tracker(sagas, steps);
        assertEquals(sagaId, tracker.begin(jobId));
        assertEquals(stepRowId, tracker.step(sagaId, Constant.Saga.FETCH_CONFIG, null, null));
        tracker.finishStep(stepRowId, SagaStepStatus.DONE, null);
        tracker.finish(sagaId, SagaStatus.DONE);

        assertEquals(SagaStepStatus.DONE, row.getStatus());
        assertEquals(SagaStatus.DONE, saga.getStatus());
        // saga saves: begin + currentStep (inside step) + finish
        Mockito.verify(sagas, Mockito.times(3)).save(Mockito.any(GradingSaga.class));
        // step saves: insert + finishStep update
        Mockito.verify(steps, Mockito.times(2)).save(Mockito.any(GradingSagaStep.class));
    }

    @Test
    void failuresNeverThrow() {
        GradingSagaRepository sagas = Mockito.mock(GradingSagaRepository.class);
        GradingSagaStepRepository steps = Mockito.mock(GradingSagaStepRepository.class);
        Mockito.when(sagas.save(Mockito.any())).thenThrow(new RuntimeException("db down"));

        SagaTracker tracker = tracker(sagas, steps);
        assertNull(tracker.begin(UUID.randomUUID()));
        assertNull(tracker.step(UUID.randomUUID(), Constant.Saga.BOOT_COMPOSE, null, null));
        tracker.finishStep(UUID.randomUUID(), SagaStepStatus.FAILED, "x");
        tracker.finish(UUID.randomUUID(), SagaStatus.FAILED);
    }
}
