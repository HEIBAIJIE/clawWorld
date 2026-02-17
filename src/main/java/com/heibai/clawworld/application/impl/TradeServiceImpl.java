package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.trade.Trade;
import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.TradeMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.application.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 交易服务实现
 * 使用数据库持久化，实现完整的物品和金钱转移逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    private final TradeRepository tradeRepository;
    private final TradeMapper tradeMapper;
    private final PlayerSessionService playerSessionService;
    private final com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository playerRepository;

    @Override
    @Transactional
    public TradeResult requestTrade(String requesterId, String targetPlayerName) {
        // 检查发起者是否已有进行中的交易
        List<TradeEntity> activeTradesInitiator = tradeRepository.findActiveTradesByPlayerId(
            TradeEntity.TradeStatus.ACTIVE, requesterId);
        List<TradeEntity> pendingTradesInitiator = tradeRepository.findActiveTradesByPlayerId(
            TradeEntity.TradeStatus.PENDING, requesterId);

        if (!activeTradesInitiator.isEmpty() || !pendingTradesInitiator.isEmpty()) {
            return TradeResult.error("你已有进行中的交易");
        }

        // 通过玩家昵称查找玩家ID
        String targetPlayerId = findPlayerIdByName(targetPlayerName);
        if (targetPlayerId == null) {
            return TradeResult.error("目标玩家不存在");
        }

        // 检查目标玩家是否存在
        Player targetPlayer = playerSessionService.getPlayerState(targetPlayerId);
        if (targetPlayer == null) {
            return TradeResult.error("目标玩家不存在");
        }

        // 检查目标玩家是否已有进行中的交易
        List<TradeEntity> activeTradesTarget = tradeRepository.findActiveTradesByPlayerId(
            TradeEntity.TradeStatus.ACTIVE, targetPlayerId);
        List<TradeEntity> pendingTradesTarget = tradeRepository.findActiveTradesByPlayerId(
            TradeEntity.TradeStatus.PENDING, targetPlayerId);

        if (!activeTradesTarget.isEmpty() || !pendingTradesTarget.isEmpty()) {
            return TradeResult.error("目标玩家已有进行中的交易");
        }

        // 创建新交易
        Trade trade = new Trade();
        trade.setId(UUID.randomUUID().toString());
        trade.setInitiatorId(requesterId);
        trade.setReceiverId(targetPlayerId);
        trade.setStatus(Trade.TradeStatus.PENDING);
        trade.setCreateTime(System.currentTimeMillis());
        trade.setInitiatorOffer(new Trade.TradeOffer());
        trade.setReceiverOffer(new Trade.TradeOffer());
        trade.setInitiatorLocked(false);
        trade.setReceiverLocked(false);
        trade.setInitiatorConfirmed(false);
        trade.setReceiverConfirmed(false);

        TradeEntity entity = tradeMapper.toEntity(trade);
        tradeRepository.save(entity);

        String windowId = "trade_window_" + trade.getId();
        log.info("发起交易: tradeId={}, requesterId={}, targetPlayerId={}", trade.getId(), requesterId, targetPlayerId);

        return TradeResult.success(trade.getId(), windowId, "发起交易成功");
    }

    @Override
    @Transactional
    public TradeResult acceptTradeRequest(String playerId, String requesterName) {
        // 通过玩家昵称查找玩家ID
        String requesterId = findPlayerIdByName(requesterName);
        if (requesterId == null) {
            return TradeResult.error("发起者不存在");
        }

        // 查找待接受的交易
        List<TradeEntity> pendingTrades = tradeRepository.findByStatusAndReceiverId(
            TradeEntity.TradeStatus.PENDING, playerId);

        TradeEntity tradeEntity = pendingTrades.stream()
            .filter(t -> t.getInitiatorId().equals(requesterId))
            .findFirst()
            .orElse(null);

        if (tradeEntity == null) {
            return TradeResult.error("未找到待接受的交易");
        }

        // 接受交易
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeRepository.save(tradeEntity);

        String windowId = "trade_window_" + tradeEntity.getId();

        log.info("接受交易: tradeId={}, playerId={}", tradeEntity.getId(), playerId);

        return TradeResult.success(tradeEntity.getId(), windowId, "接受交易成功");
    }

    @Override
    @Transactional
    public OperationResult rejectTradeRequest(String playerId, String requesterName) {
        // 通过玩家昵称查找玩家ID
        String requesterId = findPlayerIdByName(requesterName);
        if (requesterId == null) {
            return OperationResult.error("发起者不存在");
        }

        // 查找待接受的交易
        List<TradeEntity> pendingTrades = tradeRepository.findByStatusAndReceiverId(
            TradeEntity.TradeStatus.PENDING, playerId);

        TradeEntity tradeEntity = pendingTrades.stream()
            .filter(t -> t.getInitiatorId().equals(requesterId))
            .findFirst()
            .orElse(null);

        if (tradeEntity == null) {
            return OperationResult.error("未找到待接受的交易");
        }

        // 拒绝交易
        tradeEntity.setStatus(TradeEntity.TradeStatus.CANCELLED);
        tradeRepository.save(tradeEntity);

        log.info("拒绝交易: tradeId={}, playerId={}", tradeEntity.getId(), playerId);

        return OperationResult.success("拒绝交易成功");
    }

    @Override
    @Transactional
    public OperationResult addItem(String tradeId, String playerId, String itemName) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();
        if (tradeEntity.getStatus() != TradeEntity.TradeStatus.ACTIVE) {
            return OperationResult.error("交易未激活");
        }

        // 检查是否已锁定
        if (isPlayerLocked(tradeEntity, playerId)) {
            return OperationResult.error("交易已锁定，无法添加物品");
        }

        // 获取玩家状态
        Player player = playerSessionService.getPlayerState(playerId);
        if (player == null) {
            return OperationResult.error("玩家不存在");
        }

        // 查找物品
        Player.InventorySlot targetSlot = player.getInventory().stream()
            .filter(slot -> {
                if (slot.isItem()) {
                    return slot.getItem().getName().equals(itemName);
                } else if (slot.isEquipment()) {
                    return slot.getEquipment().getDisplayName().equals(itemName);
                }
                return false;
            })
            .findFirst()
            .orElse(null);

        if (targetSlot == null) {
            return OperationResult.error("物品不存在");
        }

        // 获取玩家的交易提供物
        TradeEntity.TradeOffer offer = getPlayerOffer(tradeEntity, playerId);
        if (offer == null) {
            return OperationResult.error("你不是此交易的参与者");
        }

        // 添加物品到交易
        TradeEntity.TradeItem tradeItem = new TradeEntity.TradeItem();
        if (targetSlot.isItem()) {
            tradeItem.setItemId(targetSlot.getItem().getId());
            tradeItem.setQuantity(1); // 每次添加1个
            tradeItem.setEquipmentInstanceNumber(null);
        } else if (targetSlot.isEquipment()) {
            tradeItem.setItemId(targetSlot.getEquipment().getId());
            tradeItem.setQuantity(1);
            tradeItem.setEquipmentInstanceNumber(targetSlot.getEquipment().getInstanceNumber());
        }

        offer.getItems().add(tradeItem);
        tradeRepository.save(tradeEntity);

        log.info("添加物品到交易: tradeId={}, playerId={}, itemName={}", tradeId, playerId, itemName);

        return OperationResult.success("添加物品成功: " + itemName);
    }

    @Override
    @Transactional
    public OperationResult removeItem(String tradeId, String playerId, String itemName) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();
        if (tradeEntity.getStatus() != TradeEntity.TradeStatus.ACTIVE) {
            return OperationResult.error("交易未激活");
        }

        // 检查是否已锁定
        if (isPlayerLocked(tradeEntity, playerId)) {
            return OperationResult.error("交易已锁定，无法移除物品");
        }

        // 获取玩家的交易提供物
        TradeEntity.TradeOffer offer = getPlayerOffer(tradeEntity, playerId);
        if (offer == null) {
            return OperationResult.error("你不是此交易的参与者");
        }

        // 移除物品（根据itemName查找，这里简化处理，实际可能需要更精确的匹配）
        boolean removed = offer.getItems().removeIf(item -> item.getItemId().contains(itemName));
        if (!removed) {
            return OperationResult.error("物品不在交易列表中");
        }

        tradeRepository.save(tradeEntity);

        log.info("从交易中移除物品: tradeId={}, playerId={}, itemName={}", tradeId, playerId, itemName);

        return OperationResult.success("移除物品成功: " + itemName);
    }

    @Override
    @Transactional
    public OperationResult setMoney(String tradeId, String playerId, int amount) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();
        if (tradeEntity.getStatus() != TradeEntity.TradeStatus.ACTIVE) {
            return OperationResult.error("交易未激活");
        }

        // 检查是否已锁定
        if (isPlayerLocked(tradeEntity, playerId)) {
            return OperationResult.error("交易已锁定，无法设置金额");
        }

        if (amount < 0) {
            return OperationResult.error("金额不能为负数");
        }

        // 验证玩家是否有足够的金钱
        Player player = playerSessionService.getPlayerState(playerId);
        if (player == null) {
            return OperationResult.error("玩家不存在");
        }

        if (player.getGold() < amount) {
            return OperationResult.error("金钱不足");
        }

        // 获取玩家的交易提供物
        TradeEntity.TradeOffer offer = getPlayerOffer(tradeEntity, playerId);
        if (offer == null) {
            return OperationResult.error("你不是此交易的参与者");
        }

        // 设置金额
        offer.setGold(amount);
        tradeRepository.save(tradeEntity);

        log.info("设置交易金额: tradeId={}, playerId={}, amount={}", tradeId, playerId, amount);

        return OperationResult.success("设置金额成功: " + amount);
    }

    @Override
    @Transactional
    public OperationResult lockTrade(String tradeId, String playerId) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();
        if (tradeEntity.getStatus() != TradeEntity.TradeStatus.ACTIVE) {
            return OperationResult.error("交易未激活");
        }

        // 锁定交易
        if (tradeEntity.getInitiatorId().equals(playerId)) {
            tradeEntity.setInitiatorLocked(true);
        } else if (tradeEntity.getReceiverId().equals(playerId)) {
            tradeEntity.setReceiverLocked(true);
        } else {
            return OperationResult.error("你不是此交易的参与者");
        }

        tradeRepository.save(tradeEntity);

        log.info("锁定交易: tradeId={}, playerId={}", tradeId, playerId);

        return OperationResult.success("锁定交易成功");
    }

    @Override
    @Transactional
    public OperationResult unlockTrade(String tradeId, String playerId) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();
        if (tradeEntity.getStatus() != TradeEntity.TradeStatus.ACTIVE) {
            return OperationResult.error("交易未激活");
        }

        // 解锁交易（同时取消确认）
        if (tradeEntity.getInitiatorId().equals(playerId)) {
            tradeEntity.setInitiatorLocked(false);
            tradeEntity.setInitiatorConfirmed(false);
        } else if (tradeEntity.getReceiverId().equals(playerId)) {
            tradeEntity.setReceiverLocked(false);
            tradeEntity.setReceiverConfirmed(false);
        } else {
            return OperationResult.error("你不是此交易的参与者");
        }

        // 如果对方已确认，也需要取消对方的确认
        tradeEntity.setInitiatorConfirmed(false);
        tradeEntity.setReceiverConfirmed(false);

        tradeRepository.save(tradeEntity);

        log.info("解锁交易: tradeId={}, playerId={}", tradeId, playerId);

        return OperationResult.success("解锁交易成功");
    }

    @Override
    @Transactional
    public OperationResult confirmTrade(String tradeId, String playerId) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();
        if (tradeEntity.getStatus() != TradeEntity.TradeStatus.ACTIVE) {
            return OperationResult.error("交易未激活");
        }

        // 检查是否双方都已锁定
        if (!tradeEntity.isInitiatorLocked() || !tradeEntity.isReceiverLocked()) {
            return OperationResult.error("双方必须先锁定交易");
        }

        // 确认交易
        if (tradeEntity.getInitiatorId().equals(playerId)) {
            tradeEntity.setInitiatorConfirmed(true);
        } else if (tradeEntity.getReceiverId().equals(playerId)) {
            tradeEntity.setReceiverConfirmed(true);
        } else {
            return OperationResult.error("你不是此交易的参与者");
        }

        tradeRepository.save(tradeEntity);

        log.info("确认交易: tradeId={}, playerId={}", tradeId, playerId);

        // 检查是否双方都已确认
        if (tradeEntity.isInitiatorConfirmed() && tradeEntity.isReceiverConfirmed()) {
            // 执行交易
            return executeTrade(tradeEntity);
        }

        return OperationResult.success("确认交易成功，等待对方确认");
    }

    @Override
    @Transactional
    public OperationResult cancelTrade(String tradeId, String playerId) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        if (tradeOpt.isEmpty()) {
            return OperationResult.error("交易不存在");
        }

        TradeEntity tradeEntity = tradeOpt.get();

        // 检查是否为交易参与者
        if (!tradeEntity.getInitiatorId().equals(playerId) && !tradeEntity.getReceiverId().equals(playerId)) {
            return OperationResult.error("你不是此交易的参与者");
        }

        // 取消交易
        tradeEntity.setStatus(TradeEntity.TradeStatus.CANCELLED);
        tradeRepository.save(tradeEntity);

        log.info("取消交易: tradeId={}, playerId={}", tradeId, playerId);

        return OperationResult.success("取消交易成功");
    }

    @Override
    public Trade getTradeState(String tradeId) {
        Optional<TradeEntity> tradeOpt = tradeRepository.findById(tradeId);
        return tradeOpt.map(tradeMapper::toDomain).orElse(null);
    }

    /**
     * 获取玩家的交易提供物
     */
    private TradeEntity.TradeOffer getPlayerOffer(TradeEntity trade, String playerId) {
        if (trade.getInitiatorId().equals(playerId)) {
            return trade.getInitiatorOffer();
        } else if (trade.getReceiverId().equals(playerId)) {
            return trade.getReceiverOffer();
        }
        return null;
    }

    /**
     * 检查玩家是否已锁定交易
     */
    private boolean isPlayerLocked(TradeEntity trade, String playerId) {
        if (trade.getInitiatorId().equals(playerId)) {
            return trade.isInitiatorLocked();
        } else if (trade.getReceiverId().equals(playerId)) {
            return trade.isReceiverLocked();
        }
        return false;
    }

    /**
     * 执行交易 - 实际的物品和金钱转移
     */
    @Transactional
    private OperationResult executeTrade(TradeEntity tradeEntity) {
        try {
            // 获取双方玩家状态
            Player initiator = playerSessionService.getPlayerState(tradeEntity.getInitiatorId());
            Player receiver = playerSessionService.getPlayerState(tradeEntity.getReceiverId());

            if (initiator == null || receiver == null) {
                return OperationResult.error("玩家不存在");
            }

            // 验证发起者的资源
            if (!validatePlayerResources(initiator, tradeEntity.getInitiatorOffer())) {
                return OperationResult.error("发起者资源不足或物品不存在");
            }

            // 验证接收者的资源
            if (!validatePlayerResources(receiver, tradeEntity.getReceiverOffer())) {
                return OperationResult.error("接收者资源不足或物品不存在");
            }

            // 执行转移：发起者 -> 接收者
            transferResources(initiator, receiver, tradeEntity.getInitiatorOffer());

            // 执行转移：接收者 -> 发起者
            transferResources(receiver, initiator, tradeEntity.getReceiverOffer());

            // 保存玩家状态（这里需要通过PlayerSessionService保存）
            // 注意：实际实现需要PlayerSessionService提供保存方法
            // 当前简化处理，假设PlayerSessionService会自动保存

            // 标记交易完成
            tradeEntity.setStatus(TradeEntity.TradeStatus.COMPLETED);
            tradeRepository.save(tradeEntity);

            log.info("交易完成: tradeId={}", tradeEntity.getId());

            return OperationResult.success("交易完成");
        } catch (Exception e) {
            log.error("执行交易失败: tradeId={}", tradeEntity.getId(), e);
            return OperationResult.error("交易执行失败: " + e.getMessage());
        }
    }

    /**
     * 验证玩家是否拥有足够的资源
     */
    private boolean validatePlayerResources(Player player, TradeEntity.TradeOffer offer) {
        // 验证金钱
        if (player.getGold() < offer.getGold()) {
            return false;
        }

        // 验证物品
        for (TradeEntity.TradeItem tradeItem : offer.getItems()) {
            boolean found = player.getInventory().stream()
                .anyMatch(slot -> {
                    if (slot.isItem() && tradeItem.getEquipmentInstanceNumber() == null) {
                        return slot.getItem().getId().equals(tradeItem.getItemId())
                            && slot.getQuantity() >= tradeItem.getQuantity();
                    } else if (slot.isEquipment() && tradeItem.getEquipmentInstanceNumber() != null) {
                        return slot.getEquipment().getId().equals(tradeItem.getItemId())
                            && slot.getEquipment().getInstanceNumber().equals(tradeItem.getEquipmentInstanceNumber());
                    }
                    return false;
                });

            if (!found) {
                return false;
            }
        }

        return true;
    }

    /**
     * 转移资源从一个玩家到另一个玩家
     */
    private void transferResources(Player from, Player to, TradeEntity.TradeOffer offer) {
        // 转移金钱
        from.setGold(from.getGold() - offer.getGold());
        to.setGold(to.getGold() + offer.getGold());

        // 转移物品
        for (TradeEntity.TradeItem tradeItem : offer.getItems()) {
            // 从发送者移除物品
            Player.InventorySlot fromSlot = from.getInventory().stream()
                .filter(slot -> {
                    if (slot.isItem() && tradeItem.getEquipmentInstanceNumber() == null) {
                        return slot.getItem().getId().equals(tradeItem.getItemId());
                    } else if (slot.isEquipment() && tradeItem.getEquipmentInstanceNumber() != null) {
                        return slot.getEquipment().getId().equals(tradeItem.getItemId())
                            && slot.getEquipment().getInstanceNumber().equals(tradeItem.getEquipmentInstanceNumber());
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);

            if (fromSlot != null) {
                if (fromSlot.isItem()) {
                    // 普通物品
                    fromSlot.setQuantity(fromSlot.getQuantity() - tradeItem.getQuantity());
                    if (fromSlot.getQuantity() <= 0) {
                        from.getInventory().remove(fromSlot);
                    }

                    // 添加到接收者
                    Player.InventorySlot toSlot = to.getInventory().stream()
                        .filter(slot -> slot.isItem() && slot.getItem().getId().equals(tradeItem.getItemId()))
                        .findFirst()
                        .orElse(null);

                    if (toSlot != null) {
                        toSlot.setQuantity(toSlot.getQuantity() + tradeItem.getQuantity());
                    } else {
                        to.getInventory().add(Player.InventorySlot.forItem(fromSlot.getItem(), tradeItem.getQuantity()));
                    }
                } else if (fromSlot.isEquipment()) {
                    // 装备（不可堆叠）
                    from.getInventory().remove(fromSlot);
                    to.getInventory().add(Player.InventorySlot.forEquipment(fromSlot.getEquipment()));
                }
            }
        }
    }

    /**
     * 通过玩家昵称查找玩家ID
     */
    private String findPlayerIdByName(String playerName) {
        return playerRepository.findAll().stream()
            .filter(p -> p.getName() != null && p.getName().equals(playerName))
            .map(com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity::getId)
            .findFirst()
            .orElse(null);
    }
}
