package com.heibai.clawworld.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "item_configs")
public class ItemConfig {
    @Id
    private String id;
    private String name;
    private String description;
    private ItemType type;
    private int maxStack;
    private int buyPrice;
    private int sellPrice;
    private String effect;
    private Integer effectValue;

    public enum ItemType {
        CONSUMABLE,
        MATERIAL,
        QUEST,
        SKILL_BOOK,
        OTHER
    }
}
