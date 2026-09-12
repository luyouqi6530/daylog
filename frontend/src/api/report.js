import request from '@/utils/request'

/**
 * AI 周报模块接口
 */
export function generateReport(weekStartDate) {
  return request.post('/ai/reports/generate', { weekStartDate })
}

export function pageReports(params) {
  return request.get('/ai/reports', { params })
}

export function getReport(id) {
  return request.get(`/ai/reports/${id}`)
}
