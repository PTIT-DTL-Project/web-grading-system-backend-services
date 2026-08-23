package vn.edu.ptit.web_grading_system.course_service.mapper;

import org.mapstruct.Mapper;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassStudentResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStudent;

@Mapper(componentModel = "spring")
public interface ClassStudentMapper {

    ClassStudentResponse toResponse(ClassStudent classStudent);
}