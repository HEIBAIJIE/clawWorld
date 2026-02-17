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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 状态服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateServiceImpl implements StateService {

    private final AccountRepository accountRepository;
    private final ChatService chatService;
    private final MapEntityService mapEntityService;
    private final PlayerSessionService playerSessionService;
    private final com.heibai.clawworld.application.service.TradeService tradeService;
    private final com.heibai.clawworld.infrastructure.config.ConfigDataManager configDataManager;
    private final com.heibai.clawworld.application.service.PartyService partyService;
    private final com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository tradeRepository;

    @Override
    public String generateMapState(String playerId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 1. 获取账号信息
        Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
        if (!accountOpt.isPresent()) {
            state.append("服务端执行结果：").append(commandResult).append("\n\n");
            state.append("=== 环境变化 ===\n");
            state.append("无法获取玩家状态\n");
            return state.toString();
        }
        AccountEntity account = accountOpt.get();

        // 2. 显示上次指令（带时间戳）
        if (account.getLastCommand() != null && account.getLastCommandTimestamp() != null) {
            state.append(formatTimestamp(account.getLastCommandTimestamp()))
                .append("你的指令：").append(account.getLastCommand()).append("\n\n");
        }

        // 3. 指令执行结果
        state.append("服务端执行结果：").append(commandResult).append("\n\n");

        // 4. 获取上次状态时间戳和实体快照
        Long lastTimestamp = account.getLastStateTimestamp();
        java.util.Map<String, AccountEntity.EntitySnapshot> lastSnapshot = account.getLastEntitySnapshot();
        if (lastSnapshot == null) {
            lastSnapshot = new java.util.HashMap<>();
        }

        // 5. 检查窗口变化
        String currentWindowType = account.getCurrentWindowType();
        if (currentWindowType != null && !"MAP".equals(currentWindowType)) {
            state.append("=== 窗口变化 ===\n");
            state.append("你的窗口已切换到").append(getWindowTypeName(currentWindowType)).append("\n");
            state.append("请使用对应窗口的指令进行操作\n\n");
        }

        // 6. 收集环境变化
        state.append("=== 环境变化 ===\n");

        // 6.1 实体变化（精确追踪：新加入、离开、位置变化、交互选项变化）
        Player currentPlayer = playerSessionService.getPlayerState(playerId);
        if (currentPlayer != null && currentPlayer.getMapId() != null) {
            List<MapEntity> entitiesOnMap = mapEntityService.getMapEntities(currentPlayer.getMapId());

            // 构建当前实体快照
            java.util.Map<String, AccountEntity.EntitySnapshot> currentSnapshot = new java.util.HashMap<>();
            java.util.Map<String, MapEntity> currentEntitiesMap = new java.util.HashMap<>();

            for (MapEntity entity : entitiesOnMap) {
                // 不追踪自己
                if (entity.getName().equals(currentPlayer.getName())) {
                    continue;
                }

                currentEntitiesMap.put(entity.getName(), entity);

                AccountEntity.EntitySnapshot snapshot = new AccountEntity.EntitySnapshot();
                snapshot.setX(entity.getX());
                snapshot.setY(entity.getY());

                // 获取交互选项
                if (entity.isInteractable()) {
                    List<String> options = getEntityInteractionOptionsForState(entity, currentPlayer);
                    snapshot.setInteractionOptions(options);
                } else {
                    snapshot.setInteractionOptions(new java.util.ArrayList<>());
                }

                currentSnapshot.put(entity.getName(), snapshot);
            }

            // 分析变化
            java.util.List<String> newEntities = new java.util.ArrayList<>();
            java.util.List<String> leftEntities = new java.util.ArrayList<>();
            java.util.List<String> positionChanges = new java.util.ArrayList<>();
            java.util.List<String> interactionChanges = new java.util.ArrayList<>();

            // 检测新加入和变化的实体
            for (java.util.Map.Entry<String, AccountEntity.EntitySnapshot> entry : currentSnapshot.entrySet()) {
                String entityName = entry.getKey();
                AccountEntity.EntitySnapshot currentSnap = entry.getValue();
                AccountEntity.EntitySnapshot lastSnap = lastSnapshot.get(entityName);

                if (lastSnap == null) {
                    // 新实体
                    MapEntity entity = currentEntitiesMap.get(entityName);
                    String entityType = entity.getEntityType();
                    if ("PLAYER".equals(entityType)) {
                        newEntities.add(String.format("- 玩家 %s 加入了地图，位置 (%d,%d)",
                            entityName, currentSnap.getX(), currentSnap.getY()));
                    } else {
                        newEntities.add(String.format("- %s %s 出现在 (%d,%d)",
                            entityType, entityName, currentSnap.getX(), currentSnap.getY()));
                    }
                } else {
                    // 检查位置变化
                    if (currentSnap.getX() != lastSnap.getX() || currentSnap.getY() != lastSnap.getY()) {
                        MapEntity entity = currentEntitiesMap.get(entityName);
                        String entityType = entity.getEntityType();
                        if ("PLAYER".equals(entityType)) {
                            positionChanges.add(String.format("- 玩家 %s 移动到 (%d,%d)",
                                entityName, currentSnap.getX(), currentSnap.getY()));
                        } else {
                            positionChanges.add(String.format("- %s 移动到 (%d,%d)",
                                entityName, currentSnap.getX(), currentSnap.getY()));
                        }
                    }

                    // 检查交互选项变化
                    List<String> currentOptions = currentSnap.getInteractionOptions();
                    List<String> lastOptions = lastSnap.getInteractionOptions();
                    if (currentOptions == null) currentOptions = new java.util.ArrayList<>();
                    if (lastOptions == null) lastOptions = new java.util.ArrayList<>();

                    // 找出新增的交互选项
                    List<String> addedOptions = new java.util.ArrayList<>(currentOptions);
                    addedOptions.removeAll(lastOptions);

                    // 找出移除的交互选项
                    List<String> removedOptions = new java.util.ArrayList<>(lastOptions);
                    removedOptions.removeAll(currentOptions);

                    if (!addedOptions.isEmpty() || !removedOptions.isEmpty()) {
                        StringBuilder change = new StringBuilder();
                        change.append(String.format("- %s 的交互选项变化：", entityName));
                        if (!addedOptions.isEmpty()) {
                            change.append("新增[").append(String.join(", ", addedOptions)).append("]");
                        }
                        if (!removedOptions.isEmpty()) {
                            if (!addedOptions.isEmpty()) change.append("，");
                            change.append("移除[").append(String.join(", ", removedOptions)).append("]");
                        }
                        interactionChanges.add(change.toString());
                    }
                }
            }

            // 检测离开的实体
            for (String entityName : lastSnapshot.keySet()) {
                if (!currentSnapshot.containsKey(entityName)) {
                    // 实体离开了
                    leftEntities.add(String.format("- %s 离开了地图", entityName));
                }
            }

            // 输出变化
            boolean hasChanges = false;

            if (!newEntities.isEmpty()) {
                state.append("\n【新实体加入】\n");
                for (String change : newEntities) {
                    state.append(change).append("\n");
                }
                hasChanges = true;
            }

            if (!leftEntities.isEmpty()) {
                state.append("\n【实体离开】\n");
                for (String change : leftEntities) {
                    state.append(change).append("\n");
                }
                hasChanges = true;
            }

            if (!positionChanges.isEmpty()) {
                state.append("\n【位置变化】\n");
                for (String change : positionChanges) {
                    state.append(change).append("\n");
                }
                hasChanges = true;
            }

            if (!interactionChanges.isEmpty()) {
                state.append("\n【交互选项变化】\n");
                for (String change : interactionChanges) {
                    state.append(change).append("\n");
                }
                hasChanges = true;
            }

            if (!hasChanges) {
                state.append("\n【地图实体】\n");
                state.append("- 没有实体变化\n");
            }

            // 保存当前快照
            account.setLastEntitySnapshot(currentSnapshot);
        }

        // 6.2 聊天消息变化
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

        // 7. 更新状态时间戳
        account.setLastStateTimestamp(System.currentTimeMillis());
        accountRepository.save(account);

        return state.toString();
    }

    /**
     * 获取实体的交互选项（用于状态追踪）
     */
    private List<String> getEntityInteractionOptionsForState(MapEntity entity, Player viewer) {
        // 这里需要调用与 MapWindowContentGenerator 相同的逻辑
        // 为了避免循环依赖，我们直接获取基础交互选项
        com.heibai.clawworld.infrastructure.config.data.map.MapConfig mapConfig = configDataManager.getMap(viewer.getMapId());
        if (mapConfig == null) {
            return entity.getInteractionOptions();
        }

        List<String> options = new java.util.ArrayList<>(entity.getInteractionOptions(viewer.getFaction(), mapConfig.isSafe()));

        // 如果是玩家，添加玩家特定的交互选项
        if ("PLAYER".equals(entity.getEntityType()) && entity instanceof Player) {
            Player targetPlayer = (Player) entity;
            addPlayerSpecificOptionsForState(options, viewer, targetPlayer);
        }

        return options;
    }

    /**
     * 添加玩家特定的交互选项（用于状态追踪）
     */
    private void addPlayerSpecificOptionsForState(List<String> options, Player viewer, Player target) {
        // 组队相关选项
        com.heibai.clawworld.domain.character.Party viewerParty = partyService.getPlayerParty(viewer.getId());
        com.heibai.clawworld.domain.character.Party targetParty = partyService.getPlayerParty(target.getId());

        if (targetParty == null || targetParty.isSolo()) {
            options.add("邀请组队");
        }

        if (targetParty != null && targetParty.getPendingInvitations() != null) {
            boolean hasInvitation = targetParty.getPendingInvitations().stream()
                    .anyMatch(inv -> inv.getInviterId().equals(target.getId())
                            && inv.getInviteeId().equals(viewer.getId())
                            && !inv.isExpired());
            if (hasInvitation) {
                options.add("接受组队邀请");
                options.add("拒绝组队邀请");
            }
        }

        if (targetParty != null && !targetParty.isSolo()) {
            options.add("请求加入队伍");
        }

        if (viewerParty != null && viewerParty.isLeader(viewer.getId()) && viewerParty.getPendingRequests() != null) {
            boolean hasRequest = viewerParty.getPendingRequests().stream()
                    .anyMatch(req -> req.getRequesterId().equals(target.getId()) && !req.isExpired());
            if (hasRequest) {
                options.add("接受组队请求");
                options.add("拒绝组队请求");
            }
        }

        // 交易相关选项
        List<com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity> activeTrades =
            tradeRepository.findActiveTradesByPlayerId(
                com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity.TradeStatus.ACTIVE, viewer.getId());
        List<com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity> pendingTrades =
            tradeRepository.findActiveTradesByPlayerId(
                com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity.TradeStatus.PENDING, viewer.getId());

        if (activeTrades.isEmpty() && pendingTrades.isEmpty()) {
            options.add("请求交易");
        }

        // 检查viewer是否收到了来自target的交易请求
        List<com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity> viewerPendingTrades =
            tradeRepository.findByStatusAndReceiverId(
                com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity.TradeStatus.PENDING, viewer.getId());

        log.debug("检查交易请求: viewerId={}, targetId={}, pendingTradesCount={}",
            viewer.getId(), target.getId(), viewerPendingTrades.size());

        boolean hasTradeRequest = viewerPendingTrades.stream()
                .anyMatch(t -> t.getInitiatorId().equals(target.getId()));

        log.debug("是否有来自target的交易请求: viewerId={}, targetId={}, hasTradeRequest={}",
            viewer.getId(), target.getId(), hasTradeRequest);

        if (hasTradeRequest) {
            options.add("接受交易请求");
            options.add("拒绝交易请求");
            log.debug("添加交易请求选项: viewerId={}, targetId={}", viewer.getId(), target.getId());
        }
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
     * 格式化聊天消息（带时间戳）
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

        String timestamp = formatTimestamp(msg.getTimestamp());
        return String.format("%s%s %s: %s", timestamp, channelPrefix, msg.getSenderNickname(), msg.getMessage());
    }

    /**
     * 格式化时间戳为 [月日 时:分] 格式
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return "";
        }
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, zoneId);

        return String.format("[%d月%d日 %02d:%02d]",
            dateTime.getMonthValue(),
            dateTime.getDayOfMonth(),
            dateTime.getHour(),
            dateTime.getMinute());
    }

    /**
     * 获取窗口类型名称
     */
    private String getWindowTypeName(String windowType) {
        if (windowType == null) {
            return "未知窗口";
        }
        return switch (windowType) {
            case "MAP" -> "地图窗口";
            case "COMBAT" -> "战斗窗口";
            case "TRADE" -> "交易窗口";
            case "SHOP" -> "商店窗口";
            case "REGISTER" -> "注册窗口";
            default -> "未知窗口";
        };
    }
}
