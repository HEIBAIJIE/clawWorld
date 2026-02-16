package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final PlayerRepository playerRepository;
    private final BackgroundPromptService backgroundPromptService;
    private final PlayerMapper playerMapper;

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
            } else {
                // 老用户，进入地图窗口
                account.setCurrentWindowType("MAP");
                account.setCurrentWindowId(null);
            }

            accountRepository.save(account);

            // 获取玩家信息
            Player player = null;
            if (account.getPlayerId() != null) {
                Optional<PlayerEntity> playerEntity = playerRepository.findById(account.getPlayerId());
                if (playerEntity.isPresent()) {
                    player = playerMapper.toDomain(playerEntity.get());
                }
            }

            // 生成背景prompt
            String backgroundPrompt = backgroundPromptService.generateBackgroundPrompt(player);

            return LoginResult.success(sessionId, backgroundPrompt, account.getPlayerId() == null);
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

            accountRepository.save(newAccount);

            // 新用户，生成背景prompt（不包含玩家信息）
            String backgroundPrompt = backgroundPromptService.generateBackgroundPrompt(null);

            return LoginResult.success(sessionId, backgroundPrompt, true);
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
            accountRepository.save(entity);
        }
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 登录结果
     */
    public static class LoginResult {
        private final boolean success;
        private final String message;
        private final String sessionId;
        private final String backgroundPrompt;
        private final boolean isNewUser;

        private LoginResult(boolean success, String message, String sessionId, String backgroundPrompt, boolean isNewUser) {
            this.success = success;
            this.message = message;
            this.sessionId = sessionId;
            this.backgroundPrompt = backgroundPrompt;
            this.isNewUser = isNewUser;
        }

        public static LoginResult success(String sessionId, String backgroundPrompt, boolean isNewUser) {
            return new LoginResult(true, "登录成功", sessionId, backgroundPrompt, isNewUser);
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

        public String getBackgroundPrompt() {
            return backgroundPrompt;
        }

        public boolean isNewUser() {
            return isNewUser;
        }
    }
}
