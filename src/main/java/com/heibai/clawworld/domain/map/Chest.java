package com.heibai.clawworld.domain.map;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 宝箱实体
 * 宝箱分为两种：
 * - 小宝箱（SMALL）：个人宝箱，每个玩家只能开一次，不影响其他玩家
 * - 大宝箱（LARGE）：服务器宝箱，所有玩家共享状态，开启后需要等待刷新
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Chest extends MapEntity {

    public enum ChestType {
        SMALL,  // 小宝箱（个人）
        LARGE   // 大宝箱（服务器）
    }

    private String chestConfigId;  // 宝箱配置ID
    private ChestType chestType;
    private int respawnSeconds;    // 刷新时间（仅大宝箱）

    // 大宝箱状态
    private boolean opened;        // 是否已被开启（仅大宝箱）
    private long lastOpenTime;     // 上次开启时间（仅大宝箱）

    // 当前玩家是否已开启（用于小宝箱，由查询服��设置）
    private boolean openedByCurrentPlayer;

    public Chest() {
        setDisplayPriority(55);  // 优先级低于传送点(90)但高于篝火(60)和NPC(50)
    }

    @Override
    public boolean isPassable() {
        return true;  // 宝箱不阻止玩家通过
    }

    @Override
    public boolean isInteractable() {
        return true;
    }

    @Override
    public String getEntityType() {
        if (chestType == ChestType.SMALL) {
            return "CHEST_SMALL";
        } else {
            return "CHEST_LARGE";
        }
    }

    @Override
    public List<String> getInteractionOptions() {
        // 如果宝箱对当前玩家不可开启，只显示查看选项
        if (chestType == ChestType.SMALL && openedByCurrentPlayer) {
            return Collections.singletonList("查看");
        }
        if (chestType == ChestType.LARGE && opened && !canOpen()) {
            return Collections.singletonList("查看");
        }
        return Arrays.asList("打开", "查看");
    }

    /**
     * 检查大宝箱是否可以开启（已刷新）
     */
    public boolean canOpen() {
        if (chestType == ChestType.SMALL) {
            return true;  // 小宝箱总是可以开启（由服务层检查玩家是否已开过）
        }
        if (!opened) {
            return true;
        }
        // 检查是否已刷新
        long now = System.currentTimeMillis();
        return now >= lastOpenTime + respawnSeconds * 1000L;
    }

    /**
     * 获取大宝箱剩余刷新时间（秒）
     */
    public int getRemainingRespawnSeconds() {
        if (chestType == ChestType.SMALL || !opened) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long remaining = (lastOpenTime + respawnSeconds * 1000L - now) / 1000;
        return Math.max(0, (int) remaining);
    }
}
