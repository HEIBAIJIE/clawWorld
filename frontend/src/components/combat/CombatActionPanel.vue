<template>
  <div class="combat-action-panel">
    <!-- 倒计时条（轮到自己时显示） -->
    <div class="turn-countdown" v-if="combatStore.isMyTurn">
      <div class="countdown-bar">
        <div class="countdown-fill" :style="{ width: `${countdownPercent}%` }"></div>
      </div>
      <span class="countdown-text">剩余时间: {{ combatStore.turnCountdown }}s</span>
    </div>

    <!-- 等待提示（不是自己回合时显示） -->
    <div class="waiting-hint" v-else>
      <span class="waiting-text">等待 {{ combatStore.currentTurn || '...' }} 行动中...</span>
    </div>

    <!-- 目标选择提示 -->
    <div class="target-selection-hint" v-if="combatStore.targetSelectionMode">
      <span>请选择目标释放 {{ combatStore.pendingSkill?.name }}</span>
      <button class="cancel-btn" @click="cancelTargetSelection">取消</button>
    </div>

    <!-- 行动按钮区 -->
    <div class="action-buttons" v-if="combatStore.isMyTurn && !combatStore.targetSelectionMode">
      <button class="action-btn attack-btn" @click="handleAttack">
        <span class="btn-icon">⚔</span>
        <span class="btn-text">攻击</span>
      </button>

      <button class="action-btn skill-btn" @click="showSkillPanel = true">
        <span class="btn-icon">✦</span>
        <span class="btn-text">技能</span>
      </button>

      <button class="action-btn item-btn" @click="showInventoryPanel = true">
        <span class="btn-icon">🎒</span>
        <span class="btn-text">物品</span>
      </button>

      <button class="action-btn retreat-btn" @click="handleRetreat">
        <span class="btn-icon">🏃</span>
        <span class="btn-text">撤退</span>
      </button>
    </div>

    <!-- 技能面板 -->
    <SkillPanel
      v-if="showSkillPanel"
      @close="showSkillPanel = false"
      @select="handleSkillSelect"
    />

    <!-- 战斗背包面板 -->
    <CombatInventoryPanel
      v-if="showInventoryPanel"
      @close="showInventoryPanel = false"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useCombatStore } from '../../stores/combatStore'
import { useCommand } from '../../composables/useCommand'
import SkillPanel from './SkillPanel.vue'
import CombatInventoryPanel from './CombatInventoryPanel.vue'

const combatStore = useCombatStore()
const { sendCommand } = useCommand()

const showSkillPanel = ref(false)
const showInventoryPanel = ref(false)

const countdownPercent = computed(() => (combatStore.turnCountdown / 10) * 100)

// 普通攻击（敌方单体技能）
function handleAttack() {
  const basicAttack = {
    name: '普通攻击',
    targetType: 'ENEMY_SINGLE',
    manaCost: 0
  }

  // 如果只有一个存活敌人，直接攻击
  if (combatStore.aliveEnemies.length === 1) {
    sendCommand(`cast 普通攻击 ${combatStore.aliveEnemies[0].name}`)
  } else {
    // 进入目标选择模式
    combatStore.enterTargetSelection(basicAttack)
  }
}

function handleSkillSelect(skill) {
  showSkillPanel.value = false

  // 根据技能类型决定是否需要选择目标
  if (skill.targetType === 'ENEMY_SINGLE' || skill.targetType === 'ALLY_SINGLE') {
    // 单体技能需要选择目标
    if (skill.targetType === 'ENEMY_SINGLE' && combatStore.aliveEnemies.length === 1) {
      // 只有一个敌人时直接释放
      sendCommand(`cast ${skill.name} ${combatStore.aliveEnemies[0].name}`)
    } else if (skill.targetType === 'ALLY_SINGLE' && combatStore.aliveAllies.length === 1) {
      // 只有自己时直接释放
      sendCommand(`cast ${skill.name} ${combatStore.aliveAllies[0].name}`)
    } else {
      combatStore.enterTargetSelection(skill)
    }
  } else {
    // 群体技能或自身技能直接释放
    sendCommand(`cast ${skill.name}`)
  }
}

function cancelTargetSelection() {
  combatStore.exitTargetSelection()
}

function handleRetreat() {
  if (confirm('确定要撤退吗？撤退后无法获得战利品。')) {
    sendCommand('end')
  }
}
</script>

<style scoped>
.combat-action-panel {
  padding: 12px;
  background: var(--bg-panel);
  border-top: 1px solid var(--border-color);
}

.turn-countdown {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.countdown-bar {
  flex: 1;
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

.countdown-fill {
  height: 100%;
  background: linear-gradient(90deg, #f44336, #ffc107, #4caf50);
  transition: width 1s linear;
}

.countdown-text {
  font-size: 12px;
  color: #ffc107;
  min-width: 100px;
}

.waiting-hint {
  text-align: center;
  padding: 8px;
  margin-bottom: 12px;
}

.waiting-text {
  font-size: 12px;
  color: var(--text-muted);
}

.target-selection-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px;
  background: rgba(255, 193, 7, 0.1);
  border: 1px solid #ffc107;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #ffc107;
}

.cancel-btn {
  padding: 4px 12px;
  background: transparent;
  border: 1px solid #ffc107;
  border-radius: 4px;
  color: #ffc107;
  cursor: pointer;
  font-size: 11px;
}

.cancel-btn:hover {
  background: rgba(255, 193, 7, 0.2);
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 20px;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  min-width: 80px;
}

.action-btn:hover {
  border-color: var(--primary);
  background: rgba(76, 175, 80, 0.1);
}

.btn-icon {
  font-size: 20px;
}

.btn-text {
  font-size: 12px;
  color: var(--text-secondary);
}

.attack-btn:hover {
  border-color: #f44336;
  background: rgba(244, 67, 54, 0.1);
}

.attack-btn:hover .btn-text {
  color: #f44336;
}

.skill-btn:hover {
  border-color: #2196f3;
  background: rgba(33, 150, 243, 0.1);
}

.skill-btn:hover .btn-text {
  color: #2196f3;
}

.item-btn:hover {
  border-color: #ffc107;
  background: rgba(255, 193, 7, 0.1);
}

.item-btn:hover .btn-text {
  color: #ffc107;
}

.retreat-btn:hover {
  border-color: #9e9e9e;
  background: rgba(158, 158, 158, 0.1);
}

.retreat-btn:hover .btn-text {
  color: #9e9e9e;
}
</style>
