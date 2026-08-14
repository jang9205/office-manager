package com.example.office_manager.service;

import com.example.office_manager.mapper.OfficeMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {

    private final OfficeMapper officeMapper;
    private final PasswordEncoder passwordEncoder;

    public DemoDataBootstrap(OfficeMapper officeMapper, PasswordEncoder passwordEncoder) {
        this.officeMapper = officeMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        officeMapper.deleteRolePermission("ROLE_EMPLOYEE", "APPROVAL:APPROVE");
        if (officeMapper.countAccounts() > 0) {
            return;
        }

        Long companyId = officeMapper.findCompanyIdByCode("OFFICE");
        if (companyId == null) {
            throw new IllegalStateException("DB 초기 스키마와 기준 데이터를 먼저 실행해 주세요.");
        }

        Long hrDepartmentId = officeMapper.findDepartmentIdByCode(companyId, "HR");
        Long devDepartmentId = officeMapper.findDepartmentIdByCode(companyId, "DEV");
        Long generalPositionId = officeMapper.findPositionIdByCode(companyId, "GENERAL");
        Long managerPositionId = officeMapper.findPositionIdByCode(companyId, "MANAGER");
        Long staffPositionId = officeMapper.findPositionIdByCode(companyId, "STAFF");

        Long adminEmployeeId = createDemoAccount(companyId, hrDepartmentId, generalPositionId,
                "A0001", "김관리", "admin@office.local", LocalDate.of(2022, 3, 2),
                "admin", "admin1234!", "ROLE_ADMIN");

        Long managerEmployeeId = createDemoAccount(companyId, devDepartmentId, managerPositionId,
                "M0001", "박팀장", "manager@office.local", LocalDate.of(2023, 5, 15),
                "manager", "manager1234!", "ROLE_MANAGER");

        createDemoAccount(companyId, devDepartmentId, staffPositionId,
                "E0001", "장사원", "employee@office.local", LocalDate.of(2025, 2, 3),
                "employee", "employee1234!", "ROLE_EMPLOYEE");

        if (devDepartmentId != null) {
            officeMapper.updateDepartmentLeader(devDepartmentId, managerEmployeeId);
        }
        if (hrDepartmentId != null) {
            officeMapper.updateDepartmentLeader(hrDepartmentId, adminEmployeeId);
        }
    }

    private Long createDemoAccount(Long companyId, Long departmentId, Long positionId,
                                   String employeeNumber, String employeeName, String email,
                                   LocalDate hireDate, String loginId, String password,
                                   String roleCode) {
        Map<String, Object> employee = new HashMap<>();
        employee.put("companyId", companyId);
        employee.put("departmentId", departmentId);
        employee.put("positionId", positionId);
        employee.put("employeeNumber", employeeNumber);
        employee.put("employeeName", employeeName);
        employee.put("email", email);
        employee.put("phone", "010-0000-0000");
        employee.put("hireDate", hireDate);
        employee.put("employmentStatus", "ACTIVE");
        employee.put("employmentType", "FULL_TIME");
        employee.put("memo", "포트폴리오 시연 계정");
        officeMapper.insertEmployee(employee);
        Long employeeId = ((Number) employee.get("employeeId")).longValue();

        Map<String, Object> account = new HashMap<>();
        account.put("employeeId", employeeId);
        account.put("loginId", loginId);
        account.put("passwordHash", passwordEncoder.encode(password));
        account.put("mustChangePassword", false);
        account.put("createdByAccountId", null);
        officeMapper.insertAccount(account);
        Long accountId = ((Number) account.get("accountId")).longValue();

        Long roleId = officeMapper.findRoleIdByCode(roleCode);
        officeMapper.insertAccountRole(accountId, roleId);

        LocalDate today = LocalDate.now();
        Long leavePolicyId = officeMapper.findActiveLeavePolicyId(companyId, today);
        if (leavePolicyId != null) {
            officeMapper.insertInitialLeaveBalance(employeeId, leavePolicyId, today.getYear(), 15.0);
        }
        Long workPolicyId = officeMapper.findActiveWorkPolicyId(companyId, today);
        if (workPolicyId != null) {
            officeMapper.insertWorkAssignment(employeeId, workPolicyId, hireDate);
        }
        return employeeId;
    }
}
