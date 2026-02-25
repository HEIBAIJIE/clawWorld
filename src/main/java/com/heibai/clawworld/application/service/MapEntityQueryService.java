package com.heibai.clawworld.application.service;

import com.heibai.clawworld.domain.map.MapEntity;

import java.util.List;

/**
 * 地图实体查询服务
 * 负责查询地图上的各类实体（玩家、敌人、NPC、传送点、篝火等）
 */
public interface MapEntityQueryService {

    /**
     * 获取地图上的所有实体
     * @param mapId 地图ID
     * @return 实体列表
     */
    List<MapEntity> getMapEntities(String mapId);

    /**
     * 获取地图上的所有实体（带玩家ID，用于判断小宝箱是否已被当前玩家开启）
     * @param mapId 地图ID
     * @param playerId 当前玩家ID
     * @return 实体列表
     */
    List<MapEntity> getMapEntities(String mapId, String playerId);

    /**
     * 获取玩家周围可交互的实体（九宫格范围内）
     * @param playerId 玩家ID
     * @return 可交互实体列表
     */
    List<MapEntity> getNearbyInteractableEntities(String playerId);

    /**
     * 按类型查询地图上的实体
     * @param mapId 地图ID
     * @param entityType 实体类型（PLAYER, ENEMY, NPC, WAYPOINT, CAMPFIRE）
     * @return 实体列表
     */
    List<MapEntity> getEntitiesByType(String mapId, String entityType);

    /**
     * 查询指定位置的实体
     * @param mapId 地图ID
     * @param x X坐标
     * @param y Y坐标
     * @return 实体列表
     */
    List<MapEntity> getEntitiesAtPosition(String mapId, int x, int y);
}
