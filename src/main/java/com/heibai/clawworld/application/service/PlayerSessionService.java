package com.heibai.clawworld.application.service;

import com.heibai.clawworld.domain.character.Player;

/**
 * 玩家会话管理服务
 * 负责管理玩家的在线状态、窗口状态等
 */
public interface PlayerSessionService {

    /**
     * 注册新玩家
     * @param sessionId 会话ID
     * @param roleName 职业名称
     * @param playerName 玩家昵称
     * @return 注册结果
     */
    SessionResult registerPlayer(String sessionId, String roleName, String playerName);

    /**
     * 获取玩家当前状态
     * @param playerId 玩家ID
     * @return 玩家对象
     */
    Player getPlayerState(String playerId);

    /**
     * 保存玩家状态
     * @param player 玩家对象
     */
    void savePlayerState(Player player);

    /**
     * 使用物品
     * @param playerId 玩家ID
     * @param itemName 物品名称
     * @return 操作结果
     */
    OperationResult useItem(String playerId, String itemName);

    /**
     * 装备物品
     * @param playerId 玩家ID
     * @param itemName 装备名称
     * @return 操作结果
     */
    OperationResult equipItem(String playerId, String itemName);

    /**
     * 添加属性点
     * @param playerId 玩家ID
     * @param attributeType 属性类型 (str/agi/int/vit)
     * @param amount 数量
     * @return 操作结果
     */
    OperationResult addAttribute(String playerId, String attributeType, int amount);

    /**
     * 玩家下线
     * @param sessionId 会话ID
     * @return 操作结果
     */
    OperationResult logout(String sessionId);

    /**
     * 等待指定时间
     * @param playerId 玩家ID
     * @param seconds 等待秒数
     * @return 操作结果
     */
    OperationResult wait(String playerId, int seconds);

    /**
     * 会话结果
     */
    class SessionResult {
        private boolean success;
        private String message;
        private String playerId;
        private String windowId;
        private String windowContent;

        public static SessionResult success(String playerId, String windowId, String message, String windowContent) {
            SessionResult result = new SessionResult();
            result.success = true;
            result.playerId = playerId;
            result.windowId = windowId;
            result.message = message;
            result.windowContent = windowContent;
            return result;
        }

        public static SessionResult error(String message) {
            SessionResult result = new SessionResult();
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

        public String getPlayerId() {
            return playerId;
        }

        public String getWindowId() {
            return windowId;
        }

        public String getWindowContent() {
            return windowContent;
        }
    }

    /**
     * 操作结果
     */
    class OperationResult {
        private boolean success;
        private String message;
        private Object data;

        public static OperationResult success(String message) {
            OperationResult result = new OperationResult();
            result.success = true;
            result.message = message;
            return result;
        }

        public static OperationResult success(String message, Object data) {
            OperationResult result = new OperationResult();
            result.success = true;
            result.message = message;
            result.data = data;
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

        public Object getData() {
            return data;
        }
    }
}
