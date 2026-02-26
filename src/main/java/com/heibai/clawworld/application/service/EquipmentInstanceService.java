package com.heibai.clawworld.application.service;

/**
 * 装备实例编号服务
 * 负责为每种装备生成唯一的实例编号
 */
public interface EquipmentInstanceService {
    /**
     * 获取下一个装备实例编号
     * @param equipmentId 装备配置ID
     * @return 下一个实例编号
     */
    Long getNextInstanceNumber(String equipmentId);
}
