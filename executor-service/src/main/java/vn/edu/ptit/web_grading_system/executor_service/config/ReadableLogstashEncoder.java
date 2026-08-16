package vn.edu.ptit.web_grading_system.executor_service.config;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.decorate.PrettyPrintingJsonGeneratorDecorator;
import net.logstash.logback.encoder.LogstashEncoder;

public class ReadableLogstashEncoder extends LogstashEncoder {

    private boolean isReadable() {
        String env = getContext() != null ? getContext().getProperty("APP_ENV") : "local";
        return env == null || env.isBlank() || "local".equals(env) || "default".equals(env);
    }

    @Override
    public void start() {
        if (isReadable()) {
            setJsonGeneratorDecorator(new PrettyPrintingJsonGeneratorDecorator());
        }
        super.start();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] json = super.encode(event);
        if (!isReadable()) {
            return json;
        }
        String readable = new String(json, StandardCharsets.UTF_8)
                .replace("\\t", "\t")
                .replace("\\n", "\n");
        return readable.getBytes(StandardCharsets.UTF_8);
    }
}