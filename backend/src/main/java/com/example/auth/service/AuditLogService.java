package com.example.auth.service;

import com.example.auth.entity.AuditLog;
import com.example.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua_parser.Parser;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Parser userAgentParser = new Parser();

    /**
     * บันทึก Audit Log
     */
    @Transactional
    public void log(String username, String action, String details,
                    HttpServletRequest request, String status, String errorMessage) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Parse user agent
        Map<String, String> deviceInfo = parseUserAgent(userAgent);

        AuditLog auditLog = AuditLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .device(deviceInfo.get("device"))
                .browser(deviceInfo.get("browser"))
                .os(deviceInfo.get("os"))
                .location(getLocationFromIp(ipAddress)) // Optional: GeoIP
                .status(status)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        log.info("Audit log saved: {} - {} - {}", username, action, status);
    }

    /**
     * Login สำเร็จ
     */
    public void logLoginSuccess(String username, HttpServletRequest request) {
        log(username, "LOGIN_SUCCESS", "User logged in successfully",
                request, "SUCCESS", null);
    }

//    /**
//     * Login ล้มเหลว
//     */
//    public void logLoginFailed(String username, String error, HttpServletRequest request) {
//        log(username != null ? username : "unknown", "LOGIN_FAILED",
//                "Login failed: " + error, request, "FAILED", error);
//    }

    /**
     * Logout
     */
    public void logLogout(String username, HttpServletRequest request) {
        log(username, "LOGOUT", "User logged out", request, "SUCCESS", null);
    }

    /**
     * เปลี่ยนรหัสผ่าน
     */
    public void logPasswordChange(String username, HttpServletRequest request) {
        log(username, "PASSWORD_CHANGE", "Password changed", request, "SUCCESS", null);
    }

    /**
     * Admin kick user
     */
    public void logAdminKickUser(String admin, String kickedUser, HttpServletRequest request) {
        log(admin, "ADMIN_KICK_USER", "Admin kicked user: " + kickedUser,
                request, "SUCCESS", null);
    }

    /**
     * Admin อื่นๆ
     */
    public void logAdminAction(String admin, String action, String details, HttpServletRequest request) {
        log(admin, "ADMIN_" + action, details, request, "SUCCESS", null);
    }

    /**
     * Session expired / kicked
     */
    public void logSessionExpired(String username, HttpServletRequest request) {
        log(username, "SESSION_EXPIRED", "Session expired or kicked",
                request, "FAILED", "Session expired");
    }

    /**
     * เรียกใช้จาก JwtAuthenticationFilter เมื่อ token หมดอายุ
     */
    public void logTokenExpired(String username, HttpServletRequest request) {
        if (username != null) {
            log(username, "TOKEN_EXPIRED", "Access token expired",
                    request, "FAILED", "Token expired");
        }
    }

    // ==================== QUERY METHODS ====================

    /**
     * ดึง logs ของ user
     */
    public Page<AuditLog> getUserLogs(String username, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username, pageable);
    }

    /**
     * ดึง logs ตามช่วงเวลา
     */
    public Page<AuditLog> getLogsByDateRange(LocalDateTime start, LocalDateTime end, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findByDateRange(start, end, pageable);
    }

    /**
     * ดึงสถิติการใช้งาน
     */
    public Map<String, Object> getStats(LocalDateTime since) {
        Map<String, Object> stats = new HashMap<>();

        // นับจำนวน login สำเร็จ
        long loginSuccess = auditLogRepository.countByActionSince("LOGIN_SUCCESS", since);
        // นับจำนวน login ล้มเหลว
        long loginFailed = auditLogRepository.countByActionSince("LOGIN_FAILED", since);
        // นับจำนวน logout
        long logout = auditLogRepository.countByActionSince("LOGOUT", since);
        // นับจำนวน password change
        long passwordChange = auditLogRepository.countByActionSince("PASSWORD_CHANGE", since);

        stats.put("loginSuccess", loginSuccess);
        stats.put("loginFailed", loginFailed);
        stats.put("logout", logout);
        stats.put("passwordChange", passwordChange);
        stats.put("totalActions", loginSuccess + loginFailed + logout + passwordChange);

        return stats;
    }

    /**
     * ดึง action stats
     */
    public List<Object[]> getActionStats(LocalDateTime since) {
        return auditLogRepository.getActionStats(since);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * ดึง IP Address ของ client
     */
    private String getClientIp(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress != null ? ipAddress : "unknown";
    }

    /**
     * Parse User Agent
     */
    private Map<String, String> parseUserAgent(String userAgent) {
        Map<String, String> result = new HashMap<>();
        try {
            ua_parser.Client client = userAgentParser.parse(userAgent);
            result.put("device", client.device.family != null ? client.device.family : "Desktop");
            result.put("browser", client.userAgent.family + " " + client.userAgent.major);
            result.put("os", client.os.family + " " + client.os.major);
        } catch (Exception e) {
            result.put("device", "Unknown");
            result.put("browser", "Unknown");
            result.put("os", "Unknown");
        }
        return result;
    }

    /**
     * ดึง location จาก IP (ใช้ GeoIP service)
     */
    private String getLocationFromIp(String ip) {
        // TODO: ใช้ GeoIP API เช่น ipapi.co, ip2location, etc.
        // ตัวอย่าง: https://ipapi.co/{ip}/json/
        return "Unknown";
    }

    /**
     * ดึง logs ทั้งหมด (Admin)
     */
    public Page<AuditLog> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Login ล้มเหลว
     */
    public void logLoginFailed(String username, String error, HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        Map<String, String> deviceInfo = parseUserAgent(userAgent);

        AuditLog auditLog = AuditLog.builder()
                .username(username != null ? username : "unknown")
                .action("LOGIN_FAILED")
                .details("Login failed: " + error)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .device(deviceInfo.get("device"))
                .browser(deviceInfo.get("browser"))
                .os(deviceInfo.get("os"))
                .status("FAILED")
                .errorMessage(error)
                .timestamp(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        log.info("Audit log saved: LOGIN_FAILED for user: {}, IP: {}", username, ipAddress);
    }
}