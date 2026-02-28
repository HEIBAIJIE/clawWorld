# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

ClawWorld 是一款 AI 原生的 MMORPG，人类玩家和 AI 智能体使用相同的纯文本接口进行游戏。
游戏采用类 Shell 的指令系统进行所有交互，输出结构化文本（背景/窗口/状态），专为 LLM 上下文管理优化。
在文本的基础上，前端提供GUI提高人类玩家的游戏体验，GUI纯粹基于文本解析获取数据。

## 架构

### 后端代码在./src目录下 (DDD 风格) (Java 21 + Spring Boot 4 + MongoDB)
- `interfaces/` - REST 控制器（`/api/auth`、`/api/command`）和指令解析
- `application/` - 服务层（战斗、组队、商店、交易、寻路等）
- `domain/` - 核心实体（Player、Enemy、Combat、Map、Skill、Item、Equipment）
- `infrastructure/` - MongoDB 持久化、CSV 配置加载、实体工厂

### 前端代码和GUI在./frontend目录下 (Vue 3 + Vite)
- `views/` - 主要游戏视图
- `components/` - UI 组件
- `stores/` - Pinia 状态管理
- `api/` - 后端 API 调用
- `parsers/` - 服务器响应解析

### 游戏编辑器在./editor目录下（python）

### CSV 数据文件
所有游戏内容通过 `src/main/resources/data/` 中的 CSV 配置

## 重要设计文档
开发前务必参考 `设计文档/【重要，每次开发前务必参考】核心机制.md`，理解游戏背景。
进行GUI美术开发前，务必参考 `设计文档/【重要，开发GUI前务必参考】美术设计.md`，理解游戏整体美术风格

## 图片生成工具 (draw.py)
使用 Gemini API 生成游戏图标，已封装为根目录的python脚本，调用示例：
```bash
python draw.py "绘画提示词" -o output.png --ratio 1:1 --size 0.5K
```