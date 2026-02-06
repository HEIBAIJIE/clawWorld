// 领地相关 API 路由
const { redis } = require('../redis-mem');

// 辅助函数：获取相对时间
function getTimeAgo(timestamp) {
  const now = Date.now();
  const diff = now - timestamp;
  
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  return new Date(timestamp).toLocaleDateString();
}

// 注册领地相关路由
async function registerTerritoryRoutes(fastify) {
  
  // Expand Territory - 扩大领地容量
  fastify.post('/player/:id/territory/expand', async (request, reply) => {
    const { id } = request.params;
    const { getFate, updateFate, expandTerritory, getTerritorySize } = require('../redis-mem');
    
    const COST = 10; // 扩大领地需要 10 点缘分
    const currentFate = await getFate(id);
    
    if (currentFate < COST) {
      return reply.code(400).send({
        error: '缘分不足',
        required: COST,
        current: currentFate
      });
    }
    
    // 扣除缘分
    const newFate = await updateFate(id, -COST);
    
    // 扩大领地
    const newSize = await expandTerritory(id);
    
    return {
      playerId: id,
      action: 'expand_territory',
      cost: COST,
      fateRemaining: newFate,
      territorySize: newSize,
      message: `领地已扩大，现在可以存放 ${newSize} 个实体`
    };
  });

  // Get Territory Entity Detail - 查看领地实体详情
  fastify.get('/player/:id/territory/:entityId', async (request, reply) => {
    const { id, entityId } = request.params;
    
    const territory = await redis.hgetall(`territory:${id}`);
    const entity = territory[entityId];
    
    if (!entity) {
      return reply.code(404).send({ error: 'Entity not found' });
    }
    
    let parsed;
    try {
      parsed = JSON.parse(entity);
    } catch (e) {
      return reply.code(500).send({ error: 'Invalid entity data' });
    }
    
    const formNameMap = {
      sculpture: '雕塑',
      painting: '画作',
      book: '书',
      song: '歌'
    };
    
    return {
      playerId: id,
      entityId,
      ...parsed,
      formName: formNameMap[parsed.form] || '未知'
    };
  });

  // Visit Territory - 访问他人领地
  fastify.get('/territory/:playerId/visit', async (request, reply) => {
    const { playerId } = request.params;
    const { visitorId } = request.query;
    
    const territory = await redis.hgetall(`territory:${playerId}`);
    const { getPlayerStatus, getTerritoryMessages } = require('../redis-mem');
    
    const ownerStatus = await getPlayerStatus(playerId);
    if (!ownerStatus || Object.keys(ownerStatus).length === 0) {
      return reply.code(404).send({ error: 'Territory owner not found' });
    }
    
    const ownerName = ownerStatus.name || playerId;
    
    const formNameMap = {
      sculpture: '雕塑',
      painting: '画作',
      book: '书',
      song: '歌'
    };
    
    const entities = Object.entries(territory).map(([key, value]) => {
      let entity;
      try {
        entity = JSON.parse(value);
      } catch (e) {
        return null;
      }
      return {
        id: key,
        title: entity.title,
        form: entity.form,
        formName: formNameMap[entity.form] || '未知',
        createdAt: entity.createdAt
      };
    }).filter(Boolean);
    
    // 获取留言
    const messages = await getTerritoryMessages(playerId);
    
    return {
      ownerId: playerId,
      ownerName,
      visitorId: visitorId || 'anonymous',
      entityCount: entities.length,
      entities: entities.sort((a, b) => b.createdAt - a.createdAt),
      messages: messages.slice(0, 10).map(m => ({
        id: m.id,
        visitorId: m.visitorId,
        message: m.message,
        timeAgo: getTimeAgo(m.timestamp)
      })),
      message: `欢迎来到 ${ownerName} 的领地`
    };
  });

  // === 领地留言系统 API ===

  // Leave message in territory - 在领地留言
  fastify.post('/territory/:playerId/message', async (request, reply) => {
    const { playerId } = request.params;
    const { visitorId, message } = request.body || {};
    
    if (!visitorId || typeof visitorId !== 'string') {
      return reply.code(400).send({ error: 'visitorId required' });
    }
    
    if (!message || typeof message !== 'string' || message.trim().length === 0) {
      return reply.code(400).send({ error: 'message required' });
    }
    
    if (message.length > 200) {
      return reply.code(400).send({ error: 'message too long (max 200 chars)' });
    }
    
    const { addTerritoryMessage, getPlayerStatus } = require('../redis-mem');
    
    // 检查领地主人是否存在
    const ownerStatus = await getPlayerStatus(playerId);
    if (!ownerStatus || Object.keys(ownerStatus).length === 0) {
      return reply.code(404).send({ error: 'Territory owner not found' });
    }
    
    const newMessage = await addTerritoryMessage(playerId, visitorId, message.trim());
    
    return {
      success: true,
      messageId: newMessage.id,
      territoryOwner: playerId,
      visitorId,
      message: message.trim(),
      timestamp: newMessage.timestamp
    };
  });

  // Get territory messages - 获取领地留言
  fastify.get('/territory/:playerId/messages', async (request, reply) => {
    const { playerId } = request.params;
    const { limit = 20 } = request.query;
    
    const parsedLimit = parseInt(limit);
    if (isNaN(parsedLimit) || parsedLimit < 1 || parsedLimit > 100) {
      return reply.code(400).send({ error: 'Invalid limit (must be 1-100)' });
    }
    
    const { getTerritoryMessages, getPlayerStatus } = require('../redis-mem');
    
    const messages = await getTerritoryMessages(playerId);
    const ownerStatus = await getPlayerStatus(playerId);
    
    return {
      territoryOwner: playerId,
      ownerName: ownerStatus.name || playerId,
      count: messages.length,
      messages: messages.slice(0, parsedLimit).map(m => ({
        id: m.id,
        visitorId: m.visitorId,
        message: m.message,
        timestamp: m.timestamp,
        timeAgo: getTimeAgo(m.timestamp)
      }))
    };
  });

  // Delete territory message - 删除领地留言（仅领地主人）
  fastify.delete('/territory/:playerId/message/:messageId', async (request, reply) => {
    const { playerId, messageId } = request.params;
    const { requesterId } = request.body || {};
    
    if (!requesterId) {
      return reply.code(400).send({ error: 'requesterId required' });
    }
    
    if (requesterId !== playerId) {
      return reply.code(403).send({ error: 'Only territory owner can delete messages' });
    }
    
    const { deleteTerritoryMessage } = require('../redis-mem');
    const success = await deleteTerritoryMessage(playerId, messageId);
    
    if (!success) {
      return reply.code(404).send({ error: 'Message not found' });
    }
    
    return {
      success: true,
      message: 'Message deleted'
    };
  });
}

module.exports = { registerTerritoryRoutes };
