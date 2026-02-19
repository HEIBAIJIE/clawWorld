package com.heibai.clawworld.application.impl.combat;

import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.service.PlayerLevelService;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.persistence.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 战利品分配器 - 负责分配战斗胜利后的战利品
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CombatRewardDistributor {

    private final PlayerSessionService playerSessionService;
    private final PlayerLevelService playerLevelService;
    private final ConfigDataManager configDataManager;
    private final ConfigMapper configMapper;

    /**
     * 分配战利品
     * 根据设计文档：
     * - 每个玩家都获得全部经验
     * - 金钱平分
     * - 物品归队长
     */
    public void distributeRewards(CombatInstance.RewardDistribution distribution) {
        if (distribution == null || distribution.getPlayerIds() == null || distribution.getPlayerIds().isEmpty()) {
            return;
        }

        log.info("开始分配战利品: winnerFaction={}, exp={}, gold={}, items={}, players={}",
            distribution.getWinnerFactionId(),
            distribution.getTotalExperience(),
            distribution.getTotalGold(),
            distribution.getItems().size(),
            distribution.getPlayerIds().size());

        // 为每个玩家分配经验和金钱
        for (String playerId : distribution.getPlayerIds()) {
            Player player = playerSessionService.getPlayerState(playerId);
            if (player != null) {
                distributeToPlayer(player, distribution);
            }
        }

        // 物品归队长
        distributeItemsToLeader(distribution);

        log.info("战利品分配完成");
    }

    /**
     * 为单个玩家分配经验和金钱
     */
    private void distributeToPlayer(Player player, CombatInstance.RewardDistribution distribution) {
        // 每个玩家都获得全部经验
        if (distribution.getTotalExperience() > 0) {
            boolean leveledUp = playerLevelService.addExperienceAndCheckLevelUp(player, distribution.getTotalExperience());
            log.debug("玩家 {} 获得经验: {}", player.getName(), distribution.getTotalExperience());

            if (leveledUp) {
                log.info("玩家 {} 升级到 {} 级！", player.getName(), player.getLevel());
            }
        }

        // 金钱平分
        if (distribution.getGoldPerPlayer() > 0) {
            player.setGold(player.getGold() + distribution.getGoldPerPlayer());
            log.debug("玩家 {} 获得金钱: {}", player.getName(), distribution.getGoldPerPlayer());
        }

        // 保存玩家状态
        playerSessionService.savePlayerState(player);
    }

    /**
     * 将物品分配给队长
     */
    private void distributeItemsToLeader(CombatInstance.RewardDistribution distribution) {
        if (distribution.getItems() == null || distribution.getItems().isEmpty() || distribution.getLeaderId() == null) {
            return;
        }

        Player leader = playerSessionService.getPlayerState(distribution.getLeaderId());
        if (leader == null) {
            return;
        }

        for (String itemId : distribution.getItems()) {
            addItemToPlayer(leader, itemId);
            log.debug("队长 {} 获得物品: {}", leader.getName(), itemId);
        }

        // 保存队长状态
        playerSessionService.savePlayerState(leader);
    }

    /**
     * 将物品添加到玩家背包
     */
    private void addItemToPlayer(Player player, String itemId) {
        // 检查是否已有该物品（只对普通物品堆叠）
        boolean found = false;
        if (configDataManager.getEquipment(itemId) == null) {
            // 普通物品可以堆叠
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem() && slot.getItem().getId().equals(itemId)) {
                    slot.setQuantity(slot.getQuantity() + 1);
                    found = true;
                    break;
                }
            }
        }

        // 如果没有找到或是装备，添加新的物品槽
        if (!found && player.getInventory().size() < 50) {
            var eqConfig = configDataManager.getEquipment(itemId);
            if (eqConfig != null) {
                // 装备需要生成实例编号
                player.getInventory().add(Player.InventorySlot.forEquipment(
                    configMapper.toDomain(eqConfig)));
            } else {
                var itemConfig = configDataManager.getItem(itemId);
                if (itemConfig != null) {
                    player.getInventory().add(Player.InventorySlot.forItem(
                        configMapper.toDomain(itemConfig), 1));
                }
            }
        }
    }
}
