package com.heibai.clawworld.application.impl.combat;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.CombatCharacter;
import com.heibai.clawworld.domain.combat.CombatInstance;
import com.heibai.clawworld.domain.combat.CombatParty;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 战斗保护检查器 - 负责检查等级保护和抢怪保护
 */
@Component
@RequiredArgsConstructor
public class CombatProtectionChecker {

    private final ConfigDataManager configDataManager;

    /**
     * 检查结果
     */
    public record CheckResult(boolean allowed, String errorMessage) {
        public static CheckResult ok() {
            return new CheckResult(true, null);
        }

        public static CheckResult denied(String message) {
            return new CheckResult(false, message);
        }
    }

    /**
     * 检查PVP等级保护
     * 根据设计文档：如果目标队伍中所有玩家都小于等于地图推荐等级，则不能攻击
     * 但如果队伍中有任何一个玩家高于保护等级，则整个队伍都可以被攻击
     */
    public CheckResult checkPvpLevelProtection(String mapId, List<Player> targetParty) {
        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return CheckResult.denied("地图不存在");
        }

        Integer recommendedLevel = mapConfig.getRecommendedLevel();
        if (recommendedLevel == null) {
            return CheckResult.ok();
        }

        boolean hasHighLevelPlayer = targetParty.stream()
            .anyMatch(player -> player.getLevel() > recommendedLevel);

        if (!hasHighLevelPlayer) {
            return CheckResult.denied("无法攻击该队伍，队伍中所有玩家等级都不高于地图推荐等级(" + recommendedLevel + "级)");
        }

        return CheckResult.ok();
    }

    /**
     * 检查战斗中的等级保护
     * 目标正在战斗中时，检查队伍是否受保护
     */
    public CheckResult checkInCombatProtection(String mapId, List<Player> targetParty) {
        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return CheckResult.ok();
        }

        Integer recommendedLevel = mapConfig.getRecommendedLevel();
        if (recommendedLevel == null) {
            return CheckResult.ok();
        }

        boolean hasHighLevelPlayer = targetParty.stream()
            .anyMatch(player -> player.getLevel() > recommendedLevel);

        if (!hasHighLevelPlayer) {
            return CheckResult.denied("该队伍正在战斗中且受等级保护，无法加入战斗");
        }

        return CheckResult.ok();
    }

    /**
     * 检查抢怪保护
     * 根据设计文档：如果某个队伍中所有玩家都小于等于地图推荐等级，则该队伍受保护，不允许其他人加入战斗
     */
    public CheckResult checkMonsterStealProtection(String mapId, CombatInstance combat) {
        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return CheckResult.ok();
        }

        Integer recommendedLevel = mapConfig.getRecommendedLevel();
        if (recommendedLevel == null) {
            return CheckResult.ok();
        }

        for (CombatParty party : combat.getParties().values()) {
            // 检查这个队伍是否是玩家队伍
            boolean isPlayerParty = party.getCharacters().stream()
                .anyMatch(c -> c.isPlayer() && c.isAlive());
            if (!isPlayerParty) {
                continue; // 跳过敌人队伍
            }

            // 检查队伍中是否有高等级玩家
            boolean hasHighLevelPlayer = party.getCharacters().stream()
                .filter(c -> c.isPlayer() && c.isAlive())
                .anyMatch(c -> c.getLevel() > recommendedLevel);

            if (!hasHighLevelPlayer) {
                // 这个队伍受保护
                return CheckResult.denied("战斗中有受等级保护的队伍（队伍中所有玩家等级都不高于" + recommendedLevel + "级），无法加入战斗");
            }
        }

        return CheckResult.ok();
    }

    /**
     * 检查地图是否允许战斗
     */
    public CheckResult checkMapAllowsCombat(String mapId) {
        MapConfig mapConfig = configDataManager.getMap(mapId);
        if (mapConfig == null) {
            return CheckResult.denied("地图不存在");
        }

        if (mapConfig.isSafe()) {
            return CheckResult.denied("当前地图不允许战斗");
        }

        return CheckResult.ok();
    }

    /**
     * 检查阵营是否可以攻击
     */
    public CheckResult checkFactionCanAttack(String attackerFaction, String targetFaction) {
        if (attackerFaction.equals(targetFaction)) {
            return CheckResult.denied("不能攻击同阵营角色");
        }
        return CheckResult.ok();
    }
}
