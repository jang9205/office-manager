package com.example.office_manager.service;

import com.example.office_manager.common.BusinessException;
import com.example.office_manager.common.PageResult;
import com.example.office_manager.mapper.OfficeMapper;
import com.example.office_manager.security.OfficePrincipal;
import com.example.office_manager.web.dto.ApiDtos;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class OfficeService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final OfficeMapper officeMapper;
    private final PasswordEncoder passwordEncoder;

    public OfficeService(OfficeMapper officeMapper, PasswordEncoder passwordEncoder) {
        this.officeMapper = officeMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> dashboard(OfficePrincipal principal) {
        Map<String, Object> result = new HashMap<>();
        result.put("stats", officeMapper.dashboardStats(principal.companyId(), principal.employeeId()));
        result.put("notices", officeMapper.dashboardNotices(principal.companyId()));
        result.put("events", officeMapper.dashboardEvents(principal.companyId(), principal.employeeId()));
        result.put("approvals", officeMapper.dashboardApprovals(principal.employeeId()));
        return result;
    }

    public List<Map<String, Object>> departments(Long companyId) {
        return officeMapper.listDepartments(companyId);
    }

    public List<Map<String, Object>> positions(Long companyId) {
        return officeMapper.listPositions(companyId);
    }

    public List<Map<String, Object>> leaveTypes(Long companyId) {
        return officeMapper.listLeaveTypes(companyId);
    }

    public List<Map<String, Object>> employeeOptions(Long companyId) {
        return officeMapper.listEmployeeOptions(companyId);
    }

    public List<Map<String, Object>> accountOptions(OfficePrincipal principal) {
        return officeMapper.listAccountOptions(principal.companyId(), principal.accountId());
    }

    public PageResult<Map<String, Object>> employees(OfficePrincipal principal, String keyword,
                                                     Long departmentId, Long positionId,
                                                     int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        Long scopedDepartmentId = principal.hasRole("ADMIN") ? departmentId : principal.departmentId();
        long total = officeMapper.countEmployees(principal.companyId(), clean(keyword),
                scopedDepartmentId, positionId);
        var rows = officeMapper.listEmployees(principal.companyId(), clean(keyword), scopedDepartmentId,
                positionId, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(rows, total, safePage, safeSize);
    }

    public PageResult<Map<String, Object>> organizationEmployees(OfficePrincipal principal,
                                                                  Long departmentId, Long positionId,
                                                                  int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        long total = officeMapper.countEmployees(principal.companyId(), null, departmentId, positionId);
        var rows = officeMapper.listEmployees(principal.companyId(), null, departmentId, positionId,
                (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(rows, total, safePage, safeSize);
    }

    public Map<String, Object> employee(OfficePrincipal principal, Long employeeId) {
        Map<String, Object> employee = officeMapper.findEmployee(principal.companyId(), employeeId);
        if (employee == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "사원 정보를 찾을 수 없습니다.");
        }
        Long targetDepartmentId = nullableNumber(employee.get("departmentId"));
        if (!principal.hasRole("ADMIN")
                && !Objects.equals(principal.employeeId(), employeeId)
                && !Objects.equals(principal.departmentId(), targetDepartmentId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 사원 정보에 접근할 수 없습니다.");
        }
        return employee;
    }

    @Transactional
    public Long createEmployee(OfficePrincipal actor, ApiDtos.EmployeeRequest request) {
        validateAccountFields(request.loginId(), request.initialPassword());
        String roleCode = request.roleCode() == null || request.roleCode().isBlank()
                ? "ROLE_EMPLOYEE" : request.roleCode();
        if (!List.of("ROLE_ADMIN", "ROLE_MANAGER", "ROLE_EMPLOYEE").contains(roleCode)) {
            throw new BusinessException("지원하지 않는 권한입니다.");
        }

        try {
            Map<String, Object> values = employeeValues(actor.companyId(), null, request);
            officeMapper.insertEmployee(values);
            Long employeeId = number(values, "employeeId");

            if (request.loginId() != null && !request.loginId().isBlank()) {
                Map<String, Object> account = new HashMap<>();
                account.put("employeeId", employeeId);
                account.put("loginId", request.loginId().trim());
                account.put("passwordHash", passwordEncoder.encode(request.initialPassword()));
                account.put("mustChangePassword", true);
                account.put("createdByAccountId", actor.accountId());
                officeMapper.insertAccount(account);
                Long roleId = officeMapper.findRoleIdByCode(roleCode);
                officeMapper.insertAccountRole(number(account, "accountId"), roleId);
            }

            LocalDate today = LocalDate.now();
            Long leavePolicyId = officeMapper.findActiveLeavePolicyId(actor.companyId(), today);
            if (leavePolicyId != null) {
                officeMapper.insertInitialLeaveBalance(employeeId, leavePolicyId,
                        Year.now().getValue(), initialLeaveDays(request.hireDate(), today));
            }
            Long workPolicyId = officeMapper.findActiveWorkPolicyId(actor.companyId(), today);
            if (workPolicyId != null) {
                officeMapper.insertWorkAssignment(employeeId, workPolicyId, request.hireDate());
            }
            audit(actor, "CREATE", "EMPLOYEE", employeeId, request.employeeName() + " 사원 등록");
            return employeeId;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("사번, 이메일 또는 로그인 아이디가 이미 사용 중입니다.");
        }
    }

    @Transactional
    public void updateEmployee(OfficePrincipal actor, Long employeeId, ApiDtos.EmployeeRequest request) {
        employee(actor, employeeId);
        try {
            officeMapper.updateEmployee(employeeValues(actor.companyId(), employeeId, request));
            audit(actor, "UPDATE", "EMPLOYEE", employeeId, request.employeeName() + " 사원 정보 수정");
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("사번 또는 이메일이 이미 사용 중입니다.");
        }
    }

    @Transactional
    public void deleteEmployee(OfficePrincipal actor, Long employeeId) {
        if (Objects.equals(actor.employeeId(), employeeId)) {
            throw new BusinessException("현재 로그인한 본인 계정은 삭제할 수 없습니다.");
        }
        Map<String, Object> employee = employee(actor, employeeId);
        officeMapper.softDeleteEmployee(actor.companyId(), employeeId, actor.accountId());
        audit(actor, "DELETE", "EMPLOYEE", employeeId, employee.get("employeeName") + " 사원 삭제");
    }

    @Transactional
    public Long createDepartment(OfficePrincipal actor, ApiDtos.DepartmentRequest request) {
        try {
            Map<String, Object> values = departmentValues(actor.companyId(), null, request);
            officeMapper.insertDepartment(values);
            Long id = number(values, "departmentId");
            audit(actor, "CREATE", "DEPARTMENT", id, request.departmentName() + " 부서 등록");
            return id;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("같은 부서 코드 또는 이름이 이미 존재합니다.");
        }
    }

    @Transactional
    public void updateDepartment(OfficePrincipal actor, Long departmentId,
                                 ApiDtos.DepartmentRequest request) {
        if (Objects.equals(departmentId, request.parentDepartmentId())) {
            throw new BusinessException("부서 자신을 상위 부서로 지정할 수 없습니다.");
        }
        Map<String, Object> values = departmentValues(actor.companyId(), departmentId, request);
        officeMapper.updateDepartment(values);
        audit(actor, "UPDATE", "DEPARTMENT", departmentId, request.departmentName() + " 부서 수정");
    }

    @Transactional
    public void deleteDepartment(OfficePrincipal actor, Long departmentId) {
        officeMapper.softDeleteDepartment(actor.companyId(), departmentId);
        audit(actor, "DELETE", "DEPARTMENT", departmentId, "부서 삭제");
    }

    @Transactional
    public Long createPosition(OfficePrincipal actor, ApiDtos.PositionRequest request) {
        try {
            Map<String, Object> values = positionValues(actor.companyId(), null, request);
            officeMapper.insertPosition(values);
            Long id = number(values, "positionId");
            audit(actor, "CREATE", "POSITION", id, request.positionName() + " 직급 등록");
            return id;
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("같은 직급 코드 또는 이름이 이미 존재합니다.");
        }
    }

    @Transactional
    public void updatePosition(OfficePrincipal actor, Long positionId, ApiDtos.PositionRequest request) {
        officeMapper.updatePosition(positionValues(actor.companyId(), positionId, request));
        audit(actor, "UPDATE", "POSITION", positionId, request.positionName() + " 직급 수정");
    }

    @Transactional
    public void deletePosition(OfficePrincipal actor, Long positionId) {
        officeMapper.softDeletePosition(actor.companyId(), positionId);
        audit(actor, "DELETE", "POSITION", positionId, "직급 삭제");
    }

    public Map<String, Object> leaveBalance(OfficePrincipal principal) {
        Map<String, Object> balance = officeMapper.findLeaveBalance(principal.employeeId(), Year.now().getValue());
        return balance == null ? Map.of("grantedDays", 0, "usedDays", 0,
                "pendingDays", 0, "remainingDays", 0, "availableDays", 0) : balance;
    }

    public PageResult<Map<String, Object>> leaveRequests(OfficePrincipal principal, String status,
                                                         int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        boolean admin = principal.hasRole("ADMIN");
        boolean manager = principal.hasRole("MANAGER");
        long total = officeMapper.countLeaveRequests(principal.companyId(), principal.employeeId(),
                principal.departmentId(), admin, manager, clean(status));
        var rows = officeMapper.listLeaveRequests(principal.companyId(), principal.employeeId(),
                principal.departmentId(), admin, manager, clean(status),
                (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(rows, total, safePage, safeSize);
    }

    @Transactional
    public Long requestLeave(OfficePrincipal actor, ApiDtos.LeaveRequest request) {
        validateLeavePeriod(request);
        Map<String, Object> leaveType = officeMapper.listLeaveTypes(actor.companyId()).stream()
                .filter(type -> Objects.equals(number(type, "leaveTypeId"), request.leaveTypeId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("휴가 종류를 찾을 수 없습니다."));

        double requestedDays = calculateLeaveDays(request.startDate(), request.endDate(), request.leaveUnit());
        boolean deducts = booleanValue(leaveType.get("deductsAnnualLeave"));
        Long leaveBalanceId = null;
        if (deducts) {
            if (request.startDate().getYear() != request.endDate().getYear()) {
                throw new BusinessException("연도를 넘기는 휴가는 연도별로 나누어 신청해 주세요.");
            }
            Map<String, Object> balance = officeMapper.lockLeaveBalance(actor.employeeId(),
                    request.startDate().getYear());
            if (balance == null) {
                throw new BusinessException("해당 연도의 연차 잔액이 없습니다.");
            }
            if (decimal(balance.get("availableDays")) < requestedDays) {
                throw new BusinessException("사용 가능한 연차가 부족합니다.");
            }
            leaveBalanceId = number(balance, "leaveBalanceId");
        }

        List<Long> approvers = resolveApprovers(actor);
        String documentNumber = documentNumber();
        String leaveTypeName = String.valueOf(leaveType.get("leaveTypeName"));
        Map<String, Object> document = new HashMap<>();
        document.put("companyId", actor.companyId());
        document.put("drafterEmployeeId", actor.employeeId());
        document.put("documentNumber", documentNumber);
        document.put("title", "[" + leaveTypeName + "] " + actor.employeeName() + " 휴가 신청");
        document.put("content", request.startDate() + " ~ " + request.endDate() + "\n" + request.reason());
        document.put("documentType", "LEAVE");
        document.put("businessType", "LEAVE_REQUEST");
        officeMapper.insertApprovalDocument(document);
        Long documentId = number(document, "documentId");
        insertApprovalLinesAndNotify(actor, documentId, approvers, String.valueOf(document.get("title")));

        Map<String, Object> leave = new HashMap<>();
        leave.put("employeeId", actor.employeeId());
        leave.put("leaveTypeId", request.leaveTypeId());
        leave.put("leaveBalanceId", leaveBalanceId);
        leave.put("documentId", documentId);
        leave.put("leaveUnit", request.leaveUnit());
        leave.put("startDate", request.startDate());
        leave.put("endDate", request.endDate());
        leave.put("requestedDays", requestedDays);
        leave.put("reason", request.reason());
        officeMapper.insertLeaveRequest(leave);
        Long leaveRequestId = number(leave, "leaveRequestId");
        officeMapper.updateApprovalBusinessId(documentId, leaveRequestId);
        if (leaveBalanceId != null) {
            officeMapper.addPendingLeave(leaveBalanceId, requestedDays);
        }
        approvalHistory(documentId, null, actor.employeeId(), "SUBMIT", "DRAFT", "PENDING", request.reason());
        audit(actor, "CREATE", "LEAVE_REQUEST", leaveRequestId, "휴가 신청");
        return leaveRequestId;
    }

    @Transactional
    public Long requestGenericApproval(OfficePrincipal actor, ApiDtos.GenericApprovalRequest request) {
        if (request.title() == null || request.title().isBlank()
                || request.content() == null || request.content().isBlank()) {
            throw new BusinessException("결재 제목과 내용을 입력해 주세요.");
        }
        List<Long> approvers = resolveApprovers(actor);
        Map<String, Object> document = new HashMap<>();
        document.put("companyId", actor.companyId());
        document.put("drafterEmployeeId", actor.employeeId());
        document.put("documentNumber", documentNumber());
        document.put("title", request.title().trim());
        document.put("content", request.content().trim());
        document.put("documentType", "GENERAL");
        document.put("businessType", null);
        officeMapper.insertApprovalDocument(document);
        Long documentId = number(document, "documentId");
        insertApprovalLinesAndNotify(actor, documentId, approvers, request.title());
        approvalHistory(documentId, null, actor.employeeId(), "SUBMIT", "DRAFT", "PENDING", null);
        audit(actor, "CREATE", "APPROVAL_DOCUMENT", documentId, "일반 결재 요청");
        return documentId;
    }

    public List<Map<String, Object>> approvalTasks(OfficePrincipal principal) {
        return officeMapper.listApprovalTasks(principal.employeeId());
    }

    public List<Map<String, Object>> approvalDocuments(OfficePrincipal principal) {
        return officeMapper.listApprovalDocuments(principal.companyId(), principal.employeeId(),
                principal.hasRole("ADMIN"));
    }

    public Map<String, Object> approvalDocument(OfficePrincipal principal, Long documentId) {
        Map<String, Object> document = officeMapper.findApprovalDocument(principal.companyId(), documentId,
                principal.employeeId(), principal.hasRole("ADMIN"));
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "결재 문서를 찾을 수 없습니다.");
        }
        Map<String, Object> result = new HashMap<>(document);
        result.put("lines", officeMapper.findApprovalLines(documentId));
        result.put("attachments", officeMapper.findApprovalAttachments(documentId));
        return result;
    }

    @Transactional
    public void approve(OfficePrincipal actor, Long documentId, String opinion) {
        Map<String, Object> current = officeMapper.lockPendingApprovalLine(documentId, actor.employeeId());
        if (current == null) {
            throw new BusinessException("현재 처리할 수 있는 결재가 아닙니다.");
        }
        Long lineId = number(current, "lineId");
        int stepOrder = integer(current.get("stepOrder"));
        officeMapper.approveLine(lineId, clean(opinion));
        approvalHistory(documentId, lineId, actor.employeeId(), "APPROVE",
                "PENDING", "APPROVED", opinion);

        Map<String, Object> next = officeMapper.findNextApprovalLine(documentId, stepOrder);
        if (next != null) {
            Long nextLineId = number(next, "lineId");
            int nextStep = integer(next.get("stepOrder"));
            officeMapper.activateApprovalLine(nextLineId);
            officeMapper.moveApprovalDocumentStep(documentId, nextStep);
            notifyEmployee(actor, number(next, "approverEmployeeId"), "APPROVAL_REQUESTED",
                    "새 결재 요청", String.valueOf(current.get("title")),
                    "/approvals/" + documentId, "APPROVAL_DOCUMENT", documentId);
        } else {
            officeMapper.completeApprovalDocument(documentId, "APPROVED");
            completeLeaveIfPresent(actor, documentId, true, null);
            notifyEmployee(actor, number(current, "drafterEmployeeId"), "APPROVAL_COMPLETED",
                    "결재 승인", String.valueOf(current.get("title")) + " 문서가 승인되었습니다.",
                    "/approvals/" + documentId, "APPROVAL_DOCUMENT", documentId);
        }
        audit(actor, "UPDATE", "APPROVAL_DOCUMENT", documentId, "결재 승인");
    }

    @Transactional
    public void reject(OfficePrincipal actor, Long documentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("반려 사유를 입력해 주세요.");
        }
        Map<String, Object> current = officeMapper.lockPendingApprovalLine(documentId, actor.employeeId());
        if (current == null) {
            throw new BusinessException("현재 처리할 수 있는 결재가 아닙니다.");
        }
        Long lineId = number(current, "lineId");
        officeMapper.rejectLine(lineId, reason.trim());
        officeMapper.completeApprovalDocument(documentId, "REJECTED");
        completeLeaveIfPresent(actor, documentId, false, reason.trim());
        approvalHistory(documentId, lineId, actor.employeeId(), "REJECT",
                "PENDING", "REJECTED", reason);
        notifyEmployee(actor, number(current, "drafterEmployeeId"), "APPROVAL_COMPLETED",
                "결재 반려", String.valueOf(current.get("title")) + " 문서가 반려되었습니다.",
                "/approvals/" + documentId, "APPROVAL_DOCUMENT", documentId);
        audit(actor, "UPDATE", "APPROVAL_DOCUMENT", documentId, "결재 반려");
    }

    public Map<String, Object> todayAttendance(OfficePrincipal principal) {
        Map<String, Object> attendance = officeMapper.findTodayAttendance(principal.employeeId(), LocalDate.now());
        return attendance == null ? Map.of() : attendance;
    }

    public PageResult<Map<String, Object>> attendanceHistory(OfficePrincipal principal, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        long total = officeMapper.countAttendanceHistory(principal.employeeId());
        return new PageResult<>(officeMapper.listAttendanceHistory(principal.employeeId(),
                (safePage - 1) * safeSize, safeSize), total, safePage, safeSize);
    }

    @Transactional
    public Map<String, Object> clockIn(OfficePrincipal actor) {
        LocalDate workDate = LocalDate.now();
        if (officeMapper.findTodayAttendance(actor.employeeId(), workDate) != null) {
            throw new BusinessException("오늘 출근 기록이 이미 존재합니다.");
        }
        Map<String, Object> policy = officeMapper.findEmployeeWorkPolicy(actor.employeeId(), workDate);
        if (policy == null) {
            throw new BusinessException("적용된 근무 정책이 없습니다. 관리자에게 문의해 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledStart = workDate.atTime(localTime(policy.get("workStartTime")));
        LocalDateTime scheduledEnd = workDate.atTime(localTime(policy.get("workEndTime")));
        int grace = integer(policy.get("lateGraceMinutes"));
        int lateMinutes = now.isAfter(scheduledStart.plusMinutes(grace))
                ? (int) Duration.between(scheduledStart, now).toMinutes() : 0;

        Map<String, Object> values = new HashMap<>();
        values.put("employeeId", actor.employeeId());
        values.put("workPolicyId", number(policy, "workPolicyId"));
        values.put("workDate", workDate);
        values.put("scheduledStartAt", scheduledStart);
        values.put("scheduledEndAt", scheduledEnd);
        values.put("clockInAt", now);
        values.put("lateMinutes", lateMinutes);
        values.put("status", lateMinutes > 0 ? "LATE" : "NORMAL");
        officeMapper.insertClockIn(values);
        audit(actor, "CREATE", "ATTENDANCE", number(values, "attendanceRecordId"), "출근 기록");
        return officeMapper.findTodayAttendance(actor.employeeId(), workDate);
    }

    @Transactional
    public Map<String, Object> clockOut(OfficePrincipal actor) {
        LocalDate workDate = LocalDate.now();
        Map<String, Object> attendance = officeMapper.findTodayAttendance(actor.employeeId(), workDate);
        if (attendance == null || attendance.get("clockInAt") == null) {
            throw new BusinessException("출근 기록이 없습니다.");
        }
        if (attendance.get("clockOutAt") != null) {
            throw new BusinessException("오늘 퇴근 기록이 이미 존재합니다.");
        }
        Map<String, Object> policy = officeMapper.findEmployeeWorkPolicy(actor.employeeId(), workDate);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime clockInAt = localDateTime(attendance.get("clockInAt"));
        LocalDateTime scheduledEnd = localDateTime(attendance.get("scheduledEndAt"));
        int configuredBreak = integer(policy.get("breakMinutes"));
        int elapsed = Math.max(0, (int) Duration.between(clockInAt, now).toMinutes());
        int breakMinutes = elapsed >= 360 ? configuredBreak : 0;
        int workMinutes = Math.max(0, elapsed - breakMinutes);
        int standard = integer(policy.get("standardWorkMinutes"));
        int overtime = Math.max(0, workMinutes - standard);
        int earlyGrace = integer(policy.get("earlyLeaveGraceMinutes"));
        int earlyMinutes = now.isBefore(scheduledEnd.minusMinutes(earlyGrace))
                ? (int) Duration.between(now, scheduledEnd).toMinutes() : 0;
        int lateMinutes = integer(attendance.get("lateMinutes"));
        String status = lateMinutes > 0 && earlyMinutes > 0 ? "LATE_EARLY"
                : lateMinutes > 0 ? "LATE"
                : earlyMinutes > 0 ? "EARLY_LEAVE" : "NORMAL";

        Map<String, Object> values = new HashMap<>();
        values.put("attendanceRecordId", number(attendance, "attendanceRecordId"));
        values.put("employeeId", actor.employeeId());
        values.put("clockOutAt", now);
        values.put("breakMinutes", breakMinutes);
        values.put("workMinutes", workMinutes);
        values.put("overtimeMinutes", overtime);
        values.put("earlyLeaveMinutes", earlyMinutes);
        values.put("status", status);
        officeMapper.updateClockOut(values);
        audit(actor, "UPDATE", "ATTENDANCE", number(attendance, "attendanceRecordId"), "퇴근 기록");
        return officeMapper.findTodayAttendance(actor.employeeId(), workDate);
    }

    public PageResult<Map<String, Object>> notices(OfficePrincipal principal, String keyword,
                                                   int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = normalizeSize(size);
        long total = officeMapper.countNotices(principal.companyId(), clean(keyword));
        return new PageResult<>(officeMapper.listNotices(principal.companyId(), clean(keyword),
                (safePage - 1) * safeSize, safeSize), total, safePage, safeSize);
    }

    @Transactional
    public Map<String, Object> notice(OfficePrincipal principal, Long noticeId) {
        Map<String, Object> notice = officeMapper.findNotice(principal.companyId(), noticeId);
        if (notice == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }
        officeMapper.incrementNoticeView(noticeId, principal.accountId());
        return notice;
    }

    @Transactional
    public Long createNotice(OfficePrincipal actor, ApiDtos.NoticeRequest request) {
        Map<String, Object> values = noticeValues(actor, null, request);
        officeMapper.insertNotice(values);
        Long id = number(values, "noticeId");
        audit(actor, "CREATE", "NOTICE", id, request.title() + " 공지 등록");
        return id;
    }

    @Transactional
    public void updateNotice(OfficePrincipal actor, Long noticeId, ApiDtos.NoticeRequest request) {
        Map<String, Object> values = noticeValues(actor, noticeId, request);
        officeMapper.updateNotice(values);
        audit(actor, "UPDATE", "NOTICE", noticeId, request.title() + " 공지 수정");
    }

    @Transactional
    public void deleteNotice(OfficePrincipal actor, Long noticeId) {
        officeMapper.softDeleteNotice(actor.companyId(), noticeId, actor.accountId());
        audit(actor, "DELETE", "NOTICE", noticeId, "공지 삭제");
    }

    public List<Map<String, Object>> noticeCategories(Long companyId) {
        return officeMapper.listNoticeCategories(companyId);
    }

    public List<Map<String, Object>> noticeAttachments(Long noticeId) {
        return officeMapper.findNoticeAttachments(noticeId);
    }

    public PageResult<Map<String, Object>> resources(OfficePrincipal principal, String keyword,
                                                     int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = normalizeSize(size);
        long total = officeMapper.countResources(principal.companyId(), clean(keyword));
        return new PageResult<>(officeMapper.listResources(principal.companyId(), clean(keyword),
                (safePage - 1) * safeSize, safeSize), total, safePage, safeSize);
    }

    @Transactional
    public Long createResource(OfficePrincipal actor, String title, String description, Long fileId) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("자료 제목을 입력해 주세요.");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", actor.companyId());
        values.put("authorAccountId", actor.accountId());
        values.put("title", title.trim());
        values.put("description", clean(description));
        officeMapper.insertResource(values);
        Long resourceId = number(values, "resourceId");
        officeMapper.linkResourceFile(resourceId, fileId);
        audit(actor, "CREATE", "RESOURCE", resourceId, title + " 자료 등록");
        return resourceId;
    }

    public List<Map<String, Object>> calendarEvents(OfficePrincipal principal,
                                                     LocalDateTime from, LocalDateTime to) {
        LocalDateTime safeFrom = from == null ? LocalDate.now().withDayOfMonth(1).atStartOfDay() : from;
        LocalDateTime safeTo = to == null ? safeFrom.plusMonths(1) : to;
        return officeMapper.listCalendarEvents(principal.companyId(), principal.employeeId(), safeFrom, safeTo);
    }

    @Transactional
    public Long createEvent(OfficePrincipal actor, ApiDtos.EventRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", actor.companyId());
        values.put("organizerEmployeeId", actor.employeeId());
        values.put("eventType", request.eventType());
        values.put("title", request.title());
        values.put("description", clean(request.description()));
        values.put("location", clean(request.location()));
        values.put("startsAt", request.startsAt());
        values.put("endsAt", request.endsAt());
        values.put("allDay", request.allDay());
        values.put("visibility", request.visibility());
        values.put("sourceType", "MANUAL");
        values.put("sourceId", null);
        values.put("color", request.color() == null || request.color().isBlank() ? "#4f46e5" : request.color());
        officeMapper.insertCalendarEvent(values);
        Long eventId = number(values, "eventId");
        audit(actor, "CREATE", "CALENDAR_EVENT", eventId, request.title() + " 일정 등록");
        return eventId;
    }

    @Transactional
    public void deleteEvent(OfficePrincipal actor, Long eventId) {
        officeMapper.deleteCalendarEvent(actor.companyId(), eventId, actor.employeeId(), actor.hasRole("ADMIN"));
        audit(actor, "DELETE", "CALENDAR_EVENT", eventId, "일정 삭제");
    }

    public List<Map<String, Object>> inbox(OfficePrincipal principal) {
        return officeMapper.listInbox(principal.accountId());
    }

    public List<Map<String, Object>> sentMessages(OfficePrincipal principal) {
        return officeMapper.listSentMessages(principal.accountId());
    }

    @Transactional
    public Long sendMessage(OfficePrincipal actor, ApiDtos.MessageRequest request) {
        if (Objects.equals(actor.accountId(), request.recipientAccountId())) {
            throw new BusinessException("자기 자신에게는 쪽지를 보낼 수 없습니다.");
        }
        Map<String, Object> values = new HashMap<>();
        values.put("senderAccountId", actor.accountId());
        values.put("recipientAccountId", request.recipientAccountId());
        values.put("subject", request.subject());
        values.put("body", request.body());
        officeMapper.insertMessage(values);
        Long messageId = number(values, "messageId");
        Map<String, Object> notification = new HashMap<>();
        notification.put("recipientAccountId", request.recipientAccountId());
        notification.put("actorAccountId", actor.accountId());
        notification.put("notificationType", "MESSAGE_RECEIVED");
        notification.put("title", "새 쪽지");
        notification.put("message", actor.employeeName() + "님이 쪽지를 보냈습니다.");
        notification.put("linkUrl", "/messages");
        notification.put("referenceType", "MESSAGE");
        notification.put("referenceId", messageId);
        officeMapper.insertNotification(notification);
        return messageId;
    }

    @Transactional
    public void readMessage(OfficePrincipal principal, Long messageId) {
        officeMapper.markMessageRead(messageId, principal.accountId());
    }

    public List<Map<String, Object>> notifications(OfficePrincipal principal) {
        return officeMapper.listNotifications(principal.accountId());
    }

    public int unreadNotificationCount(OfficePrincipal principal) {
        return officeMapper.countUnreadNotifications(principal.accountId());
    }

    @Transactional
    public void readNotification(OfficePrincipal principal, Long notificationId) {
        officeMapper.markNotificationRead(notificationId, principal.accountId());
    }

    @Transactional
    public void readAllNotifications(OfficePrincipal principal) {
        officeMapper.markAllNotificationsRead(principal.accountId());
    }

    @Transactional
    public List<Map<String, Object>> search(OfficePrincipal principal, String keyword) {
        String safeKeyword = clean(keyword);
        if (safeKeyword == null || safeKeyword.length() < 2) {
            return List.of();
        }
        var results = officeMapper.integratedSearch(principal.companyId(), principal.employeeId(), safeKeyword);
        officeMapper.insertSearchHistory(principal.accountId(), safeKeyword, results.size());
        return results;
    }

    public Map<String, Object> myProfile(OfficePrincipal principal) {
        return officeMapper.findMyProfile(principal.employeeId());
    }

    public List<Map<String, Object>> recentLogins(OfficePrincipal principal) {
        return officeMapper.findRecentLogins(principal.accountId());
    }

    @Transactional
    public void changePassword(OfficePrincipal actor, ApiDtos.PasswordChangeRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("새 비밀번호 확인이 일치하지 않습니다.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), officeMapper.findPasswordHash(actor.accountId()))) {
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncoder.matches(request.newPassword(), officeMapper.findPasswordHash(actor.accountId()))) {
            throw new BusinessException("현재 비밀번호와 다른 비밀번호를 사용해 주세요.");
        }
        officeMapper.changePassword(actor.accountId(), passwordEncoder.encode(request.newPassword()));
        audit(actor, "UPDATE", "ACCOUNT", actor.accountId(), "비밀번호 변경");
    }

    @Transactional
    public void resetEmployeePassword(OfficePrincipal actor, Long employeeId,
                                      ApiDtos.PasswordResetRequest request) {
        int updated = officeMapper.resetEmployeePassword(actor.companyId(), employeeId,
                passwordEncoder.encode(request.newPassword()));
        if (updated == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "초기화할 사원 계정을 찾을 수 없습니다.");
        }
        audit(actor, "UPDATE", "ACCOUNT", employeeId, "관리자 비밀번호 초기화");
    }

    private void completeLeaveIfPresent(OfficePrincipal actor, Long documentId,
                                        boolean approved, String rejectionReason) {
        Map<String, Object> leave = officeMapper.lockLeaveByApprovalDocument(documentId);
        if (leave == null) {
            return;
        }
        Long leaveRequestId = number(leave, "leaveRequestId");
        Long leaveBalanceId = nullableNumber(leave.get("leaveBalanceId"));
        double days = decimal(leave.get("requestedDays"));
        if (approved) {
            officeMapper.approveLeaveRequest(leaveRequestId);
            if (leaveBalanceId != null) {
                officeMapper.consumeLeaveBalance(leaveBalanceId, days);
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("leaveBalanceId", leaveBalanceId);
                transaction.put("leaveRequestId", leaveRequestId);
                transaction.put("transactionType", "USE");
                transaction.put("idempotencyKey", "LEAVE-" + leaveRequestId);
                transaction.put("days", -days);
                transaction.put("effectiveDate", localDate(leave.get("startDate")));
                transaction.put("description", "승인된 휴가 사용");
                transaction.put("createdByAccountId", actor.accountId());
                officeMapper.insertLeaveTransaction(transaction);
            }
            Map<String, Object> event = new HashMap<>();
            event.put("companyId", actor.companyId());
            event.put("organizerEmployeeId", number(leave, "employeeId"));
            event.put("eventType", "LEAVE");
            event.put("title", String.valueOf(leave.get("leaveTypeName")));
            event.put("description", "승인된 휴가");
            event.put("location", null);
            event.put("startsAt", localDate(leave.get("startDate")).atStartOfDay());
            event.put("endsAt", localDate(leave.get("endDate")).plusDays(1).atStartOfDay());
            event.put("allDay", true);
            event.put("visibility", "COMPANY");
            event.put("sourceType", "LEAVE_REQUEST");
            event.put("sourceId", leaveRequestId);
            event.put("color", "#10b981");
            officeMapper.insertCalendarEvent(event);
        } else {
            officeMapper.rejectLeaveRequest(leaveRequestId, rejectionReason);
            if (leaveBalanceId != null) {
                officeMapper.releasePendingLeave(leaveBalanceId, days);
            }
        }
    }

    private List<Long> resolveApprovers(OfficePrincipal actor) {
        List<Long> approvers = new ArrayList<>();
        if (actor.departmentId() != null) {
            Long leader = officeMapper.findDepartmentLeaderEmployeeId(actor.departmentId());
            if (leader != null && !leader.equals(actor.employeeId())) {
                approvers.add(leader);
            }
        }
        Long admin = officeMapper.findAdminEmployeeId(actor.companyId(), actor.employeeId());
        if (admin != null && !approvers.contains(admin)) {
            approvers.add(admin);
        }
        if (approvers.isEmpty()) {
            throw new BusinessException("결재자를 찾을 수 없습니다. 부서장 또는 관리자 계정을 확인해 주세요.");
        }
        return approvers;
    }

    private void insertApprovalLinesAndNotify(OfficePrincipal actor, Long documentId,
                                              List<Long> approvers, String title) {
        for (int index = 0; index < approvers.size(); index++) {
            Map<String, Object> line = new HashMap<>();
            line.put("documentId", documentId);
            line.put("stepOrder", index + 1);
            line.put("approverEmployeeId", approvers.get(index));
            line.put("status", index == 0 ? "PENDING" : "WAITING");
            officeMapper.insertApprovalLine(line);
        }
        notifyEmployee(actor, approvers.get(0), "APPROVAL_REQUESTED", "새 결재 요청", title,
                "/approvals/" + documentId, "APPROVAL_DOCUMENT", documentId);
    }

    private void notifyEmployee(OfficePrincipal actor, Long employeeId, String type,
                                String title, String message, String link,
                                String referenceType, Long referenceId) {
        if (employeeId == null) {
            return;
        }
        Long accountId = officeMapper.findAccountIdByEmployeeId(employeeId);
        if (accountId == null) {
            return;
        }
        Map<String, Object> notification = new HashMap<>();
        notification.put("recipientAccountId", accountId);
        notification.put("actorAccountId", actor.accountId());
        notification.put("notificationType", type);
        notification.put("title", title);
        notification.put("message", message);
        notification.put("linkUrl", link);
        notification.put("referenceType", referenceType);
        notification.put("referenceId", referenceId);
        officeMapper.insertNotification(notification);
    }

    private void approvalHistory(Long documentId, Long lineId, Long actorEmployeeId,
                                 String action, String from, String to, String comment) {
        Map<String, Object> history = new HashMap<>();
        history.put("documentId", documentId);
        history.put("lineId", lineId);
        history.put("actorEmployeeId", actorEmployeeId);
        history.put("actionType", action);
        history.put("fromStatus", from);
        history.put("toStatus", to);
        history.put("comment", clean(comment));
        officeMapper.insertApprovalHistory(history);
    }

    private void audit(OfficePrincipal actor, String action, String entityType,
                       Object entityId, String description) {
        Map<String, Object> audit = new HashMap<>();
        audit.put("companyId", actor.companyId());
        audit.put("actorAccountId", actor.accountId());
        audit.put("actionType", action);
        audit.put("entityType", entityType);
        audit.put("entityId", String.valueOf(entityId));
        audit.put("description", description);
        audit.put("requestMethod", null);
        audit.put("requestUri", null);
        audit.put("ipAddress", null);
        officeMapper.insertAuditLog(audit);
    }

    private Map<String, Object> employeeValues(Long companyId, Long employeeId,
                                               ApiDtos.EmployeeRequest request) {
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", companyId);
        values.put("employeeId", employeeId);
        values.put("departmentId", request.departmentId());
        values.put("positionId", request.positionId());
        values.put("employeeNumber", request.employeeNumber().trim());
        values.put("employeeName", request.employeeName().trim());
        values.put("email", request.email().trim().toLowerCase());
        values.put("phone", clean(request.phone()));
        values.put("hireDate", request.hireDate());
        values.put("employmentStatus", request.employmentStatus());
        values.put("employmentType", request.employmentType());
        values.put("memo", clean(request.memo()));
        return values;
    }

    private Map<String, Object> departmentValues(Long companyId, Long departmentId,
                                                 ApiDtos.DepartmentRequest request) {
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", companyId);
        values.put("departmentId", departmentId);
        values.put("parentDepartmentId", request.parentDepartmentId());
        values.put("leaderEmployeeId", request.leaderEmployeeId());
        values.put("departmentCode", request.departmentCode().trim().toUpperCase());
        values.put("departmentName", request.departmentName().trim());
        values.put("description", clean(request.description()));
        values.put("sortOrder", request.sortOrder() == null ? 0 : request.sortOrder());
        return values;
    }

    private Map<String, Object> positionValues(Long companyId, Long positionId,
                                               ApiDtos.PositionRequest request) {
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", companyId);
        values.put("positionId", positionId);
        values.put("positionCode", request.positionCode().trim().toUpperCase());
        values.put("positionName", request.positionName().trim());
        values.put("positionLevel", request.positionLevel());
        values.put("sortOrder", request.sortOrder() == null ? request.positionLevel() : request.sortOrder());
        return values;
    }

    private Map<String, Object> noticeValues(OfficePrincipal actor, Long noticeId,
                                             ApiDtos.NoticeRequest request) {
        Map<String, Object> values = new HashMap<>();
        values.put("companyId", actor.companyId());
        values.put("noticeId", noticeId);
        values.put("categoryId", request.categoryId());
        values.put("authorAccountId", actor.accountId());
        values.put("title", request.title().trim());
        values.put("content", request.content().trim());
        values.put("pinned", request.pinned());
        values.put("published", request.published());
        return values;
    }

    private void validateAccountFields(String loginId, String initialPassword) {
        if ((loginId == null || loginId.isBlank()) && initialPassword != null && !initialPassword.isBlank()) {
            throw new BusinessException("초기 비밀번호를 입력하려면 로그인 아이디도 입력해야 합니다.");
        }
        if (loginId != null && !loginId.isBlank()
                && (initialPassword == null || initialPassword.length() < 8)) {
            throw new BusinessException("초기 비밀번호는 8자 이상이어야 합니다.");
        }
    }

    private void validateLeavePeriod(ApiDtos.LeaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new BusinessException("지난 날짜로 휴가를 신청할 수 없습니다.");
        }
        if (!"DAY".equals(request.leaveUnit()) && !request.startDate().equals(request.endDate())) {
            throw new BusinessException("반차는 하루만 선택해 주세요.");
        }
    }

    private double calculateLeaveDays(LocalDate start, LocalDate end, String unit) {
        if (!"DAY".equals(unit)) {
            return 0.5;
        }
        double days = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        if (days == 0) {
            throw new BusinessException("선택한 기간에 근무일이 없습니다.");
        }
        return days;
    }

    private double initialLeaveDays(LocalDate hireDate, LocalDate today) {
        if (hireDate.plusYears(1).isAfter(today)) {
            int completedMonths = Math.max(0,
                    (today.getYear() - hireDate.getYear()) * 12 + today.getMonthValue() - hireDate.getMonthValue());
            return Math.min(11, completedMonths);
        }
        return 15;
    }

    private String documentNumber() {
        return "APP-" + LocalDateTime.now().toString().replaceAll("[-:T.]", "").substring(0, 14)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private int normalizeSize(int size) {
        return size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, 100);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Long number(Map<String, Object> source, String key) {
        return ((Number) source.get(key)).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private int integer(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private double decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return value == null ? 0 : ((Number) value).doubleValue();
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : value instanceof Number number && number.intValue() != 0;
    }

    private LocalTime localTime(Object value) {
        if (value instanceof LocalTime localTime) {
            return localTime;
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        return LocalTime.parse(String.valueOf(value));
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }
}
