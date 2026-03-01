package com.heibai.clawworld.interfaces.log;

import com.heibai.clawworld.application.service.CharacterInfoService;
import com.heibai.clawworld.application.service.ChatService;
import com.heibai.clawworld.application.service.MapEntityService;
import com.heibai.clawworld.application.service.PlayerSessionService;
import com.heibai.clawworld.domain.character.Player;
import com.heibai.clawworld.domain.chat.ChatMessage;
import com.heibai.clawworld.domain.map.GameMap;
import com.heibai.clawworld.domain.map.MapEntity;
import com.heibai.clawworld.infrastructure.factory.MapInitializationService;
import com.heibai.clawworld.infrastructure.persistence.entity.AccountEntity;
import com.heibai.clawworld.infrastructure.persistence.repository.AccountRepository;
import com.heibai.clawworld.interfaces.command.CommandContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 统一响应生成服务
 * 将指令执行结果转换为统一的日志格式
 */
@Service
@RequiredArgsConstructor
public class UnifiedResponseGenerator {

    private final AccountRepository accountRepository;
    private final PlayerSessionService playerSessionService;
    private final MapEntityService mapEntityService;
    private final ChatService chatService;
    private final MapInitializationService mapInitializationService;
    private final MapWindowLogGenerator mapWindowLogGenerator;
    private final StateLogGenerator stateLogGenerator;
    private final CombatWindowLogGenerator combatWindowLogGenerator;
    private final TradeWindowLogGenerator tradeWindowLogGenerator;
    private final ShopWindowLogGenerator shopWindowLogGenerator;
    private final com.heibai.clawworld.application.service.CombatService combatService;
    private final com.heibai.clawworld.application.service.TradeService tradeService;
    private final com.heibai.clawworld.application.service.ShopService shopService;
    private final CharacterInfoService characterInfoService;

    /**
     * 生成完整的响应（包含客户端指令日志 + 状态日志 + 可选的窗口日志）
     */
    public String generateResponse(String playerId, String command, String commandResult,
                                   CommandContext.WindowType currentWindowType,
                                   CommandContext.WindowType newWindowType) {
        return generateResponse(playerId, command, commandResult, currentWindowType, newWindowType, false);
    }

    /**
     * 生成完整的响应（包含客户端指令日志 + 状态日志 + 可选的窗口日志）
     * @param inventoryChanged 是否需要刷新背包
     */
    public String generateResponse(String playerId, String command, String commandResult,
                                   CommandContext.WindowType currentWindowType,
                                   CommandContext.WindowType newWindowType,
                                   boolean inventoryChanged) {
        GameLogBuilder builder = new GameLogBuilder();

        // 只有当 playerId 不为 null 时才查询账号信息
        // 避免查询 playerId=null 时返回多个未注册账号的问题
        CommandContext.WindowType actualCurrentWindowType = currentWindowType;
        AccountEntity account = null;
        if (playerId != null) {
            Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
            if (accountOpt.isPresent()) {
                account = accountOpt.get();

                // 检查玩家的实际窗口状态是否与玩家上次知道的窗口状态不一致
                // 这种情况发生在：其他玩家的操作导致当前玩家的窗口状态被改变
                // 例如：交易被接受、交易完成、战斗开始等
                String dbWindowType = account.getCurrentWindowType();
                String lastKnownWindowType = account.getLastKnownWindowType();
                if (dbWindowType != null && lastKnownWindowType != null && newWindowType == null) {
                    try {
                        CommandContext.WindowType dbWindowTypeEnum = CommandContext.WindowType.valueOf(dbWindowType);
                        CommandContext.WindowType lastKnownWindowTypeEnum = CommandContext.WindowType.valueOf(lastKnownWindowType);
                        if (dbWindowTypeEnum != lastKnownWindowTypeEnum) {
                            // 窗口被其他玩家的操作改变了，需要通知当前玩家
                            newWindowType = dbWindowTypeEnum;
                            actualCurrentWindowType = dbWindowTypeEnum;
                            // 更新 currentWindowType 参数，用于后面的窗口变化消息
                            currentWindowType = lastKnownWindowTypeEnum;
                        }
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的窗口类型
                    }
                }
            }
        }

        // 2. 根据窗口类型生成状态日志
        if (actualCurrentWindowType == CommandContext.WindowType.MAP) {
            // 地图窗口：生成环境变化和响应
            // 只有当 playerId 不为 null 时才调用，避免 findById(null) 异常
            if (playerId != null) {
                stateLogGenerator.generateMapStateLogs(builder, playerId, commandResult);
            } else {
                builder.addState("响应", commandResult);
            }
        } else if (actualCurrentWindowType == CommandContext.WindowType.COMBAT) {
            // 战斗窗口：生成战斗状态（增量日志）
            // 只有当 playerId 不为 null 时才调用，避免 findById(null) 异常
            if (playerId != null) {
                Player player = playerSessionService.getPlayerState(playerId);
                if (player != null && player.getCombatId() != null) {
                    com.heibai.clawworld.domain.combat.Combat combat = combatService.getCombatState(player.getCombatId());
                    if (combat != null) {
                        // 获取上次的日志序列号
                        int lastLogSequence = account != null && account.getLastCombatLogSequence() != null
                            ? account.getLastCombatLogSequence() : 0;
                        // 获取当前回合开始时间
                        long turnStartTime = combatService.getTurnStartTime(player.getCombatId());
                        // 生成增量日志并获取新的序列号
                        int newLogSequence = combatWindowLogGenerator.generateCombatStateLogs(
                            builder, combat, playerId, commandResult, lastLogSequence, turnStartTime);
                        // 保存新的序列号
                        if (account != null && newLogSequence > lastLogSequence) {
                            account.setLastCombatLogSequence(newLogSequence);
                            // 稍后统一保存
                        }
                    } else {
                        builder.addState("响应", commandResult);
                    }
                } else {
                    builder.addState("响应", commandResult);
                }
            } else {
                builder.addState("响应", commandResult);
            }
        } else if (actualCurrentWindowType == CommandContext.WindowType.TRADE) {
            // 交易窗口：生成交易状态
            // 只有当 playerId 不为 null 时才调用，避免 findById(null) 异常
            if (playerId != null) {
                Player player = playerSessionService.getPlayerState(playerId);
                if (player != null && player.getTradeId() != null) {
                    com.heibai.clawworld.domain.trade.Trade trade = tradeService.getTradeState(player.getTradeId());
                    if (trade != null) {
                        tradeWindowLogGenerator.generateTradeStateLogs(builder, trade, playerId, commandResult);
                    } else {
                        builder.addState("响应", commandResult);
                    }
                } else {
                    builder.addState("响应", commandResult);
                }
            } else {
                builder.addState("响应", commandResult);
            }
        } else if (actualCurrentWindowType == CommandContext.WindowType.SHOP) {
            // 商店窗口：生成商店状态
            // 只有当 playerId 不为 null 时才调用，避免 findById(null) 异常
            if (playerId != null) {
                Player player = playerSessionService.getPlayerState(playerId);
                if (player != null && player.getCurrentShopId() != null) {
                    com.heibai.clawworld.application.service.ShopService.ShopInfo shopInfo =
                        shopService.getShopInfo(player.getCurrentShopId());
                    if (shopInfo != null) {
                        shopWindowLogGenerator.generateShopStateLogs(builder, shopInfo, player, commandResult);
                    } else {
                        builder.addState("响应", commandResult);
                    }
                } else {
                    builder.addState("响应", commandResult);
                }
            } else {
                builder.addState("响应", commandResult);
            }
        } else {
            // 其他窗口：只返回响应
            builder.addState("响应", commandResult);
        }

        // 3. 如果窗口发生变化，添加窗口变化日志和新窗口内容
        // 注意：即使窗口类型相同（如传送时 MAP -> MAP），只要 newWindowType 不为 null，
        // 也需要刷新窗口内容，因为可能是地图变化了
        if (newWindowType != null) {
            if (newWindowType != currentWindowType) {
                String windowChangeMsg = String.format("你已经从%s切换到%s",
                    getWindowTypeName(currentWindowType),
                    getWindowTypeName(newWindowType));
                builder.addState("窗口变化", windowChangeMsg);
            }

            // 生成新窗口的内容
            // 只有当 playerId 不为 null 时才调用，避免 findById(null) 异常
            if (playerId != null) {
                generateNewWindowContent(builder, playerId, newWindowType);
            }
        }

        // 3.5 如果背包发生变化，发送背包窗口更新
        if (inventoryChanged && playerId != null && newWindowType == null) {
            Player player = playerSessionService.getPlayerState(playerId);
            if (player != null) {
                builder.addWindow("背包", "你的背包：\n" + characterInfoService.generateInventory(player));
            }
        }

        // 4. 更新玩家的 lastKnownWindowType 和 lastCombatLogSequence
        // 这样下次请求时可以检测到被动窗口变化
        if (account != null) {
            boolean needSave = false;
            String finalWindowType = newWindowType != null ? newWindowType.name() :
                (actualCurrentWindowType != null ? actualCurrentWindowType.name() : account.getCurrentWindowType());
            if (finalWindowType != null && !finalWindowType.equals(account.getLastKnownWindowType())) {
                account.setLastKnownWindowType(finalWindowType);
                needSave = true;
            }
            // 如果 lastCombatLogSequence 被更新了，也需要保存
            // 注意：在战斗中，每次指令执行后都需要保存新的日志序列号
            if (actualCurrentWindowType == CommandContext.WindowType.COMBAT) {
                needSave = true;
            }
            if (needSave) {
                accountRepository.save(account);
            }
        }

        return builder.build();
    }

    /**
     * 生成错误响应
     * @param playerId 玩家ID
     * @param errorMessage 错误消息
     * @param currentWindowType 命令执行时的窗口类型（可能与实际窗口类型不一致）
     */
    public String generateErrorResponse(String playerId, String errorMessage, CommandContext.WindowType currentWindowType) {
        GameLogBuilder builder = new GameLogBuilder();

        CommandContext.WindowType newWindowType = null;
        AccountEntity account = null;
        CommandContext.WindowType lastKnownWindowTypeEnum = null;

        // 只有当 playerId 不为 null 时才查询账号信息
        // 避免查询 playerId=null 时返回多个未注册账号的问题
        if (playerId != null) {
            Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
            if (accountOpt.isPresent()) {
                account = accountOpt.get();

                // 检查玩家的实际窗口状态是否与玩家上次知道的窗口状态不一致
                String dbWindowType = account.getCurrentWindowType();
                String lastKnownWindowType = account.getLastKnownWindowType();
                if (dbWindowType != null && lastKnownWindowType != null) {
                    try {
                        CommandContext.WindowType dbWindowTypeEnum = CommandContext.WindowType.valueOf(dbWindowType);
                        lastKnownWindowTypeEnum = CommandContext.WindowType.valueOf(lastKnownWindowType);
                        if (dbWindowTypeEnum != lastKnownWindowTypeEnum) {
                            // 窗口被其他玩家的操作改变了，需要通知当前玩家
                            newWindowType = dbWindowTypeEnum;
                        }
                    } catch (IllegalArgumentException e) {
                        // 忽略无效的窗口类型
                    }
                }
            }
        }

        builder.addState("响应", errorMessage);

        // 如果窗口发生变化，添加窗口变化日志和新窗口内容
        if (newWindowType != null) {
            String windowChangeMsg = String.format("你已经从%s切换到%s",
                getWindowTypeName(lastKnownWindowTypeEnum),
                getWindowTypeName(newWindowType));
            builder.addState("窗口变化", windowChangeMsg);

            // 生成新窗口的内容
            if (playerId != null) {
                generateNewWindowContent(builder, playerId, newWindowType);
            }
        }

        // 更新玩家的 lastKnownWindowType
        if (account != null && newWindowType != null) {
            account.setLastKnownWindowType(newWindowType.name());
            accountRepository.save(account);
        }

        return builder.build();
    }

    /**
     * 生成错误响应（不检查窗口变化，用于向后兼容）
     */
    public String generateErrorResponse(String playerId, String errorMessage) {
        return generateErrorResponse(playerId, errorMessage, null);
    }

    /**
     * 生成新窗口内容
     */
    private void generateNewWindowContent(GameLogBuilder builder, String playerId,
                                          CommandContext.WindowType windowType) {
        Player player = playerSessionService.getPlayerState(playerId);
        if (player == null) {
            return;
        }

        if (windowType == CommandContext.WindowType.MAP) {
            // 生成地图窗口内容
            GameMap map = mapInitializationService.getMap(player.getMapId());
            if (map != null) {
                // 使用 MapEntityService 获取所有实体（已包含玩家、敌人、NPC、传送点、篝火等）
                List<MapEntity> allEntities = mapEntityService.getMapEntities(player.getMapId(), playerId);

                List<ChatMessage> chatHistory = chatService.getChatHistory(playerId);
                mapWindowLogGenerator.generateMapWindowLogs(builder, player, map, allEntities, chatHistory);
            }
        } else if (windowType == CommandContext.WindowType.TRADE) {
            // 生成交易窗口内容
            if (player.getTradeId() != null) {
                com.heibai.clawworld.domain.trade.Trade trade = tradeService.getTradeState(player.getTradeId());
                if (trade != null) {
                    Player otherPlayer = null;
                    String otherPlayerId = trade.getInitiatorId().equals(playerId) ?
                        trade.getReceiverId() : trade.getInitiatorId();
                    if (otherPlayerId != null) {
                        otherPlayer = playerSessionService.getPlayerState(otherPlayerId);
                    }
                    tradeWindowLogGenerator.generateTradeWindowLogs(builder, trade, player, otherPlayer);
                }
            } else {
                builder.addWindow("交易窗口", "交易窗口已打开");
            }
        } else if (windowType == CommandContext.WindowType.COMBAT) {
            // 生成战斗窗口内容
            if (player.getCombatId() != null) {
                com.heibai.clawworld.domain.combat.Combat combat = combatService.getCombatState(player.getCombatId());
                if (combat != null) {
                    // 获取当前回合开始时间
                    long turnStartTime = combatService.getTurnStartTime(player.getCombatId());
                    combatWindowLogGenerator.generateCombatWindowLogs(builder, combat, playerId, turnStartTime);
                    // 进入战斗窗口时，重置日志序列号为0，这样第一次获取状态时会获取所有日志
                    // 但由于窗口内容已经显示了初始状态，所以设置为当前最大序列号
                    Optional<AccountEntity> accountOpt = accountRepository.findByPlayerId(playerId);
                    if (accountOpt.isPresent()) {
                        AccountEntity account = accountOpt.get();
                        // 获取当前最大日志序列号
                        int maxSequence = 0;
                        if (combat.getCombatLog() != null) {
                            for (String log : combat.getCombatLog()) {
                                int seq = parseLogSequence(log);
                                if (seq > maxSequence) {
                                    maxSequence = seq;
                                }
                            }
                        }
                        account.setLastCombatLogSequence(maxSequence);
                        accountRepository.save(account);
                    }
                }
            } else {
                builder.addWindow("战斗窗口", "战斗窗口已打开");
            }
        } else if (windowType == CommandContext.WindowType.SHOP) {
            // 生成商店窗口内容
            if (player.getCurrentShopId() != null) {
                com.heibai.clawworld.application.service.ShopService.ShopInfo shopInfo =
                    shopService.getShopInfo(player.getCurrentShopId());
                if (shopInfo != null) {
                    shopWindowLogGenerator.generateShopWindowLogs(builder, shopInfo, player);
                }
            } else {
                builder.addWindow("商店窗口", "商店窗口已打开");
            }
        }
    }

    /**
     * 解析日志序列号
     * 日志格式为 "[#序号] 内容"
     */
    private int parseLogSequence(String log) {
        if (log == null || !log.startsWith("[#")) {
            return 0;
        }
        int endIndex = log.indexOf(']');
        if (endIndex <= 2) {
            return 0;
        }
        try {
            return Integer.parseInt(log.substring(2, endIndex));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 获取窗口类型名称
     */
    private String getWindowTypeName(CommandContext.WindowType windowType) {
        if (windowType == null) {
            return "未知窗口";
        }
        return switch (windowType) {
            case MAP -> "地图窗口";
            case COMBAT -> "战斗窗口";
            case TRADE -> "交易窗口";
            case SHOP -> "商店窗口";
            case REGISTER -> "注册窗口";
        };
    }
}
