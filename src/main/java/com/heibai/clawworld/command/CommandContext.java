package com.heibai.clawworld.command;

import lombok.Builder;
import lombok.Data;

/**
 * 指令执行上下文
 * 包含执行指令所需的所有信息
 */
@Data
@Builder
public class CommandContext {
    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 窗口ID
     */
    private String windowId;

    /**
     * 玩家ID
     */
    private String playerId;

    /**
     * 当前窗口类型
     */
    private WindowType windowType;

    /**
     * 窗口类型枚举
     */
    public enum WindowType {
        REGISTER,   // 注册窗口
        MAP,        // 地图窗口
        COMBAT,     // 战斗窗口
        TRADE       // 交易窗口
    }
}
