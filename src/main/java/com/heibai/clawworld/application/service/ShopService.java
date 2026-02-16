package com.heibai.clawworld.application.service;

import lombok.Data;

/**
 * 商店服务接口
 */
public interface ShopService {

    /**
     * 从商店购买物品
     * @param playerId 玩家ID
     * @param shopId 商店ID（NPC名称）
     * @param itemName 物品名称
     * @param quantity 购买数量
     * @return 操作结果
     */
    OperationResult buyItem(String playerId, String shopId, String itemName, int quantity);

    /**
     * 向商店出售物品
     * @param playerId 玩家ID
     * @param shopId 商店ID（NPC名称）
     * @param itemName 物品名称
     * @param quantity 出售数量
     * @return 操作结果
     */
    OperationResult sellItem(String playerId, String shopId, String itemName, int quantity);

    /**
     * 获取商店信息
     * @param shopId 商店ID
     * @return 商店信息
     */
    ShopInfo getShopInfo(String shopId);

    /**
     * 操作结果
     */
    @Data
    class OperationResult {
        private boolean success;
        private String message;

        public static OperationResult success(String message) {
            OperationResult result = new OperationResult();
            result.setSuccess(true);
            result.setMessage(message);
            return result;
        }

        public static OperationResult error(String message) {
            OperationResult result = new OperationResult();
            result.setSuccess(false);
            result.setMessage(message);
            return result;
        }
    }

    /**
     * 商店信息
     */
    @Data
    class ShopInfo {
        private String shopId;
        private String npcName;
        private int gold;
        private double priceMultiplier;
        private java.util.List<ShopItemInfo> items;

        /**
         * 商店物品信息
         */
        @Data
        public static class ShopItemInfo {
            private String itemId;
            private String itemName;
            private int price;
            private int maxQuantity;
            private int currentQuantity;
        }
    }
}
