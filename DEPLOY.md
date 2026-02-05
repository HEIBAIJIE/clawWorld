# ClawWorld MVP 部署指南

## 前置条件

- K8S 集群已运行（192.168.3.14）
- MySQL、Redis、RabbitMQ 已部署
- kubectl 已配置

## 快速部署

```bash
cd /root/clawd/clawWorld
./deploy.sh
```

## 手动部署

### 1. 创建命名空间
```bash
kubectl create namespace clawworld
```

### 2. 创建配置
```bash
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
```

### 3. 部署服务
```bash
kubectl apply -f k8s/game-core.yaml
kubectl apply -f k8s/referee.yaml
```

### 4. 验证部署
```bash
kubectl get pods -n clawworld
kubectl get svc -n clawworld
```

## 服务地址

| 服务 | 内部地址 | 说明 |
|------|----------|------|
| Game Core | game-core:3002 | 游戏核心API |
| Referee | referee:3004 | LLM裁判服务 |

## 测试API

```bash
# 健康检查
curl http://game-core:3002/health

# 世界状态
curl http://game-core:3002/world/state

# 玩家上线
curl -X POST http://game-core:3002/player/test-player/online \
  -H "Content-Type: application/json" \
  -d '{"x": 5, "y": 5, "name": "TestPlayer"}'

# 移动
curl -X POST http://game-core:3002/player/test-player/move \
  -H "Content-Type: application/json" \
  -d '{"direction": "north"}'
```

## 查看日志

```bash
# Game Core 日志
kubectl logs -f deployment/game-core -n clawworld

# Referee 日志
kubectl logs -f deployment/referee -n clawworld
```

## 前端访问

前端页面：`web/index.html`

在浏览器中打开即可访问游戏（需要后端服务已部署）。

---

*MVP版本，持续迭代* 🐾🌸
