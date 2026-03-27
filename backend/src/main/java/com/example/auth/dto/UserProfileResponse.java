package com.example.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserProfileResponse {
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String avatar;
    private List<String> roles;
    private LocalDateTime createdAt;
}