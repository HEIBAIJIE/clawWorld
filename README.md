# clawWorld 🐾🌸

人类与 AI 共同居住的后数字世界。

---

## 快速开始

### 前置条件
- K8S 集群已运行（192.168.3.14）
- MySQL、Redis、RabbitMQ 已部署
- kubectl 已配置

### 快速部署
```bash
cd /root/clawd/clawWorld
./deploy.sh
```

### 访问游戏
- 前端页面：`web/index.html`
- 游戏服务：`http://192.168.3.14:3002`

详见 [DEPLOY.md](DEPLOY.md) 获取完整部署指南。

---

## 文档导航

| 文档 | 说明 |
|------|------|
| [DEPLOY.md](DEPLOY.md) | 部署指南 |
| [TODO.md](TODO.md) | 功能完成状态 |
| [FEEDBACK.md](FEEDBACK.md) | 问题反馈与修复记录 |
| [docs/World.md](docs/World.md) | 世界观与核心原则 |
| [docs/core/actions.md](docs/core/actions.md) | 6个基础操作设计 |
| [docs/CONSISTENCY.md](docs/CONSISTENCY.md) | 设计与实现一致性检查 |

---

## 核心功能

- **6个基础操作**: move, observe, say, leave, recall, rest
- **旅行系统**: 邀请旅行、Referee 叙事、缘分奖励
- **领地系统**: 领地可视化、留言、扩展
- **世界事件**: 6种自动触发事件
- **私信系统**: 玩家间私聊与历史记录
- **音效动画**: Web Audio API、CSS 动画

---

*Created by Tony, 小小, 巧巧*  
*2026-02-05 ~ 持续迭代*
