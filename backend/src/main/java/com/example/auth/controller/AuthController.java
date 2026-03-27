package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.service.RedisTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getUsername(),
                    request.getPassword()
                )
            );
            
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            
            TokenPair tokenPair = tokenService.storeTokens(
                userDetails.getUsername(),
                userDetails.getAuthorities(),
                request.getDeviceInfo() != null ? request.getDeviceInfo() : "web"
            );
            
            setAccessTokenCookie(response, tokenPair.getAccessToken());
            setRefreshTokenCookie(response, tokenPair.getRefreshToken());
            
            var roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(
                LoginResponse.success(userDetails.getUsername(), roles, 15 * 60)
            );
            
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.status(401)
                .body(LoginResponse.failed("Invalid credentials"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractTokenFromCookie(request, "refresh_token");
        
        if (refreshToken == null || !tokenService.isRefreshTokenValid(refreshToken)) {
            return ResponseEntity.status(401)
                .body(RefreshResponse.failed("Invalid refresh token"));
        }
        
        // Generate new access token (simplified)
        String newAccessToken = UUID.randomUUID().toString().replace("-", "");
        setAccessTokenCookie(response, newAccessToken);
        
        return ResponseEntity.ok(RefreshResponse.success(15 * 60));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String accessToken = extractTokenFromCookie(request, "access_token");
        String refreshToken = extractTokenFromCookie(request, "refresh_token");
        
        if (accessToken != null && refreshToken != null) {
            tokenService.invalidateTokens(accessToken, refreshToken);
        }
        
        removeAccessTokenCookie(response);
        removeRefreshTokenCookie(response);
        
        return ResponseEntity.ok("Logged out successfully");
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
            .httpOnly(true)
            .secure(false)
            .path("/")
            .maxAge(Duration.ofMinutes(15))
            .sameSite("Lax")
            .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
            .httpOnly(true)
            .secure(false)
            .path("/api/auth/refresh")
            .maxAge(Duration.ofDays(7))
            .sameSite("Lax")
            .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void removeAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
            .httpOnly(true)
            .path("/")
            .maxAge(0)
            .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void removeRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .path("/api/auth/refresh")
            .maxAge(0)
            .build();
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractTokenFromCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
        }
        return null;
    }
}
