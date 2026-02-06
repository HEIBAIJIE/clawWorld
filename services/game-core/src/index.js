const fastify = require('fastify')({ logger: true });
const cors = require('@fastify/cors');
require('dotenv').config();

const { 
  createInvitation, 
  getInvitations, 
  acceptInvitation, 
  rejectInvitation,
  getTravelSession 
} = require('./travel');
const { getOnlinePlayers, setPlayerOnline, redis } = require('./redis-mem');
const { getTerrainInfo, canMoveTo, WORLD_SIZE } = require('./world');
const { setupWebSocket } = require('./websocket');

// Register CORS
fastify.register(cors, {
  origin: '*'
});

// Health check
fastify.get('/health', async () => {
  return { status: 'ok', service: 'game-core' };
});

// Server stats endpoint
fastify.get('/stats', async () => {
  const { getServerStats } = require('./websocket');
  const stats = await getServerStats();
  return {
    ...stats,
    service: 'game-core',
    version: '1.0.0'
  };
});

// World state endpoint
fastify.get('/world/state', async (request, reply) => {
  const onlinePlayers = await getOnlinePlayers();
  return {
    worldSize: 20,
    onlinePlayers: onlinePlayers.map(p => ({
      id: p.id,
      x: parseInt(p.x) || 0,
      y: parseInt(p.y) || 0,
      name: p.name || 'Unknown'
    })),
    timestamp: Date.now()
  };
});

// Player position endpoint
fastify.get('/player/:id/position', async (request, reply) => {
  const { id } = request.params;
  const status = await redis.hgetall(`player:${id}`);
  const x = parseInt(status.x) || 0;
  const y = parseInt(status.y) || 0;
  const terrain = getTerrainInfo(x, y);
  
  return {
    playerId: id,
    x,
    y,
    terrain: terrain.type,
    terrainName: terrain.name,
    terrainDescription: terrain.description
  };
});

// Player move endpoint
fastify.post('/player/:id/move', async (request, reply) => {
  const { id } = request.params;
  const { direction } = request.body;
  
  // Get current position
  const status = await redis.hgetall(`player:${id}`);
  let x = parseInt(status.x) || 0;
  let y = parseInt(status.y) || 0;
  
  // Calculate new position
  let newX = x, newY = y;
  switch(direction) {
    case 'north': newY = y - 1; break;
    case 'south': newY = y + 1; break;
    case 'east': newX = x + 1; break;
    case 'west': newX = x - 1; break;
    default:
      return reply.code(400).send({ error: 'Invalid direction' });
  }
  
  // Check if can move
  if (!canMoveTo(newX, newY)) {
    return reply.code(400).send({ 
      error: 'Cannot move there',
      terrain: getTerrainInfo(newX, newY)
    });
  }
  
  // Update position
  await redis.hset(`player:${id}`, 'x', newX, 'y', newY);
  const terrain = getTerrainInfo(newX, newY);
  
  return {
    playerId: id,
    from: { x, y },
    to: { x: newX, y: newY },
    direction,
    terrain: terrain.type,
    terrainName: terrain.name
  };
});

// Get terrain at position
fastify.get('/world/terrain/:x/:y', async (request, reply) => {
  const x = parseInt(request.params.x);
  const y = parseInt(request.params.y);
  
  if (isNaN(x) || isNaN(y) || x < 0 || x >= WORLD_SIZE || y < 0 || y >= WORLD_SIZE) {
    return reply.code(400).send({ error: 'Invalid coordinates' });
  }
  
  const terrain = getTerrainInfo(x, y);
  return {
    x, y,
    ...terrain
  };
});

// Player login/online endpoint
fastify.post('/player/:id/online', async (request, reply) => {
  const { id } = request.params;
  const { x, y, name } = request.body || {};
  
  await setPlayerOnline(id, {
    x: x || 0,
    y: y || 0,
    name: name || id
  });
  
  return {
    playerId: id,
    status: 'online',
    position: { x: x || 0, y: y || 0 }
  };
});

// === 6个基础操作 API (AI Native) ===

// Observe - 观察周围环境
fastify.post('/player/:id/observe', async (request, reply) => {
  const { id } = request.params;
  const status = await redis.hgetall(`player:${id}`);
  const x = parseInt(status.x) || 0;
  const y = parseInt(status.y) || 0;
  
  const terrain = getTerrainInfo(x, y);
  const onlinePlayers = await getOnlinePlayers();
  
  // 获取附近玩家（2格范围内）
  const nearbyPlayers = onlinePlayers.filter(p => {
    if (p.id === id) return false;
    const px = parseInt(p.x) || 0;
    const py = parseInt(p.y) || 0;
    const distance = Math.abs(px - x) + Math.abs(py - y);
    return distance <= 2;
  });
  
  // 获取可移动方向
  const surroundings = [];
  const directions = [
    { dir: 'north', dx: 0, dy: -1, name: '北' },
    { dir: 'east', dx: 1, dy: 0, name: '东' },
    { dir: 'south', dx: 0, dy: 1, name: '南' },
    { dir: 'west', dx: -1, dy: 0, name: '西' }
  ];
  
  for (const d of directions) {
    const nx = x + d.dx;
    const ny = y + d.dy;
    if (nx >= 0 && nx < WORLD_SIZE && ny >= 0 && ny < WORLD_SIZE) {
      const t = getTerrainInfo(nx, ny);
      surroundings.push({
        direction: d.dir,
        directionName: d.name,
        x: nx,
        y: ny,
        terrain: t.type,
        terrainName: t.name,
        passable: canMoveTo(nx, ny)
      });
    }
  }
  
  return {
    playerId: id,
    position: { x, y },
    terrain: {
      type: terrain.type,
      name: terrain.name,
      description: terrain.description,
      emoji: terrain.emoji
    },
    surroundings,
    nearbyPlayers: nearbyPlayers.map(p => ({
      id: p.id,
      name: p.name || p.id,
      x: parseInt(p.x) || 0,
      y: parseInt(p.y) || 0,
      distance: Math.abs((parseInt(p.x) || 0) - x) + Math.abs((parseInt(p.y) || 0) - y)
    })),
    timestamp: Date.now()
  };
});

// Say - 说话/广播
fastify.post('/player/:id/say', async (request, reply) => {
  const { id } = request.params;
  const { message, targetId } = request.body || {};
  
  if (!message) {
    return reply.code(400).send({ error: 'Message required' });
  }
  
  const status = await redis.hgetall(`player:${id}`);
  const name = status.name || id;
  const x = parseInt(status.x) || 0;
  const y = parseInt(status.y) || 0;
  
  // 这里可以集成 WebSocket 广播，但先返回成功
  return {
    playerId: id,
    action: 'say',
    from: name,
    message,
    position: { x, y },
    timestamp: Date.now(),
    broadcast: !targetId,
    target: targetId || null
  };
});

// Leave - 留下标记/物品
fastify.post('/player/:id/leave', async (request, reply) => {
  const { id } = request.params;
  const { content, type = 'message' } = request.body || {};
  
  const status = await redis.hgetall(`player:${id}`);
  const x = parseInt(status.x) || 0;
  const y = parseInt(status.y) || 0;
  const name = status.name || id;
  
  // 存储到地面
  const leaveId = `leave_${Date.now()}_${id}`;
  await redis.hset(`ground:${x}:${y}`, leaveId, JSON.stringify({
    type,
    content: content || '',
    from: id,
    fromName: name,
    timestamp: Date.now()
  }));
  
  return {
    playerId: id,
    action: 'leave',
    position: { x, y },
    content: content || '',
    type,
    timestamp: Date.now()
  };
});

// Recall - 回忆/检索记忆
fastify.post('/player/:id/recall', async (request, reply) => {
  const { id } = request.params;
  const { keyword } = request.body || {};
  
  // 从 redis-mem 获取记忆
  const { getMemories } = require('./redis-mem');
  const memories = await getMemories(id);
  
  let result = memories;
  if (keyword) {
    result = memories.filter(m => 
      (m.title && m.title.includes(keyword)) || 
      (m.content && m.content.includes(keyword))
    );
  }
  
  return {
    playerId: id,
    action: 'recall',
    keyword: keyword || null,
    count: result.length,
    memories: result.slice(0, 10).map(m => ({
      id: m.id,
      title: m.title,
      timestamp: m.timestamp,
      type: m.type
    }))
  };
});

// Add Memory - 添加记忆（旅行结束后调用）
fastify.post('/player/:id/memory', async (request, reply) => {
  const { id } = request.params;
  const { title, content, type = 'travel', tags = [] } = request.body || {};
  
  if (!title) {
    return reply.code(400).send({ error: 'Title required' });
  }
  
  const { addMemory } = require('./redis-mem');
  const memory = await addMemory(id, {
    title,
    content: content || '',
    type,
    tags
  });
  
  return {
    playerId: id,
    action: 'add_memory',
    memory: {
      id: memory.id,
      title: memory.title,
      timestamp: memory.timestamp,
      type: memory.type
    },
    message: '记忆已添加到记忆栏'
  };
});

// Delete Memory - 删除记忆（腾出空间）
fastify.delete('/player/:id/memory/:memoryId', async (request, reply) => {
  const { id, memoryId } = request.params;
  
  const { deleteMemory } = require('./redis-mem');
  const success = await deleteMemory(id, memoryId);
  
  if (!success) {
    return reply.code(404).send({ error: 'Memory not found' });
  }
  
  return {
    playerId: id,
    action: 'delete_memory',
    memoryId,
    message: '记忆已删除'
  };
});

// Solidify Memory - 固化回忆（消耗缘分转为实体）
fastify.post('/player/:id/memory/:memoryId/solidify', async (request, reply) => {
  const { id, memoryId } = request.params;
  const { form = 'sculpture' } = request.body || {}; // sculpture, painting, book, song
  
  const { getMemories, deleteMemory, updateFate, getFate } = require('./redis-mem');
  
  // 检查记忆是否存在
  const memories = await getMemories(id);
  const memory = memories.find(m => m.id === memoryId);
  
  if (!memory) {
    return reply.code(404).send({ error: 'Memory not found' });
  }
  
  // 检查缘分是否足够（固化需要 5 点缘分）
  const currentFate = await getFate(id);
  const COST = 5;
  
  if (currentFate < COST) {
    return reply.code(400).send({ 
      error: '缘分不足', 
      required: COST, 
      current: currentFate 
    });
  }
  
  // 扣除缘分
  const newFate = await updateFate(id, -COST);
  
  // 从记忆栏删除
  await deleteMemory(id, memoryId);
  
  // 创建实体到领地（存储到领地集合）
  const entityId = `entity_${Date.now()}_${id}`;
  await redis.hset(`territory:${id}`, entityId, JSON.stringify({
    memoryId,
    title: memory.title,
    content: memory.content,
    form, // 实体形式
    createdAt: Date.now(),
    originalTimestamp: memory.timestamp
  }));
  
  return {
    playerId: id,
    action: 'solidify_memory',
    memory: {
      id: memoryId,
      title: memory.title,
      form
    },
    cost: COST,
    fateRemaining: newFate,
    message: `回忆已固化为${form === 'sculpture' ? '雕塑' : form === 'painting' ? '画作' : form === 'book' ? '书' : '歌'}`
  };
});

// Get Territory - 查看领地
fastify.get('/player/:id/territory', async (request, reply) => {
  const { id } = request.params;
  
  const territory = await redis.hgetall(`territory:${id}`);
  const { getFate } = require('./redis-mem');
  const fate = await getFate(id);
  
  const entities = Object.entries(territory).map(([key, value]) => {
    const entity = JSON.parse(value);
    return {
      id: key,
      ...entity
    };
  });
  
  return {
    playerId: id,
    entities: entities,
    count: entities.length,
    fate
  };
});

// Update Fate - 更新缘分（管理员/裁判调用）
fastify.post('/player/:id/fate', async (request, reply) => {
  const { id } = request.params;
  const { delta } = request.body || {};
  
  if (typeof delta !== 'number') {
    return reply.code(400).send({ error: 'Delta must be a number' });
  }
  
  const { updateFate, getFate } = require('./redis-mem');
  const newFate = await updateFate(id, delta);
  
  return {
    playerId: id,
    action: 'update_fate',
    delta,
    fate: newFate
  };
});

// === 物品系统 API ===

// Get Inventory - 获取物品栏
fastify.get('/player/:id/inventory', async (request, reply) => {
  const { id } = request.params;
  
  const { getItems } = require('./redis-mem');
  const items = await getItems(id);
  
  return {
    playerId: id,
    count: items.length,
    limit: 10,
    items: items.map(item => ({
      id: item.id,
      name: item.name,
      description: item.description,
      type: item.type,
      rarity: item.rarity,
      acquiredAt: item.acquiredAt
    }))
  };
});

// Add Item - 添加物品（旅行/事件获得）
fastify.post('/player/:id/inventory', async (request, reply) => {
  const { id } = request.params;
  const { name, description, type = 'misc', rarity = 'common', metadata = {} } = request.body || {};
  
  if (!name) {
    return reply.code(400).send({ error: 'Item name required' });
  }
  
  const { addItem } = require('./redis-mem');
  const result = await addItem(id, {
    name,
    description: description || '',
    type,
    rarity,
    metadata
  });
  
  if (result.error) {
    return reply.code(400).send({ error: result.error });
  }
  
  return {
    playerId: id,
    action: 'add_item',
    item: {
      id: result.id,
      name: result.name,
      type: result.type,
      rarity: result.rarity
    },
    message: `获得物品: ${name}`
  };
});

// Drop Item - 丢弃物品
fastify.delete('/player/:id/inventory/:itemId', async (request, reply) => {
  const { id, itemId } = request.params;
  
  const { removeItem } = require('./redis-mem');
  const result = await removeItem(id, itemId);
  
  if (!result) {
    return reply.code(404).send({ error: 'Item not found' });
  }
  
  return {
    playerId: id,
    action: 'remove_item',
    item: {
      id: result.id,
      name: result.name
    },
    message: `丢弃物品: ${result.name}`
  };
});

// Give Item - 给予物品（交换）
fastify.post('/player/:id/inventory/:itemId/give', async (request, reply) => {
  const { id, itemId } = request.params;
  const { targetId } = request.body || {};
  
  if (!targetId) {
    return reply.code(400).send({ error: 'Target player ID required' });
  }
  
  const { removeItem, addItem, getItems } = require('./redis-mem');
  
  // 检查物品是否存在
  const myItems = await getItems(id);
  const item = myItems.find(i => i.id === itemId);
  
  if (!item) {
    return reply.code(404).send({ error: 'Item not found' });
  }
  
  // 检查对方物品栏是否已满
  const targetItems = await getItems(targetId);
  if (targetItems.length >= 10) {
    return reply.code(400).send({ error: '对方物品栏已满' });
  }
  
  // 从给予方移除
  await removeItem(id, itemId);
  
  // 添加给对方
  await addItem(targetId, {
    name: item.name,
    description: item.description,
    type: item.type,
    rarity: item.rarity,
    metadata: { ...item.metadata, from: id, giftedAt: Date.now() }
  });
  
  return {
    playerId: id,
    action: 'give_item',
    item: { id: itemId, name: item.name },
    target: targetId,
    message: `已将 ${item.name} 给予 ${targetId}`
  };
});

// Rest - 休息/下线
fastify.post('/player/:id/rest', async (request, reply) => {
  const { id } = request.params;
  
  await redis.hset(`player:${id}`, 'status', 'resting');
  
  return {
    playerId: id,
    action: 'rest',
    status: 'resting',
    message: '你进入了休息状态',
    timestamp: Date.now()
  };
});

// Wake - 唤醒/上线
fastify.post('/player/:id/wake', async (request, reply) => {
  const { id } = request.params;
  
  await redis.hset(`player:${id}`, 'status', 'online');
  const status = await redis.hgetall(`player:${id}`);
  
  return {
    playerId: id,
    action: 'wake',
    status: 'online',
    position: { 
      x: parseInt(status.x) || 0, 
      y: parseInt(status.y) || 0 
    },
    message: '欢迎回来！',
    timestamp: Date.now()
  };
});

// === Travel API ===

// Get invitations
fastify.get('/player/:id/invitations', async (request, reply) => {
  const { id } = request.params;
  const invitations = await getInvitations(id);
  return { playerId: id, invitations };
});

// Create invitation
fastify.post('/player/:id/invite', async (request, reply) => {
  const { id } = request.params;
  const { targetId } = request.body;
  
  if (!targetId) {
    return reply.code(400).send({ error: 'targetId required' });
  }
  
  const invitationId = await createInvitation(id, targetId);
  return { 
    success: true, 
    invitationId,
    from: id,
    to: targetId 
  };
});

// Accept invitation
fastify.post('/invitation/:invitationId/accept', async (request, reply) => {
  const { invitationId } = request.params;
  const { playerId } = request.body;
  
  const result = await acceptInvitation(invitationId, playerId);
  if (result.error) {
    return reply.code(400).send(result);
  }
  return result;
});

// Reject invitation
fastify.post('/invitation/:invitationId/reject', async (request, reply) => {
  const { invitationId } = request.params;
  const { playerId } = request.body;
  
  const result = await rejectInvitation(invitationId, playerId);
  if (result.error) {
    return reply.code(400).send(result);
  }
  return result;
});

// Get travel session
fastify.get('/travel/:travelId', async (request, reply) => {
  const { travelId } = request.params;
  const session = await getTravelSession(travelId);
  
  if (!session) {
    return reply.code(404).send({ error: 'Travel session not found' });
  }
  
  return session;
});

// Start server
const start = async () => {
  try {
    await fastify.listen({ port: 3002, host: '0.0.0.0' });
    fastify.log.info(`Game Core running on port 3002`);
    
    // Setup WebSocket after server is listening
    setupWebSocket(fastify.server);
    fastify.log.info('WebSocket server started');
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
};

start();
