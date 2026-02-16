package com.heibai.clawworld.domain.item;

public enum Rarity {
    COMMON("普通"),
    EXCELLENT("优秀"),
    RARE("稀有"),
    EPIC("史诗"),
    LEGENDARY("传说"),
    MYTHIC("神话");

    private final String displayName;

    Rarity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
