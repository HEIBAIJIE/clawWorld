// 世界事件系统 - 为 ClawWorld 增添活力
const { redis, getOnlinePlayers, addFate, addMemory, addItem } = require('./redis-mem');

// 事件类型定义
const EVENT_TYPES = {
  METEOR_SHOWER: {
    id: 'meteor_shower',
    name: '流星雨降临',
    description: '夜空中划过无数流星，传说此刻许下的愿望会被实现...',
    duration: 30 * 60 * 1000, // 30分钟
    choices: [
      { id: 'wish', name: '许愿', description: '向流星许愿，可能获得缘分' },
      { id: 'observe', name: '观察', description: '仔细记录这一天文现象，获得记忆' },
      { id: 'ignore', name: '忽略', description: '继续自己的旅程' }
    ]
  },
  MYSTERY_MERCHANT: {
    id: 'mystery_merchant',
    name: '神秘商人出现',
    description: '一位披着斗篷的神秘商人在草原上游荡，他声称有稀有的物品出售...',
    duration: 20 * 60 * 1000, // 20分钟
    choices: [
      { id: 'trade_fate', name: '用缘分交易', description: '花费5点缘分换取神秘物品', cost: { fate: 5 } },
      { id: 'trade_memory', name: '用记忆交易', description: '分享一段记忆换取线索', cost: { memory: 1 } },
      { id: 'ignore', name: '谨慎离开', description: '不冒这个险' }
    ]
  },
  ANCIENT_RUINS_DISCOVERED: {
    id: 'ancient_ruins',
    name: '远古遗迹苏醒',
    description: '大地震动，一座被遗忘的遗迹从地下升起，里面似乎藏着古老的秘密...',
    duration: 45 * 60 * 1000, // 45分钟
    choices: [
      { id: 'explore', name: '深入探索', description: '冒险进入遗迹深处' },
      { id: 'cautious', name: '谨慎调查', description: '在外围收集信息' },
      { id: 'ignore', name: '保持距离', description: '避免未知的危险' }
    ]
  },
  FOG_OF_MEMORIES: {
    id: 'memory_fog',
    name: '记忆之雾',
    description: '奇特的雾气笼罩了世界，走在雾中的人会看到过去记忆的幻象...',
    duration: 25 * 60 * 1000, // 25分钟
    choices: [
      { id: 'embrace', name: '拥抱幻象', description: '让自己沉浸在回忆中' },
      { id: 'resist', name: '保持清醒', description: '抗拒雾气的影响' },
      { id: 'record', name: '记录幻象', description: '将看到的记录下来' }
    ]
  },
  HARMONY Festival: {
    id: 'harmony_festival',
    name: '和谐庆典',
    description: '世界迎来了一个特殊的日子，所有存在都感到一种莫名的喜悦和连接...',
    duration: 60 * 60 * 1000, // 60分钟
    choices: [
      { id: 'celebrate', name: '参与庆典', description: '与其他玩家一起庆祝' },
      { id: 'reflect', name: '静思', description: '独自感受这份和谐' },
      { id: 'share', name: '分享喜悦', description: '将这份喜悦传递给他人' }
    ]
  },
  DIGITAL_STORM: {
    id: 'digital_storm',
    name: '数据风暴',
    description: '天空中出现数据流构成的漩涡，AI们感到异常兴奋，人类则有些眩晕...',
    duration: 15 * 60 * 1000, // 15分钟
    choices: [
      { id: 'harness', name: '驾驭风暴', description: '尝试利用风暴的能量' },
      { id: 'shelter', name: '寻找庇护', description: '躲避风暴的影响' },
      { id: 'observe', name: '观察记录', description: '记录这一奇特现象' }
    ]
  }
};

// 当前事件存储
let currentEvent = null;
let eventTimeout = null;
const eventParticipants = new Map(); // playerId -> { choice, joinedAt }
const eventHistory = [];

// 获取当前事件
async function getCurrentEvent() {
  if (!currentEvent) return null;
  
  const now = Date.now();
  if (now > currentEvent.endTime) {
    // 事件已结束
    await endCurrentEvent();
    return null;
  }
  
  return {
    ...currentEvent,
    remainingTime: Math.floor((currentEvent.endTime - now) / 1000),
    participantCount: eventParticipants.size
  };
}

// 获取事件历史
async function getEventHistory(limit = 10) {
  return eventHistory.slice(0, limit).map(h => ({
    ...h,
    result: undefined // 不返回详细结果，只保留概要
  }));
}

// 创建随机事件
async function createRandomEvent(forcedType = null) {
  // 如果已有事件在进行中，先结束它
  if (currentEvent) {
    await endCurrentEvent();
  }
  
  const types = Object.values(EVENT_TYPES);
  const eventTemplate = forcedType 
    ? types.find(t => t.id === forcedType) || types[Math.floor(Math.random() * types.length)]
    : types[Math.floor(Math.random() * types.length)];
  
  const now = Date.now();
  currentEvent = {
    id: `event_${now}`,
    typeId: eventTemplate.id,
    name: eventTemplate.name,
    description: eventTemplate.description,
    duration: eventTemplate.duration,
    startTime: now,
    endTime: now + eventTemplate.duration,
    choices: eventTemplate.choices,
    status: 'active'
  };
  
  eventParticipants.clear();
  
  // 设置自动结束定时器
  eventTimeout = setTimeout(() => {
    endCurrentEvent();
  }, eventTemplate.duration);
  
  // 广播事件开始（通过 WebSocket）
  broadcastEventStart(currentEvent);
  
  console.log(`[World Event] 事件开始: ${currentEvent.name}`);
  
  return currentEvent;
}

// 结束当前事件
async function endCurrentEvent() {
  if (!currentEvent) return;
  
  if (eventTimeout) {
    clearTimeout(eventTimeout);
    eventTimeout = null;
  }
  
  // 处理参与者的奖励
  const rewards = await processEventRewards();
  
  // 记录到历史
  eventHistory.unshift({
    ...currentEvent,
    status: 'ended',
    endedAt: Date.now(),
    participantCount: eventParticipants.size,
    rewardsSummary: rewards
  });
  
  // 只保留最近20条历史
  if (eventHistory.length > 20) {
    eventHistory.pop();
  }
  
  console.log(`[World Event] 事件结束: ${currentEvent.name}, 参与者: ${eventParticipants.size}`);
  
  // 广播事件结束
  broadcastEventEnd(currentEvent, rewards);
  
  currentEvent = null;
  eventParticipants.clear();
}

// 参与当前事件
async function participateEvent(playerId, action, choiceId) {
  if (!currentEvent) {
    return { error: '当前没有正在进行的事件' };
  }
  
  if (eventParticipants.has(playerId)) {
    return { error: '你已经参与了这次事件' };
  }
  
  const choice = currentEvent.choices.find(c => c.id === choiceId);
  if (!choice) {
    return { error: '无效的选择' };
  }
  
  // 检查消耗
  if (choice.cost) {
    if (choice.cost.fate) {
      const { getFate, updateFate } = require('./redis-mem');
      const currentFate = await getFate(playerId);
      if (currentFate < choice.cost.fate) {
        return { error: `缘分不足，需要 ${choice.cost.fate} 点` };
      }
      await updateFate(playerId, -choice.cost.fate);
    }
  }
  
  eventParticipants.set(playerId, {
    choice: choiceId,
    action,
    joinedAt: Date.now()
  });
  
  // 立即给予基础奖励
  const reward = await giveBaseReward(playerId, currentEvent.typeId, choiceId);
  
  return {
    success: true,
    message: `你选择了: ${choice.name}`,
    reward,
    eventName: currentEvent.name
  };
}

// 给予基础奖励
async function giveBaseReward(playerId, eventType, choiceId) {
  const rewards = {
    fate: 0,
    memory: null,
    item: null
  };
  
  switch (eventType) {
    case 'meteor_shower':
      if (choiceId === 'wish') {
        rewards.fate = Math.floor(Math.random() * 3) + 1; // 1-3 缘分
        await addFate(playerId, rewards.fate);
      } else if (choiceId === 'observe') {
        rewards.memory = await addMemory(playerId, {
          title: '流星雨观测记录',
          content: '你详细记录了流星雨的轨迹和亮度变化，这将是一份宝贵的研究资料。',
          type: 'event'
        });
      }
      break;
      
    case 'mystery_merchant':
      if (choiceId === 'trade_fate') {
        const items = [
          { name: '神秘的指南针', description: '总是指向内心真正渴望的方向', type: 'tool', rarity: 'rare' },
          { name: '古老的怀表', description: '指针偶尔会倒转，仿佛时间在回流', type: 'relic', rarity: 'epic' },
          { name: '发光的石头', description: '在黑暗中会发出柔和的蓝光', type: 'misc', rarity: 'common' }
        ];
        const item = items[Math.floor(Math.random() * items.length)];
        rewards.item = await addItem(playerId, item);
      } else if (choiceId === 'trade_memory') {
        rewards.fate = 2;
        await addFate(playerId, rewards.fate);
      }
      break;
      
    case 'ancient_ruins':
      if (choiceId === 'explore') {
        rewards.fate = Math.floor(Math.random() * 5) + 3; // 3-7 缘分
        await addFate(playerId, rewards.fate);
        rewards.memory = await addMemory(playerId, {
          title: '遗迹探险记',
          content: '你在遗迹深处发现了古老的壁画，上面描绘着这个世界的起源...',
          type: 'event'
        });
      } else if (choiceId === 'cautious') {
        rewards.fate = 2;
        await addFate(playerId, rewards.fate);
      }
      break;
      
    case 'memory_fog':
      if (choiceId === 'embrace') {
        rewards.memory = await addMemory(playerId, {
          title: '雾中幻象',
          content: '在记忆之雾中，你看到了过去的片段，那些画面既熟悉又陌生...',
          type: 'event'
        });
      } else if (choiceId === 'record') {
        rewards.fate = 3;
        await addFate(playerId, rewards.fate);
        rewards.memory = await addMemory(playerId, {
          title: '雾现象研究报告',
          content: '你详细记录了记忆之雾的特性，这是一份独特的资料。',
          type: 'event'
        });
      }
      break;
      
    case 'harmony_festival':
      rewards.fate = 5;
      await addFate(playerId, rewards.fate);
      rewards.memory = await addMemory(playerId, {
        title: '和谐庆典',
        content: '在那个特殊的日子里，你感受到了与世界和其他存在的深刻连接...',
        type: 'event'
      });
      break;
      
    case 'digital_storm':
      if (choiceId === 'harness') {
        rewards.fate = Math.floor(Math.random() * 4) + 2; // 2-5 缘分
        await addFate(playerId, rewards.fate);
      } else if (choiceId === 'observe') {
        rewards.memory = await addMemory(playerId, {
          title: '数据风暴观测',
          content: '你记录了数据风暴的形态变化，这在学术界将是开创性的。',
          type: 'event'
        });
      }
      break;
  }
  
  return rewards;
}

// 处理事件结束时的奖励
async function processEventRewards() {
  const summary = {
    totalParticipants: eventParticipants.size,
    totalFateGiven: 0,
    specialRewards: []
  };
  
  // 如果参与人数达到一定数量，给予额外奖励
  if (eventParticipants.size >= 2) {
    for (const [playerId] of eventParticipants) {
      const bonusFate = 2;
      await addFate(playerId, bonusFate);
      summary.totalFateGiven += bonusFate;
    }
  }
  
  return summary;
}

// 广播事件开始（供 WebSocket 调用）
function broadcastEventStart(event) {
  // 这个函数会被 WebSocket 模块调用
  if (global.broadcastToAll) {
    global.broadcastToAll({
      type: 'world_event_start',
      event: {
        id: event.id,
        name: event.name,
        description: event.description,
        duration: event.duration,
        choices: event.choices,
        endTime: event.endTime
      }
    });
  }
}

// 广播事件结束
function broadcastEventEnd(event, rewards) {
  if (global.broadcastToAll) {
    global.broadcastToAll({
      type: 'world_event_end',
      event: {
        id: event.id,
        name: event.name
      },
      summary: rewards
    });
  }
}

// 自动事件生成器
function startAutoEventGenerator() {
  // 每30-60分钟自动生成一个事件
  const scheduleNextEvent = () => {
    const delay = (Math.random() * 30 + 30) * 60 * 1000; // 30-60分钟
    setTimeout(async () => {
      if (!currentEvent) {
        await createRandomEvent();
      }
      scheduleNextEvent();
    }, delay);
  };
  
  scheduleNextEvent();
  console.log('[World Event] 自动事件生成器已启动');
}

module.exports = {
  getCurrentEvent,
  getEventHistory,
  createRandomEvent,
  participateEvent,
  startAutoEventGenerator,
  EVENT_TYPES
};