package com.heibai.clawworld.interfaces.dto;

import lombok.Data;

/**
 * 登出请求DTO
 */
@Data
public class LogoutRequest {
    /**
     * 会话ID
     */
    private String sessionId;
}
