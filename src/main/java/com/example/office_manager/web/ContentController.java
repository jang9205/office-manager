package com.example.office_manager.web;

import com.example.office_manager.mapper.OfficeMapper;
import com.example.office_manager.security.OfficePrincipal;
import com.example.office_manager.service.ExcelService;
import com.example.office_manager.service.FileStorageService;
import com.example.office_manager.service.OfficeService;
import com.example.office_manager.web.dto.ApiDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@Controller
public class ContentController {

    private final FileStorageService fileStorageService;
    private final ExcelService excelService;
    private final OfficeService officeService;
    private final OfficeMapper officeMapper;

    public ContentController(FileStorageService fileStorageService, ExcelService excelService,
                             OfficeService officeService, OfficeMapper officeMapper) {
        this.fileStorageService = fileStorageService;
        this.excelService = excelService;
        this.officeService = officeService;
        this.officeMapper = officeMapper;
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @AuthenticationPrincipal OfficePrincipal principal,
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "false") boolean inline,
            HttpServletRequest request) {
        var file = fileStorageService.load(principal, fileId);
        fileStorageService.recordDownload(principal, fileId, request.getRemoteAddr());
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    @PostMapping(value = "/content/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('RESOURCE:MANAGE')")
    @ResponseBody
    public Map<String, Object> createResource(@AuthenticationPrincipal OfficePrincipal principal,
                                              @RequestParam String title,
                                              @RequestParam(required = false) String description,
                                              @RequestParam MultipartFile file) {
        var stored = fileStorageService.store(principal, file);
        Long resourceId = officeService.createResource(principal, title, description, stored.fileId());
        return Map.of("message", "자료가 등록되었습니다.", "resourceId", resourceId);
    }

    @PostMapping(value = "/content/notices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('NOTICE:MANAGE')")
    @ResponseBody
    public Map<String, Object> createNotice(@AuthenticationPrincipal OfficePrincipal principal,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam String title,
                                            @RequestParam String content,
                                            @RequestParam(defaultValue = "false") boolean pinned,
                                            @RequestParam(defaultValue = "true") boolean published,
                                            @RequestParam(required = false) MultipartFile file) {
        Long noticeId = officeService.createNotice(principal,
                new ApiDtos.NoticeRequest(categoryId, title, content, pinned, published));
        if (file != null && !file.isEmpty()) {
            var stored = fileStorageService.store(principal, file);
            officeMapper.linkNoticeFile(noticeId, stored.fileId());
        }
        return Map.of("message", "공지사항이 등록되었습니다.", "noticeId", noticeId);
    }

    @PostMapping(value = "/content/approvals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('APPROVAL:REQUEST')")
    @ResponseBody
    public Map<String, Object> createApproval(@AuthenticationPrincipal OfficePrincipal principal,
                                              @RequestParam String title,
                                              @RequestParam String content,
                                              @RequestParam(required = false) MultipartFile file) {
        Long documentId = officeService.requestGenericApproval(principal,
                new ApiDtos.GenericApprovalRequest(title, content));
        if (file != null && !file.isEmpty()) {
            var stored = fileStorageService.store(principal, file);
            officeMapper.linkApprovalFile(documentId, stored.fileId());
        }
        return Map.of("message", "결재 요청이 등록되었습니다.", "documentId", documentId);
    }

    @PostMapping(value = "/content/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Map<String, Object> uploadProfile(@AuthenticationPrincipal OfficePrincipal principal,
                                             @RequestParam MultipartFile file) {
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new com.example.office_manager.common.BusinessException("프로필 사진은 이미지 파일만 가능합니다.");
        }
        var stored = fileStorageService.store(principal, file);
        officeMapper.updateProfileFile(principal.employeeId(), stored.fileId());
        return Map.of("message", "프로필 사진이 변경되었습니다.", "fileId", stored.fileId());
    }

    @GetMapping("/excel/employees")
    @PreAuthorize("hasAuthority('EMPLOYEE:EXPORT')")
    public ResponseEntity<byte[]> employeesExcel(@AuthenticationPrincipal OfficePrincipal principal) {
        return excel("사원목록_" + LocalDate.now() + ".xlsx", excelService.exportEmployees(principal));
    }

    @GetMapping("/excel/leaves")
    @PreAuthorize("hasAuthority('LEAVE:EXPORT')")
    public ResponseEntity<byte[]> leavesExcel(@AuthenticationPrincipal OfficePrincipal principal) {
        return excel("휴가내역_" + LocalDate.now() + ".xlsx", excelService.exportLeaves(principal));
    }

    @GetMapping("/excel/notices")
    @PreAuthorize("hasAuthority('NOTICE:EXPORT')")
    public ResponseEntity<byte[]> noticesExcel(@AuthenticationPrincipal OfficePrincipal principal) {
        return excel("공지사항_" + LocalDate.now() + ".xlsx", excelService.exportNotices(principal));
    }

    @PostMapping(value = "/excel/employees/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EMPLOYEE:IMPORT')")
    @ResponseBody
    public ExcelService.ImportResult importEmployees(@AuthenticationPrincipal OfficePrincipal principal,
                                                      @RequestParam MultipartFile file) {
        return excelService.importEmployees(principal, file);
    }

    private ResponseEntity<byte[]> excel(String filename, byte[] body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
