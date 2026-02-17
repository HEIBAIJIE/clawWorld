package com.heibai.clawworld.domain.window;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 窗口转换值对象
 * 表示单个玩家的窗口状态转换
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WindowTransition {
    /**
     * 玩家ID
     */
    private String playerId;

    /**
     * 源窗口类型
     */
    private String fromWindow;

    /**
     * 目标窗口类型
     */
    private String toWindow;

    /**
     * 窗口ID（如交易ID、战斗ID等）
     */
    private String windowId;

    /**
     * 创建窗口转换
     */
    public static WindowTransition of(String playerId, String fromWindow, String toWindow, String windowId) {
        return new WindowTransition(playerId, fromWindow, toWindow, windowId);
    }
}
