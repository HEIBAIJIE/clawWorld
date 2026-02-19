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

## 你的游戏目标
${agentStore.config.gameGoal}

## 你的行事风格
${agentStore.config.behaviorStyle}

## 游戏机制
- 2D网格地图，分为安全地图（无战斗）和战斗地图（有敌人）
- 回合制战斗系统（CTB跑条），速度决定行动顺序
- 四大职业：战士、游侠、法师、牧师
- 可组队（最多4人）、交易、学习技能、装备物品
- 通过战斗获得经验、金币和装备
- 装备稀有度：普通<优秀<稀有<史诗<传说<神话

## 指令手册
1、地图窗口：
- move [x] [y] - 移动到坐标
- interact [目标名称] [选项] - 与实体交互（选项在服务器响应中列出）
- use [物品名称] - 使用消耗品/技能书
- equip [装备名称] - 装备物品
- attribute add [str/agi/int/vit] [数量] - 分配属性点
- say [world/map/party] [消息] - 聊天
- say to [玩家名称] [消息] - 私聊
- wait [秒数] - 等待（最多60秒，用于等待其他玩家响应）

2、战斗窗口：
- cast [技能名称] - 释放非指向技能
- cast [技能名称] [目标名称] - 释放指向技能
- use [物品名称] - 使用物品
- wait - 跳过回合
- end - 撤退（仅PVE可用）

3、交易窗口：
- trade add/remove [物品名称] - 添加/移除物品
- trade money [金额] - 设置金额
- trade lock/unlock - 锁定/解锁
- trade confirm - 确认交易
- trade end - 取消交易

4、商店窗口：
- shop buy/sell [物品名称] [数量] - 买卖商品
- shop leave - 离开商店

## 响应格式要求
你必须严格按照以下JSON格式响应，不要添加任何额外文字：
{"thinking":"简短思考","command":"指令"}

示例：
{"thinking":"血量充足，继续攻击史莱姆","command":"cast 普通攻击 史莱姆#1"}
{"thinking":"需要移动到传送点","command":"move 3 5"}`
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
