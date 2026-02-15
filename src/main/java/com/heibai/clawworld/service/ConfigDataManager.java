package com.heibai.clawworld.service;

import com.heibai.clawworld.config.character.*;
import com.heibai.clawworld.config.item.*;
import com.heibai.clawworld.config.map.*;
import com.heibai.clawworld.config.skill.SkillConfig;
import com.heibai.clawworld.service.loader.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * 配置数据管理器 - 统一入口，委托给各个Loader
 */
@Service
@RequiredArgsConstructor
public class ConfigDataManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigDataManager.class);

    private final ItemConfigLoader itemConfigLoader;
    private final SkillConfigLoader skillConfigLoader;
    private final CharacterConfigLoader characterConfigLoader;
    private final MapConfigLoader mapConfigLoader;
    private final ResourceLoader resourceLoader;

    private final Map<String, String> fileChecksums = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing ConfigDataManager...");
        loadAllConfigs();
    }

    @Scheduled(fixedDelayString = "${csv.reload-interval:60000}")
    public void checkAndReload() {
        log.debug("Checking for CSV file changes...");
        try {
            if (hasAnyFileChanged()) {
                log.info("CSV files changed, reloading...");
                loadAllConfigs();
            }
        } catch (Exception e) {
            log.error("Error checking file changes", e);
        }
    }

    private boolean hasAnyFileChanged() throws Exception {
        return hasFileChanged("classpath:data/items.csv") ||
               hasFileChanged("classpath:data/equipment.csv") ||
               hasFileChanged("classpath:data/skills.csv") ||
               hasFileChanged("classpath:data/enemies.csv") ||
               hasFileChanged("classpath:data/npcs.csv") ||
               hasFileChanged("classpath:data/maps.csv") ||
               hasFileChanged("classpath:data/roles.csv") ||
               hasFileChanged("classpath:data/waypoints.csv") ||
               hasFileChanged("classpath:data/enemy_loot.csv") ||
               hasFileChanged("classpath:data/map_terrain.csv") ||
               hasFileChanged("classpath:data/map_entities.csv") ||
               hasFileChanged("classpath:data/npc_shop_items.csv") ||
               hasFileChanged("classpath:data/role_skills.csv");
    }

    private boolean hasFileChanged(String path) throws Exception {
        Resource resource = resourceLoader.getResource(path);
        if (!resource.exists()) {
            return false;
        }

        String checksum = calculateChecksum(resource.getInputStream());
        String oldChecksum = fileChecksums.get(path);

        if (!checksum.equals(oldChecksum)) {
            fileChecksums.put(path, checksum);
            return true;
        }
        return false;
    }

    private String calculateChecksum(InputStream inputStream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) > 0) {
            md.update(buffer, 0, read);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void loadAllConfigs() {
        // 物品和装备
        itemConfigLoader.loadItems();
        itemConfigLoader.loadEquipment();

        // 技能
        skillConfigLoader.loadSkills();

        // 角色相关
        characterConfigLoader.loadEnemies();
        characterConfigLoader.loadNpcs();
        characterConfigLoader.loadRoles();
        characterConfigLoader.loadEnemyLoot();
        characterConfigLoader.loadNpcShopItems();
        characterConfigLoader.loadRoleSkills();

        // 地图相关
        mapConfigLoader.loadMaps();
        mapConfigLoader.loadWaypoints();
        mapConfigLoader.loadMapTerrain();
        mapConfigLoader.loadMapEntities();

        log.info("All configs loaded successfully");
    }

    // ========== 物品相关 ==========
    public ItemConfig getItem(String id) {
        return itemConfigLoader.getItem(id);
    }

    public EquipmentConfig getEquipment(String id) {
        return itemConfigLoader.getEquipment(id);
    }

    public Collection<ItemConfig> getAllItems() {
        return itemConfigLoader.getAllItems().values();
    }

    public Collection<EquipmentConfig> getAllEquipment() {
        return itemConfigLoader.getAllEquipment().values();
    }

    // ========== 技能相关 ==========
    public SkillConfig getSkill(String id) {
        return skillConfigLoader.getSkill(id);
    }

    public Collection<SkillConfig> getAllSkills() {
        return skillConfigLoader.getAllSkills().values();
    }

    // ========== 角色相关 ==========
    public EnemyConfig getEnemy(String id) {
        return characterConfigLoader.getEnemy(id);
    }

    public NpcConfig getNpc(String id) {
        return characterConfigLoader.getNpc(id);
    }

    public RoleConfig getRole(String id) {
        return characterConfigLoader.getRole(id);
    }

    public Collection<EnemyConfig> getAllEnemies() {
        return characterConfigLoader.getAllEnemies().values();
    }

    public Collection<NpcConfig> getAllNpcs() {
        return characterConfigLoader.getAllNpcs().values();
    }

    public Collection<RoleConfig> getAllRoles() {
        return characterConfigLoader.getAllRoles().values();
    }

    public List<EnemyLootConfig> getEnemyLoot(String enemyId) {
        return characterConfigLoader.getEnemyLoot(enemyId);
    }

    public List<NpcShopItemConfig> getNpcShopItems(String npcId) {
        return characterConfigLoader.getNpcShopItems(npcId);
    }

    public List<RoleSkillConfig> getRoleSkills(String roleId) {
        return characterConfigLoader.getRoleSkills(roleId);
    }

    // ========== 地图相关 ==========
    public MapConfig getMap(String id) {
        return mapConfigLoader.getMap(id);
    }

    public WaypointConfig getWaypoint(String id) {
        return mapConfigLoader.getWaypoint(id);
    }

    public Collection<MapConfig> getAllMaps() {
        return mapConfigLoader.getAllMaps().values();
    }

    public Collection<WaypointConfig> getAllWaypoints() {
        return mapConfigLoader.getAllWaypoints().values();
    }

    public List<MapTerrainConfig> getMapTerrain(String mapId) {
        return mapConfigLoader.getMapTerrain(mapId);
    }

    public List<MapEntityConfig> getMapEntities(String mapId) {
        return mapConfigLoader.getMapEntities(mapId);
    }
}
