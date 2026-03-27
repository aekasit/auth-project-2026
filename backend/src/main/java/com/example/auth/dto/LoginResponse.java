package com.example.auth.dto;

import java.util.List;

public record LoginResponse(
    boolean success,
    String message,
    String username,
    List<String> roles,
    Long expiresIn
) {
    public static LoginResponse success(String username, List<String> roles, long expiresIn) {
        return new LoginResponse(true, "Login successful", username, roles, expiresIn);
    }
    
    public static LoginResponse failed(String message) {
        return new LoginResponse(false, message, null, null, null);
    }
}
