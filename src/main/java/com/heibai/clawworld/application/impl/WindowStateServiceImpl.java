package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.WindowStateService;
import com.heibai.clawworld.domain.window.WindowTransition;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 窗口状态服务实现
 * 实现窗口状态机逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WindowStateServiceImpl implements WindowStateService {

    private final AccountRepository accountRepository;

    // 定义合法的窗口转换
    private static final Map<String, Set<String>> VALID_TRANSITIONS = new HashMap<>();

    static {
        // REGISTER -> MAP (注册完成)
        VALID_TRANSITIONS.put("REGISTER", Set.of("MAP"));

        // MAP -> TRADE, COMBAT, SHOP (从地图进入其他窗口)
        VALID_TRANSITIONS.put("MAP", Set.of("TRADE", "COMBAT", "SHOP"));

        // TRADE -> MAP (交易完成/取消)
        VALID_TRANSITIONS.put("TRADE", Set.of("MAP"));

        // COMBAT -> MAP (战斗结束/放弃)
        VALID_TRANSITIONS.put("COMBAT", Set.of("MAP"));

        // SHOP -> MAP (商店退出)
        VALID_TRANSITIONS.put("SHOP", Set.of("MAP"));
    }

    @Override
    @Transactional
    public boolean transitionWindow(String playerId, String toWindow, String windowId) {
        Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
        if (accountOpt.isEmpty()) {
            log.warn("玩家账号不存在: playerId={}", playerId);
            return false;
        }

        AccountEntity account = accountOpt.get();
        String fromWindow = account.getCurrentWindowType();

        // 验证转换是否合法
        if (!validateTransition(playerId, fromWindow, toWindow)) {
            log.warn("非法窗口转换: playerId={}, from={}, to={}", playerId, fromWindow, toWindow);
            return false;
        }

        // 执行转换
        account.setCurrentWindowType(toWindow);
        account.setCurrentWindowId(windowId);
        accountRepository.save(account);

        log.info("窗口转换成功: playerId={}, from={}, to={}, windowId={}", playerId, fromWindow, toWindow, windowId);
        return true;
    }

    @Override
    @Transactional
    public boolean transitionWindows(List<WindowTransition> transitions) {
        if (transitions == null || transitions.isEmpty()) {
            return true;
        }

        // 验证所有转换是否合法
        for (WindowTransition transition : transitions) {
            if (!validateTransition(transition.getPlayerId(), transition.getFromWindow(), transition.getToWindow())) {
                log.warn("非法窗口转换: playerId={}, from={}, to={}",
                    transition.getPlayerId(), transition.getFromWindow(), transition.getToWindow());
                return false;
            }
        }

        // 执行所有转换
        for (WindowTransition transition : transitions) {
            Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(transition.getPlayerId());
            if (accountOpt.isEmpty()) {
                log.warn("玩家账号不存在: playerId={}", transition.getPlayerId());
                return false;
            }

            AccountEntity account = accountOpt.get();
            account.setCurrentWindowType(transition.getToWindow());
            account.setCurrentWindowId(transition.getWindowId());
            accountRepository.save(account);

            log.info("窗口转换成功: playerId={}, from={}, to={}, windowId={}",
                transition.getPlayerId(), transition.getFromWindow(), transition.getToWindow(), transition.getWindowId());
        }

        return true;
    }

    @Override
    public boolean validateTransition(String playerId, String fromWindow, String toWindow) {
        // 如果源窗口为空，默认为MAP
        if (fromWindow == null || fromWindow.isEmpty()) {
            fromWindow = "MAP";
        }

        // 如果目标窗口为空，不合法
        if (toWindow == null || toWindow.isEmpty()) {
            return false;
        }

        // 相同窗口类型，允许转换（用于刷新窗口ID）
        if (fromWindow.equals(toWindow)) {
            return true;
        }

        // 检查是否为合法转换
        Set<String> allowedTargets = VALID_TRANSITIONS.get(fromWindow);
        if (allowedTargets == null) {
            log.warn("未知的源窗口类型: {}", fromWindow);
            return false;
        }

        return allowedTargets.contains(toWindow);
    }

    @Override
    public String getCurrentWindowType(String playerId) {
        Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
        if (accountOpt.isEmpty()) {
            return null;
        }

        String windowType = accountOpt.get().getCurrentWindowType();
        return windowType != null ? windowType : "MAP";
    }
}
