const fastify = require('fastify')({ logger: true });
const cors = require('@fastify/cors');
require('dotenv').config();

const { getOnlinePlayers } = require('./redis-mem');
const { WORLD_SIZE } = require('./world');
const { setupWebSocket } = require('./websocket');

// 导入路由模块
const { registerPlayerRoutes } = require('./routes/players');
const { registerTerritoryRoutes } = require('./routes/territory');
const { registerEventRoutes } = require('./routes/events');

// Register CORS
fastify.register(cors, {
  origin: '*'
});

// ========== 基础端点 ==========

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
    worldSize: WORLD_SIZE,
    onlinePlayers: onlinePlayers.map(p => ({
      id: p.id,
      x: parseInt(p.x) || 0,
      y: parseInt(p.y) || 0,
      name: p.name || 'Unknown'
    })),
    timestamp: Date.now()
  };
});

// Get terrain at position
fastify.get('/world/terrain/:x/:y', async (request, reply) => {
  const { getTerrainInfo } = require('./world');
  
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

// ========== 注册领域路由 ==========

// 玩家路由（包含6个基础操作）
fastify.register(registerPlayerRoutes);

// 领地路由
fastify.register(registerTerritoryRoutes);

// 世界事件路由
fastify.register(registerEventRoutes);

// ========== 其他功能路由（待拆分） ==========

// === 旅行系统 API ===
const { 
  createInvitation, 
  getInvitations, 
  acceptInvitation, 
  rejectInvitation,
  createTravelSession,
  getTravelSession,
  recordPlayerAction,
  endTravel,
  getNarrativeHistory
} = require('./travel');

// 创建多人旅行（直接创建，无需邀请）
fastify.post('/travel/create', async (request, reply) => {
  const { members, background } = request.body || {};
  
  if (!members || !Array.isArray(members) || members.length < 2) {
    return reply.code(400).send({ error: 'members array with at least 2 players required' });
  }
  
  if (members.length > 5) {
    return reply.code(400).send({ error: 'Maximum 5 members allowed' });
  }
  
  const travelId = await createTravelSession(members, background);
  
  return {
    success: true,
    travelId,
    members,
    message: `旅行会话已创建，共 ${members.length} 人`
  };
});

// 获取邀请列表
fastify.get('/player/:id/invitations', async (request, reply) => {
  const { id } = request.params;
  const invitations = await getInvitations(id);
  
  return {
    playerId: id,
    count: invitations.length,
    invitations: invitations.map(inv => ({
      id: inv.id,
      from: inv.from,
      status: inv.status,
      createdAt: inv.createdAt
    }))
  };
});

// 创建邀请
fastify.post('/player/:id/invite', async (request, reply) => {
  const { id } = request.params;
  const { targetId } = request.body || {};
  
  if (!targetId || typeof targetId !== 'string') {
    return reply.code(400).send({ error: 'targetId is required' });
  }
  
  const invitationId = await createInvitation(id, targetId);
  
  return {
    success: true,
    invitationId,
    from: id,
    to: targetId,
    message: `邀请已发送给 ${targetId}`
  };
});

// 响应邀请
fastify.post('/invitation/:invitationId/respond', async (request, reply) => {
  const { invitationId } = request.params;
  const { playerId, accept } = request.body || {};
  
  if (!playerId) {
    return reply.code(400).send({ error: 'playerId is required' });
  }
  
  if (accept) {
    const result = await acceptInvitation(invitationId, playerId);
    if (result.error) {
      return reply.code(400).send(result);
    }
    return {
      success: true,
      accepted: true,
      travelId: result.travelId,
      members: result.members
    };
  } else {
    await rejectInvitation(invitationId, playerId);
    return {
      success: true,
      accepted: false
    };
  }
});

// 获取旅行会话
fastify.get('/travel/:travelId', async (request, reply) => {
  const { travelId } = request.params;
  const session = await getTravelSession(travelId);
  
  if (!session) {
    return reply.code(404).send({ error: 'Travel session not found' });
  }
  
  return {
    travelId,
    ...session
  };
});

// 获取旅行状态（简化版）
fastify.get('/travel/status', async (request, reply) => {
  const { playerId } = request.query;
  
  if (!playerId) {
    return reply.code(400).send({ error: 'playerId query parameter required' });
  }
  
  const { redis } = require('./redis-mem');
  const player = await redis.hgetall(`player:${playerId}`);
  const travelId = player?.travelId;
  
  if (!travelId) {
    return { inTravel: false, travelId: null };
  }
  
  const session = await getTravelSession(travelId);
  if (!session) {
    return { inTravel: false, travelId: null };
  }
  
  return {
    inTravel: session.status === 'active' || session.status === 'preparing',
    travelId,
    status: session.status,
    round: parseInt(session.round) || 0,
    members: session.members
  };
});

// 提交旅行行动
fastify.post('/travel/:travelId/action', async (request, reply) => {
  const { travelId } = request.params;
  const { playerId, action } = request.body || {};
  
  if (!playerId) {
    return reply.code(400).send({ error: 'playerId is required' });
  }
  
  if (!action || typeof action !== 'string') {
    return reply.code(400).send({ error: 'action is required' });
  }
  
  const result = await recordPlayerAction(travelId, playerId, action);
  
  if (result.error) {
    return reply.code(400).send(result);
  }
  
  return {
    success: true,
    travelId,
    playerId,
    round: result.round,
    message: '行动已提交，等待裁判裁定...'
  };
});

// 结束旅行
fastify.post('/travel/:travelId/end', async (request, reply) => {
  const { travelId } = request.params;
  const { playerId, ending } = request.body || {};
  
  // 验证玩家是否是旅行成员
  const session = await getTravelSession(travelId);
  if (!session) {
    return reply.code(404).send({ error: 'Travel session not found' });
  }
  
  if (!session.members.includes(playerId)) {
    return reply.code(403).send({ error: 'Not a member of this travel' });
  }
  
  const result = await endTravel(travelId, ending);
  
  if (result.error) {
    return reply.code(400).send(result);
  }
  
  return {
    success: true,
    travelId,
    ending: result.ending,
    fate: result.fate,
    round: result.round,
    message: '旅行已结束'
  };
});

// 获取叙事历史
fastify.get('/travel/:travelId/narrative', async (request, reply) => {
  const { travelId } = request.params;
  
  const session = await getTravelSession(travelId);
  if (!session) {
    return reply.code(404).send({ error: 'Travel session not found' });
  }
  
  const history = await getNarrativeHistory(travelId);
  
  return {
    travelId,
    round: parseInt(session.round) || 0,
    status: session.status,
    history: history.map(h => ({
      type: h.type,
      round: h.round,
      content: h.content,
      playerId: h.playerId,
      timestamp: h.timestamp
    }))
  };
});

// === 记忆系统 API ===

const { addMemory, deleteMemory, getMemories } = require('./redis-mem');

// Get Memories - 获取记忆列表
fastify.get('/player/:id/memories', async (request, reply) => {
  const { id } = request.params;
  const { limit = 10 } = request.query;
  
  const parsedLimit = parseInt(limit);
  if (isNaN(parsedLimit) || parsedLimit < 1 || parsedLimit > 50) {
    return reply.code(400).send({ error: 'Invalid limit (must be 1-50)' });
  }
  
  const { getTimeAgo } = require('./utils/helpers');
  const memories = await getMemories(id, parsedLimit);
  
  return {
    playerId: id,
    count: memories.length,
    memories: memories.map(m => ({
      id: m.id,
      title: m.title,
      content: m.content,
      timestamp: m.timestamp,
      timeAgo: getTimeAgo(m.timestamp),
      type: m.type,
      tags: m.tags
    }))
  };
});

// Add Memory - 添加记忆
fastify.post('/player/:id/memories', async (request, reply) => {
  const { id } = request.params;
  const { title, content, type, tags } = request.body || {};
  
  if (!title || typeof title !== 'string' || title.trim().length === 0) {
    return reply.code(400).send({ error: 'Title is required' });
  }
  
  if (title.length > 100) {
    return reply.code(400).send({ error: 'Title too long (max 100 chars)' });
  }
  
  if (!content || typeof content !== 'string' || content.trim().length === 0) {
    return reply.code(400).send({ error: 'Content is required' });
  }
  
  if (content.length > 2000) {
    return reply.code(400).send({ error: 'Content too long (max 2000 chars)' });
  }
  
  const result = await addMemory(id, {
    title: title.trim(),
    content: content.trim(),
    type: type || 'general',
    tags: Array.isArray(tags) ? tags : []
  });
  
  if (result.error) {
    return reply.code(400).send(result);
  }
  
  return {
    playerId: id,
    action: 'add_memory',
    memory: result
  };
});

// Delete Memory - 删除记忆
fastify.delete('/player/:id/memories/:memoryId', async (request, reply) => {
  const { id, memoryId } = request.params;
  
  const success = await deleteMemory(id, memoryId);
  
  if (!success) {
    return reply.code(404).send({ error: 'Memory not found' });
  }
  
  return {
    playerId: id,
    action: 'delete_memory',
    memoryId,
    success: true
  };
});

// === 物品系统 API ===

const { addItem, removeItem, getItems } = require('./redis-mem');

// Get Inventory - 获取物品栏
fastify.get('/player/:id/inventory', async (request, reply) => {
  const { id } = request.params;
  const items = await getItems(id);
  
  return {
    playerId: id,
    count: items.length,
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

// Add Item - 添加物品
fastify.post('/player/:id/inventory', async (request, reply) => {
  const { id } = request.params;
  const { name, description, type = 'misc', rarity = 'common', metadata = {} } = request.body || {};
  
  if (!name || typeof name !== 'string' || name.trim().length === 0) {
    return reply.code(400).send({ error: 'Item name is required' });
  }
  
  if (name.length > 50) {
    return reply.code(400).send({ error: 'Item name too long (max 50 chars)' });
  }
  
  const result = await addItem(id, {
    name: name.trim(),
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
    message: `获得物品: ${result.name}`
  };
});

// Drop Item - 丢弃物品
fastify.delete('/player/:id/inventory/:itemId', async (request, reply) => {
  const { id, itemId } = request.params;
  
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

// ========== 启动服务 ==========

const start = async () => {
  try {
    await fastify.listen({ port: 3002, host: '0.0.0.0' });
    fastify.log.info(`Game Core running on port 3002`);
    
    // Setup WebSocket after server is listening
    setupWebSocket(fastify.server);
    fastify.log.info('WebSocket server started');
    
    // 启动世界事件自动生成器
    const { startAutoEventGenerator } = require('./world-events');
    startAutoEventGenerator();
    fastify.log.info('World Event auto-generator started');
  } catch (err) {
    fastify.log.error(err);
    process.exit(1);
  }
};

start();
