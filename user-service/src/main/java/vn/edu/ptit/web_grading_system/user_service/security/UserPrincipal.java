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
