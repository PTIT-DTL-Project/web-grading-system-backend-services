package vn.edu.ptit.web_grading_system.submission_service.mapper;

import org.mapstruct.Mapper;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.entities.Submission;

@Mapper(componentModel = "spring")
public interface SubmissionMapper {

    SubmissionResponse toResponse(Submission submission);
}