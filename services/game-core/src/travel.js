const { redis } = require('./redis-mem');
const CONFIG = require('./config');
const uuidv4 = require('uuid').v4;
const axios = require('axios');

const REFEREE_URL = CONFIG.REFEREE_URL;

// ========== 旅行队列系统（解决竞态条件）==========
const travelQueues = new Map(); // travelId -> queue

// 获取或创建旅行队列
function getTravelQueue(travelId) {
  if (!travelQueues.has(travelId)) {
    travelQueues.set(travelId, []);
  }
  return travelQueues.get(travelId);
}

// 添加任务到队列
async function queueTask(travelId, task) {
  const queue = getTravelQueue(travelId);
  
  return new Promise((resolve, reject) => {
    queue.push({ task, resolve, reject });
    
    // 如果队列只有一个任务，立即处理
    if (queue.length === 1) {
      processQueue(travelId);
    }
  });
}

// 处理队列
async function processQueue(travelId) {
  const queue = getTravelQueue(travelId);
  
  while (queue.length > 0) {
    const { task, resolve, reject } = queue[0];
    
    try {
      const result = await task();
      resolve(result);
    } catch (error) {
      reject(error);
    }
    
    queue.shift();
    
    // 清理空队列
    if (queue.length === 0) {
      travelQueues.delete(travelId);
    }
  }
}

// ========== Referee 调用 ==========

// 调用 Referee 服务生成旅行开场
async function generateOpeningFromReferee(travelId, background, members) {
  try {
    const memberInfo = members.map(id => ({ name: id, role: '冒险者' }));
    
    const response = await axios.post(`${REFEREE_URL}/travel/opening`, {
      background,
      members: memberInfo
    }, {
      timeout: 10000 // 10秒超时
    });
    
    if (response.data.success) {
      // 保存开场到旅行会话
      await redis.hset(`travel:${travelId}:narrative`, 'opening', JSON.stringify({
        round: 0,
        content: response.data.opening,
        timestamp: Date.now(),
        type: 'opening'
      }));
      
      return response.data.opening;
    }
  } catch (error) {
    console.error('调用 Referee 开场失败:', error.message);
    return null;
  }
}

// 调用 Referee 服务裁定玩家行动
async function adjudicateActionFromReferee(travelId, playerAction) {
  try {
    // 获取旅行上下文
    const session = await getTravelSession(travelId);
    if (!session) return null;
    
    // 获取叙事历史
    const narratives = await redis.hgetall(`travel:${travelId}:narrative`);
    const storyHistory = Object.values(narratives).map(n => {
      try { return JSON.parse(n).content; } catch(e) { return ''; }
    }).join('\n');
    
    const response = await axios.post(`${REFEREE_URL}/travel/adjudicate`, {
      travelContext: {
        round: parseInt(session.round) || 0,
        story: storyHistory,
        members: JSON.parse(session.members || '[]')
      },
      playerAction: {
        playerName: playerAction.playerId,
        content: playerAction.action
      }
    }, {
      timeout: 10000 // 10秒超时
    });
    
    if (response.data.success) {
      return response.data.adjudication;
    }
  } catch (error) {
    console.error('调用 Referee 裁定失败:', error.message);
    return null;
  }
  return null;
}

// 调用 Referee 服务结束旅行
async function endTravelFromReferee(travelId) {
  try {
    const session = await getTravelSession(travelId);
    if (!session) return null;
    
    // 获取所有行动记录
    const actions = await redis.hgetall(`travel:${travelId}:actions`);
    const actionList = Object.values(actions).map(a => {
      try { return JSON.parse(a); } catch(e) { return null; }
    }).filter(Boolean);
    
    const response = await axios.post(`${REFEREE_URL}/travel/end`, {
      travelLog: {
        background: session.background || '未知背景',
        members: JSON.parse(session.members || '[]').map(m => ({ name: m })),
        actions: actionList.map(a => ({ playerName: a.playerId, content: a.action })),
        rounds: parseInt(session.round) || 0
      }
    });
    
    if (response.data.success) {
      return {
        ending: response.data.narrative,
        score: response.data.score,
        fate: response.data.fate
      };
    }
  } catch (error) {
    console.error('调用 Referee 结束旅行失败:', error.message);
    return null;
  }
  return null;
}
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
  
  const background = generateRandomBackground();
  
  await redis.hset(key, {
    id: travelId,
    status: 'preparing', // preparing -> active -> ended
    members: JSON.stringify(memberIds),
    round: 0,
    createdAt: Date.now(),
    background: background
  });
  
  // 添加成员到旅行
  for (const memberId of memberIds) {
    await redis.hset(`player:${memberId}`, 'travelId', travelId);
  }
  
  // 使用队列序列化处理开场生成
  queueTask(travelId, async () => {
    const opening = await generateOpeningFromReferee(travelId, background, memberIds);
    if (opening) {
      console.log(`✅ 旅行 ${travelId} 开场已生成`);
      await redis.hset(key, 'status', 'active');
    } else {
      console.log(`⚠️ 旅行 ${travelId} 开场生成失败，使用默认开场`);
      await redis.hset(`travel:${travelId}:narrative`, 'opening', JSON.stringify({
        round: 0,
        content: '旅行开始了...一场未知的冒险等待着你们。',
        timestamp: Date.now(),
        type: 'opening'
      }));
      await redis.hset(key, 'status', 'active');
    }
  }).catch(err => console.error(`[Travel] 开场生成队列错误:`, err));
  
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
  
  // 使用队列序列化处理裁定
  queueTask(travelId, async () => {
    const adjudication = await adjudicateActionFromReferee(travelId, { playerId, action });
    if (adjudication) {
      // 保存裁定结果
      await redis.hset(`travel:${travelId}:narrative`, `adjudication_${currentRound}_${Date.now()}`, JSON.stringify({
        round: currentRound,
        content: adjudication,
        playerId,
        timestamp: Date.now(),
        type: 'adjudication'
      }));
      
      // 推进轮次
      await advanceRound(travelId, adjudication);
      
      console.log(`✅ 旅行 ${travelId} 第 ${currentRound} 轮裁定完成`);
    }
  }).catch(err => console.error(`[Travel] 裁定队列错误:`, err));
  
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
  
  // 尝试调用 Referee 获取更好的结局
  const refereeResult = await endTravelFromReferee(travelId);
  
  const finalEnding = refereeResult?.ending || ending || '未完成';
  const finalFate = refereeResult?.fate || (parseInt(session.round) || 0) + 3;
  
  await redis.hset(key, {
    status: 'ended',
    ending: finalEnding,
    fate: finalFate,
    endedAt: Date.now()
  });
  
  // 清除玩家的 travelId
  for (const memberId of members) {
    await redis.hset(`player:${memberId}`, 'travelId', '');
  }
  
  return {
    success: true,
    travelId,
    ending: finalEnding,
    fate: finalFate,
    round: parseInt(session.round) || 0,
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
  endTravel,
  getOpeningFromReferee: generateOpeningFromReferee,
  getAdjudicationFromReferee: adjudicateActionFromReferee
};
