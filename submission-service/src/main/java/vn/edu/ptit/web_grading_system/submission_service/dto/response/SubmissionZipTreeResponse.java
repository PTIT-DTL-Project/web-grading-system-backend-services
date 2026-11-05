package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Tree structure of a submission zip file.
 * Each node is either a folder (with children) or a file (with size and mime type).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionZipTreeResponse {

    private UUID submissionId;

    private ZipTreeNode root;
}
