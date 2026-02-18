package com.heibai.clawworld.infrastructure.persistence.mapper;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 玩家领域对象与持久化实体之间的映射器
 */
@Component
public class PlayerMapper {

    /**
     * 将领域对象转换为持久化实体
     */
    public PlayerEntity toEntity(Player player) {
        if (player == null) {
            return null;
        }

        PlayerEntity entity = new PlayerEntity();
        entity.setId(player.getId());
        entity.setName(player.getName());
        entity.setRoleId(player.getRoleId());

        // 基础属性
        entity.setLevel(player.getLevel());
        entity.setExperience(player.getExperience());

        // 四维属性
        entity.setStrength(player.getStrength());
        entity.setAgility(player.getAgility());
        entity.setIntelligence(player.getIntelligence());
        entity.setVitality(player.getVitality());
        entity.setFreeAttributePoints(player.getFreeAttributePoints());

        // 职业基础属性
        entity.setBaseMaxHealth(player.getBaseMaxHealth());
        entity.setBaseMaxMana(player.getBaseMaxMana());
        entity.setBasePhysicalAttack(player.getBasePhysicalAttack());
        entity.setBasePhysicalDefense(player.getBasePhysicalDefense());
        entity.setBaseMagicAttack(player.getBaseMagicAttack());
        entity.setBaseMagicDefense(player.getBaseMagicDefense());
        entity.setBaseSpeed(player.getBaseSpeed());
        entity.setBaseCritRate(player.getBaseCritRate());
        entity.setBaseCritDamage(player.getBaseCritDamage());
        entity.setBaseHitRate(player.getBaseHitRate());
        entity.setBaseDodgeRate(player.getBaseDodgeRate());

        // 生命和法力（最终值）
        entity.setMaxHealth(player.getMaxHealth());
        entity.setCurrentHealth(player.getCurrentHealth());
        entity.setMaxMana(player.getMaxMana());
        entity.setCurrentMana(player.getCurrentMana());

        // 战斗属性（最终值）
        entity.setPhysicalAttack(player.getPhysicalAttack());
        entity.setPhysicalDefense(player.getPhysicalDefense());
        entity.setMagicAttack(player.getMagicAttack());
        entity.setMagicDefense(player.getMagicDefense());
        entity.setSpeed(player.getSpeed());
        entity.setCritRate(player.getCritRate());
        entity.setCritDamage(player.getCritDamage());
        entity.setHitRate(player.getHitRate());
        entity.setDodgeRate(player.getDodgeRate());

        // 金钱
        entity.setGold(player.getGold());

        // 装备栏（使用枚举名称作为key）
        if (player.getEquipment() != null) {
            entity.setEquipment(player.getEquipment().entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().name(),
                            entry -> {
                                PlayerEntity.EquipmentSlotData data = new PlayerEntity.EquipmentSlotData();
                                data.setEquipmentId(entry.getValue().getId());
                                data.setInstanceNumber(entry.getValue().getInstanceNumber());
                                return data;
                            }
                    )));
        }

        // 背包（统一存储普通物品和装备）
        if (player.getInventory() != null) {
            List<PlayerEntity.InventorySlotData> inventoryList = player.getInventory().stream()
                    .map(slot -> {
                        PlayerEntity.InventorySlotData data = new PlayerEntity.InventorySlotData();
                        data.setType(slot.getType().name());
                        data.setQuantity(slot.getQuantity());

                        if (slot.isItem()) {
                            data.setItemId(slot.getItem().getId());
                        } else if (slot.isEquipment()) {
                            data.setItemId(slot.getEquipment().getId());
                            data.setEquipmentInstanceNumber(slot.getEquipment().getInstanceNumber());
                        }

                        return data;
                    })
                    .collect(Collectors.toList());
            entity.setInventory(inventoryList);
        }

        // 技能
        entity.setSkills(player.getSkills());

        // 位置信息（从MapEntity继承）
        entity.setCurrentMapId(player.getMapId());
        entity.setX(player.getX());
        entity.setY(player.getY());

        // 队伍信息
        entity.setPartyId(player.getPartyId());
        entity.setPartyLeader(player.isPartyLeader());

        // 战斗状态
        entity.setInCombat(player.isInCombat());
        entity.setCombatId(player.getCombatId());

        // 交易状态
        entity.setTradeId(player.getTradeId());

        // 商店状态
        entity.setCurrentShopId(player.getCurrentShopId());

        return entity;
    }

    /**
     * 将持久化实体转换为领域对象
     * 注意：这个方法只转换基础数据，装备和物品的完整对象需要通过ConfigDataManager加载
     */
    public Player toDomain(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }

        Player player = new Player();
        player.setId(entity.getId());
        player.setName(entity.getName());
        player.setRoleId(entity.getRoleId());

        // 基础属性
        player.setLevel(entity.getLevel());
        player.setExperience(entity.getExperience());
        // 玩家阵营：没有队伍时阵营为"#{玩家名}的队伍"，有队伍时需要在Service层设置为队伍的faction
        player.setFaction(entity.getName() + "的队伍");

        // 四维属性
        player.setStrength(entity.getStrength());
        player.setAgility(entity.getAgility());
        player.setIntelligence(entity.getIntelligence());
        player.setVitality(entity.getVitality());
        player.setFreeAttributePoints(entity.getFreeAttributePoints());

        // 职业基础属性
        player.setBaseMaxHealth(entity.getBaseMaxHealth());
        player.setBaseMaxMana(entity.getBaseMaxMana());
        player.setBasePhysicalAttack(entity.getBasePhysicalAttack());
        player.setBasePhysicalDefense(entity.getBasePhysicalDefense());
        player.setBaseMagicAttack(entity.getBaseMagicAttack());
        player.setBaseMagicDefense(entity.getBaseMagicDefense());
        player.setBaseSpeed(entity.getBaseSpeed());
        player.setBaseCritRate(entity.getBaseCritRate());
        player.setBaseCritDamage(entity.getBaseCritDamage());
        player.setBaseHitRate(entity.getBaseHitRate());
        player.setBaseDodgeRate(entity.getBaseDodgeRate());

        // 生命和法力（最终值）
        player.setMaxHealth(entity.getMaxHealth());
        player.setCurrentHealth(entity.getCurrentHealth());
        player.setMaxMana(entity.getMaxMana());
        player.setCurrentMana(entity.getCurrentMana());

        // 战斗属性（最终值）
        player.setPhysicalAttack(entity.getPhysicalAttack());
        player.setPhysicalDefense(entity.getPhysicalDefense());
        player.setMagicAttack(entity.getMagicAttack());
        player.setMagicDefense(entity.getMagicDefense());
        player.setSpeed(entity.getSpeed());
        player.setCritRate(entity.getCritRate());
        player.setCritDamage(entity.getCritDamage());
        player.setHitRate(entity.getHitRate());
        player.setDodgeRate(entity.getDodgeRate());

        // 金钱
        player.setGold(entity.getGold());

        // 装备栏和背包需要在Service层通过ConfigDataManager重建完整对象
        player.setEquipment(new HashMap<>());
        player.setInventory(new ArrayList<>());

        // 技能
        player.setSkills(entity.getSkills());

        // 位置信息（设置到MapEntity）
        player.setMapId(entity.getCurrentMapId());
        player.setX(entity.getX());
        player.setY(entity.getY());

        // 队伍信息
        player.setPartyId(entity.getPartyId());
        player.setPartyLeader(entity.isPartyLeader());

        // 战斗状态
        player.setInCombat(entity.isInCombat());
        player.setCombatId(entity.getCombatId());
        player.setCombatStartTime(entity.isInCombat() ? System.currentTimeMillis() : null);

        // 交易状态
        player.setTradeId(entity.getTradeId());

        // 商店状态
        player.setCurrentShopId(entity.getCurrentShopId());

        return player;
    }
}
