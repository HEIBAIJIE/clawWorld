<template>
  <div class="action-bar-display">
    <div class="action-bar-title">行动条</div>
    <div class="action-bar-track">
      <div
        v-for="entry in combatStore.actionBar"
        :key="entry.name"
        class="action-bar-entry"
        :class="{
          current: entry.isCurrent,
          self: entry.isSelf,
          enemy: isEnemy(entry.name),
          ally: isAlly(entry.name)
        }"
        :style="{ left: `${entry.progress}%` }"
      >
        <div class="entry-marker">
          <span class="entry-icon">{{ getCharacterIcon(entry.name) }}</span>
        </div>
        <div class="entry-info">
          <span class="entry-name">{{ entry.name }}</span>
          <span class="entry-progress">{{ entry.progress.toFixed(1) }}%</span>
        </div>
      </div>
    </div>
    <div class="action-bar-labels">
      <span class="label-start">0%</span>
      <span class="label-mid">50%</span>
      <span class="label-end">100%</span>
    </div>
  </div>
</template>

<script setup>
import { useCombatStore } from '../../stores/combatStore'

const combatStore = useCombatStore()

function isEnemy(name) {
  return combatStore.enemies.some(e => e.name === name)
}

function isAlly(name) {
  return combatStore.allies.some(a => a.name === name)
}

function getCharacterIcon(name) {
  const char = combatStore.getCharacter(name)
  if (!char) return '?'
  if (char.isDead) return '☠'
  if (char.isSelf) return '★'
  if (isEnemy(name)) return '!'
  return '♦'
}
</script>

<style scoped>
.action-bar-display {
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid var(--border-color);
}

.action-bar-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.action-bar-track {
  position: relative;
  height: 40px;
  background: linear-gradient(90deg,
    rgba(76, 175, 80, 0.1) 0%,
    rgba(255, 193, 7, 0.1) 50%,
    rgba(244, 67, 54, 0.1) 100%
  );
  border: 1px solid var(--border-color);
  border-radius: 4px;
}

.action-bar-entry {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: left 0.3s ease;
}

.entry-marker {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  border: 2px solid var(--border-color);
  background: var(--bg-panel);
}

.action-bar-entry.current .entry-marker {
  border-color: var(--primary);
  box-shadow: 0 0 8px var(--primary);
}

.action-bar-entry.self .entry-marker {
  background: var(--primary);
  color: #000;
}

.action-bar-entry.enemy .entry-marker {
  border-color: var(--entity-enemy);
}

.action-bar-entry.ally .entry-marker {
  border-color: var(--primary);
}

.entry-icon {
  font-size: 10px;
}

.entry-info {
  position: absolute;
  top: 100%;
  margin-top: 2px;
  display: flex;
  flex-direction: column;
  align-items: center;
  white-space: nowrap;
}

.entry-name {
  font-size: 10px;
  color: var(--text-secondary);
  max-width: 60px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entry-progress {
  font-size: 9px;
  color: var(--text-muted);
}

.action-bar-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
  font-size: 10px;
  color: var(--text-muted);
}
</style>
