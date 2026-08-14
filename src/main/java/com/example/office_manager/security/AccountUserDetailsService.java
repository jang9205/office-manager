package com.example.office_manager.security;

import com.example.office_manager.mapper.OfficeMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final OfficeMapper officeMapper;

    public AccountUserDetailsService(OfficeMapper officeMapper) {
        this.officeMapper = officeMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, Object> account = officeMapper.findAccountByLoginId(username);
        if (account == null) {
            throw new UsernameNotFoundException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String status = String.valueOf(account.get("accountStatus"));
        LocalDateTime lockedUntil = toLocalDateTime(account.get("lockedUntil"));
        boolean lockExpired = lockedUntil != null && lockedUntil.isBefore(LocalDateTime.now());
        boolean enabled = !"DISABLED".equals(status) && !"PENDING".equals(status);
        boolean nonLocked = !"LOCKED".equals(status) || lockExpired;

        var authorities = officeMapper.findAuthorities(number(account, "accountId")).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return new OfficePrincipal(
                number(account, "accountId"),
                number(account, "employeeId"),
                number(account, "companyId"),
                nullableNumber(account.get("departmentId")),
                String.valueOf(account.get("employeeName")),
                String.valueOf(account.get("loginId")),
                String.valueOf(account.get("passwordHash")),
                enabled,
                nonLocked,
                authorities
        );
    }

    private Long number(Map<String, Object> source, String key) {
        return ((Number) source.get(key)).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
