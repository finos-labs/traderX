package finos.traderx.positionservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthCheckController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "position-service");
        response.put("status", "UP");
        
        try {
            // Test database connection
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            response.put("database", "UP");
        } catch (Exception e) {
            response.put("database", "DOWN");
            response.put("error", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }

        // Add additional health metrics
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
