package com.example.office_manager.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record EmployeeRequest(
            @NotBlank @Size(max = 30) String employeeNumber,
            @NotBlank @Size(max = 50) String employeeName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            Long departmentId,
            Long positionId,
            @NotNull LocalDate hireDate,
            @NotBlank String employmentStatus,
            @NotBlank String employmentType,
            @Size(max = 1000) String memo,
            String loginId,
            String initialPassword,
            String roleCode
    ) {
    }

    public record DepartmentRequest(
            @NotBlank @Size(max = 30) String departmentCode,
            @NotBlank @Size(max = 100) String departmentName,
            Long parentDepartmentId,
            Long leaderEmployeeId,
            @Size(max = 500) String description,
            Integer sortOrder
    ) {
    }

    public record PositionRequest(
            @NotBlank @Size(max = 30) String positionCode,
            @NotBlank @Size(max = 50) String positionName,
            @NotNull @Positive Integer positionLevel,
            Integer sortOrder
    ) {
    }

    public record LeaveRequest(
            @NotNull Long leaveTypeId,
            @NotBlank String leaveUnit,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record ApprovalActionRequest(@Size(max = 1000) String opinion) {
    }

    public record GenericApprovalRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String content
    ) {
    }

    public record NoticeRequest(
            Long categoryId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 30000) String content,
            boolean pinned,
            boolean published
    ) {
    }

    public record EventRequest(
            @NotBlank String eventType,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @Size(max = 255) String location,
            @NotNull LocalDateTime startsAt,
            @NotNull LocalDateTime endsAt,
            boolean allDay,
            @NotBlank String visibility,
            String color
    ) {
    }

    public record MessageRequest(
            @NotNull Long recipientAccountId,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 30000) String body
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword,
            @NotBlank String confirmPassword
    ) {
    }

    public record PasswordResetRequest(
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {
    }
}
