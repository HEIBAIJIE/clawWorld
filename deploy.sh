#!/bin/bash

# ClawWorld MVP 部署脚本

set -e

echo "🚀 ClawWorld MVP 部署开始..."

# 创建命名空间
kubectl apply -f k8s/namespace.yaml || true

# 创建配置
kubectl create configmap clawworld-config \
  --from-literal=DB_HOST=mysql.mysql.svc.cluster.local \
  --from-literal=DB_PORT=3306 \
  --from-literal=DB_NAME=clawworld \
  --from-literal=REDIS_HOST=redis.redis.svc.cluster.local \
  --from-literal=REDIS_PORT=6379 \
  --from-literal=MQ_HOST=rabbitmq.mq.svc.cluster.local \
  --from-literal=MQ_PORT=5672 \
  -n clawworld --dry-run=client -o yaml | kubectl apply -f -

# 创建Secret（需要手动设置密码）
kubectl create secret generic clawworld-secrets \
  --from-literal=DB_PASSWORD=clawpass \
  --from-literal=MQ_PASSWORD=clawpass \
  --from-literal=LLM_API_KEY=sk-uX8hVbhIM27Xt4iJE84b79900eAa4931B0122034Bb092510 \
  -n clawworld --dry-run=client -o yaml | kubectl apply -f -

# 部署服务
kubectl apply -f k8s/game-core.yaml
kubectl apply -f k8s/referee.yaml

# 等待部署完成
echo "⏳ 等待服务启动..."
kubectl rollout status deployment/game-core -n clawworld --timeout=60s
kubectl rollout status deployment/referee -n clawworld --timeout=60s

echo "✅ 部署完成！"
echo ""
echo "服务地址："
echo "  Game Core: http://game-core.clawworld.svc.cluster.local:3002"
echo "  Referee:   http://referee.clawworld.svc.cluster.local:3004"
echo ""
echo "查看日志："
echo "  kubectl logs -f deployment/game-core -n clawworld"
echo "  kubectl logs -f deployment/referee -n clawworld"
