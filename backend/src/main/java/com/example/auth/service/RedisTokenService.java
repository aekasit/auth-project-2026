package com.example.auth.service;

import com.example.auth.dto.TokenPair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String ACCESS_TOKEN_PREFIX = "auth:access:";
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;
    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;

    public TokenPair storeTokens(String username, 
                                 Collection<? extends GrantedAuthority> authorities,
                                 String deviceInfo) {
        String accessToken = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        
        storeAccessToken(accessToken, username, authorities);
        storeRefreshToken(refreshToken, username, accessToken, deviceInfo);
        
        return new TokenPair(accessToken, refreshToken);
    }

    private void storeAccessToken(String accessToken, String username, 
                                  Collection<? extends GrantedAuthority> authorities) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Map<String, String> data = Map.of(
            "username", username,
            "authorities", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")),
            "createdAt", LocalDateTime.now().toString()
        );
        
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, ACCESS_TOKEN_EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    private void storeRefreshToken(String refreshToken, String username, 
                                   String accessToken, String deviceInfo) {
        String key = REFRESH_TOKEN_PREFIX + refreshToken;
        Map<String, String> data = Map.of(
            "username", username,
            "accessToken", accessToken,
            "deviceInfo", deviceInfo,
            "createdAt", LocalDateTime.now().toString()
        );
        
        redisTemplate.opsForHash().putAll(key, data);
        redisTemplate.expire(key, REFRESH_TOKEN_EXPIRATION_DAYS, TimeUnit.DAYS);
    }

    public boolean isAccessTokenValid(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Optional<UserDetails> getUserDetailsFromAccessToken(String accessToken) {
        String key = ACCESS_TOKEN_PREFIX + accessToken;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        
        if (data.isEmpty()) return Optional.empty();
        
        String username = (String) data.get("username");
        String authoritiesStr = (String) data.get("authorities");
        
        List<GrantedAuthority> authorities = Arrays.stream(authoritiesStr.split(","))
            .map(String::trim)
            .map(SimpleGrantedAuthority::new)
            .toList();
        
        return Optional.of(
            User.builder()
                .username(username)
                .password("")
                .authorities(authorities)
                .build()
        );
    }

    public void invalidateTokens(String accessToken, String refreshToken) {
        if (accessToken != null) {
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + accessToken);
        }
        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }
    }
}
