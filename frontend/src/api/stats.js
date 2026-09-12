import request from '@/utils/request'

/**
 * 统计模块接口
 */
export function getOverview() {
  return request.get('/stats/overview')
}

export function getMoodTrend(params) {
  return request.get('/stats/mood-trend', { params })
}

export function getMoodDistribution(params) {
  return request.get('/stats/mood-distribution', { params })
}

export function getTagCloud(limit = 20) {
  return request.get('/stats/tag-cloud', { params: { limit } })
}

export function getHeatmap(year) {
  return request.get('/stats/heatmap', { params: { year } })
}
