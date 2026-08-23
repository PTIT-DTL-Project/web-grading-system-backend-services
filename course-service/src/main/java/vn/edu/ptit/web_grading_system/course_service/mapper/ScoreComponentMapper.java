package vn.edu.ptit.web_grading_system.course_service.mapper;

import org.mapstruct.Mapper;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ScoreComponentResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.ScoreComponent;

@Mapper(componentModel = "spring")
public interface ScoreComponentMapper {

    ScoreComponentResponse toResponse(ScoreComponent component);
}