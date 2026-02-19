<template>
  <div class="player-status-hud" v-if="playerStore.name">
    <!-- 角色头像和名称 -->
    <div class="hud-header">
      <div class="hud-avatar">
        <span class="avatar-icon">{{ playerStore.roleIcon }}</span>
        <span class="level-badge">{{ playerStore.level }}</span>
      </div>
      <div class="hud-info">
        <div class="player-name">{{ playerStore.name }}</div>
        <div class="player-class">{{ playerStore.roleName }}</div>
      </div>
    </div>

    <!-- 状态条 -->
    <div class="hud-bars">
      <!-- 血条 -->
      <div class="bar-wrapper">
        <div class="bar-icon hp">♥</div>
        <div class="bar-track hp">
          <div class="bar-fill hp" :style="{ width: playerStore.healthPercent + '%' }"></div>
          <div class="bar-glow hp"></div>
        </div>
        <div class="bar-text">{{ playerStore.currentHealth }}/{{ playerStore.maxHealth }}</div>
      </div>

      <!-- 蓝条 -->
      <div class="bar-wrapper">
        <div class="bar-icon mp">◆</div>
        <div class="bar-track mp">
          <div class="bar-fill mp" :style="{ width: playerStore.manaPercent + '%' }"></div>
          <div class="bar-glow mp"></div>
        </div>
        <div class="bar-text">{{ playerStore.currentMana }}/{{ playerStore.maxMana }}</div>
      </div>

      <!-- 经验条 -->
      <div class="bar-wrapper exp">
        <div class="bar-icon exp">★</div>
        <div class="bar-track exp">
          <div class="bar-fill exp" :style="{ width: playerStore.expPercent + '%' }"></div>
          <div class="bar-glow exp"></div>
        </div>
        <div class="bar-text exp">{{ Math.floor(playerStore.expPercent) }}%</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePlayerStore } from '../../stores/playerStore'

const playerStore = usePlayerStore()
</script>

<style scoped>
.player-status-hud {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 50;
  background: linear-gradient(135deg, rgba(20, 20, 25, 0.95) 0%, rgba(30, 30, 40, 0.9) 100%);
  border: 1px solid rgba(76, 175, 80, 0.3);
  border-radius: 8px;
  padding: 10px 12px;
  min-width: 200px;
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 0 20px rgba(76, 175, 80, 0.1);
  backdrop-filter: blur(8px);
}

.hud-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.hud-avatar {
  position: relative;
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, rgba(76, 175, 80, 0.2) 0%, rgba(76, 175, 80, 0.1) 100%);
  border: 2px solid rgba(76, 175, 80, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 0 10px rgba(76, 175, 80, 0.3),
    inset 0 0 10px rgba(76, 175, 80, 0.1);
}

.avatar-icon {
  font-size: 18px;
}

.level-badge {
  position: absolute;
  bottom: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  background: linear-gradient(135deg, #4CAF50 0%, #388E3C 100%);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 9px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.hud-info {
  flex: 1;
}

.player-name {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}

.player-class {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
}

.hud-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.bar-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.bar-wrapper.exp {
  margin-top: 2px;
}

.bar-icon {
  width: 16px;
  font-size: 10px;
  text-align: center;
}

.bar-icon.hp {
  color: #ef5350;
  text-shadow: 0 0 6px rgba(239, 83, 80, 0.5);
}

.bar-icon.mp {
  color: #42a5f5;
  text-shadow: 0 0 6px rgba(66, 165, 245, 0.5);
}

.bar-icon.exp {
  color: #66bb6a;
  text-shadow: 0 0 6px rgba(102, 187, 106, 0.5);
}

.bar-track {
  flex: 1;
  height: 14px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 7px;
  overflow: hidden;
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.bar-track.exp {
  height: 10px;
  border-radius: 5px;
}

.bar-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.3s ease;
  position: relative;
}

.bar-fill.hp {
  background: linear-gradient(180deg, #ef5350 0%, #c62828 100%);
  box-shadow: 0 0 8px rgba(239, 83, 80, 0.5);
}

.bar-fill.mp {
  background: linear-gradient(180deg, #42a5f5 0%, #1565c0 100%);
  box-shadow: 0 0 8px rgba(66, 165, 245, 0.5);
}

.bar-fill.exp {
  background: linear-gradient(180deg, #66bb6a 0%, #388e3c 100%);
  box-shadow: 0 0 8px rgba(102, 187, 106, 0.5);
}

.bar-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 50%;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.2) 0%, transparent 100%);
  border-radius: inherit;
  pointer-events: none;
}

.bar-text {
  min-width: 55px;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  text-align: right;
  font-family: var(--font-mono);
}

.bar-text.exp {
  min-width: 35px;
  font-size: 9px;
  color: rgba(255, 255, 255, 0.6);
}
</style>
