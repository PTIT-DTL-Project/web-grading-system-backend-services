package vn.edu.ptit.web_grading_system.executor_service.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class VariableContext {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    public void put(String name, Object value) {
        variables.put(name, value);
    }

    public Object get(String name) {
        return variables.get(name);
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(variables);
    }

    /**
     * Replace every ${var} in template with its value from context.
     * Missing variables become empty string and a WARN is logged.
     */
    public String substitute(String template) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            if (value == null) {
                log.warn("Variable '{}' not found in context, replacing with empty string", varName);
                matcher.appendReplacement(sb, "");
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}