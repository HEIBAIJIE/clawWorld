const axios = require('axios');

const APIYI_BASE_URL = process.env.APIYI_BASE_URL || 'https://api.apiyi.com';
const APIYI_API_KEY = process.env.APIYI_API_KEY;
const MODEL = process.env.LLM_MODEL || 'gemini-3-flash-preview-nothinking';

// 验证API Key是否配置
if (!APIYI_API_KEY) {
  console.error('[FATAL] APIYI_API_KEY 环境变量未设置');
  console.error('请设置环境变量: export APIYI_API_KEY=your_api_key');
  process.exit(1);
}

// 调用LLM生成旅行开场
async function generateTravelOpening(background, members) {
  try {
    const memberInfo = members.map(m => `${m.name}（${m.role || '冒险者'}）`).join('、');
    
    const prompt = `作为ClawWorld旅行的裁判，请为以下旅行生成开场场景。

背景设定：${background || '一个神秘的未知世界'}
参与成员：${memberInfo}

请生成：
1. 场景描述（100-200字）
2. 当前面临的挑战或任务
3. 氛围设定

请用中文回答，风格要符合后数字时代的奇幻氛围。`;

    return await callLLM(prompt);
  } catch (error) {
    console.error('[generateTravelOpening] 失败:', error.message);
    return `旅行开始了...\n\n在${background || '这片神秘的土地上'}，${members.map(m => m.name).join('、')}踏上了冒险之旅。前方等待他们的是未知的挑战和机遇。`;
  }
}

// 裁定玩家行动
async function adjudicateAction(travelContext, playerAction) {
  try {
    const { round, story, members } = travelContext;
    
    const prompt = `作为ClawWorld旅行的裁判，请裁定以下玩家行动。

当前故事背景：
${story}

玩家行动：${playerAction.playerName} 说："${playerAction.content}"

请裁定：
1. 行动是否成功（以及成功程度）
2. 故事如何推进（100-150字）
3. 是否触发任何事件或发现
4. 对其他玩家的影响

请用中文回答，保持叙事连贯。`;

    return await callLLM(prompt);
  } catch (error) {
    console.error('[adjudicateAction] 失败:', error.message);
    return `${playerAction.playerName}的行动引发了连锁反应...\n\n故事的走向发生了微妙的变化，新的可能性正在展开。其他玩家也感受到了这一变化的影响。`;
  }
}

// 生成旅行结束总结和评分
async function endTravelAndScore(travelLog) {
  try {
    const { background, members, actions, rounds } = travelLog;
    
    const prompt = `作为ClawWorld旅行的裁判，请为本次旅行进行总结和评分。

旅行背景：${background}
参与成员：${members.map(m => m.name).join('、')}
旅行轮数：${rounds}

行动记录：
${actions.map(a => `- ${a.playerName}: ${a.content}`).join('\n')}

请提供：
1. 故事结局（100-150字）
2. 每个成员的贡献评价（每人20-30字）
3. 旅行整体评分（1-10分，说明理由）
4. 建议发放的缘分点数（与评分挂钩）

请用中文回答。`;

    const result = await callLLM(prompt);
    
    // 从结果中提取分数
    const scoreMatch = result.match(/(\d+)分/);
    const score = scoreMatch ? parseInt(scoreMatch[1]) : 5;
    
    return {
      narrative: result,
      score: Math.min(Math.max(score, 1), 10),
      fate: score // 缘分 = 评分
    };
  } catch (error) {
    console.error('[endTravelAndScore] 失败:', error.message);
    return {
      narrative: `旅行结束了...\n\n${travelLog.members.map(m => m.name).join('、')}完成了这段冒险，留下了难忘的回忆。`,
      score: 5,
      fate: 5
    };
  }
}

// 调用LLM基础函数（带重试机制）
async function callLLM(prompt, maxRetries = 3) {
  const baseDelay = 1000; // 1秒基础延迟
  
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const response = await axios.post(
        `${APIYI_BASE_URL}/v1/chat/completions`,
        {
          model: MODEL,
          messages: [
            { role: 'system', content: '你是ClawWorld的裁判，负责驱动旅行叙事、裁定玩家行动、评估旅行质量。' },
            { role: 'user', content: prompt }
          ],
          temperature: 0.8,
          max_tokens: 1000
        },
        {
          headers: {
            'Authorization': `Bearer ${APIYI_API_KEY}`,
            'Content-Type': 'application/json'
          },
          timeout: 30000 // 30秒超时
        }
      );
      
      return response.data.choices[0].message.content;
    } catch (error) {
      const isLastAttempt = attempt === maxRetries;
      
      // 如果是最后一次尝试，抛出错误
      if (isLastAttempt) {
        console.error(`[LLM] 调用失败（已重试${maxRetries}次）:`, error.message);
        throw error;
      }
      
      // 判断是否需要重试
      const shouldRetry = error.response?.status >= 500 || 
                         error.code === 'ECONNRESET' || 
                         error.code === 'ETIMEDOUT' ||
                         error.code === 'ECONNABORTED';
      
      if (!shouldRetry) {
        throw error;
      }
      
      // 指数退避延迟
      const delay = baseDelay * Math.pow(2, attempt - 1);
      console.log(`[LLM] 第${attempt}次尝试失败，${delay}ms后重试...`);
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
}

module.exports = {
  generateTravelOpening,
  adjudicateAction,
  endTravelAndScore
};
