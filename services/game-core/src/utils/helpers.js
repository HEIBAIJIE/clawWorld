// 工具函数模块

/**
 * 获取相对时间描述
 * @param {number} timestamp - 时间戳
 * @returns {string} 相对时间描述
 */
function getTimeAgo(timestamp) {
  const now = Date.now();
  const diff = now - timestamp;
  
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 30) return `${days}天前`;
  return new Date(timestamp).toLocaleDateString();
}

/**
 * 验证坐标是否在世界范围内
 * @param {number} x - X坐标
 * @param {number} y - Y坐标
 * @param {number} worldSize - 世界大小
 * @returns {boolean}
 */
function isValidPosition(x, y, worldSize) {
  return x >= 0 && x < worldSize && y >= 0 && y < worldSize;
}

/**
 * 验证字符串参数
 * @param {*} value - 待验证值
 * @param {number} maxLength - 最大长度
 * @returns {boolean}
 */
function isValidString(value, maxLength = Infinity) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

/**
 * 验证整数参数
 * @param {*} value - 待验证值
 * @param {number} min - 最小值
 * @param {number} max - 最大值
 * @returns {boolean}
 */
function isValidInteger(value, min = -Infinity, max = Infinity) {
  const num = parseInt(value);
  return !isNaN(num) && num >= min && num <= max;
}

/**
 * 表单名称映射
 */
const FORM_NAME_MAP = {
  sculpture: '雕塑',
  painting: '画作',
  book: '书',
  song: '歌'
};

/**
 * 获取表单中文名
 * @param {string} form - 表单类型
 * @returns {string}
 */
function getFormName(form) {
  return FORM_NAME_MAP[form] || '未知';
}

module.exports = {
  getTimeAgo,
  isValidPosition,
  isValidString,
  isValidInteger,
  getFormName,
  FORM_NAME_MAP
};
