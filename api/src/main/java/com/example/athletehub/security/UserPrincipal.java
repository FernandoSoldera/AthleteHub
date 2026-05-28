package com.example.athletehub.security;

import com.example.athletehub.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal backed by our {@link User} entity. Username = email
 * (also used in the JWT subject after authentication). Authorities are
 * {@code ROLE_<role>} for every role on the user (we support multi-role).
 */
@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long id;
    private String email;
    private String passwordHash;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), authorities);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
