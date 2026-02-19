package com.heibai.clawworld.interfaces.rest;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能代理API代理控制器
 * 用于转发前端的LLM API请求，解决CORS问题
 * 注意：API Key会经过服务器，但不会被存储
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentProxyController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    public static class ProxyRequest {
        private String baseUrl;
        private String apiKey;
        private String model;
        private List<Map<String, String>> messages;
        private Double temperature;
        private Integer maxTokens;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> proxyChat(@RequestBody ProxyRequest request) {
        try {
            String url = request.getBaseUrl() + "/chat/completions";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + request.getApiKey());

            Map<String, Object> body = new HashMap<>();
            body.put("model", request.getModel());
            body.put("messages", request.getMessages());
            body.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
            body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 500);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.info("[AgentProxy] 转发请求到: {}, model: {}", url, request.getModel());
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("[AgentProxy] 代理请求失败: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }
}
