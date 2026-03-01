package com.heibai.clawworld.interfaces.command;

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
     * 新窗口类型（如果窗口改变）
     */
    private CommandContext.WindowType newWindowType;

    /**
     * 窗口内容（纯文本）
     */
    private String windowContent;

    /**
     * 是否需要刷新背包
     */
    private boolean inventoryChanged;

    public static CommandResult success(String message) {
        return CommandResult.builder()
                .success(true)
                .message(message)
                .windowChanged(false)
                .inventoryChanged(false)
                .build();
    }

    public static CommandResult successWithInventoryChange(String message) {
        return CommandResult.builder()
                .success(true)
                .message(message)
                .windowChanged(false)
                .inventoryChanged(true)
                .build();
    }

    public static CommandResult error(String message) {
        return CommandResult.builder()
                .success(false)
                .message(message)
                .windowChanged(false)
                .inventoryChanged(false)
                .build();
    }

    public static CommandResult successWithWindowChange(String message, CommandContext.WindowType newWindowType, String windowContent) {
        return CommandResult.builder()
                .success(true)
                .message(message)
                .windowChanged(true)
                .newWindowType(newWindowType)
                .windowContent(windowContent)
                .inventoryChanged(false)
                .build();
    }

    public static CommandResult errorWithWindowChange(String message, CommandContext.WindowType newWindowType, String windowContent) {
        return CommandResult.builder()
                .success(false)
                .message(message)
                .windowChanged(true)
                .newWindowType(newWindowType)
                .windowContent(windowContent)
                .inventoryChanged(false)
                .build();
    }
}
