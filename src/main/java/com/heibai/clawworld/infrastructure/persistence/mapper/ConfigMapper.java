package com.heibai.clawworld.infrastructure.persistence.mapper;

import com.heibai.clawworld.application.service.EquipmentInstanceService;
import com.heibai.clawworld.domain.character.Role;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.item.Item;
import com.heibai.clawworld.domain.item.Rarity;
import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.config.data.item.EquipmentConfig;
import com.heibai.clawworld.infrastructure.config.data.item.ItemConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 配置对象到领域对象的映射器
 * 集中处理所有Config到Domain的转换逻辑
 */
@Component
@RequiredArgsConstructor
public class ConfigMapper {

    private final EquipmentInstanceService equipmentInstanceService;

    /**
     * 将RoleConfig转换为Role领域对象
     */
    public Role toDomain(RoleConfig config) {
        if (config == null) {
            return null;
        }

        Role role = new Role();
        role.setId(config.getId());
        role.setName(config.getName());
        role.setDescription(config.getDescription());
        role.setBaseHealth(config.getBaseHealth());
        role.setBaseMana(config.getBaseMana());
        role.setBasePhysicalAttack(config.getBasePhysicalAttack());
        role.setBasePhysicalDefense(config.getBasePhysicalDefense());
        role.setBaseMagicAttack(config.getBaseMagicAttack());
        role.setBaseMagicDefense(config.getBaseMagicDefense());
        role.setBaseSpeed(config.getBaseSpeed());
        role.setBaseCritRate(config.getBaseCritRate());
        role.setBaseCritDamage(config.getBaseCritDamage());
        role.setBaseHitRate(config.getBaseHitRate());
        role.setBaseDodgeRate(config.getBaseDodgeRate());
        role.setHealthPerLevel(config.getHealthPerLevel());
        role.setManaPerLevel(config.getManaPerLevel());
        role.setPhysicalAttackPerLevel(config.getPhysicalAttackPerLevel());
        role.setPhysicalDefensePerLevel(config.getPhysicalDefensePerLevel());
        role.setMagicAttackPerLevel(config.getMagicAttackPerLevel());
        role.setMagicDefensePerLevel(config.getMagicDefensePerLevel());
        role.setSpeedPerLevel(config.getSpeedPerLevel());
        role.setCritRatePerLevel(config.getCritRatePerLevel());
        role.setCritDamagePerLevel(config.getCritDamagePerLevel());
        role.setHitRatePerLevel(config.getHitRatePerLevel());
        role.setDodgeRatePerLevel(config.getDodgeRatePerLevel());
        return role;
    }

    /**
     * 将ItemConfig转换为Item领域对象
     */
    public Item toDomain(ItemConfig config) {
        if (config == null) {
            return null;
        }

        Item item = new Item();
        item.setId(config.getId());
        item.setName(config.getName());
        item.setDescription(config.getDescription());
        item.setType(Item.ItemType.valueOf(config.getType()));
        item.setMaxStack(config.getMaxStack());
        item.setBasePrice(config.getBasePrice());
        item.setEffect(config.getEffect());
        item.setEffectValue(config.getEffectValue());
        return item;
    }

    /**
     * 将EquipmentConfig转换为Equipment领域对象
     * 自动生成唯一的实例编号
     */
    public Equipment toDomain(EquipmentConfig config) {
        if (config == null) {
            return null;
        }

        Equipment equipment = toDomainWithoutInstanceNumber(config);

        // 生成唯一的实例编号
        Long instanceNumber = equipmentInstanceService.getNextInstanceNumber(config.getId());
        equipment.setInstanceNumber(instanceNumber);

        return equipment;
    }

    /**
     * 将EquipmentConfig转换为Equipment领域对象，但不生成实例编号
     * 用于从数据库加载已有装备时使用，避免浪费编号
     */
    public Equipment toDomainWithoutInstanceNumber(EquipmentConfig config) {
        if (config == null) {
            return null;
        }

        Equipment equipment = new Equipment();
        equipment.setId(config.getId());
        equipment.setName(config.getName());
        equipment.setDescription(config.getDescription());
        equipment.setType(Item.ItemType.EQUIPMENT);
        equipment.setMaxStack(1);
        equipment.setBasePrice(config.getBasePrice());
        equipment.setSlot(Equipment.EquipmentSlot.valueOf(config.getSlot()));
        equipment.setRarity(Rarity.valueOf(config.getRarity()));
        equipment.setStrength(config.getStrength());
        equipment.setAgility(config.getAgility());
        equipment.setIntelligence(config.getIntelligence());
        equipment.setVitality(config.getVitality());
        equipment.setPhysicalAttack(config.getPhysicalAttack());
        equipment.setPhysicalDefense(config.getPhysicalDefense());
        equipment.setMagicAttack(config.getMagicAttack());
        equipment.setMagicDefense(config.getMagicDefense());
        equipment.setSpeed(config.getSpeed());
        equipment.setCritRate(config.getCritRate());
        equipment.setCritDamage(config.getCritDamage());
        equipment.setHitRate(config.getHitRate());
        equipment.setDodgeRate(config.getDodgeRate());

        return equipment;
    }
}
