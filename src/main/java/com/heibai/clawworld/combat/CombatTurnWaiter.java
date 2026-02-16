package com.heibai.clawworld.combat;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 战斗回合等待器
 * 用于实现玩家执行指令后的阻塞等待机制
 *
 * 工作原理：
 * 1. 玩家执行指令后，调用waitForNextTurn阻塞等待
 * 2. 战斗引擎推进行动条时，检测到该玩家回合或战斗结束时，调用notifyTurn唤醒
 * 3. 超时10秒后自动返回"请继续等待"
 */
@Slf4j
public class CombatTurnWaiter {

    // 每个角色的等待锁（key: characterId, value: CountDownLatch）
    private final Map<String, CountDownLatch> waitingCharacters = new ConcurrentHashMap<>();

    // 超时时间：10秒
    private static final long WAIT_TIMEOUT_SECONDS = 10;

    /**
     * 等待下一个回合或战斗结束
     *
     * @param characterId 角色ID
     * @return 等待结果
     */
    public WaitResult waitForNextTurn(String characterId) {
        CountDownLatch latch = new CountDownLatch(1);
        waitingCharacters.put(characterId, latch);

        try {
            log.debug("角色 {} 开始等待下一回合", characterId);

            // 阻塞等待，最多10秒
            boolean notified = latch.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (notified) {
                log.debug("角色 {} 被唤醒", characterId);
                return WaitResult.notified();
            } else {
                log.debug("角色 {} 等待超时", characterId);
                return WaitResult.timeout();
            }
        } catch (InterruptedException e) {
            log.warn("角色 {} 等待被中断", characterId, e);
            Thread.currentThread().interrupt();
            return WaitResult.interrupted();
        } finally {
            waitingCharacters.remove(characterId);
        }
    }

    /**
     * 通知角色轮到他的回合了
     *
     * @param characterId 角色ID
     */
    public void notifyTurn(String characterId) {
        CountDownLatch latch = waitingCharacters.get(characterId);
        if (latch != null) {
            log.debug("通知角色 {} 轮到回合", characterId);
            latch.countDown();
        }
    }

    /**
     * 通知所有等待的角色（战斗结束时调用）
     */
    public void notifyAllWaiting() {
        log.debug("通知所有等待的角色");
        waitingCharacters.values().forEach(CountDownLatch::countDown);
        waitingCharacters.clear();
    }

    /**
     * 检查角色是否在等待
     */
    public boolean isWaiting(String characterId) {
        return waitingCharacters.containsKey(characterId);
    }

    /**
     * 等待结果
     */
    public static class WaitResult {
        private final WaitStatus status;

        private WaitResult(WaitStatus status) {
            this.status = status;
        }

        public static WaitResult notified() {
            return new WaitResult(WaitStatus.NOTIFIED);
        }

        public static WaitResult timeout() {
            return new WaitResult(WaitStatus.TIMEOUT);
        }

        public static WaitResult interrupted() {
            return new WaitResult(WaitStatus.INTERRUPTED);
        }

        public boolean isNotified() {
            return status == WaitStatus.NOTIFIED;
        }

        public boolean isTimeout() {
            return status == WaitStatus.TIMEOUT;
        }

        public boolean isInterrupted() {
            return status == WaitStatus.INTERRUPTED;
        }

        public WaitStatus getStatus() {
            return status;
        }
    }

    public enum WaitStatus {
        NOTIFIED,    // 被通知（轮到回合或战斗结束）
        TIMEOUT,     // 超时
        INTERRUPTED  // 被中断
    }
}
