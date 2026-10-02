import { manageApi } from '../api/manage'
import { ApiCodeError, ForbiddenError, NetworkError, UnauthorizedError } from '../utils/errors'

describe('miniapp management upload', () => {
  const originalUni = (globalThis as any).uni

  afterEach(() => {
    ;(globalThis as any).uni = originalUni
    vi.restoreAllMocks()
  })

  it('sends metadata as the JSON formData part accepted by the backend', async () => {
    let options: any
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn(() => 'token'),
      uploadFile: vi.fn((next: any) => { options = next; next.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { id: 1 } }) }); return {} })
    }
    await expect(manageApi.uploadDocument({ filePath: '/tmp/doc.pdf', documentName: 'Doc' })).resolves.toEqual({ id: 1 })
    expect(options.filePath).toBe('/tmp/doc.pdf')
    expect(options.name).toBe('file')
    expect(JSON.parse(options.formData.meta)).toEqual(expect.objectContaining({ documentName: 'Doc' }))
    expect(options.files).toBeUndefined()
  })

  it('maps upload auth and network failures to typed errors', async () => {
    for (const code of [401, 403]) {
      const redirectTo = vi.fn()
      ;(globalThis as any).uni = { getStorageSync: vi.fn(), removeStorageSync: vi.fn(), redirectTo, uploadFile: vi.fn((next: any) => { next.success({ statusCode: 200, data: JSON.stringify({ code, message: 'denied' }) }); return {} }) }
      const error = await manageApi.uploadDocument({ filePath: '/tmp/doc.pdf' }).catch((value) => value)
      expect(error).toBeInstanceOf(code === 401 ? UnauthorizedError : ForbiddenError)
      if (code === 401) expect(redirectTo).toHaveBeenCalledWith({ url: '/pages/auth/login' })
    }
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), removeStorageSync: vi.fn(), redirectTo: vi.fn(), uploadFile: vi.fn((next: any) => { next.success({ statusCode: 401, data: 'not-json' }); return {} }) }
    await expect(manageApi.uploadDocument({ filePath: '/tmp/doc.pdf' })).rejects.toBeInstanceOf(UnauthorizedError)
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), uploadFile: vi.fn((next: any) => { next.success({ statusCode: 200, data: 'not-json' }); return {} }) }
    await expect(manageApi.uploadDocument({ filePath: '/tmp/doc.pdf' })).rejects.toBeInstanceOf(NetworkError)
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), uploadFile: vi.fn((next: any) => { next.success({ statusCode: 200, data: JSON.stringify({ code: 1001, message: 'invalid metadata' }) }); return {} }) }
    await expect(manageApi.uploadDocument({ filePath: '/tmp/doc.pdf' })).rejects.toBeInstanceOf(ApiCodeError)
    ;(globalThis as any).uni = { getStorageSync: vi.fn(), uploadFile: vi.fn((next: any) => { next.fail({ errMsg: 'offline' }); return {} }) }
    await expect(manageApi.uploadDocument({ filePath: '/tmp/doc.pdf' })).rejects.toBeInstanceOf(NetworkError)
  })

  it('forwards native upload progress', async () => {
    let options: any
    let progressHandler: ((value: { progress: number }) => void) | undefined
    ;(globalThis as any).uni = {
      getStorageSync: vi.fn(),
      uploadFile: vi.fn((next: any) => { options = next; return { onProgressUpdate(handler: typeof progressHandler) { progressHandler = handler } } })
    }
    const onProgress = vi.fn()
    const pending = manageApi.uploadDocument({ filePath: '/tmp/doc.pdf' }, onProgress)
    progressHandler?.({ progress: 42 })
    options.success({ statusCode: 200, data: JSON.stringify({ code: 0, data: { taskId: 1 } }) })
    await pending
    expect(onProgress).toHaveBeenCalledWith(42)
  })
})
