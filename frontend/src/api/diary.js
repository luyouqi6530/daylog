import request from '@/utils/request'

/**
 * 日记模块接口
 */
export function pageDiaries(params) {
  return request.get('/diaries', { params })
}

export function getDiary(id) {
  return request.get(`/diaries/${id}`)
}

export function getDiaryByDate(recordDate) {
  return request.get(`/diaries/date/${recordDate}`)
}

export function createDiary(data) {
  return request.post('/diaries', data)
}

export function updateDiary(id, data) {
  return request.put(`/diaries/${id}`, data)
}

export function deleteDiary(id) {
  return request.delete(`/diaries/${id}`)
}

export function getCalendar(year, month) {
  return request.get('/diaries/calendar', { params: { year, month } })
}
