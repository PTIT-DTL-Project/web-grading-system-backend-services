package vn.edu.ptit.web_grading_system.course_service.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.edu.ptit.web_grading_system.course_service.entities.HttpLog;

@Repository
public interface HttpLogRepository extends JpaRepository<HttpLog, UUID> {

}