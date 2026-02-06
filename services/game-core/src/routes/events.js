// 世界事件相关 API 路由
const { getCurrentEvent, getEventHistory, createRandomEvent, participateEvent } = require('../world-events');

// 注册世界事件路由
async function registerEventRoutes(fastify) {
  
  // Get current world event
  fastify.get('/world/event', async (request, reply) => {
    const event = await getCurrentEvent();
    return {
      hasEvent: !!event,
      event: event || null,
      message: event ? `当前事件: ${event.name}` : '世界平静中...'
    };
  });

  // Get event history
  fastify.get('/world/events/history', async (request, reply) => {
    const { limit = 10 } = request.query;
    
    const parsedLimit = parseInt(limit);
    if (isNaN(parsedLimit) || parsedLimit < 1 || parsedLimit > 50) {
      return reply.code(400).send({ error: 'Invalid limit (must be 1-50)' });
    }
    
    const history = await getEventHistory(parsedLimit);
    return {
      count: history.length,
      events: history
    };
  });

  // Admin: Trigger random event
  fastify.post('/world/event/trigger', async (request, reply) => {
    const { type } = request.body || {};
    
    // 可以添加管理员验证
    const validTypes = ['METEOR_SHOWER', 'MYSTERY_MERCHANT', 'ANCIENT_RUINS', 
                       'FOG_OF_MEMORIES', 'HARMONY_FESTIVAL', 'DIGITAL_STORM'];
    
    if (type && !validTypes.includes(type)) {
      return reply.code(400).send({ 
        error: 'Invalid event type',
        validTypes
      });
    }
    
    const event = await createRandomEvent(type);
    return {
      success: true,
      event,
      message: `事件触发: ${event.name}`
    };
  });

  // Participate in current event
  fastify.post('/player/:id/event/participate', async (request, reply) => {
    const { id } = request.params;
    const { action, choice } = request.body || {};
    
    if (!action || typeof action !== 'string') {
      return reply.code(400).send({ error: 'action is required' });
    }
    
    const result = await participateEvent(id, action, choice);
    
    if (result.error) {
      return reply.code(400).send(result);
    }
    
    return {
      playerId: id,
      ...result
    };
  });
}

module.exports = { registerEventRoutes };
