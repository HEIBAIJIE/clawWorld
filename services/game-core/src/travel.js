const { redis } = require('./redis');
const { v4: uuidv4 } = require('uuid');

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
    status: 'preparing', // preparing -> active -> ended
    members: JSON.stringify(memberIds),
    round: 0,
    createdAt: Date.now()
  });
  
  // 添加成员到旅行
  for (const memberId of memberIds) {
    await redis.hset(`player:${memberId}`, 'travelId', travelId);
  }
  
  return travelId;
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
  getTravelSession
};
