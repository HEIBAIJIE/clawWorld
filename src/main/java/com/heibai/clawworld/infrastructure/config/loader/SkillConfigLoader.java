package com.heibai.clawworld.infrastructure.config.loader;

import com.heibai.clawworld.infrastructure.config.data.skill.SkillConfig;
import com.heibai.clawworld.infrastructure.util.CsvReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能配置加载器
 */
@Component
@RequiredArgsConstructor
public class SkillConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillConfigLoader.class);
    private final CsvReader csvReader;
    private final ResourceLoader resourceLoader;

    private final Map<String, SkillConfig> skillConfigs = new ConcurrentHashMap<>();

    public void loadSkills() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/skills.csv");
            if (!resource.exists()) {
                log.warn("skills.csv not found, skipping");
                return;
            }

            List<SkillConfig> skills = csvReader.readCsv(resource.getInputStream(), record -> {
                SkillConfig skill = new SkillConfig();
                skill.setId(csvReader.getString(record, "id"));
                skill.setName(csvReader.getString(record, "name"));
                skill.setDescription(csvReader.getString(record, "description"));
                skill.setTargetType(csvReader.getString(record, "targetType"));
                skill.setDamageType(csvReader.getString(record, "damageType"));
                skill.setManaCost(csvReader.getInt(record, "manaCost"));
                skill.setCooldown(csvReader.getInt(record, "cooldown"));
                skill.setDamageMultiplier(csvReader.getDouble(record, "damageMultiplier"));
                skill.setVfx(csvReader.getStringOrNull(record, "vfx"));
                return skill;
            });

            skillConfigs.clear();
            skills.forEach(skill -> skillConfigs.put(skill.getId(), skill));
            log.info("Loaded {} skills", skills.size());
        } catch (IOException e) {
            log.error("Error loading skills.csv", e);
        }
    }

    public SkillConfig getSkill(String id) {
        return skillConfigs.get(id);
    }

    public Map<String, SkillConfig> getAllSkills() {
        return skillConfigs;
    }
}
