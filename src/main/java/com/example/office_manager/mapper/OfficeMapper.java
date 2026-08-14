package com.example.office_manager.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OfficeMapper {

    Map<String, Object> findAccountByLoginId(@Param("loginId") String loginId);

    List<String> findAuthorities(@Param("accountId") Long accountId);

    int deleteRolePermission(@Param("roleCode") String roleCode,
                             @Param("permissionCode") String permissionCode);

    int countAccounts();

    void recordLoginFailure(@Param("loginId") String loginId);

    void recordLoginSuccess(@Param("accountId") Long accountId, @Param("ip") String ip);

    void insertLoginHistory(@Param("accountId") Long accountId,
                            @Param("loginId") String loginId,
                            @Param("success") boolean success,
                            @Param("failureReason") String failureReason,
                            @Param("ip") String ip,
                            @Param("userAgent") String userAgent);

    Long findCompanyIdByCode(@Param("code") String code);

    Long findDepartmentIdByCode(@Param("companyId") Long companyId, @Param("code") String code);

    Long findPositionIdByCode(@Param("companyId") Long companyId, @Param("code") String code);

    Long findRoleIdByCode(@Param("code") String code);

    Long findActiveLeavePolicyId(@Param("companyId") Long companyId, @Param("date") LocalDate date);

    Long findActiveWorkPolicyId(@Param("companyId") Long companyId, @Param("date") LocalDate date);

    void insertEmployee(Map<String, Object> values);

    void insertAccount(Map<String, Object> values);

    void insertAccountRole(@Param("accountId") Long accountId, @Param("roleId") Long roleId);

    void insertInitialLeaveBalance(@Param("employeeId") Long employeeId,
                                   @Param("policyId") Long policyId,
                                   @Param("year") int year,
                                   @Param("grantedDays") double grantedDays);

    void insertWorkAssignment(@Param("employeeId") Long employeeId,
                              @Param("policyId") Long policyId,
                              @Param("effectiveFrom") LocalDate effectiveFrom);

    void updateDepartmentLeader(@Param("departmentId") Long departmentId,
                                @Param("employeeId") Long employeeId);

    Map<String, Object> dashboardStats(@Param("companyId") Long companyId,
                                       @Param("employeeId") Long employeeId);

    List<Map<String, Object>> dashboardNotices(@Param("companyId") Long companyId);

    List<Map<String, Object>> dashboardEvents(@Param("companyId") Long companyId,
                                               @Param("employeeId") Long employeeId);

    List<Map<String, Object>> dashboardApprovals(@Param("employeeId") Long employeeId);

    List<Map<String, Object>> listDepartments(@Param("companyId") Long companyId);

    List<Map<String, Object>> listPositions(@Param("companyId") Long companyId);

    List<Map<String, Object>> listLeaveTypes(@Param("companyId") Long companyId);

    List<Map<String, Object>> listEmployeeOptions(@Param("companyId") Long companyId);

    List<Map<String, Object>> listAccountOptions(@Param("companyId") Long companyId,
                                                  @Param("excludeAccountId") Long excludeAccountId);

    long countEmployees(@Param("companyId") Long companyId,
                        @Param("keyword") String keyword,
                        @Param("departmentId") Long departmentId,
                        @Param("positionId") Long positionId);

    List<Map<String, Object>> listEmployees(@Param("companyId") Long companyId,
                                            @Param("keyword") String keyword,
                                            @Param("departmentId") Long departmentId,
                                            @Param("positionId") Long positionId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    Map<String, Object> findEmployee(@Param("companyId") Long companyId,
                                     @Param("employeeId") Long employeeId);

    void updateEmployee(Map<String, Object> values);

    void softDeleteEmployee(@Param("companyId") Long companyId,
                            @Param("employeeId") Long employeeId,
                            @Param("accountId") Long accountId);

    void insertDepartment(Map<String, Object> values);

    void updateDepartment(Map<String, Object> values);

    void softDeleteDepartment(@Param("companyId") Long companyId,
                              @Param("departmentId") Long departmentId);

    void insertPosition(Map<String, Object> values);

    void updatePosition(Map<String, Object> values);

    void softDeletePosition(@Param("companyId") Long companyId,
                            @Param("positionId") Long positionId);

    Map<String, Object> findLeaveBalance(@Param("employeeId") Long employeeId,
                                         @Param("year") int year);

    Map<String, Object> lockLeaveBalance(@Param("employeeId") Long employeeId,
                                         @Param("year") int year);

    long countLeaveRequests(@Param("companyId") Long companyId,
                            @Param("employeeId") Long employeeId,
                            @Param("departmentId") Long departmentId,
                            @Param("admin") boolean admin,
                            @Param("manager") boolean manager,
                            @Param("status") String status);

    List<Map<String, Object>> listLeaveRequests(@Param("companyId") Long companyId,
                                                @Param("employeeId") Long employeeId,
                                                @Param("departmentId") Long departmentId,
                                                @Param("admin") boolean admin,
                                                @Param("manager") boolean manager,
                                                @Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    Long findDepartmentLeaderEmployeeId(@Param("departmentId") Long departmentId);

    Long findAdminEmployeeId(@Param("companyId") Long companyId,
                             @Param("excludeEmployeeId") Long excludeEmployeeId);

    Long findAccountIdByEmployeeId(@Param("employeeId") Long employeeId);

    void insertApprovalDocument(Map<String, Object> values);

    void updateApprovalBusinessId(@Param("documentId") Long documentId,
                                  @Param("businessId") Long businessId);

    void insertApprovalLine(Map<String, Object> values);

    void insertLeaveRequest(Map<String, Object> values);

    void addPendingLeave(@Param("leaveBalanceId") Long leaveBalanceId,
                         @Param("days") double days);

    void insertNotification(Map<String, Object> values);

    List<Map<String, Object>> listApprovalTasks(@Param("employeeId") Long employeeId);

    List<Map<String, Object>> listApprovalDocuments(@Param("companyId") Long companyId,
                                                     @Param("employeeId") Long employeeId,
                                                     @Param("admin") boolean admin);

    Map<String, Object> findApprovalDocument(@Param("companyId") Long companyId,
                                              @Param("documentId") Long documentId,
                                              @Param("employeeId") Long employeeId,
                                              @Param("admin") boolean admin);

    List<Map<String, Object>> findApprovalLines(@Param("documentId") Long documentId);

    List<Map<String, Object>> findApprovalAttachments(@Param("documentId") Long documentId);

    void linkApprovalFile(@Param("documentId") Long documentId, @Param("fileId") Long fileId);

    Map<String, Object> lockPendingApprovalLine(@Param("documentId") Long documentId,
                                                @Param("approverEmployeeId") Long approverEmployeeId);

    void approveLine(@Param("lineId") Long lineId, @Param("opinion") String opinion);

    void rejectLine(@Param("lineId") Long lineId, @Param("reason") String reason);

    Map<String, Object> findNextApprovalLine(@Param("documentId") Long documentId,
                                              @Param("stepOrder") int stepOrder);

    void activateApprovalLine(@Param("lineId") Long lineId);

    void moveApprovalDocumentStep(@Param("documentId") Long documentId,
                                  @Param("stepOrder") int stepOrder);

    void completeApprovalDocument(@Param("documentId") Long documentId,
                                  @Param("status") String status);

    Map<String, Object> lockLeaveByApprovalDocument(@Param("documentId") Long documentId);

    void approveLeaveRequest(@Param("leaveRequestId") Long leaveRequestId);

    void rejectLeaveRequest(@Param("leaveRequestId") Long leaveRequestId,
                            @Param("reason") String reason);

    void consumeLeaveBalance(@Param("leaveBalanceId") Long leaveBalanceId,
                             @Param("days") double days);

    void releasePendingLeave(@Param("leaveBalanceId") Long leaveBalanceId,
                             @Param("days") double days);

    void insertLeaveTransaction(Map<String, Object> values);

    void insertCalendarEvent(Map<String, Object> values);

    void insertApprovalHistory(Map<String, Object> values);

    Map<String, Object> findTodayAttendance(@Param("employeeId") Long employeeId,
                                             @Param("workDate") LocalDate workDate);

    Map<String, Object> findEmployeeWorkPolicy(@Param("employeeId") Long employeeId,
                                                @Param("workDate") LocalDate workDate);

    void insertClockIn(Map<String, Object> values);

    void updateClockOut(Map<String, Object> values);

    long countAttendanceHistory(@Param("employeeId") Long employeeId);

    List<Map<String, Object>> listAttendanceHistory(@Param("employeeId") Long employeeId,
                                                     @Param("offset") int offset,
                                                     @Param("size") int size);

    long countNotices(@Param("companyId") Long companyId, @Param("keyword") String keyword);

    List<Map<String, Object>> listNotices(@Param("companyId") Long companyId,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    Map<String, Object> findNotice(@Param("companyId") Long companyId,
                                   @Param("noticeId") Long noticeId);

    List<Map<String, Object>> findNoticeAttachments(@Param("noticeId") Long noticeId);

    void incrementNoticeView(@Param("noticeId") Long noticeId,
                             @Param("accountId") Long accountId);

    void insertNotice(Map<String, Object> values);

    void updateNotice(Map<String, Object> values);

    void linkNoticeFile(@Param("noticeId") Long noticeId, @Param("fileId") Long fileId);

    void softDeleteNotice(@Param("companyId") Long companyId,
                          @Param("noticeId") Long noticeId,
                          @Param("accountId") Long accountId);

    List<Map<String, Object>> listNoticeCategories(@Param("companyId") Long companyId);

    long countResources(@Param("companyId") Long companyId, @Param("keyword") String keyword);

    List<Map<String, Object>> listResources(@Param("companyId") Long companyId,
                                            @Param("keyword") String keyword,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    void insertResource(Map<String, Object> values);

    void linkResourceFile(@Param("resourceId") Long resourceId, @Param("fileId") Long fileId);

    void insertFileAsset(Map<String, Object> values);

    Map<String, Object> findFileAsset(@Param("fileId") Long fileId);

    void recordFileDownload(@Param("fileId") Long fileId, @Param("accountId") Long accountId,
                            @Param("ip") String ip);

    void incrementResourceDownload(@Param("fileId") Long fileId);

    List<Map<String, Object>> listCalendarEvents(@Param("companyId") Long companyId,
                                                 @Param("employeeId") Long employeeId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    void deleteCalendarEvent(@Param("companyId") Long companyId,
                             @Param("eventId") Long eventId,
                             @Param("employeeId") Long employeeId,
                             @Param("admin") boolean admin);

    List<Map<String, Object>> listInbox(@Param("accountId") Long accountId);

    List<Map<String, Object>> listSentMessages(@Param("accountId") Long accountId);

    void insertMessage(Map<String, Object> values);

    void markMessageRead(@Param("messageId") Long messageId,
                         @Param("accountId") Long accountId);

    List<Map<String, Object>> listNotifications(@Param("accountId") Long accountId);

    int countUnreadNotifications(@Param("accountId") Long accountId);

    void markNotificationRead(@Param("notificationId") Long notificationId,
                              @Param("accountId") Long accountId);

    void markAllNotificationsRead(@Param("accountId") Long accountId);

    List<Map<String, Object>> integratedSearch(@Param("companyId") Long companyId,
                                               @Param("employeeId") Long employeeId,
                                               @Param("keyword") String keyword);

    void insertSearchHistory(@Param("accountId") Long accountId,
                             @Param("keyword") String keyword,
                             @Param("resultCount") int resultCount);

    Map<String, Object> findMyProfile(@Param("employeeId") Long employeeId);

    List<Map<String, Object>> findRecentLogins(@Param("accountId") Long accountId);

    String findPasswordHash(@Param("accountId") Long accountId);

    void changePassword(@Param("accountId") Long accountId,
                        @Param("passwordHash") String passwordHash);

    int resetEmployeePassword(@Param("companyId") Long companyId,
                              @Param("employeeId") Long employeeId,
                              @Param("passwordHash") String passwordHash);

    void updateProfileFile(@Param("employeeId") Long employeeId,
                           @Param("fileId") Long fileId);

    List<Map<String, Object>> exportEmployees(@Param("companyId") Long companyId);

    List<Map<String, Object>> exportLeaves(@Param("companyId") Long companyId);

    List<Map<String, Object>> exportNotices(@Param("companyId") Long companyId);

    void insertAuditLog(Map<String, Object> values);
}
