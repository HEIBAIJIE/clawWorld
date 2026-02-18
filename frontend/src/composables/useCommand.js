import { useSessionStore } from '../stores/sessionStore'
import { useLogStore } from '../stores/logStore'
import { usePlayerStore } from '../stores/playerStore'
import { useMapStore } from '../stores/mapStore'
import { usePartyStore } from '../stores/partyStore'
import { useCombatStore } from '../stores/combatStore'
import { useUIStore } from '../stores/uiStore'
import { gameApi } from '../api/game'
import { parseLogText, groupLogsByType, extractBySubType } from '../parsers/logParser'
import { parseMapGrid, parseEntityList, parseMapInfo, parseMoveToInteract } from '../parsers/mapParser'
import { parsePlayerState, parseSkills, parseEquipment, parseInventory, parsePartyInfo } from '../parsers/playerParser'
import { parseFactions, parseCharacterStatus, parseActionBar, parseCombatStatus } from '../parsers/combatParser'

/**
 * 指令发送和响应处理的composable
 */
export function useCommand() {
  const sessionStore = useSessionStore()
  const logStore = useLogStore()
  const playerStore = usePlayerStore()
  const mapStore = useMapStore()
  const partyStore = usePartyStore()
  const combatStore = useCombatStore()
  const uiStore = useUIStore()

  /**
   * 发送指令
   * @param {string} command - 指令文本
   * @returns {Promise<object>} 响应结果
   */
  async function sendCommand(command) {
    if (!command.trim() || sessionStore.isWaiting) {
      return { success: false, message: '无效指令或正在等待响应' }
    }

    sessionStore.isWaiting = true
    logStore.addUserInput(command)

    try {
      const response = await gameApi.executeCommand(sessionStore.sessionId, command)

      if (response.data.response) {
        logStore.appendRawText('\n' + response.data.response)
        // 解析响应并更新stores
        processResponse(response.data.response)
      }

      return { success: true, data: response.data }
    } catch (error) {
      const errorMsg = error.response?.data?.response || error.message || '网络错误'
      logStore.appendRawText('\n' + errorMsg)
      return { success: false, message: errorMsg }
    } finally {
      sessionStore.isWaiting = false
    }
  }

  /**
   * 处理服务端响应，更新各个store
   * @param {string} responseText - 响应文本
   */
  function processResponse(responseText) {
    const entries = parseLogText(responseText)
    const grouped = groupLogsByType(entries)

    // 处理窗口类型的日志（现在每个entry的content已经包含完整的多行内容）
    for (const entry of grouped.window) {
      processWindowEntry(entry)
    }

    // 处理状态类型的日志
    for (const entry of grouped.state) {
      processStateEntry(entry)
    }
  }

  /**
   * 处理窗口类型的日志条目
   */
  function processWindowEntry(entry) {
    const { subType, content } = entry

    switch (subType) {
      case '地图窗口':
        mapStore.setWindowType('map')
        processMapWindowContent(content)
        break
      case '战斗窗口':
        mapStore.setWindowType('combat')
        combatStore.updateCombatState({ isInCombat: true })
        break
      case '参战方':
        const factions = parseFactions(content)
        combatStore.updateCombatState({ factions })
        break
      case '角色状态':
        const characters = parseCharacterStatus(content)
        combatStore.updateCombatState({ characters })
        break
      case '行动条':
        const actionBar = parseActionBar(content)
        combatStore.updateCombatState({ actionBar })
        break
      case '当前状态':
        const status = parseCombatStatus(content)
        combatStore.updateCombatState({
          isMyTurn: status.isMyTurn,
          currentTurn: status.waitingFor
        })
        break
    }
  }

  /**
   * 处理地图窗口内容
   */
  function processMapWindowContent(content) {
    // 地图信息
    if (content.includes('当前地图名')) {
      const mapInfo = parseMapInfo(content)
      mapStore.updateMapInfo(mapInfo)
    }

    // 地图网格
    if (content.match(/\(\d+,\d+\)/)) {
      // 检查是否是地图网格（包含多个坐标）
      const coordCount = (content.match(/\(\d+,\d+\)/g) || []).length
      if (coordCount > 5) {
        const { grid, width, height } = parseMapGrid(content)
        mapStore.updateGrid(grid)
        mapStore.updateMapInfo({ width, height })
      }
    }

    // 玩家状态（自己的状态）
    if (content.includes('你的状态')) {
      const playerState = parsePlayerState(content)
      playerStore.updateFromParsed(playerState)
    }
    // 查看其他角色（包含"角色:"但不包含"你的状态"）
    else if ((content.includes('角色:') || content.includes('角色：')) && !content.includes('你的状态')) {
      const characterData = parsePlayerState(content)
      // 检查是否是查看自己（名字相同）
      if (characterData.name && characterData.name !== playerStore.name) {
        characterData.rawText = content
        uiStore.openInspectCharacter(characterData)
      } else if (characterData.name === playerStore.name) {
        // 更新自己的状态
        playerStore.updateFromParsed(characterData)
      }
    }

    // 技能列表
    if (content.includes('你的技能') || content.includes('[敌方单体]') || content.includes('[我方单体]') || content.includes('[敌方全体]') || content.includes('[我方全体]')) {
      const skills = parseSkills(content)
      if (skills.length > 0) {
        playerStore.updateFromParsed({ skills })
      }
    }

    // 装备
    if (content.includes('你的装备') || content.includes('头部:') || content.includes('头部：')) {
      const equipment = parseEquipment(content)
      playerStore.updateFromParsed({ equipment })
    }

    // 背包
    if (content.includes('你的背包') || content.includes('背包为空') || content.match(/x\d+/)) {
      const inventory = parseInventory(content)
      playerStore.updateFromParsed({ inventory })
    }

    // 队伍信息
    if (content.includes('队伍成员') || content.includes('你是队长') || content.includes('你在队伍中')) {
      const partyInfo = parsePartyInfo(content)
      partyStore.updatePartyInfo(partyInfo)
    }

    // 实体列表
    if (content.includes('[类型：') || content.includes('[类型:') || content.includes('地图实体')) {
      const entities = parseEntityList(content)
      if (entities.length > 0) {
        // 计算与玩家的距离
        entities.forEach(entity => {
          entity.distance = mapStore.getDistance(playerStore.x, playerStore.y, entity.x, entity.y)
        })
        mapStore.updateEntities(entities)
      }
    }

    // 可移动交互
    if (content.includes('移动到') && content.includes('可交互')) {
      const moveToInteract = parseMoveToInteract(content)
      // 更新实体的移动目标
      moveToInteract.forEach(info => {
        mapStore.updateEntity(info.name, {
          moveToX: info.moveToX,
          moveToY: info.moveToY
        })
      })
    }

    // 查看物品（包含物品详情关键字）
    if (content.includes('物品详情') || content.includes('装备详情') ||
        (content.includes('类型:') && (content.includes('攻击') || content.includes('防御') || content.includes('效果')))) {
      const itemData = parseItemDetails(content)
      if (itemData.name) {
        uiStore.openInspectItem(itemData)
      }
    }
  }

  /**
   * 解析物品详情
   */
  function parseItemDetails(content) {
    const result = { rawText: content }
    const lines = content.split('\n')

    // 第一行通常是物品名称
    if (lines.length > 0) {
      const firstLine = lines[0].trim()
      if (firstLine && !firstLine.includes(':') && !firstLine.includes('：')) {
        result.name = firstLine
      }
    }

    // 解析类型
    const typeMatch = content.match(/类型[：:]\s*(.+)/)
    if (typeMatch) {
      result.type = typeMatch[1].trim()
    }

    // 解析描述
    const descMatch = content.match(/描述[：:]\s*(.+)/)
    if (descMatch) {
      result.description = descMatch[1].trim()
    }

    // 解析属性
    result.stats = {}
    const statPatterns = [
      { pattern: /物理攻击[：:]\s*\+?(\d+)/, key: 'physicalAttack' },
      { pattern: /物理防御[：:]\s*\+?(\d+)/, key: 'physicalDefense' },
      { pattern: /魔法攻击[：:]\s*\+?(\d+)/, key: 'magicAttack' },
      { pattern: /魔法防御[：:]\s*\+?(\d+)/, key: 'magicDefense' },
      { pattern: /生命[：:]\s*\+?(\d+)/, key: 'health' },
      { pattern: /法力[：:]\s*\+?(\d+)/, key: 'mana' },
      { pattern: /力量[：:]\s*\+?(\d+)/, key: 'strength' },
      { pattern: /敏捷[：:]\s*\+?(\d+)/, key: 'agility' },
      { pattern: /智力[：:]\s*\+?(\d+)/, key: 'intelligence' },
      { pattern: /体力[：:]\s*\+?(\d+)/, key: 'vitality' },
      { pattern: /速度[：:]\s*\+?(\d+)/, key: 'speed' }
    ]

    for (const { pattern, key } of statPatterns) {
      const match = content.match(pattern)
      if (match) {
        result.stats[key] = parseInt(match[1])
      }
    }

    // 解析等级要求
    const levelReqMatch = content.match(/等级要求[：:]\s*(\d+)/)
    if (levelReqMatch) {
      result.requirements = result.requirements || {}
      result.requirements.level = parseInt(levelReqMatch[1])
    }

    return result
  }

  /**
   * 处理状态类型的日志条目
   */
  function processStateEntry(entry) {
    const { subType, content } = entry

    switch (subType) {
      case '窗口变化':
        if (content.includes('地图窗口')) {
          mapStore.setWindowType('map')
          combatStore.reset()
        } else if (content.includes('战斗窗口')) {
          mapStore.setWindowType('combat')
        }
        break
      case '指令响应':
        // 处理移动完成响应
        const moveMatch = content.match(/移动完成，当前位置[：:]\s*\((\d+),\s*(\d+)\)/)
        if (moveMatch) {
          playerStore.updateFromParsed({
            x: parseInt(moveMatch[1]),
            y: parseInt(moveMatch[2])
          })
        }

        // 处理加点响应
        if (content.includes('添加属性点成功')) {
          const playerState = parsePlayerState(content)
          playerStore.updateFromParsed(playerState)
        }
        break
      case '战斗日志':
        // 战斗日志已在战斗窗口处理
        break
    }
  }

  /**
   * 快捷指令：移动
   */
  function move(x, y) {
    return sendCommand(`move ${x} ${y}`)
  }

  /**
   * 快捷指令：交互
   */
  function interact(targetName, option) {
    return sendCommand(`interact ${targetName} ${option}`)
  }

  /**
   * 快捷指令：使用物品
   */
  function useItem(itemName) {
    return sendCommand(`use ${itemName}`)
  }

  /**
   * 快捷指令：装备
   */
  function equip(itemName) {
    return sendCommand(`equip ${itemName}`)
  }

  /**
   * 快捷指令：加点
   */
  function addAttribute(attr, amount = 1) {
    return sendCommand(`attribute add ${attr} ${amount}`)
  }

  /**
   * 快捷指令：释放技能
   */
  function cast(skillName, targetName = null) {
    if (targetName) {
      return sendCommand(`cast ${skillName} ${targetName}`)
    }
    return sendCommand(`cast ${skillName}`)
  }

  /**
   * 快捷指令：队伍操作
   */
  function partyKick(playerName) {
    return sendCommand(`party kick ${playerName}`)
  }

  function partyEnd() {
    return sendCommand('party end')
  }

  function partyLeave() {
    return sendCommand('party leave')
  }

  return {
    sendCommand,
    processResponse,
    // 快捷指令
    move,
    interact,
    useItem,
    equip,
    addAttribute,
    cast,
    partyKick,
    partyEnd,
    partyLeave
  }
}
