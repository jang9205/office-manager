package com.example.office_manager;

import com.example.office_manager.security.OfficePrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
class OfficeManagerApplicationTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserDetailsService userDetailsService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void openApiDocumentIsPublished() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails("admin")
	void adminPagesRenderWithoutServerError() throws Exception {
		List<String> pages = List.of(
				"/dashboard", "/employees", "/organization", "/leaves", "/approvals",
				"/attendance", "/notices", "/resources", "/calendar", "/messages",
				"/my-page", "/search?q=%EA%B9%80"
		);
		for (String page : pages) {
			mockMvc.perform(get(page)).andExpect(status().isOk());
		}
	}

	@Test
	@WithUserDetails("admin")
	void organizationSelectionShowsMatchingEmployees() throws Exception {
		Long departmentId = jdbcTemplate.queryForObject(
				"SELECT department_id FROM departments WHERE department_code = 'DEV' AND deleted_at IS NULL",
				Long.class);
		mockMvc.perform(get("/organization").param("departmentId", departmentId.toString()))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("organizationEmployees", "selectedOrganizationName"));
	}

	@Test
	void employeeCanBrowseOtherDepartmentEmployeesFromOrganization() throws Exception {
		var employee = userDetailsService.loadUserByUsername("employee");
		Long departmentId = jdbcTemplate.queryForObject(
				"SELECT department_id FROM departments WHERE department_code = 'HR' AND deleted_at IS NULL",
				Long.class);
		mockMvc.perform(get("/organization").param("departmentId", departmentId.toString()).with(user(employee)))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("organizationEmployees", "selectedOrganizationName"));
	}

	@Test
	void roleBasedAccessIsEnforced() throws Exception {
		var employee = userDetailsService.loadUserByUsername("employee");
		var manager = userDetailsService.loadUserByUsername("manager");

		mockMvc.perform(get("/employees").with(user(employee)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/employees").with(user(manager)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/admin/notices").with(user(employee)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"권한 테스트","content":"사원은 등록할 수 없습니다.","pinned":false,"published":true}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	@Transactional
	void approvalAndAttendanceWorkflowWorks() throws Exception {
		var employee = userDetailsService.loadUserByUsername("employee");
		var manager = userDetailsService.loadUserByUsername("manager");
		var admin = userDetailsService.loadUserByUsername("admin");

		String body = mockMvc.perform(post("/api/v1/approvals").with(user(employee)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"통합 테스트 결재","content":"팀장과 관리자의 순차 승인을 확인합니다."}
						"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		var matcher = Pattern.compile("\\\"documentId\\\"\\s*:\\s*(\\d+)").matcher(body);
		if (!matcher.find()) {
			throw new AssertionError("결재 문서 ID를 응답에서 찾을 수 없습니다: " + body);
		}
		long documentId = Long.parseLong(matcher.group(1));

		mockMvc.perform(post("/api/v1/approvals/{id}/approve", documentId)
				.with(user(employee)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/approvals/{id}/approve", documentId)
				.with(user(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"opinion\":\"팀장 승인\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/approvals/{id}/approve", documentId)
				.with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"opinion\":\"최종 승인\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/approvals/{id}", documentId).with(user(employee)))
				.andExpect(status().isOk());

		jdbcTemplate.update("DELETE FROM attendance_records WHERE employee_id = ? AND work_date = CURRENT_DATE",
				((OfficePrincipal) employee).employeeId());
		mockMvc.perform(post("/api/v1/attendance/clock-in").with(user(employee)).with(csrf()))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/attendance/clock-out").with(user(employee)).with(csrf()))
				.andExpect(status().isOk());
	}

	@Test
	void beanValidationReturnsBadRequest() throws Exception {
		var admin = userDetailsService.loadUserByUsername("admin");
		mockMvc.perform(post("/api/v1/employees").with(user(admin)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"employeeNumber":"","employeeName":"","email":"wrong","hireDate":null}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@Transactional
	void adminCanResetEmployeePassword() throws Exception {
		var admin = userDetailsService.loadUserByUsername("admin");
		var employee = (OfficePrincipal) userDetailsService.loadUserByUsername("employee");
		mockMvc.perform(post("/api/v1/admin/employees/{id}/reset-password", employee.employeeId())
				.with(user(admin)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newPassword\":\"temporary1234!\"}"))
				.andExpect(status().isOk());
	}

}
