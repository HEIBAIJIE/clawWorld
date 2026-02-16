package com.heibai.clawworld.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指令响应DTO
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
     * 结果消息
     */
    private String message;

    /**
     * 是否切换窗口
     */
    private boolean windowChanged;

    /**
     * 新窗口ID
     */
    private String newWindowId;

    /**
     * 新窗口类型
     */
    private String newWindowType;

    /**
     * 额外数据
     */
    private Object data;

    public static CommandResponse success(String message, boolean windowChanged,
                                         String newWindowId, String newWindowType, Object data) {
        return CommandResponse.builder()
                .success(true)
                .message(message)
                .windowChanged(windowChanged)
                .newWindowId(newWindowId)
                .newWindowType(newWindowType)
                .data(data)
                .build();
    }

    public static CommandResponse error(String message) {
        return CommandResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
