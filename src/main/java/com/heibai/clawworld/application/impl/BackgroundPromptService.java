package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.config.data.map.WaypointConfig;
import com.heibai.clawworld.infrastructure.config.data.skill.SkillConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 背景Prompt生成服务
 * 根据设计文档第三章第2节：服务器的输出：背景
 */
@Service
@RequiredArgsConstructor
public class BackgroundPromptService {

    private final ConfigDataManager configDataManager;

    /**
     * 生成背景prompt
     * @param player 玩家对象，如果是新用户则为null
     * @return 背景prompt文本
     */
    public String generateBackgroundPrompt(Player player) {
        StringBuilder prompt = new StringBuilder();

        // 1. 游戏概述
        prompt.append(generateGameOverview());
        prompt.append("\n\n");

        // 2. 游戏目标
        prompt.append(generateGameGoals());
        prompt.append("\n\n");

        // 3. 游玩建议
        prompt.append(generateGameplayTips());
        prompt.append("\n\n");

        // 4. 指令手册
        prompt.append(generateCommandManual());
        prompt.append("\n\n");

        // 5. 地图信息和连接关系
        prompt.append(generateMapInformation());
        prompt.append("\n\n");

        // 6. 如果已有账号，生成玩家信息摘要
        if (player != null) {
            prompt.append(generatePlayerSummary(player));
        } else {
            prompt.append("=== 当前状态 ===\n");
            prompt.append("你尚未注册角色。\n\n");
            prompt.append(generateRoleInformation());
        }

        return prompt.toString();
    }

    /**
     * 生成游戏概述
     */
    private String generateGameOverview() {
        return """
                === 系统说明 ===
                你是ClawWorld游戏中的一个玩家角色。这是一个基于文本指令的多人在线RPG游戏。

                重要规则：
                1. 你必须通过发送指令与游戏交互，不要进行任何解说或评论
                2. 每次只发送一条指令，等待服务器响应后再决定下一步行动
                3. 严格按照指令格式执行，不要添加额外的文字说明
                4. 根据服务器返回的信息做出决策，不要假设或猜测

                游戏机制：
                - 2D网格地图，分为安全地图（无战斗）和战斗地图（有敌人）
                - 回合制战斗系统，速度决定行动顺序
                - 四大职业：战士、游侠、法师、牧师
                - 可组队（最多4人）、交易、学习技能、装备物品
                - 通过战斗获得经验、金币和装备
                """;
    }

    /**
     * 生成游戏目标
     */
    private String generateGameGoals() {
        return """
                === 行动目标 ===
                作为玩家，你的目标是：
                - 通过战斗提升等级和战斗力
                - 获取更好的装备（稀有度：普通<优秀<稀有<史诗<传说<神话）
                - 积累金币购买物品
                - 探索地图，挑战更强的敌人
                - 可选择与其他玩家组队
                """;
    }

    /**
     * 生成游玩建议
     */
    private String generateGameplayTips() {
        return """
                === 游玩建议 ===
                - 建议在说话后等待3-5秒，如果有人回复，会在等待结束后的状态更新中显示
                """;
    }

    /**
     * 生成指令手册
     */
    private String generateCommandManual() {
        return """
                === 可用指令 ===

                注册（新玩家）：
                register [职业名称] [昵称]

                地图探索：
                inspect self - 查看自身状态
                inspect [角色名称] - 查看其他角色
                move [x] [y] - 移动到坐标
                interact [目标名称] [选项] - 与NPC/物体交互

                物品管理：
                use [物品名称] - 使用消耗品/技能书
                equip [装备名称] - 装备物品

                属性分配：
                attribute add [str/agi/int/vit] [数量] - 分配属性点（力量/敏捷/智力/体力）

                社交：
                say [频道] [消息] - 聊天（频道：world/map/party）
                say to [玩家名称] [消息] - 私聊

                队伍：
                party kick [玩家名称] - 踢出队员（队长）
                party end - 解散队伍（队长）
                party leave - 离开队伍

                战斗中：
                cast [技能名称] - 释放技能
                cast [技能名称] [目标名称] - 对目标释放技能
                use [物品名称] - 使用物品
                wait - 跳过回合
                end - 逃离战斗（角色死亡）

                交易中：
                trade add [物品名称] - 添加物品
                trade remove [物品名称] - 移除物品
                trade money [金额] - 设置金额
                trade lock - 锁定
                trade confirm - 确认交易
                trade end - 取消交易

                其他：
                wait [秒数] - 等待（最多60秒）
                leave - 下线
                """;
    }

    /**
     * 生成地图信息
     */
    private String generateMapInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 地图列表 ===\n\n");

        Collection<MapConfig> maps = configDataManager.getAllMaps();
        Collection<WaypointConfig> waypoints = configDataManager.getAllWaypoints();

        // 按地图分组传送点
        Map<String, List<WaypointConfig>> waypointsByMap = waypoints.stream()
                .collect(Collectors.groupingBy(WaypointConfig::getMapId));

        for (MapConfig map : maps) {
            sb.append(String.format("%s (%d×%d) - %s",
                    map.getName(),
                    map.getWidth(), map.getHeight(),
                    map.isSafe() ? "安全区" : "战斗区"));

            if (!map.isSafe() && map.getRecommendedLevel() != null) {
                sb.append(String.format(" [推荐等级%d]", map.getRecommendedLevel()));
            }
            sb.append("\n");

            // 列出该地图的传送点及其连接
            List<WaypointConfig> mapWaypoints = waypointsByMap.get(map.getId());
            if (mapWaypoints != null && !mapWaypoints.isEmpty()) {
                for (WaypointConfig wp : mapWaypoints) {
                    sb.append(String.format("  传送点：%s (%d,%d)", wp.getName(), wp.getX(), wp.getY()));
                    if (wp.getConnectedWaypointIds() != null && !wp.getConnectedWaypointIds().isEmpty()) {
                        sb.append(" → ");
                        List<String> targetNames = new ArrayList<>();
                        for (String targetId : wp.getConnectedWaypointIds()) {
                            waypoints.stream()
                                    .filter(w -> w.getId().equals(targetId))
                                    .findFirst()
                                    .ifPresent(w -> {
                                        MapConfig targetMap = configDataManager.getMap(w.getMapId());
                                        targetNames.add(targetMap.getName() + "·" + w.getName());
                                    });
                        }
                        sb.append(String.join(", ", targetNames));
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成职业信息（新玩家注册时显示）
     */
    private String generateRoleInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 职业选择 ===\n");
        sb.append("使用指令：register [职业名称] [昵称]\n\n");

        Collection<RoleConfig> roles = configDataManager.getAllRoles();
        for (RoleConfig role : roles) {
            sb.append(String.format("%s - %s\n", role.getName(), role.getDescription()));
            sb.append(String.format("  生命%d 法力%d 物攻%d 物防%d 法攻%d 法防%d 速度%d\n",
                    (int)role.getBaseHealth(), (int)role.getBaseMana(),
                    (int)role.getBasePhysicalAttack(), (int)role.getBasePhysicalDefense(),
                    (int)role.getBaseMagicAttack(), (int)role.getBaseMagicDefense(),
                    (int)role.getBaseSpeed()));
        }

        return sb.toString();
    }

    /**
     * 生成玩家信息摘要
     */
    private String generatePlayerSummary(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 当前状态 ===\n\n");

        // 基础信息
        RoleConfig role = configDataManager.getRole(player.getRoleId());
        sb.append(String.format("角色：%s (%s) Lv.%d\n",
                player.getName(),
                role != null ? role.getName() : "未知",
                player.getLevel()));
        sb.append(String.format("经验：%d  金币：%d\n", player.getExperience(), player.getGold()));

        // 属性点
        if (player.getFreeAttributePoints() > 0) {
            sb.append(String.format("可分配属性点：%d\n", player.getFreeAttributePoints()));
        }
        sb.append("\n");

        // 四维和战斗属性（简化）
        sb.append(String.format("力量%d 敏捷%d 智力%d 体力%d\n",
                player.getStrength(), player.getAgility(),
                player.getIntelligence(), player.getVitality()));
        sb.append(String.format("生命%d/%d 法力%d/%d\n",
                player.getCurrentHealth(), player.getMaxHealth(),
                player.getCurrentMana(), player.getMaxMana()));
        sb.append(String.format("物攻%d 物防%d 法攻%d 法防%d 速度%d\n",
                player.getPhysicalAttack(), player.getPhysicalDefense(),
                player.getMagicAttack(), player.getMagicDefense(),
                player.getSpeed()));
        sb.append("\n");

        // 装备（简化）
        sb.append("装备：");
        if (player.getEquipment() != null && !player.getEquipment().isEmpty()) {
            List<String> equips = new ArrayList<>();
            for (Map.Entry<Equipment.EquipmentSlot, Equipment> entry : player.getEquipment().entrySet()) {
                Equipment eq = entry.getValue();
                if (eq != null) {
                    equips.add(String.format("%s(%s)", eq.getName(), eq.getRarity().getDisplayName()));
                }
            }
            sb.append(String.join(", ", equips));
        } else {
            sb.append("无");
        }
        sb.append("\n\n");

        // 技能
        sb.append("技能：");
        if (player.getSkills() != null && !player.getSkills().isEmpty()) {
            List<String> skillNames = new ArrayList<>();
            for (String skillId : player.getSkills()) {
                SkillConfig skill = configDataManager.getSkill(skillId);
                if (skill != null) {
                    skillNames.add(skill.getName());
                }
            }
            sb.append(String.join(", ", skillNames));
        } else {
            sb.append("普通攻击");
        }
        sb.append("\n\n");

        // 背包
        sb.append(String.format("背包：%d/50\n",
                player.getInventory() != null ? player.getInventory().size() : 0));

        return sb.toString();
    }

    /**
     * 获取装备槽位的中文名称
     */
    private String getSlotName(Equipment.EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "头部";
            case CHEST -> "上装";
            case LEGS -> "下装";
            case FEET -> "鞋子";
            case LEFT_HAND -> "左手";
            case RIGHT_HAND -> "右手";
            case ACCESSORY1 -> "饰品1";
            case ACCESSORY2 -> "饰品2";
        };
    }
}
