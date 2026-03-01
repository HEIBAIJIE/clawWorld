package com.heibai.clawworld.interfaces.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResponse {
    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 响应内容（纯文本）
     */
    private String response;

    public static CommandResponse success(String response) {
        return CommandResponse.builder()
                .success(true)
                .response(response)
                .build();
    }

    public static CommandResponse error(String response) {
        return CommandResponse.builder()
                .success(false)
                .response(response)
                .build();
    }
}
