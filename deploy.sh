#!/bin/bash

# ClawWorld MVP 部署脚本

set -e

echo "🚀 ClawWorld MVP 部署开始..."

# 创建命名空间
kubectl apply -f k8s/namespace.yaml || true

# 创建配置（使用YAML文件，便于版本管理）
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

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
