# User Management & Auth Flow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `user-service` microservice that manages Auth (login/refresh/logout/change-password) and Admin user lifecycle (CRUD + CSV/Excel import) using Keycloak as the single source of truth.

**Architecture:** New Spring Boot service (`user-service`, port 8085) using Keycloak Admin Client for all user operations. Follows the identical `HeaderAuthenticationFilter` + `UserPrincipal` security pattern as `course-service`. API Gateway updated to route `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/admin/users/**` to the new service, with `/api/v1/auth/**` added to PUBLIC_PATHS (no JWT required for login).

**Tech Stack:** Java 21, Spring Boot 4.1.0, keycloak-admin-client 26.0.0, opencsv 5.9, poi-ooxml 5.2.5, Lombok, Spring Security (header-based, same as course-service)

## Global Constraints

- Java version: 21 (consistent with all other services)
- Spring Boot: 4.1.0 (consistent with all other services)
- Spring Cloud: 2025.1.2 (consistent with api-gateway)
- Base package: `vn.edu.ptit.web_grading_system.user_service`
- Service port: 8085
- Keycloak realm: `ptit-wgs`
- Keycloak version: 26.0.0 (matches docker-compose image `quay.io/keycloak/keycloak:26.0`)
- Security pattern: `HeaderAuthenticationFilter` + `UserPrincipal` (copy from `course-service`, adapt package)
- All entity IDs: UUID
- API response wrapper: `ApiResponse<T>` with fields `statusCode`, `message`, `data`
- Auth endpoint security: `/api/v1/auth/**` is PUBLIC (no X-Gateway-Secret required) — handled at gateway + service level
- Gateway secret env var: `GATEWAY_TRUSTED_SECRET`
- ROLE names in Keycloak: `ROLE_ADMIN`, `ROLE_LECTURER`, `ROLE_STUDENT`
- `allowed-roles` in gateway yaml uses comma-separated env var `GATEWAY_ALLOWED_ROLES`

---

### Task 1: Gateway Changes

**Files:**
- Modify: `api-gateway/src/main/java/vn/edu/ptit/web_grading_system/api_gateway/config/SecurityConfig.java` (lines 17–21)
- Modify: `api-gateway/src/main/resources/application.yaml` (routes section + allowed-roles)

**Interfaces:**
- Produces: Gateway routes `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/admin/users/**` → `user-service`; `ROLE_ADMIN` forwarded in `X-User-Roles` header

---

- [ ] **Step 1: Add `/api/v1/auth/**` to gateway PUBLIC_PATHS**

File: `api-gateway/src/main/java/vn/edu/ptit/web_grading_system/api_gateway/config/SecurityConfig.java`

Replace the `PUBLIC_PATHS` constant (lines 17–21):
```java
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
            "/api/v1/auth/**"
    };
```

- [ ] **Step 2: Add user-service route and ROLE_ADMIN to gateway application.yaml**

File: `api-gateway/src/main/resources/application.yaml`

Add the user-service route after the result-service route block (after line 28), and update `allowed-roles` default:

```yaml
            - id: user-service
              uri: ${USER_SERVICE_URI:http://localhost:8085}
              predicates:
                - Path=/api/v1/auth/**,/api/v1/users/**,/api/v1/admin/users/**
```

Change line 39 from:
```yaml
    allowed-roles: ${GATEWAY_ALLOWED_ROLES:LECTURER,STUDENT}
```
to:
```yaml
    allowed-roles: ${GATEWAY_ALLOWED_ROLES:ROLE_ADMIN,ROLE_LECTURER,ROLE_STUDENT}
```

> **Note:** The `allowedRoles` list is intersected with JWT `realm_access.roles` in `AuthenticationContextFilter`. Keycloak emits role names with `ROLE_` prefix in realm roles, so the list must include the prefix.

- [ ] **Step 3: Build gateway to verify no compilation errors**

```bash
cd api-gateway && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add api-gateway/src/main/java/vn/edu/ptit/web_grading_system/api_gateway/config/SecurityConfig.java \
        api-gateway/src/main/resources/application.yaml
git commit -m "feat(gateway): route user-service, add ROLE_ADMIN to allowed-roles, permit /auth/**"
```

---

### Task 2: Keycloak Realm Seed Update

**Files:**
- Modify: `ptit-wgs-realm.json`

**Interfaces:**
- Produces: Admin user with `ROLE_ADMIN`, service account client `wgs-user-service` with manage-users permissions; required for `user-service` Keycloak Admin API calls.

---

- [ ] **Step 1: Add admin seed user and wgs-user-service client to realm JSON**

File: `ptit-wgs-realm.json`

Replace entire file content with:
```json
{
  "realm": "ptit-wgs",
  "enabled": true,
  "users": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "username": "lecturer_test",
      "enabled": true,
      "emailVerified": true,
      "firstName": "Lecturer",
      "lastName": "Test",
      "email": "lecturer@ptit.edu.vn",
      "requiredActions": [],
      "credentials": [
        { "type": "password", "value": "123456", "temporary": false }
      ],
      "realmRoles": ["ROLE_LECTURER"],
      "attributes": {
        "status": ["ACTIVE"],
        "staff_code": ["GV001"],
        "department": ["CNTT"],
        "title": ["ThS"]
      }
    },
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "username": "student_test",
      "enabled": true,
      "emailVerified": true,
      "firstName": "Student",
      "lastName": "Test",
      "email": "student@ptit.edu.vn",
      "requiredActions": [],
      "credentials": [
        { "type": "password", "value": "123456", "temporary": false }
      ],
      "realmRoles": ["ROLE_STUDENT"],
      "attributes": {
        "status": ["ACTIVE"],
        "student_code": ["B22DCCN000"],
        "department": ["CNTT"],
        "batch": ["D22"],
        "program": ["Kỹ thuật phần mềm"],
        "class_code": ["D22CQCN01-N"]
      }
    },
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "username": "admin",
      "enabled": true,
      "emailVerified": true,
      "firstName": "System",
      "lastName": "Admin",
      "email": "admin@ptit.edu.vn",
      "requiredActions": ["UPDATE_PASSWORD"],
      "credentials": [
        { "type": "password", "value": "Admin@123", "temporary": true }
      ],
      "realmRoles": ["ROLE_ADMIN"],
      "attributes": {
        "status": ["ACTIVE"]
      }
    }
  ],
  "roles": {
    "realm": [
      { "name": "ROLE_STUDENT", "description": "Student Role" },
      { "name": "ROLE_LECTURER", "description": "Lecturer Role" },
      { "name": "ROLE_ADMIN", "description": "Admin Role" }
    ]
  },
  "clients": [
    {
      "clientId": "wgs-postman",
      "enabled": true,
      "publicClient": true,
      "directAccessGrantsEnabled": true,
      "standardFlowEnabled": true,
      "redirectUris": ["*"]
    },
    {
      "clientId": "wgs-user-service",
      "enabled": true,
      "publicClient": false,
      "secret": "wgs-user-service-secret",
      "serviceAccountsEnabled": true,
      "directAccessGrantsEnabled": false,
      "standardFlowEnabled": false
    }
  ],
  "scopeMappings": [
    {
      "client": "wgs-user-service",
      "roles": ["ROLE_ADMIN"]
    }
  ]
}
```

> **Note:** The `wgs-user-service` client secret `wgs-user-service-secret` is used in dev only. In production, generate a random secret and store in environment variable `KEYCLOAK_ADMIN_CLIENT_SECRET`.

> **Note:** Keycloak service account role assignment (manage-users, view-users) via realm JSON is complex — after Keycloak starts with this seed, run the manual step below OR automate via Keycloak Admin API on startup.

- [ ] **Step 2: Document manual Keycloak service account role assignment**

After Keycloak starts with the new realm, assign `realm-management` client roles to `wgs-user-service` service account via Keycloak Admin UI:
1. Login http://localhost:8180 with admin/admin
2. Select realm `ptit-wgs`
3. Clients → `wgs-user-service` → Service account roles
4. Assign: `realm-management` → `manage-users`, `view-users`, `query-users`, `manage-realm`

OR use the helper script added in Task 3 that does this automatically via Admin REST API on first startup.

- [ ] **Step 3: Commit**

```bash
git add ptit-wgs-realm.json
git commit -m "feat(keycloak): add admin user seed, wgs-user-service client, user attributes"
```

---

### Task 3: user-service Project Scaffold

**Files:**
- Create: `user-service/pom.xml`
- Create: `user-service/Dockerfile`
- Create: `user-service/.env.dev`
- Create: `user-service/src/main/resources/application.yaml`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/UserServiceApplication.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/GatewayTrustProperties.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/SecurityConfig.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/security/HeaderAuthenticationFilter.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/security/UserPrincipal.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/security/SecurityUtils.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/ApiResponse.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: Compilable Spring Boot project at port 8085; `HeaderAuthenticationFilter` that reads `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Gateway-Secret`; `UserPrincipal(UUID userId, String email, Collection<? extends GrantedAuthority> authorities)`; `ApiResponse<T>` wrapper.

---

- [ ] **Step 1: Create pom.xml**

File: `user-service/pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>vn.edu.ptit.web-grading-system</groupId>
    <artifactId>user-service</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>User Service</name>
    <description>User management and auth service for PTIT Web Grading System</description>

    <properties>
        <java.version>21</java.version>
        <keycloak.version>26.0.0</keycloak.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Keycloak Admin Client -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-admin-client</artifactId>
            <version>${keycloak.version}</version>
        </dependency>

        <!-- CSV parsing -->
        <dependency>
            <groupId>com.opencsv</groupId>
            <artifactId>opencsv</artifactId>
            <version>5.9</version>
        </dependency>

        <!-- Excel parsing -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
        </dependency>

        <!-- OpenAPI docs -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.9</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <phase>compile</phase>
                        <goals><goal>compile</goal></goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                    <execution>
                        <id>default-testCompile</id>
                        <phase>test-compile</phase>
                        <goals><goal>testCompile</goal></goals>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create application.yaml**

File: `user-service/src/main/resources/application.yaml`
```yaml
server:
  port: 8085

spring:
  application:
    name: user-service
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 11MB

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

logging:
  level:
    root: INFO
    vn.edu.ptit.web_grading_system: ${app.log.level:INFO}

# Keycloak Admin API config
keycloak:
  server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8180}
  realm: ${KEYCLOAK_REALM:ptit-wgs}
  admin-client-id: ${KEYCLOAK_ADMIN_CLIENT_ID:wgs-user-service}
  admin-client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:wgs-user-service-secret}

# Gateway trust secret — same secret the API Gateway stamps as X-Gateway-Secret
gateway:
  security:
    secret: ${GATEWAY_TRUSTED_SECRET:}
```

- [ ] **Step 3: Create .env.dev**

File: `user-service/.env.dev`
```env
GATEWAY_TRUSTED_SECRET=dev-secret-change-in-prod
KEYCLOAK_SERVER_URL=http://localhost:8180
KEYCLOAK_REALM=ptit-wgs
KEYCLOAK_ADMIN_CLIENT_ID=wgs-user-service
KEYCLOAK_ADMIN_CLIENT_SECRET=wgs-user-service-secret
```

- [ ] **Step 4: Create Dockerfile**

File: `user-service/Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/user-service-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 5: Create UserServiceApplication**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/UserServiceApplication.java`
```java
package vn.edu.ptit.web_grading_system.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

- [ ] **Step 6: Create GatewayTrustProperties**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/GatewayTrustProperties.java`
```java
package vn.edu.ptit.web_grading_system.user_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Secret the API Gateway stamps on every forwarded request as {@code X-Gateway-Secret}.
 * Bound from {@code gateway.security.secret} in {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "gateway.security")
public record GatewayTrustProperties(String secret) {
}
```

- [ ] **Step 7: Create UserPrincipal**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/security/UserPrincipal.java`
```java
package vn.edu.ptit.web_grading_system.user_service.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public record UserPrincipal(
        UUID userId,
        String email,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails, Serializable {

    public UserPrincipal {
        if (authorities == null) {
            authorities = Collections.emptyList();
        }
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return userId != null ? userId.toString() : ""; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
```

- [ ] **Step 8: Create SecurityUtils**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/security/SecurityUtils.java`
```java
package vn.edu.ptit.web_grading_system.user_service.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 9: Create HeaderAuthenticationFilter**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/security/HeaderAuthenticationFilter.java`
```java
package vn.edu.ptit.web_grading_system.user_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.ptit.web_grading_system.user_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.user_service.config.SecurityConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID     = "X-User-Id";
    public static final String HEADER_USER_EMAIL  = "X-User-Email";
    public static final String HEADER_USER_ROLES  = "X-User-Roles";
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayTrustProperties properties;
    private final AtomicBoolean secretWarned = new AtomicBoolean(false);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : SecurityConfig.PUBLIC_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) return true;
        }
        return !hasGatewaySecret(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userIdStr = request.getHeader(HEADER_USER_ID);
        String email     = request.getHeader(HEADER_USER_EMAIL);
        if (StringUtils.hasText(userIdStr) && !"anonymous".equalsIgnoreCase(userIdStr.trim())) {
            try {
                UUID userId = UUID.fromString(userIdStr.trim());
                List<GrantedAuthority> authorities = parseRoles(request.getHeader(HEADER_USER_ROLES));
                UserPrincipal principal = new UserPrincipal(userId, email, authorities);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for X-User-Id header: {}", userIdStr);
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static List<GrantedAuthority> parseRoles(String header) {
        if (!StringUtils.hasText(header)) return List.of();
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .map(role -> role.startsWith(ROLE_PREFIX) ? role.substring(ROLE_PREFIX.length()) : role)
                .filter(role -> !role.isEmpty())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .distinct().toList();
    }

    private boolean hasGatewaySecret(HttpServletRequest request) {
        String expected = properties.secret();
        if (!StringUtils.hasText(expected)) {
            if (secretWarned.compareAndSet(false, true)) {
                log.error("gateway.security.secret is blank — every authenticated request will be rejected.");
            }
            return false;
        }
        String provided = request.getHeader(HEADER_GATEWAY_SECRET);
        if (!StringUtils.hasText(provided)) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 10: Create SecurityConfig**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/SecurityConfig.java`
```java
package vn.edu.ptit.web_grading_system.user_service.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.edu.ptit.web_grading_system.user_service.security.HeaderAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthenticationFilter headerAuthenticationFilter;

    // Auth endpoints are public — no JWT or gateway secret required.
    // HeaderAuthenticationFilter.shouldNotFilter uses this same list.
    public static final String[] PUBLIC_PATHS = {
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api/v1/auth/**",
            "/api/v1/internal/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"statusCode\":401,\"message\":\"Unauthorized\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"statusCode\":403,\"message\":\"Forbidden\",\"data\":null}");
                        })
                )
                .build();
    }
}
```

- [ ] **Step 11: Create ApiResponse DTO**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/ApiResponse.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int statusCode;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().statusCode(200).message("Success").data(data).build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder().statusCode(201).message("Created").data(data).build();
    }

    public static <T> ApiResponse<T> noContent() {
        return ApiResponse.<T>builder().statusCode(204).message("No Content").data(null).build();
    }
}
```

- [ ] **Step 12: Create GlobalExceptionHandler**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/exception/GlobalExceptionHandler.java`
```java
package vn.edu.ptit.web_grading_system.user_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder().statusCode(400).message(e.getMessage()).data(null).build());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder().statusCode(404).message(e.getMessage()).data(null).build());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder().statusCode(409).message(e.getMessage()).data(null).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst().orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder().statusCode(400).message(msg).data(null).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder().statusCode(500).message("Internal server error").data(null).build());
    }
}
```

- [ ] **Step 13: Create exception classes**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/exception/BadRequestException.java`
```java
package vn.edu.ptit.web_grading_system.user_service.exception;
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/exception/ResourceNotFoundException.java`
```java
package vn.edu.ptit.web_grading_system.user_service.exception;
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/exception/ConflictException.java`
```java
package vn.edu.ptit.web_grading_system.user_service.exception;
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
```

- [ ] **Step 14: Verify compilation**

```bash
cd user-service && ./mvnw compile -q
```
Expected: BUILD SUCCESS (no errors)

- [ ] **Step 15: Commit**

```bash
git add user-service/
git commit -m "feat(user-service): scaffold project, security pattern, exception handling"
```

---

### Task 4: Keycloak Admin Config + Auth Service & Controller

**Files:**
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/KeycloakAdminConfig.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/KeycloakProperties.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/LoginRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/RefreshTokenRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/LogoutRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/ChangePasswordRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/LoginResponse.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/service/AuthService.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/AuthController.java`

**Interfaces:**
- Consumes: `KeycloakProperties(serverUrl, realm, adminClientId, adminClientSecret)`; `Keycloak` Admin Client bean
- Produces:
  - `AuthService.login(LoginRequest) → LoginResponse`
  - `AuthService.refresh(String refreshToken) → LoginResponse`
  - `AuthService.logout(String refreshToken) → void`
  - `AuthService.changePassword(UUID userId, String currentPassword, String newPassword) → void`
  - `LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, boolean requiresPasswordChange)`

---

- [ ] **Step 1: Create KeycloakProperties**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/KeycloakProperties.java`
```java
package vn.edu.ptit.web_grading_system.user_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak Admin API connection settings.
 * Bound from the {@code keycloak} prefix in application.yaml.
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakProperties(
        String serverUrl,
        String realm,
        String adminClientId,
        String adminClientSecret
) {}
```

- [ ] **Step 2: Create KeycloakAdminConfig**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/config/KeycloakAdminConfig.java`
```java
package vn.edu.ptit.web_grading_system.user_service.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class KeycloakAdminConfig {

    private final KeycloakProperties properties;

    /**
     * Admin client using service account (client_credentials grant).
     * The wgs-user-service client must have service accounts enabled in Keycloak
     * and the service account must be assigned realm-management > manage-users roles.
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(properties.serverUrl())
                .realm(properties.realm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(properties.adminClientId())
                .clientSecret(properties.adminClientSecret())
                .build();
    }
}
```

- [ ] **Step 3: Create request DTOs**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/LoginRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Password is required")
    private String password;
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/RefreshTokenRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/LogoutRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/ChangePasswordRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;
    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;
}
```

- [ ] **Step 4: Create LoginResponse**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/LoginResponse.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    /** Present and true only when the user must change password before proceeding. */
    private Boolean requiresPasswordChange;
}
```

- [ ] **Step 5: Create AuthService**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/service/AuthService.java`
```java
package vn.edu.ptit.web_grading_system.user_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import vn.edu.ptit.web_grading_system.user_service.config.KeycloakProperties;
import vn.edu.ptit.web_grading_system.user_service.dto.request.ChangePasswordRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.request.LoginRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.response.LoginResponse;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakProperties keycloakProperties;
    private final Keycloak keycloakAdminClient;
    private final RestTemplate restTemplate;

    private String tokenEndpoint() {
        return keycloakProperties.serverUrl()
                + "/realms/" + keycloakProperties.realm()
                + "/protocol/openid-connect/token";
    }

    /**
     * Authenticates the user via Keycloak Direct Access Grant.
     * After a successful login, checks Keycloak user requiredActions to detect
     * if the user must change their password (first-login flow).
     */
    public LoginResponse login(LoginRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "wgs-postman");   // public client for user logins
        form.add("username", request.getUsername());
        form.add("password", request.getPassword());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenEndpoint(), new HttpEntity<>(form, headers), Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) throw new BadRequestException("Empty response from Keycloak");

            String accessToken  = (String) body.get("access_token");
            String refreshToken = (String) body.get("refresh_token");
            String tokenType    = (String) body.getOrDefault("token_type", "Bearer");
            long expiresIn      = ((Number) body.get("expires_in")).longValue();

            // Detect first-login: check requiredActions via Admin API
            boolean requiresPasswordChange = hasUpdatePasswordAction(request.getUsername());

            LoginResponse.LoginResponseBuilder builder = LoginResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType(tokenType)
                    .expiresIn(expiresIn);
            if (requiresPasswordChange) {
                builder.requiresPasswordChange(true);
            }
            return builder.build();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new BadRequestException("Invalid username or password");
            }
            throw new BadRequestException("Authentication failed: " + e.getMessage());
        }
    }

    /** Refreshes an access token using a refresh token. */
    public LoginResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", "wgs-postman");
        form.add("refresh_token", refreshToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenEndpoint(), new HttpEntity<>(form, headers), Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) throw new BadRequestException("Empty response from Keycloak");
            return LoginResponse.builder()
                    .accessToken((String) body.get("access_token"))
                    .refreshToken((String) body.get("refresh_token"))
                    .tokenType((String) body.getOrDefault("token_type", "Bearer"))
                    .expiresIn(((Number) body.get("expires_in")).longValue())
                    .build();
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
    }

    /** Revokes the user's refresh token (logout). */
    public void logout(String refreshToken) {
        String logoutUrl = keycloakProperties.serverUrl()
                + "/realms/" + keycloakProperties.realm()
                + "/protocol/openid-connect/logout";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "wgs-postman");
        form.add("refresh_token", refreshToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            restTemplate.postForEntity(logoutUrl, new HttpEntity<>(form, headers), Void.class);
        } catch (HttpClientErrorException e) {
            log.warn("Logout failed (token may already be invalid): {}", e.getMessage());
        }
    }

    /**
     * Changes the user's password and clears the UPDATE_PASSWORD required action.
     * Verifies the current password first by attempting a token request.
     *
     * @param userId          Keycloak user UUID (from X-User-Id header)
     * @param request         contains currentPassword and newPassword
     */
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        // 1. Verify current password by trying to get a token
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "wgs-postman");
        // We need the username — fetch it from Admin API
        UserRepresentation user = keycloakAdminClient.realm(keycloakProperties.realm())
                .users().get(userId.toString()).toRepresentation();
        form.add("username", user.getUsername());
        form.add("password", request.getCurrentPassword());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            restTemplate.postForEntity(tokenEndpoint(), new HttpEntity<>(form, headers), Map.class);
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("Current password is incorrect");
        }

        // 2. Set new password via Admin API
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(false);
        keycloakAdminClient.realm(keycloakProperties.realm())
                .users().get(userId.toString()).resetPassword(credential);

        // 3. Clear UPDATE_PASSWORD required action
        user.setRequiredActions(List.of());
        keycloakAdminClient.realm(keycloakProperties.realm())
                .users().get(userId.toString()).update(user);
    }

    /** Returns true if the given username has UPDATE_PASSWORD in their requiredActions. */
    private boolean hasUpdatePasswordAction(String username) {
        try {
            List<UserRepresentation> users = keycloakAdminClient.realm(keycloakProperties.realm())
                    .users().searchByUsername(username, true);
            if (users.isEmpty()) return false;
            List<String> actions = users.get(0).getRequiredActions();
            return actions != null && actions.contains("UPDATE_PASSWORD");
        } catch (Exception e) {
            log.warn("Could not check requiredActions for user {}: {}", username, e.getMessage());
            return false;
        }
    }
}
```

- [ ] **Step 6: Create RestTemplate bean in KeycloakAdminConfig**

Add to `KeycloakAdminConfig.java` (append inside the class):
```java
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
```

- [ ] **Step 7: Create AuthController**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/AuthController.java`
```java
package vn.edu.ptit.web_grading_system.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.user_service.dto.request.*;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.LoginResponse;
import vn.edu.ptit.web_grading_system.user_service.security.SecurityUtils;
import vn.edu.ptit.web_grading_system.user_service.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Change own password. Requires a valid JWT (caller must be authenticated via gateway).
     * Also used for first-login forced password change.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        var principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException(
                        "Not authenticated"));
        authService.changePassword(principal.userId(), request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
```

> **Note:** `/api/v1/auth/change-password` is in PUBLIC_PATHS at the gateway level (under `/api/v1/auth/**`) but still requires the JWT Bearer token to be passed through. At the service level it's also in `PUBLIC_PATHS`, but `changePassword` reads the `X-User-Id` header injected by the gateway. This means: the user must pass their Bearer token to the gateway, which will validate it, inject `X-User-Id`, and forward to the service — even though the path is "public" from gateway's JWT-checking perspective. This is intentional: the user doesn't need the gateway secret but does need a valid Keycloak token.

- [ ] **Step 8: Verify compilation**

```bash
cd user-service && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add user-service/
git commit -m "feat(user-service): add auth service and controller (login/refresh/logout/change-password)"
```

---

### Task 5: User Profile Endpoint

**Files:**
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/UserProfileResponse.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/UpdateProfileRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/service/UserAdminService.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/UserProfileController.java`

**Interfaces:**
- Consumes: `Keycloak` admin client bean; `KeycloakProperties`; `UserPrincipal` from SecurityUtils
- Produces:
  - `UserAdminService.getUser(String userId) → UserProfileResponse`
  - `UserAdminService.updateUserProfile(String userId, UpdateProfileRequest) → UserProfileResponse`
  - `UserProfileResponse` — all fields listed below

---

- [ ] **Step 1: Create UserProfileResponse**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/UserProfileResponse.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<String> roles;
    private String status;

    // Shared optional attributes
    private String phoneNumber;
    private String gender;
    private String dateOfBirth;
    private String avatarUrl;

    // Student-only attributes
    private String studentCode;
    private String department;
    private String batch;
    private String program;
    private String classCode;

    // Lecturer-only attributes
    private String staffCode;
    private String title;
}
```

- [ ] **Step 2: Create UpdateProfileRequest**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/UpdateProfileRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String gender;      // MALE / FEMALE / OTHER
    private String dateOfBirth; // ISO: 1999-01-15
    private String avatarUrl;
}
```

- [ ] **Step 3: Create UserAdminService (getUser + updateUserProfile + mapping helper)**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/service/UserAdminService.java`
```java
package vn.edu.ptit.web_grading_system.user_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.user_service.config.KeycloakProperties;
import vn.edu.ptit.web_grading_system.user_service.dto.request.*;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserProfileResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserSummaryResponse;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.user_service.exception.ConflictException;
import vn.edu.ptit.web_grading_system.user_service.exception.ResourceNotFoundException;

import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final Keycloak keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;

    private org.keycloak.admin.client.resource.RealmResource realm() {
        return keycloakAdminClient.realm(keycloakProperties.realm());
    }

    // ──────────────────────────────────────────────────────────────────
    // Profile operations (own user)
    // ──────────────────────────────────────────────────────────────────

    public UserProfileResponse getUser(String userId) {
        try {
            UserResource userResource = realm().users().get(userId);
            UserRepresentation ur = userResource.toRepresentation();
            List<String> roles = userResource.roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> name.startsWith("ROLE_"))
                    .collect(Collectors.toList());
            return toProfileResponse(ur, roles);
        } catch (jakarta.ws.rs.NotFoundException e) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    public UserProfileResponse updateUserProfile(String userId, UpdateProfileRequest request) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation ur = userResource.toRepresentation();

        if (request.getFirstName() != null) ur.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) ur.setLastName(request.getLastName());

        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes())
                .orElse(new HashMap<>());
        setAttr(attrs, "phone_number", request.getPhoneNumber());
        setAttr(attrs, "gender",       request.getGender());
        setAttr(attrs, "date_of_birth",request.getDateOfBirth());
        setAttr(attrs, "avatar_url",   request.getAvatarUrl());
        ur.setAttributes(attrs);

        userResource.update(ur);
        return getUser(userId);
    }

    // ──────────────────────────────────────────────────────────────────
    // Admin CRUD operations
    // ──────────────────────────────────────────────────────────────────

    public List<UserSummaryResponse> listUsers(String role, String search, int page, int size) {
        List<UserRepresentation> users;
        if (search != null && !search.isBlank()) {
            users = realm().users().search(search, page * size, size);
        } else {
            users = realm().users().list(page * size, size);
        }
        return users.stream()
                .filter(u -> role == null || hasRole(u.getId(), role))
                .map(u -> toSummaryResponse(u, getRolesForUser(u.getId())))
                .collect(Collectors.toList());
    }

    public UserProfileResponse createUser(CreateUserRequest request) {
        // Check username conflict
        List<UserRepresentation> existing = realm().users().searchByUsername(request.getUsername(), true);
        if (!existing.isEmpty()) throw new ConflictException("Username already exists: " + request.getUsername());

        UserRepresentation ur = new UserRepresentation();
        ur.setUsername(request.getUsername());
        ur.setEmail(request.getEmail());
        ur.setFirstName(request.getFirstName());
        ur.setLastName(request.getLastName());
        ur.setEnabled(true);
        ur.setEmailVerified(true);

        // Password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getPassword());
        credential.setTemporary(request.isForcePasswordChange());
        ur.setCredentials(List.of(credential));

        if (request.isForcePasswordChange()) {
            ur.setRequiredActions(List.of("UPDATE_PASSWORD"));
        }

        // Attributes
        Map<String, List<String>> attrs = new HashMap<>();
        setAttr(attrs, "status", "ACTIVE");
        setAttr(attrs, "phone_number",  request.getPhoneNumber());
        setAttr(attrs, "gender",        request.getGender());
        setAttr(attrs, "date_of_birth", request.getDateOfBirth());
        setAttr(attrs, "avatar_url",    request.getAvatarUrl());
        // Student
        setAttr(attrs, "student_code",  request.getStudentCode());
        setAttr(attrs, "department",    request.getDepartment());
        setAttr(attrs, "batch",         request.getBatch());
        setAttr(attrs, "program",       request.getProgram());
        setAttr(attrs, "class_code",    request.getClassCode());
        // Lecturer
        setAttr(attrs, "staff_code",    request.getStaffCode());
        setAttr(attrs, "title",         request.getTitle());
        ur.setAttributes(attrs);

        Response response = realm().users().create(ur);
        if (response.getStatus() != 201) {
            throw new BadRequestException("Failed to create user in Keycloak: HTTP " + response.getStatus());
        }

        // Extract created user id from Location header
        String location = response.getHeaderString("Location");
        String newUserId = location.substring(location.lastIndexOf('/') + 1);

        // Assign realm role
        if (request.getRole() != null && !request.getRole().isBlank()) {
            assignRole(newUserId, request.getRole());
        }

        return getUser(newUserId);
    }

    public UserProfileResponse updateUser(String userId, UpdateUserRequest request) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation ur = userResource.toRepresentation();

        if (request.getFirstName() != null) ur.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) ur.setLastName(request.getLastName());
        if (request.getEmail()     != null) ur.setEmail(request.getEmail());

        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes())
                .orElse(new HashMap<>());
        setAttr(attrs, "phone_number",  request.getPhoneNumber());
        setAttr(attrs, "gender",        request.getGender());
        setAttr(attrs, "date_of_birth", request.getDateOfBirth());
        setAttr(attrs, "avatar_url",    request.getAvatarUrl());
        setAttr(attrs, "student_code",  request.getStudentCode());
        setAttr(attrs, "department",    request.getDepartment());
        setAttr(attrs, "batch",         request.getBatch());
        setAttr(attrs, "program",       request.getProgram());
        setAttr(attrs, "class_code",    request.getClassCode());
        setAttr(attrs, "staff_code",    request.getStaffCode());
        setAttr(attrs, "title",         request.getTitle());
        ur.setAttributes(attrs);

        userResource.update(ur);
        return getUser(userId);
    }

    /** Soft-delete: disables user and sets status=INACTIVE attribute. */
    public void deleteUser(String userId) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation ur = userResource.toRepresentation();
        ur.setEnabled(false);
        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes())
                .orElse(new HashMap<>());
        attrs.put("status", List.of("INACTIVE"));
        ur.setAttributes(attrs);
        userResource.update(ur);
    }

    public void resetPassword(String userId, ResetPasswordRequest request) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.getNewPassword());
        credential.setTemporary(request.isForceChange());
        realm().users().get(userId).resetPassword(credential);

        if (request.isForceChange()) {
            UserRepresentation ur = realm().users().get(userId).toRepresentation();
            List<String> actions = new ArrayList<>(
                    Optional.ofNullable(ur.getRequiredActions()).orElse(List.of()));
            if (!actions.contains("UPDATE_PASSWORD")) actions.add("UPDATE_PASSWORD");
            ur.setRequiredActions(actions);
            realm().users().get(userId).update(ur);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private void assignRole(String userId, String roleName) {
        RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
        realm().users().get(userId).roles().realmLevel().add(List.of(role));
    }

    private List<String> getRolesForUser(String userId) {
        try {
            return realm().users().get(userId).roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> name.startsWith("ROLE_"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean hasRole(String userId, String roleName) {
        return getRolesForUser(userId).contains(roleName);
    }

    private String getAttr(Map<String, List<String>> attrs, String key) {
        if (attrs == null) return null;
        List<String> values = attrs.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private void setAttr(Map<String, List<String>> attrs, String key, String value) {
        if (value != null) attrs.put(key, List.of(value));
    }

    public UserProfileResponse toProfileResponse(UserRepresentation ur, List<String> roles) {
        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes()).orElse(Map.of());
        return UserProfileResponse.builder()
                .id(ur.getId())
                .username(ur.getUsername())
                .email(ur.getEmail())
                .firstName(ur.getFirstName())
                .lastName(ur.getLastName())
                .enabled(Boolean.TRUE.equals(ur.isEnabled()))
                .roles(roles)
                .status(getAttr(attrs, "status"))
                .phoneNumber(getAttr(attrs, "phone_number"))
                .gender(getAttr(attrs, "gender"))
                .dateOfBirth(getAttr(attrs, "date_of_birth"))
                .avatarUrl(getAttr(attrs, "avatar_url"))
                .studentCode(getAttr(attrs, "student_code"))
                .department(getAttr(attrs, "department"))
                .batch(getAttr(attrs, "batch"))
                .program(getAttr(attrs, "program"))
                .classCode(getAttr(attrs, "class_code"))
                .staffCode(getAttr(attrs, "staff_code"))
                .title(getAttr(attrs, "title"))
                .build();
    }

    private UserSummaryResponse toSummaryResponse(UserRepresentation ur, List<String> roles) {
        Map<String, List<String>> attrs = Optional.ofNullable(ur.getAttributes()).orElse(Map.of());
        return UserSummaryResponse.builder()
                .id(ur.getId())
                .username(ur.getUsername())
                .email(ur.getEmail())
                .firstName(ur.getFirstName())
                .lastName(ur.getLastName())
                .enabled(Boolean.TRUE.equals(ur.isEnabled()))
                .roles(roles)
                .status(getAttr(attrs, "status"))
                .studentCode(getAttr(attrs, "student_code"))
                .staffCode(getAttr(attrs, "staff_code"))
                .build();
    }
}
```

- [ ] **Step 4: Create UserSummaryResponse**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/UserSummaryResponse.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSummaryResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private List<String> roles;
    private String status;
    private String studentCode;
    private String staffCode;
}
```

- [ ] **Step 5: Create UserProfileController**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/UserProfileController.java`
```java
package vn.edu.ptit.web_grading_system.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.user_service.dto.request.UpdateProfileRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserProfileResponse;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.user_service.security.SecurityUtils;
import vn.edu.ptit.web_grading_system.user_service.service.UserAdminService;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserAdminService userAdminService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        var principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Not authenticated"));
        UserProfileResponse profile = userAdminService.getUser(principal.userId().toString());
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        var principal = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Not authenticated"));
        UserProfileResponse profile = userAdminService.updateUserProfile(
                principal.userId().toString(), request);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd user-service && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add user-service/
git commit -m "feat(user-service): add user profile endpoint (GET/PUT /users/me)"
```

---

### Task 6: Admin CRUD Controller + Request DTOs

**Files:**
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/CreateUserRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/UpdateUserRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/ResetPasswordRequest.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/AdminUserController.java`

**Interfaces:**
- Consumes: `UserAdminService` from Task 5
- Produces: REST endpoints at `/api/v1/admin/users/**` protected by `@PreAuthorize("hasRole('ADMIN')")`

---

- [ ] **Step 1: Create request DTOs**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/CreateUserRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Password is required")
    private String password;

    private boolean forcePasswordChange = true;

    /** Role to assign: ROLE_STUDENT, ROLE_LECTURER, or ROLE_ADMIN */
    @NotBlank(message = "Role is required")
    @Pattern(regexp = "ROLE_STUDENT|ROLE_LECTURER|ROLE_ADMIN", message = "Role must be ROLE_STUDENT, ROLE_LECTURER, or ROLE_ADMIN")
    private String role;

    // Shared optional
    private String phoneNumber;
    private String gender;
    private String dateOfBirth;
    private String avatarUrl;

    // Student
    private String studentCode;
    private String department;
    private String batch;
    private String program;
    private String classCode;

    // Lecturer
    private String staffCode;
    private String title;
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/UpdateUserRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateUserRequest {
    private String firstName;
    private String lastName;
    @Email(message = "Email must be valid")
    private String email;
    private String phoneNumber;
    private String gender;
    private String dateOfBirth;
    private String avatarUrl;
    private String studentCode;
    private String department;
    private String batch;
    private String program;
    private String classCode;
    private String staffCode;
    private String title;
}
```

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/request/ResetPasswordRequest.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String newPassword;
    private boolean forceChange = true;
}
```

- [ ] **Step 2: Create AdminUserController**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/AdminUserController.java`
```java
package vn.edu.ptit.web_grading_system.user_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.user_service.dto.request.*;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserProfileResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.UserSummaryResponse;
import vn.edu.ptit.web_grading_system.user_service.service.UserAdminService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<UserSummaryResponse> users = userAdminService.listUsers(role, search, page, size);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        UserProfileResponse created = userAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(@PathVariable String id) {
        UserProfileResponse user = userAdminService.getUser(id);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserProfileResponse updated = userAdminService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userAdminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd user-service && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add user-service/
git commit -m "feat(user-service): add admin CRUD controller and request DTOs"
```

---

### Task 7: Import Feature (CSV/Excel)

**Files:**
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/ImportResultResponse.java`
- Create: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/service/ImportService.java`
- Modify: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/controller/AdminUserController.java` (add import endpoint)

**Interfaces:**
- Consumes: `UserAdminService.createUser(CreateUserRequest) → UserProfileResponse`
- Produces:
  - `ImportService.importUsers(MultipartFile file, String role, String passwordMode, String defaultPassword, boolean forcePasswordChange) → ImportResultResponse`
  - `ImportResultResponse(int total, int success, int failed, List<ImportError> errors)`
  - `ImportError(int row, String reason)`
  - `passwordMode` values: `"STUDENT_CODE"` or `"CUSTOM"`

---

- [ ] **Step 1: Create ImportResultResponse**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/dto/response/ImportResultResponse.java`
```java
package vn.edu.ptit.web_grading_system.user_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportResultResponse {
    private int total;
    private int success;
    private int failed;
    private List<ImportError> errors;

    @Data
    @Builder
    public static class ImportError {
        private int row;
        private String reason;
    }
}
```

- [ ] **Step 2: Create ImportService**

File: `user-service/src/main/java/vn/edu/ptit/web_grading_system/user_service/service/ImportService.java`
```java
package vn.edu.ptit.web_grading_system.user_service.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.web_grading_system.user_service.dto.request.CreateUserRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ImportResultResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ImportResultResponse.ImportError;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;

import java.io.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final UserAdminService userAdminService;

    /**
     * Imports users from a CSV or Excel file.
     *
     * @param file               uploaded CSV (.csv) or Excel (.xlsx) file
     * @param role               Keycloak role to assign: ROLE_STUDENT or ROLE_LECTURER
     * @param passwordMode       "STUDENT_CODE" or "CUSTOM"
     * @param defaultPassword    used when passwordMode=CUSTOM; ignored otherwise
     * @param forcePasswordChange whether to set Keycloak UPDATE_PASSWORD required action
     * @return ImportResultResponse with counts and per-row errors
     */
    public ImportResultResponse importUsers(MultipartFile file, String role, String passwordMode,
                                            String defaultPassword, boolean forcePasswordChange) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        List<Map<String, String>> rows;
        try {
            if (filename.endsWith(".csv")) {
                rows = parseCsv(file.getInputStream());
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                rows = parseExcel(file.getInputStream());
            } else {
                throw new BadRequestException("Unsupported file format. Use .csv or .xlsx");
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to read import file: " + e.getMessage());
        }

        int success = 0;
        List<ImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2; // 1-indexed, row 1 is header
            Map<String, String> row = rows.get(i);
            try {
                CreateUserRequest request = buildCreateUserRequest(row, role, passwordMode,
                        defaultPassword, forcePasswordChange);
                userAdminService.createUser(request);
                success++;
            } catch (Exception e) {
                log.warn("Import row {} failed: {}", rowNum, e.getMessage());
                errors.add(ImportError.builder().row(rowNum).reason(e.getMessage()).build());
            }
        }

        return ImportResultResponse.builder()
                .total(rows.size())
                .success(success)
                .failed(errors.size())
                .errors(errors)
                .build();
    }

    /** Parses CSV, first row is header. Returns list of maps {columnName → value}. */
    private List<Map<String, String>> parseCsv(InputStream inputStream) throws IOException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) return List.of();
            String[] headers = allRows.get(0);
            List<Map<String, String>> result = new ArrayList<>();
            for (int i = 1; i < allRows.size(); i++) {
                String[] values = allRows.get(i);
                Map<String, String> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    row.put(headers[j].trim(), j < values.length ? values[j].trim() : "");
                }
                result.add(row);
            }
            return result;
        } catch (CsvException e) {
            throw new IOException("CSV parse error: " + e.getMessage());
        }
    }

    /** Parses first sheet of an Excel file. First row is header. */
    private List<Map<String, String>> parseExcel(InputStream inputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return List.of();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return List.of();

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue().trim());
            }

            List<Map<String, String>> result = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    map.put(headers.get(j), cell == null ? "" : getCellValue(cell));
                }
                result.add(map);
            }
            return result;
        }
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private CreateUserRequest buildCreateUserRequest(Map<String, String> row, String role,
                                                      String passwordMode, String defaultPassword,
                                                      boolean forcePasswordChange) {
        String studentCode = row.get("studentCode");
        String username    = row.get("username");
        String email       = row.get("email");

        if (isBlank(username)) throw new BadRequestException("username is required");
        if (isBlank(email))    throw new BadRequestException("email is required");

        String password;
        boolean force = forcePasswordChange;
        if ("STUDENT_CODE".equals(passwordMode)) {
            if (isBlank(studentCode)) {
                throw new BadRequestException("studentCode is required when passwordMode=STUDENT_CODE");
            }
            password = studentCode;
            force = true; // always force when using student code
        } else if ("CUSTOM".equals(passwordMode)) {
            if (isBlank(defaultPassword)) {
                throw new BadRequestException("defaultPassword is required when passwordMode=CUSTOM");
            }
            password = defaultPassword;
        } else {
            throw new BadRequestException("passwordMode must be STUDENT_CODE or CUSTOM");
        }

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFirstName(nvl(row.get("firstName")));
        request.setLastName(nvl(row.get("lastName")));
        request.setPassword(password);
        request.setForcePasswordChange(force);
        request.setRole(role);
        // Student fields
        request.setStudentCode(row.get("studentCode"));
        request.setDepartment(row.get("department"));
        request.setBatch(row.get("batch"));
        request.setProgram(row.get("program"));
        request.setClassCode(row.get("classCode"));
        // Lecturer fields
        request.setStaffCode(row.get("staffCode"));
        request.setTitle(row.get("title"));
        // Shared
        request.setPhoneNumber(row.get("phone"));
        request.setGender(row.get("gender"));
        return request;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String nvl(String s)      { return s != null ? s : ""; }
}
```

- [ ] **Step 3: Add import endpoint to AdminUserController**

In `AdminUserController.java`, add this import at the top of the imports:
```java
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ImportResultResponse;
import vn.edu.ptit.web_grading_system.user_service.service.ImportService;
```

Add `ImportService` field to the class:
```java
    private final ImportService importService;
```

Add endpoint method to the class body (before the closing `}`):
```java
    /**
     * Import users from a CSV or Excel file.
     * Form params:
     *   file            — multipart file (.csv or .xlsx)
     *   role            — ROLE_STUDENT or ROLE_LECTURER
     *   passwordMode    — STUDENT_CODE | CUSTOM
     *   defaultPassword — (required when passwordMode=CUSTOM)
     *   forcePasswordChange — true | false (default true)
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ImportResultResponse>> importUsers(
            @RequestParam("file") MultipartFile file,
            @RequestParam("role") String role,
            @RequestParam("passwordMode") String passwordMode,
            @RequestParam(value = "defaultPassword", required = false) String defaultPassword,
            @RequestParam(value = "forcePasswordChange", defaultValue = "true") boolean forcePasswordChange) {
        ImportResultResponse result = importService.importUsers(file, role, passwordMode,
                defaultPassword, forcePasswordChange);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
```

- [ ] **Step 4: Verify compilation**

```bash
cd user-service && ./mvnw compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Run all tests**

```bash
cd user-service && ./mvnw test
```
Expected: BUILD SUCCESS (no test failures; scaffold tests pass)

- [ ] **Step 6: Commit**

```bash
git add user-service/
git commit -m "feat(user-service): add CSV/Excel import with configurable password modes"
```

---

### Task 8: Docker Compose & Environment Integration

**Files:**
- Modify: `docker-compose.yml` (add user-service service)
- Modify: `.env` (add USER_SERVICE_URI, KEYCLOAK_ADMIN_CLIENT_SECRET)
- Modify: `.env.dev` (same)

**Interfaces:**
- Produces: `user-service` reachable at port 8085 in docker-compose network; all env vars wired

---

- [ ] **Step 1: Add user-service to docker-compose.yml**

In `docker-compose.yml`, add after the `api-gateway` service block (before `course-service`):
```yaml
  # ─── User Service ─────────────────────────────────────────────────
  user-service:
    build:
      context: ./user-service
      dockerfile: Dockerfile
    container_name: wgs-user-service
    restart: unless-stopped
    ports:
      - "8085:8085"
    environment:
      GATEWAY_TRUSTED_SECRET: ${GATEWAY_TRUSTED_SECRET}
      KEYCLOAK_SERVER_URL: http://keycloak:8080
      KEYCLOAK_REALM: ptit-wgs
      KEYCLOAK_ADMIN_CLIENT_ID: wgs-user-service
      KEYCLOAK_ADMIN_CLIENT_SECRET: ${KEYCLOAK_ADMIN_CLIENT_SECRET}
    depends_on:
      keycloak:
        condition: service_healthy
    networks:
      - wgs-network
```

- [ ] **Step 2: Add env vars to .env and .env.dev**

In `.env` and `.env.dev`, add:
```env
USER_SERVICE_URI=http://user-service:8085
KEYCLOAK_ADMIN_CLIENT_SECRET=wgs-user-service-secret
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml .env .env.dev
git commit -m "feat(infra): add user-service to docker-compose and env files"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Admin manages LECTURER + STUDENT — Tasks 6, 7
- ✅ Admin CRUD (create/edit/delete) — Task 6 (`createUser`, `updateUser`, `deleteUser`)
- ✅ Import CSV/Excel — Task 7 (`ImportService`, `/import` endpoint)
- ✅ Password mode STUDENT_CODE | CUSTOM — Task 7 (`buildCreateUserRequest`)
- ✅ Login with access+refresh token — Task 4 (`AuthService.login`)
- ✅ First-login force password change (Keycloak `UPDATE_PASSWORD`) — Task 4 (`hasUpdatePasswordAction`, `login` returns `requiresPasswordChange`)
- ✅ Change password endpoint — Task 4 (`AuthService.changePassword`, `AuthController.changePassword`)
- ✅ Keycloak user attributes (all fields from spec) — Task 5 (`UserAdminService`, `UserProfileResponse`)
- ✅ ROLE_ADMIN, ROLE_LECTURER, ROLE_STUDENT — Task 2 (realm JSON), Task 6 (`@PreAuthorize("hasRole('ADMIN')")`)
- ✅ Gateway public auth paths — Task 1 (`PUBLIC_PATHS`)
- ✅ ROLE_ADMIN forwarded in X-User-Roles — Task 1 (`allowed-roles` update)
- ✅ Docker integration — Task 8

**Type consistency:**
- `UserAdminService.createUser(CreateUserRequest) → UserProfileResponse` — used in Tasks 5, 6, 7 ✅
- `UserAdminService.getUser(String userId) → UserProfileResponse` — used in Tasks 5, 6 ✅
- `UserPrincipal(UUID userId, String email, Collection<? extends GrantedAuthority>)` — used in Tasks 3, 4, 5 ✅
- `ApiResponse<T>(statusCode, message, data)` — consistent across all controllers ✅
- `LoginResponse(accessToken, refreshToken, tokenType, expiresIn, requiresPasswordChange?)` — Task 4 ✅

**No placeholders:** All code blocks are complete. ✅
