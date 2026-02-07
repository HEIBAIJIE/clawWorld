// 内存存储，带自动清理机制
const players = new Map();
const sets = new Map();
const memories = new Map(); // playerId -> []
const items = new Map();    // playerId -> []
const fate = new Map();     // playerId -> number

// ========== 配置 ==========
const CONFIG = {
  // 数据清理配置
  CLEANUP_INTERVAL_MS: 5 * 60 * 1000,  // 每5分钟清理一次
  OFFLINE_TTL_MS: 30 * 60 * 1000,      // 离线30分钟后清理数据
  
  // 系统限制
  MEMORY_LIMIT: 50,
  ITEM_LIMIT: 10,
  DEFAULT_TERRITORY_SIZE: 5,
  MAX_MESSAGES_PER_TERRITORY: 20
};

// 最后访问时间记录（用于清理）
const lastAccessTime = new Map();

// 更新访问时间
function updateAccessTime(key) {
  lastAccessTime.set(key, Date.now());
}

// 清理过期数据
function cleanupExpiredData() {
  const now = Date.now();
  let cleanedCount = 0;
  
  // 清理离线玩家数据
  for (const [key, lastAccess] of lastAccessTime.entries()) {
    if (now - lastAccess > CONFIG.OFFLINE_TTL_MS) {
      // 检查玩家是否离线
      const playerData = players.get(key);
      if (playerData && playerData.online === 'false') {
        // 清理该玩家的所有数据
        players.delete(key);
        memories.delete(key.replace('player:', ''));
        items.delete(key.replace('player:', ''));
        fate.delete(key.replace('player:', ''));
        lastAccessTime.delete(key);
        cleanedCount++;
      }
    }
  }
  
  if (cleanedCount > 0) {
    console.log(`[Cleanup] 清理了 ${cleanedCount} 个离线玩家数据`);
  }
}

// 启动定期清理
setInterval(cleanupExpiredData, CONFIG.CLEANUP_INTERVAL_MS);
console.log('[Redis-Mem] 内存清理机制已启动，间隔:', CONFIG.CLEANUP_INTERVAL_MS, 'ms');

const redis = {
  async hset(key, ...args) {
    updateAccessTime(key);
    const data = players.get(key) || {};
    
    if (args.length === 1 && typeof args[0] === 'object' && !Array.isArray(args[0])) {
      Object.assign(data, args[0]);
    } else if (args.length >= 2) {
      for (let i = 0; i < args.length; i += 2) {
        if (args[i] !== undefined && args[i+1] !== undefined) {
          data[args[i]] = args[i + 1];
        }
      }
    }
    
    players.set(key, data);
    return 'OK';
  },
  
  async hgetall(key) {
    updateAccessTime(key);
    return players.get(key) || {};
  },
  
  async hget(key, field) {
    updateAccessTime(key);
    const data = players.get(key) || {};
    return data[field] || null;
  },
  
  async keys(pattern) {
    const keys = [];
    const regex = new RegExp(pattern.replace(/\*/g, '.*'));
    for (const key of players.keys()) {
      if (regex.test(key)) {
        keys.push(key);
      }
    }
    return keys;
  },
  
  async expire() { return 1; },
  
  async smembers(key) {
    return Array.from(sets.get(key) || []);
  },
  
  async sadd(key, ...members) {
    if (!sets.has(key)) {
      sets.set(key, new Set());
    }
    const set = sets.get(key);
    for (const member of members) {
      set.add(member);
    }
    return members.length;
  },
  
  async srem(key, ...members) {
    if (!sets.has(key)) return 0;
    const set = sets.get(key);
    let removed = 0;
    for (const member of members) {
      if (set.has(member)) {
        set.delete(member);
        removed++;
      }
    }
    return removed;
  }
};

// Player online status
async function setPlayerOnline(playerId, data) {
  updateAccessTime(`player:${playerId}`);
  await redis.hset(`player:${playerId}`, {
    ...data,
    online: 'true',
    lastSeen: Date.now()
  });
}

async function setPlayerOffline(playerId) {
  await redis.hset(`player:${playerId}`, 'online', 'false');
}

async function getPlayerStatus(playerId) {
  return await redis.hgetall(`player:${playerId}`);
}

async function getOnlinePlayers() {
  const keys = await redis.keys('player:*');
  const onlinePlayers = [];
  for (const key of keys) {
    const data = await redis.hgetall(key);
    if (data.online === 'true') {
      onlinePlayers.push({
        id: key.replace('player:', ''),
        ...data
      });
    }
  }
  return onlinePlayers;
}

// ========== 记忆栏系统 ==========

async function addMemory(playerId, memory) {
  updateAccessTime(`player:${playerId}`);
  if (!memories.has(playerId)) {
    memories.set(playerId, []);
  }
  
  const playerMemories = memories.get(playerId);
  
  const newMemory = {
    id: `mem_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
    title: memory.title || '无题记忆',
    content: memory.content || '',
    timestamp: Date.now(),
    type: memory.type || 'travel', // travel, exchange, item
    tags: memory.tags || []
  };
  
  playerMemories.unshift(newMemory);
  
  // 超过上限时自动覆盖最旧的
  if (playerMemories.length > CONFIG.MEMORY_LIMIT) {
    playerMemories.pop();
  }
  
  return newMemory;
}

async function getMemories(playerId, limit = 50) {
  updateAccessTime(`player:${playerId}`);
  return memories.get(playerId) || [];
}

async function deleteMemory(playerId, memoryId) {
  updateAccessTime(`player:${playerId}`);
  if (!memories.has(playerId)) return false;
  
  const playerMemories = memories.get(playerId);
  const index = playerMemories.findIndex(m => m.id === memoryId);
  
  if (index >= 0) {
    playerMemories.splice(index, 1);
    return true;
  }
  return false;
}

// ========== 物品栏系统 ==========

async function addItem(playerId, item) {
  updateAccessTime(`player:${playerId}`);
  if (!items.has(playerId)) {
    items.set(playerId, []);
  }
  
  const playerItems = items.get(playerId);
  
  if (playerItems.length >= CONFIG.ITEM_LIMIT) {
    return { error: '物品栏已满，无法添加' };
  }
  
  const newItem = {
    id: `item_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
    name: item.name || '未知物品',
    description: item.description || '',
    type: item.type || 'misc', // tool, relic, consumable, misc
    rarity: item.rarity || 'common', // common, rare, epic, legendary
    acquiredAt: Date.now(),
    metadata: item.metadata || {}
  };
  
  playerItems.push(newItem);
  return newItem;
}

async function removeItem(playerId, itemId) {
  updateAccessTime(`player:${playerId}`);
  if (!items.has(playerId)) return false;
  
  const playerItems = items.get(playerId);
  const index = playerItems.findIndex(i => i.id === itemId);
  
  if (index >= 0) {
    const removed = playerItems.splice(index, 1)[0];
    return removed;
  }
  return null;
}

async function getItems(playerId) {
  updateAccessTime(`player:${playerId}`);
  return items.get(playerId) || [];
}

// ========== 领地系统 ==========
const territorySize = new Map(); // playerId -> size

async function getTerritorySize(playerId) {
  return territorySize.get(playerId) || CONFIG.DEFAULT_TERRITORY_SIZE;
}

async function expandTerritory(playerId) {
  updateAccessTime(`player:${playerId}`);
  const current = territorySize.get(playerId) || CONFIG.DEFAULT_TERRITORY_SIZE;
  const newSize = current + 3; // 每次扩大增加3个容量
  territorySize.set(playerId, newSize);
  return newSize;
}

async function getTerritoryEntityCount(playerId) {
  const territory = await redis.hgetall(`territory:${playerId}`);
  return Object.keys(territory).length;
}

async function getTerritoryEntity(playerId, entityId) {
  const entity = await redis.hget(`territory:${playerId}`, entityId);
  return entity ? JSON.parse(entity) : null;
}

// ========== 缘分系统 ==========

async function updateFate(playerId, delta) {
  updateAccessTime(`player:${playerId}`);
  const current = fate.get(playerId) || 0;
  const newValue = Math.max(0, current + delta);
  fate.set(playerId, newValue);
  return newValue;
}

async function getFate(playerId) {
  return fate.get(playerId) || 0;
}

async function addFate(playerId, amount) {
  return await updateFate(playerId, amount);
}

// ========== 交换系统 ==========

async function exchangeMemory(fromPlayerId, toPlayerId, memoryId) {
  updateAccessTime(`player:${fromPlayerId}`);
  updateAccessTime(`player:${toPlayerId}`);
  const fromMemories = memories.get(fromPlayerId) || [];
  const memory = fromMemories.find(m => m.id === memoryId);
  
  if (!memory) {
    return { error: '记忆不存在' };
  }
  
  // 添加到对方的记忆栏
  const result = await addMemory(toPlayerId, {
    ...memory,
    title: `[来自 ${fromPlayerId}] ${memory.title}`,
    type: 'exchange'
  });
  
  return result;
}

async function exchangeItem(fromPlayerId, toPlayerId, itemId) {
  updateAccessTime(`player:${fromPlayerId}`);
  updateAccessTime(`player:${toPlayerId}`);
  const fromItems = items.get(fromPlayerId) || [];
  const itemIndex = fromItems.findIndex(i => i.id === itemId);
  
  if (itemIndex < 0) {
    return { error: '物品不存在' };
  }
  
  const item = fromItems[itemIndex];
  
  // 检查对方物品栏是否已满
  const toItems = items.get(toPlayerId) || [];
  if (toItems.length >= CONFIG.ITEM_LIMIT) {
    return { error: '对方物品栏已满' };
  }
  
  // 从发送方移除
  fromItems.splice(itemIndex, 1);
  
  // 添加到接收方
  toItems.push({
    ...item,
    metadata: { ...item.metadata, from: fromPlayerId, exchangedAt: Date.now() }
  });
  items.set(toPlayerId, toItems);
  
  return { success: true, item };
}

// ========== 领地留言系统 ==========
const territoryMessages = new Map(); // playerId -> []

async function addTerritoryMessage(territoryOwnerId, visitorId, message) {
  updateAccessTime(`territory:${territoryOwnerId}`);
  if (!territoryMessages.has(territoryOwnerId)) {
    territoryMessages.set(territoryOwnerId, []);
  }
  
  const messages = territoryMessages.get(territoryOwnerId);
  
  const newMessage = {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
    visitorId,
    message: message || '',
    timestamp: Date.now()
  };
  
  messages.unshift(newMessage);
  
  // 超过上限时删除最旧的
  if (messages.length > CONFIG.MAX_MESSAGES_PER_TERRITORY) {
    messages.pop();
  }
  
  return newMessage;
}

async function getTerritoryMessages(territoryOwnerId) {
  updateAccessTime(`territory:${territoryOwnerId}`);
  return territoryMessages.get(territoryOwnerId) || [];
}

async function deleteTerritoryMessage(territoryOwnerId, messageId) {
  updateAccessTime(`territory:${territoryOwnerId}`);
  if (!territoryMessages.has(territoryOwnerId)) return false;
  
  const messages = territoryMessages.get(territoryOwnerId);
  const index = messages.findIndex(m => m.id === messageId);
  
  if (index >= 0) {
    messages.splice(index, 1);
    return true;
  }
  return false;
}

// ========== 地面标记系统 ==========
const groundMarkers = new Map(); // "x,y" -> []

async function addGroundMarker(x, y, playerId, content) {
  const key = `${x},${y}`;
  updateAccessTime(`marker:${key}`);

  if (!groundMarkers.has(key)) {
    groundMarkers.set(key, []);
  }

  const markers = groundMarkers.get(key);

  const newMarker = {
    id: `marker_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
    playerId,
    content: content || '',
    timestamp: Date.now()
  };

  markers.unshift(newMarker);

  // 每个位置最多保存10条标记，超过时删除最旧的
  if (markers.length > 10) {
    markers.pop();
  }

  return newMarker;
}

async function getGroundMarkers(x, y, limit = 5) {
  const key = `${x},${y}`;
  updateAccessTime(`marker:${key}`);
  const markers = groundMarkers.get(key) || [];
  return markers.slice(0, limit);
}

async function getGroundMarkersInRange(x, y, range = 2) {
  const results = [];
  for (let dx = -range; dx <= range; dx++) {
    for (let dy = -range; dy <= range; dy++) {
      const key = `${x + dx},${y + dy}`;
      const markers = groundMarkers.get(key) || [];
      if (markers.length > 0) {
        results.push({
          x: x + dx,
          y: y + dy,
          markers: markers.slice(0, 3) // 每个位置最多返回3条
        });
      }
    }
  }
  return results;
}

module.exports = {
  redis,
  setPlayerOnline,
  setPlayerOffline,
  getPlayerStatus,
  getOnlinePlayers,
  // 记忆系统
  addMemory,
  getMemories,
  deleteMemory,
  // 物品系统
  addItem,
  removeItem,
  getItems,
  // 缘分系统
  updateFate,
  getFate,
  addFate,
  // 领地系统
  getTerritorySize,
  expandTerritory,
  getTerritoryEntityCount,
  getTerritoryEntity,
  // 领地留言系统
  addTerritoryMessage,
  getTerritoryMessages,
  deleteTerritoryMessage,
  // 地面标记系统
  addGroundMarker,
  getGroundMarkers,
  getGroundMarkersInRange,
  // 交换系统
  exchangeMemory,
  exchangeItem,
  // 配置（导出供其他模块使用）
  CONFIG
};
