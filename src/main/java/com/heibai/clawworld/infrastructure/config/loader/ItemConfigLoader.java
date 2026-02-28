package com.heibai.clawworld.infrastructure.config.loader;

import com.heibai.clawworld.infrastructure.config.data.item.ItemConfig;
import com.heibai.clawworld.infrastructure.config.data.item.EquipmentConfig;
import com.heibai.clawworld.infrastructure.config.data.item.GiftLootConfig;
import com.heibai.clawworld.infrastructure.util.CsvReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品配置加载器
 */
@Component
@RequiredArgsConstructor
public class ItemConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ItemConfigLoader.class);
    private final CsvReader csvReader;
    private final ResourceLoader resourceLoader;

    private final Map<String, ItemConfig> itemConfigs = new ConcurrentHashMap<>();
    private final Map<String, EquipmentConfig> equipmentConfigs = new ConcurrentHashMap<>();
    private final List<GiftLootConfig> giftLootConfigs = new ArrayList<>();

    public void loadItems() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/items.csv");
            if (!resource.exists()) {
                log.warn("items.csv not found, skipping");
                return;
            }

            List<ItemConfig> items = csvReader.readCsv(resource.getInputStream(), record -> {
                ItemConfig item = new ItemConfig();
                item.setId(csvReader.getString(record, "id"));
                item.setName(csvReader.getString(record, "name"));
                item.setDescription(csvReader.getString(record, "description"));
                item.setType(csvReader.getString(record, "type"));
                item.setMaxStack(csvReader.getInt(record, "maxStack"));
                item.setBasePrice(csvReader.getInt(record, "basePrice"));
                item.setEffect(csvReader.getString(record, "effect"));
                item.setEffectValue(csvReader.getIntOrNull(record, "effectValue"));
                item.setIcon(csvReader.getStringOrNull(record, "icon"));
                return item;
            });

            itemConfigs.clear();
            items.forEach(item -> itemConfigs.put(item.getId(), item));
            log.info("Loaded {} items", items.size());
        } catch (IOException e) {
            log.error("Error loading items.csv", e);
        }
    }

    public void loadEquipment() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/equipment.csv");
            if (!resource.exists()) {
                log.warn("equipment.csv not found, skipping");
                return;
            }

            List<EquipmentConfig> equipment = csvReader.readCsv(resource.getInputStream(), record -> {
                EquipmentConfig eq = new EquipmentConfig();
                eq.setId(csvReader.getString(record, "id"));
                eq.setName(csvReader.getString(record, "name"));
                eq.setDescription(csvReader.getString(record, "description"));
                eq.setBasePrice(csvReader.getInt(record, "basePrice"));
                eq.setSlot(csvReader.getString(record, "slot"));
                eq.setRarity(csvReader.getString(record, "rarity"));
                eq.setStrength(csvReader.getInt(record, "strength"));
                eq.setAgility(csvReader.getInt(record, "agility"));
                eq.setIntelligence(csvReader.getInt(record, "intelligence"));
                eq.setVitality(csvReader.getInt(record, "vitality"));
                eq.setPhysicalAttack(csvReader.getInt(record, "physicalAttack"));
                eq.setPhysicalDefense(csvReader.getInt(record, "physicalDefense"));
                eq.setMagicAttack(csvReader.getInt(record, "magicAttack"));
                eq.setMagicDefense(csvReader.getInt(record, "magicDefense"));
                eq.setSpeed(csvReader.getInt(record, "speed"));
                eq.setCritRate(csvReader.getDouble(record, "critRate"));
                eq.setCritDamage(csvReader.getDouble(record, "critDamage"));
                eq.setHitRate(csvReader.getDouble(record, "hitRate"));
                eq.setDodgeRate(csvReader.getDouble(record, "dodgeRate"));
                eq.setIcon(csvReader.getStringOrNull(record, "icon"));
                return eq;
            });

            equipmentConfigs.clear();
            equipment.forEach(eq -> equipmentConfigs.put(eq.getId(), eq));
            log.info("Loaded {} equipment", equipment.size());
        } catch (IOException e) {
            log.error("Error loading equipment.csv", e);
        }
    }

    public ItemConfig getItem(String id) {
        return itemConfigs.get(id);
    }

    public EquipmentConfig getEquipment(String id) {
        return equipmentConfigs.get(id);
    }

    public Map<String, ItemConfig> getAllItems() {
        return itemConfigs;
    }

    public Map<String, EquipmentConfig> getAllEquipment() {
        return equipmentConfigs;
    }

    public void loadGiftLoot() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/gift_loot.csv");
            if (!resource.exists()) {
                log.warn("gift_loot.csv not found, skipping");
                return;
            }

            List<GiftLootConfig> loots = csvReader.readCsv(resource.getInputStream(), record -> {
                GiftLootConfig loot = new GiftLootConfig();
                loot.setGiftId(csvReader.getString(record, "giftId"));
                loot.setItemId(csvReader.getString(record, "itemId"));
                loot.setRarity(csvReader.getString(record, "rarity"));
                loot.setQuantity(csvReader.getInt(record, "quantity"));
                return loot;
            });

            giftLootConfigs.clear();
            giftLootConfigs.addAll(loots);
            log.info("Loaded {} gift loot entries", loots.size());
        } catch (IOException e) {
            log.error("Error loading gift_loot.csv", e);
        }
    }

    public List<GiftLootConfig> getGiftLoot(String giftId) {
        return giftLootConfigs.stream()
                .filter(loot -> loot.getGiftId().equals(giftId))
                .toList();
    }
}
