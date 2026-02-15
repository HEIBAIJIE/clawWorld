package com.heibai.clawworld.domain.map;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 传送点领域对象
 * 根据设计文档：传送点是一种特殊的地图实体，可交互
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Waypoint extends MapEntity {
    private String targetMapId;
    private int targetX;
    private int targetY;

    @Override
    public boolean isPassable() {
        return true; // 传送点可通过
    }

    @Override
    public boolean isInteractable() {
        return true; // 传送点可交互
    }

    @Override
    public String getEntityType() {
        return "WAYPOINT";
    }
}
