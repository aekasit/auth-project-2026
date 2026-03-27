package com.example.auth.security;

import com.example.auth.service.RedisTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final RedisTokenService tokenService;

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/refresh"
    };

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();

        // Skip public paths
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessToken = extractTokenFromCookie(request, ACCESS_TOKEN_COOKIE);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Validate token
        if (!tokenService.isAccessTokenValid(accessToken)) {
            clearInvalidTokens(response);
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        String username = tokenService.getUsernameFromAccessToken(accessToken);

        if (username == null) {
            clearInvalidTokens(response);
            sendUnauthorized(response, "Invalid token data");
            return;
        }

        // Check if this is the active session
        String activeToken = tokenService.getActiveAccessToken(username);

        if (activeToken == null || !activeToken.equals(accessToken)) {
            log.warn("Non-active session attempt for user: {}", username);
            clearInvalidTokens(response);
            sendUnauthorized(response, "Session expired - logged in from another device");
            return;
        }

        // Authenticate
        UserDetails userDetails = tokenService.getUserDetailsFromAccessToken(accessToken);

        if (userDetails != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
            authentication.setDetails(request);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Update last active
            tokenService.updateLastActive(accessToken);
            log.debug("Authenticated user: {}", username);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return Arrays.stream(PUBLIC_PATHS).anyMatch(path::startsWith);
    }

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

    private void clearInvalidTokens(HttpServletResponse response) {
        String clearAccessToken = "access_token=; HttpOnly; Path=/; Max-Age=0; SameSite=Strict";
        String clearRefreshToken = "refresh_token=; HttpOnly; Path=/api/auth/refresh; Max-Age=0; SameSite=Strict";
        response.setHeader("Set-Cookie", clearAccessToken);
        response.setHeader("Set-Cookie", clearRefreshToken);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\"}",
                message
        ));
    }
}