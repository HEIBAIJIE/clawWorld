package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.application.service.CharacterInfoService;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PartyService;
import com.heibai.clawworld.domain.character.Character;
import com.heibai.clawworld.domain.character.Party;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.MapTerrainConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图窗口日志生成器
 */
@Service
@RequiredArgsConstructor
public class MapWindowLogGenerator {

    private final CharacterInfoService characterInfoService;
    private final PartyService partyService;
    private final TradeRepository tradeRepository;
    private final MapEntityService mapEntityService;
    private final ConfigDataManager configDataManager;

    /**
     * 生成地图窗口日志
     */
    public void generateMapWindowLogs(GameLogBuilder builder, Player player, GameMap map, List<MapEntity> allEntities, List<ChatMessage> chatHistory) {
        // 预计算可达性地图（一次BFS，后续直接查询）
        java.util.Set<String> reachabilityMap = mapEntityService.calculateReachabilityMap(player.getId());

        // 1. 地图基本信息（包含尺寸和默认地形）
        var mapConfig = configDataManager.getMap(map.getId());
        String defaultTerrain = mapConfig != null ? mapConfig.getDefaultTerrain() : "GRASS";
        String mapInfo = String.format("当前地图：%s（%d×%d），%s%s，默认地形：%s",
            map.getName(),
            map.getWidth(),
            map.getHeight(),
            map.getDescription(),
            map.isSafe() ? "【安全区域】" : String.format("【危险区域】推荐等级: %d", map.getRecommendedLevel()),
            getTerrainDisplayName(defaultTerrain));
        builder.addWindow("地图信息", mapInfo);

        // 2. 特殊地形（矩形区域，节省token）
        String specialTerrain = generateSpecialTerrain(map.getId());
        if (!specialTerrain.isEmpty()) {
            builder.addWindow("特殊地形", specialTerrain);
        }

        // 3. 玩家状态（包含当前位置）
        builder.addWindow("玩家状态", characterInfoService.generatePlayerStatus(player));

        // 4. 技能
        builder.addWindow("技能列表", "你的技能：\n" + characterInfoService.generateSkills(player));

        // 5. 装备
        builder.addWindow("装备栏", "你的装备：\n" + characterInfoService.generateEquipment(player));

        // 6. 背包
        builder.addWindow("背包", "你的背包：\n" + characterInfoService.generateInventory(player));

        // 7. 组队情况
        builder.addWindow("队伍信息", "你的组队情况：\n" + characterInfoService.generatePartyInfo(player));

        // 8. 地图实体
        builder.addWindow("实体列表", map.getName() + "的地图实体：\n" + generateMapEntities(player, allEntities, map, reachabilityMap));

        // 9. 可达目标
        builder.addWindow("可达目标", "你移动后可以交互的实体：\n" + generateReachableTargets(player, allEntities, map, reachabilityMap));

        // 10. 聊天记录
        builder.addWindow("聊天记录", "新增聊天：\n" + generateChatHistory(chatHistory));

        // 注意：可用指令已移至系统上下文，不再在每次窗口刷新时输出
    }

    /**
     * 生成特殊地形信息（矩形区域格式）
     * 格式：地形名称（通过性） 矩形范围(x1,y1)~(x2,y2)
     */
    private String generateSpecialTerrain(String mapId) {
        List<MapTerrainConfig> terrainConfigs = configDataManager.getMapTerrain(mapId);
        if (terrainConfigs == null || terrainConfigs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (MapTerrainConfig tc : terrainConfigs) {
            String terrainName = getTerrainDisplayName(tc.getTerrainTypes());
            boolean passable = isTerrainPassable(tc.getTerrainTypes());
            sb.append(String.format("%s（%s） 矩形范围(%d,%d)~(%d,%d)\n",
                terrainName,
                passable ? "可通过" : "不可通过",
                tc.getX1(), tc.getY1(),
                tc.getX2(), tc.getY2()));
        }
        return sb.toString().trim();
    }

    /**
     * 获取地形的中文显示名称
     */
    private String getTerrainDisplayName(String terrainType) {
        if (terrainType == null) return "未知";
        String firstTerrain = terrainType.split(";")[0].trim();
        var config = configDataManager.getTerrainType(firstTerrain.toUpperCase());
        if (config != null) {
            return config.getName();
        }
        return firstTerrain;
    }

    /**
     * 判断地形是否可通过
     */
    private boolean isTerrainPassable(String terrainType) {
        if (terrainType == null) return true;
        String firstTerrain = terrainType.split(";")[0].trim().toUpperCase();
        var config = configDataManager.getTerrainType(firstTerrain);
        if (config != null) {
            return config.isPassable();
        }
        return true;
    }


    private String generateMapEntities(Player player, List<MapEntity> allEntities, GameMap map, java.util.Set<String> reachabilityMap) {
        StringBuilder sb = new StringBuilder();
        boolean hasEntities = false;
        for (MapEntity entity : allEntities) {
            if (entity.getName().equals(player.getName())) {
                continue;
            }

            hasEntities = true;
            sb.append(entity.getName());

            // 如果是角色类型，显示等级
            if (entity instanceof Character) {
                Character character = (Character) entity;
                sb.append(String.format(" Lv.%d", character.getLevel()));
            }

            sb.append(String.format(" (%d,%d)", entity.getX(), entity.getY()));

            // 检查敌人是否死亡
            if (entity instanceof com.heibai.clawworld.domain.character.Enemy) {
                com.heibai.clawworld.domain.character.Enemy enemy = (com.heibai.clawworld.domain.character.Enemy) entity;
                if (enemy.isDead()) {
                    long remainingSeconds = enemy.getRemainingRespawnSeconds();
                    sb.append(String.format(" [已死亡，%d秒后刷新]", remainingSeconds));
                } else {
                    appendAccessibilityStatus(sb, player, entity, map, reachabilityMap);
                }
            }
            // 检查宝箱状态
            else if (entity instanceof com.heibai.clawworld.domain.map.Chest) {
                com.heibai.clawworld.domain.map.Chest chest = (com.heibai.clawworld.domain.map.Chest) entity;
                if (chest.getChestType() == com.heibai.clawworld.domain.map.Chest.ChestType.SMALL) {
                    // 小宝箱：检查当前玩家是否已开启
                    if (chest.isOpenedByCurrentPlayer()) {
                        sb.append(" [已打开]");
                    } else {
                        appendAccessibilityStatus(sb, player, entity, map, reachabilityMap);
                    }
                } else {
                    // 大宝箱：检查是否已被开启且未刷新
                    if (chest.isOpened() && !chest.canOpen()) {
                        int remainingSeconds = chest.getRemainingRespawnSeconds();
                        sb.append(String.format(" [已打开，%d秒后刷新]", remainingSeconds));
                    } else {
                        appendAccessibilityStatus(sb, player, entity, map, reachabilityMap);
                    }
                }
            } else {
                appendAccessibilityStatus(sb, player, entity, map, reachabilityMap);
            }

            if (entity.getEntityType() != null) {
                sb.append(" [类型：").append(getEntityTypeDisplayName(entity)).append("]");
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

    /**
     * 添加可达性状态
     */
    private void appendAccessibilityStatus(StringBuilder sb, Player player, MapEntity entity, GameMap map, java.util.Set<String> reachabilityMap) {
        int dx = Math.abs(entity.getX() - player.getX());
        int dy = Math.abs(entity.getY() - player.getY());
        if (dx <= 1 && dy <= 1) {
            sb.append(" [可直接交互]");
        } else {
            // 检查是否有可达路径
            int[] nearestPos = findNearestReachablePosition(player, entity, map, reachabilityMap);
            if (nearestPos == null) {
                sb.append(" [无可达路径]");
            } else {
                sb.append(" [需移动至周边交互]");
            }
        }
    }

    private String generateReachableTargets(Player player, List<MapEntity> allEntities, GameMap map, java.util.Set<String> reachabilityMap) {
        StringBuilder sb = new StringBuilder();
        boolean hasReachableTarget = false;
        for (MapEntity entity : allEntities) {
            if (entity.getName().equals(player.getName())) {
                continue;
            }

            int dx = Math.abs(entity.getX() - player.getX());
            int dy = Math.abs(entity.getY() - player.getY());
            if ((dx > 1 || dy > 1) && entity.isInteractable()) {
                // 找到最近的可达位置
                int[] nearestPos = findNearestReachablePosition(player, entity, map, reachabilityMap);
                if (nearestPos != null) {
                    sb.append(String.format("%s: 移动到 (%d,%d) 可交互\n",
                        entity.getName(), nearestPos[0], nearestPos[1]));
                    hasReachableTarget = true;
                }
                // 如果没有可达位置，不显示在这个列表中
            }
        }
        if (!hasReachableTarget) {
            sb.append("没有需要移动才能到达的目标");
        }
        return sb.toString();
    }

    /**
     * 找到最近的可达位置来与实体交互
     * 使用预计算的可达性地图，直接查询而不是每次BFS
     * @param reachabilityMap 预计算的可达性地图
     * @return [x, y] 或 null（如果没有可达位置）
     */
    private int[] findNearestReachablePosition(Player player, MapEntity entity, GameMap map, java.util.Set<String> reachabilityMap) {
        int entityX = entity.getX();
        int entityY = entity.getY();

        // 收集候选目标位置并检查可达性
        int[] nearestPos = null;
        int minDistance = Integer.MAX_VALUE;

        // 如果实体本身可通过，将实体位置作为候选
        if (entity.isPassable()) {
            String key = entityX + "," + entityY;
            if (reachabilityMap.contains(key)) {
                int distance = Math.abs(entityX - player.getX()) + Math.abs(entityY - player.getY());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPos = new int[]{entityX, entityY};
                }
            }
        }

        // 检查周围8格
        int[][] offsets = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0},          {1, 0},
            {-1, 1},  {0, 1},  {1, 1}
        };

        for (int[] offset : offsets) {
            int checkX = entityX + offset[0];
            int checkY = entityY + offset[1];

            // 检查是否在地图范围内
            if (checkX < 0 || checkX >= map.getWidth() || checkY < 0 || checkY >= map.getHeight()) {
                continue;
            }

            String key = checkX + "," + checkY;
            if (reachabilityMap.contains(key)) {
                int distance = Math.abs(checkX - player.getX()) + Math.abs(checkY - player.getY());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPos = new int[]{checkX, checkY};
                }
            }
        }

        return nearestPos;
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

        // 检查是否在同一个队伍
        boolean inSameParty = viewerParty != null && targetParty != null
                && viewerParty.getId().equals(targetParty.getId());

        // 如果不在同一个队伍，才显示组队相关选项
        if (!inSameParty) {
            // 目标没有队伍或只有临时队伍（等待被邀请者加入），可以邀请组队
            if (targetParty == null || targetParty.isSolo()) {
                options.add("邀请组队");
            }

            // 检查是否有来自目标的组队邀请（邀请存储在目标的队伍中）
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

            // 目标有真正的队伍（2人以上），可以请求加入
            if (targetParty != null && !targetParty.isSolo()) {
                options.add("请求加入队伍");
            }

            // 检查是否有来自目标的加入请求（viewer是队长时）
            if (viewerParty != null && viewerParty.isLeader(viewer.getId()) && viewerParty.getPendingRequests() != null) {
                boolean hasRequest = viewerParty.getPendingRequests().stream()
                        .anyMatch(req -> req.getRequesterId().equals(target.getId()) && !req.isExpired());
                if (hasRequest) {
                    options.add("接受组队请求");
                    options.add("拒绝组队请求");
                }
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

    /**
     * 获取实体类型的中文显示名称
     */
    private String getEntityTypeDisplayName(MapEntity entity) {
        String type = entity.getEntityType();
        if (type == null) return "未知";

        return switch (type.toUpperCase()) {
            case "PLAYER" -> "玩家";
            case "NPC" -> "NPC";
            case "WAYPOINT" -> "传送点";
            case "CAMPFIRE" -> "篝火";
            case "CHEST_SMALL" -> "小宝箱";
            case "CHEST_LARGE" -> "大宝箱";
            case "ENEMY" -> {
                // 根据敌人等级细分
                if (entity instanceof com.heibai.clawworld.domain.character.Enemy enemy) {
                    var tier = enemy.getTier();
                    if (tier != null) {
                        yield switch (tier) {
                            case ELITE -> "精英敌人";
                            case MAP_BOSS -> "地图BOSS";
                            case SERVER_BOSS -> "世界BOSS";
                            default -> "普通敌人";
                        };
                    }
                }
                yield "普通敌人";
            }
            default -> type;
        };
    }
}
