// 临时内存存储，用于测试
const players = new Map();
const sets = new Map();
const memories = new Map(); // playerId -> []
const items = new Map();    // playerId -> []
const fate = new Map();     // playerId -> number

const redis = {
  async hset(key, ...args) {
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
    return players.get(key) || {};
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
const MEMORY_LIMIT = 50;

async function addMemory(playerId, memory) {
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
  if (playerMemories.length > MEMORY_LIMIT) {
    playerMemories.pop();
  }
  
  return newMemory;
}

async function getMemories(playerId, limit = 50) {
  return memories.get(playerId) || [];
}

async function deleteMemory(playerId, memoryId) {
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
const ITEM_LIMIT = 10;

async function addItem(playerId, item) {
  if (!items.has(playerId)) {
    items.set(playerId, []);
  }
  
  const playerItems = items.get(playerId);
  
  if (playerItems.length >= ITEM_LIMIT) {
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
  return items.get(playerId) || [];
}

// ========== 领地系统 ==========
const territorySize = new Map(); // playerId -> size
const DEFAULT_TERRITORY_SIZE = 5;

async function getTerritorySize(playerId) {
  return territorySize.get(playerId) || DEFAULT_TERRITORY_SIZE;
}

async function expandTerritory(playerId) {
  const current = territorySize.get(playerId) || DEFAULT_TERRITORY_SIZE;
  const newSize = current + 3; // 每次扩大增加3个容量
  territorySize.set(playerId, newSize);
  return newSize;
}

async function getTerritoryEntityCount(playerId) {
  const { redis } = require('./redis-mem');
  const territory = await redis.hgetall(`territory:${playerId}`);
  return Object.keys(territory).length;
}

async function getTerritoryEntity(playerId, entityId) {
  const { redis } = require('./redis-mem');
  const entity = await redis.hget(`territory:${playerId}`, entityId);
  return entity ? JSON.parse(entity) : null;
}

// ========== 缘分系统 ==========

async function updateFate(playerId, delta) {
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
  const fromItems = items.get(fromPlayerId) || [];
  const itemIndex = fromItems.findIndex(i => i.id === itemId);
  
  if (itemIndex < 0) {
    return { error: '物品不存在' };
  }
  
  const item = fromItems[itemIndex];
  
  // 检查对方物品栏是否已满
  const toItems = items.get(toPlayerId) || [];
  if (toItems.length >= ITEM_LIMIT) {
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
  // 交换系统
  exchangeMemory,
  exchangeItem
};
