package com.example.office_manager.service;

import com.example.office_manager.common.BusinessException;
import com.example.office_manager.mapper.OfficeMapper;
import com.example.office_manager.security.OfficePrincipal;
import com.example.office_manager.web.dto.ApiDtos;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelService {

    private final OfficeMapper officeMapper;
    private final OfficeService officeService;

    public ExcelService(OfficeMapper officeMapper, OfficeService officeService) {
        this.officeMapper = officeMapper;
        this.officeService = officeService;
    }

    public byte[] exportEmployees(OfficePrincipal principal) {
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("employeeNumber", "사번");
        columns.put("employeeName", "이름");
        columns.put("email", "이메일");
        columns.put("phone", "연락처");
        columns.put("departmentName", "부서");
        columns.put("positionName", "직급");
        columns.put("hireDate", "입사일");
        columns.put("employmentStatus", "재직상태");
        return workbook("사원목록", columns, officeMapper.exportEmployees(principal.companyId()));
    }

    public byte[] exportLeaves(OfficePrincipal principal) {
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("employeeNumber", "사번");
        columns.put("employeeName", "이름");
        columns.put("departmentName", "부서");
        columns.put("leaveTypeName", "휴가종류");
        columns.put("startDate", "시작일");
        columns.put("endDate", "종료일");
        columns.put("requestedDays", "사용일수");
        columns.put("status", "상태");
        columns.put("reason", "사유");
        return workbook("휴가내역", columns, officeMapper.exportLeaves(principal.companyId()));
    }

    public byte[] exportNotices(OfficePrincipal principal) {
        LinkedHashMap<String, String> columns = new LinkedHashMap<>();
        columns.put("title", "제목");
        columns.put("categoryName", "분류");
        columns.put("authorName", "작성자");
        columns.put("viewCount", "조회수");
        columns.put("publishedAt", "게시일시");
        return workbook("공지사항", columns, officeMapper.exportNotices(principal.companyId()));
    }

    public ImportResult importEmployees(OfficePrincipal actor, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("업로드할 엑셀 파일을 선택해 주세요.");
        }
        int total = 0;
        int success = 0;
        List<String> errors = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || value(formatter, row, 0).isBlank()) {
                    continue;
                }
                total++;
                try {
                    String departmentCode = value(formatter, row, 4);
                    String positionCode = value(formatter, row, 5);
                    Long departmentId = officeMapper.findDepartmentIdByCode(actor.companyId(), departmentCode);
                    Long positionId = officeMapper.findPositionIdByCode(actor.companyId(), positionCode);
                    if (departmentId == null || positionId == null) {
                        throw new BusinessException("부서코드 또는 직급코드를 찾을 수 없습니다.");
                    }
                    LocalDate hireDate = date(row.getCell(6), formatter);
                    ApiDtos.EmployeeRequest request = new ApiDtos.EmployeeRequest(
                            value(formatter, row, 0), value(formatter, row, 1),
                            value(formatter, row, 2), value(formatter, row, 3),
                            departmentId, positionId, hireDate,
                            "ACTIVE", "FULL_TIME", "엑셀 일괄 등록",
                            blankToNull(value(formatter, row, 7)),
                            blankToNull(value(formatter, row, 8)),
                            "ROLE_EMPLOYEE");
                    officeService.createEmployee(actor, request);
                    success++;
                } catch (Exception exception) {
                    errors.add((rowIndex + 1) + "행: " + exception.getMessage());
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("엑셀 파일을 읽을 수 없습니다.");
        }
        return new ImportResult(total, success, errors);
    }

    private byte[] workbook(String sheetName, LinkedHashMap<String, String> columns,
                            List<Map<String, Object>> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            int columnIndex = 0;
            for (String title : columns.values()) {
                Cell cell = header.createCell(columnIndex++);
                cell.setCellValue(title);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Map<String, Object> source : rows) {
                Row row = sheet.createRow(rowIndex++);
                columnIndex = 0;
                for (String key : columns.keySet()) {
                    Object data = source.get(key);
                    row.createCell(columnIndex++).setCellValue(data == null ? "" : String.valueOf(data));
                }
            }
            for (int index = 0; index < columns.size(); index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 800, 12000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("엑셀 파일 생성에 실패했습니다.");
        }
    }

    private String value(DataFormatter formatter, Row row, int index) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private LocalDate date(Cell cell, DataFormatter formatter) {
        if (cell != null && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        try {
            return LocalDate.parse(cell == null ? "" : formatter.formatCellValue(cell).trim());
        } catch (Exception exception) {
            throw new BusinessException("입사일은 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record ImportResult(int total, int success, List<String> errors) {
        public int failed() {
            return total - success;
        }
    }
}
