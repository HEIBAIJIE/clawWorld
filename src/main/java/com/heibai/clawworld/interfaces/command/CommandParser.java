package com.heibai.clawworld.interfaces.command;

import com.heibai.clawworld.interfaces.command.impl.combat.*;
import com.heibai.clawworld.interfaces.command.impl.map.*;
import com.heibai.clawworld.interfaces.command.impl.party.*;
import com.heibai.clawworld.interfaces.command.impl.map.AttributeAddCommand;
import com.heibai.clawworld.interfaces.command.impl.map.EquipCommand;
import com.heibai.clawworld.interfaces.command.impl.map.UnequipCommand;
import com.heibai.clawworld.interfaces.command.impl.shop.ShopBuyCommand;
import com.heibai.clawworld.interfaces.command.impl.shop.ShopLeaveCommand;
import com.heibai.clawworld.interfaces.command.impl.shop.ShopSellCommand;
import com.heibai.clawworld.interfaces.command.impl.trade.*;
import org.springframework.stereotype.Component;

/**
 * 指令解析器
 * 根据设计文档第三章第5节：指令遵循既定的语法，类似shell
 */
@Component
public class CommandParser {

    /**
     * 解析指令字符串
     * @param commandStr 指令字符串
     * @param windowType 当前窗口类型
     * @return 解析后的指令对象
     * @throws CommandParseException 解析失败时抛出
     */
    public Command parse(String commandStr, CommandContext.WindowType windowType) throws CommandParseException {
        if (commandStr == null || commandStr.trim().isEmpty()) {
            throw new CommandParseException("指令不能为空");
        }

        String trimmed = commandStr.trim();
        String[] parts = parseCommandParts(trimmed);

        if (parts.length == 0) {
            throw new CommandParseException("无效的指令格式");
        }

        String commandName = parts[0].toLowerCase();

        try {
            switch (windowType) {
                case REGISTER:
                    return parseRegisterCommand(commandName, parts, trimmed);
                case MAP:
                    return parseMapCommand(commandName, parts, trimmed);
                case COMBAT:
                    return parseCombatCommand(commandName, parts, trimmed);
                case TRADE:
                    return parseTradeCommand(commandName, parts, trimmed);
                case SHOP:
                    return parseShopCommand(commandName, parts, trimmed);
                default:
                    throw new CommandParseException("未知的窗口类型: " + windowType);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new CommandParseException("指令参数不足");
        }
    }

    /**
     * 解析注册窗口指令
     */
    private Command parseRegisterCommand(String commandName, String[] parts, String rawCommand) throws CommandParseException {
        if ("register".equals(commandName)) {
            if (parts.length < 3) {
                throw new CommandParseException("register 指令需要2个参数: register [role_name] [player_name]");
            }
            return RegisterCommand.builder()
                    .roleName(parts[1])
                    .playerName(parts[2])
                    .rawCommand(rawCommand)
                    .build();
        }
        throw new CommandParseException("注册窗口不支持的指令: " + commandName);
    }

    /**
     * 解析地图窗口指令
     */
    private Command parseMapCommand(String commandName, String[] parts, String rawCommand) throws CommandParseException {
        switch (commandName) {
            case "inspect":
                if (parts.length < 2) {
                    throw new CommandParseException("inspect 指令需要1个参数");
                }
                if ("self".equals(parts[1])) {
                    return InspectSelfCommand.builder()
                            .rawCommand(rawCommand)
                            .build();
                } else {
                    // inspect [物品名称] - 查看物品详情
                    String inspectItemName = extractItemName(rawCommand, "inspect");
                    return InspectItemCommand.builder()
                            .itemName(inspectItemName)
                            .rawCommand(rawCommand)
                            .build();
                }

            case "say":
                if (parts.length < 2) {
                    throw new CommandParseException("say 指令需要至少1个参数");
                }
                if ("to".equals(parts[1])) {
                    // say to [player_name] [message]
                    if (parts.length < 4) {
                        throw new CommandParseException("say to 指令需要2个参数: say to [player_name] [message]");
                    }
                    String targetPlayer = parts[2];
                    String message = extractMessage(rawCommand, "say to " + targetPlayer);
                    return SayToCommand.builder()
                            .targetPlayer(targetPlayer)
                            .message(message)
                            .rawCommand(rawCommand)
                            .build();
                } else {
                    // say [channel] [message]
                    String channel = parts[1];
                    String message = extractMessage(rawCommand, "say " + channel);
                    return SayCommand.builder()
                            .channel(channel)
                            .message(message)
                            .rawCommand(rawCommand)
                            .build();
                }

            case "interact":
                if (parts.length < 3) {
                    throw new CommandParseException("interact 指令需要2个参数: interact [target_name] [option]");
                }
                return InteractCommand.builder()
                        .targetName(parts[1])
                        .option(parts[2])
                        .rawCommand(rawCommand)
                        .build();

            case "move":
                if (parts.length < 3) {
                    throw new CommandParseException("move 指令需要2个参数: move [x] [y]");
                }
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    return MoveCommand.builder()
                            .targetX(x)
                            .targetY(y)
                            .rawCommand(rawCommand)
                            .build();
                } catch (NumberFormatException e) {
                    throw new CommandParseException("move 指令的坐标必须是整数");
                }

            case "use":
                if (parts.length < 2) {
                    throw new CommandParseException("use 指令需要1个参数: use [item_name]");
                }
                String itemName = extractItemName(rawCommand, "use");
                return UseItemCommand.builder()
                        .itemName(itemName)
                        .rawCommand(rawCommand)
                        .build();

            case "equip":
                if (parts.length < 2) {
                    throw new CommandParseException("equip 指令需要1个参数: equip [item_name]");
                }
                String equipName = extractItemName(rawCommand, "equip");
                return EquipCommand.builder()
                        .itemName(equipName)
                        .rawCommand(rawCommand)
                        .build();

            case "unequip":
                if (parts.length < 2) {
                    throw new CommandParseException("unequip 指令需要1个参数: unequip [slot_name]");
                }
                String slotName = extractItemName(rawCommand, "unequip");
                return UnequipCommand.builder()
                        .slotName(slotName)
                        .rawCommand(rawCommand)
                        .build();

            case "attribute":
                if (parts.length < 4 || !"add".equals(parts[1])) {
                    throw new CommandParseException("attribute 指令格式: attribute add [str/agi/int/vit] [amount]");
                }
                try {
                    String attributeType = parts[2];
                    int amount = Integer.parseInt(parts[3]);
                    return AttributeAddCommand.builder()
                            .attributeType(attributeType)
                            .amount(amount)
                            .rawCommand(rawCommand)
                            .build();
                } catch (NumberFormatException e) {
                    throw new CommandParseException("attribute add 指令的数量必须是整数");
                }

            case "party":
                if (parts.length < 2) {
                    throw new CommandParseException("party 指令需要子命令");
                }
                String subCommand = parts[1];
                switch (subCommand) {
                    case "invite":
                        if (parts.length < 3) {
                            throw new CommandParseException("party invite 指令需要1个参数: party invite [player_name]");
                        }
                        return PartyInviteCommand.builder()
                                .playerName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "accept":
                        if (parts.length < 3) {
                            throw new CommandParseException("party accept 指令需要1个参数: party accept [player_name]");
                        }
                        return PartyAcceptInviteCommand.builder()
                                .inviterName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "reject":
                        if (parts.length < 3) {
                            throw new CommandParseException("party reject 指令需要1个参数: party reject [player_name]");
                        }
                        return PartyRejectInviteCommand.builder()
                                .inviterName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "request":
                        if (parts.length < 3) {
                            throw new CommandParseException("party request 指令需要1个参数: party request [player_name]");
                        }
                        return PartyRequestJoinCommand.builder()
                                .playerName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "acceptrequest":
                        if (parts.length < 3) {
                            throw new CommandParseException("party acceptrequest 指令需要1个参数: party acceptrequest [player_name]");
                        }
                        return PartyAcceptRequestCommand.builder()
                                .requesterName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "rejectrequest":
                        if (parts.length < 3) {
                            throw new CommandParseException("party rejectrequest 指令需要1个参数: party rejectrequest [player_name]");
                        }
                        return PartyRejectRequestCommand.builder()
                                .requesterName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "kick":
                        if (parts.length < 3) {
                            throw new CommandParseException("party kick 指令需要1个参数: party kick [player_name]");
                        }
                        return PartyKickCommand.builder()
                                .playerName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "end":
                        return PartyEndCommand.builder()
                                .rawCommand(rawCommand)
                                .build();
                    case "leave":
                        return PartyLeaveCommand.builder()
                                .rawCommand(rawCommand)
                                .build();
                    default:
                        throw new CommandParseException("未知的 party 子命令: " + subCommand);
                }

            case "trade":
                if (parts.length < 2) {
                    throw new CommandParseException("trade 指令需要子命令");
                }
                String tradeSubCommand = parts[1];
                switch (tradeSubCommand) {
                    case "request":
                        if (parts.length < 3) {
                            throw new CommandParseException("trade request 指令需要1个参数: trade request [player_name]");
                        }
                        return TradeRequestCommand.builder()
                                .playerName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "accept":
                        if (parts.length < 3) {
                            throw new CommandParseException("trade accept 指令需要1个参数: trade accept [player_name]");
                        }
                        return TradeAcceptRequestCommand.builder()
                                .requesterName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    case "reject":
                        if (parts.length < 3) {
                            throw new CommandParseException("trade reject 指令需要1个参数: trade reject [player_name]");
                        }
                        return TradeRejectRequestCommand.builder()
                                .requesterName(parts[2])
                                .rawCommand(rawCommand)
                                .build();
                    default:
                        throw new CommandParseException("未知的 trade 子命令: " + tradeSubCommand);
                }

            case "wait":
                if (parts.length < 2) {
                    throw new CommandParseException("wait 指令需要1个参数: wait [seconds]");
                }
                try {
                    int seconds = Integer.parseInt(parts[1]);
                    return WaitCommand.builder()
                            .seconds(seconds)
                            .rawCommand(rawCommand)
                            .build();
                } catch (NumberFormatException e) {
                    throw new CommandParseException("wait 指令的秒数必须是整数");
                }

            case "leave":
                return LeaveCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            default:
                throw new CommandParseException("地图窗口不支持的指令: " + commandName);
        }
    }

    /**
     * 解析战斗窗口指令
     */
    private Command parseCombatCommand(String commandName, String[] parts, String rawCommand) throws CommandParseException {
        switch (commandName) {
            case "cast":
                if (parts.length < 2) {
                    throw new CommandParseException("cast 指令需要至少1个参数");
                }
                if (parts.length == 2) {
                    // cast [skill_name] - 非指向技能
                    return CastCommand.builder()
                            .skillName(parts[1])
                            .rawCommand(rawCommand)
                            .build();
                } else {
                    // cast [skill_name] [target_name] - 指向技能
                    return CastTargetCommand.builder()
                            .skillName(parts[1])
                            .targetName(parts[2])
                            .rawCommand(rawCommand)
                            .build();
                }

            case "use":
                if (parts.length < 2) {
                    throw new CommandParseException("use 指令需要1个参数: use [item_name]");
                }
                String itemName = extractItemName(rawCommand, "use");
                return UseItemCombatCommand.builder()
                        .itemName(itemName)
                        .rawCommand(rawCommand)
                        .build();

            case "wait":
                return WaitCombatCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            case "end":
                return EndCombatCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            default:
                throw new CommandParseException("战斗窗口不支持的指令: " + commandName);
        }
    }

    /**
     * 解析交易窗口指令
     */
    private Command parseTradeCommand(String commandName, String[] parts, String rawCommand) throws CommandParseException {
        if (!"trade".equals(commandName)) {
            throw new CommandParseException("交易窗口只支持 trade 指令");
        }

        if (parts.length < 2) {
            throw new CommandParseException("trade 指令需要子命令");
        }

        String subCommand = parts[1];
        switch (subCommand) {
            case "add":
                if (parts.length < 3) {
                    throw new CommandParseException("trade add 指令需要1个参数: trade add [item_name]");
                }
                String addItemName = extractItemName(rawCommand, "trade add");
                return TradeAddCommand.builder()
                        .itemName(addItemName)
                        .rawCommand(rawCommand)
                        .build();

            case "remove":
                if (parts.length < 3) {
                    throw new CommandParseException("trade remove 指令需要1个参数: trade remove [item_name]");
                }
                String removeItemName = extractItemName(rawCommand, "trade remove");
                return TradeRemoveCommand.builder()
                        .itemName(removeItemName)
                        .rawCommand(rawCommand)
                        .build();

            case "money":
                if (parts.length < 3) {
                    throw new CommandParseException("trade money 指令需要1个参数: trade money [amount]");
                }
                try {
                    int amount = Integer.parseInt(parts[2]);
                    return TradeMoneyCommand.builder()
                            .amount(amount)
                            .rawCommand(rawCommand)
                            .build();
                } catch (NumberFormatException e) {
                    throw new CommandParseException("trade money 指令的金额必须是整数");
                }

            case "lock":
                return TradeLockCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            case "unlock":
                return TradeUnlockCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            case "confirm":
                return TradeConfirmCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            case "end":
                return TradeEndCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            case "wait":
                int waitSeconds = 1; // 默认等待1秒
                if (parts.length >= 3) {
                    try {
                        waitSeconds = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        throw new CommandParseException("trade wait 指令的秒数必须是整数");
                    }
                }
                return TradeWaitCommand.builder()
                        .seconds(waitSeconds)
                        .rawCommand(rawCommand)
                        .build();

            default:
                throw new CommandParseException("未知的 trade 子命令: " + subCommand);
        }
    }

    /**
     * 解析商店窗口指令
     */
    private Command parseShopCommand(String commandName, String[] parts, String rawCommand) throws CommandParseException {
        if (!"shop".equals(commandName)) {
            throw new CommandParseException("商店窗口只支持 shop 指令");
        }

        if (parts.length < 2) {
            throw new CommandParseException("shop 指令需要子命令");
        }

        String subCommand = parts[1];
        switch (subCommand) {
            case "buy":
                if (parts.length < 4) {
                    throw new CommandParseException("shop buy 指令需要2个参数: shop buy [item_name] [quantity]");
                }
                try {
                    String itemName = parts[2];
                    int quantity = Integer.parseInt(parts[3]);
                    return ShopBuyCommand.builder()
                            .itemName(itemName)
                            .quantity(quantity)
                            .rawCommand(rawCommand)
                            .build();
                } catch (NumberFormatException e) {
                    throw new CommandParseException("shop buy 指令的数量必须是整数");
                }

            case "sell":
                if (parts.length < 4) {
                    throw new CommandParseException("shop sell 指令需要2个参数: shop sell [item_name] [quantity]");
                }
                try {
                    String itemName = parts[2];
                    int quantity = Integer.parseInt(parts[3]);
                    return ShopSellCommand.builder()
                            .itemName(itemName)
                            .quantity(quantity)
                            .rawCommand(rawCommand)
                            .build();
                } catch (NumberFormatException e) {
                    throw new CommandParseException("shop sell 指令的数量必须是整数");
                }

            case "leave":
                return ShopLeaveCommand.builder()
                        .rawCommand(rawCommand)
                        .build();

            default:
                throw new CommandParseException("未知的 shop 子命令: " + subCommand);
        }
    }

    /**
     * 解析指令部分
     * 支持引号包裹的参数
     */
    private String[] parseCommandParts(String command) {
        // 简单实现：按空格分割，后续可以增强支持引号
        return command.split("\\s+");
    }

    /**
     * 提取消息内容（从指令中提取最后的消息部分）
     */
    private String extractMessage(String rawCommand, String prefix) {
        int startIndex = rawCommand.indexOf(prefix) + prefix.length();
        if (startIndex < rawCommand.length()) {
            return rawCommand.substring(startIndex).trim();
        }
        return "";
    }

    /**
     * 提取物品名称（支持带空格的物品名）
     * 同时兼容带槽位前缀的装备名称，如 "[头部]新手头盔#1" -> "新手头盔#1"
     */
    private String extractItemName(String rawCommand, String prefix) {
        int startIndex = rawCommand.indexOf(prefix) + prefix.length();
        if (startIndex < rawCommand.length()) {
            String itemName = rawCommand.substring(startIndex).trim();
            // 移除槽位前缀，如 "[头部]新手头盔#1" -> "新手头盔#1"
            if (itemName.startsWith("[") && itemName.contains("]")) {
                int bracketEnd = itemName.indexOf("]");
                itemName = itemName.substring(bracketEnd + 1);
            }
            return itemName;
        }
        return "";
    }

    /**
     * 指令解析异常
     */
    public static class CommandParseException extends Exception {
        public CommandParseException(String message) {
            super(message);
        }
    }
}
