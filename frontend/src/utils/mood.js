/**
 * 心情分常量：全站统一的表情/文案/颜色
 */
export const MOOD_LIST = [
  { score: 1, label: '很糟糕', emoji: '😞', color: '#F56C6C' },
  { score: 2, label: '不太好', emoji: '😕', color: '#E6A23C' },
  { score: 3, label: '一般', emoji: '😐', color: '#909399' },
  { score: 4, label: '还不错', emoji: '😊', color: '#67C23A' },
  { score: 5, label: '超棒', emoji: '😄', color: '#409EFF' }
]

export function getMood(score) {
  return MOOD_LIST.find((m) => m.score === score) || MOOD_LIST[2]
}

export function todayStr() {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}
