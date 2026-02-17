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
public class TradeConfirmCommand extends Command {

    @Builder
    public TradeConfirmCommand(String rawCommand) {
        setRawCommand(rawCommand);
        setType(CommandType.TRADE_CONFIRM);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String tradeId = context.getWindowId();

        TradeService.OperationResult result = CommandServiceLocator.getInstance().getTradeService()
                .confirmTrade(tradeId, context.getPlayerId());

        if (result.isSuccess()) {
            if (result.isTradeCompleted()) {
                return CommandResult.successWithWindowChange(
                        result.getMessage(),
                        CommandContext.WindowType.MAP,
                        "交易已完成，返回地图"
                );
            } else {
                return CommandResult.success(result.getMessage());
            }
        } else {
            return CommandResult.error(result.getMessage());
        }
    }

    @Override
    public ValidationResult validate() {
        return ValidationResult.success();
    }
}
