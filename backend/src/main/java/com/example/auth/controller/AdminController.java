// backend/src/main/java/com/example/auth/controller/AdminController.java
package com.example.auth.controller;

import com.example.auth.service.AuditLogService;
import com.example.auth.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final RedisTokenService tokenService;
    private final AuditLogService auditLogService;  // 🔥 เพิ่ม

    /**
     * ดูรายชื่อผู้ใช้ออนไลน์ทั้งหมด
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getActiveSessions() {
        List<Map<String, Object>> sessions = tokenService.getAllActiveSessionsWithDetails();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Kick user ออกจากระบบ (เวอร์ชันเดียว รวม audit log)
     */
    @PostMapping("/kick/{username}")
    public ResponseEntity<Map<String, Object>> kickUser(
            @PathVariable String username,
            @AuthenticationPrincipal UserDetails admin,
            HttpServletRequest request) {

        boolean kicked = tokenService.kickUserSession(username);

        if (kicked) {
            // บันทึก log admin kick
            auditLogService.logAdminKickUser(admin.getUsername(), username, request);
            log.info("Admin {} kicked user: {}", admin.getUsername(), username);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", kicked);
        response.put("message", kicked ? "User kicked successfully" : "User not found or already offline");
        response.put("username", username);

        return ResponseEntity.ok(response);
    }

    /**
     * Kick user ออกจากระบบทั้งหมด (ยกเว้นตัวเอง)
     */
    @PostMapping("/kick-all-except-me")
    public ResponseEntity<Map<String, Object>> kickAllExceptMe(
            @RequestParam String currentUser,
            @AuthenticationPrincipal UserDetails admin,
            HttpServletRequest request) {

        int kickedCount = tokenService.kickAllUsersExcept(currentUser);

        // บันทึก log admin kick all
        auditLogService.logAdminAction(admin.getUsername(), "KICK_ALL",
                "Kicked " + kickedCount + " users", request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Kicked " + kickedCount + " users");
        response.put("kickedCount", kickedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * ดูสถิติออนไลน์
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        int totalSessions = tokenService.getTotalActiveSessions();
        List<String> allUsers = tokenService.getAllActiveUsernames();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOnline", totalSessions);
        stats.put("users", allUsers);
        stats.put("timestamp", new Date());

        return ResponseEntity.ok(stats);
    }
}