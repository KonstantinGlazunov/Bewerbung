package com.bewerbung.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Optional endpoint to verify Oracle DB connectivity when profile "oracle" is active.
 */
@RestController
@RequestMapping("/api/db")
@ConditionalOnBean(DataSource.class)
public class OracleHealthController {

    private static final Logger log = LoggerFactory.getLogger(OracleHealthController.class);

    private final JdbcTemplate jdbcTemplate;

    public OracleHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1 FROM DUAL", Integer.class);
            log.debug("Oracle health check: OK");
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "oracle",
                    "check", one != null ? "OK" : "UNKNOWN"
            ));
        } catch (Exception e) {
            log.warn("Oracle health check failed: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/sessions/count")
    public ResponseEntity<Map<String, Object>> sessionsCount() {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM BEWERB_SESSION_DATA", Long.class);
            log.debug("Session records in DB: {}", count);
            return ResponseEntity.ok(Map.of(
                    "sessionsCount", count != null ? count : 0L,
                    "database", "oracle"
            ));
        } catch (Exception e) {
            log.warn("Failed to count sessions: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("sessionsCount", 0L, "database", "oracle", "error", e.getMessage()));
        }
    }
}
