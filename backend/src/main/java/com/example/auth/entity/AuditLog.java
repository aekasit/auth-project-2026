package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_username", columnList = "username"),
        @Index(name = "idx_action", columnList = "action"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(nullable = false)
    private String ipAddress;

    private String userAgent;

    private String device;

    private String browser;

    private String os;

    private String location;

    private String status; // SUCCESS, FAILED

    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}