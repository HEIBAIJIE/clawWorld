package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.config.data.map.MapConfig;
import com.heibai.clawworld.infrastructure.config.data.map.WaypointConfig;
import com.heibai.clawworld.infrastructure.config.data.skill.SkillConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 背景日志生成服务
 * 将背景信息转换为统一的日志格式
 */
@Service
@RequiredArgsConstructor
public class BackgroundLogGenerator {

    private final ConfigDataManager configDataManager;

    /**
     * 生成背景日志
     */
    public GameLogBuilder generateBackgroundLogs(Player player) {
        GameLogBuilder builder = new GameLogBuilder();

        // 1. 系统说明
        builder.addBackground("系统说明", "你是ClawWorld游戏中的一个玩家角色。这是一个基于文本指令的多人在线RPG游戏。");

        // 2. 重要规则
        builder.addBackground("重要规则",
            "1. 你必须通过发送指令与游戏交互，不要进行任何解说或评论\n" +
            "2. 每次只发送一条指令，等待服务器响应后再决定下一步行动\n" +
            "3. 严格按照指令格式执行，不要添加额外的文字说明\n" +
            "4. 根据服务器返回的信息做出决策，不要假设或猜测");

        // 3. 游戏机制
        builder.addBackground("游戏机制",
            "- 2D网格地图，分为安全地图（无战斗）和战斗地图（有敌人）\n" +
            "- 回合制战斗系统，速度决定行动顺序\n" +
            "- 四大职业：战士、游侠、法师、牧师\n" +
            "- 可组队（最多4人）、交易、学习技能、装备物品\n" +
            "- 通过战斗获得经验、金币和装备");

        // 4. 游玩建议
        builder.addBackground("游玩建议",
            "- 建议在说话、发起交易、发起组队等玩家交互后等待3-5秒，如果有人回复/响应，会在等待结束后的状态更新中显示");

        // 5. 所有可用指令
        builder.addBackground("所有可用指令", generateAllCommandsText());

        // 6. 地图列表
        builder.addBackground("地图列表", generateMapListText());

        // 7. 玩家状态摘要
        if (player != null) {
            builder.addBackground("你的状态", generatePlayerSummaryText(player));
        } else {
            builder.addBackground("你的状态", "你尚未注册角色");
            builder.addBackground("职业选择", generateRoleSelectionText());
        }

        return builder;
    }

    /**
     * 生成所有指令文本
     */
    private String generateAllCommandsText() {
        return "请注意，不同的窗口可以使用的指令不同，此处列出游戏整体支持的指令供参考\n" +
            "1、注册窗口：\n" +
            "register [职业名称] [昵称]\n" +
            "2、地图窗口\n" +
            "inspect self - 查看自身状态\n" +
            "move [x] [y] - 移动到坐标\n" +
            "interact [目标名称] [选项] - 与NPC/物体交互\n" +
            "use [物品名称] - 使用消耗品/技能书\n" +
            "equip [装备名称] - 装备物品\n" +
            "attribute add [str/agi/int/vit] [数量] - 分配属性点（力量/敏捷/智力/体力）\n" +
            "say [频道] [消息] - 聊天（频道：world/map/party）\n" +
            "say to [玩家名称] [消息] - 私聊\n" +
            "party kick [玩家名称] - 踢出队员（队长）\n" +
            "party end - 解散队伍（队长）\n" +
            "party leave - 离开队伍\n" +
            "wait [秒数] - 等待（最多60秒）\n" +
            "leave - 下线\n" +
            "3、战斗窗口\n" +
            "cast [技能名称] - 释放技能\n" +
            "cast [技能名称] [目标名称] - 对目标释放技能\n" +
            "use [物品名称] - 使用物品\n" +
            "wait - 跳过回合\n" +
            "end - 逃离战斗（角色死亡）\n" +
            "4、交易窗口\n" +
            "trade add [物品名称] - 添加物品\n" +
            "trade remove [物品名称] - 移除物品\n" +
            "trade money [金额] - 设置金额\n" +
            "trade lock - 锁定\n" +
            "trade confirm - 确认交易\n" +
            "trade end - 取消交易\n" +
            "5、商店窗口\n" +
            "shop buy [物品名称] [数量] - 购买商品\n" +
            "shop sell [物品名称] [数量] - 出售物品\n" +
            "shop leave - 离开商店";
    }

    /**
     * 生成地图列表文本
     */
    private String generateMapListText() {
        StringBuilder sb = new StringBuilder();
        sb.append("此处给出服务器所有地图列表，供你进行战略决策\n");

        Collection<MapConfig> maps = configDataManager.getAllMaps();
        Collection<WaypointConfig> waypoints = configDataManager.getAllWaypoints();

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
        }

        return sb.toString();
    }

    /**
     * 生成玩家状态摘要文本
     */
    private String generatePlayerSummaryText(Player player) {
        StringBuilder sb = new StringBuilder();

        RoleConfig role = configDataManager.getRole(player.getRoleId());
        sb.append(String.format("角色：%s (%s) Lv.%d\n",
                player.getName(),
                role != null ? role.getName() : "未知",
                player.getLevel()));
        // 显示经验进度：当前经验/升级所需经验 (百分比%)
        int currentExp = player.getExperience();
        int requiredExp = player.getExperienceForNextLevel();
        int progressPercent = player.getExperienceProgressPercent();
        sb.append(String.format("经验：%d/%d (%d%%)  金币：%d\n\n", currentExp, requiredExp, progressPercent, player.getGold()));

        sb.append(String.format("力量%d 敏捷%d 智力%d 体力%d\n",
                player.getStrength(), player.getAgility(),
                player.getIntelligence(), player.getVitality()));
        sb.append(String.format("生命%d/%d 法力%d/%d\n",
                player.getCurrentHealth(), player.getMaxHealth(),
                player.getCurrentMana(), player.getMaxMana()));
        sb.append(String.format("物攻%d 物防%d 法攻%d 法防%d 速度%d\n\n",
                player.getPhysicalAttack(), player.getPhysicalDefense(),
                player.getMagicAttack(), player.getMagicDefense(),
                player.getSpeed()));

        sb.append("装备：");
        if (player.getEquipment() != null && !player.getEquipment().isEmpty()) {
            List<String> equips = new ArrayList<>();
            for (Map.Entry<Equipment.EquipmentSlot, Equipment> entry : player.getEquipment().entrySet()) {
                Equipment eq = entry.getValue();
                if (eq != null) {
                    equips.add(eq.getDisplayName());
                }
            }
            sb.append(String.join(", ", equips));
        } else {
            sb.append("无");
        }
        sb.append("\n\n");

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

        sb.append(String.format("背包：%d/50",
                player.getInventory() != null ? player.getInventory().size() : 0));

        return sb.toString();
    }

    /**
     * 生成职业选择文本
     */
    private String generateRoleSelectionText() {
        StringBuilder sb = new StringBuilder();
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
}
