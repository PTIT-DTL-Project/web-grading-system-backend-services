package vn.edu.ptit.web_grading_system.course_service.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.ValidateQuestionRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.ValidateQuestionResponse;
import vn.edu.ptit.web_grading_system.course_service.service.StepConfigValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates a test-plan payload (plan + steps) without persisting anything.
 * Used by the AI assistant service to check config shape before creating a
 * pending action, so the agent gets a repair loop instead of a 400 at confirm.
 * <p>
 * Route is under {@code /api/v1/internal/**} which is {@code permitAll}
 * ({@code SecurityConfig.java:34}), so it is reachable from ai-assistant-service.
 */
@RestController
@RequestMapping("/api/v1/internal/test-plans")
public class InternalTestPlanController {

    @PostMapping("/validate")
    public ResponseEntity<ValidateQuestionResponse> validate(
            @Valid @RequestBody ValidateQuestionRequest request, BindingResult bindingResult) {

        List<ValidateQuestionResponse.ValidationError> errors = new ArrayList<>();

        if (bindingResult.hasErrors()) {
            bindingResult.getFieldErrors().forEach(fe ->
                    errors.add(new ValidateQuestionResponse.ValidationError(
                            fe.getField(), fe.getDefaultMessage())));
            return ResponseEntity.ok(ValidateQuestionResponse.builder()
                    .valid(false).errors(errors).build());
        }

        // Validate each step config using the authoritative static validator.
        for (int i = 0; i < request.getSteps().size(); i++) {
            var step = request.getSteps().get(i);
            String prefix = "steps[" + i + "]";
            try {
                StepConfigValidator.validateAndSerialize(step.getStepType(), step.getConfig());
            } catch (IllegalArgumentException e) {
                errors.add(new ValidateQuestionResponse.ValidationError(
                        prefix + ".config", e.getMessage()));
            }
        }

        return ResponseEntity.ok(ValidateQuestionResponse.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .build());
    }
}
