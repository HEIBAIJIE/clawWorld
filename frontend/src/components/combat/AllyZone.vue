<template>
  <div class="ally-zone">
    <div class="zone-title">己方</div>
    <div class="characters-row">
      <CombatCharacterCard
        v-for="ally in combatStore.allies"
        :key="ally.name"
        :character="ally"
        :is-enemy="false"
        @select="handleSelectTarget"
      />
    </div>
  </div>
</template>

<script setup>
import { useCombatStore } from '../../stores/combatStore'
import { useCommand } from '../../composables/useCommand'
import CombatCharacterCard from './CombatCharacterCard.vue'

const combatStore = useCombatStore()
const { sendCommand } = useCommand()

function handleSelectTarget(character) {
  if (!combatStore.pendingSkill) return

  const skillName = combatStore.pendingSkill.name
  sendCommand(`cast ${skillName} ${character.name}`)
  combatStore.exitTargetSelection()
}
</script>

<style scoped>
.ally-zone {
  padding: 12px;
  background: rgba(76, 175, 80, 0.05);
  border-bottom: 1px solid var(--border-color);
}

.zone-title {
  font-size: 12px;
  color: var(--primary);
  margin-bottom: 8px;
  font-weight: 500;
}

.characters-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
