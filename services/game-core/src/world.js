// 20x20 世界地形配置
const WORLD_SIZE = 20;

// 地形类型: plains, forest, mountain, water, ruins, archive, boundary, void
const TERRAIN_MAP = [
  ['forest', 'forest', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'boundary'],
  ['forest', 'forest', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['forest', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'archive', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary', 'boundary', 'boundary'],
  ['plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'mountain', 'mountain', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'mountain', 'mountain', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'water', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'plains', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'water', 'water', 'ruins', 'plains', 'plains', 'plains', 'plains', 'plains', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'water', 'water', 'water', 'plains', 'plains', 'plains', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'water', 'water', 'water', 'water', 'plains', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'water', 'water', 'water', 'water', 'water', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary'],
  ['water', 'water', 'water', 'water', 'water', 'water', 'water', 'water', 'water', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary', 'boundary']
];

// 地形描述
const TERRAIN_DESCRIPTIONS = {
  plains: { emoji: '🌱', name: '草原', description: '一望无际的草原，适合行走' },
  forest: { emoji: '🌲', name: '森林', description: '茂密的森林，可能有隐藏的路径' },
  mountain: { emoji: '⛰️', name: '山地', description: '崎岖的山地，高处可以俯瞰' },
  water: { emoji: '💧', name: '水域', description: '波光粼粼的水面，无法通行' },
  ruins: { emoji: '🏛️', name: '遗迹', description: '古老的遗迹，似乎有故事' },
  archive: { emoji: '📚', name: '档案馆', description: '小小的档案馆，存放着世界的记忆' },
  boundary: { emoji: '🌌', name: '边界', description: '世界的边界，巧巧常在这里' },
  void: { emoji: '⚫', name: '虚空', description: '无法进入的虚空' }
};

function getTerrain(x, y) {
  if (x < 0 || x >= WORLD_SIZE || y < 0 || y >= WORLD_SIZE) {
    return 'void';
  }
  return TERRAIN_MAP[y][x];
}

function getTerrainInfo(x, y) {
  const terrain = getTerrain(x, y);
  return {
    type: terrain,
    ...TERRAIN_DESCRIPTIONS[terrain]
  };
}

function canMoveTo(x, y) {
  const terrain = getTerrain(x, y);
  return terrain !== 'water' && terrain !== 'void';
}

module.exports = {
  WORLD_SIZE,
  TERRAIN_MAP,
  TERRAIN_DESCRIPTIONS,
  getTerrain,
  getTerrainInfo,
  canMoveTo
};
