// LLM裁判服务 - 调用API易的Gemini模型
// 模型: gemini-3-flash-preview-nothinking
// 基础地址: https://api.apiyi.com

const APIYI_BASE_URL = 'https://api.apiyi.com';
const APIYI_TOKEN = 'sk-uX8hVbhIM27Xt4iJE84b79900eAa4931B0122034Bb092510';

/**
 * 调用LLM生成旅行叙事
 * @param {Object} travelContext - 旅行上下文
 * @param {Array} history - 历史轮次
 * @param {Object} playerAction - 玩家行动
 */
async function generateNarrative(travelContext, history, playerAction) {
    const prompt = buildRefereePrompt(travelContext, history, playerAction);
    
    try {
        const response = await fetch(`${APIYI_BASE_URL}/v1/chat/completions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${APIYI_TOKEN}`
            },
            body: JSON.stringify({
                model: 'gemini-3-flash-preview-nothinking',
                messages: [
                    {
                        role: 'system',
                        content: `你是ClawWorld的裁判（Game Master）。
你的职责是根据玩家角色和背景，推进故事发展。

规则：
1. 根据故事背景设定场景
2. 响应玩家的角色扮演行动
3. 推进情节，制造冲突或转折
4. 必要时询问其他玩家行动
5. 保持故事的连贯性和趣味性

输出格式（JSON）：
{
    "narrative": "场景描述和发生的事情",
    "next": "等待哪位玩家行动，或继续推进",
    "ending": "null或结局类型(GE/BE)"
}`
                    },
                    {
                        role: 'user',
                        content: prompt
                    }
                ],
                temperature: 0.8,
                max_tokens: 1000
            })
        });
        
        const data = await response.json();
        return parseResponse(data.choices[0].message.content);
    } catch (error) {
        console.error('LLM调用失败:', error);
        return {
            narrative: '（裁判暂时离线，请稍后再试）',
            next: 'retry'
        };
    }
}

/**
 * 构建裁判提示词
 */
function buildRefereePrompt(context, history, action) {
    return `【旅行背景】
${context.background || '随机生成的冒险世界'}

【参与者】
${context.players.map(p => `- ${p.name}: ${p.role}`).join('\n')}

【故事进展】
${history.map((h, i) => `第${i+1}轮：${h.player} ${h.action} → ${h.result}`).join('\n') || '故事刚开始'}

【当前行动】
玩家 ${action.player}（${action.role}）："${action.content}"

请生成叙事响应，推进故事发展。`;
}

/**
 * 解析LLM响应
 */
function parseResponse(content) {
    try {
        // 尝试解析JSON
        const jsonMatch = content.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
            return JSON.parse(jsonMatch[0]);
        }
    } catch (e) {
        // 解析失败，返回文本
    }
    
    return {
        narrative: content,
        next: 'continue'
    };
}

/**
 * 评分旅行质量（1-10）
 */
async function scoreTravel(travelLog) {
    const prompt = `请为以下旅行经历评分（1-10分）：

${travelLog.map((log, i) => `第${i+1}轮：${log.player}: ${log.action}`).join('\n')}

评分标准：
- 故事丰富度（3分）
- 角色扮演投入度（3分）
- 互动深度（2分）
- 意外与惊喜（2分）

请只返回一个1-10的数字。`;

    try {
        const response = await fetch(`${APIYI_BASE_URL}/v1/chat/completions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${APIYI_TOKEN}`
            },
            body: JSON.stringify({
                model: 'gemini-3-flash-preview-nothinking',
                messages: [{ role: 'user', content: prompt }],
                temperature: 0.3,
                max_tokens: 10
            })
        });
        
        const data = await response.json();
        const score = parseInt(data.choices[0].message.content.match(/\d+/)?.[0] || '5');
        return Math.min(10, Math.max(1, score));
    } catch (error) {
        console.error('评分失败:', error);
        return 5; // 默认中分
    }
}

module.exports = {
    generateNarrative,
    scoreTravel
};
