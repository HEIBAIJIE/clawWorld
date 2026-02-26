<template>
  <div class="popup-panel sci-panel character-panel">
    <div class="popup-panel-header">
      <span class="popup-panel-title">角色信息</span>
      <button class="popup-panel-close" @click="uiStore.closePanel()">×</button>
    </div>

    <div class="popup-panel-content sci-scrollbar">
      <!-- 角色头部 -->
      <div class="character-header">
        <div class="character-avatar">{{ playerStore.roleIcon }}</div>
        <div class="character-info">
          <div class="character-name">{{ playerStore.name }}</div>
          <div class="character-class">{{ playerStore.roleName }} Lv.{{ playerStore.level }}</div>
          <div class="character-position">位置: ({{ playerStore.x }}, {{ playerStore.y }})</div>
        </div>
      </div>

      <!-- 血条和蓝条 -->
      <div class="character-bars">
        <div class="bar-row">
          <span class="bar-label">HP</span>
          <div class="bar-container">
            <div class="sci-progress">
              <div
                class="sci-progress-bar health"
                :style="{ width: playerStore.healthPercent + '%', backgroundPosition: (100 - playerStore.healthPercent) + '% 0' }"
              ></div>
            </div>
          </div>
          <span class="bar-value">{{ playerStore.currentHealth }}/{{ playerStore.maxHealth }}</span>
        </div>
        <div class="bar-row">
          <span class="bar-label">MP</span>
          <div class="bar-container">
            <div class="sci-progress">
              <div class="sci-progress-bar mana" :style="{ width: playerStore.manaPercent + '%' }"></div>
            </div>
          </div>
          <span class="bar-value">{{ playerStore.currentMana }}/{{ playerStore.maxMana }}</span>
        </div>
        <div class="bar-row">
          <span class="bar-label">EXP</span>
          <div class="bar-container">
            <div class="sci-progress">
              <div class="sci-progress-bar exp" :style="{ width: playerStore.expPercent + '%' }"></div>
            </div>
          </div>
          <span class="bar-value">{{ playerStore.experience }}/{{ playerStore.experienceForNextLevel }}</span>
        </div>
      </div>

      <div class="sci-divider"></div>

      <!-- 四维属性 -->
      <div class="attributes-section">
        <div class="section-title">
          属性
          <span v-if="playerStore.freeAttributePoints > 0" class="free-points">
            (可用: {{ playerStore.freeAttributePoints }})
          </span>
        </div>
        <div class="attributes-grid">
          <div class="attribute-item">
            <span class="attribute-name">力量</span>
            <div class="attribute-value">
              <span class="attribute-number">{{ playerStore.strength }}</span>
              <button
                v-if="playerStore.freeAttributePoints > 0"
                class="sci-button attribute-add"
                @click="addAttribute('str')"
              >+</button>
            </div>
          </div>
          <div class="attribute-item">
            <span class="attribute-name">敏捷</span>
            <div class="attribute-value">
              <span class="attribute-number">{{ playerStore.agility }}</span>
              <button
                v-if="playerStore.freeAttributePoints > 0"
                class="sci-button attribute-add"
                @click="addAttribute('agi')"
              >+</button>
            </div>
          </div>
          <div class="attribute-item">
            <span class="attribute-name">智力</span>
            <div class="attribute-value">
              <span class="attribute-number">{{ playerStore.intelligence }}</span>
              <button
                v-if="playerStore.freeAttributePoints > 0"
                class="sci-button attribute-add"
                @click="addAttribute('int')"
              >+</button>
            </div>
          </div>
          <div class="attribute-item">
            <span class="attribute-name">体力</span>
            <div class="attribute-value">
              <span class="attribute-number">{{ playerStore.vitality }}</span>
              <button
                v-if="playerStore.freeAttributePoints > 0"
                class="sci-button attribute-add"
                @click="addAttribute('vit')"
              >+</button>
            </div>
          </div>
        </div>
      </div>

      <div class="sci-divider"></div>

      <!-- 战斗属性 -->
      <div class="attributes-section">
        <div class="section-title">战斗属性</div>
        <div class="combat-stats">
          <div class="stat-row">
            <span>物攻</span><span>{{ playerStore.physicalAttack }}</span>
          </div>
          <div class="stat-row">
            <span>物防</span><span>{{ playerStore.physicalDefense }}</span>
          </div>
          <div class="stat-row">
            <span>法攻</span><span>{{ playerStore.magicAttack }}</span>
          </div>
          <div class="stat-row">
            <span>法防</span><span>{{ playerStore.magicDefense }}</span>
          </div>
          <div class="stat-row">
            <span>速度</span><span>{{ playerStore.speed }}</span>
          </div>
          <div class="stat-row">
            <span>暴击率</span><span>{{ playerStore.critRate }}%</span>
          </div>
          <div class="stat-row">
            <span>暴击伤害</span><span>{{ playerStore.critDamage }}%</span>
          </div>
          <div class="stat-row">
            <span>命中率</span><span>{{ playerStore.hitRate }}%</span>
          </div>
          <div class="stat-row">
            <span>闪避率</span><span>{{ playerStore.dodgeRate }}%</span>
          </div>
        </div>
      </div>

      <div class="sci-divider"></div>

      <!-- 装备栏 -->
      <div class="attributes-section">
        <div class="section-title">装备</div>
        <div class="equipment-grid">
          <div
            v-for="(slot, key) in equipmentSlots"
            :key="key"
            class="equipment-slot"
            :class="{ empty: !playerStore.equipment[key] }"
            @click="inspectEquipment(key)"
            @contextmenu.prevent="handleEquipmentRightClick(key, $event)"
          >
            <span class="slot-icon">{{ slot.icon }}</span>
            <div class="slot-info">
              <span class="slot-label">{{ slot.label }}</span>
              <span class="slot-value" :class="{ equipped: playerStore.equipment[key] }">
                {{ getEquipmentDisplayName(playerStore.equipment[key]) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="sci-divider"></div>

      <!-- 技能列表 -->
      <div class="attributes-section">
        <div class="section-title">技能</div>
        <div class="skills-list">
          <div v-for="skill in playerStore.skills" :key="skill.name" class="skill-item">
            <span class="skill-name">{{ skill.name }}</span>
            <span class="skill-info">
              {{ skill.targetType }} | {{ skill.manaCost }}MP
              <template v-if="skill.cooldown > 0"> | CD:{{ skill.cooldown }}</template>
            </span>
          </div>
          <div v-if="playerStore.skills.length === 0" class="empty-text">
            暂无技能
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useUIStore } from '../../stores/uiStore'
import { usePlayerStore } from '../../stores/playerStore'
import { useAgentStore } from '../../stores/agentStore'
import { useCommand } from '../../composables/useCommand'

const uiStore = useUIStore()
const playerStore = usePlayerStore()
const agentStore = useAgentStore()
const { addAttribute: addAttr, sendCommand } = useCommand()

// 装备槽位配置
const equipmentSlots = {
  HEAD: { label: '头部', icon: '🪖' },
  CHEST: { label: '上装', icon: '👕' },
  LEGS: { label: '下装', icon: '👖' },
  FEET: { label: '鞋子', icon: '👟' },
  RIGHT_HAND: { label: '右手', icon: '⚔️' },
  LEFT_HAND: { label: '左手', icon: '🛡️' },
  ACCESSORY1: { label: '饰品1', icon: '💍' },
  ACCESSORY2: { label: '饰品2', icon: '📿' }
}

function addAttribute(attr) {
  addAttr(attr, 1)
}

// 获取装备显示名称（简化版，不含槽位前缀）
function getEquipmentDisplayName(equipment) {
  if (!equipment || !equipment.name) return '无'
  // 如果名称包含槽位前缀如 [头部]，则移除
  let name = equipment.name
  if (name.startsWith('[') && name.includes(']')) {
    name = name.substring(name.indexOf(']') + 1)
  }
  return name
}

function inspectEquipment(slotKey) {
  const equipment = playerStore.equipment[slotKey]
  if (equipment && equipment.name) {
    // 非AI代理模式下，发送inspect命令查看装备详情
    if (!agentStore.isAgentMode) {
      const displayName = getEquipmentDisplayName(equipment)
      sendCommand(`inspect ${displayName}`)
    }
  }
}

// 装备槽位右键菜单
function handleEquipmentRightClick(slotKey, event) {
  const equipment = playerStore.equipment[slotKey]
  if (!equipment || !equipment.name) return

  const slotLabel = equipmentSlots[slotKey].label
  const displayName = getEquipmentDisplayName(equipment)

  const items = [
    { label: '查看', action: () => sendCommand(`inspect ${displayName}`) },
    { label: '卸下', action: () => sendCommand(`unequip ${slotLabel}`) }
  ]

  uiStore.showContextMenu(event.clientX, event.clientY, items, equipment)
}
</script>

<style scoped>
.character-position {
  color: var(--text-muted);
  font-size: 11px;
  margin-top: 2px;
}

.free-points {
  color: var(--primary);
  font-size: 11px;
  font-weight: normal;
}

.combat-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 8px;
  font-size: 12px;
}

.stat-row span:first-child {
  color: var(--text-secondary);
}

.stat-row span:last-child {
  color: var(--text-primary);
}

/* 装备栏样式 */
.equipment-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
}

.equipment-slot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--bg-dark);
  border-radius: var(--button-radius);
  cursor: pointer;
  transition: all var(--transition-fast);
  border: 1px solid transparent;
}

.equipment-slot:hover:not(.empty) {
  border-color: var(--primary);
  background: var(--bg-hover);
}

.equipment-slot.empty {
  opacity: 0.6;
  cursor: default;
}

.slot-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.slot-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.slot-label {
  font-size: 10px;
  color: var(--text-muted);
}

.slot-value {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slot-value.equipped {
  color: var(--text-primary);
}

.skills-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skill-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: var(--bg-dark);
  border-radius: var(--button-radius);
}

.skill-name {
  color: var(--text-primary);
  font-size: 13px;
}

.skill-info {
  color: var(--text-muted);
  font-size: 11px;
}

.empty-text {
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
  padding: 12px;
}
</style>
