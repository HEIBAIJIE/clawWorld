package com.heibai.clawworld.infrastructure.config;

import com.heibai.clawworld.infrastructure.config.data.character.*;
import com.heibai.clawworld.infrastructure.config.data.item.*;
import com.heibai.clawworld.infrastructure.config.data.map.*;
import com.heibai.clawworld.infrastructure.config.data.skill.SkillConfig;
import com.heibai.clawworld.infrastructure.config.loader.*;
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
import java.util.stream.Collectors;

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
               hasFileChanged("classpath:data/role_skills.csv") ||
               hasFileChanged("classpath:data/chests.csv") ||
               hasFileChanged("classpath:data/chest_loot.csv") ||
               hasFileChanged("classpath:data/gift_loot.csv") ||
               hasFileChanged("classpath:data/terrain_types.csv");
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
        itemConfigLoader.loadGiftLoot();

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
        mapConfigLoader.loadChests();
        mapConfigLoader.loadChestLoot();
        mapConfigLoader.loadTerrainTypes();

        log.info("All configs loaded successfully");
    }

    // ========== 物品相关 ==========
    public ItemConfig getItem(String id) {
        return itemConfigLoader.getItem(id);
    }

    /**
     * 通过物品名称查找物品配置
     * @param name 物品名称
     * @return 物品配置，如果未找到返回null
     */
    public ItemConfig getItemByName(String name) {
        return itemConfigLoader.getAllItems().values().stream()
            .filter(item -> item.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * 通过装备名称查找装备配置
     * @param name 装备名称（不含实例编号，如"铁剑"而非"铁剑#1"）
     * @return 装备配置，如果未找到返回null
     */
    public EquipmentConfig getEquipmentByName(String name) {
        // 去除实例编号（如果有的话）
        String baseName = name.contains("#") ? name.substring(0, name.indexOf("#")) : name;
        return itemConfigLoader.getAllEquipment().values().stream()
            .filter(eq -> eq.getName().equals(baseName))
            .findFirst()
            .orElse(null);
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

    public List<String> getMapTerrain(String mapId, int x, int y) {
        List<MapTerrainConfig> terrains = mapConfigLoader.getMapTerrain(mapId);
        return terrains.stream()
            .filter(t -> x >= Math.min(t.getX1(), t.getX2()) && x <= Math.max(t.getX1(), t.getX2())
                      && y >= Math.min(t.getY1(), t.getY2()) && y <= Math.max(t.getY1(), t.getY2()))
            .flatMap(t -> Arrays.stream(t.getTerrainTypes().split(",")))
            .map(String::trim)
            .collect(Collectors.toList());
    }

    public List<MapEntityConfig> getMapEntities(String mapId) {
        return mapConfigLoader.getMapEntities(mapId);
    }

    // ========== 宝箱相关 ==========
    public ChestConfig getChest(String id) {
        return mapConfigLoader.getChest(id);
    }

    public Collection<ChestConfig> getAllChests() {
        return mapConfigLoader.getAllChests().values();
    }

    public List<ChestLootConfig> getChestLoot(String chestId) {
        return mapConfigLoader.getChestLoot(chestId);
    }

    // ========== 地形类型相关 ==========
    public TerrainTypeConfig getTerrainType(String id) {
        return mapConfigLoader.getTerrainType(id);
    }

    public Collection<TerrainTypeConfig> getAllTerrainTypes() {
        return mapConfigLoader.getAllTerrainTypes().values();
    }

    // ========== 礼包相关 ==========
    public List<GiftLootConfig> getGiftLoot(String giftId) {
        return itemConfigLoader.getGiftLoot(giftId);
    }
}
