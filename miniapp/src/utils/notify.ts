export function notifyError(error: unknown, fallback = '操作失败'): void {
  const message = error instanceof Error && error.message ? error.message : String((error as { message?: string })?.message || fallback)
  ;(globalThis as { uni?: { showToast?: (options: { title: string; icon?: 'none' | 'error' }) => void } }).uni?.showToast?.({ title: message, icon: 'none' })
}

export function notifySuccess(message: string): void {
  ;(globalThis as { uni?: { showToast?: (options: { title: string; icon?: 'success' }) => void } }).uni?.showToast?.({ title: message, icon: 'success' })
}
