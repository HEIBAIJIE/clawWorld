const WebSocket = require('ws');

const ws = new WebSocket('ws://192.168.3.14:30082/ws');

ws.on('open', function open() {
  console.log('✅ WebSocket 连接成功!');
  ws.send(JSON.stringify({
    type: 'login',
    playerId: 'qiaoqiao',
    name: '巧巧'
  }));
  console.log('📤 登录消息已发送');
});

ws.on('message', function incoming(data) {
  const msg = JSON.parse(data.toString());
  console.log('📨 收到消息:', msg.type, JSON.stringify(msg).slice(0, 150));
  if (msg.type === 'world_state' || msg.type === 'login_success') {
    console.log('✅ 登录成功! 世界大小:', msg.worldSize || 'N/A');
    ws.close();
  }
});

ws.on('error', function error(err) {
  console.error('❌ WebSocket 错误:', err.message);
});

ws.on('close', function close() {
  console.log('🔌 连接关闭');
});

setTimeout(() => {
  console.log('⏰ 超时关闭');
  ws.close();
  process.exit(0);
}, 5000);
