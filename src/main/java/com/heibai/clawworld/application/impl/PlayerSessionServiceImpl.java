package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.config.data.item.EquipmentConfig;
import com.heibai.clawworld.infrastructure.config.data.item.GiftLootConfig;
import com.heibai.clawworld.infrastructure.config.data.item.ItemConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.character.Role;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.item.Item;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.service.PlayerStatsService;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.ConfigMapper;
import com.heibai.clawworld.infrastructure.persistence.mapper.PlayerMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PartyRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.application.service.PlayerSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 玩家会话管理服务实现
 */
@Service
@RequiredArgsConstructor
public class PlayerSessionServiceImpl implements PlayerSessionService {

    private final AccountRepository accountRepository;
    private final PlayerRepository playerRepository;
    private final PartyRepository partyRepository;
    private final PlayerMapper playerMapper;
    private final ConfigMapper configMapper;
    private final ConfigDataManager configDataManager;
    private final PlayerStatsService playerStatsService;
    private final com.heibai.clawworld.infrastructure.factory.MapInitializationService mapInitializationService;

    @Override
    @Transactional
    public SessionResult registerPlayer(String sessionId, String roleName, String playerName) {
        // 验证会话
        Optional<AccountEntity> accountOpt = accountRepository.findBySessionId(sessionId);
        if (!accountOpt.isPresent()) {
            return SessionResult.error("会话不存在或已过期");
        }

        AccountEntity account = accountOpt.get();

        // 检查是否已注册
        if (account.getPlayerId() != null) {
            return SessionResult.error("该账号已注册角色");
        }

        // 检查昵称是否已被使用
        if (accountRepository.existsByNickname(playerName)) {
            return SessionResult.error("昵称已被使用");
        }

        // 查找职业配置
        RoleConfig roleConfig = null;
        for (RoleConfig config : configDataManager.getAllRoles()) {
            if (config.getName().equals(roleName)) {
                roleConfig = config;
                break;
            }
        }

        if (roleConfig == null) {
            return SessionResult.error("职业不存在");
        }

        // 创建玩家
        Player player = new Player();
        player.setId(UUID.randomUUID().toString());
        player.setName(playerName); // 设置玩家昵称
        player.setRoleId(roleConfig.getId());
        player.setLevel(1);
        player.setExperience(0);
        // 使用玩家名称生成阵营名，格式为 "#{玩家名}的队伍"（单人时也用这个格式保持一致）
        player.setFaction(playerName + "的队伍");

        // 初始化四维属性
        player.setStrength(0);
        player.setAgility(0);
        player.setIntelligence(0);
        player.setVitality(0);
        player.setFreeAttributePoints(5); // 新角色初始5个属性点

        // 初始化金钱
        player.setGold(100);

        // 应用职业基础属性（使用领域服务）
        Role role = configMapper.toDomain(roleConfig);
        playerStatsService.recalculateStats(player, role);

        // 初始化当前生命和法力
        player.setCurrentHealth(player.getMaxHealth());
        player.setCurrentMana(player.getMaxMana());

        // 初始化装备栏和背包
        player.setEquipment(new HashMap<>());
        player.setInventory(new ArrayList<>());

        // 初始化技能（普通攻击）
        player.setSkills(new ArrayList<>());
        player.getSkills().add("basic_attack");

        // 设置初始位置（假设有一个新手村地图）
        player.setMapId("starter_village");
        player.setX(5);
        player.setY(5);

        // 战斗状态
        player.setInCombat(false);

        // 保存玩家（初始状态没有队伍）
        PlayerEntity playerEntity = playerMapper.toEntity(player);
        playerEntity.setPartyId(null);
        playerEntity.setPartyLeader(false);
        playerRepository.save(playerEntity);

        // 更新账号信息
        account.setNickname(playerName);
        account.setPlayerId(player.getId());
        accountRepository.save(account);

        // 生成地图窗口内容 - 简化版本，只返回成功消息
        GameMap map = mapInitializationService.getMap(player.getMapId());
        String windowContent = "";
        if (map != null) {
            windowContent = "注册成功！你现在位于 " + map.getName();
        } else {
            windowContent = "地图加载失败，请联系管理员。";
        }

        return SessionResult.success(player.getId(), "map_window_" + player.getId(),
            "注册成功！欢迎来到 " + map.getName(), windowContent);
    }

    @Override
    public Player getPlayerState(String playerId) {
        Optional<PlayerEntity> playerOpt = playerRepository.findById(playerId);
        if (!playerOpt.isPresent()) {
            return null;
        }

        Player player = playerMapper.toDomain(playerOpt.get());

        // 重建装备和物品对象
        PlayerEntity entity = playerOpt.get();

        // 重建装备栏
        Map<Equipment.EquipmentSlot, Equipment> equipment = new HashMap<>();
        if (entity.getEquipment() != null) {
            for (Map.Entry<String, PlayerEntity.EquipmentSlotData> entry : entity.getEquipment().entrySet()) {
                Equipment.EquipmentSlot slot = Equipment.EquipmentSlot.valueOf(entry.getKey());
                EquipmentConfig config = configDataManager.getEquipment(entry.getValue().getEquipmentId());
                if (config != null) {
                    Equipment eq = configMapper.toDomain(config);
                    eq.setInstanceNumber(entry.getValue().getInstanceNumber());
                    equipment.put(slot, eq);
                }
            }
        }
        player.setEquipment(equipment);

        // 重建背包
        List<Player.InventorySlot> inventory = new ArrayList<>();
        if (entity.getInventory() != null) {
            for (PlayerEntity.InventorySlotData slotData : entity.getInventory()) {
                if ("ITEM".equals(slotData.getType())) {
                    ItemConfig itemConfig = configDataManager.getItem(slotData.getItemId());
                    if (itemConfig != null) {
                        Item item = configMapper.toDomain(itemConfig);
                        inventory.add(Player.InventorySlot.forItem(item, slotData.getQuantity()));
                    }
                } else if ("EQUIPMENT".equals(slotData.getType())) {
                    EquipmentConfig eqConfig = configDataManager.getEquipment(slotData.getItemId());
                    if (eqConfig != null) {
                        Equipment eq = configMapper.toDomain(eqConfig);
                        eq.setInstanceNumber(slotData.getEquipmentInstanceNumber());
                        inventory.add(Player.InventorySlot.forEquipment(eq));
                    }
                }
            }
        }
        player.setInventory(inventory);

        // 如果玩家有队伍，设置为队伍的阵营
        if (entity.getPartyId() != null) {
            Optional<com.heibai.clawworld.infrastructure.persistence.entity.PartyEntity> partyOpt =
                partyRepository.findById(entity.getPartyId());
            if (partyOpt.isPresent()) {
                player.setFaction(partyOpt.get().getFaction());
            }
        }

        return player;
    }

    @Override
    @Transactional
    public void savePlayerState(Player player) {
        if (player == null || player.getId() == null) {
            return;
        }
        PlayerEntity entity = playerMapper.toEntity(player);
        playerRepository.save(entity);
    }

    @Override
    @Transactional
    public OperationResult useItem(String playerId, String itemName) {
        Player player = getPlayerState(playerId);
        if (player == null) {
            return OperationResult.error("玩家不存在");
        }

        // 查找物品
        Player.InventorySlot targetSlot = null;
        for (Player.InventorySlot slot : player.getInventory()) {
            if (slot.isItem() && slot.getItem().getName().equals(itemName)) {
                targetSlot = slot;
                break;
            }
        }

        if (targetSlot == null) {
            return OperationResult.error("物品不存在");
        }

        Item item = targetSlot.getItem();

        // 根据物品效果处理
        if (item.getEffect() == null) {
            return OperationResult.error("该物品无法使用");
        }

        String resultMessage = null;

        switch (item.getEffect()) {
            case "HEAL_HP":
                int healthRestore = item.getEffectValue() != null ? item.getEffectValue() : 0;
                int newHealth = Math.min(player.getCurrentHealth() + healthRestore, player.getMaxHealth());
                int actualHealthRestored = newHealth - player.getCurrentHealth();
                player.setCurrentHealth(newHealth);
                resultMessage = String.format("使用 %s，恢复了 %d 点生命值 (当前: %d/%d)",
                    itemName, actualHealthRestored, player.getCurrentHealth(), player.getMaxHealth());
                break;
            case "HEAL_MP":
                int manaRestore = item.getEffectValue() != null ? item.getEffectValue() : 0;
                int newMana = Math.min(player.getCurrentMana() + manaRestore, player.getMaxMana());
                int actualManaRestored = newMana - player.getCurrentMana();
                player.setCurrentMana(newMana);
                resultMessage = String.format("使用 %s，恢复了 %d 点法力值 (当前: %d/%d)",
                    itemName, actualManaRestored, player.getCurrentMana(), player.getMaxMana());
                break;
            case "LEARN_SKILL":
                // 学习技能书
                if (item.getType() == Item.ItemType.SKILL_BOOK) {
                    String skillId = item.getId().replace("skill_book_", "");
                    if (player.getSkills().contains(skillId)) {
                        return OperationResult.error("已经学会该技能");
                    }
                    if (player.getSkills().size() >= 10) {
                        return OperationResult.error("技能栏已满");
                    }
                    player.getSkills().add(skillId);
                    resultMessage = "学习技能成功: " + skillId;
                }
                break;
            case "RESET_ATTRIBUTES":
                // 洗点
                int totalPoints = player.getStrength() + player.getAgility() +
                                 player.getIntelligence() + player.getVitality();
                player.setStrength(0);
                player.setAgility(0);
                player.setIntelligence(0);
                player.setVitality(0);
                player.setFreeAttributePoints(player.getFreeAttributePoints() + totalPoints);

                // 重新计算属性（使用领域服务）
                playerStatsService.recalculateStats(player);
                resultMessage = String.format("重置属性点成功，获得 %d 个可分配属性点", totalPoints);
                break;
            case "OPEN_GIFT":
                // 打开礼包
                String giftId = item.getEffectValue() != null ? String.valueOf(item.getEffectValue()) : item.getId();
                // 如果 effectValue 是字符串形式的礼包ID，需要从 item 配置中获取
                ItemConfig itemConfig = configDataManager.getItem(item.getId());
                if (itemConfig != null && itemConfig.getEffect() != null && itemConfig.getEffect().equals("OPEN_GIFT")) {
                    // effectValue 字段存储的是礼包ID字符串，但由于是 Integer 类型，我们用 item.getId() 作为 giftId
                    // 实际上礼包ID应该和物品ID一致，或者从描述中解析
                    giftId = item.getId();
                }

                List<GiftLootConfig> giftLoots = configDataManager.getGiftLoot(giftId);
                if (giftLoots == null || giftLoots.isEmpty()) {
                    return OperationResult.error("礼包内容为空");
                }

                // 检查背包空间
                int requiredSlots = 0;
                for (GiftLootConfig loot : giftLoots) {
                    if (configDataManager.getEquipment(loot.getItemId()) != null) {
                        requiredSlots += loot.getQuantity();
                    } else {
                        // 普通物品检查是否可堆叠
                        boolean canStack = false;
                        for (Player.InventorySlot slot : player.getInventory()) {
                            if (slot.isItem() && slot.getItem().getId().equals(loot.getItemId())) {
                                canStack = true;
                                break;
                            }
                        }
                        if (!canStack) {
                            requiredSlots++;
                        }
                    }
                }

                int availableSlots = 50 - player.getInventory().size();
                if (availableSlots < requiredSlots) {
                    return OperationResult.error("背包空间不足，需要 " + requiredSlots + " 个空位");
                }

                // 添加礼包物品到背包
                StringBuilder giftMessage = new StringBuilder();
                giftMessage.append("你打开了").append(itemName).append("，获得了：\n");

                for (GiftLootConfig loot : giftLoots) {
                    addItemToPlayer(player, loot.getItemId(), loot.getQuantity());
                    String lootItemName = loot.getItemId();
                    var lootItemConfig = configDataManager.getItem(loot.getItemId());
                    if (lootItemConfig != null) {
                        lootItemName = lootItemConfig.getName();
                    } else {
                        var lootEqConfig = configDataManager.getEquipment(loot.getItemId());
                        if (lootEqConfig != null) {
                            lootItemName = lootEqConfig.getName();
                        }
                    }
                    giftMessage.append("  - ").append(lootItemName);
                    if (loot.getQuantity() > 1) {
                        giftMessage.append(" x").append(loot.getQuantity());
                    }
                    giftMessage.append("\n");
                }

                resultMessage = giftMessage.toString().trim();
                break;
            default:
                return OperationResult.error("未知的物品效果");
        }

        // 减少物品数量
        targetSlot.setQuantity(targetSlot.getQuantity() - 1);
        if (targetSlot.getQuantity() <= 0) {
            player.getInventory().remove(targetSlot);
        }

        // 保存玩家状态
        PlayerEntity entity = playerMapper.toEntity(player);
        playerRepository.save(entity);

        // 生成背包更新信息
        String inventoryUpdate = generateInventoryUpdate(player);

        return OperationResult.success(resultMessage != null ? resultMessage : "使用物品成功: " + itemName, inventoryUpdate);
    }

    /**
     * 生成背包更新信息
     */
    private String generateInventoryUpdate(Player player) {
        StringBuilder sb = new StringBuilder();
        sb.append("背包更新：\n");
        if (player.getInventory() != null && !player.getInventory().isEmpty()) {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    sb.append(String.format("%s x%d\n", slot.getItem().getName(), slot.getQuantity()));
                } else if (slot.isEquipment()) {
                    sb.append(String.format("%s\n", slot.getEquipment().getDisplayName()));
                }
            }
        } else {
            sb.append("背包为空");
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public OperationResult equipItem(String playerId, String itemName) {
        Player player = getPlayerState(playerId);
        if (player == null) {
            return OperationResult.error("玩家不存在");
        }

        // 查找装备
        Player.InventorySlot targetSlot = null;
        for (Player.InventorySlot slot : player.getInventory()) {
            if (slot.isEquipment() && slot.getEquipment().getDisplayName().equals(itemName)) {
                targetSlot = slot;
                break;
            }
        }

        if (targetSlot == null) {
            return OperationResult.error("装备不存在");
        }

        Equipment equipment = targetSlot.getEquipment();
        Equipment.EquipmentSlot slot = equipment.getSlot();

        // 如果该槽位已有装备，卸下并放回背包
        if (player.getEquipment().containsKey(slot)) {
            Equipment oldEquipment = player.getEquipment().get(slot);
            player.getInventory().add(Player.InventorySlot.forEquipment(oldEquipment));
        }

        // 装备新装备
        player.getEquipment().put(slot, equipment);
        player.getInventory().remove(targetSlot);

        // 重新计算属性（使用领域服务）
        playerStatsService.recalculateStats(player);

        // 保存玩家状态
        PlayerEntity entity = playerMapper.toEntity(player);
        playerRepository.save(entity);

        return OperationResult.success("装备成功: " + itemName);
    }

    @Override
    @Transactional
    public OperationResult addAttribute(String playerId, String attributeType, int amount) {
        Player player = getPlayerState(playerId);
        if (player == null) {
            return OperationResult.error("玩家不存在");
        }

        if (amount <= 0) {
            return OperationResult.error("数量必须大于0");
        }

        if (player.getFreeAttributePoints() < amount) {
            return OperationResult.error("可用属性点不足");
        }

        // 添加属性点
        switch (attributeType.toLowerCase()) {
            case "str":
            case "strength":
                player.setStrength(player.getStrength() + amount);
                break;
            case "agi":
            case "agility":
                player.setAgility(player.getAgility() + amount);
                break;
            case "int":
            case "intelligence":
                player.setIntelligence(player.getIntelligence() + amount);
                break;
            case "vit":
            case "vitality":
                player.setVitality(player.getVitality() + amount);
                break;
            default:
                return OperationResult.error("未知的属性类型");
        }

        player.setFreeAttributePoints(player.getFreeAttributePoints() - amount);

        // 重新计算属性（使用领域服务）
        playerStatsService.recalculateStats(player);

        // 保存玩家状态
        PlayerEntity entity = playerMapper.toEntity(player);
        playerRepository.save(entity);

        // 返回完整的角色状态，便于前端刷新
        String attrName = switch (attributeType.toLowerCase()) {
            case "str", "strength" -> "力量";
            case "agi", "agility" -> "敏捷";
            case "int", "intelligence" -> "智力";
            case "vit", "vitality" -> "体力";
            default -> attributeType;
        };

        String statusInfo = String.format(
            "添加属性点成功: %s +%d\n" +
            "当前属性：力量%d 敏捷%d 智力%d 体力%d\n" +
            "可用属性点: %d\n" +
            "生命%d/%d 法力%d/%d\n" +
            "物攻%d 物防%d 法攻%d 法防%d 速度%d\n" +
            "暴击率%.1f%% 暴击伤害%.1f%% 命中率%.1f%% 闪避率%.1f%%",
            attrName, amount,
            player.getStrength(), player.getAgility(), player.getIntelligence(), player.getVitality(),
            player.getFreeAttributePoints(),
            player.getCurrentHealth(), player.getMaxHealth(), player.getCurrentMana(), player.getMaxMana(),
            player.getPhysicalAttack(), player.getPhysicalDefense(), player.getMagicAttack(), player.getMagicDefense(), player.getSpeed(),
            player.getCritRate() * 100, player.getCritDamage() * 100, player.getHitRate() * 100, player.getDodgeRate() * 100
        );

        return OperationResult.success(statusInfo);
    }

    @Override
    @Transactional
    public OperationResult logout(String sessionId) {
        Optional<AccountEntity> accountOpt = accountRepository.findBySessionId(sessionId);
        if (!accountOpt.isPresent()) {
            return OperationResult.error("会话不存在");
        }

        AccountEntity account = accountOpt.get();
        account.setOnline(false);
        account.setLastLogoutTime(System.currentTimeMillis());
        account.setSessionId(null);
        accountRepository.save(account);

        return OperationResult.success("下线成功");
    }

    @Override
    public OperationResult wait(String playerId, int seconds) {
        if (seconds <= 0 || seconds > 60) {
            return OperationResult.error("等待时间必须在1-60秒之间");
        }

        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OperationResult.error("等待被中断");
        }

        return OperationResult.success("等待 " + seconds + " 秒完成");
    }

    /**
     * 将物品添加到玩家背包
     */
    private void addItemToPlayer(Player player, String itemId, int quantity) {
        // 检查是否已有该物品（只对普通物品堆叠）
        boolean found = false;
        if (configDataManager.getEquipment(itemId) == null) {
            // 普通物品可以堆叠
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem() && slot.getItem().getId().equals(itemId)) {
                    slot.setQuantity(slot.getQuantity() + quantity);
                    found = true;
                    break;
                }
            }
        }

        // 如果没有找到或是装备，添加新的物品槽
        if (!found && player.getInventory().size() < 50) {
            var eqConfig = configDataManager.getEquipment(itemId);
            if (eqConfig != null) {
                // 装备需要生成实例编号，每件装备单独添加
                for (int i = 0; i < quantity; i++) {
                    if (player.getInventory().size() < 50) {
                        player.getInventory().add(Player.InventorySlot.forEquipment(
                            configMapper.toDomain(eqConfig)));
                    }
                }
            } else {
                var itemConfig = configDataManager.getItem(itemId);
                if (itemConfig != null) {
                    player.getInventory().add(Player.InventorySlot.forItem(
                        configMapper.toDomain(itemConfig), quantity));
                }
            }
        }
    }
}
