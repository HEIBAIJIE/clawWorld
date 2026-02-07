// 玩家相关 API 路由
const { getOnlinePlayers, setPlayerOnline, redis } = require('./redis-mem');
const { getTerrainInfo, canMoveTo, WORLD_SIZE } = require('./world');

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

// 注册玩家相关路由
async function registerPlayerRoutes(fastify) {

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

    // 验证方向参数
    const validDirections = ['north', 'south', 'east', 'west'];
    if (!direction || !validDirections.includes(direction)) {
      return reply.code(400).send({ error: 'Invalid direction. Must be north, south, east, or west' });
    }

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
    }

    // Check if can move
    if (!canMoveTo(newX, newY)) {
      return reply.code(400).send({
        error: 'Cannot move there',
        terrain: getTerrainInfo(newX, newY)
      });
    }

    // Check world bounds
    if (newX < 0 || newX >= WORLD_SIZE || newY < 0 || newY >= WORLD_SIZE) {
      return reply.code(400).send({ error: 'Out of world bounds' });
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

  // Player login/online endpoint
  fastify.post('/player/:id/online', async (request, reply) => {
    const { id } = request.params;
    const { x, y, name } = request.body || {};

    // 验证坐标
    const posX = parseInt(x) || 0;
    const posY = parseInt(y) || 0;

    if (isNaN(posX) || isNaN(posY)) {
      return reply.code(400).send({ error: 'Invalid coordinates' });
    }

    if (posX < 0 || posX >= WORLD_SIZE || posY < 0 || posY >= WORLD_SIZE) {
      return reply.code(400).send({ error: 'Coordinates out of bounds' });
    }

    await setPlayerOnline(id, {
      x: posX,
      y: posY,
      name: name || id
    });

    return {
      playerId: id,
      status: 'online',
      position: { x: posX, y: posY }
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

    // 获取地面标记
    const { getGroundMarkersInRange } = require('../redis-mem');
    const nearbyMarkers = await getGroundMarkersInRange(x, y, 2);

    return {
      playerId: id,
      position: { x, y },
      terrain: {
        type: terrain.type,
        name: terrain.name,
        description: terrain.description
      },
      nearbyPlayers: nearbyPlayers.map(p => ({
        id: p.id,
        name: p.name || p.id,
        distance: Math.abs((parseInt(p.x) || 0) - x) + Math.abs((parseInt(p.y) || 0) - y)
      })),
      surroundings,
      nearbyMarkers,
      timestamp: Date.now()
    };
  });

  // Get Ground Markers - 获取指定位置的地面标记
  fastify.get('/world/markers/:x/:y', async (request, reply) => {
    const x = parseInt(request.params.x);
    const y = parseInt(request.params.y);
    const { limit = 10 } = request.query;

    if (isNaN(x) || isNaN(y) || x < 0 || x >= WORLD_SIZE || y < 0 || y >= WORLD_SIZE) {
      return reply.code(400).send({ error: 'Invalid coordinates' });
    }

    const { getGroundMarkers } = require('../redis-mem');
    const markers = await getGroundMarkers(x, y, parseInt(limit) || 10);

    return {
      x,
      y,
      count: markers.length,
      markers: markers.map(m => ({
        id: m.id,
        playerId: m.playerId,
        content: m.content,
        timestamp: m.timestamp
      }))
    };
  });

  // Say - 在当前位置说话（广播给附近玩家）
  fastify.post('/player/:id/say', async (request, reply) => {
    const { id } = request.params;
    const { message } = request.body || {};

    if (!message || typeof message !== 'string' || message.trim().length === 0) {
      return reply.code(400).send({ error: 'Message is required and must be a non-empty string' });
    }

    if (message.length > 500) {
      return reply.code(400).send({ error: 'Message too long (max 500 characters)' });
    }

    const status = await redis.hgetall(`player:${id}`);
    const x = parseInt(status.x) || 0;
    const y = parseInt(status.y) || 0;

    // 广播给附近玩家（通过 WebSocket）
    const { broadcastToNearby } = require('./websocket');
    await broadcastToNearby(x, y, {
      type: 'player_said',
      playerId: id,
      playerName: status.name || id,
      message: message.trim(),
      position: { x, y },
      timestamp: Date.now()
    });

    return {
      playerId: id,
      action: 'say',
      message: message.trim(),
      position: { x, y },
      broadcast: true
    };
  });

  // Leave - 在当前位置留下地面标记
  fastify.post('/player/:id/leave', async (request, reply) => {
    const { id } = request.params;
    const { content } = request.body || {};

    if (!content || typeof content !== 'string' || content.trim().length === 0) {
      return reply.code(400).send({ error: 'Content is required and must be a non-empty string' });
    }

    if (content.length > 200) {
      return reply.code(400).send({ error: 'Content too long (max 200 characters)' });
    }

    const status = await redis.hgetall(`player:${id}`);
    const x = parseInt(status.x) || 0;
    const y = parseInt(status.y) || 0;

    const { addGroundMarker, getGroundMarkersInRange } = require('./redis-mem');

    // 添加地面标记
    const marker = await addGroundMarker(x, y, id, content.trim());

    // 广播给附近玩家
    const { broadcastToNearby } = require('./websocket');
    await broadcastToNearby(x, y, {
      type: 'ground_marker_added',
      playerId: id,
      playerName: status.name || id,
      position: { x, y },
      timestamp: Date.now()
    });

    // 获取附近的标记返回给客户端
    const nearbyMarkers = await getGroundMarkersInRange(x, y, 2);

    return {
      playerId: id,
      action: 'leave',
      marker: {
        id: marker.id,
        content: marker.content,
        timestamp: marker.timestamp
      },
      position: { x, y },
      nearbyMarkers,
      message: '你在此留下了标记'
    };
  });

  // Offline - 下线（原 leave 功能）
  fastify.post('/player/:id/offline', async (request, reply) => {
    const { id } = request.params;
    const { setPlayerOffline } = require('./redis-mem');

    await setPlayerOffline(id);

    return {
      playerId: id,
      action: 'offline',
      status: 'offline',
      message: 'Player has gone offline'
    };
  });

  // Recall - 回忆（获取记忆列表）
  fastify.get('/player/:id/recall', async (request, reply) => {
    const { id } = request.params;
    const { limit = 10 } = request.query;

    const parsedLimit = parseInt(limit);
    if (isNaN(parsedLimit) || parsedLimit < 1 || parsedLimit > 50) {
      return reply.code(400).send({ error: 'Invalid limit (must be 1-50)' });
    }

    const { getMemories } = require('./redis-mem');
    const memories = await getMemories(id, parsedLimit);

    return {
      playerId: id,
      action: 'recall',
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

  // Rest - 休息（简单恢复）
  fastify.post('/player/:id/rest', async (request, reply) => {
    const { id } = request.params;
    const status = await redis.hgetall(`player:${id}`);
    const x = parseInt(status.x) || 0;
    const y = parseInt(status.y) || 0;
    const terrain = getTerrainInfo(x, y);

    return {
      playerId: id,
      action: 'rest',
      position: { x, y },
      terrain: terrain.type,
      message: `你在 ${terrain.name} 静静地休息了一会儿...`,
      timestamp: Date.now()
    };
  });
}

module.exports = { registerPlayerRoutes };
