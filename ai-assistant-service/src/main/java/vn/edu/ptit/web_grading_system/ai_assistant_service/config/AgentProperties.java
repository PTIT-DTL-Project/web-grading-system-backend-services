package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Limits limits = new Limits();
    private Retry retry = new Retry();
    private Context context = new Context();

    @Data
    public static class Limits {
        private int maxModelSteps = 16;
        private int maxToolCalls = 24;
        private int maxModelAttempts = 24;
        private int maxValidationRepairs = 3;
        private int maxLookupRounds = 3;
        private int activeTimeoutSeconds = 180;
    }

    @Data
    public static class Retry {
        private int modelMaxRetries = 2;
        private int safeToolMaxRetries = 2;
    }

    @Data
    public static class Context {
        private int maxInputTokens = 64000;
        private int outputReserve = 4096;
        private int safetyMargin = 2000;
        private int historyWindowSize = 30;
        private int toolSearchLimit = 10;
        private int historyMaxTokens = 40000;
        private int toolMaxTokens = 24000;
        private int storedToolResultTtlMinutes = 60;
    }
}
