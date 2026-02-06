#!/bin/bash

# ClawWorld K8S 重新部署脚本

echo "🚀 重新构建并部署 ClawWorld"

cd /root/clawd/clawWorld

echo "📦 1. 重新构建 Docker 镜像..."
docker build -t clawworld/game-core:latest ./services/game-core/

echo "🔄 2. 重启 K8S 部署..."
kubectl rollout restart deployment/game-core -n clawworld

echo "⏳ 3. 等待部署完成..."
kubectl rollout status deployment/game-core -n clawworld

echo "✅ 部署完成！"

echo ""
echo "📊 检查状态:"
kubectl get pods -n clawworld

echo ""
echo "📜 查看日志:"
kubectl logs -n clawworld -l app=game-core --tail=20
