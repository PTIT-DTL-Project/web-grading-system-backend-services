package vn.edu.ptit.web_grading_system.submission_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filter that parses identity and role headers injected by the API Gateway
 * (X-User-Id, X-User-Email, X-User-Roles) and builds the Spring SecurityContext.
 */
@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String userIdStr = request.getHeader(HEADER_USER_ID);
        String email = request.getHeader(HEADER_USER_EMAIL);
        String rolesStr = request.getHeader(HEADER_USER_ROLES);

        if (StringUtils.hasText(userIdStr) && !"anonymous".equalsIgnoreCase(userIdStr.trim())) {
            try {
                UUID userId = UUID.fromString(userIdStr.trim());
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                if (StringUtils.hasText(rolesStr)) {
                    for (String role : rolesStr.split(",")) {
                        String trimmed = role.trim();
                        if (!trimmed.isEmpty()) {
                            if (!trimmed.startsWith("ROLE_")) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + trimmed));
                            } else {
                                authorities.add(new SimpleGrantedAuthority(trimmed));
                            }
                        }
                    }
                }

                UserPrincipal principal = new UserPrincipal(userId, email, authorities);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format for X-User-Id header: {}", userIdStr);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
