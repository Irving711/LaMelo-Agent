<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { manageApi } from '../../api/manage'
import { isTerminalTask } from '../../services/adminWorkflow'
import { notifyError, notifySuccess } from '../../utils/notify'
const emit = defineEmits<{ uploaded: [unknown]; progress: [number]; terminal: [unknown] }>()
const filePath = ref(''); const fileName = ref(''); const uploading = ref(false); const progress = ref(0); const taskState = ref('')
let pollTimer: ReturnType<typeof setTimeout> | undefined
let disposed = false
let pollFailures = 0
onBeforeUnmount(() => { disposed = true; if (pollTimer) clearTimeout(pollTimer) })
function chooseFile() { const uniApi = (globalThis as { uni?: { chooseMessageFile?: (options: any) => void } }).uni; uniApi?.chooseMessageFile?.({ count: 1, type: 'file', success: (result: any) => { const file = result?.tempFiles?.[0]; filePath.value = file?.path || file?.filePath || ''; fileName.value = file?.name || '' } }) }
async function poll(taskId: string | number) { if (disposed) return; try { const task: any = await manageApi.queryTaskLogs({ taskId, pageNo: 1, pageSize: 1 }); if (disposed) return; pollFailures = 0; taskState.value = String(task?.taskStatusName || task?.taskStatus || ''); if (isTerminalTask(task)) { emit('terminal', task); if (Number(task.taskStatus) === 3) notifySuccess('文档解析成功'); else notifyError(new Error(task.errorMsg || taskState.value), '文档解析未完成'); return }; pollTimer = setTimeout(() => void poll(taskId), 1200) } catch (error) { if (disposed) return; pollFailures++; if (pollFailures >= 3) { taskState.value = '任务状态查询失败'; notifyError(error, taskState.value); return }; pollTimer = setTimeout(() => void poll(taskId), 2000) } }
async function upload() { if (!filePath.value || uploading.value) return; if (pollTimer) clearTimeout(pollTimer); pollFailures = 0; uploading.value = true; progress.value = 0; taskState.value = ''; try { const result: any = await manageApi.uploadDocument({ filePath: filePath.value, documentName: fileName.value }, (percent) => { progress.value = percent; emit('progress', percent) }); progress.value = 100; emit('progress', 100); emit('uploaded', result); if (result?.taskId != null) void poll(result.taskId); else notifySuccess('文档上传成功') } catch (error) { notifyError(error, '文档上传失败') } finally { uploading.value = false } }
</script>
<template><view class="upload"><button size="mini" @click="chooseFile">选择文件</button><text v-if="fileName" class="file-name">{{ fileName }}</text><button size="mini" type="primary" :disabled="!filePath || uploading" @click="upload">{{ uploading ? '上传中 ' + progress + '%' : '上传并解析' }}</button><text v-if="progress" class="muted">上传进度：{{ progress }}%</text><text v-if="taskState" class="muted">解析任务：{{ taskState }}</text></view></template>
<style scoped>
.upload { display: flex; flex-direction: column; gap: 16rpx; padding: 20rpx 0; }.upload button { margin: 0; }.file-name { word-break: break-all; }.muted { color: var(--color-text-muted); font-size: 24rpx; }
</style>
