package com.example.auth.dto;

public record RefreshResponse(boolean success, String message, Long expiresIn) {
    public static RefreshResponse success(long expiresIn) {
        return new RefreshResponse(true, "Tokens refreshed successfully", expiresIn);
    }
    
    public static RefreshResponse failed(String message) {
        return new RefreshResponse(false, message, null);
    }
}
