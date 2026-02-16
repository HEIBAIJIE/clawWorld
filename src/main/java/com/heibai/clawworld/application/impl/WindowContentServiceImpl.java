package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.WindowContentService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 窗口内容生成服务实现
 */
@Service
@RequiredArgsConstructor
public class WindowContentServiceImpl implements WindowContentService {

    private final ConfigDataManager configDataManager;
    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;

    @Override
    public String generateRegisterWindowContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 欢迎来到 ClawWorld ===\n\n");
        sb.append("请选择你的职业并创建角色。\n\n");
        sb.append("可选职业：\n");

        for (RoleConfig role : configDataManager.getAllRoles()) {
            sb.append(String.format("- %s: %s\n", role.getName(), role.getDescription()));
        }

        sb.append("\n使用指令: register [职业名] [角色昵称]\n");
        sb.append("例如: register 战士 张三\n");

        return sb.toString();
    }

    @Override
    public String generateMapWindowContent(Player player, GameMap map) {
        return generateMapWindowContent(player, map, null);
    }

    @Override
    public String generateMapWindowContent(Player player, GameMap map, List<ChatMessage> chatHistory) {
        StringBuilder sb = new StringBuilder();

        // 标题
        sb.append("=== ").append(map.getName()).append(" ===\n");
        sb.append(map.getDescription()).append("\n");
        if (!map.isSafe()) {
            sb.append("【危险区域】推荐等级: ").append(map.getRecommendedLevel()).append("\n");
        } else {
            sb.append("【安全区域】\n");
        }
        sb.append("\n");

        // 获取地图上的所有实体（包括静态实体和动态玩家）
        List<MapEntity> allEntities = new ArrayList<>();
        if (map.getEntities() != null) {
            allEntities.addAll(map.getEntities()); // 静态实体（NPC、敌人、传送点等）
        }
        // 添加当前地图上的所有玩家
        List<PlayerEntity> playersOnMap = playerRepository.findAll().stream()
                .filter(p -> p.getCurrentMapId() != null && p.getCurrentMapId().equals(map.getId()))
                .collect(Collectors.toList());
        for (PlayerEntity p : playersOnMap) {
            Player domainPlayer = playerMapper.toDomain(p);
            allEntities.add(domainPlayer);
        }

        // 地图网格
        sb.append("--- 地图 ---\n");
        if (map.getTerrain() != null && !map.getTerrain().isEmpty()) {
            for (int y = map.getHeight() - 1; y >= 0; y--) {
                for (int x = 0; x < map.getWidth(); x++) {
                    sb.append(String.format("(%d,%d) ", x, y));

                    // 查找该位置的实体（优先显示玩家）
                    MapEntity entityAtPos = null;
                    for (MapEntity entity : allEntities) {
                        if (entity.getX() == x && entity.getY() == y) {
                            // 优先显示玩家
                            if (entityAtPos == null || "PLAYER".equals(entity.getEntityType())) {
                                entityAtPos = entity;
                            }
                        }
                    }

                    if (entityAtPos != null) {
                        sb.append(entityAtPos.getName());
                    } else if (y < map.getTerrain().size() && x < map.getTerrain().get(y).size()) {
                        GameMap.TerrainCell cell = map.getTerrain().get(y).get(x);
                        if (cell.getTerrainTypes() != null && !cell.getTerrainTypes().isEmpty()) {
                            sb.append(cell.getTerrainTypes().get(0));
                        } else {
                            sb.append("空地");
                        }
                    } else {
                        sb.append("空地");
                    }

                    sb.append("  ");
                }
                sb.append("\n");
            }
        } else {
            sb.append("地图数据加载中...\n");
        }
        sb.append("\n");

        // 玩家状态
        sb.append("--- 你的状态 ---\n");
        sb.append(String.format("角色: %s (%s) Lv.%d\n",
            player.getName() != null ? player.getName() : "未命名",
            getRoleName(player.getRoleId()),
            player.getLevel()));
        sb.append(String.format("位置: (%d, %d)\n", player.getX(), player.getY()));
        sb.append(String.format("经验: %d  金币: %d\n", player.getExperience(), player.getGold()));
        sb.append(String.format("力量%d 敏捷%d 智力%d 体力%d\n",
            player.getStrength(), player.getAgility(),
            player.getIntelligence(), player.getVitality()));
        sb.append(String.format("生命%d/%d 法力%d/%d\n",
            player.getCurrentHealth(), player.getMaxHealth(),
            player.getCurrentMana(), player.getMaxMana()));
        sb.append(String.format("物攻%d 物防%d 法攻%d 法防%d 速度%d\n",
            player.getPhysicalAttack(), player.getPhysicalDefense(),
            player.getMagicAttack(), player.getMagicDefense(), player.getSpeed()));
        if (player.getFreeAttributePoints() > 0) {
            sb.append(String.format("可用属性点: %d\n", player.getFreeAttributePoints()));
        }
        sb.append("\n");

        // 技能
        sb.append("--- 技能 ---\n");
        if (player.getSkills() != null && !player.getSkills().isEmpty()) {
            for (String skillId : player.getSkills()) {
                String skillName = getSkillName(skillId);
                sb.append(skillName).append("\n");
            }
        } else {
            sb.append("无技能\n");
        }
        sb.append("\n");

        // 装备
        sb.append("--- 装备 ---\n");
        if (player.getEquipment() != null && !player.getEquipment().isEmpty()) {
            for (Map.Entry<Equipment.EquipmentSlot, Equipment> entry : player.getEquipment().entrySet()) {
                sb.append(String.format("%s: %s\n",
                    getSlotName(entry.getKey()),
                    entry.getValue().getDisplayName()));
            }
        } else {
            sb.append("无装备\n");
        }
        sb.append("\n");

        // 地图实体列表
        sb.append("--- 地图实体 ---\n");
        if (!allEntities.isEmpty()) {
            for (MapEntity entity : allEntities) {
                // 不显示自己
                if (entity.getName().equals(player.getName())) {
                    continue;
                }

                sb.append(String.format("%s (%d,%d)", entity.getName(), entity.getX(), entity.getY()));

                // 计算是否可达（简单判断：曼哈顿距离）
                int distance = Math.abs(entity.getX() - player.getX()) + Math.abs(entity.getY() - player.getY());
                if (distance <= 1) {
                    sb.append(" [可直接交互]");
                } else {
                    sb.append(" [需移动]");
                }

                // 显示实体类型
                if (entity.getEntityType() != null) {
                    sb.append(" [类型：").append(entity.getEntityType()).append("]");
                }

                // 显示交互选项
                if (entity.isInteractable() && entity.getInteractionOptions() != null && !entity.getInteractionOptions().isEmpty()) {
                    sb.append(" [交互选项: ");
                    sb.append(String.join(", ", entity.getInteractionOptions()));
                    sb.append("]");
                }

                sb.append("\n");
            }
        } else {
            sb.append("地图上没有其他实体\n");
        }
        sb.append("\n");

        // 背包
        sb.append("--- 背包 ---\n");
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    sb.append(String.format("%s x%d\n", slot.getItem().getName(), slot.getQuantity()));
                } else if (slot.isEquipment()) {
                    sb.append(String.format("%s\n", slot.getEquipment().getDisplayName()));
                }
            }
        } else {
            sb.append("背包为空\n");
        }
        sb.append("\n");

        // 组队情况
        sb.append("--- 组队情况 ---\n");
        if (player.getPartyId() != null) {
            if (player.isPartyLeader()) {
                sb.append("你是队长\n");
            } else {
                sb.append("你在队伍中\n");
            }
            sb.append("队伍ID: ").append(player.getPartyId()).append("\n");
        } else {
            sb.append("你当前没有队伍\n");
        }
        sb.append("\n");

        // 可达的有意义格子
        sb.append("--- 可达目标 ---\n");
        boolean hasReachableTarget = false;
        for (MapEntity entity : allEntities) {
            // 不显示自己
            if (entity.getName().equals(player.getName())) {
                continue;
            }

            int dx = Math.abs(entity.getX() - player.getX());
            int dy = Math.abs(entity.getY() - player.getY());
            // 如果实体不在周围9格内，但可能是有意义的目标
            if ((dx > 1 || dy > 1) && entity.isInteractable()) {
                sb.append(String.format("- %s: 移动到 (%d,%d) 可交互\n",
                    entity.getName(), entity.getX(), entity.getY()));
                hasReachableTarget = true;
            }
        }
        if (!hasReachableTarget) {
            sb.append("没有需要移动才能到达的目标\n");
        }
        sb.append("\n");

        // 聊天记录
        sb.append("--- 最近聊天 ---\n");
        if (chatHistory != null && !chatHistory.isEmpty()) {
            int count = 0;
            for (ChatMessage msg : chatHistory) {
                if (count >= 10) break; // 最多显示10条
                String channelPrefix = "";
                switch (msg.getChannelType()) {
                    case WORLD:
                        channelPrefix = "[世界]";
                        break;
                    case MAP:
                        channelPrefix = "[地图]";
                        break;
                    case PARTY:
                        channelPrefix = "[队伍]";
                        break;
                    case PRIVATE:
                        channelPrefix = "[私聊]";
                        break;
                }
                sb.append(String.format("%s %s: %s\n", channelPrefix, msg.getSenderNickname(), msg.getMessage()));
                count++;
            }
        } else {
            sb.append("(暂无聊天记录，使用 say 指令发送消息)\n");
        }
        sb.append("\n");

        // 可用指令
        sb.append("--- 可用指令 ---\n");
        sb.append("move [x] [y] - 移动到指定位置\n");
        sb.append("inspect self - 查看自身详细状态\n");
        sb.append("inspect [角色名] - 查看其他角色\n");
        sb.append("interact [目标名] [选项] - 与实体交互\n");
        sb.append("say [频道] [消息] - 聊天 (频道: world/map/party)\n");
        sb.append("use [物品名] - 使用物品\n");
        sb.append("equip [装备名] - 装备物品\n");
        sb.append("attribute add [str/agi/int/vit] [数量] - 加属性点\n");
        sb.append("wait [秒数] - 等待\n");
        sb.append("leave - 下线\n");

        return sb.toString();
    }

    @Override
    public String generateCombatWindowContent(String playerId, String combatId) {
        // TODO: 实现战斗窗口内容生成
        return "战斗窗口内容（待实现）\n";
    }

    @Override
    public String generateTradeWindowContent(String playerId, String tradeId) {
        // TODO: 实现交易窗口内容生成
        return "交易窗口内容（待实现）\n";
    }

    private String getSlotName(Equipment.EquipmentSlot slot) {
        switch (slot) {
            case HEAD: return "头部";
            case CHEST: return "上装";
            case LEGS: return "下装";
            case FEET: return "鞋子";
            case LEFT_HAND: return "左手";
            case RIGHT_HAND: return "右手";
            case ACCESSORY1: return "饰品1";
            case ACCESSORY2: return "饰品2";
            default: return slot.name();
        }
    }

    private String getRoleName(String roleId) {
        if (roleId == null) {
            return "未知";
        }
        RoleConfig roleConfig = configDataManager.getRole(roleId);
        return roleConfig != null ? roleConfig.getName() : "未知";
    }

    private String getSkillName(String skillId) {
        if (skillId == null) {
            return "未知技能";
        }
        if ("basic_attack".equals(skillId)) {
            return "普通攻击";
        }
        var skillConfig = configDataManager.getSkill(skillId);
        return skillConfig != null ? skillConfig.getName() : skillId;
    }
}
