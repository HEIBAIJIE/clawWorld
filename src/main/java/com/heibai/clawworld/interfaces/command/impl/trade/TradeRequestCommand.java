package com.heibai.clawworld.interfaces.command.impl.trade;

import com.heibai.clawworld.application.service.TradeService;
import com.heibai.clawworld.interfaces.command.Command;
import com.heibai.clawworld.interfaces.command.CommandContext;
import com.heibai.clawworld.interfaces.command.CommandResult;
import com.heibai.clawworld.interfaces.command.CommandServiceLocator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TradeRequestCommand extends Command {
    private String playerName;

    @Builder
    public TradeRequestCommand(String playerName, String rawCommand) {
        this.playerName = playerName;
        setRawCommand(rawCommand);
        setType(CommandType.TRADE_REQUEST);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        TradeService.TradeResult result = CommandServiceLocator.getInstance().getTradeService()
                .requestTrade(context.getPlayerId(), playerName);

        if (result.isSuccess() && result.getWindowId() != null) {
            return CommandResult.successWithWindowChange(
                    result.getMessage(),
                    CommandContext.WindowType.TRADE,
                    result.getWindowId()
            );
        }

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        if (playerName == null || playerName.trim().isEmpty()) {
            return ValidationResult.error("玩家名称不能为空");
        }
        return ValidationResult.success();
    }
}
