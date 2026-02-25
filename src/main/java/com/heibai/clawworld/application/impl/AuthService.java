package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.domain.service.PlayerLevelService;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.factory.MapInitializationService;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.interfaces.log.GameLogBuilder;
import com.heibai.clawworld.interfaces.log.MapWindowLogGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证服务
 * 处理登录、注册、会话管理
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final PlayerSessionService playerSessionService;
    private final MapWindowLogGenerator mapWindowLogGenerator;
    private final MapInitializationService mapInitializationService;
    private final MapEntityService mapEntityService;
    private final ChatService chatService;
    private final PlayerLevelService playerLevelService;
    private final ConfigDataManager configDataManager;
    private final com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository tradeRepository;
    private final com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository playerRepository;

    /**
     * 登录或注册
     * 根据设计文档：如果用户名密码没有记录，则直接注册；如果有记录但密码不对，则报错；如果有记录且密码对，则允许登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录结果，包含会话ID和背景prompt
     */
    public LoginResult loginOrRegister(String username, String password) {
        // 查找账号
        Optional<AccountEntity> existingAccount = accountRepository.findByUsername(username);

        if (existingAccount.isPresent()) {
            // 账号已存在，验证密码
            AccountEntity account = existingAccount.get();
            if (!account.getPassword().equals(password)) {
                return LoginResult.error("密码错误");
            }

            // 密码正确，登录成功
            String sessionId = generateSessionId();
            account.setSessionId(sessionId);
            account.setOnline(true);
            account.setLastLoginTime(System.currentTimeMillis());

            // 设置初始窗口状态
            if (account.getPlayerId() == null) {
                // 新用户，进入注册窗口
                account.setCurrentWindowType("REGISTER");
                account.setCurrentWindowId(null);
                account.setLastKnownWindowType("REGISTER");
            } else {
                // 老用户，进入地图窗口
                account.setCurrentWindowType("MAP");
                account.setCurrentWindowId(null);
                account.setLastKnownWindowType("MAP");
            }

            accountRepository.save(account);

            // 获取玩家信息（使用 PlayerSessionService 获取完整数据）
            Player player = null;
            if (account.getPlayerId() != null) {
                player = playerSessionService.getPlayerState(account.getPlayerId());

                // 清理玩家的残留交易记录
                cleanupPlayerTrades(account.getPlayerId());

                // 清理玩家的战斗状态（处理异常离线的情况）
                cleanupPlayerCombatState(player);

                // 检查并处理升级（处理离线期间可能获得的经验）
                if (playerLevelService.processLevelUp(player)) {
                    // 升级后保存玩家状态
                    playerSessionService.savePlayerState(player);
                }
                // 重新获取玩家信息（升级后数据可能已更新）
                player = playerSessionService.getPlayerState(account.getPlayerId());
            }

            // 生成窗口内容
            GameLogBuilder windowBuilder = new GameLogBuilder();
            if (player != null) {
                // 已注册用户，生成地图窗口内容
                GameMap map = mapInitializationService.getMap(player.getMapId());
                if (map != null) {
                    // 使用 MapEntityService 获取所有实体（已包含玩家、敌人、NPC、传送点、篝火等）
                    List<MapEntity> allEntities = mapEntityService.getMapEntities(player.getMapId(), player.getId());
                    List<ChatMessage> chatHistory = chatService.getChatHistory(player.getId());
                    mapWindowLogGenerator.generateMapWindowLogs(windowBuilder, player, map, allEntities, chatHistory);
                } else {
                    windowBuilder.addWindow("错误", "地图加载失败，请联系管理员。");
                }
            } else {
                // 未注册用户，生成注册窗口内容
                generateRegisterWindowContent(windowBuilder);
            }

            return LoginResult.success(sessionId, windowBuilder.build(), account.getPlayerId() == null);
        } else {
            // 账号不存在，创建新账号
            AccountEntity newAccount = new AccountEntity();
            newAccount.setUsername(username);
            newAccount.setPassword(password);
            newAccount.setOnline(true);
            newAccount.setLastLoginTime(System.currentTimeMillis());

            String sessionId = generateSessionId();
            newAccount.setSessionId(sessionId);

            // 新用户进入注册窗口
            newAccount.setCurrentWindowType("REGISTER");
            newAccount.setCurrentWindowId(null);
            newAccount.setLastKnownWindowType("REGISTER");

            accountRepository.save(newAccount);

            // 新用户，生成注册窗口内容
            GameLogBuilder windowBuilder = new GameLogBuilder();
            generateRegisterWindowContent(windowBuilder);

            return LoginResult.success(sessionId, windowBuilder.build(), true);
        }
    }

    /**
     * 根据会话ID获取账号
     */
    public Optional<AccountEntity> getAccountBySessionId(String sessionId) {
        return accountRepository.findBySessionId(sessionId);
    }

    /**
     * 登出
     */
    public void logout(String sessionId) {
        Optional<AccountEntity> account = accountRepository.findBySessionId(sessionId);
        if (account.isPresent()) {
            AccountEntity entity = account.get();

            // 清理玩家的残留交易记录
            if (entity.getPlayerId() != null) {
                cleanupPlayerTrades(entity.getPlayerId());
            }

            entity.setOnline(false);
            entity.setLastLogoutTime(System.currentTimeMillis());
            entity.setSessionId(null);
            entity.setCurrentWindowId(null);
            entity.setCurrentWindowType(null);
            accountRepository.save(entity);
        }
    }

    /**
     * 更新窗口状态
     */
    public void updateWindowState(String sessionId, String windowId, String windowType) {
        Optional<AccountEntity> account = accountRepository.findBySessionId(sessionId);
        if (account.isPresent()) {
            AccountEntity entity = account.get();
            entity.setCurrentWindowId(windowId);
            entity.setCurrentWindowType(windowType);
            // 主动窗口变化时，同时更新 lastKnownWindowType
            entity.setLastKnownWindowType(windowType);
            accountRepository.save(entity);
        }
    }

    /**
     * 保存账号实体
     */
    public void saveAccount(AccountEntity account) {
        accountRepository.save(account);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 清理玩家的战斗状态
     * 在玩家上线时调用，处理异常离线导致的战斗状态残留
     */
    private void cleanupPlayerCombatState(Player player) {
        if (player != null && player.isInCombat()) {
            player.setInCombat(false);
            player.setCombatId(null);
            // 保存到数据库
            Optional<com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity> playerEntityOpt =
                playerRepository.findById(player.getId());
            if (playerEntityOpt.isPresent()) {
                com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity playerEntity = playerEntityOpt.get();
                playerEntity.setInCombat(false);
                playerEntity.setCombatId(null);
                playerRepository.save(playerEntity);
            }
        }
    }

    /**
     * 清理玩家的残留交易记录
     * 在玩家上线或下线时调用，清理所有未完成的交易
     */
    private void cleanupPlayerTrades(String playerId) {
        // 查找玩家的所有PENDING和ACTIVE状态的交易
        List<com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity> pendingTrades =
            tradeRepository.findActiveTradesByPlayerId(
                com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity.TradeStatus.PENDING, playerId);
        List<com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity> activeTrades =
            tradeRepository.findActiveTradesByPlayerId(
                com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity.TradeStatus.ACTIVE, playerId);

        // 合并所有需要清理的交易
        List<com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity> allTrades = new ArrayList<>();
        allTrades.addAll(pendingTrades);
        allTrades.addAll(activeTrades);

        // 取消所有交易
        for (com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity trade : allTrades) {
            trade.setStatus(com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity.TradeStatus.CANCELLED);
            tradeRepository.save(trade);

            // 清理双方玩家的tradeId
            String initiatorId = trade.getInitiatorId();
            String receiverId = trade.getReceiverId();

            if (initiatorId != null) {
                Optional<com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity> initiatorOpt =
                    playerRepository.findById(initiatorId);
                if (initiatorOpt.isPresent()) {
                    com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity initiator = initiatorOpt.get();
                    initiator.setTradeId(null);
                    playerRepository.save(initiator);
                }
            }

            if (receiverId != null) {
                Optional<com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity> receiverOpt =
                    playerRepository.findById(receiverId);
                if (receiverOpt.isPresent()) {
                    com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity receiver = receiverOpt.get();
                    receiver.setTradeId(null);
                    playerRepository.save(receiver);
                }
            }
        }
    }

    /**
     * 生成注册窗口内容
     */
    private void generateRegisterWindowContent(GameLogBuilder builder) {
        builder.addWindow("注册窗口", "欢迎来到ClawWorld！");
        builder.addWindow("注册窗口", "请使用指令：register [职业名称] [昵称]");

        // 生成职业选择信息
        StringBuilder roleInfo = new StringBuilder();
        roleInfo.append("可选职业：\n");

        Collection<RoleConfig> roles = configDataManager.getAllRoles();
        for (RoleConfig role : roles) {
            roleInfo.append(String.format("%s - %s\n", role.getName(), role.getDescription()));
            roleInfo.append(String.format("  生命%d 法力%d 物攻%d 物防%d 法攻%d 法防%d 速度%d\n",
                    (int) role.getBaseHealth(), (int) role.getBaseMana(),
                    (int) role.getBasePhysicalAttack(), (int) role.getBasePhysicalDefense(),
                    (int) role.getBaseMagicAttack(), (int) role.getBaseMagicDefense(),
                    (int) role.getBaseSpeed()));
        }

        builder.addWindow("职业选择", roleInfo.toString().trim());
    }

    /**
     * 登录结果
     */
    public static class LoginResult {
        private final boolean success;
        private final String message;
        private final String sessionId;
        private final String content;  // 合并后的完整内容（背景+窗口）
        private final boolean isNewUser;

        private LoginResult(boolean success, String message, String sessionId, String content, boolean isNewUser) {
            this.success = success;
            this.message = message;
            this.sessionId = sessionId;
            this.content = content;
            this.isNewUser = isNewUser;
        }

        public static LoginResult success(String sessionId, String content, boolean isNewUser) {
            return new LoginResult(true, "登录成功", sessionId, content, isNewUser);
        }

        public static LoginResult error(String message) {
            return new LoginResult(false, message, null, null, false);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getContent() {
            return content;
        }

        // 为了兼容性保留这两个方法
        public String getBackgroundPrompt() {
            return content;
        }

        public String getWindowContent() {
            return "";
        }

        public boolean isNewUser() {
            return isNewUser;
        }
    }
}
