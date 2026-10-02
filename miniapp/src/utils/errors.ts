export class ApiError extends Error {
  readonly status?: number
  readonly payload?: unknown

  constructor(message: string, status?: number, payload?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

export class UnauthorizedError extends ApiError {
  constructor(message = '请先登录', payload?: unknown) {
    super(message, 401, payload)
    this.name = 'UnauthorizedError'
  }
}

export class ForbiddenError extends ApiError {
  constructor(message = '没有权限执行此操作', payload?: unknown) {
    super(message, 403, payload)
    this.name = 'ForbiddenError'
  }
}

export class ApiCodeError extends ApiError {
  readonly code: number | string

  constructor(message: string, code: number | string, payload?: unknown) {
    super(message, typeof code === 'number' ? code : undefined, payload)
    this.name = 'ApiCodeError'
    this.code = code
  }
}

export class NetworkError extends ApiError {
  constructor(message = '网络请求失败', payload?: unknown) {
    super(message, undefined, payload)
    this.name = 'NetworkError'
  }
}
