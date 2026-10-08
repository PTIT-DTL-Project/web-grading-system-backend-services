package vn.edu.ptit.web_grading_system.course_service.mapper;

import org.mapstruct.Mapper;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentClassResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;

@Mapper(componentModel = "spring")
public interface ClassMapper {

    ClassResponse toResponse(CourseClass courseClass);

    StudentClassResponse toStudentResponse(CourseClass courseClass);
}