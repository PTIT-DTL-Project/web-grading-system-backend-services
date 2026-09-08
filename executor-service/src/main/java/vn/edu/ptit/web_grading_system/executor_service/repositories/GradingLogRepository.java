package vn.edu.ptit.web_grading_system.executor_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLog;

import java.util.UUID;

@Repository
public interface GradingLogRepository extends JpaRepository<GradingLog, UUID> {
}
