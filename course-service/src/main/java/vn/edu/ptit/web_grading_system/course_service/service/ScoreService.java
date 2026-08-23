package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.course_service.dto.request.ScoreComponentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.StudentScoreRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.*;
import vn.edu.ptit.web_grading_system.course_service.entities.*;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.ScoreComponentMapper;
import vn.edu.ptit.web_grading_system.course_service.repositories.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreService {

    public static final BigDecimal ONE = BigDecimal.ONE;
    public static final BigDecimal WEIGHT_EPSILON = new BigDecimal("0.001");
    public static final BigDecimal MIN_FINAL_EXAM_WEIGHT = new BigDecimal("0.40");

    private final CourseClassRepository courseClassRepository;
    private final ScoreComponentMapper scoreComponentMapper;
    private final ScoreComponentRepository scoreComponentRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final ClassStudentRepository classStudentRepository;
    private final AssignmentRepository assignmentRepository;
    private final ResultServiceClient resultServiceClient;

    private String requireStudentInClass(UUID classId, UUID ownerId, String studentCode) {
        requireOwnedClass(classId, ownerId);
        String code = studentCode == null ? "" : studentCode.trim();
        if (code.isEmpty() || !classStudentRepository.findByClassIdAndStudentCode(classId, code).isPresent()) {
            throw new ResourceNotFoundException("Student '" + code + "' not found in class " + classId);
        }
        return code;
    }

    private CourseClass requireOwnedClass(UUID classId, UUID ownerId) {
        return courseClassRepository.findByIdAndOwnerId(classId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    public List<ScoreComponentResponse> getComponents(UUID classId, UUID ownerId) {
        requireOwnedClass(classId, ownerId);
        return scoreComponentRepository.findAllByClassId(classId).stream()
                .map(scoreComponentMapper::toResponse)
                .toList();
    }

    @Transactional
    public List<ScoreComponentResponse> replaceComponents(UUID classId, UUID ownerId, List<ScoreComponentRequest> requests) {
        requireOwnedClass(classId, ownerId);
        validateComponents(requests);

        Map<ScoreComponentType, ScoreComponent> existing = scoreComponentRepository
                .findAllByClassId(classId).stream()
                .collect(Collectors.toMap(ScoreComponent::getType, c -> c));
        for (ScoreComponentRequest req : requests) {
            ScoreComponent component = existing.remove(req.getType());
            if (component == null) {
                component = ScoreComponent.builder()
                        .classId(classId)
                        .type(req.getType())
                        .build();
            }
            component.setWeight(req.getWeight());
            scoreComponentRepository.save(component);
        }
        // remaining components were removed by the lecturer
        existing.values().forEach(c -> c.setDeletedAt(java.time.OffsetDateTime.now()));

        return scoreComponentRepository.findAllByClassId(classId).stream()
                .map(scoreComponentMapper::toResponse)
                .toList();
    }

    private void validateComponents(List<ScoreComponentRequest> requests) {
        Set<ScoreComponentType> types = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal finalExamWeight = null;
        for (ScoreComponentRequest req : requests) {
            if (!types.add(req.getType())) {
                throw new IllegalArgumentException("Duplicate component type: " + req.getType());
            }
            sum = sum.add(req.getWeight());
            if (req.getType() == ScoreComponentType.FINAL_EXAM) {
                finalExamWeight = req.getWeight();
            }
        }
        if (finalExamWeight == null) {
            throw new IllegalArgumentException("FINAL_EXAM component is required");
        }
        if (finalExamWeight.compareTo(MIN_FINAL_EXAM_WEIGHT) < 0) {
            throw new IllegalArgumentException("FINAL_EXAM weight must be >= " + MIN_FINAL_EXAM_WEIGHT);
        }
        if (sum.subtract(ONE).abs().compareTo(WEIGHT_EPSILON) > 0) {
            throw new IllegalArgumentException("Weights must sum to 1.000, got " + sum);
        }
    }

    @Transactional
    public void setStudentScores(UUID classId, UUID ownerId, String studentCode, List<StudentScoreRequest> requests) {
        final String code = requireStudentInClass(classId, ownerId, studentCode);
        Map<ScoreComponentType, ScoreComponent> components = scoreComponentRepository
                .findAllByClassId(classId).stream()
                .collect(Collectors.toMap(ScoreComponent::getType, c -> c));
        if (components.isEmpty()) {
            throw new IllegalArgumentException("No score components configured for class " + classId);
        }
        for (StudentScoreRequest req : requests) {
            ScoreComponent component = components.get(req.getComponentType());
            if (component == null) {
                throw new IllegalArgumentException("Unknown component type: " + req.getComponentType());
            }
            if (component.getType() == ScoreComponentType.EXERCISE) {
                throw new IllegalArgumentException("EXERCISE is auto-graded from results, cannot set manually");
            }
            StudentScore score = studentScoreRepository
                    .findByClassIdAndStudentCodeAndComponentId(classId, code, component.getId())
                    .orElseGet(() -> StudentScore.builder()
                            .classId(classId)
                            .studentCode(code)
                            .componentId(component.getId())
                            .build());
            score.setScore(req.getScore());
            studentScoreRepository.save(score);
        }
    }

    public StudentScoresResponse getStudentScores(UUID classId, UUID ownerId, String studentCode) {
        final String code = requireStudentInClass(classId, ownerId, studentCode);
        List<ScoreComponent> components = scoreComponentRepository.findAllByClassId(classId);
        Map<UUID, BigDecimal> manual = studentScoreRepository
                .findAllByClassIdAndStudentCode(classId, code).stream()
                .collect(Collectors.toMap(StudentScore::getComponentId, StudentScore::getScore));

        BigDecimal exercise = computeExercise(classId, code);
        List<StudentScoreEntryResponse> entries = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean complete = true;
        for (ScoreComponent c : components) {
            BigDecimal score = c.getType() == ScoreComponentType.EXERCISE ? exercise : manual.get(c.getId());
            entries.add(StudentScoreEntryResponse.builder()
                    .type(c.getType())
                    .weight(c.getWeight())
                    .score(score)
                    .build());
            if (score == null) {
                complete = false;
            } else {
                total = total.add(score.multiply(c.getWeight()));
            }
        }
        BigDecimal finalTotal = complete ? total.setScale(2, RoundingMode.HALF_UP) : null;
        LetterGrade grade = evaluateGrade(finalTotal, entries);
        return StudentScoresResponse.builder()
                .studentCode(code)
                .entries(entries)
                .total(finalTotal)
                .letterGrade(grade != null ? grade.getDisplay() : null)
                .gpa(grade != null ? grade.getGpa() : null)
                .build();
    }

    private BigDecimal computeExercise(UUID classId, String studentCode) {
        UUID studentUserId = classStudentRepository
                .findByClassIdAndStudentCode(classId, studentCode)
                .map(ClassStudent::getStudentUserId).orElse(null);
        if (studentUserId == null) {
            return null; // no user mapping yet — auto grading unavailable
        }
        List<UUID> assignmentIds = assignmentRepository.findAllByClassId(classId).stream()
                .map(Assignment::getId)
                .toList();
        if (assignmentIds.isEmpty()) {
            return null;
        }
        return resultServiceClient.average(new ResultServiceClient.AverageRequest(assignmentIds, studentUserId))
                .get("average");
    }

    private LetterGrade evaluateGrade(BigDecimal finalTotal, List<StudentScoreEntryResponse> entries) {
        if (finalTotal == null) {
            return null; // incomplete — cannot grade
        }
        boolean anySubScoreFailed = entries.stream()
                .map(StudentScoreEntryResponse::getScore)
                .anyMatch(sc -> sc != null && sc.signum() <= 0); // a real 0 is valid input -> immediate F
        boolean failed = anySubScoreFailed || finalTotal.compareTo(new BigDecimal("4.00")) < 0;
        return failed ? LetterGrade.F : LetterGrade.fromTotal(finalTotal);
    }

    public List<TranscriptEntryResponse> transcript(UUID classId, UUID ownerId) {
        requireOwnedClass(classId, ownerId);
        List<ScoreComponent> components = scoreComponentRepository.findAllByClassId(classId);
        Map<String, List<StudentScore>> scoresByStudent = studentScoreRepository
                .findAllByClassId(classId).stream()
                .collect(Collectors.groupingBy(StudentScore::getStudentCode));
        List<ClassStudent> roster = classStudentRepository.findAllByClassId(classId);
        // ponytail: N+1 exercise fetches, fine for class-size rosters; batch endpoint if it hurts
        return roster.stream().map(student -> {
            Map<UUID, BigDecimal> manual = Optional.ofNullable(scoresByStudent.get(student.getStudentCode()))
                    .orElse(List.of()).stream()
                    .collect(Collectors.toMap(StudentScore::getComponentId, StudentScore::getScore));
            BigDecimal exercise = computeExercise(classId, student.getStudentCode());
            List<StudentScoreEntryResponse> entries = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            boolean complete = true;
            for (ScoreComponent c : components) {
                BigDecimal score = c.getType() == ScoreComponentType.EXERCISE ? exercise : manual.get(c.getId());
                entries.add(StudentScoreEntryResponse.builder()
                        .type(c.getType()).weight(c.getWeight()).score(score).build());
                if (score == null) {
                    complete = false;
                } else {
                    total = total.add(score.multiply(c.getWeight()));
                }
            }
            BigDecimal finalTotal = complete ? total.setScale(2, RoundingMode.HALF_UP) : null;
            LetterGrade grade = evaluateGrade(finalTotal, entries);
            return TranscriptEntryResponse.builder()
                    .studentCode(student.getStudentCode())
                    .studentName(student.getStudentName())
                    .entries(entries)
                    .total(finalTotal)
                    .letterGrade(grade != null ? grade.getDisplay() : null)
                    .gpa(grade != null ? grade.getGpa() : null)
                    .build();
        }).toList();
    }
}