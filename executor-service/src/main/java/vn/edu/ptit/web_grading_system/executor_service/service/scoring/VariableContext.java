package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Per-job variable context.
 *
 * <p>Stores extracted variables as {@link Object} (not just String) so that a
 * variable holding a JSON array/object (from {@code extract as: "json"}) can be
 * navigated later with deep access such as {@code ${books[0].id}}.</p>
 *
 * <p>Tokens prefixed with {@code $} are built-in dynamic variables resolved at
 * substitution time (never stored): {@code $UUID}, {@code $TIMESTAMP},
 * {@code $FAKER_NAME}, {@code $FAKER_BOOK_TITLE}, {@code $FAKER_WORD},
 * {@code $FAKER_EMAIL}.</p>
 */
@Slf4j
public class VariableContext
{
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Configuration jsonPathConfig = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    private final Map<String, Object> variables = new ConcurrentHashMap<>();

    public void put(String name, Object value)
    {
        variables.put(name, value);
    }

    public Object get(String name)
    {
        return variables.get(name);
    }

    public Map<String, Object> snapshot()
    {
        return Map.copyOf(variables);
    }

    /**
     * Replace every ${var} in template with its value from context.
     * Missing variables become empty string and a WARN is logged.
     *
     * <p>Built-in dynamic variables ($-prefixed) are generated on the fly and
     * never stored. Deep access into a stored JSON value is supported via
     * JsonPath after the base variable name, e.g. {@code ${books[0].id}}.</p>
     */
    public String substitute(String template)
    {
        if (template == null)
        {
            return null;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find())
        {
            String token = matcher.group(1);
            String resolved = resolveToken(token);
            if (resolved == null)
            {
                log.warn(Constant.Message.VARIABLE_NOT_FOUND, token);
                matcher.appendReplacement(sb, "");
            }
            else
            {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve a single {@code ${...}} token.
     *
     * <ul>
     *   <li>Built-in dynamic ({@code $UUID}, {@code $TIMESTAMP}, {@code $FAKER_*}):
     *       generated on the fly.</li>
     *   <li>Deep access ({@code books[0].id}): base variable looked up, then the
     *       remainder navigated with JsonPath against the stored value.</li>
     *   <li>Plain variable: stored value stringified.</li>
     * </ul>
     */
    private String resolveToken(String token)
    {
        if (token.startsWith(Constant.VariableContext.DYNAMIC_PREFIX))
        {
            return resolveDynamic(token);
        }

        // Deep access: books[0].id -> base = "books", nav = "[0].id"
        int bracketIdx = token.indexOf('[');
        if (bracketIdx > 0)
        {
            String baseName = token.substring(0, bracketIdx);
            String nav = token.substring(bracketIdx);
            Object base = variables.get(baseName);
            if (base == null)
            {
                return null;
            }
            Object navValue = navigateJson(base, nav);
            return navValue == null ? null : String.valueOf(navValue);
        }

        Object value = variables.get(token);
        return value == null ? null : String.valueOf(value);
    }

    private String resolveDynamic(String token)
    {
        return switch (token)
        {
            case Constant.VariableContext.DYNAMIC_UUID -> UUID.randomUUID().toString();
            case Constant.VariableContext.DYNAMIC_TIMESTAMP ->
                    String.valueOf(System.currentTimeMillis());
            case Constant.VariableContext.FAKER_NAME -> fakerName();
            case Constant.VariableContext.FAKER_BOOK_TITLE -> fakerBookTitle();
            case Constant.VariableContext.FAKER_WORD -> fakerWord();
            case Constant.VariableContext.FAKER_EMAIL -> fakerEmail();
            default -> {
                if (token.startsWith(Constant.VariableContext.DYNAMIC_FAKER_PREFIX))
                {
                    yield fakerFallback(token);
                }
                yield null;
            }
        };
    }

    private Object navigateJson(Object base, String nav)
    {
        if (base == null)
        {
            return null;
        }
        String json;
        if (base instanceof String str)
        {
            json = str;
        }
        else
        {
            json = new com.google.gson.Gson().toJson(base);
        }
        try
        {
            Object result = JsonPath.using(jsonPathConfig).parse(json).read(nav);
            return result;
        }
        catch (Exception e)
        {
            log.warn("JsonPath navigate failed for '{}': {}", nav, e.getMessage());
            return null;
        }
    }

    /* ---------------- faker helpers (deterministic, no external dep) ---------------- */

    private static final String[] FIRST_NAMES = {
            "Nguyen", "Tran", "Le", "Pham", "Hoang", "Phan", "Vu", "Do"
    };
    private static final String[] LAST_NAMES = {
            "Van Anh", "Thi Hoa", "Minh", "Quang", "Lan", "Duc", "Hai", "Mai"
    };
    private static final String[] BOOK_TITLES = {
            "Lap trinh Java", "Co so du lieu", "Mang may tinh", "Phat trien web",
            "He dieu hanh", "Toan rung doi", "Ky thuat phan mem", "Tri tue nhan tao"
    };
    private static final String[] WORDS = {
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta"
    };
    private static final String[] DOMAINS = {
            "gmail.com", "yahoo.com", "outlook.com", "example.com"
    };
    private final java.util.Random random = new java.util.Random();

    private String fakerName()
    {
        return FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " "
                + LAST_NAMES[random.nextInt(LAST_NAMES.length)];
    }

    private String fakerBookTitle()
    {
        return BOOK_TITLES[random.nextInt(BOOK_TITLES.length)]
                + " @"
                + (1000 + random.nextInt(9000));
    }

    private String fakerWord()
    {
        return WORDS[random.nextInt(WORDS.length)];
    }

    private String fakerEmail()
    {
        return fakerWord() + random.nextInt(10000) + "@"
                + DOMAINS[random.nextInt(DOMAINS.length)];
    }

    private String fakerFallback(String token)
    {
        // Unknown $FAKER_* token: return a random alphanumeric token so the
        // lecturer can still use it without breaking the step.
        return "faker-" + random.nextInt(100000);
    }
}
