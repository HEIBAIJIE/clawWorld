<template>
  <div class="register-overlay" v-if="registerStore.isInRegister">
    <div class="register-window sci-panel">
      <!-- 标题栏 -->
      <div class="register-header">
        <div class="header-decoration left"></div>
        <div class="header-content">
          <span class="register-title">欢迎来到 ClawWorld</span>
          <span class="register-subtitle">创建你的冒险角色</span>
        </div>
        <div class="header-decoration right"></div>
      </div>

      <!-- 主区域 -->
      <div class="register-main">
        <!-- 左侧：职业选择 -->
        <div class="role-selection">
          <div class="section-title">
            <span class="title-icon">⚔</span>
            <span>选择职业</span>
          </div>

          <div class="role-list">
            <div
              v-for="role in registerStore.roles"
              :key="role.name"
              class="role-card"
              :class="{ selected: registerStore.selectedRole?.name === role.name }"
              @click="registerStore.selectRole(role)"
            >
              <div class="role-icon-wrapper">
                <div class="role-icon">{{ getRoleIcon(role.name) }}</div>
                <div class="role-icon-bg"></div>
              </div>
              <div class="role-info">
                <div class="role-name">{{ role.name }}</div>
                <div class="role-desc">{{ role.description }}</div>
              </div>
              <div class="role-check" v-if="registerStore.selectedRole?.name === role.name">
                <span>✓</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 分隔线 -->
        <div class="main-divider"></div>

        <!-- 右侧：职业详情和昵称输入 -->
        <div class="role-details">
          <!-- 职业属性展示 -->
          <div class="stats-panel" v-if="registerStore.selectedRole">
            <div class="section-title">
              <span class="title-icon">📊</span>
              <span>{{ registerStore.selectedRole.name }} 属性</span>
            </div>

            <div class="role-portrait">
              <div class="portrait-frame">
                <div class="portrait-icon">{{ getRoleIcon(registerStore.selectedRole.name) }}</div>
              </div>
              <div class="portrait-glow"></div>
              <div class="portrait-name">{{ registerStore.selectedRole.name }}</div>
            </div>

            <div class="stats-grid">
              <div class="stat-item" v-for="stat in displayStats" :key="stat.key">
                <div class="stat-icon">{{ stat.icon }}</div>
                <div class="stat-info">
                  <div class="stat-label">
                    <span class="stat-name">{{ stat.label }}</span>
                    <span class="stat-value">{{ stat.value }}</span>
                  </div>
                  <div class="stat-bar-container">
                    <div
                      class="stat-bar"
                      :class="stat.colorClass"
                      :style="{ width: getStatPercent(stat.value) + '%' }"
                    ></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 未选择职业时的提示 -->
          <div class="no-selection" v-else>
            <div class="no-selection-icon">👈</div>
            <div class="no-selection-text">请从左侧选择一个职业</div>
          </div>

          <!-- 昵称输入 -->
          <div class="nickname-section">
            <div class="section-title">
              <span class="title-icon">✏</span>
              <span>角色昵称</span>
            </div>

            <div class="nickname-input-wrapper">
              <input
                type="text"
                class="sci-input nickname-input"
                v-model="nickname"
                placeholder="请输入2-12个字符的昵称..."
                maxlength="12"
                @keyup.enter="handleRegister"
              />
              <span class="nickname-length" :class="{ valid: nickname.length >= 2 }">
                {{ nickname.length }}/12
              </span>
            </div>
            <div class="nickname-hint" v-if="nickname.length > 0 && nickname.length < 2">
              昵称至少需要2个字符
            </div>
          </div>

          <!-- 创建按钮 -->
          <button
            class="sci-button primary create-button"
            :disabled="!canRegister || isRegistering"
            @click="handleRegister"
          >
            <span class="button-icon" v-if="!isRegistering">🚀</span>
            <span class="button-text">{{ isRegistering ? '创建中...' : '开始冒险' }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRegisterStore } from '../../stores/registerStore'
import { useCommand } from '../../composables/useCommand'

const registerStore = useRegisterStore()
const { sendCommand } = useCommand()

const nickname = ref('')
const isRegistering = ref(false)

// 同步昵称到 store
watch(nickname, (val) => {
  registerStore.setNickname(val)
})

// 职业图标映射（默认图标）
const roleIcons = {
  '游侠': '🏹',
  '战士': '⚔️',
  '法师': '🔮',
  '牧师': '✨',
  '刺客': '🗡️',
  '骑士': '🛡️',
  '猎人': '🎯',
  '术士': '💀',
  '盗贼': '🥷',
  '弓箭手': '🎯',
  '圣骑士': '⚜️',
  '死灵法师': '💀'
}

// 获取职业图标
function getRoleIcon(roleName) {
  return roleIcons[roleName] || '👤'
}

// 属性显示配置
const displayStats = computed(() => {
  const role = registerStore.selectedRole
  if (!role || !role.stats) return []

  return [
    { key: 'health', label: '生命', icon: '❤️', value: role.stats.health || 0, colorClass: 'health' },
    { key: 'mana', label: '法力', icon: '💧', value: role.stats.mana || 0, colorClass: 'mana' },
    { key: 'physicalAttack', label: '物攻', icon: '⚔️', value: role.stats.physicalAttack || 0, colorClass: 'attack' },
    { key: 'physicalDefense', label: '物防', icon: '🛡️', value: role.stats.physicalDefense || 0, colorClass: 'defense' },
    { key: 'magicAttack', label: '法攻', icon: '✨', value: role.stats.magicAttack || 0, colorClass: 'magic' },
    { key: 'magicDefense', label: '法防', icon: '🔮', value: role.stats.magicDefense || 0, colorClass: 'defense' },
    { key: 'speed', label: '速度', icon: '💨', value: role.stats.speed || 0, colorClass: 'speed' }
  ]
})

// 计算属性条百分比（基于最大值150）
function getStatPercent(value) {
  const maxValue = 150
  return Math.min((value / maxValue) * 100, 100)
}

// 是否可以注册
const canRegister = computed(() => {
  return registerStore.selectedRole && nickname.value.trim().length >= 2
})

// 处理注册
async function handleRegister() {
  if (!canRegister.value || isRegistering.value) return

  isRegistering.value = true
  try {
    await sendCommand(`register ${registerStore.selectedRole.name} ${nickname.value.trim()}`)
    // 注册成功后，服务端会返回新的窗口状态，由 useCommand 处理
  } finally {
    isRegistering.value = false
  }
}
</script>

<style scoped>
.register-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.85);
  z-index: 100;
  backdrop-filter: blur(4px);
}

.register-window {
  width: 90%;
  max-width: 780px;
  min-width: 560px;
  max-height: 90%;
  display: flex;
  flex-direction: column;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5), 0 0 60px var(--primary-glow);
}

.register-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-color);
  background: linear-gradient(180deg, var(--bg-dark) 0%, var(--bg-panel) 100%);
}

.header-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.header-decoration {
  width: 80px;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--primary), transparent);
}

.register-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-highlight);
  text-shadow: 0 0 15px var(--primary-glow);
}

.register-subtitle {
  font-size: 13px;
  color: var(--text-muted);
}

.register-main {
  display: flex;
  padding: 20px;
  gap: 20px;
  flex: 1;
  overflow: hidden;
}

.main-divider {
  width: 1px;
  background: linear-gradient(180deg, transparent, var(--border-color), transparent);
}

/* 左侧职业选择 */
.role-selection {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.title-icon {
  font-size: 16px;
}

.role-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.role-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: var(--bg-dark);
  border: 1px solid var(--border-color);
  border-radius: var(--button-radius);
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
}

.role-card:hover {
  border-color: var(--primary);
  background: var(--bg-hover);
  transform: translateX(4px);
}

.role-card.selected {
  border-color: var(--primary);
  background: rgba(76, 175, 80, 0.15);
  box-shadow: 0 0 12px var(--primary-glow), inset 0 0 20px rgba(76, 175, 80, 0.05);
}

.role-icon-wrapper {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.role-icon {
  position: relative;
  z-index: 1;
  font-size: 28px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.role-icon-bg {
  position: absolute;
  inset: 0;
  background: var(--bg-panel);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.role-card.selected .role-icon-bg {
  border-color: var(--primary);
  background: rgba(76, 175, 80, 0.1);
}

.role-info {
  flex: 1;
  min-width: 0;
}

.role-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-highlight);
  margin-bottom: 2px;
}

.role-desc {
  font-size: 11px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-check {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  font-weight: bold;
  box-shadow: 0 0 8px var(--primary-glow);
}

/* 右侧详情 */
.role-details {
  flex: 1.3;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stats-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.no-selection {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-muted);
}

.no-selection-icon {
  font-size: 48px;
  animation: point-left 1s ease-in-out infinite;
}

@keyframes point-left {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(-10px); }
}

.no-selection-text {
  font-size: 14px;
}

.role-portrait {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px;
  position: relative;
}

.portrait-frame {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-dark);
  border: 2px solid var(--primary);
  border-radius: 12px;
  position: relative;
  z-index: 1;
}

.portrait-icon {
  font-size: 48px;
}

.portrait-glow {
  position: absolute;
  width: 100px;
  height: 100px;
  background: radial-gradient(circle, var(--primary-glow) 0%, transparent 70%);
  border-radius: 50%;
  animation: pulse-glow 2s ease-in-out infinite;
}

.portrait-name {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--primary);
  text-shadow: 0 0 10px var(--primary-glow);
}

@keyframes pulse-glow {
  0%, 100% {
    transform: scale(1);
    opacity: 0.4;
  }
  50% {
    transform: scale(1.3);
    opacity: 0.7;
  }
}

.stats-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  padding-right: 4px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  background: var(--bg-dark);
  border-radius: var(--button-radius);
  border: 1px solid transparent;
  transition: border-color var(--transition-fast);
}

.stat-item:hover {
  border-color: var(--border-color);
}

.stat-icon {
  font-size: 16px;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.stat-name {
  font-size: 12px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-highlight);
}

.stat-bar-container {
  height: 6px;
  background: var(--bg-panel);
  border-radius: 3px;
  overflow: hidden;
}

.stat-bar {
  height: 100%;
  border-radius: 3px;
  transition: width var(--transition-normal);
}

.stat-bar.health {
  background: linear-gradient(90deg, #c62828, #ef5350);
}

.stat-bar.mana {
  background: linear-gradient(90deg, #1565c0, #42a5f5);
}

.stat-bar.attack {
  background: linear-gradient(90deg, #e65100, #ff9800);
}

.stat-bar.defense {
  background: linear-gradient(90deg, #558b2f, #8bc34a);
}

.stat-bar.magic {
  background: linear-gradient(90deg, #6a1b9a, #ab47bc);
}

.stat-bar.speed {
  background: linear-gradient(90deg, #00838f, #26c6da);
}

/* 昵称输入 */
.nickname-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nickname-input-wrapper {
  position: relative;
}

.nickname-input {
  width: 100%;
  padding: 12px 60px 12px 14px;
  font-size: 15px;
  box-sizing: border-box;
}

.nickname-length {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: var(--text-muted);
  transition: color var(--transition-fast);
}

.nickname-length.valid {
  color: var(--primary);
}

.nickname-hint {
  font-size: 11px;
  color: var(--entity-enemy);
  padding-left: 4px;
}

/* 创建按钮 */
.create-button {
  width: 100%;
  padding: 14px 20px;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all var(--transition-fast);
}

.button-icon {
  font-size: 18px;
}

.create-button:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px var(--primary-glow);
}

.create-button:not(:disabled):active {
  transform: translateY(0);
}

/* 滚动条 */
.role-list::-webkit-scrollbar,
.stats-grid::-webkit-scrollbar {
  width: 4px;
}

.role-list::-webkit-scrollbar-track,
.stats-grid::-webkit-scrollbar-track {
  background: var(--bg-dark);
  border-radius: 2px;
}

.role-list::-webkit-scrollbar-thumb,
.stats-grid::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 2px;
}

.role-list::-webkit-scrollbar-thumb:hover,
.stats-grid::-webkit-scrollbar-thumb:hover {
  background: var(--border-light);
}
</style>
