package com.heibai.clawworld.application.service;

/**
 * 状态服务
 * 负责生成玩家的状态信息，包括环境变化
 * 根据设计文档，状态包括：
 * 1. 上次执行的指令结果
 * 2. 距离上一次获取状态的这段时间，有哪些实体的变化
 * 3. 这段时间，实体的交互是否有变化
 * 4. 这段时间，是否有新的聊天
 */
public interface StateService {

    /**
     * 生成地图窗口的状态信息
     * @param playerId 玩家ID
     * @param commandResult 指令执行结果
     * @return 状态信息（纯文本）
     */
    String generateMapState(String playerId, String commandResult);

    /**
     * 生成战斗窗口的状态信息
     * @param playerId 玩家ID
     * @param combatId 战斗ID
     * @param commandResult 指令执行结果
     * @return 状态信息（纯文本）
     */
    String generateCombatState(String playerId, String combatId, String commandResult);

    /**
     * 生成交易窗口的状态信息
     * @param playerId 玩家ID
     * @param tradeId 交易ID
     * @param commandResult 指令执行结果
     * @return 状态信息（纯文本）
     */
    String generateTradeState(String playerId, String tradeId, String commandResult);

    /**
     * 更新玩家的最后状态时间戳
     * @param playerId 玩家ID
     */
    void updateLastStateTimestamp(String playerId);

    /**
     * 获取玩家的最后状态时间戳
     * @param playerId 玩家ID
     * @return 时间戳（毫秒）
     */
    Long getLastStateTimestamp(String playerId);
}
