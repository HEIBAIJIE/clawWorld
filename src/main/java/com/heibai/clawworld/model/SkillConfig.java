package com.heibai.clawworld.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "skill_configs")
public class SkillConfig {
    @Id
    private String id;
    private String name;
    private String description;
    private SkillTarget targetType;
    private DamageType damageType;
    private int manaCost;
    private int cooldown;
    private double damageMultiplier;
    private List<String> allowedRoles;
    private Integer learnLevel;

    public enum SkillTarget {
        SELF, ALLY_SINGLE, ALLY_ALL, ENEMY_SINGLE, ENEMY_ALL
    }

    public enum DamageType {
        PHYSICAL, MAGICAL, NONE
    }
}
