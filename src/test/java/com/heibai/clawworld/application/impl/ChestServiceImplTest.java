package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.application.service.ChestService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.infrastructure.config.ConfigDataManager;
import com.heibai.clawworld.infrastructure.config.data.map.ChestConfig;
import com.heibai.clawworld.infrastructure.config.data.map.ChestLootConfig;
import com.heibai.clawworld.infrastructure.persistence.entity.ChestInstanceEntity;
import com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.ConfigMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.ChestInstanceRepository;
import com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 宝箱服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class ChestServiceImplTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ChestInstanceRepository chestInstanceRepository;

    @Mock
    private ConfigDataManager configDataManager;

    @Mock
    private PlayerSessionService playerSessionService;

    @Mock
    private ConfigMapper configMapper;

    @InjectMocks
    private ChestServiceImpl chestService;

    private PlayerEntity testPlayer;
    private ChestInstanceEntity testSmallChest;
    private ChestInstanceEntity testLargeChest;
    private ChestConfig smallChestConfig;
    private ChestConfig largeChestConfig;

    @BeforeEach
    void setUp() {
        // 设置测试玩家
        testPlayer = new PlayerEntity();
        testPlayer.setId("player1");
        testPlayer.setName("测试玩家");
        testPlayer.setCurrentMapId("starter_village");
        testPlayer.setX(8);
        testPlayer.setY(3);
        testPlayer.setInCombat(false);

        // 设置小宝箱实例
        testSmallChest = new ChestInstanceEntity();
        testSmallChest.setId("chest1");
        testSmallChest.setMapId("starter_village");
        testSmallChest.setInstanceId("small_chest_common_8_3");
        testSmallChest.setTemplateId("small_chest_common");
        testSmallChest.setDisplayName("普通小宝箱");
        testSmallChest.setX(8);
        testSmallChest.setY(3);
        testSmallChest.setChestType("SMALL");
        testSmallChest.setOpened(false);
        testSmallChest.setOpenedByPlayers(new HashSet<>());

        // 设置大宝箱实例
        testLargeChest = new ChestInstanceEntity();
        testLargeChest.setId("chest2");
        testLargeChest.setMapId("forest_entrance");
        testLargeChest.setInstanceId("large_chest_common_9_5");
        testLargeChest.setTemplateId("large_chest_common");
        testLargeChest.setDisplayName("普通大宝箱");
        testLargeChest.setX(9);
        testLargeChest.setY(5);
        testLargeChest.setChestType("LARGE");
        testLargeChest.setOpened(false);

        // 设置小宝箱配置
        smallChestConfig = new ChestConfig();
        smallChestConfig.setId("small_chest_common");
        smallChestConfig.setName("普通小宝箱");
        smallChestConfig.setDescription("一个普通的小宝箱");
        smallChestConfig.setType("SMALL");
        smallChestConfig.setRespawnSeconds(0);

        // 设置大宝箱配置
        largeChestConfig = new ChestConfig();
        largeChestConfig.setId("large_chest_common");
        largeChestConfig.setName("普通大宝箱");
        largeChestConfig.setDescription("一个普通的大宝箱");
        largeChestConfig.setType("LARGE");
        largeChestConfig.setRespawnSeconds(300);
    }

    @Test
    @DisplayName("打开小宝箱 - 成功")
    void openSmallChest_Success() {
        // 准备
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("starter_village")).thenReturn(List.of(testSmallChest));
        when(configDataManager.getChest("small_chest_common")).thenReturn(smallChestConfig);
        when(configDataManager.getChestLoot("small_chest_common")).thenReturn(Collections.emptyList());

        Player player = new Player();
        player.setId("player1");
        player.setInventory(new ArrayList<>());
        when(playerSessionService.getPlayerState("player1")).thenReturn(player);

        // 执行
        ChestService.OpenChestResult result = chestService.openChest("player1", "普通小宝箱");

        // 验证
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("普通小宝箱"));
        verify(chestInstanceRepository).save(any(ChestInstanceEntity.class));
    }

    @Test
    @DisplayName("打开小宝箱 - 已开启过")
    void openSmallChest_AlreadyOpened() {
        // 准备
        testSmallChest.markPlayerOpened("player1");
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("starter_village")).thenReturn(List.of(testSmallChest));
        when(configDataManager.getChest("small_chest_common")).thenReturn(smallChestConfig);

        // 执行
        ChestService.OpenChestResult result = chestService.openChest("player1", "普通小宝箱");

        // 验证
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("已经开启过"));
    }

    @Test
    @DisplayName("打开大宝箱 - 成功")
    void openLargeChest_Success() {
        // 准备
        testPlayer.setCurrentMapId("forest_entrance");
        testPlayer.setX(9);
        testPlayer.setY(5);
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("forest_entrance")).thenReturn(List.of(testLargeChest));
        when(configDataManager.getChest("large_chest_common")).thenReturn(largeChestConfig);
        when(configDataManager.getChestLoot("large_chest_common")).thenReturn(Collections.emptyList());

        Player player = new Player();
        player.setId("player1");
        player.setInventory(new ArrayList<>());
        when(playerSessionService.getPlayerState("player1")).thenReturn(player);

        // 执行
        ChestService.OpenChestResult result = chestService.openChest("player1", "普通大宝箱");

        // 验证
        assertTrue(result.isSuccess());
        verify(chestInstanceRepository).save(argThat(chest ->
            chest.isOpened() && chest.getLastOpenTime() != null
        ));
    }

    @Test
    @DisplayName("打开大宝箱 - 未刷新")
    void openLargeChest_NotRefreshed() {
        // 准备
        testPlayer.setCurrentMapId("forest_entrance");
        testPlayer.setX(9);
        testPlayer.setY(5);
        testLargeChest.setOpened(true);
        testLargeChest.setLastOpenTime(System.currentTimeMillis()); // 刚刚开启

        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("forest_entrance")).thenReturn(List.of(testLargeChest));
        when(configDataManager.getChest("large_chest_common")).thenReturn(largeChestConfig);

        // 执行
        ChestService.OpenChestResult result = chestService.openChest("player1", "普通大宝箱");

        // 验证
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("秒后刷新"));
    }

    @Test
    @DisplayName("打开宝箱 - 距离太远")
    void openChest_TooFar() {
        // 准备
        testPlayer.setX(0);
        testPlayer.setY(0);
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("starter_village")).thenReturn(List.of(testSmallChest));

        // 执行
        ChestService.OpenChestResult result = chestService.openChest("player1", "普通小宝箱");

        // 验证
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("太远"));
    }

    @Test
    @DisplayName("打开宝箱 - 战斗中")
    void openChest_InCombat() {
        // 准备
        testPlayer.setInCombat(true);
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));

        // 执行
        ChestService.OpenChestResult result = chestService.openChest("player1", "普通小宝箱");

        // 验证
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("战斗中"));
    }

    @Test
    @DisplayName("查看宝箱信息 - 小宝箱未开启")
    void inspectSmallChest_NotOpened() {
        // 准备
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("starter_village")).thenReturn(List.of(testSmallChest));
        when(configDataManager.getChest("small_chest_common")).thenReturn(smallChestConfig);

        // 执行
        ChestService.ChestInfo info = chestService.inspectChest("player1", "普通小宝箱");

        // 验证
        assertTrue(info.isSuccess());
        assertEquals("普通小宝箱", info.getChestName());
        assertEquals("SMALL", info.getChestType());
        assertTrue(info.isCanOpen());
    }

    @Test
    @DisplayName("查看宝箱信息 - 小宝箱已开启")
    void inspectSmallChest_Opened() {
        // 准备
        testSmallChest.markPlayerOpened("player1");
        when(playerRepository.findById("player1")).thenReturn(Optional.of(testPlayer));
        when(chestInstanceRepository.findByMapId("starter_village")).thenReturn(List.of(testSmallChest));
        when(configDataManager.getChest("small_chest_common")).thenReturn(smallChestConfig);

        // 执行
        ChestService.ChestInfo info = chestService.inspectChest("player1", "普通小宝箱");

        // 验证
        assertTrue(info.isSuccess());
        assertFalse(info.isCanOpen());
    }
}
