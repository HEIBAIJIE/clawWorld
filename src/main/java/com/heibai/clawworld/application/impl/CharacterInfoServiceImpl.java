package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.CharacterInfoService;
import com.heibai.clawworld.application.service.PartyService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Party;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.config.data.item.EquipmentConfig;
import com.heibai.clawworld.infrastructure.config.data.item.ItemConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 角色信息服务实现
 * 统一生成角色状态、技能、装备、背包、队伍等信息的展示文本
 */
@Service
@RequiredArgsConstructor
public class CharacterInfoServiceImpl implements CharacterInfoService {

    private final ConfigDataManager configDataManager;
    private final PartyService partyService;
    private final PlayerRepository playerRepository;

    @Override
    public String generatePlayerStatus(Player player) {
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
        sb.append(String.format("物攻%d 物防%d 法攻%d 法防%d 速度%d\n",
            player.getPhysicalAttack(), player.getPhysicalDefense(),
            player.getMagicAttack(), player.getMagicDefense(), player.getSpeed()));
        sb.append(String.format("暴击率%.1f%% 暴击伤害%.1f%% 命中率%.1f%% 闪避率%.1f%%",
            player.getCritRate() * 100, player.getCritDamage() * 100,
            player.getHitRate() * 100, player.getDodgeRate() * 100));
        if (player.getFreeAttributePoints() > 0) {
            sb.append(String.format("\n可用属性点: %d", player.getFreeAttributePoints()));
        }
        return sb.toString();
    }

    @Override
    public String generateSkills(Player player) {
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

    @Override
    public String generateEquipment(Player player) {
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

    @Override
    public String generateInventory(Player player) {
        StringBuilder sb = new StringBuilder();
        // 先显示金币
        sb.append(String.format("金币: %d\n", player.getGold()));
        // 再显示背包物品
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    sb.append(String.format("%s x%d\n", slot.getItem().getName(), slot.getQuantity()));
                } else if (slot.isEquipment()) {
                    // 装备显示格式: [槽位]装备名#编号
                    Equipment eq = slot.getEquipment();
                    String slotName = getSlotName(eq.getSlot());
                    sb.append(String.format("[%s]%s\n", slotName, eq.getDisplayName()));
                }
            }
        } else {
            sb.append("背包为空");
        }
        return sb.toString();
    }

    @Override
    public String generatePartyInfo(Player player) {
        StringBuilder sb = new StringBuilder();
        if (player.getPartyId() != null) {
            Party party = partyService.getParty(player.getPartyId());
            if (party != null && !party.isSolo()) {
                if (player.isPartyLeader()) {
                    sb.append("你是队长\n");
                } else {
                    sb.append("你在队伍中\n");
                }
                sb.append("队伍成员(").append(party.getMemberIds().size()).append("/4)：");
                for (String memberId : party.getMemberIds()) {
                    Optional<PlayerEntity> memberOpt = playerRepository.findById(memberId);
                    if (memberOpt.isPresent()) {
                        sb.append("\n  - ").append(memberOpt.get().getName());
                        if (memberId.equals(party.getLeaderId())) {
                            sb.append(" [队长]");
                        }
                    }
                }
            } else {
                sb.append("你当前没有队伍");
            }
        } else {
            sb.append("你当前没有队伍");
        }
        return sb.toString();
    }

    @Override
    public String generateOtherPlayerInfo(Player target) {
        StringBuilder sb = new StringBuilder();

        // 基本状态
        sb.append("=== 角色信息 ===\n");
        sb.append(generatePlayerStatus(target));
        sb.append("\n\n");

        // 技能
        sb.append("=== 技能 ===\n");
        sb.append(generateSkills(target));
        sb.append("\n");

        // 装备
        sb.append("=== 装备 ===\n");
        sb.append(generateEquipment(target));

        return sb.toString();
    }

    @Override
    public String generateSelfInfo(Player player) {
        StringBuilder sb = new StringBuilder();

        // 基本状态
        sb.append("=== 角色信息 ===\n");
        sb.append(generatePlayerStatus(player));
        sb.append("\n\n");

        // 技能
        sb.append("=== 技能 ===\n");
        sb.append(generateSkills(player));
        sb.append("\n");

        // 装备
        sb.append("=== 装备 ===\n");
        sb.append(generateEquipment(player));
        sb.append("\n");

        // 背包
        sb.append("=== 背包 ===\n");
        sb.append(generateInventory(player));
        sb.append("\n");

        // 队伍
        sb.append("=== 队伍 ===\n");
        sb.append(generatePartyInfo(player));

        return sb.toString();
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

    @Override
    public String generateItemInfo(String playerId, String itemName) {
        // 首先尝试从配置中查找普通物品
        ItemConfig itemConfig = configDataManager.getItemByName(itemName);
        if (itemConfig != null) {
            return generateItemConfigInfo(itemConfig);
        }

        // 尝试查找装备（支持带实例编号的名称，如"铁剑#1"）
        EquipmentConfig equipmentConfig = configDataManager.getEquipmentByName(itemName);
        if (equipmentConfig != null) {
            return generateEquipmentConfigInfo(equipmentConfig);
        }

        return null;
    }

    /**
     * 生成普通物品的详细信息
     */
    private String generateItemConfigInfo(ItemConfig item) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 物品详情 ===\n");
        sb.append("名称: ").append(item.getName()).append("\n");
        sb.append("类型: ").append(getItemTypeName(item.getType())).append("\n");
        sb.append("描述: ").append(item.getDescription()).append("\n");
        sb.append("价格: ").append(item.getBasePrice()).append(" 金币\n");
        sb.append("最大堆叠: ").append(item.getMaxStack()).append("\n");

        // 显示物品效果
        if (item.getEffect() != null && !item.getEffect().isEmpty()) {
            sb.append("效果: ").append(getEffectDescription(item.getEffect(), item.getEffectValue()));
        } else {
            sb.append("效果: 无");
        }

        return sb.toString();
    }

    /**
     * 生成装备的详细信息
     */
    private String generateEquipmentConfigInfo(EquipmentConfig eq) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 装备详情 ===\n");
        sb.append("名称: ").append(eq.getName()).append("\n");
        sb.append("稀有度: ").append(getRarityName(eq.getRarity())).append("\n");
        sb.append("装备位置: ").append(getSlotNameFromString(eq.getSlot())).append("\n");
        sb.append("描述: ").append(eq.getDescription()).append("\n");
        sb.append("价格: ").append(eq.getBasePrice()).append(" 金币\n");

        // 属性加成
        sb.append("\n--- 属性加成 ---\n");
        boolean hasBonus = false;

        // 四维属性
        if (eq.getStrength() > 0) {
            sb.append("力量 +").append(eq.getStrength()).append("\n");
            hasBonus = true;
        }
        if (eq.getAgility() > 0) {
            sb.append("敏捷 +").append(eq.getAgility()).append("\n");
            hasBonus = true;
        }
        if (eq.getIntelligence() > 0) {
            sb.append("智力 +").append(eq.getIntelligence()).append("\n");
            hasBonus = true;
        }
        if (eq.getVitality() > 0) {
            sb.append("体力 +").append(eq.getVitality()).append("\n");
            hasBonus = true;
        }

        // 战斗属性
        if (eq.getPhysicalAttack() > 0) {
            sb.append("物理攻击 +").append(eq.getPhysicalAttack()).append("\n");
            hasBonus = true;
        }
        if (eq.getPhysicalDefense() > 0) {
            sb.append("物理防御 +").append(eq.getPhysicalDefense()).append("\n");
            hasBonus = true;
        }
        if (eq.getMagicAttack() > 0) {
            sb.append("法术攻击 +").append(eq.getMagicAttack()).append("\n");
            hasBonus = true;
        }
        if (eq.getMagicDefense() > 0) {
            sb.append("法术防御 +").append(eq.getMagicDefense()).append("\n");
            hasBonus = true;
        }
        if (eq.getSpeed() > 0) {
            sb.append("速度 +").append(eq.getSpeed()).append("\n");
            hasBonus = true;
        }
        if (eq.getCritRate() > 0) {
            sb.append("暴击率 +").append(String.format("%.1f%%", eq.getCritRate() * 100)).append("\n");
            hasBonus = true;
        }
        if (eq.getCritDamage() > 0) {
            sb.append("暴击伤害 +").append(String.format("%.1f%%", eq.getCritDamage() * 100)).append("\n");
            hasBonus = true;
        }
        if (eq.getHitRate() > 0) {
            sb.append("命中率 +").append(String.format("%.1f%%", eq.getHitRate() * 100)).append("\n");
            hasBonus = true;
        }
        if (eq.getDodgeRate() > 0) {
            sb.append("闪避率 +").append(String.format("%.1f%%", eq.getDodgeRate() * 100)).append("\n");
            hasBonus = true;
        }

        if (!hasBonus) {
            sb.append("无属性加成");
        }

        return sb.toString();
    }

    private String getItemTypeName(String type) {
        if (type == null) return "未知";
        return switch (type) {
            case "CONSUMABLE" -> "消耗品";
            case "MATERIAL" -> "材料";
            case "QUEST" -> "任务物品";
            case "SKILL_BOOK" -> "技能书";
            case "EQUIPMENT" -> "装备";
            case "OTHER" -> "其他";
            default -> type;
        };
    }

    private String getEffectDescription(String effect, Integer effectValue) {
        if (effect == null) return "无";
        String value = effectValue != null ? String.valueOf(effectValue) : "?";
        return switch (effect) {
            case "HEAL_HP" -> "恢复 " + value + " 点生命值";
            case "HEAL_MP" -> "恢复 " + value + " 点法力值";
            case "LEARN_SKILL" -> "学习技能";
            case "RESET_ATTRIBUTES" -> "重置所有属性点";
            default -> effect + (effectValue != null ? " (" + value + ")" : "");
        };
    }

    private String getRarityName(String rarity) {
        if (rarity == null) return "普通";
        return switch (rarity) {
            case "COMMON" -> "普通";
            case "EXCELLENT" -> "优秀";
            case "RARE" -> "稀有";
            case "EPIC" -> "史诗";
            case "LEGENDARY" -> "传说";
            case "MYTHIC" -> "神话";
            default -> rarity;
        };
    }

    private String getSlotNameFromString(String slot) {
        if (slot == null) return "未知";
        return switch (slot) {
            case "HEAD" -> "头部";
            case "CHEST" -> "上装";
            case "LEGS" -> "下装";
            case "FEET" -> "鞋子";
            case "LEFT_HAND" -> "左手";
            case "RIGHT_HAND" -> "右手";
            case "ACCESSORY1" -> "饰品1";
            case "ACCESSORY2" -> "饰品2";
            default -> slot;
        };
    }
}
