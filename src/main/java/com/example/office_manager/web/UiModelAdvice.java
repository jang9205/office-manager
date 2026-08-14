package com.example.office_manager.web;

import com.example.office_manager.security.OfficePrincipal;
import com.example.office_manager.service.OfficeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

@ControllerAdvice
public class UiModelAdvice {

    private final OfficeService officeService;

    public UiModelAdvice(OfficeService officeService) {
        this.officeService = officeService;
    }

    @ModelAttribute
    public void commonAttributes(@AuthenticationPrincipal OfficePrincipal principal,
                                 HttpServletRequest request,
                                 org.springframework.ui.Model model) {
        if (principal == null) {
            return;
        }
        model.addAttribute("currentUser", principal);
        model.addAttribute("isAdmin", principal.hasRole("ADMIN"));
        model.addAttribute("isManager", principal.hasRole("MANAGER"));
        model.addAttribute("unreadNotifications", officeService.unreadNotificationCount(principal));
        model.addAttribute("notifications", officeService.notifications(principal));
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("currentDate", LocalDate.now());
    }
}
