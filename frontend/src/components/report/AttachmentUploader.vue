<template>
  <section class="att">
    <header class="att__head">
      <h3>附件</h3>
      <span class="att__hint">单个文件不超过 10MB</span>
    </header>

    <ul v-if="attachments.length" class="att__list">
      <li v-for="file in attachments" :key="file.id" class="file">
        <span class="file__icon">
          <el-icon><Document /></el-icon>
        </span>

        <button class="file__name" type="button" :title="file.fileName" @click="download(file)">
          {{ file.fileName }}
        </button>

        <span class="file__size">{{ formatSize(file.fileSize) }}</span>

        <span class="file__actions">
          <el-tooltip content="下载" placement="top" :show-after="150">
            <button class="file__btn" type="button" @click="download(file)">
              <el-icon><Download /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top" :show-after="150">
            <button
              class="file__btn file__btn--danger"
              type="button"
              :disabled="disabled || uploading"
              @click="remove(file)"
            >
              <el-icon><Delete /></el-icon>
            </button>
          </el-tooltip>
        </span>
      </li>
    </ul>

    <p v-else-if="!reportId" class="att__empty">先保存草稿，就可以上传附件了</p>
    <p v-else class="att__empty">还没有附件</p>

    <el-upload
      v-if="reportId"
      class="att__upload"
      :show-file-list="false"
      :disabled="disabled || uploading"
      :before-upload="beforeUpload"
      :http-request="doUpload"
    >
      <el-button plain :loading="uploading" :disabled="disabled" :icon="Upload">
        上传附件
      </el-button>
    </el-upload>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Download, Upload } from '@element-plus/icons-vue'
import { deleteAttachment, downloadAttachment, uploadAttachment } from '@/api/report'

const props = defineProps({
  /** 1 日报 / 2 周报 */
  reportType: { type: Number, default: 1 },
  /** 单据ID，为空说明还没有草稿 */
  reportId: { type: Number, default: null },
  /** 已上传附件列表 */
  attachments: { type: Array, default: () => [] },
  /** 已提交状态：禁止上传/删除 */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['refresh'])

const uploading = ref(false)
const MAX_SIZE = 10 * 1024 * 1024

function beforeUpload(file) {
  if (file.size > MAX_SIZE) {
    ElMessage.error('单个附件不能超过 10MB')
    return false
  }
  return true
}

async function doUpload(options) {
  const form = new FormData()
  form.append('file', options.file)
  form.append('reportType', String(props.reportType))
  form.append('reportId', String(props.reportId))

  uploading.value = true
  try {
    await uploadAttachment(form)
    ElMessage.success('附件上传成功')
    options.onSuccess?.({})
    emit('refresh')
  } catch (error) {
    options.onError?.(error)
  } finally {
    uploading.value = false
  }
}

async function remove(file) {
  try {
    await ElMessageBox.confirm(`确定删除附件「${file.fileName}」？`, '删除附件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteAttachment(file.id)
    ElMessage.success('附件已删除')
    emit('refresh')
  } catch {
    /* 拦截器已提示 */
  }
}

async function download(file) {
  if (!file?.id) {
    return
  }
  try {
    // 后端校验登录态 + 单据范围后回流文件，前端拿 blob 后再触发下载
    const blob = await downloadAttachment(file.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName || 'attachment'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch {
    /* 拦截器已提示 */
  }
}

function formatSize(size) {
  if (!size && size !== 0) {
    return ''
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.att {
  padding-top: 18px;
  border-top: 1px dashed rgba(63, 74, 90, 0.12);
}

.att__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.att__head h3 {
  margin: 0;
  color: var(--wl-text);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
}

.att__hint {
  color: rgba(40, 38, 36, 0.38);
  font-size: 11px;
}

.att__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0 0 12px;
  padding: 0;
  list-style: none;
}

.file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid rgba(63, 74, 90, 0.07);
  border-radius: 10px;
  background: rgba(63, 74, 90, 0.02);
  transition:
    border-color 0.28s var(--wl-ease),
    background-color 0.28s var(--wl-ease);
}

.file:hover {
  border-color: rgba(122, 106, 147, 0.3);
  background: rgba(122, 106, 147, 0.04);
}

.file__icon {
  display: inline-flex;
  color: var(--wl-violet);
  font-size: 15px;
}

.file__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--wl-text);
  font-size: 13px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.file__name:hover {
  color: var(--wl-violet);
}

.file__size {
  flex: 0 0 auto;
  color: rgba(40, 38, 36, 0.38);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.file__actions {
  display: inline-flex;
  gap: 2px;
}

.file__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: rgba(40, 38, 36, 0.45);
  cursor: pointer;
  transition:
    background-color 0.26s var(--wl-ease),
    color 0.26s var(--wl-ease),
    transform 0.34s var(--wl-spring);
}

.file__btn:hover:not(:disabled) {
  background: rgba(63, 74, 90, 0.08);
  color: var(--wl-primary);
  transform: translateY(-1px);
}

.file__btn--danger:hover:not(:disabled) {
  background: rgba(185, 154, 122, 0.16);
  color: #a5774c;
}

/* 删除按钮只在悬停（或键盘聚焦）时出现，保持列表干净 */
.file__btn--danger {
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.24s var(--wl-ease),
    background-color 0.26s var(--wl-ease),
    color 0.26s var(--wl-ease);
}

.file:hover .file__btn--danger,
.file:focus-within .file__btn--danger {
  opacity: 1;
  pointer-events: auto;
}

@media (hover: none) {
  /* 触屏没有 hover，直接常显，避免无法删除 */
  .file__btn--danger {
    opacity: 1;
    pointer-events: auto;
  }
}

.file__btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.att__empty {
  margin: 0 0 12px;
  color: rgba(40, 38, 36, 0.34);
  font-size: 12px;
}
</style>
