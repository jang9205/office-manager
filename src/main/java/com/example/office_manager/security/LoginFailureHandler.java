package com.example.office_manager.security;

import com.example.office_manager.mapper.OfficeMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final OfficeMapper officeMapper;

    public LoginFailureHandler(OfficeMapper officeMapper) {
        this.officeMapper = officeMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String loginId = request.getParameter("username");
        if (loginId != null && !loginId.isBlank()) {
            officeMapper.recordLoginFailure(loginId);
            officeMapper.insertLoginHistory(null, loginId, false,
                    exception.getClass().getSimpleName(), clientIp(request), request.getHeader("User-Agent"));
        }
        response.sendRedirect(request.getContextPath() + "/login?error");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
