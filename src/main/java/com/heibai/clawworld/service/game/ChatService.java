package com.heibai.clawworld.service.game;

import com.heibai.clawworld.domain.chat.ChatMessage;

import java.util.List;

/**
 * 聊天服务
 * 负责处理各种聊天频道的消息
 */
public interface ChatService {

    /**
     * 发送世界频道消息
     * @param playerId 玩家ID
     * @param message 消息内容
     * @return 操作结果
     */
    ChatResult sendWorldMessage(String playerId, String message);

    /**
     * 发送地图频道消息
     * @param playerId 玩家ID
     * @param message 消息内容
     * @return 操作结果
     */
    ChatResult sendMapMessage(String playerId, String message);

    /**
     * 发送队伍频道消息
     * @param playerId 玩家ID
     * @param message 消息内容
     * @return 操作结果
     */
    ChatResult sendPartyMessage(String playerId, String message);

    /**
     * 发送私聊消息
     * @param senderId 发送者ID
     * @param targetPlayerName 目标玩家昵称
     * @param message 消息内容
     * @return 操作结果
     */
    ChatResult sendPrivateMessage(String senderId, String targetPlayerName, String message);

    /**
     * 获取玩家的聊天记录
     * 根据设计文档：聊天记录在服务器保留5分钟，每次最多获取50条
     * 优先级：私聊 > 队内 > 本地 > 服务器
     * @param playerId 玩家ID
     * @return 聊天记录列表
     */
    List<ChatMessage> getChatHistory(String playerId);

    /**
     * 聊天结果
     */
    class ChatResult {
        private boolean success;
        private String message;

        public static ChatResult success(String message) {
            ChatResult result = new ChatResult();
            result.success = true;
            result.message = message;
            return result;
        }

        public static ChatResult error(String message) {
            ChatResult result = new ChatResult();
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
    }
}
