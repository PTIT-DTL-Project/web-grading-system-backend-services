package vn.edu.ptit.web_grading_system.course_service.mapper;

import org.mapstruct.Mapper;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;

@Mapper(componentModel = "spring")
public interface AssignmentMapper {

    AssignmentResponse toResponse(Assignment assignment);
}