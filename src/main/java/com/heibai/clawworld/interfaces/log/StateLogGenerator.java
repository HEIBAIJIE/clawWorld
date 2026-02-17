package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Party;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 状态日志生成器
 * 生成状态更新的日志
 */
@Service
@RequiredArgsConstructor
public class StateLogGenerator {

    private final AccountRepository accountRepository;
    private final ChatService chatService;
    private final MapEntityService mapEntityService;
    private final PlayerSessionService playerSessionService;
    private final ConfigDataManager configDataManager;
    private final com.heibai.clawworld.application.service.PartyService partyService;
    private final TradeRepository tradeRepository;

    /**
     * 生成地图状态日志
     */
    public void generateMapStateLogs(GameLogBuilder builder, String playerId, String commandResult) {
        Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
        if (!accountOpt.isPresent()) {
            builder.addState("指令响应", "错误: 无法获取玩家状态");
            return;
        }
        AccountEntity account = accountOpt.get();

        // 1. 获取上次状态时间戳和实体快照
        Long lastTimestamp = account.getLastStateTimestamp();
        Map<String, AccountEntity.EntitySnapshot> lastSnapshot = account.getLastEntitySnapshot();
        if (lastSnapshot == null) {
            lastSnapshot = new HashMap<>();
        }

        // 2. 收集环境变化
        Player currentPlayer = playerSessionService.getPlayerState(playerId);
        if (currentPlayer != null && currentPlayer.getMapId() != null) {
            List<MapEntity> entitiesOnMap = mapEntityService.getMapEntities(currentPlayer.getMapId());

            // 构建当前实体快照
            Map<String, AccountEntity.EntitySnapshot> currentSnapshot = new HashMap<>();
            Map<String, MapEntity> currentEntitiesMap = new HashMap<>();

            for (MapEntity entity : entitiesOnMap) {
                if (entity.getName().equals(currentPlayer.getName())) {
                    continue;
                }

                currentEntitiesMap.put(entity.getName(), entity);

                AccountEntity.EntitySnapshot snapshot = new AccountEntity.EntitySnapshot();
                snapshot.setX(entity.getX());
                snapshot.setY(entity.getY());

                if (entity.isInteractable()) {
                    List<String> options = getEntityInteractionOptions(entity, currentPlayer);
                    snapshot.setInteractionOptions(options);
                } else {
                    snapshot.setInteractionOptions(new ArrayList<>());
                }

                currentSnapshot.put(entity.getName(), snapshot);
            }

            // 分析变化并生成日志
            generateEntityChangeLogs(builder, lastSnapshot, currentSnapshot, currentEntitiesMap);

            // 保存当前快照
            account.setLastEntitySnapshot(currentSnapshot);
        }

        // 3. 聊天消息变化
        generateChatChangeLogs(builder, playerId, lastTimestamp);

        // 4. 指令响应（放在最后）
        builder.addState("指令响应", commandResult);

        // 5. 更新状态时间戳
        account.setLastStateTimestamp(System.currentTimeMillis());
        accountRepository.save(account);
    }

    /**
     * 生成实体变化日志
     */
    private void generateEntityChangeLogs(GameLogBuilder builder,
                                          Map<String, AccountEntity.EntitySnapshot> lastSnapshot,
                                          Map<String, AccountEntity.EntitySnapshot> currentSnapshot,
                                          Map<String, MapEntity> currentEntitiesMap) {

        // 检测新加入的实体
        for (Map.Entry<String, AccountEntity.EntitySnapshot> entry : currentSnapshot.entrySet()) {
            String entityName = entry.getKey();
            AccountEntity.EntitySnapshot currentSnap = entry.getValue();
            AccountEntity.EntitySnapshot lastSnap = lastSnapshot.get(entityName);

            if (lastSnap == null) {
                // 新实体 - 显示完整信息，包括位置和交互选项
                MapEntity entity = currentEntitiesMap.get(entityName);
                String entityType = entity.getEntityType();
                List<String> options = currentSnap.getInteractionOptions();
                String optionsStr = (options != null && !options.isEmpty())
                    ? String.join(", ", options)
                    : "无";

                if ("PLAYER".equals(entityType)) {
                    builder.addState("环境变化",
                        String.format("玩家 %s 加入了地图，位置 (%d,%d)",
                            entityName, currentSnap.getX(), currentSnap.getY()));
                    builder.addState("环境变化",
                        String.format("%s 的交互选项：[%s]", entityName, optionsStr));
                } else {
                    builder.addState("环境变化",
                        String.format("%s %s 出现在 (%d,%d)",
                            entityType, entityName, currentSnap.getX(), currentSnap.getY()));
                    if (entity.isInteractable()) {
                        builder.addState("环境变化",
                            String.format("%s 的交互选项：[%s]", entityName, optionsStr));
                    }
                }
            }
        }

        // 检测离开的实体
        for (String entityName : lastSnapshot.keySet()) {
            if (!currentSnapshot.containsKey(entityName)) {
                builder.addState("环境变化", String.format("%s 离开了地图", entityName));
            }
        }

        // 检测位置变化
        for (Map.Entry<String, AccountEntity.EntitySnapshot> entry : currentSnapshot.entrySet()) {
            String entityName = entry.getKey();
            AccountEntity.EntitySnapshot currentSnap = entry.getValue();
            AccountEntity.EntitySnapshot lastSnap = lastSnapshot.get(entityName);

            if (lastSnap != null) {
                if (currentSnap.getX() != lastSnap.getX() || currentSnap.getY() != lastSnap.getY()) {
                    MapEntity entity = currentEntitiesMap.get(entityName);
                    String entityType = entity.getEntityType();
                    if ("PLAYER".equals(entityType)) {
                        builder.addState("环境变化",
                            String.format("玩家 %s 移动到 (%d,%d)",
                                entityName, currentSnap.getX(), currentSnap.getY()));
                    } else {
                        builder.addState("环境变化",
                            String.format("%s 移动到 (%d,%d)",
                                entityName, currentSnap.getX(), currentSnap.getY()));
                    }
                }
            }
        }

        // 检测交互选项变化
        for (Map.Entry<String, AccountEntity.EntitySnapshot> entry : currentSnapshot.entrySet()) {
            String entityName = entry.getKey();
            AccountEntity.EntitySnapshot currentSnap = entry.getValue();
            AccountEntity.EntitySnapshot lastSnap = lastSnapshot.get(entityName);

            if (lastSnap != null) {
                List<String> currentOptions = currentSnap.getInteractionOptions();
                List<String> lastOptions = lastSnap.getInteractionOptions();
                if (currentOptions == null) currentOptions = new ArrayList<>();
                if (lastOptions == null) lastOptions = new ArrayList<>();

                List<String> addedOptions = new ArrayList<>(currentOptions);
                addedOptions.removeAll(lastOptions);

                List<String> removedOptions = new ArrayList<>(lastOptions);
                removedOptions.removeAll(currentOptions);

                if (!addedOptions.isEmpty() || !removedOptions.isEmpty()) {
                    StringBuilder change = new StringBuilder();
                    change.append(String.format("%s 的交互选项变化：", entityName));
                    if (!addedOptions.isEmpty()) {
                        change.append("新增[").append(String.join(", ", addedOptions)).append("]");
                    }
                    if (!removedOptions.isEmpty()) {
                        if (!addedOptions.isEmpty()) change.append("，");
                        change.append("移除[").append(String.join(", ", removedOptions)).append("]");
                    }
                    builder.addState("环境变化", change.toString());
                }
            }
        }
    }

    /**
     * 生成聊天变化日志
     */
    private void generateChatChangeLogs(GameLogBuilder builder, String playerId, Long lastTimestamp) {
        List<ChatMessage> chatHistory = chatService.getChatHistory(playerId);
        if (chatHistory != null && !chatHistory.isEmpty()) {
            List<ChatMessage> newMessages = chatHistory.stream()
                    .filter(msg -> lastTimestamp == null || msg.getTimestamp() > lastTimestamp)
                    .toList();

            for (ChatMessage msg : newMessages) {
                String channelPrefix = switch (msg.getChannelType()) {
                    case WORLD -> "[世界]";
                    case MAP -> "[地图]";
                    case PARTY -> "[队伍]";
                    case PRIVATE -> "[私聊]";
                };
                builder.addState("新增聊天",
                    String.format("%s %s: %s", channelPrefix, msg.getSenderNickname(), msg.getMessage()));
            }
        }
    }

    /**
     * 获取实体的交互选项
     */
    private List<String> getEntityInteractionOptions(MapEntity entity, Player viewer) {
        MapConfig mapConfig = configDataManager.getMap(viewer.getMapId());
        if (mapConfig == null) {
            return entity.getInteractionOptions();
        }

        List<String> options = new ArrayList<>(entity.getInteractionOptions(viewer.getFaction(), mapConfig.isSafe()));

        if ("PLAYER".equals(entity.getEntityType()) && entity instanceof Player) {
            Player targetPlayer = (Player) entity;
            addPlayerSpecificOptions(options, viewer, targetPlayer);
        }

        return options;
    }

    /**
     * 添加玩家特定的交互选项
     */
    private void addPlayerSpecificOptions(List<String> options, Player viewer, Player target) {
        Party viewerParty = partyService.getPlayerParty(viewer.getId());
        Party targetParty = partyService.getPlayerParty(target.getId());

        // 检查是否在同一个队伍
        boolean inSameParty = viewerParty != null && targetParty != null
                && viewerParty.getId().equals(targetParty.getId());

        // 如果不在同一个队伍，才显示组队相关选项
        if (!inSameParty) {
            // 目标没有队伍或只有临时队伍（等待被邀请者加入），可以邀请组队
            if (targetParty == null || targetParty.isSolo()) {
                options.add("邀请组队");
            }

            // 检查是否有来自目标的组队邀请
            if (viewerParty != null && viewerParty.getPendingInvitations() != null) {
                boolean hasInvitation = viewerParty.getPendingInvitations().stream()
                        .anyMatch(inv -> inv.getInviterId().equals(target.getId())
                                && inv.getInviteeId().equals(viewer.getId())
                                && !inv.isExpired());
                if (hasInvitation) {
                    options.add("接受组队邀请");
                    options.add("拒绝组队邀请");
                }
            }

            // 目标有真正的队伍（2人以上），可以请求加入
            if (targetParty != null && !targetParty.isSolo()) {
                options.add("请求加入队伍");
            }

            // 检查是否有来自目标的加入请求（viewer是队长时）
            if (viewerParty != null && viewerParty.isLeader(viewer.getId()) && viewerParty.getPendingRequests() != null) {
                boolean hasRequest = viewerParty.getPendingRequests().stream()
                        .anyMatch(req -> req.getRequesterId().equals(target.getId()) && !req.isExpired());
                if (hasRequest) {
                    options.add("接受组队请求");
                    options.add("拒绝组队请求");
                }
            }
        }

        List<TradeEntity> activeTrades = tradeRepository.findActiveTradesByPlayerId(
                TradeEntity.TradeStatus.ACTIVE, viewer.getId());
        List<TradeEntity> pendingTrades = tradeRepository.findActiveTradesByPlayerId(
                TradeEntity.TradeStatus.PENDING, viewer.getId());

        if (activeTrades.isEmpty() && pendingTrades.isEmpty()) {
            options.add("请求交易");
        }

        // 检查viewer是否收到了来自target的交易请求
        List<TradeEntity> viewerPendingTrades = tradeRepository.findByStatusAndReceiverId(
                TradeEntity.TradeStatus.PENDING, viewer.getId());
        boolean hasTradeRequest = viewerPendingTrades.stream()
                .anyMatch(t -> t.getInitiatorId().equals(target.getId()));
        if (hasTradeRequest) {
            options.add("接受交易请求");
            options.add("拒绝交易请求");
        }
    }
}
