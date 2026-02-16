package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.WindowContentService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 窗口内容生成服务实现
 */
@Service
@RequiredArgsConstructor
public class WindowContentServiceImpl implements WindowContentService {

    private final ConfigDataManager configDataManager;

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

        // 地图网格
        sb.append("--- 地图 ---\n");
        if (map.getTerrain() != null && !map.getTerrain().isEmpty()) {
            for (int y = map.getHeight() - 1; y >= 0; y--) {
                for (int x = 0; x < map.getWidth(); x++) {
                    sb.append(String.format("(%d,%d) ", x, y));

                    // 查找该位置的实体
                    MapEntity entityAtPos = null;
                    if (map.getEntities() != null) {
                        for (MapEntity entity : map.getEntities()) {
                            if (entity.getX() == x && entity.getY() == y) {
                                entityAtPos = entity;
                                break;
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
        sb.append(String.format("位置: (%d, %d)\n", player.getX(), player.getY()));
        sb.append(String.format("等级: %d  经验: %d\n", player.getLevel(), player.getExperience()));
        sb.append(String.format("生命: %d/%d  法力: %d/%d\n",
            player.getCurrentHealth(), player.getMaxHealth(),
            player.getCurrentMana(), player.getMaxMana()));
        sb.append(String.format("金币: %d\n", player.getGold()));
        sb.append(String.format("力量: %d  敏捷: %d  智力: %d  体力: %d\n",
            player.getStrength(), player.getAgility(),
            player.getIntelligence(), player.getVitality()));
        if (player.getFreeAttributePoints() > 0) {
            sb.append(String.format("可用属性点: %d\n", player.getFreeAttributePoints()));
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
}
