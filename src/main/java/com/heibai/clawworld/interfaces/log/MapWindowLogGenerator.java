package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.application.service.PartyService;
import com.heibai.clawworld.domain.character.Party;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 地图窗口日志生成器
 */
@Service
@RequiredArgsConstructor
public class MapWindowLogGenerator {

    private final ConfigDataManager configDataManager;
    private final PartyService partyService;
    private final TradeRepository tradeRepository;

    /**
     * 生成地图窗口日志
     */
    public void generateMapWindowLogs(GameLogBuilder builder, Player player, GameMap map, List<MapEntity> allEntities, List<ChatMessage> chatHistory) {
        // 1. 地图基本信息
        String mapInfo = String.format("当前地图名：%s，%s%s",
            map.getName(),
            map.getDescription(),
            map.isSafe() ? "【安全区域】" : String.format("【危险区域】推荐等级: %d", map.getRecommendedLevel()));
        builder.addWindow("地图窗口", mapInfo);

        // 2. 地图网格
        builder.addWindow("地图窗口", "地图：\n" + generateMapGrid(map, allEntities));

        // 3. 玩家状态
        builder.addWindow("地图窗口", "你的状态：\n" + generatePlayerStatus(player));

        // 4. 技能
        builder.addWindow("地图窗口", "你的技能：\n" + generateSkills(player));

        // 5. 装备
        builder.addWindow("地图窗口", "你的装备：\n" + generateEquipment(player));

        // 6. 背包
        builder.addWindow("地图窗口", "你的背包：\n" + generateInventory(player));

        // 7. 组队情况
        builder.addWindow("地图窗口", "你的组队情况：\n" + generatePartyInfo(player));

        // 8. 地图实体
        builder.addWindow("地图窗口", map.getName() + "的地图实体：\n" + generateMapEntities(player, allEntities, map));

        // 9. 可达目标
        builder.addWindow("地图窗口", "你移动后可以交互的实体：\n" + generateReachableTargets(player, allEntities));

        // 10. 聊天记录
        builder.addWindow("地图窗口", "新增聊天：\n" + generateChatHistory(chatHistory));

        // 11. 可用指令
        builder.addWindow("地图窗口", "当前窗口可用指令：\n" + generateAvailableCommands());
    }

    private String generateMapGrid(GameMap map, List<MapEntity> allEntities) {
        StringBuilder sb = new StringBuilder();
        if (map.getTerrain() != null && !map.getTerrain().isEmpty()) {
            for (int y = map.getHeight() - 1; y >= 0; y--) {
                for (int x = 0; x < map.getWidth(); x++) {
                    sb.append(String.format("(%d,%d) ", x, y));

                    MapEntity entityAtPos = null;
                    for (MapEntity entity : allEntities) {
                        if (entity.getX() == x && entity.getY() == y) {
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
            sb.append("地图数据加载中...");
        }
        return sb.toString();
    }

    private String generatePlayerStatus(Player player) {
        RoleConfig role = configDataManager.getRole(player.getRoleId());
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("角色: %s (%s) Lv.%d\n",
            player.getName() != null ? player.getName() : "未命名",
            role != null ? role.getName() : "未知",
            player.getLevel()));
        sb.append(String.format("位置: (%d, %d)\n", player.getX(), player.getY()));
        // 显示经验进度：当前经验/升级所需经验 (百分比%)
        int currentExp = player.getExperience();
        int requiredExp = player.getExperienceForNextLevel();
        int progressPercent = player.getExperienceProgressPercent();
        sb.append(String.format("经验: %d/%d (%d%%)  金币: %d\n", currentExp, requiredExp, progressPercent, player.getGold()));
        sb.append(String.format("力量%d 敏捷%d 智力%d 体力%d\n",
            player.getStrength(), player.getAgility(),
            player.getIntelligence(), player.getVitality()));
        sb.append(String.format("生命%d/%d 法力%d/%d\n",
            player.getCurrentHealth(), player.getMaxHealth(),
            player.getCurrentMana(), player.getMaxMana()));
        sb.append(String.format("物攻%d 物防%d 法攻%d 法防%d 速度%d",
            player.getPhysicalAttack(), player.getPhysicalDefense(),
            player.getMagicAttack(), player.getMagicDefense(), player.getSpeed()));
        if (player.getFreeAttributePoints() > 0) {
            sb.append(String.format("\n可用属性点: %d", player.getFreeAttributePoints()));
        }
        return sb.toString();
    }

    private String generateSkills(Player player) {
        StringBuilder sb = new StringBuilder();
        // 始终显示普通攻击
        sb.append("- 普通攻击 [敌方单体] (消耗:0MP, 无CD) - 基础物理攻击\n");

        if (player.getSkills() != null && !player.getSkills().isEmpty()) {
            for (String skillId : player.getSkills()) {
                // 跳过普通攻击，已经在上面显示了
                if ("basic_attack".equals(skillId) || "普通攻击".equals(skillId)) {
                    continue;
                }
                var skillConfig = configDataManager.getSkill(skillId);
                if (skillConfig != null) {
                    sb.append("- ").append(skillConfig.getName());
                    sb.append(" [").append(getTargetTypeName(skillConfig.getTargetType())).append("]");
                    sb.append(" (消耗:").append(skillConfig.getManaCost()).append("MP");
                    if (skillConfig.getCooldown() > 0) {
                        sb.append(", CD:").append(skillConfig.getCooldown()).append("回合");
                    } else {
                        sb.append(", 无CD");
                    }
                    sb.append(")");
                    if (skillConfig.getDescription() != null && !skillConfig.getDescription().isEmpty()) {
                        sb.append(" - ").append(skillConfig.getDescription());
                    }
                    sb.append("\n");
                } else {
                    sb.append("- ").append(skillId).append("\n");
                }
            }
        }
        return sb.toString();
    }

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

    private String generateEquipment(Player player) {
        StringBuilder sb = new StringBuilder();
        if (player.getEquipment() != null && !player.getEquipment().isEmpty()) {
            for (Map.Entry<Equipment.EquipmentSlot, Equipment> entry : player.getEquipment().entrySet()) {
                sb.append(String.format("%s: %s\n",
                    getSlotName(entry.getKey()),
                    entry.getValue().getDisplayName()));
            }
        } else {
            sb.append("无装备");
        }
        return sb.toString();
    }

    private String generateInventory(Player player) {
        StringBuilder sb = new StringBuilder();
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    sb.append(String.format("%s x%d\n", slot.getItem().getName(), slot.getQuantity()));
                } else if (slot.isEquipment()) {
                    sb.append(String.format("%s\n", slot.getEquipment().getDisplayName()));
                }
            }
        } else {
            sb.append("背包为空");
        }
        return sb.toString();
    }

    private String generatePartyInfo(Player player) {
        StringBuilder sb = new StringBuilder();
        if (player.getPartyId() != null) {
            if (player.isPartyLeader()) {
                sb.append("你是队长\n");
            } else {
                sb.append("你在队伍中\n");
            }
            sb.append("队伍ID: ").append(player.getPartyId());
        } else {
            sb.append("你当前没有队伍");
        }
        return sb.toString();
    }

    private String generateMapEntities(Player player, List<MapEntity> allEntities, GameMap map) {
        StringBuilder sb = new StringBuilder();
        boolean hasEntities = false;
        for (MapEntity entity : allEntities) {
            if (entity.getName().equals(player.getName())) {
                continue;
            }

            hasEntities = true;
            sb.append(String.format("%s (%d,%d)", entity.getName(), entity.getX(), entity.getY()));

            int distance = Math.abs(entity.getX() - player.getX()) + Math.abs(entity.getY() - player.getY());
            if (distance <= 1) {
                sb.append(" [可直接交互]");
            } else {
                sb.append(" [需移动]");
            }

            if (entity.getEntityType() != null) {
                sb.append(" [类型：").append(entity.getEntityType()).append("]");
            }

            if (entity.isInteractable()) {
                List<String> options = getEntityInteractionOptions(entity, player, map);
                if (options != null && !options.isEmpty()) {
                    sb.append(" [交互选项: ");
                    sb.append(String.join(", ", options));
                    sb.append("]");
                }
            }

            sb.append("\n");
        }

        if (!hasEntities) {
            sb.append("地图上没有其他实体");
        }
        return sb.toString();
    }

    private String generateReachableTargets(Player player, List<MapEntity> allEntities) {
        StringBuilder sb = new StringBuilder();
        boolean hasReachableTarget = false;
        for (MapEntity entity : allEntities) {
            if (entity.getName().equals(player.getName())) {
                continue;
            }

            int dx = Math.abs(entity.getX() - player.getX());
            int dy = Math.abs(entity.getY() - player.getY());
            if ((dx > 1 || dy > 1) && entity.isInteractable()) {
                sb.append(String.format("%s: 移动到 (%d,%d) 可交互\n",
                    entity.getName(), entity.getX(), entity.getY()));
                hasReachableTarget = true;
            }
        }
        if (!hasReachableTarget) {
            sb.append("没有需要移动才能到达的目标");
        }
        return sb.toString();
    }

    private String generateChatHistory(List<ChatMessage> chatHistory) {
        StringBuilder sb = new StringBuilder();
        if (chatHistory != null && !chatHistory.isEmpty()) {
            int count = 0;
            for (ChatMessage msg : chatHistory) {
                if (count >= 10) break;
                String channelPrefix = switch (msg.getChannelType()) {
                    case WORLD -> "[世界]";
                    case MAP -> "[地图]";
                    case PARTY -> "[队伍]";
                    case PRIVATE -> "[私聊]";
                };
                sb.append(String.format("%s %s: %s\n", channelPrefix, msg.getSenderNickname(), msg.getMessage()));
                count++;
            }
        } else {
            sb.append("(暂无聊天记录，使用 say 指令发送消息)");
        }
        return sb.toString();
    }

    private String generateAvailableCommands() {
        return "inspect self - 查看自身状态\n" +
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
            "leave - 下线";
    }

    private List<String> getEntityInteractionOptions(MapEntity entity, Player viewer, GameMap map) {
        List<String> options = new ArrayList<>(entity.getInteractionOptions(viewer.getFaction(), map.isSafe()));

        if ("PLAYER".equals(entity.getEntityType()) && entity instanceof Player) {
            Player targetPlayer = (Player) entity;
            addPlayerSpecificOptions(options, viewer, targetPlayer);
        }

        return options;
    }

    private void addPlayerSpecificOptions(List<String> options, Player viewer, Player target) {
        Party viewerParty = partyService.getPlayerParty(viewer.getId());
        Party targetParty = partyService.getPlayerParty(target.getId());

        if (targetParty == null || targetParty.isSolo()) {
            options.add("邀请组队");
        }

        if (targetParty != null && targetParty.getPendingInvitations() != null) {
            boolean hasInvitation = targetParty.getPendingInvitations().stream()
                    .anyMatch(inv -> inv.getInviterId().equals(target.getId())
                            && inv.getInviteeId().equals(viewer.getId())
                            && !inv.isExpired());
            if (hasInvitation) {
                options.add("接受组队邀请");
                options.add("拒绝组队邀请");
            }
        }

        if (targetParty != null && !targetParty.isSolo()) {
            options.add("请求加入队伍");
        }

        if (viewerParty != null && viewerParty.isLeader(viewer.getId()) && viewerParty.getPendingRequests() != null) {
            boolean hasRequest = viewerParty.getPendingRequests().stream()
                    .anyMatch(req -> req.getRequesterId().equals(target.getId()) && !req.isExpired());
            if (hasRequest) {
                options.add("接受组队请求");
                options.add("拒绝组队请求");
            }
        }

        List<TradeEntity> activeTrades = tradeRepository.findActiveTradesByPlayerId(
                TradeEntity.TradeStatus.ACTIVE, viewer.getId());
        List<TradeEntity> pendingTrades = tradeRepository.findActiveTradesByPlayerId(
                TradeEntity.TradeStatus.PENDING, viewer.getId());

        if (activeTrades.isEmpty() && pendingTrades.isEmpty()) {
            options.add("请求交易");
        }

        List<TradeEntity> targetPendingTrades = tradeRepository.findByStatusAndReceiverId(
                TradeEntity.TradeStatus.PENDING, viewer.getId());
        boolean hasTradeRequest = targetPendingTrades.stream()
                .anyMatch(t -> t.getInitiatorId().equals(target.getId()));
        if (hasTradeRequest) {
            options.add("接受交易请求");
            options.add("拒绝交易请求");
        }
    }

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
