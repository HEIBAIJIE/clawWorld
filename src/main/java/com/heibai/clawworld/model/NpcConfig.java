package com.heibai.clawworld.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "npc_configs")
public class NpcConfig {
    @Id
    private String id;
    private String name;
    private String description;
    private boolean hasShop;
    private boolean hasDialogue;
    private List<String> dialogues;
    private List<ShopItem> shopItems;
    private int shopGold;
    private int shopRefreshSeconds;

    @Data
    public static class ShopItem {
        private String itemId;
        private int quantity;
        private int price;
    }
}
