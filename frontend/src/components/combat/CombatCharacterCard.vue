<template>
  <div class="combat-character-card"
    :class="{
      dead: character.isDead,
      self: character.isSelf,
      current: isCurrent,
      hovered: isHovered,
      selectable: isSelectable
    }"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
    @click="handleClick"
  >
    <!-- 当前行动指示器 -->
    <div class="current-indicator" v-if="isCurrent">
      <span class="indicator-arrow">▶</span>
      <div class="countdown-bar" v-if="showCountdown">
        <div class="countdown-fill" :style="{ width: `${countdownPercent}%` }"></div>
      </div>
      <span class="countdown-text" v-if="showCountdown">{{ countdown }}s</span>
    </div>

    <!-- 角色信息 -->
    <div class="card-content">
      <div class="character-name" :class="{ self: character.isSelf }">
        {{ character.name }}
        <span class="self-tag" v-if="character.isSelf">(你)</span>
      </div>

      <!-- 生命条 -->
      <div class="stat-bar health-bar">
        <div class="bar-fill" :style="{ width: `${healthPercent}%` }"></div>
        <span class="bar-text">{{ character.currentHealth }}/{{ character.maxHealth }}</span>
      </div>

      <!-- 法力条 -->
      <div class="stat-bar mana-bar" v-if="character.maxMana > 0">
        <div class="bar-fill" :style="{ width: `${manaPercent}%` }"></div>
        <span class="bar-text">{{ character.currentMana }}/{{ character.maxMana }}</span>
      </div>

      <!-- 速度 -->
      <div class="character-speed" v-if="character.speed">
        速度: {{ character.speed }}
      </div>
    </div>

    <!-- 死亡遮罩 -->
    <div class="dead-overlay" v-if="character.isDead">
      <span class="dead-icon">☠</span>
    </div>

    <!-- 特效容器 -->
    <div class="effect-container" ref="effectContainer"></div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useCombatStore } from '../../stores/combatStore'

const props = defineProps({
  character: {
    type: Object,
    required: true
  },
  isEnemy: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select'])

const combatStore = useCombatStore()
const effectContainer = ref(null)

const healthPercent = computed(() => {
  if (props.character.maxHealth <= 0) return 0
  return (props.character.currentHealth / props.character.maxHealth) * 100
})

const manaPercent = computed(() => {
  if (props.character.maxMana <= 0) return 0
  return (props.character.currentMana / props.character.maxMana) * 100
})

const isCurrent = computed(() => {
  const current = combatStore.currentActor
  return current && current.name === props.character.name
})

const isHovered = computed(() => {
  return combatStore.hoveredTarget === props.character.name
})

const isSelectable = computed(() => {
  if (!combatStore.targetSelectionMode) return false
  const skill = combatStore.pendingSkill
  if (!skill) return false

  // 根据技能目标类型判断是否可选
  if (skill.targetType === 'ENEMY_SINGLE') {
    return props.isEnemy && !props.character.isDead
  }
  if (skill.targetType === 'ALLY_SINGLE') {
    return !props.isEnemy && !props.character.isDead
  }
  return false
})

const showCountdown = computed(() => {
  // 只有当前行动者是玩家时才显示倒计时
  if (!isCurrent.value) return false
  // 如果是自己的回合，显示自己的倒计时
  if (props.character.isSelf && combatStore.isMyTurn) return true
  // 如果是其他玩家的回合（非敌人AI），也显示倒计时
  // 这里简化处理：敌人名字通常包含#，玩家名字不包含
  return !props.character.name.includes('#')
})

const countdown = computed(() => combatStore.turnCountdown)

const countdownPercent = computed(() => (countdown.value / 10) * 100)

function handleMouseEnter() {
  if (isSelectable.value) {
    combatStore.setHoveredTarget(props.character.name)
  }
}

function handleMouseLeave() {
  if (combatStore.hoveredTarget === props.character.name) {
    combatStore.setHoveredTarget(null)
  }
}

function handleClick() {
  if (isSelectable.value) {
    emit('select', props.character)
  }
}
</script>

<style scoped>
.combat-character-card {
  position: relative;
  padding: 10px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  min-width: 120px;
  transition: all 0.2s ease;
}

.combat-character-card.current {
  border-color: var(--primary);
  box-shadow: 0 0 10px rgba(76, 175, 80, 0.3);
}

.combat-character-card.self {
  border-color: var(--primary);
}

.combat-character-card.hovered {
  border-color: #ffc107;
  box-shadow: 0 0 12px rgba(255, 193, 7, 0.5);
  transform: scale(1.05);
}

.combat-character-card.selectable {
  cursor: pointer;
}

.combat-character-card.selectable:hover {
  border-color: #ffc107;
}

.combat-character-card.dead {
  opacity: 0.5;
}

.current-indicator {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 4px;
}

.indicator-arrow {
  color: var(--primary);
  font-size: 12px;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.countdown-bar {
  width: 40px;
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
}

.countdown-fill {
  height: 100%;
  background: linear-gradient(90deg, #f44336, #ffc107);
  transition: width 1s linear;
}

.countdown-text {
  font-size: 10px;
  color: #ffc107;
  min-width: 20px;
}

.card-content {
  position: relative;
  z-index: 1;
}

.character-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.character-name.self {
  color: var(--primary);
}

.self-tag {
  font-size: 10px;
  color: var(--primary);
}

.stat-bar {
  height: 14px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 2px;
  margin-bottom: 4px;
  position: relative;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  transition: width 0.3s ease;
}

.health-bar .bar-fill {
  background: linear-gradient(90deg, #f44336, #4caf50);
}

.mana-bar .bar-fill {
  background: linear-gradient(90deg, #2196f3, #03a9f4);
}

.bar-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 10px;
  color: #fff;
  text-shadow: 0 0 2px #000;
}

.character-speed {
  font-size: 10px;
  color: var(--text-muted);
  margin-top: 4px;
}

.dead-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 6px;
}

.dead-icon {
  font-size: 24px;
  color: #f44336;
}

.effect-container {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: visible;
}
</style>
