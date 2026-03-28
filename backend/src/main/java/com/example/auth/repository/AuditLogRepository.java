package com.example.auth.repository;

import com.example.auth.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    Page<AuditLog> findByDateRange(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end,
                                   Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.action = :action AND a.timestamp >= :since")
    long countByActionSince(@Param("action") String action, @Param("since") LocalDateTime since);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.action")
    List<Object[]> getActionStats(@Param("since") LocalDateTime since);
}