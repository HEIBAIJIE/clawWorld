package com.heibai.clawworld.domain.character;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.item.Item;

import java.util.Map;

/**
 * 玩家领域对象
 * 根据设计文档：玩家是一类特殊的角色，特殊之处在于他们的行为受控制
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Player extends Character {
    private String username;
    private String nickname;
    private String roleId; // 职业ID

    // 四维属性
    private int strength;
    private int agility;
    private int intelligence;
    private int vitality;
    private int freeAttributePoints;

    // 金钱
    private int gold;

    // 装备栏
    private Map<Equipment.EquipmentSlot, Equipment> equipment;

    // 背包（最多50类物品）
    private Map<String, ItemStack> inventory;

    // 队伍ID
    private String partyId;
    private boolean isPartyLeader;

    /**
     * 是否可通过
     */
    @Override
    public boolean isPassable() {
        return true;
    }

    @Override
    public String getEntityType() {
        return "PLAYER";
    }

    @Data
    public static class ItemStack {
        private Item item;
        private int quantity;
    }
}
