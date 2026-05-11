import request from '../utils/request'

export function healthCheck() {
  return request.get('/health')
}

export * from './auth'
