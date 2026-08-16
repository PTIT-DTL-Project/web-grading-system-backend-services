package vn.edu.ptit.web_grading_system.executor_service.config;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.decorate.PrettyPrintingJsonGeneratorDecorator;
import net.logstash.logback.encoder.LogstashEncoder;

public class ReadableLogstashEncoder extends LogstashEncoder {

    @Override
    public void start() {
        if (!"prod".equals(getContext() != null ? getContext().getProperty("APP_ENV") : "local")) {
            setJsonGeneratorDecorator(new PrettyPrintingJsonGeneratorDecorator());
        }
        super.start();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] json = super.encode(event);
        if ("prod".equals(getContext() != null ? getContext().getProperty("APP_ENV") : "local")) {
            return json;
        }
        String readable = new String(json, StandardCharsets.UTF_8)
                .replace("\\t", "\t")
                .replace("\\n", "\n");
        return readable.getBytes(StandardCharsets.UTF_8);
    }
}