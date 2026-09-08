package vn.edu.ptit.web_grading_system.executor_service.service.step;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Type-keyed registry for step executors. New step type = one new class
 * implementing {@link StepExecutor}; it registers itself automatically.
 */
@Component
public class StepRegistry {

    private final Map<String, StepExecutor> executors;

    public StepRegistry(List<StepExecutor> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toUnmodifiableMap(StepExecutor::type, Function.identity()));
    }

    public StepExecutor of(String type) {
        StepExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("Unknown step type: " + type);
        }
        return executor;
    }
}
