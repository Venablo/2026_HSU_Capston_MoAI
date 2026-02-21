package com.moai.backend.domain.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthCheckController {

    @GetMapping(value = "/health", produces = "application/json; charset=UTF-8")
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "message", "MoAI 서버가 정상적으로 작동 중입니다!");
    }
}