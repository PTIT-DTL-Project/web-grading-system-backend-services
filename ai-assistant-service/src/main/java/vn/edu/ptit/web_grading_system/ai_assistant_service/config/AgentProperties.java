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
        private int maxModelSteps = 8;
        private int maxToolCalls = 12;
        private int maxModelAttempts = 16;
        private int maxValidationRepairs = 2;
        private int maxLookupRounds = 2;
        private int activeTimeoutSeconds = 120;
    }

    @Data
    public static class Retry {
        private int modelMaxRetries = 2;
        private int safeToolMaxRetries = 2;
    }

    @Data
    public static class Context {
        private int maxInputTokens = 26000;
        private int outputReserve = 4000;
        private int safetyMargin = 2000;
        private int historyWindowSize = 10;
        private int initialToolLimit = 4;
        private int toolSearchLimit = 5;
        private int historyMaxTokens = 12000;
        private int toolMaxTokens = 6000;
        private int storedToolResultTtlMinutes = 60;
    }
}
