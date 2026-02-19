<template>
  <teleport to="body">
    <template v-if="uiStore.infoModal.visible">
      <!-- 遮罩 -->
      <div class="modal-overlay" @click="uiStore.closeInfoModal()"></div>

      <!-- 弹窗 -->
      <div class="info-modal sci-panel">
        <div class="info-modal-header">
          <div class="header-icon">
            <span v-if="isEnemy">👹</span>
            <span v-else-if="isNPC">🧙</span>
            <span v-else-if="isCharacter">👤</span>
            <span v-else-if="isItem">📦</span>
            <span v-else>📋</span>
          </div>
          <span class="info-modal-title">{{ uiStore.infoModal.title }}</span>
          <button class="close-btn" @click="uiStore.closeInfoModal()">×</button>
        </div>

        <div class="info-modal-content">
          <!-- 解析后的结构化显示 -->
          <div class="info-sections">
            <template v-for="(section, index) in parsedSections" :key="index">
              <div class="info-section" v-if="section.lines.length > 0">
                <div class="section-header" v-if="section.title">
                  {{ section.title }}
                </div>
                <div class="section-content">
                  <div
                    v-for="(line, lineIndex) in section.lines"
                    :key="lineIndex"
                    class="info-line"
                    :class="getLineClass(line)"
                  >
                    <template v-if="line.includes(':') || line.includes('：')">
                      <span class="line-label">{{ getLabel(line) }}</span>
                      <span class="line-value" :class="getValueClass(line)">{{ getValue(line) }}</span>
                    </template>
                    <template v-else>
                      <span class="line-text">{{ line }}</span>
                    </template>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div class="info-modal-footer">
          <button class="confirm-btn" @click="uiStore.closeInfoModal()">确定</button>
        </div>
      </div>
    </template>
  </teleport>
</template>

<script setup>
import { computed } from 'vue'
import { useUIStore } from '../../stores/uiStore'

const uiStore = useUIStore()

const isEnemy = computed(() => uiStore.infoModal.title.includes('敌人'))
const isNPC = computed(() => uiStore.infoModal.title.includes('NPC'))
const isCharacter = computed(() => uiStore.infoModal.title.includes('角色'))
const isItem = computed(() => uiStore.infoModal.title.includes('物品'))

// 解析内容为结构化的sections
const parsedSections = computed(() => {
  const content = uiStore.infoModal.content || ''
  const lines = content.split('\n').filter(line => line.trim())

  const sections = []
  let currentSection = { title: '', lines: [] }

  for (const line of lines) {
    const trimmed = line.trim()
    // 检测section标题（以===包围的行）
    if (trimmed.startsWith('===') && trimmed.endsWith('===')) {
      // 保存之前的section
      if (currentSection.lines.length > 0 || currentSection.title) {
        sections.push(currentSection)
      }
      currentSection = {
        title: trimmed.replace(/===/g, '').trim(),
        lines: []
      }
    } else if (trimmed) {
      currentSection.lines.push(trimmed)
    }
  }

  // 保存最后一个section
  if (currentSection.lines.length > 0 || currentSection.title) {
    sections.push(currentSection)
  }

  return sections
})

function getLabel(line) {
  const colonIndex = line.indexOf(':') !== -1 ? line.indexOf(':') : line.indexOf('：')
  return colonIndex !== -1 ? line.substring(0, colonIndex + 1) : ''
}

function getValue(line) {
  const colonIndex = line.indexOf(':') !== -1 ? line.indexOf(':') : line.indexOf('：')
  return colonIndex !== -1 ? line.substring(colonIndex + 1).trim() : line
}

function getLineClass(line) {
  if (line.includes('生命') || line.includes('法力')) return 'stat-line'
  if (line.includes('攻击') || line.includes('防御')) return 'combat-line'
  if (line.includes('经验') || line.includes('金')) return 'reward-line'
  return ''
}

function getValueClass(line) {
  const value = getValue(line)
  if (value.includes('%')) return 'percent-value'
  if (value.match(/^\d+\/\d+$/)) return 'fraction-value'
  if (value.match(/^\d+ - \d+$/)) return 'range-value'
  if (line.includes('位置')) return 'position-value'
  return ''
}
</script>

<style scoped>
.info-modal {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 400px;
  max-height: 80vh;
  z-index: 1001;
  display: flex;
  flex-direction: column;
  background: var(--bg-panel);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  animation: modal-appear 0.2s ease-out;
}

@keyframes modal-appear {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

.info-modal-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-color);
  background: rgba(0, 0, 0, 0.2);
}

.header-icon {
  font-size: 20px;
  line-height: 1;
}

.info-modal-title {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  color: var(--primary-color);
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 22px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.close-btn:hover {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.1);
}

.info-modal-content {
  padding: 16px;
  overflow-y: auto;
  max-height: calc(80vh - 120px);
}

.info-sections {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-section {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 6px;
  overflow: hidden;
}

.section-header {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--primary-color);
  background: rgba(76, 175, 80, 0.15);
  border-bottom: 1px solid rgba(76, 175, 80, 0.2);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.section-content {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  padding: 4px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.info-line:last-child {
  border-bottom: none;
}

.line-label {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.line-value {
  color: var(--text-primary);
  text-align: right;
  font-weight: 500;
}

.line-text {
  color: var(--text-primary);
}

/* 特殊值样式 */
.percent-value {
  color: #64b5f6;
}

.fraction-value {
  color: #81c784;
}

.range-value {
  color: #ffb74d;
}

.position-value {
  color: #ba68c8;
  font-family: monospace;
}

/* 特殊行样式 */
.stat-line .line-value {
  color: #81c784;
}

.combat-line .line-value {
  color: #ef5350;
}

.reward-line .line-value {
  color: #ffd54f;
}

.info-modal-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: center;
}

.confirm-btn {
  padding: 8px 32px;
  background: var(--primary);
  border: none;
  border-radius: 4px;
  color: #000;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.confirm-btn:hover {
  background: #5dbb63;
  transform: translateY(-1px);
}

.confirm-btn:active {
  transform: translateY(0);
}
</style>
