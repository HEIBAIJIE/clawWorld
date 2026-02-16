package com.heibai.clawworld.application.service;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.map.GameMap;

/**
 * 窗口内容生成服务
 * 负责生成各种窗口的文本内容
 */
public interface WindowContentService {

    /**
     * 生成注册窗口内容
     * @return 注册窗口的文本内容
     */
    String generateRegisterWindowContent();

    /**
     * 生成地图窗口内容
     * @param player 玩家对象
     * @param map 地图对象
     * @return 地图窗口的文本内容
     */
    String generateMapWindowContent(Player player, GameMap map);

    /**
     * 生成战斗窗口内容
     * @param playerId 玩家ID
     * @param combatId 战斗ID
     * @return 战斗窗口的文本内容
     */
    String generateCombatWindowContent(String playerId, String combatId);

    /**
     * 生成交易窗口内容
     * @param playerId 玩家ID
     * @param tradeId 交易ID
     * @return 交易窗口的文本内容
     */
    String generateTradeWindowContent(String playerId, String tradeId);
}
