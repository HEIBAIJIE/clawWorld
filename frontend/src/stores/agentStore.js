import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAgentStore = defineStore('agent', () => {
  // 智能代理开关
  const isEnabled = ref(false)

  // 配置项
  const config = ref({
    gameGoal: `作为玩家，你的目标是：
- 通过战斗提升等级和战斗力
- 获取更好的装备（稀有度：普通<优秀<稀有<史诗<传说<神话）
- 积累金币购买物品
- 探索地图，挑战更强的敌人
- 可选择与其他玩家组队`,
    behaviorStyle: `谨慎而有策略。优先确保生存，在血量充足时积极战斗。遇到强敌时考虑撤退或寻求组队。合理使用技能和物品，不浪费资源。`,
    baseUrl: '',
    apiKey: '',
    model: 'gpt-4o',
    useBackendProxy: false  // 是否使用后端代理模式
  })

  // 对话历史（发给大模型的上下文）
  const conversationHistory = ref([])

  // 战斗中缓存的日志（等待轮到自己时再发送）
  const pendingCombatLogs = ref([])

  // 是否正在等待大模型响应
  const isThinking = ref(false)

  // 上一次的思考内容（用于显示）
  const lastThinking = ref('')

  // 配置是否完整
  const isConfigured = computed(() => {
    return config.value.gameGoal.trim() !== '' &&
           config.value.behaviorStyle.trim() !== '' &&
           config.value.baseUrl.trim() !== '' &&
           config.value.apiKey.trim() !== '' &&
           config.value.model.trim() !== ''
  })

  // 从localStorage恢复配置
  function restoreConfig() {
    const stored = localStorage.getItem('agentConfig')
    if (stored) {
      try {
        const parsed = JSON.parse(stored)
        config.value = { ...config.value, ...parsed }
        console.log('[AgentStore] 配置已恢复')
      } catch (e) {
        console.error('[AgentStore] 配置恢复失败:', e)
      }
    }
  }

  // 保存配置到localStorage
  function saveConfig(newConfig) {
    config.value = { ...config.value, ...newConfig }
    localStorage.setItem('agentConfig', JSON.stringify(config.value))
    console.log('[AgentStore] 配置已保存')
  }

  // 开启智能代理
  function enable() {
    if (!isConfigured.value) {
      return false
    }
    isEnabled.value = true
    conversationHistory.value = []
    pendingCombatLogs.value = []
    console.log('[AgentStore] 智能代理已开启')
    return true
  }

  // 关闭智能代理
  function disable() {
    isEnabled.value = false
    isThinking.value = false
    lastThinking.value = ''
    console.log('[AgentStore] 智能代理已关闭')
  }

  // 设置思考内容
  function setThinking(thinking) {
    lastThinking.value = thinking
  }

  // 添加对话历史
  function addMessage(role, content) {
    conversationHistory.value.push({ role, content })
    // 保留最近50条对话，防止上下文过长
    if (conversationHistory.value.length > 50) {
      // 保留第一条系统消息和最近49条
      const systemMsg = conversationHistory.value[0]
      conversationHistory.value = [systemMsg, ...conversationHistory.value.slice(-49)]
    }
  }

  // 清空对话历史
  function clearHistory() {
    conversationHistory.value = []
    pendingCombatLogs.value = []
  }

  // 添加待处理的战斗日志
  function addPendingCombatLog(log) {
    pendingCombatLogs.value.push(log)
  }

  // 获取并清空待处理的战斗日志
  function flushPendingCombatLogs() {
    const logs = [...pendingCombatLogs.value]
    pendingCombatLogs.value = []
    return logs
  }

  return {
    isEnabled,
    config,
    conversationHistory,
    pendingCombatLogs,
    isThinking,
    lastThinking,
    isConfigured,
    restoreConfig,
    saveConfig,
    enable,
    disable,
    setThinking,
    addMessage,
    clearHistory,
    addPendingCombatLog,
    flushPendingCombatLogs
  }
})
