# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

ClawWorld 是一款 AI 原生的 MMORPG，人类玩家和 AI 智能体使用相同的纯文本接口进行游戏。游戏采用类 Shell 的指令系统进行所有交互，输出结构化文本（背景/窗口/状态），专为 LLM 上下文管理优化。

## 构建与运行命令

### 后端 (Java 21 + Spring Boot 4 + MongoDB)
```bash
# 构建
mvn clean package

# 运行（需要 MongoDB 运行在 localhost:27017，无需认证）
mvn spring-boot:run

# 运行单个测试
mvn test -Dtest=类名

# 运行所有测试（已启用并行执行）
mvn test
```

### 前端 (Vue 3 + Vite)
```bash
cd frontend
npm install
npm run dev
```

### 地图编辑器 (Python)
```bash
cd editor
python main.py   # Windows 下可运行 run_editor.bat
```

## 架构

### 后端代码在./src目录下 (DDD 风格)
- `interfaces/` - REST 控制器（`/api/auth`、`/api/command`）和指令解析
- `application/` - 服务层（战斗、组队、商店、交易、寻路等）
- `domain/` - 核心实体（Player、Enemy、Combat、Map、Skill、Item、Equipment）
- `infrastructure/` - MongoDB 持久化、CSV 配置加载、实体工厂

### 前端代码在./frontend目录下
- `views/` - 主要游戏视图
- `components/` - UI 组件
- `stores/` - Pinia 状态管理
- `api/` - 后端 API 调用
- `parsers/` - 服务器响应解析

### 地图编辑器在./editor目录下

### CSV 数据文件
所有游戏内容通过 `src/main/resources/data/` 中的 CSV 配置：
- `maps.csv`、`map_terrain.csv`、`map_entities.csv`、`waypoints.csv` - 地图定义
- `enemies.csv`、`enemy_loot.csv` - 敌人属性和掉落
- `npcs.csv`、`npc_shop_items.csv` - NPC 和商店
- `items.csv`、`equipment.csv` - 物品和装备
- `skills.csv`、`roles.csv`、`role_skills.csv` - 职业和技能

## 重要设计文档
开发前务必参考 `设计文档/【重要，每次开发前务必参考】核心机制.md`，理解游戏背景。