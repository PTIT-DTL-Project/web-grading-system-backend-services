package vn.edu.ptit.web_grading_system.user_service.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.web_grading_system.user_service.dto.request.CreateUserRequest;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ImportResultResponse;
import vn.edu.ptit.web_grading_system.user_service.dto.response.ImportResultResponse.ImportError;
import vn.edu.ptit.web_grading_system.user_service.exception.BadRequestException;

import java.io.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final UserAdminService userAdminService;

    /**
     * Imports users from a CSV or Excel file.
     *
     * @param file               uploaded CSV (.csv) or Excel (.xlsx) file
     * @param role               Keycloak role to assign: ROLE_STUDENT or ROLE_LECTURER
     * @param passwordMode       "STUDENT_CODE" or "CUSTOM"
     * @param defaultPassword    used when passwordMode=CUSTOM; ignored otherwise
     * @param forcePasswordChange whether to set Keycloak UPDATE_PASSWORD required action
     * @return ImportResultResponse with counts and per-row errors
     */
    public ImportResultResponse importUsers(MultipartFile file, String role, String passwordMode,
                                            String defaultPassword, boolean forcePasswordChange) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        List<Map<String, String>> rows;
        try {
            if (filename.endsWith(".csv")) {
                rows = parseCsv(file.getInputStream());
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                rows = parseExcel(file.getInputStream());
            } else {
                throw new BadRequestException("Unsupported file format. Use .csv or .xlsx");
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to read import file: " + e.getMessage());
        }

        int success = 0;
        List<ImportError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNum = i + 2; // 1-indexed, row 1 is header
            Map<String, String> row = rows.get(i);
            try {
                CreateUserRequest request = buildCreateUserRequest(row, role, passwordMode,
                        defaultPassword, forcePasswordChange);
                userAdminService.createUser(request);
                success++;
            } catch (Exception e) {
                log.warn("Import row {} failed: {}", rowNum, e.getMessage());
                errors.add(ImportError.builder().row(rowNum).reason(e.getMessage()).build());
            }
        }

        return ImportResultResponse.builder()
                .total(rows.size())
                .success(success)
                .failed(errors.size())
                .errors(errors)
                .build();
    }

    /** Parses CSV, first row is header. Returns list of maps {columnName → value}. */
    private List<Map<String, String>> parseCsv(InputStream inputStream) throws IOException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) return List.of();
            String[] headers = allRows.get(0);
            List<Map<String, String>> result = new ArrayList<>();
            for (int i = 1; i < allRows.size(); i++) {
                String[] values = allRows.get(i);
                Map<String, String> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    row.put(headers[j].trim(), j < values.length ? values[j].trim() : "");
                }
                result.add(row);
            }
            return result;
        } catch (CsvException e) {
            throw new IOException("CSV parse error: " + e.getMessage());
        }
    }

    /** Parses first sheet of an Excel file. First row is header. */
    private List<Map<String, String>> parseExcel(InputStream inputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return List.of();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return List.of();

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(cell.getStringCellValue().trim());
            }

            List<Map<String, String>> result = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    map.put(headers.get(j), cell == null ? "" : getCellValue(cell));
                }
                result.add(map);
            }
            return result;
        }
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private CreateUserRequest buildCreateUserRequest(Map<String, String> row, String role,
                                                      String passwordMode, String defaultPassword,
                                                      boolean forcePasswordChange) {
        String studentCode = row.get("studentCode");
        String username    = row.get("username");
        String email       = row.get("email");

        if (isBlank(username)) throw new BadRequestException("username is required");
        if (isBlank(email))    throw new BadRequestException("email is required");

        String password;
        boolean force = forcePasswordChange;
        if ("STUDENT_CODE".equals(passwordMode)) {
            if (isBlank(studentCode)) {
                throw new BadRequestException("studentCode is required when passwordMode=STUDENT_CODE");
            }
            password = studentCode;
            force = true; // always force when using student code
        } else if ("CUSTOM".equals(passwordMode)) {
            if (isBlank(defaultPassword)) {
                throw new BadRequestException("defaultPassword is required when passwordMode=CUSTOM");
            }
            password = defaultPassword;
        } else {
            throw new BadRequestException("passwordMode must be STUDENT_CODE or CUSTOM");
        }

        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setFirstName(nvl(row.get("firstName")));
        request.setLastName(nvl(row.get("lastName")));
        request.setPassword(password);
        request.setForcePasswordChange(force);
        request.setRole(role);
        // Student fields
        request.setStudentCode(row.get("studentCode"));
        request.setDepartment(row.get("department"));
        request.setBatch(row.get("batch"));
        request.setProgram(row.get("program"));
        request.setClassCode(row.get("classCode"));
        // Lecturer fields
        request.setStaffCode(row.get("staffCode"));
        request.setTitle(row.get("title"));
        // Shared
        request.setPhoneNumber(row.get("phone"));
        request.setGender(row.get("gender"));
        return request;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String nvl(String s)      { return s != null ? s : ""; }
}
