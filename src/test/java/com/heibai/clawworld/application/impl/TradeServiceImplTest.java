package com.heibai.clawworld.application.impl;

import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.item.Equipment;
import com.heibai.clawworld.domain.item.Item;
import com.heibai.clawworld.domain.item.Rarity;
import com.heibai.clawworld.domain.trade.Trade;
import com.heibai.clawworld.infrastructure.persistence.entity.TradeEntity;
import com.heibai.clawworld.infrastructure.persistence.mapper.TradeMapper;
import com.heibai.clawworld.infrastructure.persistence.repository.TradeRepository;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.application.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 交易服务测试
 */
@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private PlayerSessionService playerSessionService;

    @Mock
    private com.heibai.clawworld.infrastructure.persistence.repository.PlayerRepository playerRepository;

    @Mock
    private com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository accountRepository;

    @Mock
    private com.heibai.clawworld.application.service.WindowStateService windowStateService;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private Player player1;
    private Player player2;
    private com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity playerEntity1;
    private com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity playerEntity2;
    private Item testItem;
    private Equipment testEquipment;

    @BeforeEach
    void setUp() {
        // 创建测试玩家实体1
        playerEntity1 = new com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity();
        playerEntity1.setId("player1");
        playerEntity1.setName("玩家1");

        // 创建测试玩家实体2
        playerEntity2 = new com.heibai.clawworld.infrastructure.persistence.entity.PlayerEntity();
        playerEntity2.setId("player2");
        playerEntity2.setName("玩家2");

        // 创建测试玩家1
        player1 = new Player();
        player1.setId("player1");
        player1.setGold(1000);
        player1.setInventory(new ArrayList<>());

        // 创建测试物品
        testItem = new Item();
        testItem.setId("item1");
        testItem.setName("生命药剂");
        testItem.setType(Item.ItemType.CONSUMABLE);
        player1.getInventory().add(Player.InventorySlot.forItem(testItem, 10));

        // 创建测试装备
        testEquipment = new Equipment();
        testEquipment.setId("equipment1");
        testEquipment.setName("铁剑");
        testEquipment.setInstanceNumber(1L);
        testEquipment.setSlot(Equipment.EquipmentSlot.RIGHT_HAND);
        testEquipment.setRarity(Rarity.COMMON);
        player1.getInventory().add(Player.InventorySlot.forEquipment(testEquipment));

        // 创建测试玩家2
        player2 = new Player();
        player2.setId("player2");
        player2.setGold(500);
        player2.setInventory(new ArrayList<>());

        // Mock WindowStateService 行为 (使用 lenient 模式，因为不是所有测试都需要这些 mock)
        lenient().when(windowStateService.getCurrentWindowType(anyString())).thenReturn("MAP");
        lenient().when(windowStateService.transitionWindow(anyString(), anyString(), anyString())).thenReturn(true);
        lenient().when(windowStateService.transitionWindows(any())).thenReturn(true);
    }

    @Test
    void testRequestTrade_Success() {
        // 准备
        when(tradeRepository.findActiveTradesByPlayerId(any(), anyString())).thenReturn(new ArrayList<>());
        when(playerRepository.findAll()).thenReturn(List.of(playerEntity1, playerEntity2));
        when(playerSessionService.getPlayerState("player2")).thenReturn(player2);
        when(tradeMapper.toEntity(any(Trade.class))).thenReturn(new TradeEntity());
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(new TradeEntity());

        // 执行
        TradeService.TradeResult result = tradeService.requestTrade("player1", "玩家2");

        // 验证
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("发起交易请求"));
        verify(tradeRepository, times(1)).save(any(TradeEntity.class));
    }

    @Test
    void testRequestTrade_AlreadyHasActiveTrade() {
        // 准备 - 玩家1已有活跃交易
        List<TradeEntity> activeTrades = new ArrayList<>();
        activeTrades.add(new TradeEntity());
        when(tradeRepository.findActiveTradesByPlayerId(TradeEntity.TradeStatus.ACTIVE, "player1"))
            .thenReturn(activeTrades);

        // 执行
        TradeService.TradeResult result = tradeService.requestTrade("player1", "player2");

        // 验证
        assertFalse(result.isSuccess());
        assertEquals("你已有进行中的交易", result.getMessage());
        verify(tradeRepository, never()).save(any(TradeEntity.class));
    }

    @Test
    void testRequestTrade_TargetPlayerNotExists() {
        // 准备
        when(tradeRepository.findActiveTradesByPlayerId(any(), anyString())).thenReturn(new ArrayList<>());
        when(playerRepository.findAll()).thenReturn(List.of(playerEntity1)); // 只返回player1，没有player2

        // 执行
        TradeService.TradeResult result = tradeService.requestTrade("player1", "不存在的玩家");

        // 验证
        assertFalse(result.isSuccess());
        assertEquals("目标玩家不存在", result.getMessage());
    }

    @Test
    void testAcceptTradeRequest_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.PENDING);

        List<TradeEntity> pendingTrades = new ArrayList<>();
        pendingTrades.add(tradeEntity);

        when(playerRepository.findAll()).thenReturn(List.of(playerEntity1, playerEntity2));
        when(tradeRepository.findByStatusAndReceiverId(TradeEntity.TradeStatus.PENDING, "player2"))
            .thenReturn(pendingTrades);
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);
        when(playerRepository.findById("player1")).thenReturn(Optional.of(playerEntity1));
        when(playerRepository.findById("player2")).thenReturn(Optional.of(playerEntity2));

        // 执行
        TradeService.TradeResult result = tradeService.acceptTradeRequest("player2", "玩家1");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("接受交易成功", result.getMessage());
        assertEquals(TradeEntity.TradeStatus.ACTIVE, tradeEntity.getStatus());
    }

    @Test
    void testRejectTradeRequest_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.PENDING);

        List<TradeEntity> pendingTrades = new ArrayList<>();
        pendingTrades.add(tradeEntity);

        when(playerRepository.findAll()).thenReturn(List.of(playerEntity1, playerEntity2));
        when(tradeRepository.findByStatusAndReceiverId(TradeEntity.TradeStatus.PENDING, "player2"))
            .thenReturn(pendingTrades);
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);
        when(playerRepository.findById("player1")).thenReturn(Optional.of(playerEntity1));

        // 执行
        TradeService.OperationResult result = tradeService.rejectTradeRequest("player2", "玩家1");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("拒绝交易成功", result.getMessage());
        assertEquals(TradeEntity.TradeStatus.CANCELLED, tradeEntity.getStatus());
    }

    @Test
    void testAddItem_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(false);
        tradeEntity.setReceiverLocked(false);

        TradeEntity.TradeOffer initiatorOffer = new TradeEntity.TradeOffer();
        initiatorOffer.setItems(new ArrayList<>());
        tradeEntity.setInitiatorOffer(initiatorOffer);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(playerSessionService.getPlayerState("player1")).thenReturn(player1);
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);

        // 执行
        TradeService.OperationResult result = tradeService.addItem("trade1", "player1", "生命药剂");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("添加物品成功: 生命药剂", result.getMessage());
        assertEquals(1, initiatorOffer.getItems().size());
    }

    @Test
    void testAddItem_TradeLocked() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(true); // 已锁定

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));

        // 执行
        TradeService.OperationResult result = tradeService.addItem("trade1", "player1", "生命药剂");

        // 验证
        assertFalse(result.isSuccess());
        assertEquals("交易已锁定，无法添加物品", result.getMessage());
    }

    @Test
    void testSetMoney_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(false);

        TradeEntity.TradeOffer initiatorOffer = new TradeEntity.TradeOffer();
        tradeEntity.setInitiatorOffer(initiatorOffer);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(playerSessionService.getPlayerState("player1")).thenReturn(player1);
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);

        // 执行
        TradeService.OperationResult result = tradeService.setMoney("trade1", "player1", 100);

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("设置金额成功: 100", result.getMessage());
        assertEquals(100, initiatorOffer.getGold());
    }

    @Test
    void testSetMoney_InsufficientGold() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(false);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(playerSessionService.getPlayerState("player1")).thenReturn(player1);

        // 执行 - 尝试设置超过玩家拥有的金钱
        TradeService.OperationResult result = tradeService.setMoney("trade1", "player1", 2000);

        // 验证
        assertFalse(result.isSuccess());
        assertEquals("金钱不足", result.getMessage());
    }

    @Test
    void testLockTrade_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(false);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);

        // 执行
        TradeService.OperationResult result = tradeService.lockTrade("trade1", "player1");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("锁定交易成功", result.getMessage());
        assertTrue(tradeEntity.isInitiatorLocked());
    }

    @Test
    void testUnlockTrade_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(true);
        tradeEntity.setInitiatorConfirmed(true);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);

        // 执行
        TradeService.OperationResult result = tradeService.unlockTrade("trade1", "player1");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("解锁交易成功", result.getMessage());
        assertFalse(tradeEntity.isInitiatorLocked());
        assertFalse(tradeEntity.isInitiatorConfirmed());
    }

    @Test
    void testConfirmTrade_WaitingForOtherPlayer() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(true);
        tradeEntity.setReceiverLocked(true);
        tradeEntity.setInitiatorConfirmed(false);
        tradeEntity.setReceiverConfirmed(false);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);

        // 执行 - 玩家1确认
        TradeService.OperationResult result = tradeService.confirmTrade("trade1", "player1");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("确认交易成功，等待对方确认", result.getMessage());
        assertTrue(tradeEntity.isInitiatorConfirmed());
        assertFalse(tradeEntity.isReceiverConfirmed());
    }

    @Test
    void testConfirmTrade_NotBothLocked() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);
        tradeEntity.setInitiatorLocked(true);
        tradeEntity.setReceiverLocked(false); // 对方未锁定

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));

        // 执行
        TradeService.OperationResult result = tradeService.confirmTrade("trade1", "player1");

        // 验证
        assertFalse(result.isSuccess());
        assertEquals("双方必须先锁定交易", result.getMessage());
    }

    @Test
    void testCancelTrade_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(tradeRepository.save(any(TradeEntity.class))).thenReturn(tradeEntity);
        when(playerRepository.findById("player1")).thenReturn(Optional.of(playerEntity1));
        when(playerRepository.findById("player2")).thenReturn(Optional.of(playerEntity2));

        // 执行
        TradeService.OperationResult result = tradeService.cancelTrade("trade1", "player1");

        // 验证
        assertTrue(result.isSuccess());
        assertEquals("取消交易成功", result.getMessage());
        assertEquals(TradeEntity.TradeStatus.CANCELLED, tradeEntity.getStatus());
    }

    @Test
    void testCancelTrade_NotParticipant() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.ACTIVE);

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));

        // 执行 - 非参与者尝试取消
        TradeService.OperationResult result = tradeService.cancelTrade("trade1", "player3");

        // 验证
        assertFalse(result.isSuccess());
        assertEquals("你不是此交易的参与者", result.getMessage());
    }

    @Test
    void testGetTradeState_Success() {
        // 准备
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        Trade trade = new Trade();
        trade.setId("trade1");

        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(tradeMapper.toDomain(tradeEntity)).thenReturn(trade);

        // 执行
        Trade result = tradeService.getTradeState("trade1");

        // 验证
        assertNotNull(result);
        assertEquals("trade1", result.getId());
    }

    @Test
    void testGetTradeState_NotFound() {
        // 准备
        when(tradeRepository.findById("trade1")).thenReturn(Optional.empty());

        // 执行
        Trade result = tradeService.getTradeState("trade1");

        // 验证
        assertNull(result);
    }

    @Test
    void testCompleteTradeFlow() {
        // 这是一个完整的交易流程测试
        // 1. 发起交易
        when(tradeRepository.findActiveTradesByPlayerId(any(), anyString())).thenReturn(new ArrayList<>());
        when(playerRepository.findAll()).thenReturn(List.of(playerEntity1, playerEntity2));
        when(playerSessionService.getPlayerState("player2")).thenReturn(player2);
        when(tradeMapper.toEntity(any(Trade.class))).thenReturn(new TradeEntity());
        when(tradeRepository.save(any(TradeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findById("player1")).thenReturn(Optional.of(playerEntity1));

        TradeService.TradeResult requestResult = tradeService.requestTrade("player1", "玩家2");
        assertTrue(requestResult.isSuccess());

        // 2. 接受交易
        TradeEntity tradeEntity = new TradeEntity();
        tradeEntity.setId("trade1");
        tradeEntity.setInitiatorId("player1");
        tradeEntity.setReceiverId("player2");
        tradeEntity.setStatus(TradeEntity.TradeStatus.PENDING);
        tradeEntity.setInitiatorOffer(new TradeEntity.TradeOffer());
        tradeEntity.setReceiverOffer(new TradeEntity.TradeOffer());
        tradeEntity.getInitiatorOffer().setItems(new ArrayList<>());
        tradeEntity.getReceiverOffer().setItems(new ArrayList<>());

        List<TradeEntity> pendingTrades = new ArrayList<>();
        pendingTrades.add(tradeEntity);
        when(tradeRepository.findByStatusAndReceiverId(TradeEntity.TradeStatus.PENDING, "player2"))
            .thenReturn(pendingTrades);
        when(playerRepository.findById("player2")).thenReturn(Optional.of(playerEntity2));

        TradeService.TradeResult acceptResult = tradeService.acceptTradeRequest("player2", "玩家1");
        assertTrue(acceptResult.isSuccess());
        assertEquals(TradeEntity.TradeStatus.ACTIVE, tradeEntity.getStatus());

        // 3. 添加物品和金钱
        when(tradeRepository.findById("trade1")).thenReturn(Optional.of(tradeEntity));
        when(playerSessionService.getPlayerState("player1")).thenReturn(player1);

        TradeService.OperationResult addItemResult = tradeService.addItem("trade1", "player1", "生命药剂");
        assertTrue(addItemResult.isSuccess());

        TradeService.OperationResult setMoneyResult = tradeService.setMoney("trade1", "player1", 100);
        assertTrue(setMoneyResult.isSuccess());

        // 4. 锁定交易
        TradeService.OperationResult lockResult1 = tradeService.lockTrade("trade1", "player1");
        assertTrue(lockResult1.isSuccess());

        tradeEntity.setReceiverLocked(true); // 模拟对方也锁定
        TradeService.OperationResult lockResult2 = tradeService.lockTrade("trade1", "player2");
        assertTrue(lockResult2.isSuccess());

        // 5. 确认交易
        TradeService.OperationResult confirmResult1 = tradeService.confirmTrade("trade1", "player1");
        assertTrue(confirmResult1.isSuccess());

        // 验证整个流程
        assertTrue(tradeEntity.isInitiatorLocked());
        assertTrue(tradeEntity.isReceiverLocked());
        assertTrue(tradeEntity.isInitiatorConfirmed());
    }
}
