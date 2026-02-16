package com.heibai.clawworld.command;

import com.heibai.clawworld.command.impl.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指令验证测试
 */
@DisplayName("指令验证测试")
class CommandValidationTest {

    // ==================== 注册指令验证 ====================

    @Test
    @DisplayName("注册指令验证 - 成功")
    void testRegisterCommand_Valid() {
        RegisterCommand command = RegisterCommand.builder()
                .roleName("战士")
                .playerName("张三")
                .rawCommand("register 战士 张三")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("注册指令验证 - 职业名为空")
    void testRegisterCommand_EmptyRoleName() {
        RegisterCommand command = RegisterCommand.builder()
                .roleName("")
                .playerName("张三")
                .rawCommand("register  张三")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("注册指令验证 - 玩家名为空")
    void testRegisterCommand_EmptyPlayerName() {
        RegisterCommand command = RegisterCommand.builder()
                .roleName("战士")
                .playerName("")
                .rawCommand("register 战士 ")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== 聊天指令验证 ====================

    @Test
    @DisplayName("聊天指令验证 - 成功")
    void testSayCommand_Valid() {
        SayCommand command = SayCommand.builder()
                .channel("world")
                .message("大家好")
                .rawCommand("say world 大家好")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("聊天指令验证 - 无效频道")
    void testSayCommand_InvalidChannel() {
        SayCommand command = SayCommand.builder()
                .channel("invalid")
                .message("测试")
                .rawCommand("say invalid 测试")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("频道"));
    }

    @Test
    @DisplayName("聊天指令验证 - 消息为空")
    void testSayCommand_EmptyMessage() {
        SayCommand command = SayCommand.builder()
                .channel("world")
                .message("")
                .rawCommand("say world ")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("聊天指令验证 - 消息过长")
    void testSayCommand_MessageTooLong() {
        String longMessage = "这是一条非常非常非常非常非常非常非常非常非常非常长的消息超过三十个字";
        SayCommand command = SayCommand.builder()
                .channel("world")
                .message(longMessage)
                .rawCommand("say world " + longMessage)
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("30"));
    }

    @Test
    @DisplayName("私聊指令验证 - 成功")
    void testSayToCommand_Valid() {
        SayToCommand command = SayToCommand.builder()
                .targetPlayer("张三")
                .message("你好")
                .rawCommand("say to 张三 你好")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("私聊指令验证 - 目标玩家为空")
    void testSayToCommand_EmptyTarget() {
        SayToCommand command = SayToCommand.builder()
                .targetPlayer("")
                .message("你好")
                .rawCommand("say to  你好")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    // ==================== 移动指令验证 ====================

    @Test
    @DisplayName("移动指令验证 - 成功")
    void testMoveCommand_Valid() {
        MoveCommand command = MoveCommand.builder()
                .targetX(5)
                .targetY(10)
                .rawCommand("move 5 10")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("移动指令验证 - 负坐标")
    void testMoveCommand_NegativeCoordinates() {
        MoveCommand command = MoveCommand.builder()
                .targetX(-1)
                .targetY(10)
                .rawCommand("move -1 10")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("负数"));
    }

    @Test
    @DisplayName("移动指令验证 - 零坐标有效")
    void testMoveCommand_ZeroCoordinates() {
        MoveCommand command = MoveCommand.builder()
                .targetX(0)
                .targetY(0)
                .rawCommand("move 0 0")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    // ==================== 交互指令验证 ====================

    @Test
    @DisplayName("交互指令验证 - 成功")
    void testInteractCommand_Valid() {
        InteractCommand command = InteractCommand.builder()
                .targetName("传送点")
                .option("传送")
                .rawCommand("interact 传送点 传送")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("交互指令验证 - 目标为空")
    void testInteractCommand_EmptyTarget() {
        InteractCommand command = InteractCommand.builder()
                .targetName("")
                .option("传送")
                .rawCommand("interact  传送")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("交互指令验证 - 选项为空")
    void testInteractCommand_EmptyOption() {
        InteractCommand command = InteractCommand.builder()
                .targetName("传送点")
                .option("")
                .rawCommand("interact 传送点 ")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    // ==================== 属性加点指令验证 ====================

    @Test
    @DisplayName("加点指令验证 - 力量")
    void testAttributeAddCommand_Strength() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("str")
                .amount(5)
                .rawCommand("attribute add str 5")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("加点指令验证 - 敏捷")
    void testAttributeAddCommand_Agility() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("agi")
                .amount(3)
                .rawCommand("attribute add agi 3")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("加点指令验证 - 智力")
    void testAttributeAddCommand_Intelligence() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("int")
                .amount(4)
                .rawCommand("attribute add int 4")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("加点指令验证 - 体力")
    void testAttributeAddCommand_Vitality() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("vit")
                .amount(2)
                .rawCommand("attribute add vit 2")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("加点指令验证 - 无效属性类型")
    void testAttributeAddCommand_InvalidType() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("invalid")
                .amount(5)
                .rawCommand("attribute add invalid 5")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("str") ||
                   result.getErrorMessage().contains("属性"));
    }

    @Test
    @DisplayName("加点指令验证 - 数量为零")
    void testAttributeAddCommand_ZeroAmount() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("str")
                .amount(0)
                .rawCommand("attribute add str 0")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("大于0"));
    }

    @Test
    @DisplayName("加点指令验证 - 负数量")
    void testAttributeAddCommand_NegativeAmount() {
        AttributeAddCommand command = AttributeAddCommand.builder()
                .attributeType("str")
                .amount(-5)
                .rawCommand("attribute add str -5")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    // ==================== 等待指令验证 ====================

    @Test
    @DisplayName("等待指令验证 - 成功")
    void testWaitCommand_Valid() {
        WaitCommand command = WaitCommand.builder()
                .seconds(30)
                .rawCommand("wait 30")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("等待指令验证 - 最小值")
    void testWaitCommand_MinValue() {
        WaitCommand command = WaitCommand.builder()
                .seconds(1)
                .rawCommand("wait 1")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("等待指令验证 - 最大值")
    void testWaitCommand_MaxValue() {
        WaitCommand command = WaitCommand.builder()
                .seconds(60)
                .rawCommand("wait 60")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("等待指令验证 - 超过最大值")
    void testWaitCommand_ExceedMax() {
        WaitCommand command = WaitCommand.builder()
                .seconds(61)
                .rawCommand("wait 61")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("1-60"));
    }

    @Test
    @DisplayName("等待指令验证 - 小于最小值")
    void testWaitCommand_BelowMin() {
        WaitCommand command = WaitCommand.builder()
                .seconds(0)
                .rawCommand("wait 0")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    // ==================== 战斗指令验证 ====================

    @Test
    @DisplayName("释放技能指令验证 - 成功")
    void testCastCommand_Valid() {
        CastCommand command = CastCommand.builder()
                .skillName("火球术")
                .rawCommand("cast 火球术")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("释放技能指令验证 - 技能名为空")
    void testCastCommand_EmptySkillName() {
        CastCommand command = CastCommand.builder()
                .skillName("")
                .rawCommand("cast ")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("释放指向技能指令验证 - 成功")
    void testCastTargetCommand_Valid() {
        CastTargetCommand command = CastTargetCommand.builder()
                .skillName("火球术")
                .targetName("哥布林")
                .rawCommand("cast 火球术 哥布林")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("释放指向技能指令验证 - 目标为空")
    void testCastTargetCommand_EmptyTarget() {
        CastTargetCommand command = CastTargetCommand.builder()
                .skillName("火球术")
                .targetName("")
                .rawCommand("cast 火球术 ")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    // ==================== 交易指令验证 ====================

    @Test
    @DisplayName("交易金额指令验证 - 成功")
    void testTradeMoneyCommand_Valid() {
        TradeMoneyCommand command = TradeMoneyCommand.builder()
                .amount(100)
                .rawCommand("trade money 100")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("交易金额指令验证 - 零金额")
    void testTradeMoneyCommand_ZeroAmount() {
        TradeMoneyCommand command = TradeMoneyCommand.builder()
                .amount(0)
                .rawCommand("trade money 0")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid()); // 零金额是允许的
    }

    @Test
    @DisplayName("交易金额指令验证 - 负金额")
    void testTradeMoneyCommand_NegativeAmount() {
        TradeMoneyCommand command = TradeMoneyCommand.builder()
                .amount(-100)
                .rawCommand("trade money -100")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("负数"));
    }

    // ==================== 物品相关指令验证 ====================

    @Test
    @DisplayName("使用物品指令验证 - 成功")
    void testUseItemCommand_Valid() {
        UseItemCommand command = UseItemCommand.builder()
                .itemName("生命药剂")
                .rawCommand("use 生命药剂")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("使用物品指令验证 - 物品名为空")
    void testUseItemCommand_EmptyItemName() {
        UseItemCommand command = UseItemCommand.builder()
                .itemName("")
                .rawCommand("use ")
                .build();

        Command.ValidationResult result = command.validate();
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("装备指令验证 - 成功")
    void testEquipCommand_Valid() {
        EquipCommand command = EquipCommand.builder()
                .itemName("铁剑#1")
                .rawCommand("equip 铁剑#1")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    // ==================== 简单指令验证 ====================

    @Test
    @DisplayName("查看自身指令验证")
    void testInspectSelfCommand_Valid() {
        InspectSelfCommand command = InspectSelfCommand.builder()
                .rawCommand("inspect self")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("离队指令验证")
    void testPartyLeaveCommand_Valid() {
        PartyLeaveCommand command = PartyLeaveCommand.builder()
                .rawCommand("party leave")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("解散队伍指令验证")
    void testPartyEndCommand_Valid() {
        PartyEndCommand command = PartyEndCommand.builder()
                .rawCommand("party end")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("下线指令验证")
    void testLeaveCommand_Valid() {
        LeaveCommand command = LeaveCommand.builder()
                .rawCommand("leave")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("战斗等待指令验证")
    void testWaitCombatCommand_Valid() {
        WaitCombatCommand command = WaitCombatCommand.builder()
                .rawCommand("wait")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("退出战斗指令验证")
    void testEndCombatCommand_Valid() {
        EndCombatCommand command = EndCombatCommand.builder()
                .rawCommand("end")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("锁定交易指令验证")
    void testTradeLockCommand_Valid() {
        TradeLockCommand command = TradeLockCommand.builder()
                .rawCommand("trade lock")
                .build();

        Command.ValidationResult result = command.validate();
        assertTrue(result.isValid());
    }
}
