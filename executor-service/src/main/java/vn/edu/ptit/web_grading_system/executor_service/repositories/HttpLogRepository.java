package vn.edu.ptit.web_grading_system.executor_service.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.ptit.web_grading_system.executor_service.entities.HttpLog;

public interface HttpLogRepository extends JpaRepository<HttpLog, UUID> {
}