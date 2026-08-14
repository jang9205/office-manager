package com.example.office_manager.security;

import com.example.office_manager.mapper.OfficeMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final OfficeMapper officeMapper;

    public LoginSuccessHandler(OfficeMapper officeMapper) {
        this.officeMapper = officeMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OfficePrincipal principal = (OfficePrincipal) authentication.getPrincipal();
        String ip = clientIp(request);
        officeMapper.recordLoginSuccess(principal.accountId(), ip);
        officeMapper.insertLoginHistory(principal.accountId(), principal.getUsername(), true,
                null, ip, request.getHeader("User-Agent"));
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
