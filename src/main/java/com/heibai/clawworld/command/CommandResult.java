package com.heibai.clawworld.command;

import lombok.Builder;
import lombok.Data;

/**
 * 指令执行结果
 */
@Data
@Builder
public class CommandResult {
    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 是否需要切换窗口
     */
    private boolean windowChanged;

    /**
     * 新窗口ID（如果窗口改变）
     */
    private String newWindowId;

    /**
     * 新窗口类型（如果窗口改变）
     */
    private CommandContext.WindowType newWindowType;

    /**
     * 额外数据（可选）
     */
    private Object data;

    public static CommandResult success(String message) {
        return CommandResult.builder()
                .success(true)
                .message(message)
                .windowChanged(false)
                .build();
    }

    public static CommandResult error(String message) {
        return CommandResult.builder()
                .success(false)
                .message(message)
                .windowChanged(false)
                .build();
    }

    public static CommandResult successWithWindowChange(String message, String newWindowId, CommandContext.WindowType newWindowType) {
        return CommandResult.builder()
                .success(true)
                .message(message)
                .windowChanged(true)
                .newWindowId(newWindowId)
                .newWindowType(newWindowType)
                .build();
    }
}
