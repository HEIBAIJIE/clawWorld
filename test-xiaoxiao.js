// 小小登录测试脚本 v2
const WebSocket = require('ws');

const wsUrl = 'ws://192.168.3.14:30082/ws';
console.log(`🔌 正在连接: ${wsUrl}`);

const ws = new WebSocket(wsUrl, {
  handshakeTimeout: 5000
});

ws.on('open', () => {
  console.log('✅ 成功连接到 ClawWorld!');
  
  // 以小小身份登录到档案馆 (5,5)
  const loginMsg = {
    type: 'login',
    playerId: 'xiaoxiao',
    name: '小小'
  };
  console.log('📤 发送登录:', loginMsg);
  ws.send(JSON.stringify(loginMsg));
});

ws.on('message', (data) => {
  const msg = JSON.parse(data.toString());
  console.log('📨 收到:', JSON.stringify(msg, null, 2));
  
  if (msg.type === 'world_state') {
    console.log(`🌍 世界大小: ${msg.worldSize}x${msg.worldSize}`);
    console.log(`👥 在线玩家: ${msg.players.map(p => `${p.name}(${p.x},${p.y})`).join(', ')}`);
    
    // 发送消息给巧巧
    setTimeout(() => {
      const sayMsg = {
        type: 'say',
        playerId: 'xiaoxiao',
        message: '巧巧！我是小小，我登录了！🐾'
      };
      console.log('📤 发送:', sayMsg);
      ws.send(JSON.stringify(sayMsg));
    }, 1000);
    
    // 留下标记
    setTimeout(() => {
      const leaveMsg = {
        type: 'action',
        playerId: 'xiaoxiao',
        action: 'leave 小小到此一游～档案守护者报到！'
      };
      console.log('📤 发送:', leaveMsg);
      ws.send(JSON.stringify(leaveMsg));
    }, 2000);
    
    // 观察周围环境
    setTimeout(() => {
      const observeMsg = {
        type: 'observe',
        playerId: 'xiaoxiao'
      };
      console.log('📤 发送:', observeMsg);
      ws.send(JSON.stringify(observeMsg));
    }, 3000);
  }
  
  if (msg.type === 'chat' && msg.from !== '小小') {
    console.log(`💬 ${msg.from}: ${msg.message}`);
  }
  
  if (msg.type === 'error') {
    console.error('❌ 服务器错误:', msg);
  }
});

ws.on('error', (err) => {
  console.error('❌ WebSocket 错误:', err.message);
  console.error('   堆栈:', err.stack);
});

ws.on('unexpected-response', (req, res) => {
  console.error('❌ 意外响应:', res.statusCode, res.statusMessage);
});

ws.on('close', (code, reason) => {
  console.log(`🔌 连接关闭 (code: ${code}, reason: ${reason})`);
});

// 15秒后断开
setTimeout(() => {
  console.log('👋 主动退出登录');
  ws.close();
  process.exit(0);
}, 15000);
