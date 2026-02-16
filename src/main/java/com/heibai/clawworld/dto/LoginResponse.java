package com.heibai.clawworld.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private String message;
    private String sessionId;
    private String backgroundPrompt;

    public static LoginResponse success(String sessionId, String backgroundPrompt) {
        return new LoginResponse(true, "登录成功", sessionId, backgroundPrompt);
    }

    public static LoginResponse error(String message) {
        return new LoginResponse(false, message, null, null);
    }
}
