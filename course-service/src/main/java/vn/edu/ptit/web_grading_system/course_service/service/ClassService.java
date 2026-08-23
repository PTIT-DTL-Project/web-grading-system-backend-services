package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateClassRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassStudentResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.mapper.ClassMapper;
import vn.edu.ptit.web_grading_system.course_service.mapper.ClassStudentMapper;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entities.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.repositories.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repositories.CourseClassRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassService {

    private final CourseClassRepository courseClassRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ClassMapper classMapper;
    private final ClassStudentMapper classStudentMapper;

    @Transactional
    public ClassResponse create(UUID ownerId, CreateClassRequest request) {
        String name = request.getName().trim();
        String semester = request.getSemester().trim();
        if (courseClassRepository.existsByOwnerIdAndNameAndSemester(ownerId, name, semester)) {
            log.warn("Duplicate class creation rejected: owner={}, name={}, semester={}", ownerId, name, semester);
            throw new BadRequestException(
                    "Class '%s' already exists in semester %s".formatted(name, semester));
        }
        CourseClass courseClass = CourseClass.builder()
                .ownerId(ownerId)
                .name(name)
                .semester(semester)
                .status(ClassStatus.ACTIVE)
                .build();
        return classMapper.toResponse(courseClassRepository.save(courseClass));
    }

    public Page<ClassResponse> listMine(UUID ownerId, Pageable pageable) {
        return courseClassRepository.findAllByOwnerId(ownerId, pageable)
                .map(classMapper::toResponse);
    }

    public ClassResponse getById(UUID id, UUID ownerId) {
        return findOwned(id, ownerId).map(classMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + id));
    }

    @Transactional
    public ClassResponse archive(UUID id, UUID ownerId) {
        CourseClass courseClass = findOwned(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + id));
        courseClass.setStatus(ClassStatus.ARCHIVED);
        return classMapper.toResponse(courseClassRepository.save(courseClass));
    }

    @Transactional
    public ImportResult importStudents(UUID classId, UUID ownerId, MultipartFile file) {
        findOwned(classId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is empty");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            throw new BadRequestException("Only CSV files are accepted");
        }
        log.info("Importing students into class {}: file={} ({}B)", classId,
                file.getOriginalFilename(), file.getSize());
        List<String[]> rows = parseCsv(file);
        int imported = 0;
        int skipped = 0;
        for (String[] row : rows) {
            if (row.length < 2 || row[0].isBlank()) {
                skipped++;
                continue;
            }
            String studentCode = row[0].trim();
            String studentName = row[1].trim();
            String email = row.length > 2 ? row[2].trim() : null;
            UUID studentUserId = null;
            if (row.length > 3 && !row[3].isBlank()) {
                try {
                    studentUserId = UUID.fromString(row[3].trim());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid student_user_id UUID in row: " + studentCode);
                }
            }
            if (classStudentRepository.existsByClassIdAndStudentCode(classId, studentCode)) {
                skipped++;
                continue;
            }
            classStudentRepository.save(ClassStudent.builder()
                    .classId(classId)
                    .studentCode(studentCode)
                    .studentName(studentName)
                    .email(email)
                    .studentUserId(studentUserId)
                    .build());
            imported++;
        }
        log.info("Import finished for class {}: imported={}, skipped={}", classId, imported, skipped);
        return new ImportResult(imported, skipped);
    }

    public Page<ClassStudentResponse> listStudents(UUID classId, UUID ownerId, Pageable pageable) {
        findOwned(classId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Class not found: " + classId));
        return classStudentRepository.findAllByClassId(classId, pageable)
                .map(classStudentMapper::toResponse);
    }

    private java.util.Optional<CourseClass> findOwned(UUID id, UUID ownerId) {
        return courseClassRepository.findByIdAndOwnerId(id, ownerId);
    }

    // ponytail: naive split, no CSV lib; header row skipped by first-line heuristic
    static List<String[]> parseCsv(MultipartFile file) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split(",", -1);
                if (first && (cols[0].trim().equalsIgnoreCase("studentCode")
                        || cols[0].trim().equalsIgnoreCase("mã sinh viên")
                        || cols[0].trim().equalsIgnoreCase("ma_sv"))) {
                    first = false;
                    continue;
                }
                first = false;
                rows.add(cols);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV file", e);
        }
        return rows;
    }

    public record ImportResult(int imported, int skipped) {
    }
}