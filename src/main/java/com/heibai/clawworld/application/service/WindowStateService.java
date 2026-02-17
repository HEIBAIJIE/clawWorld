package com.heibai.clawworld.application.service;

import com.heibai.clawworld.domain.window.WindowTransition;

import java.util.List;

/**
 * 窗口状态服务
 * 管理窗口状态转换的状态机
 */
public interface WindowStateService {

    /**
     * 单个玩家窗口转换
     *
     * @param playerId 玩家ID
     * @param toWindow 目标窗口类型
     * @param windowId 窗口ID（可选）
     * @return 是否转换成功
     */
    boolean transitionWindow(String playerId, String toWindow, String windowId);

    /**
     * 多个玩家原子窗口转换
     * 所有转换要么全部成功，要么全部失败
     *
     * @param transitions 窗口转换列表
     * @return 是否转换成功
     */
    boolean transitionWindows(List<WindowTransition> transitions);

    /**
     * 验证窗口转换是否合法
     *
     * @param playerId 玩家ID
     * @param fromWindow 源窗口类型
     * @param toWindow 目标窗口类型
     * @return 是否合法
     */
    boolean validateTransition(String playerId, String fromWindow, String toWindow);

    /**
     * 获取玩家当前窗口类型
     *
     * @param playerId 玩家ID
     * @return 当前窗口类型
     */
    String getCurrentWindowType(String playerId);
}
