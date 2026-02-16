package com.heibai.clawworld.command.impl;

import com.heibai.clawworld.command.Command;
import com.heibai.clawworld.command.CommandContext;
import com.heibai.clawworld.command.CommandResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttributeAddCommand extends Command {
    private String attributeType;
    private int amount;

    @Builder
    public AttributeAddCommand(String attributeType, int amount, String rawCommand) {
        this.attributeType = attributeType;
        this.amount = amount;
        setRawCommand(rawCommand);
        setType(CommandType.ATTRIBUTE_ADD);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        throw new UnsupportedOperationException("需要注入 PlayerSessionService 来执行此指令");
    }

    @Override
    public ValidationResult validate() {
        if (attributeType == null || attributeType.trim().isEmpty()) {
            return ValidationResult.error("属性类型不能为空");
        }
        if (!attributeType.equals("str") && !attributeType.equals("agi") &&
            !attributeType.equals("int") && !attributeType.equals("vit")) {
            return ValidationResult.error("属性类型必须是 str, agi, int 或 vit");
        }
        if (amount <= 0) {
            return ValidationResult.error("属性点数必须大于0");
        }
        return ValidationResult.success();
    }
}
