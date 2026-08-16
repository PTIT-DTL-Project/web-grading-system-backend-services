package vn.edu.ptit.web_grading_system.api_gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        String readable = unescapeField(new String(json, StandardCharsets.UTF_8), "message");
        readable = unescapeField(readable, "stack_trace");
        return readable.getBytes(StandardCharsets.UTF_8);
    }

    private String unescapeField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:").matcher(json);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(json, last, matcher.end());
            int i = matcher.end();
            if (i < json.length() && json.charAt(i) == '"') {
                i++;
                StringBuilder value = new StringBuilder();
                while (i < json.length()) {
                    char c = json.charAt(i);
                    if (c == '\\' && i + 1 < json.length()) {
                        char next = json.charAt(i + 1);
                        if (next == 'n') {
                            value.append('\n');
                        } else if (next == 't') {
                            value.append('\t');
                        } else {
                            value.append(c).append(next);
                        }
                        i += 2;
                    } else if (c == '"') {
                        break;
                    } else {
                        value.append(c);
                        i++;
                    }
                }
                out.append('"').append(value).append('"');
                last = i + 1;
            } else {
                last = matcher.end();
            }
        }
        out.append(json, last, json.length());
        return out.toString();
    }
}
