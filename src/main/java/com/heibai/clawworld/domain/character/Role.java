package com.heibai.clawworld.domain.character;

import lombok.Data;

import java.util.List;

/**
 * 职业领域对象
 */
@Data
public class Role {
    private String id;
    private String name;
    private String description;
    private int baseHealth;
    private int baseMana;
    private int baseStrength;
    private int baseAgility;
    private int baseIntelligence;
    private int baseVitality;
    private double healthPerLevel;
    private double manaPerLevel;
    private double strengthPerLevel;
    private double agilityPerLevel;
    private double intelligencePerLevel;
    private double vitalityPerLevel;
    private List<SkillLearn> skillLearns;

    @Data
    public static class SkillLearn {
        private String skillId;
        private int learnLevel;
    }
}
