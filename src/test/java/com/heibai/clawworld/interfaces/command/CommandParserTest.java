package com.heibai.clawworld.interfaces.command;

import com.heibai.clawworld.interfaces.command.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 指令解析器单元测试
 */
@DisplayName("指令解析器测试")
class CommandParserTest {

    private CommandParser parser;

    @BeforeEach
    void setUp() {
        parser = new CommandParser();
    }

    // ==================== 注册窗口指令测试 ====================

    @Test
    @DisplayName("解析注册指令 - 成功")
    void testParseRegisterCommand_Success() throws CommandParser.CommandParseException {
        Command command = parser.parse("register 战士 张三", CommandContext.WindowType.REGISTER);

        assertNotNull(command);
        assertTrue(command instanceof RegisterCommand);
        assertEquals(Command.CommandType.REGISTER, command.getType());

        RegisterCommand registerCommand = (RegisterCommand) command;
        assertEquals("战士", registerCommand.getRoleName());
        assertEquals("张三", registerCommand.getPlayerName());
    }

    @Test
    @DisplayName("解析注册指令 - 参数不足")
    void testParseRegisterCommand_InsufficientArgs() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("register 战士", CommandContext.WindowType.REGISTER);
        });
    }

    @Test
    @DisplayName("注册窗口不支持其他指令")
    void testRegisterWindow_UnsupportedCommand() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("move 5 10", CommandContext.WindowType.REGISTER);
        });
    }

    // ==================== 地图窗口指令测试 ====================

    @Test
    @DisplayName("解析查看自身指令")
    void testParseInspectSelfCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("inspect self", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof InspectSelfCommand);
        assertEquals(Command.CommandType.INSPECT_SELF, command.getType());
    }

    @Test
    @DisplayName("解析查看角色指令")
    void testParseInspectCharacterCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("inspect 哥布林", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof InspectCharacterCommand);
        assertEquals(Command.CommandType.INSPECT_CHARACTER, command.getType());

        InspectCharacterCommand inspectCommand = (InspectCharacterCommand) command;
        assertEquals("哥布林", inspectCommand.getCharacterName());
    }

    @Test
    @DisplayName("解析公屏聊天指令 - 世界频道")
    void testParseSayCommand_World() throws CommandParser.CommandParseException {
        Command command = parser.parse("say world 大家好", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof SayCommand);

        SayCommand sayCommand = (SayCommand) command;
        assertEquals("world", sayCommand.getChannel());
        assertEquals("大家好", sayCommand.getMessage());
    }

    @Test
    @DisplayName("解析公屏聊天指令 - 地图频道")
    void testParseSayCommand_Map() throws CommandParser.CommandParseException {
        Command command = parser.parse("say map 这里有怪物", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof SayCommand);

        SayCommand sayCommand = (SayCommand) command;
        assertEquals("map", sayCommand.getChannel());
        assertEquals("这里有怪物", sayCommand.getMessage());
    }

    @Test
    @DisplayName("解析公屏聊天指令 - 队伍频道")
    void testParseSayCommand_Party() throws CommandParser.CommandParseException {
        Command command = parser.parse("say party 准备战斗", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof SayCommand);

        SayCommand sayCommand = (SayCommand) command;
        assertEquals("party", sayCommand.getChannel());
        assertEquals("准备战斗", sayCommand.getMessage());
    }

    @Test
    @DisplayName("解析私聊指令")
    void testParseSayToCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("say to 张三 你好吗", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof SayToCommand);

        SayToCommand sayToCommand = (SayToCommand) command;
        assertEquals("张三", sayToCommand.getTargetPlayer());
        assertEquals("你好吗", sayToCommand.getMessage());
    }

    @Test
    @DisplayName("解析交互指令")
    void testParseInteractCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("interact 传送点 传送", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof InteractCommand);

        InteractCommand interactCommand = (InteractCommand) command;
        assertEquals("传送点", interactCommand.getTargetName());
        assertEquals("传送", interactCommand.getOption());
    }

    @Test
    @DisplayName("解析移动指令")
    void testParseMoveCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("move 5 10", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof MoveCommand);

        MoveCommand moveCommand = (MoveCommand) command;
        assertEquals(5, moveCommand.getTargetX());
        assertEquals(10, moveCommand.getTargetY());
    }

    @Test
    @DisplayName("解析移动指令 - 坐标格式错误")
    void testParseMoveCommand_InvalidCoordinates() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("move abc def", CommandContext.WindowType.MAP);
        });
    }

    @Test
    @DisplayName("解析使用物品指令")
    void testParseUseItemCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("use 生命药剂", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof UseItemCommand);

        UseItemCommand useItemCommand = (UseItemCommand) command;
        assertEquals("生命药剂", useItemCommand.getItemName());
    }

    @Test
    @DisplayName("解析使用物品指令 - 带空格的物品名")
    void testParseUseItemCommand_WithSpaces() throws CommandParser.CommandParseException {
        Command command = parser.parse("use 高级生命药剂", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof UseItemCommand);

        UseItemCommand useItemCommand = (UseItemCommand) command;
        assertEquals("高级生命药剂", useItemCommand.getItemName());
    }

    @Test
    @DisplayName("解析装备指令")
    void testParseEquipCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("equip 铁剑#1", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof EquipCommand);

        EquipCommand equipCommand = (EquipCommand) command;
        assertEquals("铁剑#1", equipCommand.getItemName());
    }

    @Test
    @DisplayName("解析加点指令 - 力量")
    void testParseAttributeAddCommand_Strength() throws CommandParser.CommandParseException {
        Command command = parser.parse("attribute add str 5", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof AttributeAddCommand);

        AttributeAddCommand attrCommand = (AttributeAddCommand) command;
        assertEquals("str", attrCommand.getAttributeType());
        assertEquals(5, attrCommand.getAmount());
    }

    @Test
    @DisplayName("解析加点指令 - 敏捷")
    void testParseAttributeAddCommand_Agility() throws CommandParser.CommandParseException {
        Command command = parser.parse("attribute add agi 3", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof AttributeAddCommand);

        AttributeAddCommand attrCommand = (AttributeAddCommand) command;
        assertEquals("agi", attrCommand.getAttributeType());
        assertEquals(3, attrCommand.getAmount());
    }

    @Test
    @DisplayName("解析加点指令 - 智力")
    void testParseAttributeAddCommand_Intelligence() throws CommandParser.CommandParseException {
        Command command = parser.parse("attribute add int 4", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof AttributeAddCommand);

        AttributeAddCommand attrCommand = (AttributeAddCommand) command;
        assertEquals("int", attrCommand.getAttributeType());
        assertEquals(4, attrCommand.getAmount());
    }

    @Test
    @DisplayName("解析加点指令 - 体力")
    void testParseAttributeAddCommand_Vitality() throws CommandParser.CommandParseException {
        Command command = parser.parse("attribute add vit 2", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof AttributeAddCommand);

        AttributeAddCommand attrCommand = (AttributeAddCommand) command;
        assertEquals("vit", attrCommand.getAttributeType());
        assertEquals(2, attrCommand.getAmount());
    }

    @Test
    @DisplayName("解析踢人指令")
    void testParsePartyKickCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party kick 李四", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof PartyKickCommand);

        PartyKickCommand kickCommand = (PartyKickCommand) command;
        assertEquals("李四", kickCommand.getPlayerName());
    }

    @Test
    @DisplayName("解析解散队伍指令")
    void testParsePartyEndCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party end", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof PartyEndCommand);
        assertEquals(Command.CommandType.PARTY_END, command.getType());
    }

    @Test
    @DisplayName("解析离队指令")
    void testParsePartyLeaveCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party leave", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof PartyLeaveCommand);
        assertEquals(Command.CommandType.PARTY_LEAVE, command.getType());
    }

    @Test
    @DisplayName("解析等待指令")
    void testParseWaitCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("wait 10", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof WaitCommand);

        WaitCommand waitCommand = (WaitCommand) command;
        assertEquals(10, waitCommand.getSeconds());
    }

    @Test
    @DisplayName("解析下线指令")
    void testParseLeaveCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("leave", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof LeaveCommand);
        assertEquals(Command.CommandType.LEAVE, command.getType());
    }

    // ==================== 战斗窗口指令测试 ====================

    @Test
    @DisplayName("解析释放技能指令 - 非指向")
    void testParseCastCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("cast 群体治疗", CommandContext.WindowType.COMBAT);

        assertNotNull(command);
        assertTrue(command instanceof CastCommand);

        CastCommand castCommand = (CastCommand) command;
        assertEquals("群体治疗", castCommand.getSkillName());
    }

    @Test
    @DisplayName("解析释放技能指令 - 指向")
    void testParseCastTargetCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("cast 火球术 哥布林", CommandContext.WindowType.COMBAT);

        assertNotNull(command);
        assertTrue(command instanceof CastTargetCommand);

        CastTargetCommand castCommand = (CastTargetCommand) command;
        assertEquals("火球术", castCommand.getSkillName());
        assertEquals("哥布林", castCommand.getTargetName());
    }

    @Test
    @DisplayName("解析战斗中使用物品指令")
    void testParseUseItemCombatCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("use 生命药剂", CommandContext.WindowType.COMBAT);

        assertNotNull(command);
        assertTrue(command instanceof UseItemCombatCommand);

        UseItemCombatCommand useCommand = (UseItemCombatCommand) command;
        assertEquals("生命药剂", useCommand.getItemName());
    }

    @Test
    @DisplayName("解析战斗等待指令")
    void testParseWaitCombatCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("wait", CommandContext.WindowType.COMBAT);

        assertNotNull(command);
        assertTrue(command instanceof WaitCombatCommand);
        assertEquals(Command.CommandType.WAIT_COMBAT, command.getType());
    }

    @Test
    @DisplayName("解析退出战斗指令")
    void testParseEndCombatCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("end", CommandContext.WindowType.COMBAT);

        assertNotNull(command);
        assertTrue(command instanceof EndCombatCommand);
        assertEquals(Command.CommandType.END_COMBAT, command.getType());
    }

    // ==================== 交易窗口指令测试 ====================

    @Test
    @DisplayName("解析添加交易物品指令")
    void testParseTradeAddCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade add 铁剑#1", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeAddCommand);

        TradeAddCommand addCommand = (TradeAddCommand) command;
        assertEquals("铁剑#1", addCommand.getItemName());
    }

    @Test
    @DisplayName("解析移除交易物品指令")
    void testParseTradeRemoveCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade remove 铁剑#1", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeRemoveCommand);

        TradeRemoveCommand removeCommand = (TradeRemoveCommand) command;
        assertEquals("铁剑#1", removeCommand.getItemName());
    }

    @Test
    @DisplayName("解析设置交易金额指令")
    void testParseTradeMoneyCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade money 100", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeMoneyCommand);

        TradeMoneyCommand moneyCommand = (TradeMoneyCommand) command;
        assertEquals(100, moneyCommand.getAmount());
    }

    @Test
    @DisplayName("解析锁定交易指令")
    void testParseTradeLockCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade lock", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeLockCommand);
        assertEquals(Command.CommandType.TRADE_LOCK, command.getType());
    }

    @Test
    @DisplayName("解析解锁交易指令")
    void testParseTradeUnlockCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade unlock", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeUnlockCommand);
        assertEquals(Command.CommandType.TRADE_UNLOCK, command.getType());
    }

    @Test
    @DisplayName("解析确认交易指令")
    void testParseTradeConfirmCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade confirm", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeConfirmCommand);
        assertEquals(Command.CommandType.TRADE_CONFIRM, command.getType());
    }

    @Test
    @DisplayName("解析取消交易指令")
    void testParseTradeEndCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade end", CommandContext.WindowType.TRADE);

        assertNotNull(command);
        assertTrue(command instanceof TradeEndCommand);
        assertEquals(Command.CommandType.TRADE_END, command.getType());
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("空指令")
    void testParseEmptyCommand() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("", CommandContext.WindowType.MAP);
        });
    }

    @Test
    @DisplayName("null指令")
    void testParseNullCommand() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse(null, CommandContext.WindowType.MAP);
        });
    }

    @Test
    @DisplayName("只有空格的指令")
    void testParseWhitespaceCommand() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("   ", CommandContext.WindowType.MAP);
        });
    }

    @Test
    @DisplayName("未知指令")
    void testParseUnknownCommand() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("unknown command", CommandContext.WindowType.MAP);
        });
    }

    @Test
    @DisplayName("大小写不敏感")
    void testParseCaseInsensitive() throws CommandParser.CommandParseException {
        Command command1 = parser.parse("MOVE 5 10", CommandContext.WindowType.MAP);
        Command command2 = parser.parse("Move 5 10", CommandContext.WindowType.MAP);
        Command command3 = parser.parse("move 5 10", CommandContext.WindowType.MAP);

        assertTrue(command1 instanceof MoveCommand);
        assertTrue(command2 instanceof MoveCommand);
        assertTrue(command3 instanceof MoveCommand);
    }

    @Test
    @DisplayName("多余空格处理")
    void testParseExtraWhitespace() throws CommandParser.CommandParseException {
        Command command = parser.parse("  move   5   10  ", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertTrue(command instanceof MoveCommand);

        MoveCommand moveCommand = (MoveCommand) command;
        assertEquals(5, moveCommand.getTargetX());
        assertEquals(10, moveCommand.getTargetY());
    }

    // ==================== 组队命令测试 ====================

    @Test
    @DisplayName("解析邀请组队指令")
    void testParsePartyInviteCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party invite 张三", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.PARTY_INVITE, command.getType());
        assertTrue(command instanceof PartyInviteCommand);
        assertEquals("张三", ((PartyInviteCommand) command).getPlayerName());
    }

    @Test
    @DisplayName("解析接受组队邀请指令")
    void testParsePartyAcceptInviteCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party accept 李四", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.PARTY_ACCEPT_INVITE, command.getType());
        assertTrue(command instanceof PartyAcceptInviteCommand);
        assertEquals("李四", ((PartyAcceptInviteCommand) command).getInviterName());
    }

    @Test
    @DisplayName("解析拒绝组队邀请指令")
    void testParsePartyRejectInviteCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party reject 王五", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.PARTY_REJECT_INVITE, command.getType());
        assertTrue(command instanceof PartyRejectInviteCommand);
        assertEquals("王五", ((PartyRejectInviteCommand) command).getInviterName());
    }

    @Test
    @DisplayName("解析请求加入队伍指令")
    void testParsePartyRequestJoinCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party request 赵六", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.PARTY_REQUEST_JOIN, command.getType());
        assertTrue(command instanceof PartyRequestJoinCommand);
        assertEquals("赵六", ((PartyRequestJoinCommand) command).getPlayerName());
    }

    @Test
    @DisplayName("解析接受组队请求指令")
    void testParsePartyAcceptRequestCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party acceptrequest 孙七", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.PARTY_ACCEPT_REQUEST, command.getType());
        assertTrue(command instanceof PartyAcceptRequestCommand);
        assertEquals("孙七", ((PartyAcceptRequestCommand) command).getRequesterName());
    }

    @Test
    @DisplayName("解析拒绝组队请求指令")
    void testParsePartyRejectRequestCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("party rejectrequest 周八", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.PARTY_REJECT_REQUEST, command.getType());
        assertTrue(command instanceof PartyRejectRequestCommand);
        assertEquals("周八", ((PartyRejectRequestCommand) command).getRequesterName());
    }

    @Test
    @DisplayName("解析邀请组队指令 - 缺少玩家名")
    void testParsePartyInviteWithoutPlayerName() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("party invite", CommandContext.WindowType.MAP);
        });
    }

    // ==================== 交易请求命令测试 ====================

    @Test
    @DisplayName("解析发起交易请求指令")
    void testParseTradeRequestCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade request 吴九", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.TRADE_REQUEST, command.getType());
        assertTrue(command instanceof TradeRequestCommand);
        assertEquals("吴九", ((TradeRequestCommand) command).getPlayerName());
    }

    @Test
    @DisplayName("解析接受交易请求指令")
    void testParseTradeAcceptRequestCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade accept 郑十", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.TRADE_ACCEPT_REQUEST, command.getType());
        assertTrue(command instanceof TradeAcceptRequestCommand);
        assertEquals("郑十", ((TradeAcceptRequestCommand) command).getRequesterName());
    }

    @Test
    @DisplayName("解析拒绝交易请求指令")
    void testParseTradeRejectRequestCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("trade reject 冯十一", CommandContext.WindowType.MAP);

        assertNotNull(command);
        assertEquals(Command.CommandType.TRADE_REJECT_REQUEST, command.getType());
        assertTrue(command instanceof TradeRejectRequestCommand);
        assertEquals("冯十一", ((TradeRejectRequestCommand) command).getRequesterName());
    }

    // ==================== 商店命令测试 ====================

    @Test
    @DisplayName("解析商店购买指令")
    void testParseShopBuyCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("shop buy 生命药剂 5", CommandContext.WindowType.SHOP);

        assertNotNull(command);
        assertEquals(Command.CommandType.SHOP_BUY, command.getType());
        assertTrue(command instanceof ShopBuyCommand);
        assertEquals("生命药剂", ((ShopBuyCommand) command).getItemName());
        assertEquals(5, ((ShopBuyCommand) command).getQuantity());
    }

    @Test
    @DisplayName("解析商店出售指令")
    void testParseShopSellCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("shop sell 铁剑 1", CommandContext.WindowType.SHOP);

        assertNotNull(command);
        assertEquals(Command.CommandType.SHOP_SELL, command.getType());
        assertTrue(command instanceof ShopSellCommand);
        assertEquals("铁剑", ((ShopSellCommand) command).getItemName());
        assertEquals(1, ((ShopSellCommand) command).getQuantity());
    }

    @Test
    @DisplayName("解析离开商店指令")
    void testParseShopLeaveCommand() throws CommandParser.CommandParseException {
        Command command = parser.parse("shop leave", CommandContext.WindowType.SHOP);

        assertNotNull(command);
        assertEquals(Command.CommandType.SHOP_LEAVE, command.getType());
        assertTrue(command instanceof ShopLeaveCommand);
    }

    @Test
    @DisplayName("解析商店购买指令 - 缺少数量")
    void testParseShopBuyWithoutQuantity() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("shop buy 生命药剂", CommandContext.WindowType.SHOP);
        });
    }

    @Test
    @DisplayName("解析商店购买指令 - 数量格式错误")
    void testParseShopBuyWithInvalidQuantity() {
        assertThrows(CommandParser.CommandParseException.class, () -> {
            parser.parse("shop buy 生命药剂 abc", CommandContext.WindowType.SHOP);
        });
    }
}
