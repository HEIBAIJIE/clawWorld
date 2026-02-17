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
public class TradeLockCommand extends Command {

    @Builder
    public TradeLockCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.TRADE_LOCK);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String tradeId = context.getWindowId();

        TradeService.OperationResult result = CommandServiceLocator.getInstance().getTradeService()
                .lockTrade(tradeId, context.getPlayerId());

        return result.isSuccess() ?
                CommandResult.success(result.getMessage()) :
                CommandResult.error(result.getMessage());
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}
