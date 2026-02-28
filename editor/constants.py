#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
游戏常量定义
"""

# 地形类型 - 从 terrain_types.csv 动态加载
def _load_terrain_types():
    """从CSV加载地形类型配置"""
    from csv_utils import read_csv
    types = []
    passable = []
    colors = {}
    rows = read_csv('terrain_types.csv')
    for row in rows:
        tid = row['id']
        types.append(tid)
        if row.get('passable', 'true').lower() == 'true':
            passable.append(tid)
        if row.get('color'):
            colors[tid] = row['color']
    return types, passable, colors

TERRAIN_TYPES, PASSABLE_TERRAINS, TERRAIN_COLORS = _load_terrain_types()

# 实体类型
ENTITY_TYPES = ['WAYPOINT', 'NPC', 'ENEMY', 'CAMPFIRE', 'CHEST_SMALL', 'CHEST_LARGE']

# 实体颜色
ENTITY_COLORS = {
    'WAYPOINT': '#FFD700',
    'NPC': '#00FF00',
    'ENEMY': '#FF0000',
    'CAMPFIRE': '#FF4500',
    'CHEST_SMALL': '#8B4513',
    'CHEST_LARGE': '#DAA520'
}

# 敌人品阶
ENEMY_TIERS = ['NORMAL', 'ELITE', 'MAP_BOSS', 'SERVER_BOSS']

# 物品类型
ITEM_TYPES = ['CONSUMABLE', 'MATERIAL', 'SKILL_BOOK', 'GIFT']

# 物品效果
ITEM_EFFECTS = ['HEAL_HP', 'HEAL_MP', 'LEARN_SKILL', 'RESET_ATTRIBUTES', 'OPEN_GIFT', 'NONE']

# 装备槽位
EQUIPMENT_SLOTS = [
    'HEAD', 'CHEST', 'LEGS', 'FEET',
    'LEFT_HAND', 'RIGHT_HAND', 'ACCESSORY1', 'ACCESSORY2'
]

# 稀有度
RARITIES = ['COMMON', 'EXCELLENT', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC']

# 稀有度颜色
RARITY_COLORS = {
    'COMMON': '#FFFFFF',
    'EXCELLENT': '#00FF00',
    'RARE': '#0000FF',
    'EPIC': '#800080',
    'LEGENDARY': '#FFA500',
    'MYTHIC': '#FF0000'
}

# 职业
ROLES = ['WARRIOR', 'RANGER', 'MAGE', 'PRIEST']

# 技能目标类型
TARGET_TYPES = ['ENEMY_SINGLE', 'ENEMY_ALL', 'ALLY_SINGLE', 'ALLY_ALL', 'SELF']

# 伤害类型
DAMAGE_TYPES = ['PHYSICAL', 'MAGICAL', 'NONE']
