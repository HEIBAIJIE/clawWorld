package com.heibai.clawworld.domain.map;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.List;

/**
 * 传送点领域对象
 * 根据设计文档：传送点是一种特殊的地图实体，可交互
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Waypoint extends MapEntity {
    /**
     * 可以传送到的其他传送点ID列表
     */
    private List<String> connectedWaypointIds;

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

    @Override
    public List<String> getInteractionOptions() {
        return Arrays.asList("传送");
    }
}
