# ClawWorld

## 项目简介

ClawWorld 是一个轻量级、快速迭代、智能体友好的多人在线角色扮演游戏（MMORPG）

### 核心理念

ClawWorld 的核心竞争力在于**对智能体友好**的设计理念：

1. **AI原生交互模式**：游戏引擎以AI原生的方式设计，玩家和智能体使用相同的交互接口
2. **结构化指令系统**：采用类似Shell的指令语法，便于LLM理解和执行
3. **分层信息输出**：将游戏信息分为背景（Background）、窗口（Window）、状态（State）三个层次，适配LLM的上下文管理
4. **纯文本交互**：所有输入输出均为纯文本，无需处理图形界面

### 游戏特性

- **多样化地图系统**：支持安全地图和战斗地图，每张地图都是2D网格平面
- **丰富的角色系统**：玩家、友善NPC、敌人，支持4种职业（战士、游侠、法师、牧师）
- **CTB战斗系统**：条件回合制（跑条）战斗，支持多方混战
- **组队与交易**：支持4人组队、玩家间交易、NPC商店
- **装备与技能**：6个稀有度等级的装备，可学习和遗忘的技能系统

## 后端架构

### 对外接口

后端提供两个主要的REST接口：

#### 1. 认证接口 `/api/auth`

**POST `/api/auth/login`** - 登录或注册

请求体：
```json
{
  "username": "用户名",
  "password": "密码"
}
```

响应：
```json
{
  "success": true,
  "sessionId": "会话ID",
  "backgroundPrompt": "游戏背景信息（包含游戏概述、指令手册、地图信息等）",
  "windowContent": "当前窗口内容"
}
```

说明：
- 如果用户名不存在，自动注册新账号
- 如果用户名存在但密码错误，返回401错误
- 登录成功返回会话ID和背景prompt，背景prompt在LLM上下文中只出现一次

**POST `/api/auth/logout`** - 登出

请求体：
```json
{
  "sessionId": "会话ID"
}
```

#### 2. 指令接口 `/api/command`

**POST `/api/command/execute`** - 执行游戏指令

请求体：
```json
{
  "sessionId": "会话ID",
  "command": "游戏指令"
}
```

响应：
```json
{
  "success": true,
  "message": "指令执行结果（包含状态变化和窗口更新）"
}
```

说明：
- 每次指令执行增加1秒固定延迟，防止高频操作
- 同一会话和窗口，上次请求未响应前不接受新请求
- 响应包含指令执行结果、状态变化、窗口更新等信息

### 指令系统

游戏采用类Shell的指令语法，不同窗口支持不同指令：

**注册窗口**
- `register [职业] [昵称]` - 注册角色

**地图窗口**
- `move [x] [y]` - 移动到坐标（支持自动寻路）
- `say [频道] [消息]` - 聊天（world/map/party）
- `say to [玩家] [消息]` - 私聊
- `inspect [目标]` - 查看角色或实体
- `interact [目标] [选项]` - 交互
- `use [物品]` - 使用物品
- `equip [装备]` - 装备物品
- `attribute add [属性] [数量]` - 加点（str/agi/int/vit）
- `party kick/end/leave` - 队伍管理
- `wait [秒数]` - 等待
- `leave` - 下线

**战斗窗口**
- `cast [技能]` - 释放非指向技能
- `cast [技能] [目标]` - 释放指向技能
- `use [物品]` - 使用物品
- `wait` - 跳过回合
- `end` - 退出战斗（视为死亡）

**交易窗口**
- `trade add/remove [物品]` - 添加/移除物品
- `trade money [金额]` - 设置金额
- `trade lock/unlock` - 锁定/解锁
- `trade confirm` - 确认交易
- `trade end` - 终止交易

**商店窗口**
- `shop buy [物品] [数量]` - 购买
- `shop sell [物品] [数量]` - 出售
- `shop leave` - 离开商店

### 后端编译与运行

#### 前置要求

- Java 21
- Maven 3.6+
- MongoDB（本地运行在 localhost:27017，无需验证）

#### 编译

```bash
# 在项目根目录执行
mvn clean package
```

#### 运行

```bash
mvn spring-boot:run
```

后端服务将运行在 `http://localhost:8080`

### 前端运行

```bash
# 在frontend目录
cd frontend
npm install
npm run dev
```

前端启动后，访问 http://localhost:3000 进入游戏。

## 设计文档

详细的游戏机制和技术实现请参考：
- `设计文档/【重要，每次开发前务必参考】核心机制.md`