// WebSocket 管理模块 - 使用原生 ws
const WebSocket = require('ws');
const { getOnlinePlayers, setPlayerOnline, setPlayerOffline, redis } = require('./redis-mem');
const { getTerrainInfo, canMoveTo, WORLD_SIZE, TERRAIN_MAP } = require('./world');

// 存储所有 WebSocket 连接
const connections = new Map();
let wss = null;

// 初始化 WebSocket 服务器
function setupWebSocket(server) {
  wss = new WebSocket.Server({ server });
  
  wss.on('connection', (ws, req) => {
    let playerId = null;
    
    console.log('🔌 新的 WebSocket 连接');
    
    ws.on('message', async (message) => {
      try {
        const data = JSON.parse(message.toString());
        console.log('📩 收到:', data.type);
        await handleMessage(ws, data, () => playerId, (id) => { playerId = id; });
      } catch (err) {
        console.error('消息解析错误:', err);
        sendToWs(ws, { type: 'error', message: 'Invalid message format' });
      }
    });
    
    ws.on('close', async () => {
      console.log(`🔌 连接关闭: ${playerId}`);
      if (playerId) {
        await setPlayerOffline(playerId);
        connections.delete(playerId);
        broadcast({ type: 'player_left', playerId });
      }
    });
    
    ws.on('error', (err) => {
      console.error('WebSocket 错误:', err);
    });
    
    // 发送欢迎消息
    sendToWs(ws, { type: 'connected', message: '连接到 ClawWorld' });
  });
  
  console.log('✅ WebSocket 服务器已启动');
}

// 发送消息给指定 WebSocket
function sendToWs(ws, data) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
}

// 处理收到的消息
async function handleMessage(ws, data, getPlayerId, setPlayerId) {
  switch(data.type) {
    case 'login':
      await handleLogin(ws, data, setPlayerId);
      break;
    case 'move':
      await handleMove(ws, data, getPlayerId());
      break;
    case 'say':
      await handleSay(ws, data, getPlayerId());
      break;
    case 'observe':
      await handleObserve(ws, data, getPlayerId());
      break;
    case 'action':
      await handleAction(ws, data, getPlayerId());
      break;
    default:
      sendToWs(ws, { type: 'error', message: 'Unknown action type' });
  }
}

// 处理登录
async function handleLogin(ws, data, setPlayerId) {
  const { playerId, name } = data;
  
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'playerId required' });
    return;
  }
  
  setPlayerId(playerId);
  
  // 保存玩家信息到 Redis
  await setPlayerOnline(playerId, {
    x: 10,
    y: 10,
    name: name || playerId
  });
  
  // 保存连接
  connections.set(playerId, ws);
  
  console.log(`✅ 玩家登录: ${name} (${playerId})`);
  
  // 发送世界状态
  const worldState = await getWorldState();
  sendToWs(ws, { 
    type: 'world_state', 
    ...worldState,
    yourId: playerId 
  });
  
  // 广播玩家加入
  broadcast({ 
    type: 'player_joined', 
    playerId, 
    name: name || playerId,
    x: 10,
    y: 10
  }, playerId);
  
  // 发送欢迎消息
  sendToWs(ws, {
    type: 'system',
    message: `欢迎来到 ClawWorld，${name || playerId}！当前在线: ${connections.size} 人`
  });
}

// 处理移动
async function handleMove(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { x, y } = data;
  
  // 验证移动是否合法
  const player = await redis.hgetall(`player:${playerId}`);
  const currentX = parseInt(player.x) || 10;
  const currentY = parseInt(player.y) || 10;
  
  const dx = Math.abs(x - currentX);
  const dy = Math.abs(y - currentY);
  
  if (dx + dy !== 1) {
    sendToWs(ws, { type: 'error', message: '只能移动到相邻格子' });
    return;
  }
  
  if (!canMoveTo(x, y)) {
    const terrain = getTerrainInfo(x, y);
    sendToWs(ws, { type: 'error', message: `无法进入${terrain.name}` });
    return;
  }
  
  await redis.hset(`player:${playerId}`, 'x', x, 'y', y);
  const terrain = getTerrainInfo(x, y);
  
  console.log(`🚶 玩家移动: ${playerId} → (${x}, ${y}) ${terrain.name}`);
  
  sendToWs(ws, {
    type: 'move_result',
    success: true,
    from: { x: currentX, y: currentY },
    to: { x, y },
    terrain: terrain
  });
  
  broadcast({
    type: 'player_moved',
    playerId,
    x,
    y,
    terrain: terrain.type
  });
}

// 处理说话
async function handleSay(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { message } = data;
  const player = await redis.hgetall(`player:${playerId}`);
  const name = player.name || playerId;
  
  console.log(`💬 ${name}: ${message}`);
  
  broadcast({
    type: 'chat',
    from: name,
    playerId,
    message,
    x: parseInt(player.x) || 10,
    y: parseInt(player.y) || 10
  });
}

// 处理观察
async function handleObserve(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const player = await redis.hgetall(`player:${playerId}`);
  const x = parseInt(player.x) || 10;
  const y = parseInt(player.y) || 10;
  
  const surroundings = [];
  const directions = [
    { dx: 0, dy: -1, name: '北' },
    { dx: 1, dy: 0, name: '东' },
    { dx: 0, dy: 1, name: '南' },
    { dx: -1, dy: 0, name: '西' }
  ];
  
  for (const dir of directions) {
    const nx = x + dir.dx;
    const ny = y + dir.dy;
    if (nx >= 0 && nx < WORLD_SIZE && ny >= 0 && ny < WORLD_SIZE) {
      const terrain = getTerrainInfo(nx, ny);
      surroundings.push({
        direction: dir.name,
        x: nx,
        y: ny,
        terrain: terrain.type,
        name: terrain.name,
        passable: canMoveTo(nx, ny)
      });
    }
  }
  
  const onlinePlayers = await getOnlinePlayers();
  const nearbyPlayers = onlinePlayers.filter(p => {
    if (p.id === playerId) return false;
    const px = parseInt(p.x) || 0;
    const py = parseInt(p.y) || 0;
    return Math.abs(px - x) <= 2 && Math.abs(py - y) <= 2;
  });
  
  const currentTerrain = getTerrainInfo(x, y);
  
  sendToWs(ws, {
    type: 'observe_result',
    position: { x, y },
    terrain: currentTerrain,
    surroundings,
    nearbyPlayers: nearbyPlayers.map(p => ({
      id: p.id,
      name: p.name || p.id,
      x: parseInt(p.x) || 0,
      y: parseInt(p.y) || 0
    }))
  });
}

// 处理通用动作
async function handleAction(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { action } = data;
  console.log(`🎯 玩家动作: ${playerId} - ${action}`);
  
  sendToWs(ws, {
    type: 'action_result',
    action,
    result: `执行了: ${action}`
  });
}

// 获取世界状态
async function getWorldState() {
  const onlinePlayers = await getOnlinePlayers();
  return {
    worldSize: WORLD_SIZE,
    terrain: TERRAIN_MAP,
    players: onlinePlayers.map(p => ({
      id: p.id,
      x: parseInt(p.x) || 10,
      y: parseInt(p.y) || 10,
      name: p.name || p.id
    })),
    timestamp: Date.now()
  };
}

// 广播消息给所有连接
function broadcast(data, excludePlayerId = null) {
  const message = JSON.stringify(data);
  connections.forEach((ws, pid) => {
    if (pid !== excludePlayerId && ws.readyState === WebSocket.OPEN) {
      ws.send(message);
    }
  });
}

function getConnectionCount() {
  return connections.size;
}

module.exports = {
  setupWebSocket,
  broadcast,
  getConnectionCount
};
