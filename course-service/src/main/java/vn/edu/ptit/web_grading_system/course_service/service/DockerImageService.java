package vn.edu.ptit.web_grading_system.course_service.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.DockerImageResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.DockerImage;
import vn.edu.ptit.web_grading_system.course_service.repositories.DockerImageRepository;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lecturer-facing library of Docker images (DB images, Java SDK, ...).
 * Soft-delete is enforced by the entity's {@code @SQLRestriction}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerImageService {

    /** image_url must carry an explicit tag; {@code :latest} is forbidden for grading reproducibility. */
    private static final String IMAGE_URL_REGEX = "^(?!.*:latest$).*[A-Za-z0-9][A-Za-z0-9._/-]*:[A-Za-z0-9._-]+$";
    private static final int NAME_MAX = 255;
    private static final int IMAGE_URL_MAX = 500;

    private final DockerImageRepository dockerImageRepository;

    @Transactional
    public DockerImageResponse create(CreateDockerImageRequest req) {
        validateImageUrl(req.getImageUrl());
        DockerImage image = DockerImage.builder()
                .name(req.getName().trim().substring(0, Math.min(req.getName().length(), NAME_MAX)))
                .imageUrl(req.getImageUrl().trim().substring(0, IMAGE_URL_MAX))
                .description(req.getDescription())
                .build();
        DockerImage saved = dockerImageRepository.save(image);
        log.info("Docker image created: id={} url={}", saved.getId(), saved.getImageUrl());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DockerImageResponse> list(Pageable pageable) {
        return dockerImageRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DockerImageResponse getById(UUID id) {
        return toResponse(requireActive(id));
    }

    @Transactional
    public DockerImageResponse update(UUID id, UpdateDockerImageRequest req) {
        DockerImage image = requireActive(id);
        if (req.getName() != null) {
            image.setName(req.getName().trim().substring(0, Math.min(req.getName().length(), NAME_MAX)));
        }
        if (req.getImageUrl() != null) {
            validateImageUrl(req.getImageUrl());
            image.setImageUrl(req.getImageUrl().trim().substring(0, IMAGE_URL_MAX));
        }
        if (req.getDescription() != null) {
            image.setDescription(req.getDescription());
        }
        DockerImage saved = dockerImageRepository.save(image);
        log.info("Docker image updated: id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        DockerImage image = requireActive(id);
        image.setDeletedAt(OffsetDateTime.now());
        dockerImageRepository.save(image);
        log.info("Docker image soft-deleted: id={}", id);
    }

    /** Returns active images in {@code ids}, ignoring any that are soft-deleted. */
    @Transactional(readOnly = true)
    public List<DockerImage> findActiveByIdIn(List<UUID> ids) {
        return dockerImageRepository.findAllByIdIn(ids);
    }

    private DockerImage requireActive(UUID id) {
        return dockerImageRepository.findById(id)
                .filter(i -> i.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Docker image not found: " + id));
    }

    /** Returns active image URLs (for the executor's async pre-pull scanner). */
    @Transactional(readOnly = true)
    public List<String> listActiveUrls() {
        return dockerImageRepository.findAll(org.springframework.data.domain.Pageable.unpaged())
                .map(DockerImage::getImageUrl)
                .getContent();
    }

    static void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BadRequestException("image_url is required");
        }
        if (imageUrl.endsWith(":latest")) {
            throw new BadRequestException(
                    "image_url must be a registry/repo:tag with an explicit tag; ':latest' is forbidden");
        }
        if (!imageUrl.matches(IMAGE_URL_REGEX)) {
            throw new BadRequestException(
                    "image_url must be of the form registry/repo:tag with an explicit tag (':latest' is forbidden)");
        }
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
