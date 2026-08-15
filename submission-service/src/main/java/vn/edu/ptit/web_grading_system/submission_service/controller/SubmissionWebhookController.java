package vn.edu.ptit.web_grading_system.submission_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.submission_service.dto.webhook.RustFsPayload;
import vn.edu.ptit.web_grading_system.submission_service.dto.webhook.RustFsRecord;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/submissions/webhook")
@RequiredArgsConstructor
public class SubmissionWebhookController {

    private final SubmissionService submissionService;

    @PostMapping("/upload-complete")
    public ResponseEntity<Void> handleRustFsWebhook(@RequestBody RustFsPayload payload) {
        List<RustFsRecord> records = payload.getRecords();
        if (records == null || records.isEmpty()) {
            log.info("RustFS empty payload.");
            return ResponseEntity.ok().build();
        }

        log.info("RustFS webhook received: {} event(s)", records.size());

        for (RustFsRecord record : records) {
            if (record.getEventName() != null && record.getEventName().contains("ObjectCreated:Put")) {
                if (record.getS3() == null || record.getS3().getObject() == null) {
                    continue;
                }

                String rawKey = record.getS3().getObject().getKey();
                String decodedKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);

                if (decodedKey.startsWith("submissions/") && decodedKey.endsWith(".zip")) {
                    log.info("Submission upload detected: key={}, size={}", decodedKey, record.getS3().getObject().getSize());
                    submissionService.handleUploadCompleteAsync(decodedKey);
                }
            }
        }

        return ResponseEntity.ok().build();
    }
}
