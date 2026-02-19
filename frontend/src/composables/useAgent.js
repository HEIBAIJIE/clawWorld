import { useAgentStore } from '../stores/agentStore'
import { useLogStore } from '../stores/logStore'
import { useSessionStore } from '../stores/sessionStore'
import { useCommand } from './useCommand'
import { gameApi } from '../api/game'

/**
 * 智能代理composable - 处理与大模型的交互
 */
export function useAgent() {
  const agentStore = useAgentStore()
  const logStore = useLogStore()
  const sessionStore = useSessionStore()
  const { sendCommand } = useCommand()

  /**
   * 构建系统提示词
   */
  function buildSystemPrompt() {
    return `你是一个游戏AI代理，正在玩一款名为ClawWorld的文字MMORPG游戏。

## 游戏目标
${agentStore.config.gameGoal}

## 行事风格
${agentStore.config.behaviorStyle}

## 游戏规则
1. 你需要通过发送指令来控制角色
2. 每次只能发送一条指令
3. 指令格式示例：
   - 移动：move 5 3
   - 攻击：interact 史莱姆#1 攻击
   - 查看：interact 小小 查看
   - 使用物品：use 小型生命药水
   - 装备：equip 铁剑
   - 释放技能：cast 火球术 史莱姆#1
   - 等待：wait
   - 撤退：end

## 响应格式
你必须严格按照以下JSON格式响应：
{
  "thinking": "你的思考过程（简短）",
  "command": "要执行的指令"
}

注意：
- command字段必须是一个有效的游戏指令
- 不要添加任何额外的文字或解释
- 如果不确定该做什么，可以使用 wait 指令`
  }

  /**
   * 调用大模型API（前端直连模式）
   */
  async function callLLMDirect(messages) {
    const { baseUrl, apiKey, model } = agentStore.config

    const response = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${apiKey}`
      },
      body: JSON.stringify({
        model: model,
        messages: messages,
        temperature: 0.7,
        max_tokens: 500
      })
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`API请求失败: ${response.status} - ${errorText}`)
    }

    const data = await response.json()
    return data.choices[0].message.content
  }

  /**
   * 调用大模型API（后端代理模式）
   */
  async function callLLMProxy(messages) {
    const { baseUrl, apiKey, model } = agentStore.config

    const response = await gameApi.proxyAgentChat({
      baseUrl,
      apiKey,
      model,
      messages,
      temperature: 0.7,
      maxTokens: 500
    })

    return response.data.choices[0].message.content
  }

  /**
   * 调用大模型API（根据配置选择模式）
   */
  async function callLLM(messages) {
    try {
      if (agentStore.config.useBackendProxy) {
        console.log('[Agent] 使用后端代理模式')
        return await callLLMProxy(messages)
      } else {
        console.log('[Agent] 使用前端直连模式')
        return await callLLMDirect(messages)
      }
    } catch (error) {
      console.error('[Agent] LLM调用失败:', error)
      throw error
    }
  }

  /**
   * 解析大模型响应，提取指令
   */
  function parseResponse(responseText) {
    try {
      // 尝试直接解析JSON
      const parsed = JSON.parse(responseText)
      return {
        thinking: parsed.thinking || '',
        command: parsed.command || 'wait'
      }
    } catch (e) {
      // 尝试从文本中提取JSON
      const jsonMatch = responseText.match(/\{[\s\S]*\}/)
      if (jsonMatch) {
        try {
          const parsed = JSON.parse(jsonMatch[0])
          return {
            thinking: parsed.thinking || '',
            command: parsed.command || 'wait'
          }
        } catch (e2) {
          console.error('[Agent] JSON解析失败:', e2)
        }
      }
      // 如果都失败了，尝试提取command
      const commandMatch = responseText.match(/"command"\s*:\s*"([^"]+)"/)
      if (commandMatch) {
        return { thinking: '', command: commandMatch[1] }
      }
      console.error('[Agent] 无法解析响应:', responseText)
      return { thinking: '', command: 'wait' }
    }
  }

  /**
   * 检查是否需要等待（战斗中不是自己的回合）
   * 这种情况下前端会自动发送wait，不需要调用LLM
   */
  function shouldSkipLLM(serverResponse) {
    return serverResponse.includes('未轮到你的回合，请输入wait继续等待')
  }

  /**
   * 检查是否轮到自己行动
   */
  function isMyTurn(serverResponse) {
    return serverResponse.includes('★ 轮到你的回合！请选择行动')
  }

  /**
   * 检查战斗是否结束
   */
  function isCombatEnded(serverResponse) {
    return serverResponse.includes('战斗结束') ||
           serverResponse.includes('获得胜利') ||
           serverResponse.includes('战斗失败') ||
           serverResponse.includes('切换到地图窗口')
  }

  /**
   * 获取最近的服务器响应（从上次发送指令后的内容）
   */
  function getRecentResponse() {
    const lines = logStore.rawText.split('\n')
    // 获取最近100行作为上下文
    return lines.slice(-100).join('\n')
  }

  /**
   * 启动智能代理循环
   * @param {string} initialContext - 初始上下文（当前文本窗口内容）
   */
  async function startAgentLoop(initialContext) {
    if (!agentStore.isEnabled || !agentStore.isConfigured) {
      console.warn('[Agent] 代理未启用或未配置')
      return
    }

    // 初始化对话历史
    agentStore.clearHistory()

    // 添加系统消息
    const systemPrompt = buildSystemPrompt()
    agentStore.addMessage('system', systemPrompt)

    // 添加初始上下文作为第一条用户消息
    agentStore.addMessage('user', `当前游戏状态：\n${initialContext}`)

    // 开始循环
    await agentLoop()
  }

  /**
   * 智能代理主循环
   */
  async function agentLoop() {
    while (agentStore.isEnabled) {
      try {
        // 等待之前的请求完成
        while (sessionStore.isWaiting) {
          await new Promise(resolve => setTimeout(resolve, 100))
          if (!agentStore.isEnabled) return
        }

        // 获取当前服务器响应
        const currentResponse = getRecentResponse()

        // 检查是否需要跳过LLM（战斗中等待对方行动）
        if (shouldSkipLLM(currentResponse)) {
          console.log('[Agent] 战斗中等待对方行动，缓存日志，跳过LLM调用')
          // 缓存当前响应
          agentStore.addPendingCombatLog(currentResponse)
          // 等待前端自动发送wait并获取新响应
          await new Promise(resolve => setTimeout(resolve, 500))
          continue
        }

        // 准备发送给LLM的上下文
        let contextToSend = currentResponse

        // 如果有缓存的战斗日志，合并它们
        const pendingLogs = agentStore.flushPendingCombatLogs()
        if (pendingLogs.length > 0) {
          console.log('[Agent] 合并缓存的战斗日志，共', pendingLogs.length, '条')
          contextToSend = pendingLogs.join('\n---\n') + '\n---\n当前状态：\n' + currentResponse
        }

        // 更新对话历史（如果不是第一次循环）
        if (agentStore.conversationHistory.length > 2) {
          agentStore.addMessage('user', `服务器响应：\n${contextToSend}`)
        }

        agentStore.isThinking = true
        console.log('[Agent] 开始思考...')

        // 调用大模型
        const llmResponse = await callLLM(agentStore.conversationHistory)
        console.log('[Agent] 大模型响应:', llmResponse)

        // 解析响应
        const { thinking, command } = parseResponse(llmResponse)
        console.log('[Agent] 解析结果:', { thinking, command })

        // 记录大模型的响应
        agentStore.addMessage('assistant', llmResponse)

        agentStore.isThinking = false

        if (!agentStore.isEnabled) {
          console.log('[Agent] 代理已关闭，停止循环')
          break
        }

        // 发送指令到游戏服务器
        await sendCommand(command)

        // 短暂延迟，等待响应处理完成
        await new Promise(resolve => setTimeout(resolve, 1000))

      } catch (error) {
        console.error('[Agent] 循环出错:', error)
        agentStore.isThinking = false
        // 出错后等待一段时间再重试
        await new Promise(resolve => setTimeout(resolve, 3000))
      }
    }
  }

  return {
    startAgentLoop,
    callLLM,
    parseResponse
  }
}
