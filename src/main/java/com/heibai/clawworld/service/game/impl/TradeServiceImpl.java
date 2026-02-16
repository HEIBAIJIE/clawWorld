package com.heibai.clawworld.service.game.impl;

import com.heibai.clawworld.domain.trade.Trade;
import com.heibai.clawworld.service.game.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 交易服务实现（Stub）
 */
@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeService {

    @Override
    public TradeResult requestTrade(String requesterId, String targetPlayerId) {
        // TODO: 实现发起交易逻辑
        return TradeResult.success("trade-id", "window-id", "发起交易");
    }

    @Override
    public TradeResult acceptTradeRequest(String playerId, String requesterId) {
        // TODO: 实现接受交易逻辑
        return TradeResult.success("trade-id", "window-id", "接受交易");
    }

    @Override
    public OperationResult rejectTradeRequest(String playerId, String requesterId) {
        // TODO: 实现拒绝交易逻辑
        return OperationResult.success("拒绝交易");
    }

    @Override
    public OperationResult addItem(String tradeId, String playerId, String itemName) {
        // TODO: 实现添加物品逻辑
        return OperationResult.success("添加物品: " + itemName);
    }

    @Override
    public OperationResult removeItem(String tradeId, String playerId, String itemName) {
        // TODO: 实现移除物品逻辑
        return OperationResult.success("移除物品: " + itemName);
    }

    @Override
    public OperationResult setMoney(String tradeId, String playerId, int amount) {
        // TODO: 实现设置金额逻辑
        return OperationResult.success("设置金额: " + amount);
    }

    @Override
    public OperationResult lockTrade(String tradeId, String playerId) {
        // TODO: 实现锁定交易逻辑
        return OperationResult.success("锁定交易");
    }

    @Override
    public OperationResult unlockTrade(String tradeId, String playerId) {
        // TODO: 实现解锁交易逻辑
        return OperationResult.success("解锁交易");
    }

    @Override
    public OperationResult confirmTrade(String tradeId, String playerId) {
        // TODO: 实现确认交易逻辑
        return OperationResult.success("确认交易");
    }

    @Override
    public OperationResult cancelTrade(String tradeId, String playerId) {
        // TODO: 实现取消交易逻辑
        return OperationResult.success("取消交易");
    }

    @Override
    public Trade getTradeState(String tradeId) {
        // TODO: 实现获取交易状态逻辑
        return null;
    }
}
