<template>
  <div class="mb-15px flex items-center gap-10px">
    <el-tooltip :content="uploadDisabledTip" :disabled="canUpload" placement="top">
      <span>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :disabled="!canUpload"
          :accept="acceptTypes"
          :on-change="(file) => handleFileChange(file, 'MAIN')"
        >
          <el-button type="primary" :disabled="!canUpload" :loading="uploading">
            <Icon icon="ep:upload" class="mr-5px" /> 上传正文新版本
          </el-button>
        </el-upload>
      </span>
    </el-tooltip>
    <el-tooltip :content="uploadDisabledTip" :disabled="canUpload" placement="top">
      <span>
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :disabled="!canUpload"
          :accept="acceptTypes"
          :on-change="(file) => handleFileChange(file, 'ATTACHMENT')"
        >
          <el-button :disabled="!canUpload" :loading="uploading">
            <Icon icon="ep:paperclip" class="mr-5px" /> 上传附件
          </el-button>
        </el-upload>
      </span>
    </el-tooltip>
    <span class="text-sm text-gray-500">
      支持 docx/doc/pdf/xlsx/xls/pptx/ppt/txt/zip/png/jpg，单文件不超过 50MB
    </span>
  </div>

  <el-empty
    v-if="!canViewDocuments"
    description="当前角色无正文版本与附件查看权限"
    :image-size="72"
  />
  <div v-else v-loading="loading">
    <el-empty v-if="documents.length === 0" description="暂无正文或附件" :image-size="60" />
    <div v-for="doc in documents" :key="doc.id" class="mb-20px">
      <div class="mb-8px flex items-center gap-8px">
        <span class="font-bold">{{ doc.name }}</span>
        <dict-tag :type="DICT_TYPE.CLM_DOCUMENT_ROLE" :value="doc.roleCode" />
      </div>
      <el-table :data="doc.versions || []" border stripe :row-class-name="rowClassName(doc)">
        <el-table-column label="版本" align="center" width="90">
          <template #default="scope">
            <span>v{{ scope.row.versionNo }}</span>
            <el-tag
              v-if="scope.row.id === doc.currentVersionId"
              size="small"
              type="success"
              class="ml-5px"
            >
              当前
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件名" prop="fileName" min-width="160" show-overflow-tooltip />
        <el-table-column label="大小" align="center" width="90">
          <template #default="scope">{{ formatFileSize(scope.row.fileSize || 0) }}</template>
        </el-table-column>
        <el-table-column label="来源" align="center" width="100">
          <template #default="scope">
            <dict-tag :type="DICT_TYPE.CLM_DOCUMENT_SOURCE_TYPE" :value="scope.row.sourceType" />
          </template>
        </el-table-column>
        <el-table-column label="冻结" align="center" width="70">
          <template #default="scope">
            <el-tag v-if="scope.row.frozen" type="warning" size="small">已冻结</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="时间"
          align="center"
          prop="createTime"
          width="170"
          :formatter="dateFormatter"
        />
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <el-button
              v-if="canOnlineEdit(doc, scope.row)"
              v-hasPermi="['clm:contract:update']"
              link
              type="warning"
              @click="handleOnlineEdit(scope.row, 'edit')"
            >
              在线编辑
            </el-button>
            <el-button
              v-if="permissions.canDownload"
              v-hasPermi="['clm:contract:download']"
              link
              type="primary"
              @click="emit('preview', scope.row.id, scope.row.fileName)"
            >
              预览
            </el-button>
            <el-button
              v-if="permissions.canDownload"
              v-hasPermi="['clm:contract:download']"
              link
              type="primary"
              @click="handleDownload(scope.row)"
            >
              下载
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { UploadFile as ElUploadFile } from 'element-plus'
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import { formatFileSize } from '@/utils/file'
import { checkPermi } from '@/utils/permission'
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmContractDocuments' })

const props = defineProps<{ contract: ContractApi.ContractVO }>()
const emit = defineEmits<{ refresh: []; preview: [versionId: number, fileName: string] }>()

const message = useMessage()
const { push } = useRouter()

const loading = ref(false)
const uploading = ref(false)
const documents = ref<ContractApi.DocumentVO[]>([])
const acceptTypes = '.docx,.doc,.pdf,.xlsx,.xls,.pptx,.ppt,.txt,.zip,.png,.jpg,.jpeg'
const MAX_SIZE = 50 * 1024 * 1024

const permissions = computed<Partial<ContractApi.ContractPermissionsVO>>(
  () => props.contract.permissions || {}
)
const canViewDocuments = computed(
  () => !!permissions.value.canDownload && checkPermi(['clm:contract:download'])
)
const canUpload = computed(() => canViewDocuments.value && !!permissions.value.canEdit)
const uploadDisabledTip = computed(() => '当前用户或当前节点无编辑权限')

/** 当前版本行高亮 */
const rowClassName = (doc: ContractApi.DocumentVO) => {
  return ({ row }: { row: ContractApi.DocumentVersionVO }) =>
    row.id === doc.currentVersionId ? 'clm-current-version-row' : ''
}

/** 加载文档列表 */
const getList = async () => {
  if (!canViewDocuments.value) {
    documents.value = []
    loading.value = false
    return
  }
  if (!props.contract.id) return
  loading.value = true
  try {
    documents.value = (await ContractApi.getDocumentList(props.contract.id)) || []
  } finally {
    loading.value = false
  }
}

/** 选择文件后上传 */
const handleFileChange = async (file: ElUploadFile, roleCode: 'MAIN' | 'ATTACHMENT') => {
  if (!file.raw || !canUpload.value) return
  if (file.raw.size > MAX_SIZE) {
    message.error('文件大小不能超过 50MB')
    return
  }
  const formData = new FormData()
  formData.append('contractId', String(props.contract.id))
  formData.append('roleCode', roleCode)
  formData.append('file', file.raw)
  uploading.value = true
  try {
    const res = await ContractApi.uploadDocument(formData)
    if (res && res.code !== undefined && res.code !== 0 && res.code !== 200) {
      message.error(res.msg || '上传失败')
      return
    }
    message.success(roleCode === 'MAIN' ? '正文新版本上传成功' : '附件上传成功')
    await getList()
    emit('refresh')
  } finally {
    uploading.value = false
  }
}

/** 下载 */
const handleDownload = async (row: ContractApi.DocumentVersionVO) => {
  if (!canViewDocuments.value) return
  await ContractApi.downloadVersionFile(row.id, row.fileName)
}

// ========== 在线编辑 / 预览 ==========

const OFFICE_EXTENSIONS = ['docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt']

/** 是否 ONLYOFFICE 可打开的文件类型 */
const isOfficeFile = (fileName?: string) => {
  if (!fileName) return false
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  return OFFICE_EXTENSIONS.includes(ext)
}

/** 是否允许在线编辑：当前版本 + 服务端授予编辑权限 + 未冻结 */
const canOnlineEdit = (doc: ContractApi.DocumentVO, row: ContractApi.DocumentVersionVO) => {
  return (
    row.id === doc.currentVersionId &&
    !!permissions.value.canEdit &&
    !row.frozen &&
    isOfficeFile(row.fileName)
  )
}

/** 跳转在线编辑 / 预览 */
const handleOnlineEdit = (row: ContractApi.DocumentVersionVO, mode: 'edit' | 'view') => {
  push({
    name: 'ClmContractOnlineEdit',
    query: { versionId: row.id, mode, contractId: props.contract.id }
  })
}

watch(
  () => [
    props.contract.id,
    props.contract.updateTime,
    props.contract.currentDocumentVersionId,
    props.contract.permissions?.canDownload
  ],
  () => {
    getList()
  },
  { immediate: true }
)
</script>

<style lang="scss">
.clm-current-version-row {
  --el-table-tr-bg-color: var(--el-color-success-light-9);
}
</style>
