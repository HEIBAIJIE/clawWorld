import { useSessionStore } from '../stores/sessionStore'
import { useLogStore } from '../stores/logStore'
import { usePlayerStore } from '../stores/playerStore'
import { useMapStore } from '../stores/mapStore'
import { usePartyStore } from '../stores/partyStore'
import { useCombatStore } from '../stores/combatStore'
import { useTradeStore } from '../stores/tradeStore'
import { useShopStore } from '../stores/shopStore'
import { useRegisterStore } from '../stores/registerStore'
import { useUIStore } from '../stores/uiStore'
import { useAgentStore } from '../stores/agentStore'
import { gameApi } from '../api/game'
import { parseLogText, groupLogsByType, extractBySubType } from '../parsers/logParser'
import { parseSpecialTerrain, buildMapGrid, parseEntityList, parseMapInfo, parseMoveToInteract } from '../parsers/mapParser'
import { parsePlayerState, parseSkills, parseEquipment, parseInventory, parsePartyInfo } from '../parsers/playerParser'
import {
  parseFactions, parseCharacterStatus, parseActionBar, parseCombatStatus,
  parseSkillList, parseCombatAction, parseCommandResponse, needsAutoWait
} from '../parsers/combatParser'

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
  const tradeStore = useTradeStore()
  const shopStore = useShopStore()
  const registerStore = useRegisterStore()
  const uiStore = useUIStore()
  const agentStore = useAgentStore()

  /**
   * 发送指令
   * @param {string} command - 指令文本
   * @returns {Promise<object>} 响应结果
   */
  async function sendCommand(command) {
    if (!command.trim() || sessionStore.isWaiting) {
      console.warn('[Command] 指令被拒绝:', { command, isWaiting: sessionStore.isWaiting })
      return { success: false, message: '无效指令或正在等待响应' }
    }

    console.log('[Command] 发送指令:', command)
    sessionStore.isWaiting = true
    logStore.addUserInput(command)

    try {
      const response = await gameApi.executeCommand(sessionStore.sessionId, command)

      if (response.data.response) {
        console.log('[Command] 收到响应，长度:', response.data.response.length)
        logStore.appendRawText('\n' + response.data.response)
        // 解析响应并更新stores
        processResponse(response.data.response)
      }

      return { success: true, data: response.data }
    } catch (error) {
      const errorMsg = error.response?.data?.response || error.message || '网络错误'
      console.error('[Command] 指令执行失败:', errorMsg)
      logStore.appendRawText('\n' + errorMsg)
      // 即使指令失败（如400错误），也要解析响应内容
      // 因为服务端可能在错误响应中返回了窗口切换等重要信息
      if (error.response?.data?.response) {
        console.log('[Command] 解析错误响应中的内容')
        processResponse(error.response.data.response)
      }
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
    console.log('[Command] 开始解析响应')
    const entries = parseLogText(responseText)
    const grouped = groupLogsByType(entries)
    console.log('[Command] 解析结果:', {
      window: grouped.window.length,
      state: grouped.state.length,
      background: grouped.background.length,
      other: grouped.other.length
    })

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
   * 根据后端的 subType 精确匹配不同类型的内容
   */
  function processWindowEntry(entry) {
    const { subType, content, timestamp } = entry
    console.log('[Command] 处理窗口条目:', subType, '内容长度:', content.length, '时间戳:', timestamp)

    switch (subType) {
      // ===== 地图窗口相关 =====
      case '地图信息':
        mapStore.setWindowType('map')
        const mapInfo = parseMapInfo(content)
        mapStore.updateMapInfo(mapInfo)
        // 如果有尺寸和默认地形信息，构建初始网格
        if (mapInfo.width && mapInfo.height) {
          const { grid } = buildMapGrid(mapInfo.width, mapInfo.height, mapInfo.defaultTerrain, mapStore.specialTerrains)
          mapStore.updateGrid(grid)
        }
        break

      case '特殊地形':
        mapStore.setWindowType('map')
        const specialTerrains = parseSpecialTerrain(content)
        mapStore.updateSpecialTerrains(specialTerrains)
        // 重新构建网格
        if (mapStore.width && mapStore.height) {
          const { grid } = buildMapGrid(mapStore.width, mapStore.height, mapStore.defaultTerrain, specialTerrains)
          mapStore.updateGrid(grid)
        }
        break

      case '地图网格':
        // 兼容旧格式（如果后端还返回地图网格）
        mapStore.setWindowType('map')
        break

      case '玩家状态':
        const playerState = parsePlayerState(content)
        playerStore.updateFromParsed(playerState)
        break

      case '技能列表':
        const skills = parseSkills(content)
        if (skills.length > 0) {
          playerStore.updateFromParsed({ skills })
        }
        // 同时更新战斗技能列表
        const combatSkills = parseSkillList(content)
        if (combatSkills.length > 0) {
          combatStore.updateCombatState({ mySkills: combatSkills })
        }
        break

      case '装备栏':
        const equipment = parseEquipment(content)
        playerStore.updateFromParsed({ equipment })
        break

      case '背包':
        const inventory = parseInventory(content)
        playerStore.updateFromParsed({ inventory })
        break

      case '队伍信息':
        if (content.includes('队伍成员') || content.includes('你是队长') || content.includes('你在队伍中')) {
          const partyInfo = parsePartyInfo(content)
          partyStore.updatePartyInfo(partyInfo)
        } else if (content.includes('没有队伍')) {
          partyStore.reset()
        }
        break

      case '实体列表':
        const entities = parseEntityList(content)
        if (entities.length > 0) {
          entities.forEach(entity => {
            entity.distance = mapStore.getDistance(playerStore.x, playerStore.y, entity.x, entity.y)
          })
          mapStore.updateEntities(entities)
        }
        break

      case '可达目标':
        const moveToInteract = parseMoveToInteract(content)
        moveToInteract.forEach(info => {
          mapStore.updateEntity(info.name, {
            moveToX: info.moveToX,
            moveToY: info.moveToY
          })
        })
        break

      case '聊天记录':
        // 聊天记录由 chatStore 处理，这里暂不处理
        break

      case '可用指令':
        // 可用指令暂不处理
        break

      // ===== 战斗窗口相关 =====
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
        // 先找到自己的名字
        const selfChar = characters.find(c => c.isSelf)
        // 同时更新角色和自己的名字，确保 myFaction 能正确计算
        if (selfChar) {
          combatStore.updateCombatState({ characters, myName: selfChar.name })
        } else {
          combatStore.updateCombatState({ characters })
        }
        break

      case '行动条':
        const actionBar = parseActionBar(content)
        combatStore.updateCombatState({ actionBar })
        break

      case '当前状态':
        const status = parseCombatStatus(content)
        console.log('[Command] 战斗当前状态:', status, '时间戳:', timestamp)
        combatStore.updateCombatState({
          isMyTurn: status.isMyTurn,
          currentTurn: status.waitingFor
        })
        // 轮到自己回合时启动倒计时，传入服务端时间戳
        if (status.isMyTurn) {
          console.log('[Command] 启动倒计时，服务端时间戳:', timestamp)
          combatStore.startCountdown(timestamp || 0)
        }
        // 自动wait机制：需要等待对方行动时自动发送wait
        if (status.needsAutoWait) {
          console.log('[Command] 检测到需要自动wait')
          // 延迟发送，确保当前响应处理完毕且 isWaiting 已重置
          setTimeout(() => {
            if (!combatStore.isMyTurn && combatStore.isInCombat && !combatStore.showResult) {
              console.log('[Command] 执行自动wait')
              sendCommand('wait')
            }
          }, 150)
        }
        break

      // ===== 兼容旧格式（地图窗口） =====
      case '地图窗口':
        mapStore.setWindowType('map')
        processMapWindowContent(content)
        break

      // ===== 交易窗口相关 =====
      case '交易窗口':
        mapStore.setWindowType('trade')
        processTradeWindowContent(content)
        break

      // ===== 商店窗口相关 =====
      case '商店窗口':
        mapStore.setWindowType('shop')
        processShopWindowContent(content)
        break

      // ===== 注册窗口相关 =====
      case '注册窗口':
        mapStore.setWindowType('register')
        registerStore.openRegister()
        break

      case '职业选择':
        // 解析职业列表
        const roles = parseRoleSelection(content)
        if (roles.length > 0) {
          registerStore.updateRoles(roles)
        }
        break

      default:
        // 未知的 subType，尝试用旧的内容匹配方式处理
        processMapWindowContent(content)
    }
  }

  /**
   * 处理地图窗口内容（兼容旧格式，通过内容特征判断）
   */
  function processMapWindowContent(content) {
    // 地图信息
    if (content.includes('当前地图名')) {
      const mapInfo = parseMapInfo(content)
      mapStore.updateMapInfo(mapInfo)
    }

    // 玩家状态（自己的状态）
    if (content.includes('你的状态')) {
      const playerState = parsePlayerState(content)
      playerStore.updateFromParsed(playerState)
    }
    // 查看其他角色（包含"角色:"但不包含"你的状态"）
    else if ((content.includes('角色:') || content.includes('角色：')) && !content.includes('你的状态')) {
      const characterData = parsePlayerState(content)
      if (characterData.name && characterData.name !== playerStore.name) {
        characterData.rawText = content
        uiStore.openInspectCharacter(characterData)
      } else if (characterData.name === playerStore.name) {
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
    if (content.includes('你的背包') || content.includes('=== 背包 ===') || content.includes('背包为空')) {
      // 提取背包部分内容
      let inventoryContent = content
      if (content.includes('=== 背包 ===')) {
        const startIndex = content.indexOf('=== 背包 ===')
        const endIndex = content.indexOf('===', startIndex + 10)
        if (endIndex > startIndex) {
          inventoryContent = content.substring(startIndex, endIndex)
        } else {
          inventoryContent = content.substring(startIndex)
        }
      }
      const inventory = parseInventory(inventoryContent)
      playerStore.updateFromParsed({ inventory })
    }

    // 队伍信息
    if (content.includes('队伍成员') || content.includes('你是队长') || content.includes('你在队伍中')) {
      const partyInfo = parsePartyInfo(content)
      partyStore.updatePartyInfo(partyInfo)
    } else if (content.includes('你当前没有队伍') || content.includes('组队情况') && content.includes('没有队伍')) {
      partyStore.reset()
    }

    // 实体列表
    if (content.includes('[类型：') || content.includes('[类型:') || content.includes('地图实体')) {
      const entities = parseEntityList(content)
      if (entities.length > 0) {
        entities.forEach(entity => {
          entity.distance = mapStore.getDistance(playerStore.x, playerStore.y, entity.x, entity.y)
        })
        mapStore.updateEntities(entities)
      }
    }

    // 可移动交互
    if (content.includes('移动到') && content.includes('可交互')) {
      const moveToInteract = parseMoveToInteract(content)
      moveToInteract.forEach(info => {
        mapStore.updateEntity(info.name, {
          moveToX: info.moveToX,
          moveToY: info.moveToY
        })
      })
    }

    // 查看物品
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

    // 解析名称: "名称: xxx"
    const nameMatch = content.match(/名称[：:]\s*(.+)/)
    if (nameMatch) {
      result.name = nameMatch[1].trim()
    } else {
      // 兼容旧格式：第一行是物品名称
      if (lines.length > 0) {
        const firstLine = lines[0].trim()
        if (firstLine && !firstLine.includes(':') && !firstLine.includes('：') && !firstLine.includes('===')) {
          result.name = firstLine
        }
      }
    }

    // 解析类型
    const typeMatch = content.match(/类型[：:]\s*(.+)/)
    if (typeMatch) {
      result.type = typeMatch[1].trim()
    }

    // 解析装备位置（装备特有）
    const slotMatch = content.match(/装备位置[：:]\s*(.+)/)
    if (slotMatch) {
      result.slot = slotMatch[1].trim()
      result.type = result.slot  // 用装备位置作为类型显示
    }

    // 解析稀有度
    const rarityMatch = content.match(/稀有度[：:]\s*(.+)/)
    if (rarityMatch) {
      const rarityName = rarityMatch[1].trim()
      // 转换为英文稀有度用于样式
      const rarityMap = {
        '普通': 'common',
        '优秀': 'uncommon',
        '稀有': 'rare',
        '史诗': 'epic',
        '传说': 'legendary',
        '神话': 'mythic'
      }
      result.rarity = rarityMap[rarityName] || 'common'
      result.rarityName = rarityName
    }

    // 解析描述
    const descMatch = content.match(/描述[：:]\s*(.+)/)
    if (descMatch) {
      result.description = descMatch[1].trim()
    }

    // 解析价格
    const priceMatch = content.match(/价格[：:]\s*(\d+)\s*金币/)
    if (priceMatch) {
      result.price = parseInt(priceMatch[1])
    }

    // 解析属性加成（装备详情格式: "力量 +3"）
    result.stats = {}
    const statPatterns = [
      { pattern: /物理攻击\s*\+(\d+)/, key: 'physicalAttack' },
      { pattern: /物理防御\s*\+(\d+)/, key: 'physicalDefense' },
      { pattern: /法术攻击\s*\+(\d+)/, key: 'magicAttack' },
      { pattern: /法术防御\s*\+(\d+)/, key: 'magicDefense' },
      { pattern: /力量\s*\+(\d+)/, key: 'strength' },
      { pattern: /敏捷\s*\+(\d+)/, key: 'agility' },
      { pattern: /智力\s*\+(\d+)/, key: 'intelligence' },
      { pattern: /体力\s*\+(\d+)/, key: 'vitality' },
      { pattern: /速度\s*\+(\d+)/, key: 'speed' },
      { pattern: /暴击率\s*\+([\d.]+)%/, key: 'critRate' },
      { pattern: /暴击伤害\s*\+([\d.]+)%/, key: 'critDamage' },
      { pattern: /命中率\s*\+([\d.]+)%/, key: 'hitRate' },
      { pattern: /闪避率\s*\+([\d.]+)%/, key: 'dodgeRate' }
    ]

    for (const { pattern, key } of statPatterns) {
      const match = content.match(pattern)
      if (match) {
        result.stats[key] = key.includes('Rate') || key.includes('Damage')
          ? parseFloat(match[1])
          : parseInt(match[1])
      }
    }

    // 兼容旧格式的属性解析（"物理攻击: +10"）
    const oldStatPatterns = [
      { pattern: /物理攻击[：:]\s*\+?(\d+)/, key: 'physicalAttack' },
      { pattern: /物理防御[：:]\s*\+?(\d+)/, key: 'physicalDefense' },
      { pattern: /魔法攻击[：:]\s*\+?(\d+)/, key: 'magicAttack' },
      { pattern: /魔法防御[：:]\s*\+?(\d+)/, key: 'magicDefense' },
      { pattern: /生命[：:]\s*\+?(\d+)/, key: 'health' },
      { pattern: /法力[：:]\s*\+?(\d+)/, key: 'mana' }
    ]

    for (const { pattern, key } of oldStatPatterns) {
      if (!result.stats[key]) {
        const match = content.match(pattern)
        if (match) {
          result.stats[key] = parseInt(match[1])
        }
      }
    }

    // 解析等级要求
    const levelReqMatch = content.match(/等级要求[：:]\s*(\d+)/)
    if (levelReqMatch) {
      result.requirements = result.requirements || {}
      result.requirements.level = parseInt(levelReqMatch[1])
    }

    // 解析效果（消耗品）
    const effectMatch = content.match(/效果[：:]\s*(.+)/)
    if (effectMatch) {
      result.effect = effectMatch[1].trim()
    }

    return result
  }

  /**
   * 处理交易窗口内容
   * 解析交易窗口的初始信息：交易对象、我方资产
   */
  function processTradeWindowContent(content) {
    console.log('[Command] 处理交易窗口内容:', content)

    // 解析交易对象: "与 巧巧 的交易"
    const partnerMatch = content.match(/与\s+(.+?)\s+的交易/)
    if (partnerMatch) {
      const partnerName = partnerMatch[1]
      // 如果还没开始交易，先初始化（后续资产日志会更新）
      if (!tradeStore.isInTrade) {
        tradeStore.startTrade(partnerName, playerStore.gold, playerStore.inventory)
      }
      return
    }

    // 解析我方资产: "你的资产：..."
    if (content.includes('你的资产') || content.includes('金币:') || content.includes('金币：')) {
      const goldMatch = content.match(/金币[：:]\s*(\d+)/)
      const gold = goldMatch ? parseInt(goldMatch[1]) : playerStore.gold

      // 解析背包物品
      const inventory = parseTradeInventory(content)

      // 更新交易资产
      if (tradeStore.isInTrade) {
        tradeStore.updateMyAssets(gold, inventory.length > 0 ? inventory : playerStore.inventory)
      }
    }
  }

  /**
   * 解析交易窗口中的背包物品
   */
  function parseTradeInventory(content) {
    const inventory = []
    const lines = content.split('\n')

    let inInventorySection = false
    for (const line of lines) {
      if (line.includes('背包物品')) {
        inInventorySection = true
        continue
      }

      if (inInventorySection && line.trim().startsWith('-')) {
        // 格式: "- 小型生命药水 x1" 或 "- 铁剑#1"
        const itemMatch = line.match(/-\s+(.+?)(?:\s+x(\d+))?$/)
        if (itemMatch) {
          const name = itemMatch[1].trim()
          const quantity = itemMatch[2] ? parseInt(itemMatch[2]) : 1
          const isEquipment = name.includes('#')
          inventory.push({ name, quantity, isEquipment })
        }
      }
    }

    return inventory
  }

  /**
   * 处理商店窗口内容
   * 解析商店窗口的信息：商店名称、商品列表、收购信息、商店资金
   */
  function processShopWindowContent(content) {
    console.log('[Command] 处理商店窗口内容:', content)

    // 解析商店名称: "商店：商人约翰"
    const shopNameMatch = content.match(/商店[：:]\s*(.+)/)
    if (shopNameMatch) {
      const name = shopNameMatch[1].trim()
      if (!shopStore.isInShop) {
        shopStore.openShop(name)
      }
    }

    // 解析出售商品列表
    if (content.includes('出售商品')) {
      const items = parseShopItems(content)
      if (items.length > 0) {
        shopStore.updateShopItems(items)
      }
    }

    // 解析收购信息
    if (content.includes('收购信息')) {
      const purchaseMatch = content.match(/收购信息[：:]\s*\n?(.+?)(?:\n|$)/)
      if (purchaseMatch) {
        shopStore.updatePurchaseInfo(purchaseMatch[1].trim())
      }
    }

    // 解析商店资金: "商店资金: 1000 金币"
    const shopGoldMatch = content.match(/商店资金[：:]\s*(\d+)\s*金币/)
    if (shopGoldMatch) {
      shopStore.updateShopGold(parseInt(shopGoldMatch[1]))
    }

    // 初始化玩家资产（从 playerStore 获取）
    if (shopStore.isInShop && shopStore.playerGold === 0) {
      shopStore.updatePlayerAssets(playerStore.gold, playerStore.inventory, playerStore.inventory.length, 50)
    }
  }

  /**
   * 解析商店商品列表
   * 格式: "- 小型生命药水 (恢复50点生命值)  价格:10  库存:50"
   * 或: "商店库存：\n- 小型生命药水 (恢复50点生命值)  价格:10  库存:49"
   */
  function parseShopItems(content) {
    const items = []
    const lines = content.split('\n')

    for (const line of lines) {
      // 匹配格式: "- 物品名 (描述)  价格:XX  库存:XX"
      const itemMatch = line.match(/^-\s+(.+?)\s+\((.+?)\)\s+价格[：:]\s*(\d+)\s+库存[：:]\s*(\d+)/)
      if (itemMatch) {
        items.push({
          name: itemMatch[1].trim(),
          description: itemMatch[2].trim(),
          price: parseInt(itemMatch[3]),
          stock: parseInt(itemMatch[4])
        })
      }
    }

    return items
  }

  /**
   * 解析商店中玩家背包物品
   * 格式:
   * 背包物品：
   * - 小型生命药水 x3
   * - 铁矿石 x2
   */
  function parseShopPlayerInventory(content) {
    const inventory = []
    const lines = content.split('\n')

    let inInventorySection = false
    for (const line of lines) {
      if (line.includes('背包物品')) {
        inInventorySection = true
        continue
      }

      if (inInventorySection && line.trim().startsWith('-')) {
        // 格式: "- 小型生命药水 x3" 或 "- 铁剑#1"
        const itemMatch = line.match(/-\s+(.+?)(?:\s+x(\d+))?$/)
        if (itemMatch) {
          const name = itemMatch[1].trim()
          const quantity = itemMatch[2] ? parseInt(itemMatch[2]) : 1
          const isEquipment = name.includes('#')
          inventory.push({ name, quantity, isEquipment })
        }
      }
    }

    return inventory
  }

  /**
   * 解析职业选择列表
   * 格式：
   * 可选职业：
   * 游侠 - 敏捷的远程射手
   *   生命100 法力60 物攻20 物防10 法攻8 法防6 速度110
   * 战士 - 强壮的近战战士
   *   生命120 法力50 物攻25 物防15 法攻5 法防8 速度100
   */
  function parseRoleSelection(content) {
    const roles = []
    const lines = content.split('\n')

    let currentRole = null

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('可选职业')) continue

      // 匹配职业行: "游侠 - 敏捷的远程射手"
      const roleMatch = trimmed.match(/^(.+?)\s+-\s+(.+)$/)
      if (roleMatch) {
        // 保存上一个职业
        if (currentRole) {
          roles.push(currentRole)
        }
        currentRole = {
          name: roleMatch[1].trim(),
          description: roleMatch[2].trim(),
          stats: {}
        }
        continue
      }

      // 匹配属性行: "生命100 法力60 物攻20 物防10 法攻8 法防6 速度110"
      if (currentRole && trimmed.includes('生命')) {
        const statPatterns = [
          { pattern: /生命(\d+)/, key: 'health' },
          { pattern: /法力(\d+)/, key: 'mana' },
          { pattern: /物攻(\d+)/, key: 'physicalAttack' },
          { pattern: /物防(\d+)/, key: 'physicalDefense' },
          { pattern: /法攻(\d+)/, key: 'magicAttack' },
          { pattern: /法防(\d+)/, key: 'magicDefense' },
          { pattern: /速度(\d+)/, key: 'speed' }
        ]

        for (const { pattern, key } of statPatterns) {
          const match = trimmed.match(pattern)
          if (match) {
            currentRole.stats[key] = parseInt(match[1])
          }
        }
      }
    }

    // 添加最后一个职业
    if (currentRole) {
      roles.push(currentRole)
    }

    console.log('[Command] 解析职业列表:', roles)
    return roles
  }

  /**
   * 解析交易状态
   * 格式：
   * 交易状态：
   * 你的提供：
   *   金币: 20
   *   物品:
   *     - 小型生命药水
   *   状态: 已锁定
   * 对方的提供：
   *   金币: 10
   *   物品:
   *     - 小型法力药水
   *   状态: 未锁定
   */
  function parseTradeState(content) {
    const result = {
      myOfferGold: 0,
      myOfferItems: [],
      myLocked: false,
      partnerOfferGold: 0,
      partnerOfferItems: [],
      partnerLocked: false
    }

    const lines = content.split('\n')
    let currentSection = null // 'my' | 'partner'
    let inItemsSection = false

    for (const line of lines) {
      const trimmed = line.trim()

      if (trimmed.includes('你的提供')) {
        currentSection = 'my'
        inItemsSection = false
        continue
      }

      if (trimmed.includes('对方的提供')) {
        currentSection = 'partner'
        inItemsSection = false
        continue
      }

      if (!currentSection) continue

      // 解析金币
      const goldMatch = trimmed.match(/金币[：:]\s*(\d+)/)
      if (goldMatch) {
        const gold = parseInt(goldMatch[1])
        if (currentSection === 'my') {
          result.myOfferGold = gold
        } else {
          result.partnerOfferGold = gold
        }
        continue
      }

      // 进入物品列表
      if (trimmed.includes('物品:') || trimmed.includes('物品：')) {
        inItemsSection = true
        continue
      }

      // 解析物品
      if (inItemsSection && trimmed.startsWith('-')) {
        const itemName = trimmed.substring(1).trim()
        if (itemName && itemName !== '(无)') {
          const isEquipment = itemName.includes('#')
          if (currentSection === 'my') {
            result.myOfferItems.push({ name: itemName, quantity: 1, isEquipment })
          } else {
            result.partnerOfferItems.push({ name: itemName, quantity: 1, isEquipment })
          }
        }
        continue
      }

      // 解析锁定状态
      if (trimmed.includes('状态:') || trimmed.includes('状态：')) {
        const isLocked = trimmed.includes('已锁定')
        if (currentSection === 'my') {
          result.myLocked = isLocked
        } else {
          result.partnerLocked = isLocked
        }
        inItemsSection = false
        continue
      }
    }

    return result
  }

  /**
   * 处理状态类型的日志条目
   */
  function processStateEntry(entry) {
    const { subType, content, timestamp } = entry
    console.log('[Command] 处理状态条目:', subType, '内容长度:', content.length)

    switch (subType) {
      case '窗口变化':
        // 注意：内容格式是 "你已经从XXX窗口切换到YYY窗口"
        // 需要检查"切换到"后面的目标窗口类型
        console.log('[Command] 窗口变化:', content)
        if (content.includes('切换到战斗窗口')) {
          mapStore.setWindowType('combat')
          combatStore.updateCombatState({ isInCombat: true })
        } else if (content.includes('切换到交易窗口')) {
          mapStore.setWindowType('trade')
          // 交易窗口的详细信息会在后续的交易窗口日志中处理
        } else if (content.includes('切换到商店窗口')) {
          mapStore.setWindowType('shop')
          // 商店窗口的详细信息会在后续的商店窗口日志中处理
        } else if (content.includes('切换到地图窗口')) {
          // 如果正在显示战斗结果，不要切换窗口类型，让战斗窗口继续显示
          if (!combatStore.showResult) {
            mapStore.setWindowType('map')
            combatStore.reset()
          }
          // 如果从交易窗口切换回来，结束交易
          if (tradeStore.isInTrade) {
            tradeStore.endTrade()
          }
          // 如果从商店窗口切换回来，关闭商店
          if (shopStore.isInShop) {
            shopStore.closeShop()
          }
          // 如果从注册窗口切换回来，关闭注册
          if (registerStore.isInRegister) {
            registerStore.closeRegister()
          }
        }
        break

      case '环境变化':
        processEnvironmentChange(content)
        break

      case '队伍变化':
        processPartyChange(content)
        break

      case '角色状态':
        // 状态日志中的角色状态更新
        if (combatStore.isInCombat) {
          const stateCharacters = parseCharacterStatus(content)
          const stateSelfChar = stateCharacters.find(c => c.isSelf)
          // 同时更新角色和自己的名字
          if (stateSelfChar) {
            combatStore.updateCombatState({ characters: stateCharacters, myName: stateSelfChar.name })
          } else {
            combatStore.updateCombatState({ characters: stateCharacters })
          }
        }
        break

      case '参战方':
        // 状态日志中的参战方更新（当新阵营加入战斗时）
        if (combatStore.isInCombat) {
          const stateFactions = parseFactions(content)
          combatStore.updateCombatState({ factions: stateFactions })
        }
        break

      case '行动条':
        // 状态日志中的行动条更新
        if (combatStore.isInCombat) {
          const stateActionBar = parseActionBar(content)
          combatStore.updateCombatState({ actionBar: stateActionBar })
        }
        break

      case '当前状态':
        // 状态日志中的当前状态更新
        if (combatStore.isInCombat) {
          const stateStatus = parseCombatStatus(content)
          combatStore.updateCombatState({
            isMyTurn: stateStatus.isMyTurn,
            currentTurn: stateStatus.waitingFor
          })
          // 轮到自己回合时启动倒计时，传入服务端时间戳
          if (stateStatus.isMyTurn) {
            combatStore.startCountdown(timestamp || 0)
          }
          // 自动wait机制：需要等待对方行动时自动发送wait
          if (stateStatus.needsAutoWait) {
            console.log('[Command] 检测到需要自动wait')
            // 延迟发送，确保当前响应处理完毕且 isWaiting 已重置
            setTimeout(() => {
              if (!combatStore.isMyTurn && combatStore.isInCombat && !combatStore.showResult) {
                console.log('[Command] 执行自动wait')
                sendCommand('wait')
              }
            }, 150)
          }
        }
        break

      case '指令响应':
        // 处理移动完成响应
        const moveMatch = content.match(/移动完成，当前位置[：:]\s*\((\d+),\s*(\d+)\)/)
        if (moveMatch) {
          const newX = parseInt(moveMatch[1])
          const newY = parseInt(moveMatch[2])
          playerStore.updateFromParsed({ x: newX, y: newY })
          // 移动后重新计算所有实体的距离和可交互状态
          recalculateEntityDistances()
        }

        // 处理加点响应
        if (content.includes('添加属性点成功')) {
          const playerState = parsePlayerState(content)
          playerStore.updateFromParsed(playerState)
        }

        // 处理背包更新响应
        if (content.includes('背包更新：')) {
          const inventoryStartIndex = content.indexOf('背包更新：')
          if (inventoryStartIndex !== -1) {
            const inventoryContent = content.substring(inventoryStartIndex)
            const inventory = parseInventory(inventoryContent)
            playerStore.updateFromParsed({ inventory })
          }
        }

        // 处理使用物品后的生命/法力更新
        const healthMatch = content.match(/当前:\s*(\d+)\/(\d+)/)
        if (healthMatch && content.includes('生命值')) {
          playerStore.updateFromParsed({
            currentHealth: parseInt(healthMatch[1]),
            maxHealth: parseInt(healthMatch[2])
          })
        }
        if (healthMatch && content.includes('法力值')) {
          playerStore.updateFromParsed({
            currentMana: parseInt(healthMatch[1]),
            maxMana: parseInt(healthMatch[2])
          })
        }

        // 处理队伍解散响应
        if (content.includes('队伍已解散')) {
          partyStore.reset()
        }

        // 处理查看信息响应（从GUI发起的interact查看或inspect）
        // 智能代理模式下不弹出弹窗，避免干扰AI操作
        if (!agentStore.isEnabled) {
          // 敌人信息
          if (content.includes('=== 敌人信息 ===')) {
            uiStore.openInfoModal('查看敌人', content.replace('=== 敌人信息 ===', '').trim())
          }
          // NPC信息
          else if (content.includes('=== NPC信息 ===')) {
            uiStore.openInfoModal('查看NPC', content.replace('=== NPC信息 ===', '').trim())
          }
          // 角色信息（其他玩家）
          else if (content.includes('=== 角色信息 ===')) {
            uiStore.openInfoModal('查看角色', content.replace('=== 角色信息 ===', '').trim())
          }
          // 物品详情
          else if (content.includes('=== 物品详情 ===')) {
            uiStore.openInfoModal('查看物品', content.replace('=== 物品详情 ===', '').trim())
          }
          // 宝箱信息
          else if (content.includes('=== 宝箱信息 ===')) {
            uiStore.openInfoModal('查看宝箱', content.replace('=== 宝箱信息 ===', '').trim())
          }
          // 宝箱开启奖励
          else if (content.includes('你打开了') && content.includes('获得了')) {
            const chestReward = parseChestReward(content)
            if (chestReward) {
              uiStore.showChestReward(chestReward.chestName, chestReward.items)
            }
          }
        }

        // 处理战斗中的指令响应（包含战斗结果）
        // 注意：即使 isInCombat 已经被重置，也要检查是否包含战斗结果
        if (combatStore.isInCombat || content.includes('获得胜利')) {
          processCombatCommandResponse(content)
        }

        // 处理交易相关的指令响应
        if (tradeStore.isInTrade) {
          // 交易完成
          if (content.includes('交易完成')) {
            tradeStore.endTrade()
          }
          // 交易取消/终止
          else if (content.includes('交易已取消') || content.includes('交易终止') || content.includes('交易已终止')) {
            tradeStore.endTrade()
          }
        }

        // 处理商店相关的指令响应
        if (shopStore.isInShop) {
          // 离开商店
          if (content.includes('离开商店')) {
            shopStore.closeShop()
          }
        }
        break

      case '库存变化':
        // 商店库存变化
        if (shopStore.isInShop) {
          const items = parseShopItems(content)
          if (items.length > 0) {
            shopStore.updateShopItems(items)
          }
        }
        break

      case '商店资金':
        // 商店资金变化
        if (shopStore.isInShop) {
          const shopGoldMatch = content.match(/(\d+)\s*金币/)
          if (shopGoldMatch) {
            shopStore.updateShopGold(parseInt(shopGoldMatch[1]))
          }
        }
        break

      case '你的资产':
        // 商店中的玩家资产更新（包含金币和完整背包）
        if (shopStore.isInShop) {
          const goldMatch = content.match(/金币[：:]\s*(\d+)/)
          const gold = goldMatch ? parseInt(goldMatch[1]) : shopStore.playerGold

          // 解析背包物品
          const inventory = parseShopPlayerInventory(content)

          shopStore.updatePlayerAssets(gold, inventory.length > 0 ? inventory : null, inventory.length, 50)

          // 同时更新 playerStore
          if (goldMatch) {
            playerStore.updateFromParsed({ gold: parseInt(goldMatch[1]) })
          }
          if (inventory.length > 0) {
            playerStore.updateFromParsed({ inventory })
          }
        }
        break

      case '交易状态':
        // 处理交易状态更新
        if (tradeStore.isInTrade) {
          const tradeState = parseTradeState(content)
          tradeStore.updateTradeState(tradeState)
        }
        break

      case '战斗日志':
        // 解析战斗日志并添加特效
        processCombatLog(content)
        break
    }
  }

  /**
   * 处理环境变化
   */
  function processEnvironmentChange(content) {
    // 玩家移动: "玩家 小小 移动到 (5,1)"
    const playerMoveMatch = content.match(/玩家\s+(.+?)\s+移动到\s+\((\d+),(\d+)\)/)
    if (playerMoveMatch) {
      const playerName = playerMoveMatch[1]
      const newX = parseInt(playerMoveMatch[2])
      const newY = parseInt(playerMoveMatch[3])
      mapStore.updateEntity(playerName, { x: newX, y: newY })
      // 重新计算距离
      recalculateEntityDistances()
      return
    }

    // 玩家加入地图: "玩家 小小 加入了地图，位置 (3,1)"
    const playerJoinMatch = content.match(/玩家\s+(.+?)\s+加入了地图，位置\s+\((\d+),(\d+)\)/)
    if (playerJoinMatch) {
      const playerName = playerJoinMatch[1]
      const x = parseInt(playerJoinMatch[2])
      const y = parseInt(playerJoinMatch[3])
      // 添加新玩家实体
      const existingEntity = mapStore.entities.find(e => e.name === playerName)
      if (!existingEntity) {
        mapStore.addEntity({
          name: playerName,
          x,
          y,
          type: 'PLAYER',
          displayType: '玩家',
          interactionOptions: [],
          isInRange: mapStore.getDistance(playerStore.x, playerStore.y, x, y) <= 1,
          distance: mapStore.getDistance(playerStore.x, playerStore.y, x, y)
        })
      } else {
        mapStore.updateEntity(playerName, { x, y })
      }
      recalculateEntityDistances()
      return
    }

    // 敌人刷新/出现: "ENEMY 野狼#1 出现在 (6,6)"
    const enemySpawnMatch = content.match(/ENEMY\s+(.+?)\s+出现在\s+\((\d+),(\d+)\)/)
    if (enemySpawnMatch) {
      const enemyName = enemySpawnMatch[1]
      const x = parseInt(enemySpawnMatch[2])
      const y = parseInt(enemySpawnMatch[3])
      // 更新敌人状态：标记为存活
      const existingEntity = mapStore.entities.find(e => e.name === enemyName)
      if (existingEntity) {
        mapStore.updateEntity(enemyName, { x, y, isDead: false, respawnSeconds: 0 })
      } else {
        mapStore.addEntity({
          name: enemyName,
          x,
          y,
          type: 'ENEMY',
          displayType: '普通敌人',
          interactionOptions: [],
          isInRange: mapStore.getDistance(playerStore.x, playerStore.y, x, y) <= 1,
          distance: mapStore.getDistance(playerStore.x, playerStore.y, x, y),
          isDead: false,
          respawnSeconds: 0
        })
      }
      recalculateEntityDistances()
      return
    }

    // 实体离开地图: "小小 离开了地图"
    const leaveMatch = content.match(/(.+?)\s+离开了地图/)
    if (leaveMatch) {
      const entityName = leaveMatch[1]
      mapStore.removeEntity(entityName)
      return
    }

    // 交互选项变化: "小小 的交互选项：[查看, 攻击, 请求交易]"
    const optionsMatch = content.match(/(.+?)\s+的交互选项[：:]\s*\[([^\]]*)\]/)
    if (optionsMatch) {
      const entityName = optionsMatch[1]
      const options = optionsMatch[2].split(',').map(s => s.trim()).filter(Boolean)
      mapStore.updateEntity(entityName, { interactionOptions: options })
      return
    }

    // 交互选项变化（增量）: "小小 的交互选项变化：新增[邀请组队]"
    const optionsChangeMatch = content.match(/(.+?)\s+的交互选项变化[：:](.+)/)
    if (optionsChangeMatch) {
      const entityName = optionsChangeMatch[1]
      const changeContent = optionsChangeMatch[2]

      const entity = mapStore.entities.find(e => e.name === entityName)
      if (entity) {
        let options = [...(entity.interactionOptions || [])]

        // 解析新增选项
        const addMatch = changeContent.match(/新增\[([^\]]*)\]/)
        if (addMatch) {
          const addedOptions = addMatch[1].split(',').map(s => s.trim()).filter(Boolean)
          addedOptions.forEach(opt => {
            if (!options.includes(opt)) {
              options.push(opt)
            }
          })
        }

        // 解析移除选项
        const removeMatch = changeContent.match(/移除\[([^\]]*)\]/)
        if (removeMatch) {
          const removedOptions = removeMatch[1].split(',').map(s => s.trim()).filter(Boolean)
          options = options.filter(opt => !removedOptions.includes(opt))
        }

        mapStore.updateEntity(entityName, { interactionOptions: options })
      }
      return
    }

    // 宝箱状态变化: "普通小宝箱 已被打开" 或 "普通大宝箱 已刷新"
    const chestOpenedMatch = content.match(/(.+?)\s+已被打开$/)
    if (chestOpenedMatch) {
      const entityName = chestOpenedMatch[1]
      mapStore.updateEntity(entityName, { isOpened: true })
      return
    }

    const chestRefreshedMatch = content.match(/(.+?)\s+已刷新$/)
    if (chestRefreshedMatch) {
      const entityName = chestRefreshedMatch[1]
      mapStore.updateEntity(entityName, { isOpened: false })
      return
    }

    // 实体移动（非玩家）: "史莱姆#1 移动到 (5,6)"
    const entityMoveMatch = content.match(/(.+?)\s+移动到\s+\((\d+),(\d+)\)/)
    if (entityMoveMatch) {
      const entityName = entityMoveMatch[1]
      const newX = parseInt(entityMoveMatch[2])
      const newY = parseInt(entityMoveMatch[3])
      mapStore.updateEntity(entityName, { x: newX, y: newY })
      recalculateEntityDistances()
      return
    }
  }

  /**
   * 处理队伍变化
   */
  function processPartyChange(content) {
    // 离开队伍（被踢或解散）
    if (content.includes('你已离开队伍') || content.includes('被踢出')) {
      partyStore.reset()
      return
    }

    // 队伍已组建或成员列表更新: "当前队伍成员(2/4)：\n  - 小小 [队长]\n  - 巧巧"
    if (content.includes('当前队伍成员') || content.includes('队伍已组建')) {
      const partyInfo = parsePartyInfo(content)
      if (partyInfo.members.length > 0) {
        partyInfo.isInParty = true
        partyStore.updatePartyInfo(partyInfo)
      }
      return
    }

    // 成员加入: "小小 加入了队伍"
    const joinMatch = content.match(/(.+?)\s+加入了队伍/)
    if (joinMatch) {
      const memberName = joinMatch[1]
      partyStore.addMember({ name: memberName, isLeader: false })
      // 标记自己在队伍中
      if (!partyStore.isInParty) {
        partyStore.updatePartyInfo({ isInParty: true })
      }
      return
    }

    // 成员离开: "小小 离开了队伍"
    const leaveMatch = content.match(/(.+?)\s+离开了队伍/)
    if (leaveMatch) {
      const memberName = leaveMatch[1]
      partyStore.removeMember(memberName)
      // 如果队伍只剩自己，重置队伍状态
      if (partyStore.members.length <= 1) {
        partyStore.reset()
      }
      return
    }

    // 收到组队邀请: "小小 邀请你加入队伍"
    if (content.includes('邀请你加入队伍')) {
      // 可以在这里添加 UI 提示
      return
    }
  }

  /**
   * 重新计算所有实体的距离和可交互状态
   */
  function recalculateEntityDistances() {
    mapStore.entities.forEach(entity => {
      if (entity.name !== playerStore.name) {
        const distance = mapStore.getDistance(playerStore.x, playerStore.y, entity.x, entity.y)
        const isInRange = distance <= 1
        // 使用 updateEntity 确保响应式更新
        mapStore.updateEntity(entity.name, { distance, isInRange })
      }
    })
  }

  /**
   * 处理战斗日志，添加战斗特效
   */
  function processCombatLog(content) {
    const action = parseCombatAction(content)
    console.log('[Command] 处理战斗日志:', content, '解析结果:', action)

    // 添加到战斗日志
    combatStore.addBattleLog({ content, action })

    if (!action) return

    // 根据动作类型添加特效
    if (action.type === 'damage') {
      combatStore.addEffect({
        type: action.isCrit ? 'crit' : 'damage',
        text: action.isCrit ? `${action.amount} 暴击!` : `-${action.amount}`
      })
    } else if (action.type === 'heal') {
      combatStore.addEffect({
        type: 'heal',
        text: `+${action.amount}`
      })
    } else if (action.type === 'defeat') {
      combatStore.addEffect({
        type: 'defeat',
        text: `${action.target} 被击败!`
      })
    } else if (action.type === 'victory') {
      // 战斗胜利，初始化战斗结果
      if (!combatStore.combatResult) {
        const isMyVictory = action.faction === combatStore.myFaction
        combatStore.showCombatResult({
          victory: isMyVictory,
          experience: 0,
          gold: 0,
          items: []
        })
      }
    } else if (action.type === 'exp') {
      // 更新经验值
      if (combatStore.combatResult) {
        combatStore.combatResult.experience = action.amount
      }
    } else if (action.type === 'gold') {
      // 更新金币
      if (combatStore.combatResult) {
        combatStore.combatResult.gold = action.amount
      }
    }
  }

  /**
   * 处理战斗中的指令响应
   */
  function processCombatCommandResponse(content) {
    const result = parseCommandResponse(content)
    console.log('[Command] 处理战斗指令响应:', result)

    // 处理战斗动作特效
    for (const action of result.actions) {
      if (action.type === 'skill' && action.damage !== undefined) {
        combatStore.addEffect({
          type: action.isCrit ? 'crit' : 'damage',
          text: action.isCrit ? `${action.damage} 暴击!` : `-${action.damage}`
        })
      } else if (action.type === 'defeat') {
        combatStore.addEffect({
          type: 'defeat',
          text: `${action.target} 被击败!`
        })
      }
    }

    // 处理战斗结束
    if (result.combatEnd) {
      // 判断是否是自己的阵营获胜
      const isMyVictory = result.combatEnd.winnerFaction === combatStore.myFaction
      combatStore.showCombatResult({
        victory: isMyVictory,
        experience: result.combatEnd.experience || 0,
        gold: result.combatEnd.gold || 0
      })
    }
  }

  /**
   * 解析宝箱奖励
   * 格式：你打开了普通小宝箱，获得了：
   *   - 铁矿石 x2
   *   - 小型生命药水
   */
  function parseChestReward(content) {
    const chestMatch = content.match(/你打开了(.+?)，获得了/)
    if (!chestMatch) return null

    const chestName = chestMatch[1]
    const items = []

    // 解析物品列表
    const lines = content.split('\n')
    for (const line of lines) {
      const itemMatch = line.match(/^\s*-\s*(.+?)(?:\s+x(\d+))?$/)
      if (itemMatch) {
        items.push({
          name: itemMatch[1].trim(),
          quantity: itemMatch[2] ? parseInt(itemMatch[2]) : 1
        })
      }
    }

    return { chestName, items }
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
