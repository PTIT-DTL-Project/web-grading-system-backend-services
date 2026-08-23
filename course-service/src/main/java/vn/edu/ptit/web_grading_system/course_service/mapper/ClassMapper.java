package vn.edu.ptit.web_grading_system.course_service.mapper;

import org.mapstruct.Mapper;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.CourseClass;

@Mapper(componentModel = "spring")
public interface ClassMapper {

    ClassResponse toResponse(CourseClass courseClass);
}