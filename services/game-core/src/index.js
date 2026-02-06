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
