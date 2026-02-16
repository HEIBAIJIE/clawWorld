package com.heibai.clawworld.service.game;

import com.heibai.clawworld.domain.map.MapEntity;

import java.util.List;

/**
 * 地图实体管理服务
 * 负责管理地图上的实体、移动、交互等
 */
public interface MapEntityService {

    /**
     * 查看角色信息
     * @param playerId 查看者玩家ID
     * @param characterName 目标角色名称
     * @return 角色信息
     */
    EntityInfo inspectCharacter(String playerId, String characterName);

    /**
     * 移动玩家到指定位置
     * 根据设计文档：支持自动寻路，以每0.5秒1格的速度移动
     * @param playerId 玩家ID
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @return 移动结果
     */
    MoveResult movePlayer(String playerId, int targetX, int targetY);

    /**
     * 与地图实体交互
     * @param playerId 玩家ID
     * @param targetName 目标实体名称
     * @param option 交互选项
     * @return 交互结果
     */
    InteractionResult interact(String playerId, String targetName, String option);

    /**
     * 获取玩家周围可交互的实体
     * @param playerId 玩家ID
     * @return 可交互实体列表
     */
    List<MapEntity> getNearbyInteractableEntities(String playerId);

    /**
     * 获取地图上的所有实体
     * @param mapId 地图ID
     * @return 实体列表
     */
    List<MapEntity> getMapEntities(String mapId);

    /**
     * 实体信息
     */
    class EntityInfo {
        private boolean success;
        private String message;
        private String entityName;
        private String entityType;
        private Object attributes;

        public static EntityInfo success(String entityName, String entityType, Object attributes) {
            EntityInfo info = new EntityInfo();
            info.success = true;
            info.entityName = entityName;
            info.entityType = entityType;
            info.attributes = attributes;
            return info;
        }

        public static EntityInfo error(String message) {
            EntityInfo info = new EntityInfo();
            info.success = false;
            info.message = message;
            return info;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getEntityName() {
            return entityName;
        }

        public String getEntityType() {
            return entityType;
        }

        public Object getAttributes() {
            return attributes;
        }
    }

    /**
     * 移动结果
     */
    class MoveResult {
        private boolean success;
        private String message;
        private int currentX;
        private int currentY;
        private boolean isMoving;

        public static MoveResult success(int currentX, int currentY, String message) {
            MoveResult result = new MoveResult();
            result.success = true;
            result.currentX = currentX;
            result.currentY = currentY;
            result.message = message;
            return result;
        }

        public static MoveResult moving(int currentX, int currentY) {
            MoveResult result = new MoveResult();
            result.success = true;
            result.currentX = currentX;
            result.currentY = currentY;
            result.isMoving = true;
            result.message = "正在移动中...";
            return result;
        }

        public static MoveResult error(String message) {
            MoveResult result = new MoveResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getCurrentX() {
            return currentX;
        }

        public int getCurrentY() {
            return currentY;
        }

        public boolean isMoving() {
            return isMoving;
        }
    }

    /**
     * 交互结果
     */
    class InteractionResult {
        private boolean success;
        private String message;
        private boolean windowChanged;
        private String newWindowId;
        private String newWindowType;

        public static InteractionResult success(String message) {
            InteractionResult result = new InteractionResult();
            result.success = true;
            result.message = message;
            return result;
        }

        public static InteractionResult successWithWindowChange(String message, String newWindowId, String newWindowType) {
            InteractionResult result = new InteractionResult();
            result.success = true;
            result.message = message;
            result.windowChanged = true;
            result.newWindowId = newWindowId;
            result.newWindowType = newWindowType;
            return result;
        }

        public static InteractionResult error(String message) {
            InteractionResult result = new InteractionResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isWindowChanged() {
            return windowChanged;
        }

        public String getNewWindowId() {
            return newWindowId;
        }

        public String getNewWindowType() {
            return newWindowType;
        }
    }
}
