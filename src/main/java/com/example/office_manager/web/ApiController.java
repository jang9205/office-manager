package com.example.office_manager.web;

import com.example.office_manager.security.OfficePrincipal;
import com.example.office_manager.service.OfficeService;
import com.example.office_manager.web.dto.ApiDtos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final OfficeService officeService;

    public ApiController(OfficeService officeService) {
        this.officeService = officeService;
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE:READ_ALL','EMPLOYEE:READ_TEAM')")
    public Object employees(@AuthenticationPrincipal OfficePrincipal principal,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Long departmentId,
                            @RequestParam(required = false) Long positionId,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int size) {
        return officeService.employees(principal, keyword, departmentId, positionId, page, size);
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE:READ_ALL','EMPLOYEE:READ_TEAM','EMPLOYEE:READ_SELF')")
    public Object employee(@AuthenticationPrincipal OfficePrincipal principal,
                           @PathVariable Long employeeId) {
        return officeService.employee(principal, employeeId);
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('EMPLOYEE:CREATE')")
    public Map<String, Object> createEmployee(@AuthenticationPrincipal OfficePrincipal principal,
                                              @Valid @RequestBody ApiDtos.EmployeeRequest request) {
        return Map.of("message", "사원이 등록되었습니다.",
                "employeeId", officeService.createEmployee(principal, request));
    }

    @PutMapping("/employees/{employeeId}")
    @PreAuthorize("hasAuthority('EMPLOYEE:UPDATE')")
    public Map<String, Object> updateEmployee(@AuthenticationPrincipal OfficePrincipal principal,
                                              @PathVariable Long employeeId,
                                              @Valid @RequestBody ApiDtos.EmployeeRequest request) {
        officeService.updateEmployee(principal, employeeId, request);
        return Map.of("message", "사원 정보가 수정되었습니다.");
    }

    @DeleteMapping("/employees/{employeeId}")
    @PreAuthorize("hasAuthority('EMPLOYEE:DELETE')")
    public Map<String, Object> deleteEmployee(@AuthenticationPrincipal OfficePrincipal principal,
                                              @PathVariable Long employeeId) {
        officeService.deleteEmployee(principal, employeeId);
        return Map.of("message", "사원이 삭제되었습니다.");
    }

    @PostMapping("/admin/employees/{employeeId}/reset-password")
    @PreAuthorize("hasAuthority('ACCOUNT:MANAGE')")
    public Map<String, Object> resetEmployeePassword(@AuthenticationPrincipal OfficePrincipal principal,
                                                     @PathVariable Long employeeId,
                                                     @Valid @RequestBody ApiDtos.PasswordResetRequest request) {
        officeService.resetEmployeePassword(principal, employeeId, request);
        return Map.of("message", "임시 비밀번호로 초기화했습니다. 다음 로그인 후 변경하도록 안내해 주세요.");
    }

    @PostMapping("/admin/departments")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public Map<String, Object> createDepartment(@AuthenticationPrincipal OfficePrincipal principal,
                                                @Valid @RequestBody ApiDtos.DepartmentRequest request) {
        return Map.of("message", "부서가 등록되었습니다.",
                "departmentId", officeService.createDepartment(principal, request));
    }

    @PutMapping("/admin/departments/{departmentId}")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public Map<String, Object> updateDepartment(@AuthenticationPrincipal OfficePrincipal principal,
                                                @PathVariable Long departmentId,
                                                @Valid @RequestBody ApiDtos.DepartmentRequest request) {
        officeService.updateDepartment(principal, departmentId, request);
        return Map.of("message", "부서가 수정되었습니다.");
    }

    @DeleteMapping("/admin/departments/{departmentId}")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public Map<String, Object> deleteDepartment(@AuthenticationPrincipal OfficePrincipal principal,
                                                @PathVariable Long departmentId) {
        officeService.deleteDepartment(principal, departmentId);
        return Map.of("message", "부서가 삭제되었습니다.");
    }

    @PostMapping("/admin/positions")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public Map<String, Object> createPosition(@AuthenticationPrincipal OfficePrincipal principal,
                                              @Valid @RequestBody ApiDtos.PositionRequest request) {
        return Map.of("message", "직급이 등록되었습니다.",
                "positionId", officeService.createPosition(principal, request));
    }

    @PutMapping("/admin/positions/{positionId}")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public Map<String, Object> updatePosition(@AuthenticationPrincipal OfficePrincipal principal,
                                              @PathVariable Long positionId,
                                              @Valid @RequestBody ApiDtos.PositionRequest request) {
        officeService.updatePosition(principal, positionId, request);
        return Map.of("message", "직급이 수정되었습니다.");
    }

    @DeleteMapping("/admin/positions/{positionId}")
    @PreAuthorize("hasAuthority('ORGANIZATION:MANAGE')")
    public Map<String, Object> deletePosition(@AuthenticationPrincipal OfficePrincipal principal,
                                              @PathVariable Long positionId) {
        officeService.deletePosition(principal, positionId);
        return Map.of("message", "직급이 삭제되었습니다.");
    }

    @PostMapping("/leaves")
    @PreAuthorize("hasAuthority('LEAVE:REQUEST')")
    public Map<String, Object> requestLeave(@AuthenticationPrincipal OfficePrincipal principal,
                                            @Valid @RequestBody ApiDtos.LeaveRequest request) {
        return Map.of("message", "휴가 신청이 결재선에 등록되었습니다.",
                "leaveRequestId", officeService.requestLeave(principal, request));
    }

    @PostMapping("/approvals")
    @PreAuthorize("hasAuthority('APPROVAL:REQUEST')")
    public Map<String, Object> requestApproval(@AuthenticationPrincipal OfficePrincipal principal,
                                               @Valid @RequestBody ApiDtos.GenericApprovalRequest request) {
        return Map.of("message", "결재 요청이 등록되었습니다.",
                "documentId", officeService.requestGenericApproval(principal, request));
    }

    @PostMapping("/approvals/{documentId}/approve")
    @PreAuthorize("hasAuthority('APPROVAL:APPROVE')")
    public Map<String, Object> approve(@AuthenticationPrincipal OfficePrincipal principal,
                                       @PathVariable Long documentId,
                                       @RequestBody(required = false) ApiDtos.ApprovalActionRequest request) {
        officeService.approve(principal, documentId, request == null ? null : request.opinion());
        return Map.of("message", "결재를 승인했습니다.");
    }

    @PostMapping("/approvals/{documentId}/reject")
    @PreAuthorize("hasAuthority('APPROVAL:APPROVE')")
    public Map<String, Object> reject(@AuthenticationPrincipal OfficePrincipal principal,
                                      @PathVariable Long documentId,
                                      @Valid @RequestBody ApiDtos.ApprovalActionRequest request) {
        officeService.reject(principal, documentId, request.opinion());
        return Map.of("message", "결재를 반려했습니다.");
    }

    @PostMapping("/attendance/clock-in")
    @PreAuthorize("hasAuthority('ATTENDANCE:CLOCK')")
    public Object clockIn(@AuthenticationPrincipal OfficePrincipal principal) {
        return Map.of("message", "출근 처리되었습니다.", "attendance", officeService.clockIn(principal));
    }

    @PostMapping("/attendance/clock-out")
    @PreAuthorize("hasAuthority('ATTENDANCE:CLOCK')")
    public Object clockOut(@AuthenticationPrincipal OfficePrincipal principal) {
        return Map.of("message", "퇴근 처리되었습니다.", "attendance", officeService.clockOut(principal));
    }

    @PostMapping("/admin/notices")
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    public Map<String, Object> createNotice(@AuthenticationPrincipal OfficePrincipal principal,
                                            @Valid @RequestBody ApiDtos.NoticeRequest request) {
        return Map.of("message", "공지사항이 등록되었습니다.",
                "noticeId", officeService.createNotice(principal, request));
    }

    @PutMapping("/admin/notices/{noticeId}")
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    public Map<String, Object> updateNotice(@AuthenticationPrincipal OfficePrincipal principal,
                                            @PathVariable Long noticeId,
                                            @Valid @RequestBody ApiDtos.NoticeRequest request) {
        officeService.updateNotice(principal, noticeId, request);
        return Map.of("message", "공지사항이 수정되었습니다.");
    }

    @DeleteMapping("/admin/notices/{noticeId}")
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    public Map<String, Object> deleteNotice(@AuthenticationPrincipal OfficePrincipal principal,
                                            @PathVariable Long noticeId) {
        officeService.deleteNotice(principal, noticeId);
        return Map.of("message", "공지사항이 삭제되었습니다.");
    }

    @PostMapping("/events")
    @PreAuthorize("hasAuthority('SCHEDULE:MANAGE')")
    public Map<String, Object> createEvent(@AuthenticationPrincipal OfficePrincipal principal,
                                           @Valid @RequestBody ApiDtos.EventRequest request) {
        return Map.of("message", "일정이 등록되었습니다.",
                "eventId", officeService.createEvent(principal, request));
    }

    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('SCHEDULE:MANAGE')")
    public Map<String, Object> deleteEvent(@AuthenticationPrincipal OfficePrincipal principal,
                                           @PathVariable Long eventId) {
        officeService.deleteEvent(principal, eventId);
        return Map.of("message", "일정이 삭제되었습니다.");
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('MESSAGE:USE')")
    public Map<String, Object> sendMessage(@AuthenticationPrincipal OfficePrincipal principal,
                                           @Valid @RequestBody ApiDtos.MessageRequest request) {
        return Map.of("message", "쪽지를 보냈습니다.",
                "messageId", officeService.sendMessage(principal, request));
    }

    @PostMapping("/messages/{messageId}/read")
    @PreAuthorize("hasAuthority('MESSAGE:USE')")
    public Map<String, Object> readMessage(@AuthenticationPrincipal OfficePrincipal principal,
                                           @PathVariable Long messageId) {
        officeService.readMessage(principal, messageId);
        return Map.of("message", "읽음 처리했습니다.");
    }

    @PostMapping("/notifications/{notificationId}/read")
    public Map<String, Object> readNotification(@AuthenticationPrincipal OfficePrincipal principal,
                                                @PathVariable Long notificationId) {
        officeService.readNotification(principal, notificationId);
        return Map.of("message", "알림을 읽음 처리했습니다.");
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Object> readAllNotifications(@AuthenticationPrincipal OfficePrincipal principal) {
        officeService.readAllNotifications(principal);
        return Map.of("message", "모든 알림을 읽음 처리했습니다.");
    }

    @PostMapping("/my-page/password")
    public Map<String, Object> changePassword(@AuthenticationPrincipal OfficePrincipal principal,
                                              @Valid @RequestBody ApiDtos.PasswordChangeRequest request) {
        officeService.changePassword(principal, request);
        return Map.of("message", "비밀번호가 변경되었습니다.");
    }
}
