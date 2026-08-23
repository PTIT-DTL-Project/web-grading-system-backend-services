package vn.edu.ptit.web_grading_system.course_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.request.ScoreComponentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.StudentScoreRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ScoreComponentResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentScoresResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.TranscriptEntryResponse;
import vn.edu.ptit.web_grading_system.course_service.service.ScoreService;
import vn.edu.ptit.web_grading_system.course_service.util.annotation.ApiMessage;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping("/{id}/score-components")
    public ResponseEntity<List<ScoreComponentResponse>> getComponents(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(scoreService.getComponents(id, UUID.fromString(ownerId)));
    }

    @PutMapping("/{id}/score-components")
    @ApiMessage("Score components updated")
    public ResponseEntity<List<ScoreComponentResponse>> replaceComponents(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestBody List<@Valid ScoreComponentRequest> requests) {
        return ResponseEntity.ok(scoreService.replaceComponents(id, UUID.fromString(ownerId), requests));
    }

    @PutMapping("/{id}/students/{studentCode}/scores")
    @ApiMessage("Student scores updated")
    public ResponseEntity<Void> setStudentScores(
            @PathVariable UUID id,
            @PathVariable String studentCode,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestBody List<@Valid StudentScoreRequest> requests) {
        scoreService.setStudentScores(id, UUID.fromString(ownerId), studentCode, requests);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/students/{studentCode}/scores")
    public ResponseEntity<StudentScoresResponse> getStudentScores(
            @PathVariable UUID id,
            @PathVariable String studentCode,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(scoreService.getStudentScores(id, UUID.fromString(ownerId), studentCode));
    }

    @GetMapping("/{id}/transcript")
    public ResponseEntity<List<TranscriptEntryResponse>> transcript(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(scoreService.transcript(id, UUID.fromString(ownerId)));
    }
}