package com.heibai.clawworld.application.service;

import com.heibai.clawworld.domain.character.Player;

/**
 * 角色信息服务接口
 * 统一生成角色状态、技能、装备、背包、队伍等信息的展示文本
 */
public interface CharacterInfoService {

    /**
     * 生成角色状态信息
     */
    String generatePlayerStatus(Player player);

    /**
     * 生成技能信息
     */
    String generateSkills(Player player);

    /**
     * 生成装备信息
     */
    String generateEquipment(Player player);

    /**
     * 生成背包信息
     */
    String generateInventory(Player player);

    /**
     * 生成队伍信息
     */
    String generatePartyInfo(Player player);

    /**
     * 生成查看其他玩家的信息（不包含背包）
     */
    String generateOtherPlayerInfo(Player target);

    /**
     * 生成查看自己的完整信息（包含背包）
     */
    String generateSelfInfo(Player player);
}
