<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-content sci-panel">
      <div class="modal-header">
        <h3>配置智能代理</h3>
        <button class="close-btn" @click="$emit('close')">×</button>
      </div>

      <div class="modal-body sci-scrollbar">
        <div class="form-group">
          <label>游戏目标</label>
          <textarea
            v-model="localConfig.gameGoal"
            class="sci-input"
            rows="5"
            placeholder="描述你希望AI达成的游戏目标..."
          ></textarea>
        </div>

        <div class="form-group">
          <label>行事风格</label>
          <textarea
            v-model="localConfig.behaviorStyle"
            class="sci-input"
            rows="3"
            placeholder="描述AI的行为风格..."
          ></textarea>
        </div>

        <div class="form-group">
          <label>模型 (Model)</label>
          <input
            v-model="localConfig.model"
            class="sci-input"
            type="text"
            placeholder="例如: gpt-4o, claude-3-opus, deepseek-chat"
          />
        </div>

        <div class="form-group">
          <label>Base URL</label>
          <input
            v-model="localConfig.baseUrl"
            class="sci-input"
            type="text"
            placeholder="例如: https://api.openai.com/v1"
          />
        </div>

        <div class="form-group">
          <label>API Key</label>
          <input
            v-model="localConfig.apiKey"
            class="sci-input"
            type="password"
            placeholder="输入你的API密钥"
          />
        </div>

        <div class="form-group">
          <label class="checkbox-label">
            <input
              type="checkbox"
              v-model="localConfig.useBackendProxy"
              @change="handleProxyModeChange"
            />
            <span>使用后端代理模式</span>
          </label>
          <div class="mode-hint" v-if="!localConfig.useBackendProxy">
            前端直连模式：API Key 仅存储在浏览器本地，但需要 API 支持 CORS 跨域
          </div>
          <div class="mode-warning" v-else>
            后端代理模式：API Key 会经过游戏服务器转发（不会被存储），建议仅用于中转站的临时 Key，避免使用高价值 Key
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <button class="sci-button" @click="$emit('close')">取消</button>
        <button class="sci-button primary" @click="handleSave">保存</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAgentStore } from '../../stores/agentStore'

const emit = defineEmits(['close'])
const agentStore = useAgentStore()

const localConfig = ref({
  gameGoal: '',
  behaviorStyle: '',
  model: '',
  baseUrl: '',
  apiKey: '',
  useBackendProxy: false
})

onMounted(() => {
  // 加载现有配置
  localConfig.value = { ...agentStore.config }
})

function handleProxyModeChange() {
  if (localConfig.value.useBackendProxy) {
    const confirmed = confirm(
      '警告：开启后端代理模式后，你的 API Key 将会被发送到游戏服务器进行转发。\n\n' +
      '虽然服务器不会存储你的 Key，但仍存在泄露风险。\n\n' +
      '建议：仅使用中转站提供的临时 Key，不要使用高价值的正式 Key。\n\n' +
      '确定要开启后端代理模式吗？'
    )
    if (!confirmed) {
      localConfig.value.useBackendProxy = false
    }
  }
}

function handleSave() {
  agentStore.saveConfig(localConfig.value)
  emit('close')
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 500px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
}

.modal-header h3 {
  margin: 0;
  color: var(--primary);
  font-size: 16px;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:hover {
  color: var(--text-primary);
}

.modal-body {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.form-group input[type="text"],
.form-group input[type="password"],
.form-group textarea {
  width: 100%;
  box-sizing: border-box;
}

.form-group textarea {
  resize: vertical;
  min-height: 60px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.mode-hint {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(0, 150, 136, 0.1);
  border: 1px solid rgba(0, 150, 136, 0.3);
  border-radius: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.mode-warning {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(255, 152, 0, 0.15);
  border: 1px solid rgba(255, 152, 0, 0.5);
  border-radius: 4px;
  font-size: 12px;
  color: #ffb74d;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid var(--border-color);
}
</style>
