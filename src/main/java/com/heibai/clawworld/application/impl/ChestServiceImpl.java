package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ChestService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.ChestConfig;
import com.heibai.clawworld.infrastructure.config.data.map.ChestLootConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapEntityConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.ChestInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.ConfigMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.ChestInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 宝箱服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChestServiceImpl implements ChestService {

    private final PlayerRepository playerRepository;
    private final ChestInstanceRepository chestInstanceRepository;
    private final ConfigDataManager configDataManager;
    private final PlayerSessionService playerSessionService;
    private final ConfigMapper configMapper;
    private final Random random = new Random();

    @Override
    @Transactional
    public OpenChestResult openChest(String playerId, String chestName) {
        // 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return OpenChestResult.error("玩家不存在");
        }

        PlayerEntity playerEntity = playerOpt.get();

        // 检查玩家是否在战斗中
        if (playerEntity.isInCombat()) {
            return OpenChestResult.error("战斗中无法打开宝箱");
        }

        // 查找宝箱
        ChestInstanceEntity chest = findChest(playerEntity.getCurrentMapId(), chestName);
        if (chest == null) {
            return OpenChestResult.error("找不到宝箱: " + chestName);
        }

        // 检查玩家是否在宝箱附近（九宫格范围内）
        int dx = Math.abs(playerEntity.getX() - chest.getX());
        int dy = Math.abs(playerEntity.getY() - chest.getY());
        if (dx > 1 || dy > 1) {
            return OpenChestResult.error("你离宝箱太远了，请先靠近");
        }

        // 获取宝箱配置
        ChestConfig chestConfig = configDataManager.getChest(chest.getTemplateId());
        if (chestConfig == null) {
            return OpenChestResult.error("宝箱配置不存在");
        }

        // 检查是否可以开启
        if ("SMALL".equals(chest.getChestType())) {
            // 小宝箱：检查玩家是否已开过
            if (chest.hasPlayerOpened(playerId)) {
                return OpenChestResult.error("你已经开启过这个宝箱了");
            }
        } else {
            // 大宝箱：检查是否已被开启且未刷新
            if (chest.isOpened()) {
                long now = System.currentTimeMillis();
                long respawnTime = chest.getLastOpenTime() + chestConfig.getRespawnSeconds() * 1000L;
                if (now < respawnTime) {
                    int remaining = (int) ((respawnTime - now) / 1000);
                    return OpenChestResult.error("宝箱已被开启，" + remaining + "秒后刷新");
                }
            }
        }

        // 计算掉落
        List<LootItem> lootItems = calculateLoot(chest.getTemplateId());

        // 获取玩家状态并添加物品
        Player player = playerSessionService.getPlayerState(playerId);
        if (player == null) {
            return OpenChestResult.error("无法获取玩家状态");
        }

        // 添加物品到玩家背包
        for (LootItem loot : lootItems) {
            addItemToPlayer(player, loot.getItemId(), loot.getQuantity());
        }

        // 保存玩家状态
        playerSessionService.savePlayerState(player);

        // 更新宝箱状态
        if ("SMALL".equals(chest.getChestType())) {
            chest.markPlayerOpened(playerId);
        } else {
            chest.setOpened(true);
            chest.setLastOpenTime(System.currentTimeMillis());
        }
        chestInstanceRepository.save(chest);

        // 构建消息
        StringBuilder message = new StringBuilder();
        message.append("你打开了").append(chestConfig.getName()).append("，获得了：\n");
        if (lootItems.isEmpty()) {
            message.append("  （空空如也）");
        } else {
            for (LootItem loot : lootItems) {
                message.append("  - ").append(loot.getItemName());
                if (loot.getQuantity() > 1) {
                    message.append(" x").append(loot.getQuantity());
                }
                message.append("\n");
            }
        }

        log.info("玩家 {} 打开宝箱 {}，获得 {} 件物品", player.getName(), chestName, lootItems.size());

        return OpenChestResult.success(message.toString().trim(), 0, lootItems);
    }

    @Override
    public ChestInfo inspectChest(String playerId, String chestName) {
        // 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return ChestInfo.error("玩家不存在");
        }

        PlayerEntity playerEntity = playerOpt.get();

        // 查找宝箱
        ChestInstanceEntity chest = findChest(playerEntity.getCurrentMapId(), chestName);
        if (chest == null) {
            return ChestInfo.error("找不到宝箱: " + chestName);
        }

        // 获取宝箱配置
        ChestConfig chestConfig = configDataManager.getChest(chest.getTemplateId());
        if (chestConfig == null) {
            return ChestInfo.error("宝箱配置不存在");
        }

        boolean canOpen;
        int remainingSeconds = 0;

        if ("SMALL".equals(chest.getChestType())) {
            canOpen = !chest.hasPlayerOpened(playerId);
        } else {
            if (!chest.isOpened()) {
                canOpen = true;
            } else {
                long now = System.currentTimeMillis();
                long respawnTime = chest.getLastOpenTime() + chestConfig.getRespawnSeconds() * 1000L;
                canOpen = now >= respawnTime;
                if (!canOpen) {
                    remainingSeconds = (int) ((respawnTime - now) / 1000);
                }
            }
        }

        return ChestInfo.success(chestConfig.getName(), chest.getChestType(), canOpen, remainingSeconds);
    }

    @Override
    @Transactional
    public void initializeChestsForMap(String mapId) {
        List<MapEntityConfig> mapEntities = configDataManager.getMapEntities(mapId);

        for (MapEntityConfig entity : mapEntities) {
            if ("CHEST_SMALL".equals(entity.getEntityType()) || "CHEST_LARGE".equals(entity.getEntityType())) {
                String instanceId = entity.getInstanceId();
                if (instanceId == null || instanceId.isEmpty()) {
                    instanceId = entity.getEntityId() + "_" + entity.getX() + "_" + entity.getY();
                }

                // 检查是否已存在
                Optional<ChestInstanceEntity> existing = chestInstanceRepository.findByMapIdAndInstanceId(mapId, instanceId);
                if (existing.isPresent()) {
                    continue;
                }

                // 获取宝箱配置
                ChestConfig chestConfig = configDataManager.getChest(entity.getEntityId());
                if (chestConfig == null) {
                    log.warn("宝箱配置不存在: {}", entity.getEntityId());
                    continue;
                }

                // 创建宝箱实例
                ChestInstanceEntity chest = new ChestInstanceEntity();
                chest.setMapId(mapId);
                chest.setInstanceId(instanceId);
                chest.setTemplateId(entity.getEntityId());
                chest.setDisplayName(chestConfig.getName());
                chest.setX(entity.getX());
                chest.setY(entity.getY());
                chest.setChestType(chestConfig.getType());
                chest.setOpened(false);
                chest.setOpenedByPlayers(new HashSet<>());

                chestInstanceRepository.save(chest);
                log.info("初始化宝箱实例: {} 在地图 {} 位置 ({}, {})", chestConfig.getName(), mapId, entity.getX(), entity.getY());
            }
        }
    }

    /**
     * 查找地图上的宝箱
     */
    private ChestInstanceEntity findChest(String mapId, String chestName) {
        List<ChestInstanceEntity> chests = chestInstanceRepository.findByMapId(mapId);
        for (ChestInstanceEntity chest : chests) {
            if (chest.getDisplayName().equals(chestName)) {
                return chest;
            }
        }
        return null;
    }

    /**
     * 计算宝箱掉落
     */
    private List<LootItem> calculateLoot(String chestId) {
        List<LootItem> lootItems = new ArrayList<>();
        List<ChestLootConfig> lootConfigs = configDataManager.getChestLoot(chestId);

        if (lootConfigs == null || lootConfigs.isEmpty()) {
            return lootItems;
        }

        for (ChestLootConfig lootConfig : lootConfigs) {
            // 根据掉落率判断是否掉落
            if (random.nextDouble() < lootConfig.getDropRate()) {
                // 计算数量
                int quantity = lootConfig.getMinQuantity();
                if (lootConfig.getMaxQuantity() > lootConfig.getMinQuantity()) {
                    quantity += random.nextInt(lootConfig.getMaxQuantity() - lootConfig.getMinQuantity() + 1);
                }

                // 获取物品名称
                String itemName = lootConfig.getItemId();
                var itemConfig = configDataManager.getItem(lootConfig.getItemId());
                if (itemConfig != null) {
                    itemName = itemConfig.getName();
                } else {
                    var eqConfig = configDataManager.getEquipment(lootConfig.getItemId());
                    if (eqConfig != null) {
                        itemName = eqConfig.getName();
                    }
                }

                lootItems.add(new LootItem(lootConfig.getItemId(), itemName, quantity, lootConfig.getRarity()));
            }
        }

        return lootItems;
    }

    /**
     * 将物品添加到玩家背包
     */
    private void addItemToPlayer(Player player, String itemId, int quantity) {
        // 检查是否已有该物品（只对普通物品堆叠）
        boolean found = false;
        if (configDataManager.getEquipment(itemId) == null) {
            // 普通物品可以堆叠
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem() && slot.getItem().getId().equals(itemId)) {
                    slot.setQuantity(slot.getQuantity() + quantity);
                    found = true;
                    break;
                }
            }
        }

        // 如果没有找到或是装备，添加新的物品槽
        if (!found && player.getInventory().size() < 50) {
            var eqConfig = configDataManager.getEquipment(itemId);
            if (eqConfig != null) {
                // 装备需要生成实例编号，每件装备单独添加
                for (int i = 0; i < quantity; i++) {
                    if (player.getInventory().size() < 50) {
                        player.getInventory().add(Player.InventorySlot.forEquipment(
                            configMapper.toDomain(eqConfig)));
                    }
                }
            } else {
                var itemConfig = configDataManager.getItem(itemId);
                if (itemConfig != null) {
                    player.getInventory().add(Player.InventorySlot.forItem(
                        configMapper.toDomain(itemConfig), quantity));
                }
            }
        }
    }
}
