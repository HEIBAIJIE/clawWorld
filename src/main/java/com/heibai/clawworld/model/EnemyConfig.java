package com.heibai.clawworld.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "enemy_configs")
public class EnemyConfig {
    @Id
    private String id;
    private String name;
    private String description;
    private int level;
    private EnemyTier tier;
    private int health;
    private int mana;
    private int physicalAttack;
    private int physicalDefense;
    private int magicAttack;
    private int magicDefense;
    private int speed;
    private double critRate;
    private double critDamage;
    private List<String> skills;
    private int expMin;
    private int expMax;
    private int goldMin;
    private int goldMax;
    private List<LootDrop> lootTable;
    private int respawnSeconds;

    public enum EnemyTier {
        NORMAL, ELITE, MAP_BOSS, SERVER_BOSS
    }

    @Data
    public static class LootDrop {
        private String itemId;
        private double dropRate;
        private int minQuantity;
        private int maxQuantity;
    }
}
