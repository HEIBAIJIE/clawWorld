package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.application.service.StateService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 状态服务实现
 */
@Service
@RequiredArgsConstructor
public class StateServiceImpl implements StateService {

    private final AccountRepository accountRepository;
    private final ChatService chatService;
    private final MapEntityService mapEntityService;
    private final PlayerSessionService playerSessionService;
    private final com.heibai.clawworld.application.service.TradeService tradeService;
    private final com.heibai.clawworld.infrastructure.config.ConfigDataManager configDataManager;

    @Override
    public String generateMapState(String playerId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 1. 指令执行结果
        state.append(">>> ").append(commandResult).append("\n\n");

        // 2. 获取上次状态时间戳
        Long lastTimestamp = getLastStateTimestamp(playerId);
        long currentTime = System.currentTimeMillis();

        // 3. 收集环境变化
        state.append("=== 环境变化 ===\n");

        // 3.1 实体变化（特别是其他玩家的变化）
        Player currentPlayer = playerSessionService.getPlayerState(playerId);
        if (currentPlayer != null && currentPlayer.getMapId() != null) {
            List<MapEntity> entitiesOnMap = mapEntityService.getMapEntities(currentPlayer.getMapId());

            if (!entitiesOnMap.isEmpty()) {
                state.append("\n【地图实体更新】\n");
                boolean hasOtherPlayers = false;
                for (MapEntity entity : entitiesOnMap) {
                    // 不显示自己
                    if (entity.getName().equals(currentPlayer.getName())) {
                        continue;
                    }

                    // 显示其他玩家的位置
                    if ("PLAYER".equals(entity.getEntityType())) {
                        state.append(String.format("- 玩家 %s 在 (%d,%d)\n",
                            entity.getName(), entity.getX(), entity.getY()));
                        hasOtherPlayers = true;
                    }
                }

                if (!hasOtherPlayers) {
                    state.append("- 当前地图上没有其他玩家\n");
                }
            } else {
                state.append("\n【地图实体更新】\n");
                state.append("- 当前地图上没有其他玩家\n");
            }
        }

        // 3.2 聊天消息变化
        List<ChatMessage> chatHistory = chatService.getChatHistory(playerId);
        if (chatHistory != null && !chatHistory.isEmpty()) {
            // 过滤出上次状态之后的新消息
            List<ChatMessage> newMessages = chatHistory.stream()
                    .filter(msg -> lastTimestamp == null || msg.getTimestamp() > lastTimestamp)
                    .collect(java.util.stream.Collectors.toList());

            if (!newMessages.isEmpty()) {
                state.append("\n【新的聊天消息】\n");
                for (ChatMessage msg : newMessages) {
                    state.append(formatChatMessage(msg)).append("\n");
                }
            } else {
                state.append("\n【聊天消息】\n");
                state.append("- 没有新的聊天消息\n");
            }
        } else {
            state.append("\n【聊天消息】\n");
            state.append("- 没有新的聊天消息\n");
        }

        // 4. 更新状态时间戳
        updateLastStateTimestamp(playerId);

        return state.toString();
    }

    @Override
    public String generateCombatState(String playerId, String combatId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 战斗窗口的状态相对简单，主要是战斗日志和角色状态变化
        state.append(">>> ").append(commandResult).append("\n\n");

        // 战斗状态的详细信息
        state.append("=== 战斗状态 ===\n");
        state.append("（战斗日志）\n");
        state.append("- 等待你的回合...\n");
        state.append("\n");

        state.append("（行动条顺序）\n");
        state.append("- 当前回合信息需要从战斗系统获取\n");
        state.append("\n");

        updateLastStateTimestamp(playerId);

        return state.toString();
    }

    @Override
    public String generateTradeState(String playerId, String tradeId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 交易窗口的状态
        state.append(">>> ").append(commandResult).append("\n\n");

        // 获取交易状态
        com.heibai.clawworld.domain.trade.Trade trade = tradeService.getTradeState(tradeId);
        if (trade == null) {
            state.append("交易不存在或已结束\n");
            updateLastStateTimestamp(playerId);
            return state.toString();
        }

        // 确定当前玩家是发起者还是接收者
        boolean isInitiator = trade.getInitiatorId().equals(playerId);
        com.heibai.clawworld.domain.trade.Trade.TradeOffer myOffer = isInitiator ? trade.getInitiatorOffer() : trade.getReceiverOffer();
        com.heibai.clawworld.domain.trade.Trade.TradeOffer otherOffer = isInitiator ? trade.getReceiverOffer() : trade.getInitiatorOffer();
        boolean myLocked = isInitiator ? trade.isInitiatorLocked() : trade.isReceiverLocked();
        boolean otherLocked = isInitiator ? trade.isReceiverLocked() : trade.isInitiatorLocked();

        // 交易状态的详细信息
        state.append("=== 交易状态 ===\n");
        state.append("（双方提供的物品和金钱）\n");

        // 你的提供
        state.append("你的提供：\n");
        if (myOffer != null) {
            if (myOffer.getGold() > 0) {
                state.append("  金币: ").append(myOffer.getGold()).append("\n");
            }
            if (myOffer.getItems() != null && !myOffer.getItems().isEmpty()) {
                state.append("  物品:\n");
                for (com.heibai.clawworld.domain.trade.Trade.TradeItem item : myOffer.getItems()) {
                    String itemName = getItemDisplayName(item);
                    state.append("    - ").append(itemName);
                    if (!item.isEquipment() && item.getQuantity() > 1) {
                        state.append(" x").append(item.getQuantity());
                    }
                    state.append("\n");
                }
            }
            if ((myOffer.getGold() == 0) && (myOffer.getItems() == null || myOffer.getItems().isEmpty())) {
                state.append("  （无）\n");
            }
        } else {
            state.append("  （无）\n");
        }

        // 对方的提供
        state.append("对方的提供：\n");
        if (otherOffer != null) {
            if (otherOffer.getGold() > 0) {
                state.append("  金币: ").append(otherOffer.getGold()).append("\n");
            }
            if (otherOffer.getItems() != null && !otherOffer.getItems().isEmpty()) {
                state.append("  物品:\n");
                for (com.heibai.clawworld.domain.trade.Trade.TradeItem item : otherOffer.getItems()) {
                    String itemName = getItemDisplayName(item);
                    state.append("    - ").append(itemName);
                    if (!item.isEquipment() && item.getQuantity() > 1) {
                        state.append(" x").append(item.getQuantity());
                    }
                    state.append("\n");
                }
            }
            if ((otherOffer.getGold() == 0) && (otherOffer.getItems() == null || otherOffer.getItems().isEmpty())) {
                state.append("  （无）\n");
            }
        } else {
            state.append("  （无）\n");
        }
        state.append("\n");

        state.append("（锁定状态）\n");
        state.append("你的锁定状态：").append(myLocked ? "已锁定" : "未锁定").append("\n");
        state.append("对方的锁定状态：").append(otherLocked ? "已锁定" : "未锁定").append("\n");

        updateLastStateTimestamp(playerId);

        return state.toString();
    }

    /**
     * 获取物品显示名称
     */
    private String getItemDisplayName(com.heibai.clawworld.domain.trade.Trade.TradeItem item) {
        if (item.isEquipment()) {
            // 装备显示为：物品名#实例编号
            com.heibai.clawworld.infrastructure.config.data.item.EquipmentConfig equipConfig =
                configDataManager.getEquipment(item.getItemId());
            if (equipConfig != null) {
                return equipConfig.getName() + "#" + item.getEquipmentInstanceNumber();
            }
            return item.getItemId() + "#" + item.getEquipmentInstanceNumber();
        } else {
            // 普通物品显示名称
            com.heibai.clawworld.infrastructure.config.data.item.ItemConfig itemConfig =
                configDataManager.getItem(item.getItemId());
            if (itemConfig != null) {
                return itemConfig.getName();
            }
            return item.getItemId();
        }
    }

    @Override
    public String generateShopState(String playerId, String shopId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 商店窗口的状态
        state.append(">>> ").append(commandResult).append("\n\n");

        // 商店状态的详细信息
        state.append("=== 商店状态 ===\n");

        // 获取玩家最新状态
        Player player = playerSessionService.getPlayerState(playerId);
        if (player != null) {
            state.append("你的金币: ").append(player.getGold()).append("\n");
            state.append("背包空间: ");
            if (player.getInventory() != null) {
                state.append(player.getInventory().size()).append("/50\n");
            } else {
                state.append("0/50\n");
            }
        }

        updateLastStateTimestamp(playerId);

        return state.toString();
    }

    @Override
    public void updateLastStateTimestamp(String playerId) {
        Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
        if (accountOpt.isPresent()) {
            AccountEntity account = accountOpt.get();
            account.setLastStateTimestamp(System.currentTimeMillis());
            accountRepository.save(account);
        }
    }

    @Override
    public Long getLastStateTimestamp(String playerId) {
        Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
        return accountOpt.map(AccountEntity::getLastStateTimestamp).orElse(null);
    }

    /**
     * 格式化聊天消息
     */
    private String formatChatMessage(ChatMessage msg) {
        String channelPrefix;
        switch (msg.getChannelType()) {
            case WORLD:
                channelPrefix = "[世界]";
                break;
            case MAP:
                channelPrefix = "[地图]";
                break;
            case PARTY:
                channelPrefix = "[队伍]";
                break;
            case PRIVATE:
                channelPrefix = "[私聊]";
                break;
            default:
                channelPrefix = "[未知]";
        }

        return String.format("%s %s: %s", channelPrefix, msg.getSenderNickname(), msg.getMessage());
    }
}
