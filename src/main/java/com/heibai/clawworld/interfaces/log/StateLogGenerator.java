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
            builder.addState("响应", "错误: 无法获取玩家状态");
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
            String currentMapId = currentPlayer.getMapId();
            String lastMapId = account.getLastMapId();
            boolean isMapChanged = lastMapId != null && !lastMapId.equals(currentMapId);

            List<MapEntity> entitiesOnMap = mapEntityService.getMapEntities(currentMapId, playerId);

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
                snapshot.setEntityType(entity.getEntityType());

                // 记录敌人的死亡状态
                if (entity instanceof com.heibai.clawworld.domain.character.Enemy) {
                    com.heibai.clawworld.domain.character.Enemy enemy = (com.heibai.clawworld.domain.character.Enemy) entity;
                    snapshot.setIsDead(enemy.isDead());
                }

                // 记录宝箱的开启状态
                if (entity instanceof com.heibai.clawworld.domain.map.Chest) {
                    com.heibai.clawworld.domain.map.Chest chest = (com.heibai.clawworld.domain.map.Chest) entity;
                    // 小宝箱：检查当前玩家是否已开启
                    // 大宝箱：检查是否已被开启且未刷新
                    if (chest.getChestType() == com.heibai.clawworld.domain.map.Chest.ChestType.SMALL) {
                        snapshot.setIsOpened(chest.isOpenedByCurrentPlayer());
                    } else {
                        snapshot.setIsOpened(chest.isOpened() && !chest.canOpen());
                    }
                }

                if (entity.isInteractable()) {
                    List<String> options = getEntityInteractionOptions(entity, currentPlayer);
                    snapshot.setInteractionOptions(options);
                } else {
                    snapshot.setInteractionOptions(new ArrayList<>());
                }

                currentSnapshot.put(entity.getName(), snapshot);
            }

            // 分析变化并生成日志
            // 跳过条件：1. 地图切换时  2. 首次创建快照时（lastSnapshot为空表示初始化，不是真正的变化）
            boolean isFirstSnapshot = lastSnapshot.isEmpty();
            if (!isMapChanged && !isFirstSnapshot) {
                generateEntityChangeLogs(builder, lastSnapshot, currentSnapshot, currentEntitiesMap);
            }

            // 保存当前快照和地图ID
            account.setLastEntitySnapshot(currentSnapshot);
            account.setLastMapId(currentMapId);
        }

        // 3. 队伍状态变化
        generatePartyChangeLogs(builder, account, currentPlayer);

        // 4. 交易邀请变化
        generateTradeInvitationChangeLogs(builder, account, currentPlayer);

        // 5. 聊天消息变化
        generateChatChangeLogs(builder, playerId, lastTimestamp);

        // 6. 响应（放在最后）
        builder.addState("响应", commandResult);

        // 7. 更新状态时间戳
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

        // 检测敌人刷新（从死亡变为存活）
        for (Map.Entry<String, AccountEntity.EntitySnapshot> entry : currentSnapshot.entrySet()) {
            String entityName = entry.getKey();
            AccountEntity.EntitySnapshot currentSnap = entry.getValue();
            AccountEntity.EntitySnapshot lastSnap = lastSnapshot.get(entityName);

            if (lastSnap != null && "ENEMY".equals(currentSnap.getEntityType())) {
                Boolean lastIsDead = lastSnap.getIsDead();
                Boolean currentIsDead = currentSnap.getIsDead();
                // 从死亡变为存活 = 刷新
                if (Boolean.TRUE.equals(lastIsDead) && Boolean.FALSE.equals(currentIsDead)) {
                    MapEntity entity = currentEntitiesMap.get(entityName);
                    List<String> options = currentSnap.getInteractionOptions();
                    String optionsStr = (options != null && !options.isEmpty())
                        ? String.join(", ", options)
                        : "无";
                    builder.addState("环境变化",
                        String.format("ENEMY %s 出现在 (%d,%d)",
                            entityName, currentSnap.getX(), currentSnap.getY()));
                    builder.addState("环境变化",
                        String.format("%s 的交互选项：[%s]", entityName, optionsStr));
                }
            }
        }

        // 检测宝箱状态变化（从未开启变为已开启）
        for (Map.Entry<String, AccountEntity.EntitySnapshot> entry : currentSnapshot.entrySet()) {
            String entityName = entry.getKey();
            AccountEntity.EntitySnapshot currentSnap = entry.getValue();
            AccountEntity.EntitySnapshot lastSnap = lastSnapshot.get(entityName);

            String entityType = currentSnap.getEntityType();
            if (lastSnap != null && (entityType != null && entityType.startsWith("CHEST"))) {
                Boolean lastIsOpened = lastSnap.getIsOpened();
                Boolean currentIsOpened = currentSnap.getIsOpened();
                // 从未开启变为已开启
                if (Boolean.FALSE.equals(lastIsOpened) && Boolean.TRUE.equals(currentIsOpened)) {
                    builder.addState("环境变化",
                        String.format("%s 已被打开", entityName));
                }
                // 从已开启变为未开启（大宝箱刷新）
                else if (Boolean.TRUE.equals(lastIsOpened) && Boolean.FALSE.equals(currentIsOpened)) {
                    builder.addState("环境变化",
                        String.format("%s 已刷新", entityName));
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
     * 生成队伍变化日志
     */
    private void generatePartyChangeLogs(GameLogBuilder builder, AccountEntity account, Player currentPlayer) {
        if (currentPlayer == null) {
            return;
        }

        AccountEntity.PartySnapshot lastSnapshot = account.getLastPartySnapshot();
        AccountEntity.PartySnapshot currentSnapshot = buildCurrentPartySnapshot(currentPlayer);

        // 检测队伍状态变化
        if (lastSnapshot == null) {
            // 首次检测，如果已经在队伍中，生成队伍信息
            if (currentSnapshot.getPartyId() != null && currentSnapshot.getMemberNames() != null
                && currentSnapshot.getMemberNames().size() > 1) {
                generatePartyMemberList(builder, currentSnapshot, currentPlayer.getName());
            }
        } else {
            String lastPartyId = lastSnapshot.getPartyId();
            String currentPartyId = currentSnapshot.getPartyId();
            List<String> lastMembers = lastSnapshot.getMemberNames() != null ? lastSnapshot.getMemberNames() : new ArrayList<>();
            List<String> currentMembers = currentSnapshot.getMemberNames() != null ? currentSnapshot.getMemberNames() : new ArrayList<>();

            // 检测队伍解散或被踢
            if (lastPartyId != null && currentPartyId == null) {
                // 之前在队伍中，现在不在了
                if (lastSnapshot.isLeader()) {
                    // 自己是队长，检查是否有成员离开导致队伍解散
                    if (lastMembers.size() > 1) {
                        // 之前有多个成员，现在队伍解散了
                        for (String member : lastMembers) {
                            if (!member.equals(currentPlayer.getName())) {
                                builder.addState("队伍变化", String.format("%s 离开了队伍", member));
                            }
                        }
                        builder.addState("队伍变化", "队伍已解散");
                    }
                } else {
                    // 自己不是队长，可能是被踢或队伍解散
                    builder.addState("队伍变化", "你已离开队伍（队伍解散或被踢出）");
                }
            } else if (lastPartyId != null && currentPartyId != null && lastPartyId.equals(currentPartyId)) {
                // 在同一个队伍中，检测成员变化
                boolean memberChanged = false;

                // 检测新加入的成员
                for (String member : currentMembers) {
                    if (!lastMembers.contains(member) && !member.equals(currentPlayer.getName())) {
                        builder.addState("队伍变化", String.format("%s 加入了队伍", member));
                        memberChanged = true;
                    }
                }

                // 检测离开的成员
                for (String member : lastMembers) {
                    if (!currentMembers.contains(member) && !member.equals(currentPlayer.getName())) {
                        builder.addState("队伍变化", String.format("%s 离开了队伍", member));
                        memberChanged = true;
                    }
                }

                // 如果成员有变化，生成最新的队伍成员列表
                if (memberChanged && currentMembers.size() > 1) {
                    generatePartyMemberList(builder, currentSnapshot, currentPlayer.getName());
                }
            } else if (lastPartyId == null && currentPartyId != null) {
                // 之前不在队伍，现在在队伍中
                // 检查是否是因为邀请被接受（队伍成员大于1）
                if (currentMembers.size() > 1) {
                    // 邀请被接受，生成队伍信息通知
                    builder.addState("队伍变化", "队伍已组建");
                    generatePartyMemberList(builder, currentSnapshot, currentPlayer.getName());
                }
            } else if (lastPartyId != null && currentPartyId != null && !lastPartyId.equals(currentPartyId)) {
                // 换了一个队伍（理论上不应该发生，但以防万一）
                builder.addState("队伍变化", "你加入了新的队伍");
                generatePartyMemberList(builder, currentSnapshot, currentPlayer.getName());
            }

            // 检测收到的新邀请
            Map<String, Long> lastInvitations = lastSnapshot.getPendingInvitationsReceived() != null
                ? lastSnapshot.getPendingInvitationsReceived() : new HashMap<>();
            Map<String, Long> currentInvitations = currentSnapshot.getPendingInvitationsReceived() != null
                ? currentSnapshot.getPendingInvitationsReceived() : new HashMap<>();

            for (String inviterName : currentInvitations.keySet()) {
                if (!lastInvitations.containsKey(inviterName)) {
                    builder.addState("队伍变化", String.format("%s 邀请你加入队伍", inviterName));
                }
            }
        }

        // 保存当前快照
        account.setLastPartySnapshot(currentSnapshot);
    }

    /**
     * 生成队伍成员列表日志
     */
    private void generatePartyMemberList(GameLogBuilder builder, AccountEntity.PartySnapshot snapshot, String currentPlayerName) {
        if (snapshot.getMemberNames() == null || snapshot.getMemberNames().isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        String leaderName = snapshot.getLeaderName();
        // 如果当前玩家是队长，先显示提示
        if (currentPlayerName.equals(leaderName)) {
            sb.append("你是队长\n");
        }

        sb.append("当前队伍成员(").append(snapshot.getMemberNames().size()).append("/4)：");

        for (String memberName : snapshot.getMemberNames()) {
            sb.append("\n  - ").append(memberName);
            // 标记队长
            if (memberName.equals(leaderName)) {
                sb.append(" [队长]");
            }
        }

        builder.addState("队伍变化", sb.toString());
    }

    /**
     * 构建当前队伍状态快照
     */
    private AccountEntity.PartySnapshot buildCurrentPartySnapshot(Player player) {
        AccountEntity.PartySnapshot snapshot = new AccountEntity.PartySnapshot();

        Party party = partyService.getPlayerParty(player.getId());
        if (party != null && !party.isSolo()) {
            snapshot.setPartyId(party.getId());
            snapshot.setLeader(party.isLeader(player.getId()));

            // 获取成员名称列表和队长名字
            List<String> memberNames = new ArrayList<>();
            String leaderName = null;
            for (String memberId : party.getMemberIds()) {
                Player member = playerSessionService.getPlayerState(memberId);
                if (member != null) {
                    memberNames.add(member.getName());
                    if (party.isLeader(memberId)) {
                        leaderName = member.getName();
                    }
                }
            }
            snapshot.setMemberNames(memberNames);
            snapshot.setLeaderName(leaderName);
        }

        // 收集所有发给当前玩家的待处理邀请
        Map<String, Long> pendingInvitations = new HashMap<>();
        // 遍历所有可能的邀请者（地图上的其他玩家）
        List<MapEntity> entitiesOnMap = mapEntityService.getMapEntities(player.getMapId(), player.getId());
        for (MapEntity entity : entitiesOnMap) {
            if ("PLAYER".equals(entity.getEntityType()) && !entity.getName().equals(player.getName())) {
                // 获取这个玩家的完整信息
                Player otherPlayer = null;
                if (entity instanceof Player) {
                    otherPlayer = (Player) entity;
                }

                if (otherPlayer != null && otherPlayer.getId() != null) {
                    Party otherParty = partyService.getPlayerParty(otherPlayer.getId());
                    if (otherParty != null && otherParty.getPendingInvitations() != null) {
                        for (Party.PartyInvitation inv : otherParty.getPendingInvitations()) {
                            if (inv.getInviteeId().equals(player.getId()) && !inv.isExpired()) {
                                pendingInvitations.put(otherPlayer.getName(), inv.getInviteTime());
                            }
                        }
                    }
                }
            }
        }
        snapshot.setPendingInvitationsReceived(pendingInvitations);

        return snapshot;
    }

    /**
     * 生成交易邀请变化日志
     */
    private void generateTradeInvitationChangeLogs(GameLogBuilder builder, AccountEntity account, Player currentPlayer) {
        if (currentPlayer == null) {
            return;
        }

        AccountEntity.TradeInvitationSnapshot lastSnapshot = account.getLastTradeInvitationSnapshot();
        AccountEntity.TradeInvitationSnapshot currentSnapshot = buildCurrentTradeInvitationSnapshot(currentPlayer);

        Map<String, Long> lastInvitations = lastSnapshot != null && lastSnapshot.getPendingTradeInvitations() != null
            ? lastSnapshot.getPendingTradeInvitations() : new HashMap<>();
        Map<String, Long> currentInvitations = currentSnapshot.getPendingTradeInvitations() != null
            ? currentSnapshot.getPendingTradeInvitations() : new HashMap<>();

        // 检测新收到的交易邀请
        for (String inviterName : currentInvitations.keySet()) {
            if (!lastInvitations.containsKey(inviterName)) {
                builder.addState("交易变化", String.format("%s 邀请你进行交易", inviterName));
            }
        }

        // 保存当前快照
        account.setLastTradeInvitationSnapshot(currentSnapshot);
    }

    /**
     * 构建当前交易邀请状态快照
     */
    private AccountEntity.TradeInvitationSnapshot buildCurrentTradeInvitationSnapshot(Player player) {
        AccountEntity.TradeInvitationSnapshot snapshot = new AccountEntity.TradeInvitationSnapshot();
        Map<String, Long> pendingInvitations = new HashMap<>();

        // 查找所有发给当前玩家的待处理交易请求
        List<TradeEntity> pendingTrades = tradeRepository.findByStatusAndReceiverId(
            TradeEntity.TradeStatus.PENDING, player.getId());

        for (TradeEntity trade : pendingTrades) {
            // 获取发起者的名字
            Player initiator = playerSessionService.getPlayerState(trade.getInitiatorId());
            if (initiator != null) {
                pendingInvitations.put(initiator.getName(), trade.getCreateTime());
            }
        }

        snapshot.setPendingTradeInvitations(pendingInvitations);
        return snapshot;
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

            // 检查是否有来自目标的组队邀请（邀请存储在目标的队伍中）
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
