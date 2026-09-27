package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.web_grading_system.course_service.Constant;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.DockerImageResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.DockerImage;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ConflictException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentDockerImageRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.DockerImageRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecturer-facing library of Docker images (DB images, Java SDK, ...).
 * <p>
 * Mutations are scoped to the owning lecturer (mirroring assignments.owner_id /
 * requireOwnedAssignment). Legacy images backfilled to the system principal remain
 * shared and editable by any authenticated lecturer so the picker stays usable.
 * Soft delete is enforced by the entity's {@code @SQLRestriction}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerImageService {

    private final DockerImageRepository dockerImageRepository;
    private final AssignmentDockerImageRepository assignmentDockerImageRepository;

    /** Validates image_url against the single sourced grammar in {@link Constant.Image}. */
    static void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BadRequestException("image_url is required");
        }
        if (imageUrl.endsWith(":latest")) {
            throw new BadRequestException(
                    "image_url must carry an explicit tag; ':latest' is forbidden");
        }
        if (!imageUrl.matches(Constant.Image.IMAGE_URL_REGEX)) {
            throw new BadRequestException(
                    "image_url must be registry/repo:tag (explicit tag, ':latest' forbidden) " +
                    "or name@sha256:<digest>");
        }
    }

    private DockerImage requireOwnedImage(UUID id, UUID requesterId) {
        // Review: 2026-09-27, Pullfrog PR #N — ownership scope mirrors assignments.owner_id.
        // Legacy system-principal images stay editable by any lecturer so the shared library
        // (backfilled defaults) remains usable.
        DockerImage image = dockerImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docker image not found: " + id));
        if (image.getOwnerId().equals(requesterId)
                || image.getOwnerId().equals(Constant.IMAGE_LIBRARY_SYSTEM_OWNER)) {
            return image;
        }
        throw new ResourceNotFoundException("Docker image not found: " + id);
    }

    private DockerImage requireActive(UUID id) {
        return dockerImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Docker image not found: " + id));
    }

    @Transactional
    public DockerImageResponse create(UUID ownerId, CreateDockerImageRequest req) {
        validateImageUrl(req.getImageUrl());
        DockerImage image = DockerImage.builder()
                .name(req.getName().trim())
                .imageUrl(req.getImageUrl().trim())
                .description(req.getDescription())
                .ownerId(ownerId)
                .build();
        DockerImage saved = dockerImageRepository.save(image);
        log.info("Docker image created: id={} name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DockerImageResponse> list(String name, Pageable pageable) {
        Page<DockerImage> images = StringUtils.hasText(name)
                ? dockerImageRepository.findByNameContainingIgnoreCase(name, pageable)
                : dockerImageRepository.findAll(pageable);
        return images.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DockerImageResponse getById(UUID id) {
        return toResponse(requireActive(id));
    }

    @Transactional
    public DockerImageResponse update(UUID id, UUID requesterId,
                                      UpdateDockerImageRequest req) {
        DockerImage image = requireOwnedImage(id, requesterId);
        if (req.getName() != null) {
            image.setName(req.getName().trim());
        }
        if (req.getImageUrl() != null) {
            validateImageUrl(req.getImageUrl());
            image.setImageUrl(req.getImageUrl().trim());
        }
        if (req.getDescription() != null) {
            image.setDescription(req.getDescription());
        }
        DockerImage saved = dockerImageRepository.save(image);
        log.info("Docker image updated: id={} name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id, UUID requesterId) {
        DockerImage image = requireOwnedImage(id, requesterId);
        // Review: 2026-09-27, Pullfrog PR #N — refuse to delete an image still referenced by
        // an assignment so the executor's grading-config fetch does not silently lose an image.
        if (!assignmentDockerImageRepository.findByDockerImageIdIn(List.of(id)).isEmpty()) {
            throw new ConflictException(
                    "Cannot delete an image still referenced by an assignment");
        }
        image.setDeletedAt(OffsetDateTime.now());
        dockerImageRepository.save(image);
        log.info("Docker image deleted: id={} name={}", id, image.getName());
    }

    /** Returns active image URLs (for the executor's async pre-pull scanner). */
    @Transactional(readOnly = true)
    public List<String> listActiveUrls() {
        return dockerImageRepository.findAll().stream()
                .map(DockerImage::getImageUrl)
                .toList();
    }

    private DockerImageResponse toResponse(DockerImage d) {
        return DockerImageResponse.builder()
                .id(d.getId())
                .name(d.getName())
                .imageUrl(d.getImageUrl())
                .description(d.getDescription())
                .build();
    }
}
