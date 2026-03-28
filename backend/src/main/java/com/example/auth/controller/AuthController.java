package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.service.AuditLogService;
import com.example.auth.service.RedisTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final RedisTokenService tokenService;
    private final AuditLogService auditLogService;

    // ==================== COOKIE CONSTANTS ====================
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String ACCESS_TOKEN_PATH = "/";
//    private static final String REFRESH_TOKEN_PATH = "/api/auth/refresh";

    private static final long ACCESS_TOKEN_MAX_AGE_SECONDS = 15 * 60;      // 15 minutes
    private static final long REFRESH_TOKEN_MAX_AGE_SECONDS = 7 * 24 * 60 * 60; // 7 days

    @Value("${app.cookie.same-site}")
    private String sameSite;

    @Value("${app.cookie.secure}")
    private boolean secure;

    // ==================== AUTH ENDPOINTS ====================

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            TokenPair tokenPair = tokenService.storeTokens(
                    userDetails.getUsername(),
                    userDetails.getAuthorities(),
                    request.deviceInfo() != null ? request.deviceInfo() : "web"
            );

            // Set cookies
            setAccessTokenCookie(response, tokenPair.accessToken());
            setRefreshTokenCookie(response, tokenPair.refreshToken());

            // 🔥 บันทึก log login สำเร็จ
            auditLogService.logLoginSuccess(userDetails.getUsername(), httpRequest);

            var roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    LoginResponse.success(userDetails.getUsername(), roles, ACCESS_TOKEN_MAX_AGE_SECONDS)
            );

        } catch (BadCredentialsException e) {
            // ✅ บันทึก LOGIN_FAILED
            auditLogService.logLoginFailed(request.username(), "Invalid credentials", httpRequest);

            return ResponseEntity.status(401)
                    .body(LoginResponse.failed("Invalid credentials"));
        } catch (Exception e) {
            // ✅ บันทึก LOGIN_FAILED อื่นๆ
            auditLogService.logLoginFailed(request.username(), e.getMessage(), httpRequest);

            return ResponseEntity.status(401)
                    .body(LoginResponse.failed("Login failed"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractTokenFromCookie(request, REFRESH_TOKEN_COOKIE);

        log.info("=== REFRESH TOKEN REQUEST ===");
        log.info("Refresh token from cookie: {}", refreshToken != null ? refreshToken.substring(0, 8) + "..." : "null");

        if (refreshToken == null || !tokenService.isRefreshTokenValid(refreshToken)) {
            log.warn("Invalid refresh token");
            return ResponseEntity.status(401)
                    .body(RefreshResponse.failed("Invalid refresh token"));
        }

        log.info("Refresh token is valid");

        String username = tokenService.getUsernameFromRefreshToken(refreshToken);

        log.info("Username from refresh token: {}", username);

        if (username == null) {
            return ResponseEntity.status(401)
                    .body(RefreshResponse.failed("User not found"));
        }

        // Check if user still has active session
        if (!tokenService.isUserHasActiveSession(username)) {
            return ResponseEntity.status(401)
                    .body(RefreshResponse.failed("Session expired. Please login again"));
        }

        // Generate new access token
        String newAccessToken = tokenService.refreshAccessToken(refreshToken);
        if (newAccessToken == null) {
            return ResponseEntity.status(401)
                    .body(RefreshResponse.failed("Failed to refresh token"));
        }

        log.info("New access token generated: {}", newAccessToken != null ? newAccessToken.substring(0, 8) + "..." : "null");

        setAccessTokenCookie(response, newAccessToken);

        log.info("Refresh successful for user: {}", username);

        return ResponseEntity.ok(RefreshResponse.success(ACCESS_TOKEN_MAX_AGE_SECONDS));
    }

// backend/src/main/java/com/example/auth/controller/AuthController.java

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal UserDetails userDetails) {

        String accessToken = extractTokenFromCookie(request, "access_token");
        String refreshToken = extractTokenFromCookie(request, "refresh_token");

        if (accessToken != null && refreshToken != null) {
            tokenService.invalidateTokens(accessToken, refreshToken);
        }

        // ✅ บันทึก LOGOUT
        if (userDetails != null) {
            auditLogService.logLogout(userDetails.getUsername(), request);
            log.info("User logged out: {}", userDetails.getUsername());
        } else {
            // ถ้าไม่มี userDetails ให้ลองดึงจาก token
            if (accessToken != null) {
                String username = tokenService.getUsernameFromAccessToken(accessToken);
                if (username != null) {
                    auditLogService.logLogout(username, request);
                }
            }
        }

        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);

        return ResponseEntity.ok("Logged out successfully");
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(secure)                    // environment-based
                .path(ACCESS_TOKEN_PATH)
                .maxAge(Duration.ofSeconds(ACCESS_TOKEN_MAX_AGE_SECONDS))
                .sameSite(sameSite)                // environment-based
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(secure)
                .path(ACCESS_TOKEN_PATH)  // เฉพาะ path นี้
                .maxAge(Duration.ofSeconds(REFRESH_TOKEN_MAX_AGE_SECONDS))
                .sameSite(sameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .path(ACCESS_TOKEN_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        // Clear from refresh path
        ResponseCookie cookie1 = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path(ACCESS_TOKEN_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie1.toString());

        // Also clear from root path as fallback
        ResponseCookie cookie2 = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path(ACCESS_TOKEN_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie2.toString());
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)          // Set to true in production with HTTPS
                .path(path)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .sameSite("Strict")
                .build();
    }

    // ==================== UTILITY METHODS ====================

    private String extractTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(cookie -> cookieName.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}