package com.heibai.clawworld.combat;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 参战方 - 代表战斗中的一个阵营
 */
@Data
public class CombatParty {
    private String factionId;
    private List<CombatCharacter> characters;

    public CombatParty(String factionId) {
        this.factionId = factionId;
        this.characters = new ArrayList<>();
    }

    public void addCharacter(CombatCharacter character) {
        characters.add(character);
    }

    public boolean hasAliveCharacters() {
        return characters.stream().anyMatch(CombatCharacter::isAlive);
    }

    public List<CombatCharacter> getAliveCharacters() {
        return characters.stream()
            .filter(CombatCharacter::isAlive)
            .toList();
    }
}
