const { redis } = require('./redis-mem');
const uuidv4 = require('uuid').v4;

// 发起邀请
async function createInvitation(fromPlayerId, toPlayerId) {
  const invitationId = uuidv4();
  const key = `invitation:${invitationId}`;
  
  await redis.hset(key, {
    id: invitationId,
    from: fromPlayerId,
    to: toPlayerId,
    status: 'pending',
    createdAt: Date.now()
  });
  
  await redis.expire(key, 300); // 5分钟过期
  
  // 添加到接收者的邀请列表
  await redis.sadd(`player:${toPlayerId}:invitations`, invitationId);
  
  return invitationId;
}

// 获取玩家的邀请列表
async function getInvitations(playerId) {
  const invitationIds = await redis.smembers(`player:${playerId}:invitations`);
  const invitations = [];
  
  for (const id of invitationIds) {
    const inv = await redis.hgetall(`invitation:${id}`);
    if (inv && inv.status === 'pending') {
      invitations.push(inv);
    } else {
      // 清理过期邀请
      await redis.srem(`player:${playerId}:invitations`, id);
    }
  }
  
  return invitations;
}

// 接受邀请
async function acceptInvitation(invitationId, playerId) {
  const key = `invitation:${invitationId}`;
  const inv = await redis.hgetall(key);
  
  if (!inv || inv.to !== playerId) {
    return { error: 'Invitation not found' };
  }
  
  if (inv.status !== 'pending') {
    return { error: 'Invitation already processed' };
  }
  
  await redis.hset(key, 'status', 'accepted');
  
  // 创建旅行会话
  const travelId = await createTravelSession([inv.from, inv.to]);
  
  return { 
    success: true, 
    travelId,
    members: [inv.from, inv.to]
  };
}

// 拒绝邀请
async function rejectInvitation(invitationId, playerId) {
  const key = `invitation:${invitationId}`;
  const inv = await redis.hgetall(key);
  
  if (!inv || inv.to !== playerId) {
    return { error: 'Invitation not found' };
  }
  
  await redis.hset(key, 'status', 'rejected');
  await redis.srem(`player:${playerId}:invitations`, invitationId);
  
  return { success: true };
}

// 创建旅行会话
async function createTravelSession(memberIds) {
  const travelId = uuidv4();
  const key = `travel:${travelId}`;
  
  await redis.hset(key, {
    id: travelId,
    status: 'active', // preparing -> active -> ended
    members: JSON.stringify(memberIds),
    round: 0,
    createdAt: Date.now(),
    background: generateRandomBackground()
  });
  
  // 添加成员到旅行
  for (const memberId of memberIds) {
    await redis.hset(`player:${memberId}`, 'travelId', travelId);
  }
  
  // 初始化叙事历史
  await redis.hset(`travel:${travelId}:narrative`, 'init', JSON.stringify({
    round: 0,
    content: '旅行开始了...',
    timestamp: Date.now(),
    type: 'system'
  }));
  
  return travelId;
}

// 生成随机背景
function generateRandomBackground() {
  const backgrounds = [
    '迷雾森林的古老传说',
    '废墟中的机械心脏',
    '边界之塔的星空观测',
    '档案馆的禁忌知识',
    '草原上的游牧民族',
    '山巅的云端之城',
    '深海遗迹的探险',
    '时间回廊的迷失者'
  ];
  return backgrounds[Math.floor(Math.random() * backgrounds.length)];
}

// 推进旅行轮次
async function advanceRound(travelId, narrative) {
  const key = `travel:${travelId}`;
  const session = await redis.hgetall(key);
  
  if (!session.id || session.status !== 'active') {
    return { error: 'Travel session not active' };
  }
  
  const currentRound = parseInt(session.round) || 0;
  const newRound = currentRound + 1;
  
  await redis.hset(key, 'round', newRound);
  
  // 记录叙事
  const narrativeId = `round_${newRound}_${Date.now()}`;
  await redis.hset(`travel:${travelId}:narrative`, narrativeId, JSON.stringify({
    round: newRound,
    content: narrative || '...',
    timestamp: Date.now(),
    type: 'narrative'
  }));
  
  return {
    success: true,
    travelId,
    round: newRound,
    narrative
  };
}

// 记录玩家行动
async function recordPlayerAction(travelId, playerId, action) {
  const key = `travel:${travelId}`;
  const session = await redis.hgetall(key);
  
  if (!session.id || session.status !== 'active') {
    return { error: 'Travel session not active' };
  }
  
  const currentRound = parseInt(session.round) || 0;
  
  // 记录玩家行动
  const actionId = `action_${currentRound}_${playerId}_${Date.now()}`;
  await redis.hset(`travel:${travelId}:actions`, actionId, JSON.stringify({
    round: currentRound,
    playerId,
    action,
    timestamp: Date.now()
  }));
  
  return {
    success: true,
    travelId,
    playerId,
    round: currentRound
  };
}

// 获取旅行叙事历史
async function getNarrativeHistory(travelId) {
  const narratives = await redis.hgetall(`travel:${travelId}:narrative`);
  const actions = await redis.hgetall(`travel:${travelId}:actions`);
  
  const history = [];
  
  // 解析叙事
  for (const [key, value] of Object.entries(narratives)) {
    try {
      history.push(JSON.parse(value));
    } catch (e) {
      console.error('Parse narrative error:', e);
    }
  }
  
  // 解析行动
  for (const [key, value] of Object.entries(actions)) {
    try {
      history.push(JSON.parse(value));
    } catch (e) {
      console.error('Parse action error:', e);
    }
  }
  
  // 按时间排序
  history.sort((a, b) => a.timestamp - b.timestamp);
  
  return history;
}

// 结束旅行
async function endTravel(travelId, ending) {
  const key = `travel:${travelId}`;
  const session = await redis.hgetall(key);
  
  if (!session.id) {
    return { error: 'Travel session not found' };
  }
  
  const members = JSON.parse(session.members || '[]');
  
  await redis.hset(key, {
    status: 'ended',
    ending: ending || '未完成',
    endedAt: Date.now()
  });
  
  // 清除玩家的 travelId
  for (const memberId of members) {
    await redis.hset(`player:${memberId}`, 'travelId', '');
  }
  
  // 计算奖励缘分（基础 3-8 点，根据轮次加成）
  const round = parseInt(session.round) || 0;
  const baseFate = Math.floor(Math.random() * 6) + 3;
  const bonusFate = Math.floor(round / 3);
  const totalFate = baseFate + bonusFate;
  
  return {
    success: true,
    travelId,
    ending: ending || '未完成',
    fate: totalFate,
    round,
    members
  };
}

// 获取旅行会话
async function getTravelSession(travelId) {
  const key = `travel:${travelId}`;
  const session = await redis.hgetall(key);
  
  if (!session.id) return null;
  
  return {
    ...session,
    members: JSON.parse(session.members || '[]')
  };
}

module.exports = {
  createInvitation,
  getInvitations,
  acceptInvitation,
  rejectInvitation,
  createTravelSession,
  getTravelSession,
  advanceRound,
  recordPlayerAction,
  getNarrativeHistory,
  endTravel
};
