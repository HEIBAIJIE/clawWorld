package com.heibai.clawworld.dto;

import com.heibai.clawworld.command.CommandContext;
import lombok.Data;

/**
 * 指令请求DTO
 */
@Data
public class CommandRequest {
    /**
     * 指令字符串
     */
    private String command;

    /**
     * 当前窗口ID
     */
    private String windowId;

    /**
     * 当前窗口类型
     */
    private CommandContext.WindowType windowType;
}
