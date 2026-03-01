package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.trade.Trade;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 交易窗口日志生成器
 */
@Service
@RequiredArgsConstructor
public class TradeWindowLogGenerator {

    private final ConfigDataManager configDataManager;

    /**
     * 生成交易窗口日志
     */
    public void generateTradeWindowLogs(GameLogBuilder builder, Trade trade, Player player, Player otherPlayer) {
        // 1. 交易基本信息
        builder.addWindow("交易窗口", String.format("与 %s 的交易", otherPlayer.getName()));

        // 2. 你的背包和金钱
        StringBuilder myInventory = new StringBuilder();
        myInventory.append("你的资产：\n");
        myInventory.append(String.format("金币: %d\n", player.getGold()));
        myInventory.append("背包物品：\n");
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    myInventory.append(String.format("- %s x%d\n", slot.getItem().getName(), slot.getQuantity()));
                } else if (slot.isEquipment()) {
                    myInventory.append(String.format("- %s\n", slot.getEquipment().getDisplayName()));
                }
            }
        } else {
            myInventory.append("(背包为空)\n");
        }
        builder.addWindow("交易窗口", myInventory.toString());

        // 3. 交易状态
        generateTradeStatus(builder, trade, player.getId());

        // 注意：可用指令已移至系统上下文，不再在每次窗口刷新时输出
    }

    /**
     * 生成交易状态日志
     */
    public void generateTradeStateLogs(GameLogBuilder builder, Trade trade, String playerId, String commandResult) {
        // 1. 交易状态
        generateTradeStatus(builder, trade, playerId);

        // 2. 响应
        builder.addState("响应", commandResult);
    }

    /**
     * 生成交易状态信息
     */
    private void generateTradeStatus(GameLogBuilder builder, Trade trade, String playerId) {
        boolean isInitiator = trade.getInitiatorId().equals(playerId);
        Trade.TradeOffer myOffer = isInitiator ? trade.getInitiatorOffer() : trade.getReceiverOffer();
        Trade.TradeOffer otherOffer = isInitiator ? trade.getReceiverOffer() : trade.getInitiatorOffer();
        boolean myLocked = isInitiator ? trade.isInitiatorLocked() : trade.isReceiverLocked();
        boolean otherLocked = isInitiator ? trade.isReceiverLocked() : trade.isInitiatorLocked();

        StringBuilder status = new StringBuilder();
        status.append("交易状态：\n\n");

        // 你的提供
        status.append("你的提供：\n");
        if (myOffer != null && (myOffer.getGold() > 0 || (myOffer.getItems() != null && !myOffer.getItems().isEmpty()))) {
            if (myOffer.getGold() > 0) {
                status.append(String.format("  金币: %d\n", myOffer.getGold()));
            }
            if (myOffer.getItems() != null && !myOffer.getItems().isEmpty()) {
                status.append("  物品:\n");
                for (Trade.TradeItem item : myOffer.getItems()) {
                    String itemName = getItemDisplayName(item);
                    status.append("    - ").append(itemName);
                    if (!item.isEquipment() && item.getQuantity() > 1) {
                        status.append(" x").append(item.getQuantity());
                    }
                    status.append("\n");
                }
            }
        } else {
            status.append("  (无)\n");
        }
        status.append(String.format("  状态: %s\n\n", myLocked ? "已锁定" : "未锁定"));

        // 对方的提供
        status.append("对方的提供：\n");
        if (otherOffer != null && (otherOffer.getGold() > 0 || (otherOffer.getItems() != null && !otherOffer.getItems().isEmpty()))) {
            if (otherOffer.getGold() > 0) {
                status.append(String.format("  金币: %d\n", otherOffer.getGold()));
            }
            if (otherOffer.getItems() != null && !otherOffer.getItems().isEmpty()) {
                status.append("  物品:\n");
                for (Trade.TradeItem item : otherOffer.getItems()) {
                    String itemName = getItemDisplayName(item);
                    status.append("    - ").append(itemName);
                    if (!item.isEquipment() && item.getQuantity() > 1) {
                        status.append(" x").append(item.getQuantity());
                    }
                    status.append("\n");
                }
            }
        } else {
            status.append("  (无)\n");
        }
        status.append(String.format("  状态: %s\n", otherLocked ? "已锁定" : "未锁定"));

        builder.addState("交易状态", status.toString());
    }

    /**
     * 获取物品显示名称
     */
    private String getItemDisplayName(Trade.TradeItem item) {
        if (item.isEquipment()) {
            var equipConfig = configDataManager.getEquipment(item.getItemId());
            if (equipConfig != null) {
                return equipConfig.getName() + "#" + item.getEquipmentInstanceNumber();
            }
            return item.getItemId() + "#" + item.getEquipmentInstanceNumber();
        } else {
            var itemConfig = configDataManager.getItem(item.getItemId());
            if (itemConfig != null) {
                return itemConfig.getName();
            }
            return item.getItemId();
        }
    }
}
