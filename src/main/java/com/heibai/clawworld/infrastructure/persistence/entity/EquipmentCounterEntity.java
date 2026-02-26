package com.heibai.clawworld.infrastructure.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 装备实例编号计数器实体
 * 用于为每种装备生成唯一的实例编号
 */
@Data
@Document(collection = "equipment_counters")
public class EquipmentCounterEntity {
    @Id
    private String equipmentId;  // 装备配置ID

    private Long currentNumber;  // 当前编号
}
