<template>
  <div class="clm-doc-preview">
    <!-- 工具条 -->
    <div class="clm-doc-preview__toolbar">
      <template v-if="currentFile">
        <el-select
          v-if="isMainSelected"
          v-model="selectedId"
          size="small"
          class="clm-doc-preview__version-select"
          @change="onSelectVersion"
        >
          <el-option
            v-for="v in mainVersions"
            :key="v.id"
            :value="v.id"
            :label="`v${v.versionNo} ${v.fileName}`"
          />
        </el-select>
        <span v-else class="text-sm font-bold">{{ currentFile.fileName }}</span>
        <el-tag v-if="isCurrentVersion" type="success" size="small">当前版本</el-tag>
        <el-tag v-if="currentFile.frozen" type="warning" size="small">已冻结</el-tag>
        <span class="text-xs text-gray-400">{{ formatFileSize(currentFile.fileSize || 0) }}</span>
      </template>
      <span v-else class="text-sm text-gray-500">合同正文预览</span>
      <div class="flex-1"></div>
      <el-button
        v-if="currentFile && permissions.canDownload"
        v-hasPermi="['clm:contract:download']"
        size="small"
        @click="handleDownload"
      >
        <Icon icon="ep:download" class="mr-5px" /> 下载
      </el-button>
    </div>

    <!-- 预览区 -->
    <div v-loading="loading" class="clm-doc-preview__body">
      <div v-if="viewMode === 'denied'" class="clm-doc-preview__placeholder">
        <el-empty description="当前角色无合同正文查看权限" :image-size="100" />
      </div>
      <!-- 空态：无正文 -->
      <div v-if="viewMode === 'empty'" class="clm-doc-preview__placeholder">
        <el-empty description="尚未上传合同正文" :image-size="100">
          <el-upload
            v-if="permissions.canEdit"
            :auto-upload="false"
            :show-file-list="false"
            :accept="acceptTypes"
            :on-change="handleUploadMain"
          >
            <el-button type="primary" :loading="uploading">
              <Icon icon="ep:upload" class="mr-5px" /> 上传正文
            </el-button>
          </el-upload>
        </el-empty>
      </div>
      <!-- docx 渲染容器（常驻，便于 renderAsync 挂载） -->
      <div v-show="viewMode === 'docx'" ref="docxContainerRef" class="clm-doc-preview__docx"></div>
      <!-- PDF -->
      <iframe
        v-if="viewMode === 'pdf' && objectUrl"
        :src="objectUrl"
        class="clm-doc-preview__pdf"
      ></iframe>
      <!-- 图片 -->
      <div v-if="viewMode === 'image' && objectUrl" class="clm-doc-preview__image-wrap">
        <img :src="objectUrl" class="clm-doc-preview__image" alt="预览图片" />
      </div>
      <!-- 不支持的格式 -->
      <div v-if="viewMode === 'unsupported'" class="clm-doc-preview__placeholder">
        <el-empty description="该格式暂不支持预览，请下载查看" :image-size="100">
          <el-button
            v-if="currentFile && permissions.canDownload"
            v-hasPermi="['clm:contract:download']"
            type="primary"
            plain
            @click="handleDownload"
          >
            <Icon icon="ep:download" class="mr-5px" /> 下载文件
          </el-button>
        </el-empty>
      </div>
      <!-- 加载失败 -->
      <div v-if="viewMode === 'error'" class="clm-doc-preview__placeholder">
        <el-empty description="预览加载失败，请重试或下载查看" :image-size="100" />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { renderAsync } from 'docx-preview'
import type { UploadFile as ElUploadFile } from 'element-plus'
import { formatFileSize } from '@/utils/file'
import { checkPermi } from '@/utils/permission'
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmDocumentPreview' })

const props = defineProps<{ contract: ContractApi.ContractVO }>()
const emit = defineEmits<{ refresh: [] }>()

const message = useMessage()

type ViewMode = 'denied' | 'empty' | 'docx' | 'pdf' | 'image' | 'unsupported' | 'error'

const loading = ref(false)
const uploading = ref(false)
const viewMode = ref<ViewMode>('empty')
const documents = ref<ContractApi.DocumentVO[]>([])
const selectedId = ref<number | undefined>(undefined)
const objectUrl = ref('')
const docxContainerRef = ref<HTMLElement>()
const acceptTypes = '.docx,.doc,.pdf,.xlsx,.xls,.pptx,.ppt,.txt,.zip,.png,.jpg,.jpeg'
const MAX_SIZE = 50 * 1024 * 1024
let renderToken = 0 // 防止异步渲染竞态

const permissions = computed<Partial<ContractApi.ContractPermissionsVO>>(
  () => props.contract.permissions || {}
)
const hasContentAccess = computed(
  () => !!permissions.value.canDownload && checkPermi(['clm:contract:download'])
)

/** 正文（MAIN）版本列表，版本号倒序 */
const mainVersions = computed<ContractApi.DocumentVersionVO[]>(() => {
  const main = documents.value.find((doc) => doc.roleCode === 'MAIN')
  return [...(main?.versions || [])].sort((a, b) => b.versionNo - a.versionNo)
})

/** 全部版本（含附件），用于按编号查找 */
const allVersions = computed<ContractApi.DocumentVersionVO[]>(() =>
  documents.value.flatMap((doc) => doc.versions || [])
)

const currentFile = computed<ContractApi.DocumentVersionVO | undefined>(() =>
  allVersions.value.find((v) => v.id === selectedId.value)
)
const isMainSelected = computed(() => mainVersions.value.some((v) => v.id === selectedId.value))
const isCurrentVersion = computed(
  () => !!selectedId.value && selectedId.value === props.contract.currentDocumentVersionId
)

/** 文件扩展名 */
const fileExt = (fileName?: string) => fileName?.split('.').pop()?.toLowerCase() || ''

/** 释放 object URL */
const revokeUrl = () => {
  if (objectUrl.value) {
    window.URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

const showDeniedState = () => {
  renderToken++
  documents.value = []
  selectedId.value = undefined
  revokeUrl()
  if (docxContainerRef.value) docxContainerRef.value.innerHTML = ''
  viewMode.value = 'denied'
  loading.value = false
}

/** 渲染指定版本 */
const renderVersion = async (version: ContractApi.DocumentVersionVO) => {
  if (!hasContentAccess.value) {
    showDeniedState()
    return
  }
  const token = ++renderToken
  loading.value = true
  try {
    const data = await ContractApi.previewVersionBlob(version.id)
    if (token !== renderToken) return
    revokeUrl()
    const ext = fileExt(version.fileName)
    if (ext === 'docx') {
      viewMode.value = 'docx'
      await nextTick()
      if (docxContainerRef.value) {
        docxContainerRef.value.innerHTML = ''
        await renderAsync(new Blob([data]), docxContainerRef.value)
      }
    } else if (ext === 'pdf') {
      objectUrl.value = window.URL.createObjectURL(new Blob([data], { type: 'application/pdf' }))
      viewMode.value = 'pdf'
    } else if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(ext)) {
      objectUrl.value = window.URL.createObjectURL(new Blob([data]))
      viewMode.value = 'image'
    } else {
      viewMode.value = 'unsupported'
    }
  } catch {
    if (token === renderToken) {
      viewMode.value = 'error'
    }
  } finally {
    if (token === renderToken) {
      loading.value = false
    }
  }
}

/** 加载文档列表并预览默认版本（当前正文版本） */
const loadDocuments = async () => {
  if (!hasContentAccess.value) {
    showDeniedState()
    return
  }
  if (!props.contract.id) return
  loading.value = true
  try {
    documents.value = (await ContractApi.getDocumentList(props.contract.id)) || []
  } finally {
    loading.value = false
  }
  const defaultVersion =
    mainVersions.value.find((v) => v.id === props.contract.currentDocumentVersionId) ||
    mainVersions.value[0]
  if (defaultVersion) {
    selectedId.value = defaultVersion.id
    await renderVersion(defaultVersion)
  } else {
    selectedId.value = undefined
    revokeUrl()
    viewMode.value = 'empty'
  }
}

/** 下拉切换版本 */
const onSelectVersion = (id: number) => {
  if (!hasContentAccess.value) return
  const version = allVersions.value.find((v) => v.id === id)
  if (version) {
    renderVersion(version)
  }
}

/** 外部调用：预览指定版本（版本表"预览"联动） */
const loadVersion = async (versionId: number, fileName?: string) => {
  if (!hasContentAccess.value) {
    showDeniedState()
    return
  }
  let version = allVersions.value.find((v) => v.id === versionId)
  if (!version) {
    // 列表中不存在（如刚上传），重新拉取后再找
    documents.value = (await ContractApi.getDocumentList(props.contract.id!)) || []
    version = allVersions.value.find((v) => v.id === versionId)
  }
  if (!version) {
    message.warning(`未找到版本文件 ${fileName || versionId}`)
    return
  }
  selectedId.value = version.id
  await renderVersion(version)
}
defineExpose({ loadVersion })

/** 空态上传正文 */
const handleUploadMain = async (file: ElUploadFile) => {
  if (!file.raw || !permissions.value.canEdit) return
  if (file.raw.size > MAX_SIZE) {
    message.error('文件大小不能超过 50MB')
    return
  }
  const formData = new FormData()
  formData.append('contractId', String(props.contract.id))
  formData.append('roleCode', 'MAIN')
  formData.append('file', file.raw)
  uploading.value = true
  try {
    const res = await ContractApi.uploadDocument(formData)
    if (res && res.code !== undefined && res.code !== 0 && res.code !== 200) {
      message.error(res.msg || '上传失败')
      return
    }
    message.success('正文上传成功')
    emit('refresh')
    await loadDocuments()
  } finally {
    uploading.value = false
  }
}

/** 下载当前预览文件 */
const handleDownload = async () => {
  if (!currentFile.value || !hasContentAccess.value) return
  await ContractApi.downloadVersionFile(currentFile.value.id, currentFile.value.fileName)
}

watch(
  () => [
    props.contract.id,
    props.contract.updateTime,
    props.contract.currentDocumentVersionId,
    props.contract.permissions?.canDownload
  ],
  () => {
    loadDocuments()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  renderToken++
  revokeUrl()
})
</script>

<style lang="scss" scoped>
.clm-doc-preview {
  display: flex;
  flex-direction: column;

  &__toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-bottom: 10px;
    margin-bottom: 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__version-select {
    width: 260px;
  }

  &__body {
    height: calc(100vh - 360px);
    min-height: 420px;
    overflow: auto;
    background-color: var(--el-fill-color-light);
    border-radius: 4px;
  }

  &__placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
  }

  &__docx {
    :deep(.docx-wrapper) {
      padding: 16px;
      background: transparent;

      > section.docx {
        width: 100% !important;
        max-width: 794px;
        margin-right: auto;
        margin-bottom: 16px;
        margin-left: auto;
        box-shadow: 0 0 8px rgb(0 0 0 / 10%);
      }
    }
  }

  &__pdf {
    display: block;
    width: 100%;
    height: 100%;
    border: none;
  }

  &__image-wrap {
    display: flex;
    justify-content: center;
    padding: 16px;
  }

  &__image {
    height: auto;
    max-width: 100%;
  }
}
</style>
