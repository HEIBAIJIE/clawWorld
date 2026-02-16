package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ShopService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Item;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.NpcConfig;
import com.heibai.clawworld.infrastructure.config.data.character.NpcShopItemConfig;
import com.heibai.clawworld.infrastructure.config.data.item.ItemConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.NpcShopInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.NpcShopInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 商店服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final NpcShopInstanceRepository npcShopInstanceRepository;
    private final ConfigDataManager configDataManager;

    @Override
    @Transactional
    public OperationResult buyItem(String playerId, String shopId, String itemName, int quantity) {
        // 1. 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return OperationResult.error("玩家不存在");
        }
        PlayerEntity playerEntity = playerOpt.get();
        Player player = playerMapper.toDomain(playerEntity);

        // 2. 获取商店实例
        Optional<NpcShopInstanceEntity> shopOpt = npcShopInstanceRepository.findByNpcId(shopId);
        if (!shopOpt.isPresent()) {
            return OperationResult.error("商店不存在");
        }
        NpcShopInstanceEntity shop = shopOpt.get();

        // 3. 获取NPC配置
        NpcConfig npcConfig = configDataManager.getNpc(shopId);
        if (npcConfig == null || !npcConfig.isHasShop()) {
            return OperationResult.error("该NPC没有商店");
        }

        // 4. 查找物品配置
        ItemConfig itemConfig = configDataManager.getItem(itemName);
        if (itemConfig == null) {
            return OperationResult.error("物品不存在: " + itemName);
        }

        // 5. 检查商店库存
        NpcShopInstanceEntity.ShopItemData shopItem = shop.getItems().stream()
            .filter(item -> item.getItemId().equals(itemConfig.getId()))
            .findFirst()
            .orElse(null);

        if (shopItem == null) {
            return OperationResult.error("商店不出售该物品");
        }

        if (shopItem.getCurrentQuantity() < quantity) {
            return OperationResult.error(String.format("商店库存不足，当前库存: %d", shopItem.getCurrentQuantity()));
        }

        // 6. 计算价格
        int totalPrice = itemConfig.getBasePrice() * quantity;

        // 7. 验证玩家金钱
        if (player.getGold() < totalPrice) {
            return OperationResult.error(String.format("金钱不足，需要 %d 金币", totalPrice));
        }

        // 8. 检查背包空间
        if (player.getInventory() == null) {
            player.setInventory(new java.util.ArrayList<>());
        }

        // 查找是否已有该物品
        Player.InventorySlot existingSlot = player.getInventory().stream()
            .filter(slot -> slot.isItem() && slot.getItem().getId().equals(itemConfig.getId()))
            .findFirst()
            .orElse(null);

        if (existingSlot == null && player.getInventory().size() >= 50) {
            return OperationResult.error("背包已满");
        }

        // 9. 执行交易
        // 扣除玩家金钱
        player.setGold(player.getGold() - totalPrice);

        // 增加商店金钱
        shop.setCurrentGold(shop.getCurrentGold() + totalPrice);

        // 减少商店库存
        shopItem.setCurrentQuantity(shopItem.getCurrentQuantity() - quantity);

        // 添加物品到玩家背包
        if (existingSlot != null) {
            // 堆叠到现有物品
            existingSlot.setQuantity(existingSlot.getQuantity() + quantity);
        } else {
            // 创建新的背包槽
            Item item = new Item();
            item.setId(itemConfig.getId());
            item.setName(itemConfig.getName());
            item.setDescription(itemConfig.getDescription());
            item.setBasePrice(itemConfig.getBasePrice());
            item.setMaxStack(itemConfig.getMaxStack());
            item.setEffect(itemConfig.getEffect());
            item.setEffectValue(itemConfig.getEffectValue());
            // 根据type字符串设置ItemType枚举
            if ("consumable".equalsIgnoreCase(itemConfig.getType())) {
                item.setType(Item.ItemType.CONSUMABLE);
            } else if ("material".equalsIgnoreCase(itemConfig.getType())) {
                item.setType(Item.ItemType.MATERIAL);
            } else if ("quest".equalsIgnoreCase(itemConfig.getType())) {
                item.setType(Item.ItemType.QUEST);
            } else if ("skill_book".equalsIgnoreCase(itemConfig.getType())) {
                item.setType(Item.ItemType.SKILL_BOOK);
            } else {
                item.setType(Item.ItemType.OTHER);
            }

            Player.InventorySlot newSlot = Player.InventorySlot.forItem(item, quantity);
            player.getInventory().add(newSlot);
        }

        // 10. 保存更新
        PlayerEntity updatedEntity = playerMapper.toEntity(player);
        updatedEntity.setId(playerId);
        playerRepository.save(updatedEntity);
        npcShopInstanceRepository.save(shop);

        log.info("玩家 {} 从商店 {} 购买 {} x{}, 花费 {} 金币", playerId, shopId, itemName, quantity, totalPrice);
        return OperationResult.success(String.format("购买 %s x%d 成功，花费 %d 金币", itemName, quantity, totalPrice));
    }

    @Override
    @Transactional
    public OperationResult sellItem(String playerId, String shopId, String itemName, int quantity) {
        // 1. 获取玩家信息
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return OperationResult.error("玩家不存在");
        }
        PlayerEntity playerEntity = playerOpt.get();
        Player player = playerMapper.toDomain(playerEntity);

        // 2. 获取商店实例
        Optional<NpcShopInstanceEntity> shopOpt = npcShopInstanceRepository.findByNpcId(shopId);
        if (!shopOpt.isPresent()) {
            return OperationResult.error("商店不存在");
        }
        NpcShopInstanceEntity shop = shopOpt.get();

        // 3. 获取NPC配置
        NpcConfig npcConfig = configDataManager.getNpc(shopId);
        if (npcConfig == null || !npcConfig.isHasShop()) {
            return OperationResult.error("该NPC没有商店");
        }

        // 4. 查找物品配置
        ItemConfig itemConfig = configDataManager.getItem(itemName);
        if (itemConfig == null) {
            return OperationResult.error("物品不存在: " + itemName);
        }

        // 5. 验证玩家是否有该物品
        if (player.getInventory() == null) {
            return OperationResult.error("背包为空");
        }

        Player.InventorySlot playerSlot = player.getInventory().stream()
            .filter(slot -> slot.isItem() && slot.getItem().getId().equals(itemConfig.getId()))
            .findFirst()
            .orElse(null);

        if (playerSlot == null) {
            return OperationResult.error("你没有该物品");
        }

        if (playerSlot.getQuantity() < quantity) {
            return OperationResult.error(String.format("物品数量不足，当前拥有: %d", playerSlot.getQuantity()));
        }

        // 6. 计算出售价格（根据NPC的价格倍率）
        int sellPrice = (int)(itemConfig.getBasePrice() * npcConfig.getPriceMultiplier() * quantity);

        // 7. 验证商店金钱
        if (shop.getCurrentGold() < sellPrice) {
            return OperationResult.error(String.format("商店金钱不足，无法收购"));
        }

        // 8. 执行交易
        // 从玩家背包移除物品
        if (playerSlot.getQuantity() == quantity) {
            player.getInventory().remove(playerSlot);
        } else {
            playerSlot.setQuantity(playerSlot.getQuantity() - quantity);
        }

        // 增加玩家金钱
        player.setGold(player.getGold() + sellPrice);

        // 减少商店金钱
        shop.setCurrentGold(shop.getCurrentGold() - sellPrice);

        // 增加商店库存（如果商店出售该物品）
        NpcShopInstanceEntity.ShopItemData shopItem = shop.getItems().stream()
            .filter(item -> item.getItemId().equals(itemConfig.getId()))
            .findFirst()
            .orElse(null);

        if (shopItem != null) {
            shopItem.setCurrentQuantity(shopItem.getCurrentQuantity() + quantity);
        }

        // 9. 保存更新
        PlayerEntity updatedEntity = playerMapper.toEntity(player);
        updatedEntity.setId(playerId);
        playerRepository.save(updatedEntity);
        npcShopInstanceRepository.save(shop);

        log.info("玩家 {} 向商店 {} 出售 {} x{}, 获得 {} 金币", playerId, shopId, itemName, quantity, sellPrice);
        return OperationResult.success(String.format("出售 %s x%d 成功，获得 %d 金币", itemName, quantity, sellPrice));
    }

    @Override
    public ShopInfo getShopInfo(String shopId) {
        // 从NPC配置中读取商店信息
        NpcConfig npcConfig = configDataManager.getNpc(shopId);
        if (npcConfig == null || !npcConfig.isHasShop()) {
            return null;
        }

        // 获取商店实例
        Optional<NpcShopInstanceEntity> shopOpt = npcShopInstanceRepository.findByNpcId(shopId);
        if (!shopOpt.isPresent()) {
            return null;
        }
        NpcShopInstanceEntity shop = shopOpt.get();

        // 获取商店物品配置
        List<NpcShopItemConfig> shopItemConfigs = configDataManager.getNpcShopItems(shopId);

        ShopInfo info = new ShopInfo();
        info.setShopId(shopId);
        info.setNpcName(npcConfig.getName());
        info.setGold(shop.getCurrentGold());
        info.setPriceMultiplier(npcConfig.getPriceMultiplier());

        // 添加商品列表
        java.util.List<ShopInfo.ShopItemInfo> items = new java.util.ArrayList<>();
        for (NpcShopItemConfig itemConfig : shopItemConfigs) {
            ItemConfig item = configDataManager.getItem(itemConfig.getItemId());
            if (item != null) {
                // 查找当前库存
                NpcShopInstanceEntity.ShopItemData shopItem = shop.getItems().stream()
                    .filter(si -> si.getItemId().equals(itemConfig.getItemId()))
                    .findFirst()
                    .orElse(null);

                ShopInfo.ShopItemInfo itemInfo = new ShopInfo.ShopItemInfo();
                itemInfo.setItemId(item.getId());
                itemInfo.setItemName(item.getName());
                itemInfo.setPrice(item.getBasePrice());
                itemInfo.setMaxQuantity(itemConfig.getQuantity());
                itemInfo.setCurrentQuantity(shopItem != null ? shopItem.getCurrentQuantity() : 0);
                items.add(itemInfo);
            }
        }
        info.setItems(items);

        return info;
    }
}

