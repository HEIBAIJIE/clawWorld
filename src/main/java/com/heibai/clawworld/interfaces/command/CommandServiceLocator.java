package com.heibai.clawworld.interfaces.command;

import com.heibai.clawworld.application.service.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * 命令服务定位器
 * 为Command类提供访问服务的能力
 */
@Component
public class CommandServiceLocator {

    private static CommandServiceLocator instance;

    @Getter
    private final PlayerSessionService playerSessionService;
    @Getter
    private final MapEntityService mapEntityService;
    @Getter
    private final PartyService partyService;
    @Getter
    private final CombatService combatService;
    @Getter
    private final TradeService tradeService;
    @Getter
    private final ChatService chatService;
    @Getter
    private final ShopService shopService;

    public CommandServiceLocator(
            PlayerSessionService playerSessionService,
            MapEntityService mapEntityService,
            PartyService partyService,
            CombatService combatService,
            TradeService tradeService,
            ChatService chatService,
            ShopService shopService) {
        this.playerSessionService = playerSessionService;
        this.mapEntityService = mapEntityService;
        this.partyService = partyService;
        this.combatService = combatService;
        this.tradeService = tradeService;
        this.chatService = chatService;
        this.shopService = shopService;

        // 设置单例实例
        CommandServiceLocator.instance = this;
    }

    /**
     * 获取服务定位器实例
     */
    public static CommandServiceLocator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CommandServiceLocator 尚未初始化");
        }
        return instance;
    }
}
