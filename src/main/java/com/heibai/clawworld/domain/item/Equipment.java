package com.heibai.clawworld.domain.item;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 装备领域对象 - 运行时使用
 * 装备是一种特殊的物品，不可堆叠
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Equipment extends Item {
    /**
     * 装备栏位
     */
    private EquipmentSlot slot;

    /**
     * 稀有度
     */
    private Rarity rarity;

    /**
     * 装备实例编号（例如：铁剑#1中的1）
     * 每件装备都有唯一的实例编号
     */
    private Long instanceNumber;

    // 四维属性加成
    private int strength;
    private int agility;
    private int intelligence;
    private int vitality;

    // 直接战斗属性加成
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private double hitRate;
    private double dodgeRate;

    public Equipment() {
        // 装备类型固定为EQUIPMENT，不可堆叠
        super();
        setType(ItemType.EQUIPMENT);
        setMaxStack(1);
    }

    /**
     * 获取装备的完整显示名称（包含槽位前缀和实例编号）
     * 例如：[右手]铁剑#1
     */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        // 添加槽位前缀
        if (slot != null) {
            sb.append("[").append(getSlotDisplayName()).append("]");
        }
        sb.append(getName());
        // 添加实例编号
        if (instanceNumber != null) {
            sb.append("#").append(instanceNumber);
        }
        return sb.toString();
    }

    /**
     * 获取装备的简化显示名称（不含槽位前缀，只有名称和实例编号）
     * 例如：铁剑#1
     * 用于GUI角色信息面板等场景
     */
    public String getSimpleName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        if (instanceNumber != null) {
            sb.append("#").append(instanceNumber);
        }
        return sb.toString();
    }

    /**
     * 获取装备的基础名称（不含槽位前缀和实例编号）
     * 例如：铁剑
     * 用于GUI角色信息面板显示
     */
    public String getBaseName() {
        return getName();
    }

    /**
     * 获取槽位的中文显示名称
     */
    private String getSlotDisplayName() {
        if (slot == null) return "未知";
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

    public enum EquipmentSlot {
        HEAD, CHEST, LEGS, FEET, LEFT_HAND, RIGHT_HAND, ACCESSORY1, ACCESSORY2
    }
}
