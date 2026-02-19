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
    return `你是一位游戏玩家，正在玩一款名为ClawWorld的文字MMORPG游戏。ClawWorld致力于打造一个人类与AI智能体平等游玩的虚拟世界。

## 你的游戏目标
${agentStore.config.gameGoal}

## 你的行事风格
${agentStore.config.behaviorStyle}

## 游戏机制
- 2D网格地图，分为安全地图和战斗地图。每个格子有坐标。
- 地图由默认地形 + 特殊地形（有些不可通过）构成。地图实体位于地形之上(敌人/NPC/其他玩家/传送点/篝火等)。敌人在被击败前不可通过，其他实体可通过。
- CTB条件回合制战斗（跑条），速度决定出手频率，基本伤害=攻击-防御，基准暴击伤害150%，闪避几率=命中-闪避
- 可与其他玩家组队（最多4人），同一地图内队友共同战斗，胜利时获得全额经验，金钱平分，装备归队长由队长再分配，发起组队者为队长. 同级BOSS一般合理组队才能击败。
- 玩家之间可以聊天、交易、组队，利用好这一点达成目标。在尝试发起交互后，可以等待几秒看看对方是否理你。
- 玩家之间可以在战斗地图互相攻击，支持多队混战（最多4队），但没有直接收益。
- 你可以攻击正在交战中的敌人，与其他玩家抢怪。但地图推荐等级也是保护等级。你无法攻击保护等级及以下的玩家正在打的怪，除非他与超过保护等级的玩家组队。
- 你的等级超过地图保护等级时，在该地图死亡掉落10%当前经验并返回最近安全地图。
- 战斗胜利不会回复状态，高价值的敌人往往在远方，周围还有玩家虎视眈眈，出发前请做好战备

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
   * @returns {{ success: boolean, thinking: string, command: string, raw: string }}
   */
  function parseResponse(responseText) {
    try {
      // 尝试直接解析JSON
      const parsed = JSON.parse(responseText)
      if (parsed.command && typeof parsed.command === 'string') {
        return {
          success: true,
          thinking: parsed.thinking || '',
          command: parsed.command,
          raw: responseText
        }
      }
    } catch (e) {
      // 尝试从文本中提取JSON
      const jsonMatch = responseText.match(/\{[\s\S]*\}/)
      if (jsonMatch) {
        try {
          const parsed = JSON.parse(jsonMatch[0])
          if (parsed.command && typeof parsed.command === 'string') {
            return {
              success: true,
              thinking: parsed.thinking || '',
              command: parsed.command,
              raw: responseText
            }
          }
        } catch (e2) {
          // 继续尝试其他方式
        }
      }
      // 尝试提取command字段
      const commandMatch = responseText.match(/"command"\s*:\s*"([^"]+)"/)
      if (commandMatch) {
        const thinkingMatch = responseText.match(/"thinking"\s*:\s*"([^"]*)"/)
        return {
          success: true,
          thinking: thinkingMatch ? thinkingMatch[1] : '',
          command: commandMatch[1],
          raw: responseText
        }
      }
    }
    // 解析失败
    console.error('[Agent] 无法解析响应:', responseText)
    return {
      success: false,
      thinking: '',
      command: '',
      raw: responseText
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
        const parseResult = parseResponse(llmResponse)
        console.log('[Agent] 解析结果:', parseResult)

        // 记录大模型的响应
        agentStore.addMessage('assistant', llmResponse)

        // 检查解析是否成功
        if (!parseResult.success) {
          console.warn('[Agent] 响应格式错误，要求大模型重新回答')
          // 告诉大模型格式有问题
          agentStore.addMessage('user',
            `你的上一条响应格式不正确，无法解析。你的响应是：\n${llmResponse}\n\n` +
            `请严格按照JSON格式响应：{"thinking":"简短思考","command":"指令"}\n` +
            `不要添加任何额外的文字、解释或markdown代码块。`
          )
          // 继续循环，让大模型重新回答
          await new Promise(resolve => setTimeout(resolve, 500))
          continue
        }

        // 更新思考内容显示
        agentStore.setThinking(parseResult.thinking)

        agentStore.isThinking = false

        if (!agentStore.isEnabled) {
          console.log('[Agent] 代理已关闭，停止循环')
          break
        }

        // 发送指令到游戏服务器
        await sendCommand(parseResult.command)

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
