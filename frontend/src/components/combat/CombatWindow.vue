<template>
  <div class="combat-overlay" v-if="combatStore.isInCombat">
    <div class="combat-window sci-panel">
      <!-- 行动条区域 -->
      <ActionBarDisplay />

      <!-- 战斗主区域 -->
      <div class="combat-main">
        <!-- 敌方区 -->
        <EnemyZone />

        <!-- 己方区 -->
        <AllyZone />

        <!-- 行动区 -->
        <CombatActionPanel />
      </div>

      <!-- 战斗特效层 -->
      <CombatEffects />

      <!-- 战斗结算弹窗 -->
      <CombatResultModal v-if="combatStore.showResult" />
    </div>
  </div>
</template>

<script setup>
import { useCombatStore } from '../../stores/combatStore'
import ActionBarDisplay from './ActionBarDisplay.vue'
import EnemyZone from './EnemyZone.vue'
import AllyZone from './AllyZone.vue'
import CombatActionPanel from './CombatActionPanel.vue'
import CombatEffects from './CombatEffects.vue'
import CombatResultModal from './CombatResultModal.vue'

const combatStore = useCombatStore()
</script>

<style scoped>
.combat-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  z-index: 100;
}

.combat-window {
  width: 80%;
  height: 80%;
  display: flex;
  flex-direction: column;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
  overflow: hidden;
  position: relative;
}

.combat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
