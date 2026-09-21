package vn.edu.ptit.web_grading_system.executor_service;

public class Constant {

    public static final class VariableContext {
        public static final String APP_PORT = "app_port";
        public static final String SUBMISSION_ID = "submission_id";
        public static final String ASSIGNMENT_ID = "assignment_id";
        public static final String STUDENT_ID = "student_id";
    }

    public static final class HttpStep {
        public static final String QUERY_PARAMS = "query_params";
        public static final String HEADERS = "headers";
        public static final String BODY = "body";
        public static final String EXTRACT = "extract";
        public static final String EXPECTED_STATUS = "expected_status";
        public static final String METHOD = "method";
        public static final String DEFAULT_METHOD = "GET";
        public static final String NAME = "name";
        public static final String FROM = "from";
        public static final String EXPRESSION = "expression";
        public static final String FROM_DEFAULT = "response_body";
        public static final String PATH = "path";
        public static final String NULL_JSON = "null";
        public static final String PATH_DEFAULT = "/";
        public static final String TIMEOUT_MS = "timeoutMs";
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String APPLICATION_JSON = "application/json";
        public static final String SERVICE_NAME = "executor-grading";
        public static final String HTTP_REQUEST = "HTTP_REQUEST";
        public static final String HTTP_POST = "POST";
        public static final String HTTP_PUT = "PUT";
        public static final String HTTP_PATCH = "PATCH";
        public static final String HTTP_DELETE = "DELETE";
        public static final String HTTP_HEAD = "HEAD";
    }

    public static final class Assertion {
        public static final String ASSERTIONS = "assertions";
        public static final String EXISTS = "exists";
        public static final String EQUALS = "equals";
        public static final String TEXT = "text";
        public static final String JSON = "json";
        public static final String PATH = "path";
        public static final String NULL_JSON = "null";
        public static final String KIND = "kind";
        public static final String STATUS = "status";
        public static final String CONTAINS = "contains";
        public static final String JSON_PATH = "json_path";
        public static final String BODY_EQUALS = "body_equals";
        public static final String BODY_STRUCTURE = "body_structure";
        public static final String FIELD_EQUALS = "field_equals";
    }

    public static final class DockerCompose {
        public static final String SERVICES = "services";
        public static final String PORTS = "ports";
        public static final String PRIVILEGED = "privileged";
        public static final String VOLUMES = "volumes";
        public static final String DEPLOY = "deploy";
        public static final String RESOURCES = "resources";
        public static final String LIMITS = "limits";
        public static final String CPUS = "cpus";
        public static final String MEMORY = "memory";
        public static final String DOCKER_SOCK = "docker.sock";
    }

    public static final class Strategy {
        public static final String LECTURER_DOCKER_COMPOSE = "LECTURER_DOCKER_COMPOSE";
        public static final String DOCKER_COMPOSE_YML = "docker-compose.yml";
        public static final String DOCKER_COMPOSE_YAML = "docker-compose.yaml";
    }

    public static final class GradingLog {
        public static final String STEP_GRADING = "GRADING";
        public static final String STEP_RECEIVED = "RECEIVED";
        public static final String STEP_DONE = "DONE";
        public static final String STEP_FAILED = "FAILED";
        public static final String GRADING_STATUS = "GRADING";
        public static final String DONE_STATUS = "DONE";
    }

    public static final class Saga {
        public static final String FETCH_CONFIG = "FETCH_CONFIG";
        public static final String DOWNLOAD_ARTIFACT = "DOWNLOAD_ARTIFACT";
        public static final String BOOT_COMPOSE = "BOOT_COMPOSE";
        public static final String RUN_STEPS = "RUN_STEPS";
        public static final String SCORE_REPORT = "SCORE_REPORT";
        public static final String STEP_PREFIX = "STEP:";
        public static final String GRADE_SUBMISSION = "GRADE_SUBMISSION";
    }

    public static final class Event {
        public static final String ACTION = "action";
        public static final String TRACE_ID = "traceId";
        public static final String PAYLOAD = "payload";
        public static final String SUBMISSION_ID = "submissionId";
        public static final String ASSIGNMENT_ID = "assignmentId";
        public static final String STUDENT_ID = "studentId";
        public static final String PLAN_ID = "planId";
        public static final String RUSTFS_PATH = "rustfsPath";
    }

    public static final class Reaper {
        public static final String TRACE_ID = "reaper";
    }

    public static final class HttpHeader {
        public static final String AUTHORIZATION = "authorization";
        public static final String COOKIE = "cookie";
        public static final String SET_COOKIE = "set-cookie";
        public static final String PROXY_AUTHORIZATION = "proxy-authorization";
        public static final String X_API_KEY = "x-api-key";
    }

    public static final class Logstash {
        public static final String APP_ENV = "APP_ENV";
        public static final String LOCAL = "local";
        public static final String DEFAULT = "default";
        public static final String MESSAGE = "message";
        public static final String STACK_TRACE = "stack_trace";
        public static final String HTTP_LOG = "HttpLog";
        public static final String OPEN_API_CONFIG = "OpenApiConfig";
        public static final String HEALTH = "health";
        public static final String VERSION = "version";
        public static final String NULL = "null";
        public static final String API_V1 = "v1";
    }

    public static final class Artifact {
        public static final String SUBMISSION_ZIP = "submission.zip";
    }

    public static final class Message {

        /* --- nested: grading lifecycle --- */
        public static final class Grade {
            public static final String RECEIVED = "Grading job received from wgs-events (traceId=";
            public static final String RECEIVED_SUFFIX = ")";
            public static final String PERSISTED_PREFIX = "Grading job persisted: id={} submission={}";
            public static final String DUPLICATE_PREFIX = "Grading job already exists for submission={}, skipping";
            public static final String REENQUEUE_PREFIX = "Re-enqueueing stale grading job: id={} status={} attempt={}";
        }

        /* --- nested: reset/rerun --- */
        public static final class Reset {
            public static final String REQUEST_LOG = "Reset request: submissionId={} traceId={}";
            public static final String LOG_PREFIX = "Reset grading job: id={} submission={} action={}";
            public static final String SUCCESS_PREFIX = "Grading job reset: id={} submission={}";
            public static final String FAILED_PREFIX = "Reset failed: job already DONE, submission={} status={}";
            public static final String NOT_FOUND = "Reset failed: no grading job found for submission={}";
            public static final String NOT_FOUND_MESSAGE = "No grading job found for submission";
            public static final String NOT_FAILED_PREFIX = "Job is already DONE, current status: ";
            public static final String SAGA_DELETED = "Deleted saga steps for saga={}, reset saga to STARTED for job={}";
            public static final String SUCCESS = "Job reset to PENDING";
            public static final String FAILED_TRIGGER = "Existing grading job for submission={} is FAILED, triggering reset";
            public static final String RESPONSE_KEY_SUCCESS = "success";
            public static final String RESPONSE_KEY_MESSAGE = "message";
            public static final String RESPONSE_KEY_JOB_ID = "jobId";
        }

        /* --- nested: grade flow (infrastructure steps) --- */
        public static final class Infra {
            public static final String FETCH_CONFIG = "Failed to fetch grading config: ";
            public static final String DOWNLOAD_SUBMISSION = "Failed to download submission: ";
            public static final String GRADING_INFRA = "Grading infrastructure failed: ";
            public static final String FETCHING_PREFIX = "FETCHING: ";
            public static final String BUILDING_PREFIX = "BUILDING: ";
            public static final String RUNNING_PREFIX = "RUNNING: ";
            public static final String PLAN_SUFFIX = " plan(s)";
        }

        /* --- nested: step execution --- */
        public static final class Step {
            public static final String UNKNOWN_TYPE = "Unknown step type: ";
            public static final String INVALID_CONFIG = "Invalid step config JSON: ";
            public static final String REQUIRED_FAILED = "Required step failed, stopping plan: ";
            public static final String EXCEEDED_TIMEOUT = "Grading exceeded execution timeout";
            public static final String SKIPPED_REQUIRED_FAILED = "Skipped: required step failed earlier in plan";
            public static final String HTTP_STEP_FAILED = "HTTP step '{}' failed: {}";
            public static final String EXTRACT_FAILED = "Extract '{}' failed: {}";
            public static final String HTTP_LOG_SAVE_FAILED = "Failed to save http_log: {}";
        }

        /* --- nested: infrastructure/compose errors --- */
        public static final class InfraError {
            public static final String NO_COMPOSE_TEMPLATE = "Assignment has no docker compose template";
            public static final String NO_COMPOSE_FILE = "docker-compose.yml not found in submission";
            public static final String NO_SERVICES = "Compose file defines no services";
            public static final String NO_TOP_LEVEL = "Compose file has no top-level mapping";
            public static final String NO_SERVICES_SECTION = "Compose file has no services section";
            public static final String PRIVILEGED_MODE_PREFIX = "Service uses privileged mode: ";
            public static final String MOUNTS_DOCKER_SOCK_PREFIX = "Service mounts docker.sock: ";
            public static final String COMPOSE_STOP_FAILED = "Compose stop failed: {}";
        }

        /* --- nested: artifact --- */
        public static final class ArtifactMessage {
            public static final String FAILED_FETCH = "Failed to fetch submission artifact: ";
            public static final String FAILED_DOWNLOAD_OBJECT = "Failed to download object: ";
            public static final String ZIP_ESCAPE_PREFIX = "Zip entry escapes work dir: ";
        }

        /* --- nested: assertion/body/JSON path --- */
        public static final class Assertion {
            public static final String STATUS_MATCHED = "status matched";
            public static final String BODY_CONTAINS_PREFIX = "body contains '";
            public static final String BODY_CONTAINS_SUFFIX = "'";
            public static final String BODY_DOES_NOT_CONTAIN_PREFIX = "Body does not contain '";
            public static final String BODY_DOES_NOT_CONTAIN_SUFFIX = "'";
            public static final String UNKNOWN_KIND = "Unknown assertion kind: ";
            public static final String JSON_PATH_EXISTS_PREFIX = "json_path '";
            public static final String JSON_PATH_EXISTS_MIDDLE = "' existence matched";
            public static final String JSON_PATH_EXPECTED_PREFIX = "' expected exists=";
            public static final String JSON_PATH_EXPECTED_MIDDLE = " but was ";
            public static final String JSON_PATH_ERROR_PREFIX = "JSONPath error: ";
            public static final String BODY_EQUALS_PARSE_ERROR = "body_equals parse error: ";
            public static final String BODY_STRUCTURE_PARSE_ERROR = "body_structure parse error: ";
            public static final String BODY_EQUALS_MATCHED = "body equals matched";
            public static final String BODY_STRUCTURE_MATCHED = "body structure matched";
            public static final String JSON_PATH_NOT_FOUND = "json_path correctly not found";
            public static final String BODY_NOT_EQUAL_PREFIX = "Body not equal. Expected: ";
            public static final String BODY_STRUCTURE_MISMATCH_PREFIX = "Body structure mismatch. Expected keys: ";
        }

        /* --- nested: misc --- */
        public static final class Misc {
            public static final String NO_FREE_PORTS_PREFIX = "No free grading ports in range ";
            public static final String VARIABLE_NOT_FOUND = "Variable '{}' not found in context, replacing with empty string";
        }

        /* --- top-level aliases (kept for backward compatibility, will be phased out) --- */
        public static final String FAILED_FETCH_CONFIG = Message.Infra.FETCH_CONFIG;
        public static final String FAILED_DOWNLOAD_SUBMISSION = Message.Infra.DOWNLOAD_SUBMISSION;
        public static final String FAILED_GRADING_INFRA = Message.Infra.GRADING_INFRA;
        public static final String SKIPPED_REQUIRED_FAILED = Message.Step.SKIPPED_REQUIRED_FAILED;
        public static final String EXCEEDED_TIMEOUT = Message.Step.EXCEEDED_TIMEOUT;
        public static final String INVALID_STEP_CONFIG = Message.Step.INVALID_CONFIG;
        public static final String UNKNOWN_STEP_TYPE = Message.Step.UNKNOWN_TYPE;
        public static final String REQUIRED_STEP_FAILED = Message.Step.REQUIRED_FAILED;
        public static final String NO_COMPOSE_TEMPLATE = Message.InfraError.NO_COMPOSE_TEMPLATE;
        public static final String NO_COMPOSE_FILE = Message.InfraError.NO_COMPOSE_FILE;
        public static final String NO_SERVICES = Message.InfraError.NO_SERVICES;
        public static final String NO_TOP_LEVEL = Message.InfraError.NO_TOP_LEVEL;
        public static final String NO_SERVICES_SECTION = Message.InfraError.NO_SERVICES_SECTION;
        public static final String PRIVILEGED_MODE_PREFIX = Message.InfraError.PRIVILEGED_MODE_PREFIX;
        public static final String MOUNTS_DOCKER_SOCK_PREFIX = Message.InfraError.MOUNTS_DOCKER_SOCK_PREFIX;
        public static final String HTTP_STEP_FAILED = Message.Step.HTTP_STEP_FAILED;
        public static final String EXTRACT_FAILED = Message.Step.EXTRACT_FAILED;
        public static final String HTTP_LOG_SAVE_FAILED = Message.Step.HTTP_LOG_SAVE_FAILED;
        public static final String COMPOSE_STOP_FAILED = Message.InfraError.COMPOSE_STOP_FAILED;
        public static final String VARIABLE_NOT_FOUND = Message.Misc.VARIABLE_NOT_FOUND;
        public static final String FAILED_FETCH_ARTIFACT = Message.ArtifactMessage.FAILED_FETCH;
        public static final String FAILED_DOWNLOAD_OBJECT = Message.ArtifactMessage.FAILED_DOWNLOAD_OBJECT;
        public static final String ZIP_ESCAPE_PREFIX = Message.ArtifactMessage.ZIP_ESCAPE_PREFIX;
        public static final String NO_FREE_PORTS_PREFIX = Message.Misc.NO_FREE_PORTS_PREFIX;
        public static final String STATUS_MATCHED = Message.Assertion.STATUS_MATCHED;
        public static final String BODY_CONTAINS_PREFIX = Message.Assertion.BODY_CONTAINS_PREFIX;
        public static final String BODY_CONTAINS_SUFFIX = Message.Assertion.BODY_CONTAINS_SUFFIX;
        public static final String BODY_DOES_NOT_CONTAIN_PREFIX = Message.Assertion.BODY_DOES_NOT_CONTAIN_PREFIX;
        public static final String BODY_DOES_NOT_CONTAIN_SUFFIX = Message.Assertion.BODY_DOES_NOT_CONTAIN_SUFFIX;
        public static final String UNKNOWN_ASSERTION_KIND_PREFIX = Message.Assertion.UNKNOWN_KIND;
        public static final String JSON_PATH_EXISTENCE_MATCHED_PREFIX = Message.Assertion.JSON_PATH_EXISTS_PREFIX;
        public static final String JSON_PATH_EXISTENCE_MATCHED_MIDDLE = Message.Assertion.JSON_PATH_EXISTS_MIDDLE;
        public static final String JSON_PATH_EXPECTED_EXISTS_PREFIX = Message.Assertion.JSON_PATH_EXPECTED_PREFIX;
        public static final String JSON_PATH_EXPECTED_EXISTS_MIDDLE = Message.Assertion.JSON_PATH_EXPECTED_MIDDLE;
        public static final String JSON_PATH_ERROR_PREFIX = Message.Assertion.JSON_PATH_ERROR_PREFIX;
        public static final String BODY_EQUALS_PARSE_ERROR_PREFIX = Message.Assertion.BODY_EQUALS_PARSE_ERROR;
        public static final String BODY_STRUCTURE_PARSE_ERROR_PREFIX = Message.Assertion.BODY_STRUCTURE_PARSE_ERROR;
        public static final String BODY_EQUALS_MATCHED = Message.Assertion.BODY_EQUALS_MATCHED;
        public static final String BODY_STRUCTURE_MATCHED = Message.Assertion.BODY_STRUCTURE_MATCHED;
        public static final String JSON_PATH_CORRECTLY_NOT_FOUND = Message.Assertion.JSON_PATH_NOT_FOUND;
        public static final String BODY_NOT_EQUAL_PREFIX = Message.Assertion.BODY_NOT_EQUAL_PREFIX;
        public static final String BODY_STRUCTURE_MISMATCH_PREFIX = Message.Assertion.BODY_STRUCTURE_MISMATCH_PREFIX;
        public static final String FETCHING_PREFIX = Message.Infra.FETCHING_PREFIX;
        public static final String BUILDING_PREFIX = Message.Infra.BUILDING_PREFIX;
        public static final String RUNNING_PREFIX = Message.Infra.RUNNING_PREFIX;
        public static final String PLAN_SUFFIX = Message.Infra.PLAN_SUFFIX;
        public static final String GRADE_RECEIVED = Message.Grade.RECEIVED;
        public static final String GRADE_RECEIVED_SUFFIX = Message.Grade.RECEIVED_SUFFIX;
        public static final String GRADE_PERSISTED_PREFIX = Message.Grade.PERSISTED_PREFIX;
        public static final String GRADE_DUPLICATE_PREFIX = Message.Grade.DUPLICATE_PREFIX;
        public static final String REENQUEUE_PREFIX = Message.Grade.REENQUEUE_PREFIX;
        public static final String RESET_PREFIX = Message.Reset.LOG_PREFIX;
        public static final String RESET_SUCCESS_PREFIX = Message.Reset.SUCCESS_PREFIX;
        public static final String RESET_FAILED_PREFIX = Message.Reset.FAILED_PREFIX;
        public static final String RESET_NOT_FOUND_PREFIX = Message.Reset.NOT_FOUND;
    }

    private Constant()
    {
    }
}
