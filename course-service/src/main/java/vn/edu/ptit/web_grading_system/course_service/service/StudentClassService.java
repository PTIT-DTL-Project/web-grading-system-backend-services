package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentClassResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.ClassMapper;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.CourseClassRepository;
import vn.edu.ptit.web_grading_system.course_service.spec.filter.ClassFilter;

import java.util.List;
import java.util.UUID;

/**
 * Student-facing class reads. Students see only classes they are enrolled in
 * (class_students.student_user_id); anything else answers 404, indistinguishable
 * from missing — the project's ownership convention.
 */
// Review: 2026-10-08 — student class list + detail (FE StudentClassesPage).
@Service
@RequiredArgsConstructor
public class StudentClassService {

    private final ClassStudentRepository classStudentRepository;
    private final CourseClassRepository courseClassRepository;
    private final ClassMapper classMapper;

    @Transactional(readOnly = true)
    public Page<StudentClassResponse> listEnrolledClasses(UUID studentId, String search,
                                                          ClassStatus status, Pageable pageable) {
        List<UUID> classIds = enrolledClassIds(studentId);
        if (classIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return courseClassRepository
                .findEnrolled(classIds, ClassFilter.parse(search), status, pageable)
                .map(classMapper::toStudentResponse);
    }

    @Transactional(readOnly = true)
    public StudentClassResponse getEnrolledClass(UUID studentId, UUID classId) {
        if (!enrolledClassIds(studentId).contains(classId)) {
            throw new ResourceNotFoundException("Class not found: " + classId);
        }
        return courseClassRepository.findById(classId)
                .map(classMapper::toStudentResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    private List<UUID> enrolledClassIds(UUID studentId) {
        return classStudentRepository.findAllByStudentUserId(studentId).stream()
                .map(ClassStudent::getClassId)
                .toList();
    }
}
