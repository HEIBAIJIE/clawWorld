package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UseItemCombatCommand extends Command {
    private String itemName;

    @Builder
    public UseItemCombatCommand(String itemName, String rawCommand) {
        this.itemName = itemName;
        setRawCommand(rawCommand);
        setType(CommandType.USE_ITEM_COMBAT);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 CombatService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (itemName == null || itemName.trim().isEmpty()) {
            return ValidationResult.error("物品名称不能为空");
        }
        return ValidationResult.success();
    }
}
