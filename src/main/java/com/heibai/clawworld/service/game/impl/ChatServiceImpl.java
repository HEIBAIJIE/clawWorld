package com.heibai.clawworld.service.game.impl;

import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.service.game.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 聊天服务实现（Stub）
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    @Override
    public ChatResult sendWorldMessage(String playerId, String message) {
        // TODO: 实现世界频道聊天逻辑
        return ChatResult.success("世界频道: " + message);
    }

    @Override
    public ChatResult sendMapMessage(String playerId, String message) {
        // TODO: 实现地图频道聊天逻辑
        return ChatResult.success("地图频道: " + message);
    }

    @Override
    public ChatResult sendPartyMessage(String playerId, String message) {
        // TODO: 实现队伍频道聊天逻辑
        return ChatResult.success("队伍频道: " + message);
    }

    @Override
    public ChatResult sendPrivateMessage(String senderId, String receiverId, String message) {
        // TODO: 实现私聊逻辑
        return ChatResult.success("私聊 " + receiverId + ": " + message);
    }

    @Override
    public List<ChatMessage> getChatHistory(String playerId) {
        // TODO: 实现获取聊天记录逻辑
        return Collections.emptyList();
    }
}
