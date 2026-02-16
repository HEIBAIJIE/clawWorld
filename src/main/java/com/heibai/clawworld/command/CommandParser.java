package com.heibai.clawworld.command;

import com.heibai.clawworld.command.impl.*;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    return InspectCharacterCommand.builder()
                            .characterName(parts[1])
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

            default:
                throw new CommandParseException("未知的 trade 子命令: " + subCommand);
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
     */
    private String extractItemName(String rawCommand, String prefix) {
        int startIndex = rawCommand.indexOf(prefix) + prefix.length();
        if (startIndex < rawCommand.length()) {
            return rawCommand.substring(startIndex).trim();
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
