package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.domain.character.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商店窗口日志生成器
 */
@Service
@RequiredArgsConstructor
public class ShopWindowLogGenerator {

    /**
     * 生成商店窗口日志
     */
    public void generateShopWindowLogs(GameLogBuilder builder,
                                      com.heibai.clawworld.application.service.ShopService.ShopInfo shop,
                                      Player player) {
        // 1. 商店基本信息
        builder.addWindow("商店窗口", String.format("商店：%s", shop.getNpcName()));

        // 2. 商店出售的商品
        StringBuilder sellItems = new StringBuilder();
        sellItems.append("出售商品：\n");
        if (shop.getItems() != null && !shop.getItems().isEmpty()) {
            for (com.heibai.clawworld.application.service.ShopService.ShopInfo.ShopItemInfo item : shop.getItems()) {
                sellItems.append(String.format("- %s (%s)  价格:%d  库存:%d\n",
                    item.getItemName(),
                    item.getDescription(),
                    item.getPrice(),
                    item.getCurrentQuantity()));
            }
        } else {
            sellItems.append("(暂无商品)\n");
        }
        builder.addWindow("商店窗口", sellItems.toString());

        // 3. 商店收购信息
        StringBuilder buyInfo = new StringBuilder();
        buyInfo.append("收购信息：\n");
        buyInfo.append("商店收购物品\n");
        builder.addWindow("商店窗口", buyInfo.toString());

        // 4. 商店资金（替代原来的"你的资产"）
        builder.addWindow("商店窗口", String.format("商店资金: %d 金币", shop.getGold()));

        // 注意：可用指令已移至系统上下文，不再在每次窗口刷新时输出
    }

    /**
     * 生成商店状态日志（买卖后的状态更新）
     */
    public void generateShopStateLogs(GameLogBuilder builder,
                                     com.heibai.clawworld.application.service.ShopService.ShopInfo shop,
                                     Player player,
                                     String commandResult) {
        // 1. 商店库存变化
        if (shop.getItems() != null && !shop.getItems().isEmpty()) {
            StringBuilder stockChanges = new StringBuilder();
            stockChanges.append("商店库存：\n");
            for (com.heibai.clawworld.application.service.ShopService.ShopInfo.ShopItemInfo item : shop.getItems()) {
                stockChanges.append(String.format("- %s (%s)  价格:%d  库存:%d\n",
                    item.getItemName(),
                    item.getDescription(),
                    item.getPrice(),
                    item.getCurrentQuantity()));
            }
            builder.addState("库存变化", stockChanges.toString());
        }

        // 2. 商店资金变化
        builder.addState("商店资金", String.format("%d 金币", shop.getGold()));

        // 3. 玩家资产变化（包含金币和完整背包）
        StringBuilder playerAssets = new StringBuilder();
        playerAssets.append(String.format("金币: %d\n", player.getGold()));
        playerAssets.append("背包物品：\n");
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    playerAssets.append(String.format("- %s x%d\n", slot.getItem().getName(), slot.getQuantity()));
                } else if (slot.isEquipment()) {
                    playerAssets.append(String.format("- %s\n", slot.getEquipment().getDisplayName()));
                }
            }
        } else {
            playerAssets.append("(空)");
        }
        builder.addState("你的资产", playerAssets.toString());

        // 4. 响应
        builder.addState("响应", commandResult);
    }
}
