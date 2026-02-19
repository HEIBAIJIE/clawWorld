import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { estimateTokenCount, formatTokenCount } from '../utils/tokenCounter'
import { useAgentStore } from './agentStore'

export const useLogStore = defineStore('log', () => {
  // 原始日志文本
  const rawText = ref('')

  // 解析后的日志条目
  const entries = ref([])

  // 文本窗口的Token计数（仅供参考）
  const textTokenCount = computed(() => estimateTokenCount(rawText.value))

  // 实际发送给LLM的Token计数（更准确）
  const tokenCount = computed(() => {
    const agentStore = useAgentStore()
    // 如果agent已启用且有对话历史，使用实际的token计数
    if (agentStore.isEnabled && agentStore.conversationHistory.length > 0) {
      return agentStore.actualTokenCount
    }
    // 否则使用文本窗口的估算
    return textTokenCount.value
  })

  const formattedTokenCount = computed(() => formatTokenCount(tokenCount.value))

  // 添加原始文本
  function appendRawText(text) {
    console.log('[LogStore] 追加日志文本，长度:', text.length)
    if (rawText.value) {
      rawText.value += '\n' + text
    } else {
      rawText.value = text
    }
  }

  // 设置原始文本
  function setRawText(text) {
    console.log('[LogStore] 设置日志文本，长度:', text.length)
    rawText.value = text
  }

  // 添加日志条目
  function addEntry(entry) {
    entries.value.push({
      ...entry,
      id: Date.now() + Math.random()
    })
    // 保留最近500条
    if (entries.value.length > 500) {
      entries.value.shift()
    }
  }

  // 添加用户输入
  function addUserInput(command) {
    console.log('[LogStore] 添加用户输入:', command)
    appendRawText('\n> ' + command)
    addEntry({
      type: 'user-input',
      content: command,
      timestamp: Date.now()
    })
  }

  // 清空日志
  function clear() {
    console.log('[LogStore] 清空日志')
    rawText.value = ''
    entries.value = []
  }

  return {
    rawText,
    entries,
    tokenCount,
    formattedTokenCount,
    appendRawText,
    setRawText,
    addEntry,
    addUserInput,
    clear
  }
})
