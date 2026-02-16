package com.heibai.clawworld.service.game;

import com.heibai.clawworld.domain.trade.Trade;

/**
 * 交易管理服务
 * 负责玩家之间的交易
 */
public interface TradeService {

    /**
     * 发起交易请求
     * @param requesterId 发起者ID
     * @param targetPlayerId 目标玩家ID
     * @return 交易结果
     */
    TradeResult requestTrade(String requesterId, String targetPlayerId);

    /**
     * 接受交易请求
     * @param playerId 玩家ID
     * @param requesterId 请求者ID
     * @return 交易结果
     */
    TradeResult acceptTradeRequest(String playerId, String requesterId);

    /**
     * 拒绝交易请求
     * @param playerId 玩家ID
     * @param requesterId 请求者ID
     * @return 操作结果
     */
    OperationResult rejectTradeRequest(String playerId, String requesterId);

    /**
     * 添加物品到交易
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @param itemName 物品名称
     * @return 操作结果
     */
    OperationResult addItem(String tradeId, String playerId, String itemName);

    /**
     * 从交易中移除物品
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @param itemName 物品名称
     * @return 操作结果
     */
    OperationResult removeItem(String tradeId, String playerId, String itemName);

    /**
     * 设置交易金额
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @param amount 金额
     * @return 操作结果
     */
    OperationResult setMoney(String tradeId, String playerId, int amount);

    /**
     * 锁定交易
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @return 操作结果
     */
    OperationResult lockTrade(String tradeId, String playerId);

    /**
     * 解锁交易
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @return 操作结果
     */
    OperationResult unlockTrade(String tradeId, String playerId);

    /**
     * 确认交易（双方都锁定后）
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @return 操作结果
     */
    OperationResult confirmTrade(String tradeId, String playerId);

    /**
     * 取消交易
     * @param tradeId 交易ID
     * @param playerId 玩家ID
     * @return 操作结果
     */
    OperationResult cancelTrade(String tradeId, String playerId);

    /**
     * 获取交易状态
     * @param tradeId 交易ID
     * @return 交易对象
     */
    Trade getTradeState(String tradeId);

    /**
     * 交易结果
     */
    class TradeResult {
        private boolean success;
        private String message;
        private String tradeId;
        private String windowId;

        public static TradeResult success(String tradeId, String windowId, String message) {
            TradeResult result = new TradeResult();
            result.success = true;
            result.tradeId = tradeId;
            result.windowId = windowId;
            result.message = message;
            return result;
        }

        public static TradeResult error(String message) {
            TradeResult result = new TradeResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getTradeId() {
            return tradeId;
        }

        public String getWindowId() {
            return windowId;
        }
    }

    /**
     * 操作结果
     */
    class OperationResult {
        private boolean success;
        private String message;
        private boolean tradeCompleted;

        public static OperationResult success(String message) {
            OperationResult result = new OperationResult();
            result.success = true;
            result.message = message;
            return result;
        }

        public static OperationResult tradeCompleted(String message) {
            OperationResult result = new OperationResult();
            result.success = true;
            result.message = message;
            result.tradeCompleted = true;
            return result;
        }

        public static OperationResult error(String message) {
            OperationResult result = new OperationResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isTradeCompleted() {
            return tradeCompleted;
        }
    }
}
