package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.combat.Combat;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 战斗窗口日志生成器
 * 根据设计文档生成完整的战斗窗口和状态信息
 */
@Service
@RequiredArgsConstructor
public class CombatWindowLogGenerator {

    private final ConfigDataManager configDataManager;
    private final PlayerSessionService playerSessionService;

    /**
     * 生成战斗窗口日志（进入战斗时显示）
     * 根据设计文档，战斗窗口需要返回���
     * （1）当前战斗一共有哪些方，有哪些角色
     * （2）这些角色的属性、状态等基础信息
     * （3）当前行动条顺序
     * （4）自己的技能，以及技能的冷却情况
     * （5）再次强调战斗中支持的指令
     */
    public void generateCombatWindowLogs(GameLogBuilder builder, Combat combat, String playerId) {
        // 1. 战斗基本信息
        builder.addWindow("战斗窗口", "=== 战斗开始 ===");

        // 2. 参战方信息
        StringBuilder partiesInfo = new StringBuilder();
        partiesInfo.append("【参战方】\n");
        int partyIndex = 1;
        for (Combat.CombatParty party : combat.getParties()) {
            partiesInfo.append(String.format("第%d方（%s）：", partyIndex++, party.getFaction()));
            for (Combat.CombatCharacter character : party.getCharacters()) {
                partiesInfo.append(character.getName()).append(" ");
            }
            partiesInfo.append("\n");
        }
        builder.addWindow("参战方", partiesInfo.toString().trim());

        // 3. 所有参战角色状态
        StringBuilder participantsStatus = new StringBuilder();
        participantsStatus.append("【角色状态】\n");
        for (Combat.CombatParty party : combat.getParties()) {
            for (Combat.CombatCharacter character : party.getCharacters()) {
                String statusIcon = character.getCurrentHealth() <= 0 ? "☠" : "♥";
                participantsStatus.append(String.format("%s %s - HP:%d/%d MP:%d/%d 速度:%d",
                    statusIcon,
                    character.getName(),
                    character.getCurrentHealth(),
                    character.getMaxHealth(),
                    character.getCurrentMana(),
                    character.getMaxMana(),
                    character.getSpeed()));
                if (playerId != null && playerId.equals(character.getCharacterId())) {
                    participantsStatus.append(" (你)");
                }
                participantsStatus.append("\n");
            }
        }
        builder.addWindow("角色状态", participantsStatus.toString().trim());

        // 4. 行动条顺序（使用百分比显示）
        StringBuilder turnOrder = new StringBuilder();
        turnOrder.append("【行动条】\n");
        if (combat.getActionBar() != null && !combat.getActionBar().isEmpty()) {
            int displayCount = Math.min(5, combat.getActionBar().size());
            for (int i = 0; i < displayCount; i++) {
                Combat.ActionBarEntry entry = combat.getActionBar().get(i);
                String characterName = findCharacterName(combat, entry.getCharacterId());
                String marker = "";
                if (playerId != null && playerId.equals(entry.getCharacterId())) {
                    marker = " ← 你";
                }
                // 将进度转换为百分比（10000为100%）
                double percent = entry.getProgress() / 100.0;
                if (i == 0) {
                    turnOrder.append(String.format("→ %d. %s (%.1f%%)%s\n", i + 1, characterName, percent, marker));
                } else {
                    turnOrder.append(String.format("  %d. %s (%.1f%%)%s\n", i + 1, characterName, percent, marker));
                }
            }
        }
        builder.addWindow("行动条", turnOrder.toString().trim());

        // 5. 当前玩家的技能（去重）
        StringBuilder skills = new StringBuilder();
        skills.append("【你的技能】\n");
        // 从玩家状态获取技能列表
        Player player = playerSessionService.getPlayerState(playerId);
        java.util.Set<String> addedSkills = new java.util.HashSet<>();
        if (player != null && player.getSkills() != null && !player.getSkills().isEmpty()) {
            for (String skillId : player.getSkills()) {
                // 跳过普通攻击，最后统一添加
                if ("basic_attack".equals(skillId) || "普通攻击".equals(skillId)) {
                    continue;
                }
                var skillConfig = configDataManager.getSkill(skillId);
                if (skillConfig != null && !addedSkills.contains(skillConfig.getName())) {
                    addedSkills.add(skillConfig.getName());
                    skills.append("- ").append(skillConfig.getName());
                    skills.append(" [").append(getTargetTypeName(skillConfig.getTargetType())).append("]");
                    skills.append(" (消耗:").append(skillConfig.getManaCost()).append("MP");
                    if (skillConfig.getCooldown() > 0) {
                        skills.append(", CD:").append(skillConfig.getCooldown()).append("回合");
                    }
                    skills.append(")\n");
                }
            }
        }
        // 普通攻击总是可用，放在最后
        skills.append("- 普通攻击 [敌方单体] (消耗:0MP, 无CD)\n");
        builder.addWindow("技能列表", skills.toString().trim());

        // 6. 可用指令
        builder.addWindow("可用指令", "【战斗指令】\n" +
            "cast [技能名称] - 释放敌方群体、我方群体或自己的技能；对单体技能则随机选择目标\n" +
            "cast [技能名称] [目标名称] - 释放敌方单体或我方单体的技能\n" +
            "use [物品名称] - 使用物品\n" +
            "wait - 跳过回合\n" +
            "end - 逃离战斗（角色视为死亡）");

        // 7. 当前回合提示
        if (combat.getActionBar() != null && !combat.getActionBar().isEmpty()) {
            String currentTurnId = combat.getActionBar().get(0).getCharacterId();
            if (currentTurnId != null && currentTurnId.equals(playerId)) {
                builder.addWindow("当前状态", "★ 轮到你的回合！请选择行动。");
            } else {
                String currentTurnName = findCharacterName(combat, currentTurnId);
                builder.addWindow("当前状态", "等待 " + currentTurnName + " 行动...");
            }
        }
    }

    /**
     * 生成战斗状态日志（增量）
     * 根据设计文档，战斗窗口的状态包括：
     * （1）这段时间的战斗日志（增量）
     * （2）发生变化的各个角色情况
     * （3）行动条情况
     * （4）自己的可选操作
     *
     * @param lastLogSequence 上次获取的日志序列号，用于增量获取
     * @return 当前最新的日志序列号
     */
    public int generateCombatStateLogs(GameLogBuilder builder, Combat combat, String playerId,
                                       String commandResult, int lastLogSequence) {
        // 1. 指令响应（不再单独添加，因为战斗日志中已经包含了行动信息）
        // 只有当没有新的战斗日志时才显示指令响应
        boolean hasNewLogs = false;

        // 2. 增量战斗日志（每条日志单独作为一条状态日志）
        int currentMaxSequence = lastLogSequence;
        if (combat.getCombatLog() != null && !combat.getCombatLog().isEmpty()) {
            for (String log : combat.getCombatLog()) {
                // 日志格式为 "[#序号] 内容"
                int sequence = parseLogSequence(log);
                if (sequence > lastLogSequence) {
                    hasNewLogs = true;
                    // 每条战斗日志单独添加，这样每条都会有完整的格式
                    builder.addState("战斗日志", log);
                    if (sequence > currentMaxSequence) {
                        currentMaxSequence = sequence;
                    }
                }
            }
        }

        // 如果没有新的战斗日志，显示指令响应
        if (!hasNewLogs) {
            builder.addState("指令响应", commandResult);
        }

        // 3. 角色状态
        StringBuilder statusChanges = new StringBuilder();
        for (Combat.CombatParty party : combat.getParties()) {
            for (Combat.CombatCharacter character : party.getCharacters()) {
                String statusIcon = character.getCurrentHealth() <= 0 ? "☠" : "♥";
                statusChanges.append(String.format("%s %s - HP:%d/%d MP:%d/%d",
                    statusIcon,
                    character.getName(),
                    character.getCurrentHealth(),
                    character.getMaxHealth(),
                    character.getCurrentMana(),
                    character.getMaxMana()));
                if (playerId != null && playerId.equals(character.getCharacterId())) {
                    statusChanges.append(" (你)");
                }
                statusChanges.append("\n");
            }
        }
        builder.addState("角色状态", statusChanges.toString().trim());

        // 4. 行动条更新（使用百分比显示）
        if (combat.getActionBar() != null && !combat.getActionBar().isEmpty()) {
            StringBuilder turnOrderUpdate = new StringBuilder();
            int displayCount = Math.min(5, combat.getActionBar().size());
            for (int i = 0; i < displayCount; i++) {
                Combat.ActionBarEntry entry = combat.getActionBar().get(i);
                String characterName = findCharacterName(combat, entry.getCharacterId());
                String marker = "";
                if (playerId != null && playerId.equals(entry.getCharacterId())) {
                    marker = " ← 你";
                }
                // 将进度转换为百分比（10000为100%）
                double percent = entry.getProgress() / 100.0;
                if (i == 0) {
                    turnOrderUpdate.append(String.format("→ %d. %s (%.1f%%)%s\n", i + 1, characterName, percent, marker));
                } else {
                    turnOrderUpdate.append(String.format("  %d. %s (%.1f%%)%s\n", i + 1, characterName, percent, marker));
                }
            }
            builder.addState("行动条", turnOrderUpdate.toString().trim());
        }

        // 5. 当前回合提示
        if (combat.getActionBar() != null && !combat.getActionBar().isEmpty()) {
            String currentTurnId = combat.getActionBar().get(0).getCharacterId();
            if (currentTurnId != null && currentTurnId.equals(playerId)) {
                builder.addState("当前状态", "★ 轮到你的回合！请选择行动。");
            } else {
                String currentTurnName = findCharacterName(combat, currentTurnId);
                builder.addState("当前状态", "等待 " + currentTurnName + " 行动...");
            }
        }

        return currentMaxSequence;
    }

    /**
     * 解析日志序列号
     * 日志格式为 "[#序号] 内容"
     */
    private int parseLogSequence(String log) {
        if (log == null || !log.startsWith("[#")) {
            return 0;
        }
        int endIndex = log.indexOf(']');
        if (endIndex <= 2) {
            return 0;
        }
        try {
            return Integer.parseInt(log.substring(2, endIndex));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 查找玩家角色
     */
    private Combat.CombatCharacter findPlayerCharacter(Combat combat, String playerId) {
        for (Combat.CombatParty party : combat.getParties()) {
            for (Combat.CombatCharacter character : party.getCharacters()) {
                if (playerId.equals(character.getCharacterId())) {
                    return character;
                }
            }
        }
        return null;
    }

    /**
     * 根据角色ID查找角色名称
     */
    private String findCharacterName(Combat combat, String characterId) {
        for (Combat.CombatParty party : combat.getParties()) {
            for (Combat.CombatCharacter character : party.getCharacters()) {
                if (characterId.equals(character.getCharacterId())) {
                    return character.getName();
                }
            }
        }
        return characterId;
    }

    /**
     * 获取技能目标类型的中文名称
     */
    private String getTargetTypeName(String targetType) {
        if (targetType == null) {
            return "未知";
        }
        return switch (targetType) {
            case "ENEMY_SINGLE" -> "敌方单体";
            case "ENEMY_ALL" -> "敌方群体";
            case "ALLY_SINGLE" -> "我方单体";
            case "ALLY_ALL" -> "我方群体";
            case "SELF" -> "自己";
            default -> targetType;
        };
    }
}
