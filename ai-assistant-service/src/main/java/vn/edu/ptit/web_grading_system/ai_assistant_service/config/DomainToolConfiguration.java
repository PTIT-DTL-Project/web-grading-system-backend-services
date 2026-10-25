package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.DomainApiTool;
import java.time.Duration;

@Configuration
public class DomainToolConfiguration {
    private static final String L = "ROLE_LECTURER", S = "ROLE_STUDENT";
    private static final String[] NONE = {};
    private final RestClient course, submission;
    private final ObjectMapper mapper;

    public DomainToolConfiguration(@Value("${feign.course-service.url}") String courseUrl,
                                   @Value("${feign.submission-service.url:http://localhost:8082}") String submissionUrl,
                                   ObjectMapper mapper) {
        var transport = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        transport.setReadTimeout(Duration.ofSeconds(10));
        this.course = RestClient.builder().baseUrl(courseUrl).requestFactory(transport).build();
        this.submission = RestClient.builder().baseUrl(submissionUrl).requestFactory(transport).build();
        this.mapper = mapper;
    }

    private AgentTool t(String name, String description, String role, String method, String path,
                        String[] ids, String[] query, boolean body, RestClient client) {
        return new DomainApiTool(name, description, role, method, path, ids, query, body, null, client, mapper);
    }
    private AgentTool plan(String name, String description, String role, String method, String path,
                           String[] ids, boolean body) {
        return t(name, description, role, method, path, ids, NONE, body, course);
    }

    @Bean AgentTool listTestPlans() { return plan("listTestPlans", "Liệt kê câu kiểm tra và bước chấm của bài tập mình quản lý.", L, "GET", "/api/v1/assignments/{assignmentId}/plans", new String[]{"assignmentId"}, false); }
    @Bean AgentTool getTestPlan() { return new DomainApiTool("getTestPlan", "Xem một câu kiểm tra thuộc bài tập mình quản lý.", L, "GET", "/api/v1/assignments/{assignmentId}/plans", new String[]{"assignmentId", "planId"}, NONE, false, "planId", course, mapper); }
    @Bean AgentTool listTestSteps() { return plan("listTestSteps", "Liệt kê bước chấm của một câu kiểm tra.", L, "GET", "/api/v1/assignments/{assignmentId}/plans/{planId}/steps", new String[]{"assignmentId", "planId"}, false); }
    @Bean AgentTool createTestPlan() { return plan("createTestPlan", "Tạo câu kiểm tra với name, sequenceOrder, weight và description; cần duyệt.", L, "POST", "/api/v1/assignments/{assignmentId}/plans", new String[]{"assignmentId"}, true); }
    @Bean AgentTool createTestQuestion() { return plan("createTestQuestion", "Tạo câu kiểm tra và toàn bộ bước chấm trong một transaction; payload gồm plan và steps; cần duyệt.", L, "POST", "/api/v1/assignments/{assignmentId}/plans/with-steps", new String[]{"assignmentId"}, true); }
    @Bean AgentTool updateTestPlan() { return plan("updateTestPlan", "Sửa câu kiểm tra; payload phải có name; cần duyệt.", L, "PUT", "/api/v1/assignments/{assignmentId}/plans/{planId}", new String[]{"assignmentId", "planId"}, true); }
    @Bean AgentTool deleteTestPlan() { return plan("deleteTestPlan", "Xóa câu kiểm tra cùng các bước chấm; cần duyệt.", L, "DELETE", "/api/v1/assignments/{assignmentId}/plans/{planId}", new String[]{"assignmentId", "planId"}, false); }
    @Bean AgentTool createTestStep() { return plan("createTestStep", "Tạo bước chấm với stepOrder, name, stepType, config và weight; cần duyệt.", L, "POST", "/api/v1/assignments/{assignmentId}/plans/{planId}/steps", new String[]{"assignmentId", "planId"}, true); }
    @Bean AgentTool updateTestStep() { return plan("updateTestStep", "Sửa bước chấm; payload phải có name; cần duyệt.", L, "PUT", "/api/v1/assignments/{assignmentId}/plans/{planId}/steps/{stepId}", new String[]{"assignmentId", "planId", "stepId"}, true); }
    @Bean AgentTool deleteTestStep() { return plan("deleteTestStep", "Xóa bước chấm; cần duyệt.", L, "DELETE", "/api/v1/assignments/{assignmentId}/plans/{planId}/steps/{stepId}", new String[]{"assignmentId", "planId", "stepId"}, false); }

    @Bean AgentTool getAssignmentResults() { return t("getAssignmentResults", "Xem điểm chấm theo bài tập; có thể lọc studentCode và includeSteps.", L, "GET", "/api/v1/assignments/{assignmentId}/results", new String[]{"assignmentId"}, new String[]{"studentCode", "includeSteps", "page", "size"}, false, course); }
    @Bean AgentTool getClassTranscript() { return plan("getClassTranscript", "Xem bảng điểm lớp mình quản lý.", L, "GET", "/api/v1/classes/{classId}/transcript", new String[]{"classId"}, false); }
    @Bean AgentTool getStudentScores() { return plan("getStudentScores", "Xem điểm một sinh viên trong lớp mình quản lý.", L, "GET", "/api/v1/classes/{classId}/students/{studentCode}/scores", new String[]{"classId", "studentCode"}, false); }
    @Bean AgentTool getScoreComponents() { return plan("getScoreComponents", "Xem thành phần điểm của lớp mình quản lý.", L, "GET", "/api/v1/classes/{classId}/score-components", new String[]{"classId"}, false); }
    @Bean AgentTool updateAssignment() { return plan("updateAssignment", "Sửa bài tập; payload phải có title; cần duyệt.", L, "PUT", "/api/v1/assignments/{assignmentId}", new String[]{"assignmentId"}, true); }
    @Bean AgentTool publishAssignment() { return plan("publishAssignment", "Xuất bản bài tập; cần duyệt.", L, "POST", "/api/v1/assignments/{assignmentId}/publish", new String[]{"assignmentId"}, false); }
    @Bean AgentTool archiveClass() { return plan("archiveClass", "Lưu trữ lớp mình quản lý; cần duyệt.", L, "PUT", "/api/v1/classes/{classId}/archive", new String[]{"classId"}, false); }
    @Bean AgentTool setScoreComponents() { return plan("setScoreComponents", "Thay toàn bộ thành phần điểm bằng payload là mảng {type, weight}; cần duyệt.", L, "PUT", "/api/v1/classes/{classId}/score-components", new String[]{"classId"}, true); }
    @Bean AgentTool setStudentScores() { return plan("setStudentScores", "Cập nhật điểm bằng payload là mảng {componentType, score}; cần duyệt.", L, "PUT", "/api/v1/classes/{classId}/students/{studentCode}/scores", new String[]{"classId", "studentCode"}, true); }

    @Bean AgentTool listMySubmissions() { return t("listMySubmissions", "Liệt kê các bài nộp của chính mình.", S, "GET", "/api/v1/submissions", NONE, new String[]{"page", "size", "search"}, false, submission); }
    @Bean AgentTool listMyTestPlans() { return plan("listMyTestPlans", "Xem các câu kiểm tra công khai của bài tập được giao, không gồm đáp án ẩn.", S, "GET", "/api/v1/student/assignments/{assignmentId}/plans", new String[]{"assignmentId"}, false); }
    @Bean AgentTool getMyClassScores() { return plan("getMyClassScores", "Xem điểm của mình trong một lớp đã tham gia.", S, "GET", "/api/v1/student/classes/{classId}/my-scores", new String[]{"classId"}, false); }
    @Bean AgentTool getMyClass() { return plan("getMyClass", "Xem chi tiết một lớp mình đã tham gia.", S, "GET", "/api/v1/student/classes/{classId}", new String[]{"classId"}, false); }
    @Bean AgentTool getClassRoster() { return t("getClassRoster", "Xem danh sách sinh viên của lớp mình đã tham gia.", S, "GET", "/api/v1/student/classes/{classId}/students", new String[]{"classId"}, new String[]{"page", "size"}, false, course); }
}
