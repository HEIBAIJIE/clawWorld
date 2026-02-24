package com.heibai.clawworld.application.service;

import java.util.List;

/**
 * 宝箱服务
 * 负责处理宝箱的开启和掉落
 */
public interface ChestService {

    /**
     * 打开宝箱
     * @param playerId 玩家ID
     * @param chestName 宝箱名称
     * @return 开启结果
     */
    OpenChestResult openChest(String playerId, String chestName);

    /**
     * 查看宝箱信息
     * @param playerId 玩家ID
     * @param chestName 宝箱名称
     * @return 宝箱信息
     */
    ChestInfo inspectChest(String playerId, String chestName);

    /**
     * 初始化地图上的宝箱实例
     * @param mapId 地图ID
     */
    void initializeChestsForMap(String mapId);

    /**
     * 开启宝箱结果
     */
    class OpenChestResult {
        private boolean success;
        private String message;
        private int goldObtained;
        private List<LootItem> itemsObtained;

        public static OpenChestResult success(String message, int goldObtained, List<LootItem> itemsObtained) {
            OpenChestResult result = new OpenChestResult();
            result.success = true;
            result.message = message;
            result.goldObtained = goldObtained;
            result.itemsObtained = itemsObtained;
            return result;
        }

        public static OpenChestResult error(String message) {
            OpenChestResult result = new OpenChestResult();
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

        public int getGoldObtained() {
            return goldObtained;
        }

        public List<LootItem> getItemsObtained() {
            return itemsObtained;
        }
    }

    /**
     * 掉落物品
     */
    class LootItem {
        private String itemId;
        private String itemName;
        private int quantity;
        private String rarity;

        public LootItem(String itemId, String itemName, int quantity, String rarity) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.rarity = rarity;
        }

        public String getItemId() {
            return itemId;
        }

        public String getItemName() {
            return itemName;
        }

        public int getQuantity() {
            return quantity;
        }

        public String getRarity() {
            return rarity;
        }
    }

    /**
     * 宝箱信息
     */
    class ChestInfo {
        private boolean success;
        private String message;
        private String chestName;
        private String chestType;
        private boolean canOpen;
        private int remainingRespawnSeconds;

        public static ChestInfo success(String chestName, String chestType, boolean canOpen, int remainingRespawnSeconds) {
            ChestInfo info = new ChestInfo();
            info.success = true;
            info.chestName = chestName;
            info.chestType = chestType;
            info.canOpen = canOpen;
            info.remainingRespawnSeconds = remainingRespawnSeconds;
            return info;
        }

        public static ChestInfo error(String message) {
            ChestInfo info = new ChestInfo();
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

        public String getChestName() {
            return chestName;
        }

        public String getChestType() {
            return chestType;
        }

        public boolean isCanOpen() {
            return canOpen;
        }

        public int getRemainingRespawnSeconds() {
            return remainingRespawnSeconds;
        }
    }
}
