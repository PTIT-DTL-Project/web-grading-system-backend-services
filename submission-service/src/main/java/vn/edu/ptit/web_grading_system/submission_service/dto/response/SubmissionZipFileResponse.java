package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Content of a single file entry inside a submission zip, returned for the read-only editor.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionZipFileResponse {

    private UUID submissionId;

    private String path; // relative path within the zip

    private Long size; // bytes

    private String mime; // detected mime type

    private Boolean isBinary; // true if binary, false if text

    private String content; // text content (null if binary)

    private Boolean truncated; // true if content was cut due to size limit

    private String message; // additional info (e.g. "File nhị phân, không thể xem trước")
}
