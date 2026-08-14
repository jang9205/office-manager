package com.example.office_manager.web;

import com.example.office_manager.security.OfficePrincipal;
import com.example.office_manager.service.OfficeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class PageController {

    private final OfficeService officeService;

    public PageController(OfficeService officeService) {
        this.officeService = officeService;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OfficePrincipal principal, Model model) {
        model.addAllAttributes(officeService.dashboard(principal));
        model.addAttribute("departmentStats", officeService.departments(principal.companyId()));
        return "dashboard";
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE:READ_ALL','EMPLOYEE:READ_TEAM')")
    public String employees(@AuthenticationPrincipal OfficePrincipal principal,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Long departmentId,
                            @RequestParam(required = false) Long positionId,
                            @RequestParam(defaultValue = "1") int page,
                            Model model) {
        model.addAttribute("employees", officeService.employees(principal, keyword, departmentId,
                positionId, page, 10));
        model.addAttribute("departments", officeService.departments(principal.companyId()));
        model.addAttribute("positions", officeService.positions(principal.companyId()));
        model.addAttribute("keyword", keyword);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("positionId", positionId);
        return "employees";
    }

    @GetMapping("/organization")
    public String organization(@AuthenticationPrincipal OfficePrincipal principal,
                               @RequestParam(required = false) Long departmentId,
                               @RequestParam(required = false) Long positionId,
                               @RequestParam(defaultValue = "1") int page,
                               Model model) {
        var departments = officeService.departments(principal.companyId());
        var positions = officeService.positions(principal.companyId());
        model.addAttribute("departments", departments);
        model.addAttribute("positions", positions);
        model.addAttribute("employeeOptions", officeService.employeeOptions(principal.companyId()));
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("positionId", positionId);
        if (departmentId != null || positionId != null) {
            model.addAttribute("organizationEmployees",
                    officeService.organizationEmployees(principal, departmentId, positionId, page, 10));
            model.addAttribute("selectedOrganizationName",
                    selectedOrganizationName(departments, positions, departmentId, positionId));
        }
        return "organization";
    }

    private String selectedOrganizationName(List<Map<String, Object>> departments,
                                            List<Map<String, Object>> positions,
                                            Long departmentId, Long positionId) {
        if (departmentId != null) {
            return departments.stream()
                    .filter(item -> departmentId.equals(((Number) item.get("departmentId")).longValue()))
                    .map(item -> String.valueOf(item.get("departmentName")))
                    .findFirst().orElse("선택한 부서");
        }
        return positions.stream()
                .filter(item -> positionId.equals(((Number) item.get("positionId")).longValue()))
                .map(item -> String.valueOf(item.get("positionName")))
                .findFirst().orElse("선택한 직급");
    }

    @GetMapping("/leaves")
    public String leaves(@AuthenticationPrincipal OfficePrincipal principal,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        model.addAttribute("balance", officeService.leaveBalance(principal));
        model.addAttribute("leaveTypes", officeService.leaveTypes(principal.companyId()));
        model.addAttribute("leaveRequests", officeService.leaveRequests(principal, status, page, 10));
        model.addAttribute("status", status);
        return "leaves";
    }

    @GetMapping("/approvals")
    public String approvals(@AuthenticationPrincipal OfficePrincipal principal, Model model) {
        model.addAttribute("tasks", officeService.approvalTasks(principal));
        model.addAttribute("documents", officeService.approvalDocuments(principal));
        return "approvals";
    }

    @GetMapping("/approvals/{documentId}")
    public String approvalDetail(@AuthenticationPrincipal OfficePrincipal principal,
                                 @PathVariable Long documentId, Model model) {
        var document = officeService.approvalDocument(principal, documentId);
        @SuppressWarnings("unchecked")
        var lines = (java.util.List<java.util.Map<String, Object>>) document.get("lines");
        boolean canApprove = lines.stream().anyMatch(line ->
                "PENDING".equals(String.valueOf(line.get("status")))
                        && ((Number) line.get("approverEmployeeId")).longValue() == principal.employeeId());
        model.addAttribute("document", document);
        model.addAttribute("canApprove", canApprove);
        return "approval-detail";
    }

    @GetMapping("/attendance")
    public String attendance(@AuthenticationPrincipal OfficePrincipal principal,
                             @RequestParam(defaultValue = "1") int page,
                             Model model) {
        model.addAttribute("today", officeService.todayAttendance(principal));
        model.addAttribute("history", officeService.attendanceHistory(principal, page, 15));
        return "attendance";
    }

    @GetMapping("/notices")
    public String notices(@AuthenticationPrincipal OfficePrincipal principal,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          Model model) {
        model.addAttribute("notices", officeService.notices(principal, keyword, page, 10));
        model.addAttribute("categories", officeService.noticeCategories(principal.companyId()));
        model.addAttribute("keyword", keyword);
        return "notices";
    }

    @GetMapping("/notices/{noticeId}")
    public String noticeDetail(@AuthenticationPrincipal OfficePrincipal principal,
                               @PathVariable Long noticeId, Model model) {
        model.addAttribute("notice", officeService.notice(principal, noticeId));
        model.addAttribute("attachments", officeService.noticeAttachments(noticeId));
        model.addAttribute("categories", officeService.noticeCategories(principal.companyId()));
        return "notice-detail";
    }

    @GetMapping("/resources")
    public String resources(@AuthenticationPrincipal OfficePrincipal principal,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") int page,
                            Model model) {
        model.addAttribute("resources", officeService.resources(principal, keyword, page, 12));
        model.addAttribute("keyword", keyword);
        return "resources";
    }

    @GetMapping("/calendar")
    public String calendar(@AuthenticationPrincipal OfficePrincipal principal,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
                           Model model) {
        LocalDate target = month == null ? LocalDate.now().withDayOfMonth(1) : month.withDayOfMonth(1);
        LocalDateTime from = target.atStartOfDay();
        var events = officeService.calendarEvents(principal, from.minusDays(7), from.plusMonths(1).plusDays(7));
        LocalDate gridStart = target.minusDays(target.getDayOfWeek().getValue() % 7L);
        List<CalendarDay> calendarDays = new ArrayList<>();
        for (int index = 0; index < 42; index++) {
            LocalDate date = gridStart.plusDays(index);
            List<Map<String, Object>> dayEvents = events.stream().filter(event -> {
                LocalDate starts = toDateTime(event.get("startsAt")).toLocalDate();
                LocalDate ends = toDateTime(event.get("endsAt")).toLocalDate();
                Object allDayValue = event.get("allDay");
                boolean allDay = Boolean.TRUE.equals(allDayValue)
                        || (allDayValue instanceof Number number && number.intValue() == 1)
                        || "1".equals(String.valueOf(allDayValue));
                if (allDay && ends.isAfter(starts)) {
                    ends = ends.minusDays(1);
                }
                return !date.isBefore(starts) && !date.isAfter(ends);
            }).toList();
            calendarDays.add(new CalendarDay(date, date.getMonth() == target.getMonth(),
                    date.equals(LocalDate.now()), dayEvents));
        }
        model.addAttribute("calendarDays", calendarDays);
        model.addAttribute("targetMonth", target);
        model.addAttribute("previousMonth", target.minusMonths(1));
        model.addAttribute("nextMonth", target.plusMonths(1));
        return "calendar";
    }

    @GetMapping("/messages")
    public String messages(@AuthenticationPrincipal OfficePrincipal principal, Model model) {
        model.addAttribute("inbox", officeService.inbox(principal));
        model.addAttribute("sent", officeService.sentMessages(principal));
        model.addAttribute("recipients", officeService.accountOptions(principal));
        return "messages";
    }

    @GetMapping("/search")
    public String search(@AuthenticationPrincipal OfficePrincipal principal,
                         @RequestParam(required = false) String q, Model model) {
        model.addAttribute("query", q);
        model.addAttribute("results", officeService.search(principal, q));
        return "search";
    }

    @GetMapping("/my-page")
    public String myPage(@AuthenticationPrincipal OfficePrincipal principal, Model model) {
        model.addAttribute("profile", officeService.myProfile(principal));
        model.addAttribute("recentLogins", officeService.recentLogins(principal));
        return "my-page";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    private LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    public record CalendarDay(LocalDate date, boolean currentMonth, boolean today,
                              List<Map<String, Object>> events) {
    }
}
