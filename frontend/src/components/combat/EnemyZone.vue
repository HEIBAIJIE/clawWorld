<template>
  <div class="enemy-zone">
    <div class="zone-title">敌方</div>
    <div class="factions-container">
      <div
        v-for="(enemies, factionId) in combatStore.enemyFactions"
        :key="factionId"
        class="faction-group"
      >
        <div class="faction-label">{{ getFactionLabel(factionId) }}</div>
        <div class="characters-row">
          <CombatCharacterCard
            v-for="enemy in enemies"
            :key="enemy.name"
            :character="enemy"
            :is-enemy="true"
            @select="handleSelectTarget"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useCombatStore } from '../../stores/combatStore'
import { useCommand } from '../../composables/useCommand'
import CombatCharacterCard from './CombatCharacterCard.vue'

const combatStore = useCombatStore()
const { sendCommand } = useCommand()

function getFactionLabel(factionId) {
  // 简化阵营显示
  if (factionId.startsWith('enemy_')) {
    return factionId.replace('enemy_', '').replace(/_/g, ' ')
  }
  if (factionId.startsWith('PARTY_')) {
    return '敌方队伍'
  }
  if (factionId.startsWith('PLAYER_')) {
    return '敌方玩家'
  }
  // 新格式：玩家名的队伍
  if (factionId.endsWith('的队伍')) {
    return factionId
  }
  return factionId
}

function handleSelectTarget(character) {
  if (!combatStore.pendingSkill) return

  const skillName = combatStore.pendingSkill.name
  sendCommand(`cast ${skillName} ${character.name}`)
  combatStore.exitTargetSelection()
}
</script>

<style scoped>
.enemy-zone {
  padding: 12px;
  background: rgba(244, 67, 54, 0.05);
  border-bottom: 1px solid var(--border-color);
}

.zone-title {
  font-size: 12px;
  color: var(--entity-enemy);
  margin-bottom: 8px;
  font-weight: 500;
}

.factions-container {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.faction-group {
  flex: 1;
  min-width: 200px;
}

.faction-label {
  font-size: 10px;
  color: var(--text-muted);
  margin-bottom: 6px;
  text-transform: capitalize;
}

.characters-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
