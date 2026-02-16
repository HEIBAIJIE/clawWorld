package com.heibai.clawworld.service;

import com.heibai.clawworld.config.character.RoleConfig;
import com.heibai.clawworld.config.map.MapConfig;
import com.heibai.clawworld.config.map.WaypointConfig;
import com.heibai.clawworld.config.skill.SkillConfig;
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

        // 3. 指令手册
        prompt.append(generateCommandManual());
        prompt.append("\n\n");

        // 4. 地图信息和连接关系
        prompt.append(generateMapInformation());
        prompt.append("\n\n");

        // 5. 如果已有账号，生成玩家信息摘要
        if (player != null) {
            prompt.append(generatePlayerSummary(player));
        } else {
            prompt.append("=== 新玩家 ===\n");
            prompt.append("欢迎来到ClawWorld！你需要先注册角色才能开始游戏。\n");
            prompt.append("请使用 register [职业名称] [昵称] 指令注册你的角色。\n\n");
            prompt.append(generateRoleInformation());
        }

        return prompt.toString();
    }

    /**
     * 生成游戏概述
     */
    private String generateGameOverview() {
        return """
                === ClawWorld 游戏概述 ===
                ClawWorld是一个轻量级、智能体友好的多人在线角色扮演游戏。

                核心系统：
                - 地图系统：多张2D网格地图，分为安全地图和战斗地图
                - 角色系统：玩家、友善NPC、敌人，每个角色都有战斗属性
                - 战斗系统：CTB条件回合制战斗（跑条战斗）
                - 物品系统：装备、消耗品、技能书等
                - 队伍系统：最多4人组队，共享阵营
                - 职业系统：战士、游侠、法师、牧师四大职业
                - 技能系统：每个职业有独特的技能树

                地形与移动：
                - 草地、沙漠、雪地、石头地、浅水可通过
                - 树、岩石、山脉、河流、海洋、墙不可通过
                - 实体可以改变地形通过性（如桥让河流可通过）

                战斗机制：
                - 速度决定行动顺序，基础速度100，进度条总长10000
                - 伤害 = 攻击力 - 防御力（暴击时乘以150%+暴击伤害）
                - 命中率 = 命中率 - 闪避率
                - 战斗最多持续10分钟
                """;
    }

    /**
     * 生成游戏目标
     */
    private String generateGameGoals() {
        return """
                === 游戏目标 ===
                1. 提升等级：通过击败敌人获得经验值，升级后获得5点自由属性点
                2. 提升战斗力：通过装备更好的装备、学习更强的技能来提升战斗力
                3. 积累财富：获取更多的金币，用于购买物品和装备
                4. 收集装备：获取稀有度更高的装备（普通、优秀、稀有、史诗、传说、神话）
                5. 探索世界：探索不同的地图，挑战更强的敌人
                6. 组队协作：与其他玩家组队，挑战地图BOSS和服务器BOSS
                """;
    }

    /**
     * 生成指令手册
     */
    private String generateCommandManual() {
        return """
                === 指令手册 ===

                【注册窗口】
                register [职业名称] [昵称] - 注册职业和昵称

                【地图窗口】
                inspect self - 再次确认自身状态
                inspect [角色名称] - 查看地图上的其他角色
                say [频道] [消息] - 公屏聊天，频道可以为 world, map, party
                say to [玩家名称] [消息] - 私聊
                interact [目标名称] [选项] - 与实体交互，选项会在服务器输出中列出
                move [x] [y] - 移动到指定坐标，支持自动寻路
                use [物品名称] - 使用物品（生命药剂、法力药剂、技能书、洗点药等）
                equip [装备名称] - 装备物品，如果已有对应装备则替换
                attribute add [str/agi/int/vit] [数量] - 加点（力量/敏捷/智力/体力）
                party kick [玩家名称] - 队长踢人
                party end - 队长解散队伍
                party leave - 非队长离队
                wait [秒数] - 原地等待，最多60秒
                leave - 下线

                【战斗窗口】
                cast [技能名称] - 释放非指向技能
                cast [技能名称] [目标名称] - 释放指向技能
                use [物品名称] - 使用物品
                wait - 空过回合
                end - 退出战斗（角色视为死亡）

                【交易窗口】
                trade add [物品名称] - 添加物品到交易框（仅未锁定时）
                trade remove [物品名称] - 从交易框移除物品（仅未锁定时）
                trade money [金额] - 设置交易金额（仅未锁定时）
                trade lock - 锁定交易框
                trade unlock - 取消锁定
                trade confirm - 双方锁定后确认交易
                trade end - 终止交易
                """;
    }

    /**
     * 生成地图信息
     */
    private String generateMapInformation() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 地图信息 ===\n\n");

        Collection<MapConfig> maps = configDataManager.getAllMaps();
        Collection<WaypointConfig> waypoints = configDataManager.getAllWaypoints();

        // 按地图分组传送点
        Map<String, List<WaypointConfig>> waypointsByMap = waypoints.stream()
                .collect(Collectors.groupingBy(WaypointConfig::getMapId));

        for (MapConfig map : maps) {
            sb.append(String.format("【%s】\n", map.getName()));
            sb.append(String.format("  描述：%s\n", map.getDescription()));
            sb.append(String.format("  大小：%d×%d\n", map.getWidth(), map.getHeight()));
            sb.append(String.format("  类型：%s\n", map.isSafe() ? "安全地图" : "战斗地图"));

            if (!map.isSafe() && map.getRecommendedLevel() != null) {
                sb.append(String.format("  推荐等级：%d\n", map.getRecommendedLevel()));
            }

            // 列出该地图的传送点及其连接
            List<WaypointConfig> mapWaypoints = waypointsByMap.get(map.getId());
            if (mapWaypoints != null && !mapWaypoints.isEmpty()) {
                sb.append("  传送点：\n");
                for (WaypointConfig wp : mapWaypoints) {
                    sb.append(String.format("    - %s (%d,%d)", wp.getName(), wp.getX(), wp.getY()));
                    if (wp.getConnectedWaypointIds() != null && !wp.getConnectedWaypointIds().isEmpty()) {
                        sb.append(" -> 可前往：");
                        List<String> targetNames = new ArrayList<>();
                        for (String targetId : wp.getConnectedWaypointIds()) {
                            waypoints.stream()
                                    .filter(w -> w.getId().equals(targetId))
                                    .findFirst()
                                    .ifPresent(w -> {
                                        MapConfig targetMap = configDataManager.getMap(w.getMapId());
                                        targetNames.add(targetMap.getName() + "-" + w.getName());
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
        sb.append("=== 可选职业 ===\n\n");

        Collection<RoleConfig> roles = configDataManager.getAllRoles();
        for (RoleConfig role : roles) {
            sb.append(String.format("【%s】\n", role.getName()));
            sb.append(String.format("  描述：%s\n", role.getDescription()));
            sb.append(String.format("  初始属性：生命%d 法力%d 物攻%d 物防%d 法攻%d 法防%d 速度%d\n",
                    (int)role.getBaseHealth(), (int)role.getBaseMana(),
                    (int)role.getBasePhysicalAttack(), (int)role.getBasePhysicalDefense(),
                    (int)role.getBaseMagicAttack(), (int)role.getBaseMagicDefense(),
                    (int)role.getBaseSpeed()));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成玩家信息摘要
     */
    private String generatePlayerSummary(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 你的角色信息 ===\n\n");

        // 基础信息
        RoleConfig role = configDataManager.getRole(player.getRoleId());
        sb.append(String.format("昵称：%s\n", player.getName()));
        sb.append(String.format("职业：%s\n", role != null ? role.getName() : "未知"));
        sb.append(String.format("等级：%d (经验：%d)\n", player.getLevel(), player.getExperience()));
        sb.append(String.format("金币：%d\n\n", player.getGold()));

        // 四维属性
        sb.append("四维属性：\n");
        sb.append(String.format("  力量：%d  敏捷：%d  智力：%d  体力：%d\n",
                player.getStrength(), player.getAgility(),
                player.getIntelligence(), player.getVitality()));
        if (player.getFreeAttributePoints() > 0) {
            sb.append(String.format("  可分配属性点：%d\n", player.getFreeAttributePoints()));
        }
        sb.append("\n");

        // 战斗属性
        sb.append("战斗属性：\n");
        sb.append(String.format("  生命：%d/%d  法力：%d/%d\n",
                player.getCurrentHealth(), player.getMaxHealth(),
                player.getCurrentMana(), player.getMaxMana()));
        sb.append(String.format("  物理攻击：%d  物理防御：%d\n",
                player.getPhysicalAttack(), player.getPhysicalDefense()));
        sb.append(String.format("  法术攻击：%d  法术防御：%d\n",
                player.getMagicAttack(), player.getMagicDefense()));
        sb.append(String.format("  速度：%d  暴击率：%.1f%%  暴击伤害：%.1f%%\n",
                player.getSpeed(), player.getCritRate() * 100, player.getCritDamage() * 100));
        sb.append(String.format("  命中率：%.1f%%  闪避率：%.1f%%\n\n",
                player.getHitRate() * 100, player.getDodgeRate() * 100));

        // 装备
        sb.append("装备：\n");
        if (player.getEquipment() != null && !player.getEquipment().isEmpty()) {
            for (Map.Entry<Equipment.EquipmentSlot, Equipment> entry : player.getEquipment().entrySet()) {
                Equipment eq = entry.getValue();
                if (eq != null) {
                    sb.append(String.format("  %s：%s#%d (%s)\n",
                            getSlotName(entry.getKey()),
                            eq.getName(),
                            eq.getInstanceNumber(),
                            eq.getRarity().getDisplayName()));
                }
            }
        } else {
            sb.append("  （无装备）\n");
        }
        sb.append("\n");

        // 技能
        sb.append("已学习技能：\n");
        if (player.getSkills() != null && !player.getSkills().isEmpty()) {
            for (String skillId : player.getSkills()) {
                SkillConfig skill = configDataManager.getSkill(skillId);
                if (skill != null) {
                    sb.append(String.format("  - %s：%s\n", skill.getName(), skill.getDescription()));
                }
            }
        } else {
            sb.append("  （仅有普通攻击）\n");
        }
        sb.append("\n");

        // 背包摘要
        sb.append("背包：\n");
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            sb.append(String.format("  已使用 %d/50 个槽位\n", player.getInventory().size()));
        } else {
            sb.append("  （空）\n");
        }

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
