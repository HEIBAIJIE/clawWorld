package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.application.service.StateService;
import com.heibai.clawworld.domain.chat.ChatMessage;
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

        // 3.1 聊天消息变化
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
                state.append("- 没有新的聊天消息\n");
            }
        } else {
            state.append("- 没有新的聊天消息\n");
        }

        // TODO: 3.2 实体变化（需要实现实体变化追踪）
        // state.append("\n【实体变化】\n");
        // state.append("- 暂未实现\n");

        // TODO: 3.3 交互变化（需要实现交互变化追踪）
        // state.append("\n【交互变化】\n");
        // state.append("- 暂未实现\n");

        // 4. 更新状态时间戳
        updateLastStateTimestamp(playerId);

        return state.toString();
    }

    @Override
    public String generateCombatState(String playerId, String combatId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 战斗窗口的状态相对简单，主要是战斗日志和角色状态变化
        state.append(">>> ").append(commandResult).append("\n\n");

        // TODO: 实现战斗状态的详细信息
        // 1. 战斗日志
        // 2. 角色状态变化
        // 3. 行动条情况
        // 4. 可选操作

        updateLastStateTimestamp(playerId);

        return state.toString();
    }

    @Override
    public String generateTradeState(String playerId, String tradeId, String commandResult) {
        StringBuilder state = new StringBuilder();

        // 交易窗口的状态
        state.append(">>> ").append(commandResult).append("\n\n");

        // TODO: 实现交易状态的详细信息
        // 1. 双方提供的金钱和物品
        // 2. 锁定状态

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
