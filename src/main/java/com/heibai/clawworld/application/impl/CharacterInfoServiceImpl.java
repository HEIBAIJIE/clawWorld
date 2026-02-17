package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.CharacterInfoService;
import com.heibai.clawworld.application.service.PartyService;
import com.heibai.clawworld.domain.character.Party;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
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
}
