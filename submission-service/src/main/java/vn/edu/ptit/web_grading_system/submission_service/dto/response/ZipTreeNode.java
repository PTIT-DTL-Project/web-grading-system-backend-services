package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A single node in the zip tree: either a folder (has children) or a file (has size/mime).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZipTreeNode {

    private String name;

    private String type; // "folder" or "file"

    private String path; // relative path within the zip, empty string for root

    private Long size; // bytes, only for files

    private String mime; // detected mime type, only for files

    private List<ZipTreeNode> children; // only for folders
}
