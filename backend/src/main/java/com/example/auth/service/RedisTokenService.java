package com.example.auth.service;

import com.example.auth.dto.TokenPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== REDIS KEY CONSTANTS ====================
    private static final String ACCESS_TOKEN_PREFIX = "auth:access:";
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final String USER_SESSION_PREFIX = "auth:user:session:";
    private static final String USER_ACTIVE_TOKEN_PREFIX = "auth:user:active:";
    private static final String ACCESS_REFRESH_MAPPING = "auth:access:refresh:";

    // ==================== EXPIRATION CONSTANTS ====================
//    private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;
//    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;

    // ==================== TOKEN STORAGE ====================

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refresTtokenExpiration;


    /**
     * Store both access and refresh tokens for a user
     */
    public TokenPair storeTokens(String username,
                                 Collection<? extends GrantedAuthority> authorities,
                                 String deviceInfo) {
        log.info("Storing tokens for user: {}, device: {}", username, deviceInfo);

        // Kick existing session if any
        kickExistingSession(username);

        // Generate new tokens
        String accessToken = generateSecureToken();
        String refreshToken = generateSecureToken();

        // Store tokens
        storeAccessToken(accessToken, username, authorities);
        storeRefreshToken(refreshToken, username, accessToken, deviceInfo, authorities);
        storeUserSession(username, accessToken, refreshToken, deviceInfo);
        storeActiveToken(username, accessToken);

        log.info("Tokens stored successfully for user: {}", username);
        return new TokenPair(accessToken, refreshToken);
    }

    private void storeAccessToken(String accessToken, String username,
                                  Collection<? extends GrantedAuthority> authorities) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Map<String, String> data = new HashMap<>();
        data.put("username", username);
        data.put("authorities", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")));
        data.put("type", "ACCESS");
        data.put("createdAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, Duration.ofMinutes(accessTokenExpiration));

        log.debug("Access token stored: {}", maskToken(accessToken));
    }

    private void storeRefreshToken(String refreshToken, String username,
                                   String accessToken, String deviceInfo,
                                   Collection<? extends GrantedAuthority> authorities) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        Map<String, String> data = new HashMap<>();
        data.put("username", username);
        data.put("accessToken", accessToken);
        data.put("deviceInfo", deviceInfo);
        data.put("authorities", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")));
        data.put("type", "REFRESH");
        data.put("createdAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, Duration.ofDays(refresTtokenExpiration));

        // Store mapping for quick lookup
        String mappingKey = ACCESS_REFRESH_MAPPING + accessToken;
        redisTemplate.opsForValue().set(mappingKey, refreshToken,
                Duration.ofDays(refresTtokenExpiration));

        log.debug("Refresh token stored: {}", maskToken(refreshToken));
    }

    private void storeUserSession(String username, String accessToken,
                                  String refreshToken, String deviceInfo) {
        String key = USER_SESSION_PREFIX + username;
        Map<String, String> data = new HashMap<>();
        data.put("username", username);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("deviceInfo", deviceInfo);
        data.put("loginTime", LocalDateTime.now().toString());
        data.put("lastActive", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, Duration.ofDays(refresTtokenExpiration));

        log.debug("User session stored: {}", username);
    }

    private void storeActiveToken(String username, String accessToken) {
        String key = USER_ACTIVE_TOKEN_PREFIX + username;
        redisTemplate.opsForValue().set(key, accessToken,
                Duration.ofDays(refresTtokenExpiration));

        log.debug("Active token stored for user: {}", username);
    }

    private void kickExistingSession(String username) {
        String activeTokenKey = USER_ACTIVE_TOKEN_PREFIX + username;
        String oldAccessToken = (String) redisTemplate.opsForValue().get(activeTokenKey);

        if (oldAccessToken != null) {
            String oldRefreshToken = getRefreshTokenFromAccessToken(oldAccessToken);
            if (oldRefreshToken != null) {
                invalidateTokens(oldAccessToken, oldRefreshToken);
                log.info("Kicked existing session for user: {}", username);
            }
        }
    }

    // ==================== TOKEN VALIDATION ====================

    public boolean isAccessTokenValid(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) {
            return false;
        }
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean isRefreshTokenValid(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return false;
        }
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public boolean isUserHasActiveSession(String username) {
        String key = USER_ACTIVE_TOKEN_PREFIX + username;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ==================== TOKEN DATA RETRIEVAL ====================

    public String getUsernameFromAccessToken(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        return data.isEmpty() ? null : (String) data.get("username");
    }

    public String getUsernameFromRefreshToken(String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        return data.isEmpty() ? null : (String) data.get("username");
    }

    public String getActiveAccessToken(String username) {
        String key = USER_ACTIVE_TOKEN_PREFIX + username;
        return (String) redisTemplate.opsForValue().get(key);
    }

    private String getRefreshTokenFromAccessToken(String accessToken) {
        String mappingKey = ACCESS_REFRESH_MAPPING + accessToken;
        return (String) redisTemplate.opsForValue().get(mappingKey);
    }

    public UserDetails getUserDetailsFromAccessToken(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

        if (data.isEmpty()) {
            return null;
        }

        String username = (String) data.get("username");
        String authoritiesStr = (String) data.get("authorities");

        if (username == null || authoritiesStr == null) {
            return null;
        }

        List<GrantedAuthority> authorities = Arrays.stream(authoritiesStr.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return User.builder()
                .username(username)
                .password("")
                .authorities(authorities)
                .build();
    }

    public Map<String, Object> getUserSessionInfo(String username) {
        String key = USER_SESSION_PREFIX + username;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

        Map<String, Object> result = new HashMap<>();
        if (!data.isEmpty()) {
            result.put("username", data.get("username"));
            result.put("deviceInfo", data.get("deviceInfo"));
            result.put("loginTime", data.get("loginTime"));
            result.put("lastActive", data.get("lastActive"));
            result.put("hasActiveSession", true);
        } else {
            result.put("hasActiveSession", false);
        }
        return result;
    }

    // ==================== TOKEN REFRESH ====================

    public String refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token for refresh token: {}", maskToken(refreshToken));

        String refreshKey = REFRESH_TOKEN_PREFIX + refreshToken;
        Map<Object, Object> refreshData = redisTemplate.opsForHash().entries(refreshKey);

        if (refreshData.isEmpty()) {
            log.warn("Refresh token not found in Redis: {}", maskToken(refreshToken));
            return null;
        }

        String username = (String) refreshData.get("username");
        String deviceInfo = (String) refreshData.get("deviceInfo");

        // Get authorities from refresh token (stored during login)
        String authoritiesStr = (String) refreshData.get("authorities");
        List<GrantedAuthority> authorities;

        if (authoritiesStr != null && !authoritiesStr.isEmpty()) {
            authorities = Arrays.stream(authoritiesStr.split(","))
                    .map(String::trim)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            log.debug("Using authorities from refresh token: {}", authoritiesStr);
        } else {
            // Fallback to default if no authorities found
            log.warn("No authorities in refresh token, using default");
            authorities = getDefaultAuthorities();
        }

        // Generate new access token
        String newAccessToken = generateSecureToken();

        // Store new access token
        storeAccessToken(newAccessToken, username, authorities);

        // Update refresh token with new access token
        refreshData.put("accessToken", newAccessToken);
        refreshData.put("lastRefreshed", LocalDateTime.now().toString());
        redisTemplate.opsForHash().putAll(refreshKey, refreshData);

        // Update user session
        String sessionKey = USER_SESSION_PREFIX + username;
        redisTemplate.opsForHash().put(sessionKey, "accessToken", newAccessToken);
        redisTemplate.opsForHash().put(sessionKey, "lastActive", LocalDateTime.now().toString());
        redisTemplate.expire(sessionKey, Duration.ofDays(refresTtokenExpiration));

        // Update active token
        redisTemplate.opsForValue().set(
                USER_ACTIVE_TOKEN_PREFIX + username,
                newAccessToken,
                Duration.ofDays(refresTtokenExpiration)
        );

        // Update mapping
        String mappingKey = ACCESS_REFRESH_MAPPING + newAccessToken;
        redisTemplate.opsForValue().set(mappingKey, refreshToken,
                Duration.ofDays(refresTtokenExpiration));

        // Delete old mapping (if exists)
        String oldAccessToken = (String) refreshData.get("accessToken");
        if (oldAccessToken != null && !oldAccessToken.equals(newAccessToken)) {
            redisTemplate.delete(ACCESS_REFRESH_MAPPING + oldAccessToken);
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + oldAccessToken);
        }

        log.info("Access token refreshed successfully for user: {}", username);
        return newAccessToken;
    }

    // ==================== TOKEN INVALIDATION ====================

    public void invalidateTokens(String accessToken, String refreshToken) {
        log.info("Invalidating tokens");

        if (accessToken != null) {
            String username = getUsernameFromAccessToken(accessToken);
            if (username != null) {
                // Clean up user data
                redisTemplate.delete(USER_ACTIVE_TOKEN_PREFIX + username);
                redisTemplate.delete(USER_SESSION_PREFIX + username);
                redisTemplate.delete(ACCESS_REFRESH_MAPPING + accessToken);
            }
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + accessToken);
            log.debug("Access token invalidated: {}", maskToken(accessToken));
        }

        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
            log.debug("Refresh token invalidated: {}", maskToken(refreshToken));
        }
    }

    /**
     * Kick user session
     *
     * @return true if user was kicked, false if user not found or already offline
     */
    public boolean kickUserSession(String username) {
        log.info("Kicking user session: {}", username);

        String activeToken = getActiveAccessToken(username);
        if (activeToken == null) {
            log.warn("User {} has no active session", username);
            return false;
        }

        String refreshToken = getRefreshTokenFromAccessToken(activeToken);
        if (refreshToken != null) {
            invalidateTokens(activeToken, refreshToken);
            log.info("Successfully kicked user: {}", username);
            return true;
        }

        // Fallback: ลบเฉพาะ access token
        invalidateTokens(activeToken, null);
        log.info("Kicked user (no refresh token): {}", username);
        return true;
    }

    public void invalidateAllUserTokens(String username) {
        log.info("Invalidating all tokens for user: {}", username);
        kickUserSession(username);
    }

    // ==================== SESSION MANAGEMENT ====================

    public void updateLastActive(String accessToken) {
        String username = getUsernameFromAccessToken(accessToken);
        if (username != null) {
            String key = USER_SESSION_PREFIX + username;
            redisTemplate.opsForHash().put(key, "lastActive", LocalDateTime.now().toString());
            redisTemplate.expire(key, Duration.ofDays(refresTtokenExpiration));
            log.debug("Updated last active for user: {}", username);
        }
    }

    public int getActiveSessionCount(String username) {
        String key = USER_ACTIVE_TOKEN_PREFIX + username;
        String activeToken = (String) redisTemplate.opsForValue().get(key);
        return activeToken != null ? 1 : 0;
    }

    public Set<String> getAllActiveSessions() {
        Set<String> keys = redisTemplate.keys(USER_ACTIVE_TOKEN_PREFIX + "*");
        Set<String> sessions = new HashSet<>();
        if (keys != null) {
            for (String key : keys) {
                String username = key.substring(USER_ACTIVE_TOKEN_PREFIX.length());
                sessions.add(username);
            }
        }
        return sessions;
    }

    // ==================== UTILITY METHODS ====================

    private String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "null";
        }
        return token.substring(0, 8) + "...";
    }

    private List<GrantedAuthority> getDefaultAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // ==================== DEBUG METHODS ====================

    public void printAllSessions() {
        Set<String> keys = redisTemplate.keys(USER_ACTIVE_TOKEN_PREFIX + "*");
        log.info("=== Active Sessions ===");
        if (keys == null || keys.isEmpty()) {
            log.info("No active sessions");
            return;
        }
        for (String key : keys) {
            String username = key.substring(USER_ACTIVE_TOKEN_PREFIX.length());
            String accessToken = (String) redisTemplate.opsForValue().get(key);
            log.info("User: {} -> Active token: {}", username, maskToken(accessToken));
        }
    }

    public Set<String> getAllRefreshKeys() {
        Set<String> keys = redisTemplate.keys(REFRESH_TOKEN_PREFIX + "*");
        log.debug("All refresh keys: {}", keys);
        return keys != null ? keys : Collections.emptySet();
    }

    // backend/src/main/java/com/example/auth/service/RedisTokenService.java

// ==================== ADMIN MANAGEMENT ====================

    /**
     * ดึงข้อมูล sessions ทั้งหมดพร้อมรายละเอียด
     */
    public List<Map<String, Object>> getAllActiveSessionsWithDetails() {
        Set<String> keys = redisTemplate.keys(USER_ACTIVE_TOKEN_PREFIX + "*");
        List<Map<String, Object>> sessions = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                String username = key.substring(USER_ACTIVE_TOKEN_PREFIX.length());
                Map<String, Object> sessionInfo = getUserSessionInfo(username);

                if (!sessionInfo.isEmpty() && Boolean.TRUE.equals(sessionInfo.get("hasActiveSession"))) {
                    sessionInfo.put("username", username);

                    // เพิ่มข้อมูลเพิ่มเติม
                    String accessToken = getActiveAccessToken(username);
                    sessionInfo.put("accessToken", maskToken(accessToken));
                    sessionInfo.put("onlineTime", calculateOnlineTime((String) sessionInfo.get("loginTime")));

                    sessions.add(sessionInfo);
                }
            }
        }

        // เรียงตาม lastActive ล่าสุด
        sessions.sort((a, b) -> {
            String dateA = (String) a.get("lastActive");
            String dateB = (String) b.get("lastActive");
            if (dateA == null || dateB == null) return 0;
            return dateB.compareTo(dateA);
        });

        return sessions;
    }

    /**
     * ดึงรายชื่อผู้ใช้ออนไลน์ทั้งหมด
     */
    public List<String> getAllActiveUsernames() {
        Set<String> keys = redisTemplate.keys(USER_ACTIVE_TOKEN_PREFIX + "*");
        List<String> usernames = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                String username = key.substring(USER_ACTIVE_TOKEN_PREFIX.length());
                usernames.add(username);
            }
        }
        return usernames;
    }

    /**
     * นับจำนวนผู้ใช้ออนไลน์
     */
    public int getTotalActiveSessions() {
        Set<String> keys = redisTemplate.keys(USER_ACTIVE_TOKEN_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    /**
     * Kick user ทั้งหมดยกเว้นตัวเอง
     */
    public int kickAllUsersExcept(String currentUser) {
        List<String> allUsers = getAllActiveUsernames();
        int kickedCount = 0;

        for (String username : allUsers) {
            if (!username.equals(currentUser)) {
                kickUserSession(username);
                kickedCount++;
                log.info("Admin kicked user: {}", username);
            }
        }

        return kickedCount;
    }

    /**
     * คำนวณเวลาที่ออนไลน์
     */
    private String calculateOnlineTime(String loginTimeStr) {
        if (loginTimeStr == null) return "Unknown";
        try {
            LocalDateTime loginTime = LocalDateTime.parse(loginTimeStr);
            LocalDateTime now = LocalDateTime.now();
            Duration duration = Duration.between(loginTime, now);

            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();

            if (hours > 0) {
                return hours + "h " + minutes + "m";
            } else if (minutes > 0) {
                return minutes + "m";
            } else {
                return "Just now";
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }
}