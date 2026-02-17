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
public class TradeUnlockCommand extends Command {

    @Builder
    public TradeUnlockCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.TRADE_UNLOCK);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String tradeId = context.getWindowId();

        TradeService.OperationResult result = CommandServiceLocator.getInstance().getTradeService()
                .unlockTrade(tradeId, context.getPlayerId());

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}
