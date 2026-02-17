package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.infrastructure.config.data.character.RoleConfig;
import com.heibai.clawworld.infrastructure.config.data.item.EquipmentConfig;
import com.heibai.clawworld.infrastructure.config.data.item.ItemConfig;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.character.Role;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.item.Item;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.service.PlayerStatsService;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PartyEntity;
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
        player.setFaction("PLAYER_" + player.getId());

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
    public String getPlayerDetailedStatus(String playerId) {
        Player player = getPlayerState(playerId);
        if (player == null) {
            return "玩家不存在";
        }

        StringBuilder status = new StringBuilder();

        // 基础信息
        status.append("=== 角色信息 ===\n");
        status.append("昵称: ").append(player.getName()).append("\n");

        // 获取职业名称
        RoleConfig roleConfig = configDataManager.getRole(player.getRoleId());
        if (roleConfig != null) {
            status.append("职业: ").append(roleConfig.getName()).append("\n");
        }

        status.append("等级: ").append(player.getLevel()).append("\n");
        status.append("经验: ").append(player.getExperience()).append("\n");
        status.append("金钱: ").append(player.getGold()).append("\n\n");

        // 生命和法力
        status.append("=== 生命与法力 ===\n");
        status.append("生命值: ").append(player.getCurrentHealth()).append("/").append(player.getMaxHealth()).append("\n");
        status.append("法力值: ").append(player.getCurrentMana()).append("/").append(player.getMaxMana()).append("\n\n");

        // 四维属性
        status.append("=== 四维属性 ===\n");
        status.append("力量: ").append(player.getStrength()).append("\n");
        status.append("敏捷: ").append(player.getAgility()).append("\n");
        status.append("智力: ").append(player.getIntelligence()).append("\n");
        status.append("体力: ").append(player.getVitality()).append("\n");
        status.append("可用属性点: ").append(player.getFreeAttributePoints()).append("\n\n");

        // 战斗属性
        status.append("=== 战斗属性 ===\n");
        status.append("物理攻击: ").append(player.getPhysicalAttack()).append("\n");
        status.append("物理防御: ").append(player.getPhysicalDefense()).append("\n");
        status.append("法术攻击: ").append(player.getMagicAttack()).append("\n");
        status.append("法术防御: ").append(player.getMagicDefense()).append("\n");
        status.append("速度: ").append(player.getSpeed()).append("\n");
        status.append("暴击率: ").append(String.format("%.1f%%", player.getCritRate() * 100)).append("\n");
        status.append("暴击伤害: ").append(String.format("%.1f%%", player.getCritDamage() * 100)).append("\n");
        status.append("命中率: ").append(String.format("%.1f%%", player.getHitRate() * 100)).append("\n");
        status.append("闪避率: ").append(String.format("%.1f%%", player.getDodgeRate() * 100)).append("\n\n");

        // 装备栏
        status.append("=== 装备栏 ===\n");
        if (player.getEquipment() == null || player.getEquipment().isEmpty()) {
            status.append("- 无装备\n");
        } else {
            for (Map.Entry<Equipment.EquipmentSlot, Equipment> entry : player.getEquipment().entrySet()) {
                Equipment eq = entry.getValue();
                status.append(getSlotDisplayName(entry.getKey())).append(": ")
                      .append(eq.getDisplayName())
                      .append(" (").append(eq.getRarity().name()).append(")\n");
            }
        }
        status.append("\n");

        // 背包
        status.append("=== 背包 (").append(player.getInventory() != null ? player.getInventory().size() : 0).append("/50) ===\n");
        if (player.getInventory() == null || player.getInventory().isEmpty()) {
            status.append("- 背包为空\n");
        } else {
            for (Player.InventorySlot slot : player.getInventory()) {
                if (slot.isItem()) {
                    status.append("- ").append(slot.getItem().getName())
                          .append(" x").append(slot.getQuantity()).append("\n");
                } else if (slot.isEquipment()) {
                    status.append("- ").append(slot.getEquipment().getDisplayName())
                          .append(" (").append(slot.getEquipment().getRarity().name()).append(")\n");
                }
            }
        }
        status.append("\n");

        // 技能
        status.append("=== 技能 (").append(player.getSkills() != null ? player.getSkills().size() : 0).append("/10) ===\n");
        if (player.getSkills() == null || player.getSkills().isEmpty()) {
            status.append("- 无技能\n");
        } else {
            for (String skillId : player.getSkills()) {
                // 这里可以从配置中获取技能名称，暂时直接显示ID
                status.append("- ").append(skillId).append("\n");
            }
        }
        status.append("\n");

        // 队伍信息
        status.append("=== 队伍信息 ===\n");
        if (player.getPartyId() != null) {
            Optional<PartyEntity> partyOpt = partyRepository.findById(player.getPartyId());
            if (partyOpt.isPresent()) {
                PartyEntity party = partyOpt.get();
                status.append("队伍人数: ").append(party.getMemberIds().size()).append("/4\n");
                status.append("队长: ").append(player.isPartyLeader() ? "是" : "否").append("\n");
            } else {
                status.append("- 无队伍\n");
            }
        } else {
            status.append("- 无队伍\n");
        }

        return status.toString();
    }

    /**
     * 获取装备槽位的显示名称
     */
    private String getSlotDisplayName(Equipment.EquipmentSlot slot) {
        switch (slot) {
            case HEAD: return "头部";
            case CHEST: return "上装";
            case LEGS: return "下装";
            case FEET: return "鞋子";
            case LEFT_HAND: return "左手";
            case RIGHT_HAND: return "右手";
            case ACCESSORY1: return "饰品1";
            case ACCESSORY2: return "饰品2";
            default: return slot.name();
        }
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

        switch (item.getEffect()) {
            case "restore_health":
                int healthRestore = item.getEffectValue() != null ? item.getEffectValue() : 0;
                player.setCurrentHealth(Math.min(player.getCurrentHealth() + healthRestore, player.getMaxHealth()));
                break;
            case "restore_mana":
                int manaRestore = item.getEffectValue() != null ? item.getEffectValue() : 0;
                player.setCurrentMana(Math.min(player.getCurrentMana() + manaRestore, player.getMaxMana()));
                break;
            case "learn_skill":
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
                }
                break;
            case "reset_attributes":
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

        return OperationResult.success("使用物品成功: " + itemName);
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

        return OperationResult.success("添加属性点成功: " + attributeType + " +" + amount);
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
}
