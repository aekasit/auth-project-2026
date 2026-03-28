package com.example.auth.controller;

import com.example.auth.entity.AuditLog;
import com.example.auth.repository.AuditLogRepository;
import com.example.auth.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;

    /**
     * ดึง logs ของ user เฉพาะ
     */
    @GetMapping("/user/{username}")
    @PreAuthorize("hasRole('ADMIN') or #username == authentication.name")
    public ResponseEntity<Page<AuditLog>> getUserLogs(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogService.getUserLogs(username, page, size));
    }

    /**
     * ดึง logs ทั้งหมด (Admin เท่านั้น)
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        Page<AuditLog> logs;

        if (username != null && !username.isEmpty()) {
            logs = auditLogService.getUserLogs(username, page, size);
        } else if (startDate != null && endDate != null) {
            logs = auditLogService.getLogsByDateRange(startDate, endDate, page, size);
        } else {
            logs = auditLogService.getAllLogs(page, size);
        }

        return ResponseEntity.ok(logs);
    }

    /**
     * ดึง logs ตามช่วงเวลา
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditLogService.getLogsByDateRange(start, end, page, size));
    }

    /**
     * ดึงสถิติ (รับ parameter แบบ string แล้วแปลงเอง)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(required = false) String since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        LocalDateTime sinceDate;

        // แปลง since string เป็น LocalDateTime
        if (since != null) {
            sinceDate = parseSinceParameter(since);
        } else if (startDate != null && endDate != null) {
            sinceDate = startDate;
        } else {
            // Default: last 7 days
            sinceDate = LocalDateTime.now().minusDays(7);
        }

        Map<String, Object> stats = auditLogService.getStats(sinceDate);

        // เพิ่มข้อมูลช่วงเวลา
        stats.put("since", sinceDate);
        stats.put("until", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }

    /**
     * แปลง since parameter (support: 7d, 24h, 30m, 2024-01-01)
     */
    private LocalDateTime parseSinceParameter(String since) {
        if (since == null) {
            return LocalDateTime.now().minusDays(7);
        }

        try {
            // ถ้าเป็นรูปแบบวันที่
            if (since.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDateTime.parse(since + "T00:00:00");
            }

            // ถ้าเป็นรูปแบบ 7d, 24h, 30m
            if (since.matches("\\d+[dhms]")) {
                int value = Integer.parseInt(since.substring(0, since.length() - 1));
                char unit = since.charAt(since.length() - 1);

                return switch (unit) {
                    case 'd' -> LocalDateTime.now().minusDays(value);
                    case 'h' -> LocalDateTime.now().minusHours(value);
                    case 'm' -> LocalDateTime.now().minusMinutes(value);
                    case 's' -> LocalDateTime.now().minusSeconds(value);
                    default -> LocalDateTime.now().minusDays(7);
                };
            }
        } catch (Exception e) {
            log.warn("Invalid since parameter: {}, using default 7 days", since);
        }

        return LocalDateTime.now().minusDays(7);
    }

    @GetMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testCreateLog() {
        // สร้าง test log
        AuditLog testLog = AuditLog.builder()
                .username("test_user")
                .action("TEST")
                .details("Test log entry")
                .ipAddress("127.0.0.1")
                .device("Desktop")
                .browser("Chrome")
                .os("Windows")
                .status("SUCCESS")
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(testLog);
        return ResponseEntity.ok("Test log created");
    }
}