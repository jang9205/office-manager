package com.example.office_manager.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public final class OfficePrincipal implements UserDetails {

    private final Long accountId;
    private final Long employeeId;
    private final Long companyId;
    private final Long departmentId;
    private final String employeeName;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public OfficePrincipal(Long accountId, Long employeeId, Long companyId, Long departmentId,
                           String employeeName, String username, String password,
                           boolean enabled, boolean accountNonLocked,
                           Collection<? extends GrantedAuthority> authorities) {
        this.accountId = accountId;
        this.employeeId = employeeId;
        this.companyId = companyId;
        this.departmentId = departmentId;
        this.employeeName = employeeName;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.authorities = authorities;
    }

    public Long accountId() {
        return accountId;
    }

    public Long employeeId() {
        return employeeId;
    }

    public Long companyId() {
        return companyId;
    }

    public Long departmentId() {
        return departmentId;
    }

    public String employeeName() {
        return employeeName;
    }

    public boolean hasRole(String role) {
        String expected = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return authorities.stream().anyMatch(authority -> authority.getAuthority().equals(expected));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
