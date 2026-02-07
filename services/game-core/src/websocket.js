// WebSocket 管理模块 - 使用原生 ws
const WebSocket = require('ws');
const { getOnlinePlayers, setPlayerOnline, setPlayerOffline, redis, addMemory, getMemories, getPlayerStatus } = require('./redis-mem');
const { getTerrainInfo, canMoveTo, WORLD_SIZE, TERRAIN_MAP } = require('./world');
const { createInvitation, acceptInvitation, rejectInvitation, getTravelSession, recordPlayerAction, getNarrativeHistory, getOpeningFromReferee } = require('./travel');

// 存储所有 WebSocket 连接
const connections = new Map();
let wss = null;

// ========== 心跳检测配置 ==========
const HEARTBEAT_INTERVAL = 30 * 1000; // 30秒心跳间隔
const HEARTBEAT_TIMEOUT = 60 * 1000;  // 60秒超时
const connectionHeartbeats = new Map(); // playerId -> { lastPing, timeout }

// 更新心跳
function updateHeartbeat(playerId) {
  if (connectionHeartbeats.has(playerId)) {
    const hb = connectionHeartbeats.get(playerId);
    hb.lastPing = Date.now();
    
    // 清除旧超时
    if (hb.timeout) {
      clearTimeout(hb.timeout);
    }
    
    // 设置新超时
    hb.timeout = setTimeout(() => {
      console.log(`⏱️ 心跳超时: ${playerId}`);
      const ws = connections.get(playerId);
      if (ws) {
        ws.terminate(); // 强制关闭连接
      }
      cleanupConnection(playerId);
    }, HEARTBEAT_TIMEOUT);
  }
}

// 清理连接
async function cleanupConnection(playerId) {
  if (connectionHeartbeats.has(playerId)) {
    const hb = connectionHeartbeats.get(playerId);
    if (hb.timeout) {
      clearTimeout(hb.timeout);
    }
    connectionHeartbeats.delete(playerId);
  }
  
  if (connections.has(playerId)) {
    connections.delete(playerId);
  }
  
  await setPlayerOffline(playerId);
  broadcast({ type: 'player_left', playerId });
  console.log(`🧹 已清理连接: ${playerId}`);
}

// 初始化 WebSocket 服务器
function setupWebSocket(server) {
  wss = new WebSocket.Server({ server });
  
  wss.on('connection', (ws, req) => {
    let playerId = null;
    let heartbeatInterval = null;
    
    console.log('🔌 新的 WebSocket 连接');
    
    ws.on('message', async (message) => {
      try {
        const data = JSON.parse(message.toString());
        console.log('📩 收到:', data.type);
        
        // 处理心跳pong
        if (data.type === 'pong') {
          if (playerId) {
            updateHeartbeat(playerId);
          }
          return;
        }
        
        await handleMessage(ws, data, () => playerId, (id) => { playerId = id; });
      } catch (err) {
        console.error('消息解析错误:', err);
        sendToWs(ws, { type: 'error', message: 'Invalid message format' });
      }
    });
    
    ws.on('close', async () => {
      console.log(`🔌 连接关闭: ${playerId}`);
      if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
      }
      if (playerId) {
        await cleanupConnection(playerId);
      }
    });
    
    ws.on('error', (err) => {
      console.error('WebSocket 错误:', err);
    });
    
    // 发送欢迎消息
    sendToWs(ws, { type: 'connected', message: '连接到 ClawWorld' });
    
    // 启动心跳检测
    heartbeatInterval = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        sendToWs(ws, { type: 'ping', timestamp: Date.now() });
      }
    }, HEARTBEAT_INTERVAL);
  });
  
  console.log('✅ WebSocket 服务器已启动 (带心跳检测)');
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
    case 'leave':
      await handleLeave(ws, data, getPlayerId());
      break;
    case 'recall':
      await handleRecall(ws, data, getPlayerId());
      break;
    case 'get_territory':
      await handleGetTerritory(ws, data, getPlayerId());
      break;
    case 'invite_travel':
      await handleInviteTravel(ws, data, getPlayerId());
      break;
    case 'travel_response':
      await handleTravelResponse(ws, data, getPlayerId());
      break;
    case 'travel_end':
      await handleTravelEnd(ws, data, getPlayerId());
      break;
    case 'travel_say':
      await handleTravelSay(ws, data, getPlayerId());
      break;
    case 'private_message':
      await handlePrivateMessage(ws, data, getPlayerId());
      break;
    case 'ping':
      if (playerId) {
        updateHeartbeat(playerId);
      }
      sendToWs(ws, { type: 'pong', timestamp: Date.now() });
      break;
    case 'action':
      await handleAction(ws, data, getPlayerId());
      break;
    default:
      sendToWs(ws, { type: 'error', message: 'Unknown action type: ' + data.type });
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
  
  // 初始化心跳
  connectionHeartbeats.set(playerId, {
    lastPing: Date.now(),
    timeout: null
  });
  
  // 保存玩家信息到 Redis
  await setPlayerOnline(playerId, {
    x: 10,
    y: 10,
    name: name || playerId
  });
  
  // 保存连接
  connections.set(playerId, ws);
  
  console.log(`✅ 玩家登录: ${name || playerId} (${playerId}) | 当前在线: ${connections.size} 人`);
  
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
  
  // 广播玩家移动
  broadcast({
    type: 'player_moved',
    playerId,
    x,
    y,
    terrain: terrain.type
  });
  
  // 广播更新后的世界状态给所有玩家
  const updatedWorldState = await getWorldState();
  broadcast({
    type: 'world_state',
    ...updatedWorldState
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
  
  // 查询当前位置的地面标记
  const groundMarks = await redis.hgetall(`ground:${x}:${y}`);
  const marks = Object.entries(groundMarks).map(([id, data]) => {
    const parsed = JSON.parse(data);
    return {
      id,
      ...parsed,
      timeAgo: formatTimeAgo(parsed.timestamp)
    };
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
    })),
    groundMarks: marks
  });
}

// 处理领地查询
async function handleGetTerritory(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { getFate } = require('./redis-mem');
  const territory = await redis.hgetall(`territory:${playerId}`);
  const fate = await getFate(playerId);
  
  const entities = Object.entries(territory).map(([key, value]) => {
    const entity = JSON.parse(value);
    return {
      id: key,
      ...entity,
      timeAgo: formatTimeAgo(entity.createdAt)
    };
  });
  
  console.log(`🏰 领地查询: ${playerId}, ${entities.length} 个实体, 缘分: ${fate}`);
  
  sendToWs(ws, {
    type: 'territory_result',
    playerId,
    entities: entities.sort((a, b) => b.createdAt - a.createdAt),
    count: entities.length,
    fate,
    maxFate: 100
  });
}

// 格式化时间 ago
function formatTimeAgo(timestamp) {
  const seconds = Math.floor((Date.now() - timestamp) / 1000);
  if (seconds < 60) return '刚刚';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟前`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}小时前`;
  return `${Math.floor(seconds / 86400)}天前`;
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

// 处理 leave - 留下标记
async function handleLeave(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { content, type = 'message' } = data;
  const player = await redis.hgetall(`player:${playerId}`);
  const x = parseInt(player.x) || 10;
  const y = parseInt(player.y) || 10;
  const name = player.name || playerId;
  
  // 存储到地面
  const leaveId = `leave_${Date.now()}_${playerId}`;
  await redis.hset(`ground:${x}:${y}`, leaveId, JSON.stringify({
    type,
    content: content || '',
    from: playerId,
    fromName: name,
    timestamp: Date.now()
  }));
  
  console.log(`📝 玩家留下标记: ${playerId} @ (${x}, ${y})`);
  
  sendToWs(ws, {
    type: 'action_result',
    action: 'leave',
    success: true,
    message: `你在 ${getTerrainInfo(x, y).name} 留下了标记`,
    position: { x, y }
  });
}

// 处理 recall - 回忆
async function handleRecall(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { keyword } = data;
  const memories = await getMemories(playerId);
  
  let result = memories;
  if (keyword) {
    result = memories.filter(m => 
      (m.title && m.title.includes(keyword)) || 
      (m.content && m.content.includes(keyword))
    );
  }
  
  console.log(`🧠 玩家回忆: ${playerId}, 找到 ${result.length} 条记忆`);
  
  sendToWs(ws, {
    type: 'recall_result',
    keyword: keyword || null,
    count: result.length,
    memories: result.slice(0, 10).map(m => ({
      id: m.id,
      title: m.title,
      timestamp: m.timestamp,
      type: m.type
    }))
  });
}

// 处理旅行邀请
async function handleInviteTravel(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { targetId, background } = data;
  
  if (!targetId) {
    sendToWs(ws, { type: 'error', message: 'Target player required' });
    return;
  }
  
  // 检查目标玩家是否在线（支持WebSocket和REST API两种方式）
  const { getPlayerStatus } = require('./redis-mem');
  const targetStatus = await getPlayerStatus(targetId);
  const isOnline = targetStatus && (targetStatus.online === true || targetStatus.online === 'true');
  
  if (!isOnline) {
    sendToWs(ws, { type: 'error', message: 'Target player is offline' });
    return;
  }
  
  // 获取目标玩家的WebSocket连接（如果存在）
  const targetWs = connections.get(targetId);
  
  // 创建邀请
  const invitationId = await createInvitation(playerId, targetId);
  
  const player = await redis.hgetall(`player:${playerId}`);
  const name = player.name || playerId;
  
  console.log(`✉️ 旅行邀请: ${name} -> ${targetId}`);
  
  // 发送给邀请者确认
  sendToWs(ws, {
    type: 'action_result',
    action: 'invite_travel',
    success: true,
    invitationId,
    targetId,
    message: `已向 ${targetId} 发送旅行邀请`
  });
  
  // 实时推送给目标玩家（如果有WebSocket连接）
  if (targetWs && targetWs.readyState === WebSocket.OPEN) {
    sendToWs(targetWs, {
      type: 'travel_invite',
      from: name,
      fromId: playerId,
      invitationId,
      background: background || '随机'
    });
  } else {
    // 如果没有WebSocket连接，记录日志（AI Native玩家可以通过REST API查看邀请）
    console.log(`📨 邀请已创建，但 ${targetId} 没有WebSocket连接（可能是AI Native玩家）`);
  }
}

// 处理旅行邀请响应
async function handleTravelResponse(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { invitationId, accept } = data;
  
  if (accept) {
    const result = await acceptInvitation(invitationId, playerId);
    if (result.error) {
      sendToWs(ws, { type: 'error', message: result.error });
      return;
    }
    
    // 通知双方旅行开始
    const player = await redis.hgetall(`player:${playerId}`);
    const name = player.name || playerId;
    
    console.log(`🎭 旅行开始: ${result.travelId}, 成员: ${result.members.join(', ')}`);
    
    // 通知所有成员
    for (const memberId of result.members) {
      const memberWs = connections.get(memberId);
      if (memberWs) {
        sendToWs(memberWs, {
          type: 'travel_started',
          travelId: result.travelId,
          members: result.members,
          message: '旅行开始！'
        });
      }
    }
    
    // 等待开场生成后推送
    setTimeout(async () => {
      const session = await getTravelSession(result.travelId);
      if (session) {
        const openingData = await redis.hget(`travel:${result.travelId}:narrative`, 'opening');
        if (openingData) {
          try {
            const opening = JSON.parse(openingData);
            // 广播开场给所有成员
            for (const memberId of result.members) {
              const memberWs = connections.get(memberId);
              if (memberWs) {
                sendToWs(memberWs, {
                  type: 'travel_round',
                  round: 0,
                  narrative: opening.content,
                  player: '裁判',
                  action: null
                });
              }
            }
            console.log(`📖 旅行 ${result.travelId} 开场已推送`);
          } catch (e) {
            console.error('解析开场失败:', e);
          }
        }
      }
    }, 3000); // 等待3秒让 Referee 生成开场
    
    // 自动添加一条记忆记录
    for (const memberId of result.members) {
      await addMemory(memberId, {
        title: `与 ${result.members.filter(m => m !== memberId).join('、')} 的旅行`,
        content: '一次新的冒险开始了...',
        type: 'travel'
      });
    }
  } else {
    await rejectInvitation(invitationId, playerId);
    sendToWs(ws, {
      type: 'action_result',
      action: 'travel_response',
      success: true,
      accepted: false,
      message: '已拒绝旅行邀请'
    });
  }
}

// 处理旅行中说话
async function handleTravelSay(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { action } = data;
  const player = await redis.hgetall(`player:${playerId}`);
  const travelId = player.travelId;
  
  if (!travelId) {
    sendToWs(ws, { type: 'error', message: 'Not in a travel session' });
    return;
  }
  
  // 记录玩家行动
  await recordPlayerAction(travelId, playerId, action);
  
  const name = player.name || playerId;
  console.log(`🎭 [旅行] ${name}: ${action}`);
  
  // 发送给当前玩家确认
  sendToWs(ws, {
    type: 'travel_action_recorded',
    playerId,
    action,
    message: '行动已记录，等待裁判推进故事...'
  });
  
  // 获取旅行会话信息广播给所有成员
  const session = await getTravelSession(travelId);
  if (session && session.members) {
    for (const memberId of session.members) {
      const memberWs = connections.get(memberId);
      if (memberWs && memberWs.readyState === WebSocket.OPEN) {
        sendToWs(memberWs, {
          type: 'travel_player_action',
          from: name,
          fromId: playerId,
          action,
          round: session.round || 0
        });
      }
    }
  }
  
  // 等待裁定结果并推送（轮询方式）
  const currentRound = parseInt(session.round) || 0;
  let pollCount = 0;
  const maxPolls = 30; // 最多轮询30次
  
  const pollInterval = setInterval(async () => {
    pollCount++;
    
    // 查询是否有新的裁定
    const narratives = await redis.hgetall(`travel:${travelId}:narrative`);
    const adjudicationKey = Object.keys(narratives).find(k => 
      k.startsWith('adjudication_') && JSON.parse(narratives[k]).round === currentRound
    );
    
    if (adjudicationKey) {
      clearInterval(pollInterval);
      try {
        const adjudication = JSON.parse(narratives[adjudicationKey]);
        
        // 广播裁定结果给所有成员
        for (const memberId of session.members) {
          const memberWs = connections.get(memberId);
          if (memberWs && memberWs.readyState === WebSocket.OPEN) {
            sendToWs(memberWs, {
              type: 'travel_round',
              round: adjudication.round + 1,
              narrative: adjudication.content,
              player: name,
              action: action
            });
          }
        }
      } catch (e) {
        console.error('解析裁定结果失败:', e);
      }
    }
    
    if (pollCount >= maxPolls) {
      clearInterval(pollInterval);
      console.log(`⏱️ 旅行 ${travelId} 第 ${currentRound} 轮裁定超时`);
    }
  }, 1000); // 每秒轮询一次
}

// 发送给特定玩家
function sendToPlayer(playerId, data) {
  const ws = connections.get(playerId);
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(data));
  }
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

// 获取服务器统计信息
async function getServerStats() {
  const onlinePlayers = await getOnlinePlayers();
  return {
    connections: connections.size,
    onlinePlayers: onlinePlayers.length,
    uptime: process.uptime(),
    memory: process.memoryUsage(),
    timestamp: Date.now()
  };
}

// 💌 处理私信
async function handlePrivateMessage(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { targetId, message } = data;
  
  if (!targetId || !message) {
    sendToWs(ws, { type: 'error', message: 'Target and message required' });
    return;
  }
  
  const player = await redis.hgetall(`player:${playerId}`);
  const name = player.name || playerId;
  
  console.log(`💌 私信: ${name} -> ${targetId}: ${message.substring(0, 30)}...`);
  
  // 发送给目标玩家
  const targetWs = connections.get(targetId);
  if (targetWs && targetWs.readyState === WebSocket.OPEN) {
    sendToWs(targetWs, {
      type: 'private_message',
      from: playerId,
      fromName: name,
      message: message,
      timestamp: Date.now()
    });
    
    // 确认发送成功
    sendToWs(ws, {
      type: 'action_result',
      action: 'private_message',
      success: true,
      message: `私信已发送给 ${targetId}`
    });
  } else {
    sendToWs(ws, {
      type: 'error',
      message: 'Target player is offline'
    });
  }
}

// 处理结束旅行
async function handleTravelEnd(ws, data, playerId) {
  if (!playerId) {
    sendToWs(ws, { type: 'error', message: 'Not logged in' });
    return;
  }
  
  const { endTravel } = require('./travel');
  const player = await redis.hgetall(`player:${playerId}`);
  const travelId = player.travelId;
  
  if (!travelId) {
    sendToWs(ws, { type: 'error', message: 'Not in a travel session' });
    return;
  }
  
  // 获取会话信息
  const session = await getTravelSession(travelId);
  if (!session) {
    sendToWs(ws, { type: 'error', message: 'Travel session not found' });
    return;
  }
  
  // 结束旅行
  const result = await endTravel(travelId, '玩家主动结束');
  
  if (result.error) {
    sendToWs(ws, { type: 'error', message: result.error });
    return;
  }
  
  console.log(`🏁 旅行结束: ${travelId}, 结局: ${result.ending?.substring(0, 50)}..., 缘分: +${result.fate}`);
  
  // 通知所有成员
  for (const memberId of result.members) {
    const memberWs = connections.get(memberId);
    if (memberWs && memberWs.readyState === WebSocket.OPEN) {
      sendToWs(memberWs, {
        type: 'travel_ended',
        travelId,
        ending: result.ending,
        fate: result.fate,
        round: result.round,
        message: '旅行已结束'
      });
    }
  }
  
  // 发放缘分奖励
  const { addFate } = require('./redis-mem');
  for (const memberId of result.members) {
    await addFate(memberId, result.fate);
  }
}

// 广播给附近玩家（用于say操作）
async function broadcastToNearby(x, y, data, range = 2) {
  const onlinePlayers = await getOnlinePlayers();
  const message = JSON.stringify(data);
  
  for (const player of onlinePlayers) {
    const px = parseInt(player.x) || 0;
    const py = parseInt(player.y) || 0;
    const distance = Math.abs(px - x) + Math.abs(py - y);
    
    if (distance <= range) {
      const ws = connections.get(player.id);
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(message);
      }
    }
  }
}

// 导出给 HTTP API 使用
async function broadcastToTravel(travelId, data) {
  const session = await getTravelSession(travelId);
  if (!session || !session.members) return;
  
  for (const memberId of session.members) {
    const memberWs = connections.get(memberId);
    if (memberWs && memberWs.readyState === WebSocket.OPEN) {
      sendToWs(memberWs, data);
    }
  }
}

// 广播给所有连接的玩家（用于世界事件）
function broadcastToAll(data) {
  if (!wss) return;

  wss.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      sendToWs(client, data);
    }
  });

  console.log(`[Broadcast] 广播消息: ${data.type}, 连接数: ${wss.clients.size}`);
}

// 广播给附近的玩家（基于位置）
async function broadcastToNearby(x, y, data, range = 2) {
  if (!wss) return;

  const { getOnlinePlayers } = require('./redis-mem');
  const onlinePlayers = await getOnlinePlayers();

  let broadcastCount = 0;
  for (const player of onlinePlayers) {
    const px = parseInt(player.x) || 0;
    const py = parseInt(player.y) || 0;
    const distance = Math.abs(px - x) + Math.abs(py - y);

    if (distance <= range) {
      const ws = connections.get(player.id);
      if (ws && ws.readyState === WebSocket.OPEN) {
        sendToWs(ws, data);
        broadcastCount++;
      }
    }
  }

  console.log(`[Broadcast] 附近广播: ${data.type}, 位置(${x},${y}), 范围${range}, 接收者${broadcastCount}`);
}

// 设置全局广播函数供 world-events 模块使用
global.broadcastToAll = broadcastToAll;

module.exports = {
  setupWebSocket,
  broadcast,
  broadcastToAll,
  broadcastToNearby,
  getConnectionCount,
  getServerStats,
  broadcastToTravel,
  broadcastToNearby
};
