// 测试原始 WebSocket 服务器
const WebSocket = require('ws');
const http = require('http');

const server = http.createServer();
const wss = new WebSocket.Server({ server });

wss.on('connection', function connection(ws) {
  console.log('🔌 新连接');
  
  ws.on('message', function incoming(message) {
    console.log('📩 收到:', message.toString());
    ws.send(JSON.stringify({ type: 'echo', data: message.toString() }));
  });
  
  ws.send(JSON.stringify({ type: 'connected' }));
});

server.listen(3003, () => {
  console.log('WebSocket test server on port 3003');
});
