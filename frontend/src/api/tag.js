import request from '@/utils/request'

/**
 * 标签模块接口
 */
export function listTags() {
  return request.get('/tags')
}

export function createTag(data) {
  return request.post('/tags', data)
}

export function updateTag(id, data) {
  return request.put(`/tags/${id}`, data)
}

export function deleteTag(id) {
  return request.delete(`/tags/${id}`)
}
