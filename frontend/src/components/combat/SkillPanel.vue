<template>
  <div class="skill-panel-overlay" @click.self="$emit('close')">
    <div class="skill-panel">
      <div class="panel-header">
        <span class="panel-title">技能列表</span>
        <button class="close-btn" @click="$emit('close')">×</button>
      </div>

      <div class="skill-list">
        <div
          v-for="skill in combatStore.mySkills"
          :key="skill.name"
          class="skill-item"
          :class="{ disabled: !canUseSkill(skill) }"
          @click="handleSelectSkill(skill)"
        >
          <div class="skill-info">
            <span class="skill-name">{{ skill.name }}</span>
            <span class="skill-target">[{{ skill.targetTypeText }}]</span>
          </div>
          <div class="skill-cost">
            <span v-if="skill.manaCost > 0">{{ skill.manaCost }} MP</span>
            <span v-else>无消耗</span>
            <span v-if="skill.cooldown > 0" class="skill-cd">CD: {{ skill.cooldown }}回合</span>
          </div>
        </div>

        <div v-if="combatStore.mySkills.length === 0" class="no-skills">
          暂无可用技能
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useCombatStore } from '../../stores/combatStore'
import { usePlayerStore } from '../../stores/playerStore'

const emit = defineEmits(['close', 'select'])

const combatStore = useCombatStore()
const playerStore = usePlayerStore()

function canUseSkill(skill) {
  // 检查法力是否足够
  const selfChar = combatStore.allies.find(a => a.isSelf)
  if (selfChar && selfChar.currentMana < skill.manaCost) {
    return false
  }
  // TODO: 检查冷却
  return true
}

function handleSelectSkill(skill) {
  if (!canUseSkill(skill)) return
  emit('select', skill)
}
</script>

<style scoped>
.skill-panel-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.skill-panel {
  width: 300px;
  max-height: 400px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid var(--border-color);
}

.panel-title {
  font-size: 13px;
  color: var(--text-primary);
}

.close-btn {
  width: 24px;
  height: 24px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: var(--text-primary);
}

.skill-list {
  max-height: 350px;
  overflow-y: auto;
}

.skill-item {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background 0.2s ease;
}

.skill-item:hover:not(.disabled) {
  background: rgba(76, 175, 80, 0.1);
}

.skill-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.skill-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.skill-name {
  font-size: 13px;
  color: var(--text-primary);
}

.skill-target {
  font-size: 11px;
  color: var(--text-muted);
}

.skill-cost {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 11px;
  color: #2196f3;
}

.skill-cd {
  color: var(--text-muted);
}

.no-skills {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
}
</style>
