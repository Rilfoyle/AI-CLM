<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>相对方导入</h2>
          <p>按标准模板上传，先校验和去重预览，再显式确认；失败行可修正后单独重试。</p>
        </div>
        <div class="header-actions">
          <el-button v-hasPermi="['clm:party-import:query']" @click="downloadTemplate">
            <Icon icon="ep:download" class="mr-5px" />下载模板
          </el-button>
          <el-button
            v-hasPermi="['clm:party-import:create']"
            type="primary"
            @click="openUploadDialog"
          >
            <Icon icon="ep:upload" class="mr-5px" />上传导入文件
          </el-button>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form :model="query" inline class="-mb-15px">
        <el-form-item label="文件名">
          <el-input
            v-model="query.fileName"
            clearable
            placeholder="输入原始文件名"
            class="!w-220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="query.status" clearable class="!w-180px" placeholder="全部状态">
            <el-option
              v-for="item in jobStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        class="mb-12px"
        type="warning"
        :closable="false"
        show-icon
        title="导入任务加载失败"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无相对方导入记录">
        <el-table-column label="任务" width="90">
          <template #default="scope">#{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="文件名" prop="fileName" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="scope">
            <el-tag :type="jobStatusType(scope.row.status)">{{
              jobStatusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数" width="90" align="center" prop="totalCount" />
        <el-table-column label="校验预览" min-width="210">
          <template #default="scope">
            <span class="count-text success">有效 {{ scope.row.validCount || 0 }}</span>
            <span class="count-text danger">异常 {{ scope.row.invalidCount || 0 }}</span>
            <span class="count-text muted">跳过 {{ scope.row.skippedCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="执行结果" min-width="160">
          <template #default="scope">
            <span class="count-text success">成功 {{ scope.row.successCount || 0 }}</span>
            <span class="count-text danger">失败 {{ scope.row.failedCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(scope.row.id)">查看与处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-if="total > 0"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="getList"
      />
    </ContentWrap>

    <Dialog v-model="uploadVisible" title="上传相对方导入文件" width="560px">
      <el-alert
        class="mb-14px"
        type="info"
        :closable="false"
        show-icon
        title="字段映射按标准模板固定：相对方名称、统一社会信用代码、联系人、联系电话。上传后不会立即写入目录。"
      />
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xls,.xlsx"
        :on-change="handleUploadChange"
        :on-remove="handleUploadRemove"
      >
        <Icon icon="ep:upload-filled" :size="42" />
        <div class="el-upload__text">拖拽 Excel 到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 xls/xlsx，最大 10 MB、最多 5000 行</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!uploadFile"
          :loading="uploading"
          @click="submitUpload"
        >
          上传并生成预览
        </el-button>
      </template>
    </Dialog>

    <el-drawer v-model="detailVisible" title="导入任务详情" size="920px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="currentJob.id">
          <div class="detail-heading">
            <div>
              <h3>#{{ currentJob.id }} · {{ currentJob.fileName }}</h3>
              <p
                >任务键 {{ currentJob.jobKey }} · {{ formatNullableDate(currentJob.createTime) }}</p
              >
            </div>
            <el-tag :type="jobStatusType(currentJob.status)" size="large">
              {{ jobStatusText(currentJob.status) }}
            </el-tag>
          </div>
          <el-row :gutter="10" class="summary-row">
            <el-col v-for="item in summaryItems" :key="item.label" :span="4">
              <div class="summary-item">
                <strong :class="item.type">{{ item.value }}</strong>
                <span>{{ item.label }}</span>
              </div>
            </el-col>
          </el-row>
          <el-alert
            v-if="currentJob.invalidCount || currentJob.failedCount"
            class="mb-14px"
            type="warning"
            :closable="false"
            show-icon
            title="异常行不会静默导入。可逐行修正；首次确认后，再对失败项执行“仅重试失败”。"
          />
          <div class="detail-actions">
            <div>
              <el-select
                v-model="itemQuery.status"
                clearable
                class="!w-160px"
                placeholder="全部行状态"
                @change="handleItemFilter"
              >
                <el-option
                  v-for="item in itemStatusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
            <div class="header-actions">
              <el-button
                v-if="currentJob.failedCount > 0"
                v-hasPermi="['clm:party-import:query']"
                @click="downloadFailedFile"
              >
                下载失败行
              </el-button>
              <el-button
                v-if="canRetry"
                v-hasPermi="['clm:party-import:confirm']"
                :loading="executing"
                @click="retryFailed"
              >
                仅重试失败
              </el-button>
              <el-button
                v-if="canConfirm"
                v-hasPermi="['clm:party-import:confirm']"
                type="primary"
                :loading="executing"
                @click="confirmImport"
              >
                确认导入有效行
              </el-button>
            </div>
          </div>
          <el-table v-loading="itemsLoading" :data="items" border size="small">
            <el-table-column label="Excel 行" prop="rowNo" width="85" align="center" />
            <el-table-column label="相对方名称" prop="name" min-width="170" show-overflow-tooltip />
            <el-table-column label="统一社会信用代码" prop="unifiedCreditCode" min-width="190" />
            <el-table-column label="联系人" prop="contactName" width="100" />
            <el-table-column label="联系电话" prop="contactPhone" width="130" />
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <el-tag :type="itemStatusType(scope.row.status)" size="small">
                  {{ itemStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="校验/执行结果" min-width="210" show-overflow-tooltip>
              <template #default="scope">
                <span>{{ scope.row.errorMessage || '校验通过' }}</span>
                <el-link
                  v-if="scope.row.duplicatePartyId"
                  class="ml-6px"
                  type="primary"
                  :underline="false"
                  @click="router.push('/clm/basic-data/directory')"
                >
                  已有主体 #{{ scope.row.duplicatePartyId }}
                </el-link>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="scope">
                <el-button
                  v-if="scope.row.status !== 'IMPORTED'"
                  v-hasPermi="['clm:party-import:create']"
                  link
                  type="primary"
                  @click="openItemEditor(scope.row)"
                >
                  修正
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            v-if="itemTotal > 0"
            v-model:page="itemQuery.pageNo"
            v-model:limit="itemQuery.pageSize"
            :total="itemTotal"
            @pagination="loadItems"
          />
        </template>
      </div>
    </el-drawer>

    <Dialog v-model="itemEditorVisible" title="修正导入行" width="600px">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="130px">
        <el-form-item label="相对方名称" prop="name">
          <el-input v-model="itemForm.name" maxlength="255" />
        </el-form-item>
        <el-form-item label="统一社会信用代码" prop="unifiedCreditCode">
          <el-input v-model="itemForm.unifiedCreditCode" maxlength="18" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="itemForm.contactName" maxlength="64" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="itemForm.contactPhone" maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemEditorVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSaving" @click="saveItem">保存并重新校验</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules, UploadFile, UploadInstance } from 'element-plus'
import { formatNullableDate } from '@/utils/formatTime'
import download from '@/utils/download'
import * as PartyImportApi from '@/api/clm/partyImport'

defineOptions({ name: 'ClmGovernancePartyImport' })
const router = useRouter()
const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<PartyImportApi.PartyImportJobVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, fileName: '', status: '' })
const jobStatusOptions = [
  { label: '校验中', value: 'VALIDATING' },
  { label: '待确认', value: 'PREVIEW_READY' },
  { label: '导入中', value: 'IMPORTING' },
  { label: '部分成功', value: 'PARTIAL' },
  { label: '全部成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' }
]
const itemStatusOptions = [
  { label: '校验通过', value: 'VALID' },
  { label: '校验异常', value: 'INVALID' },
  { label: '重复跳过', value: 'SKIPPED' },
  { label: '已导入', value: 'IMPORTED' },
  { label: '导入失败', value: 'FAILED' }
]

const jobStatusText = (status: string) =>
  jobStatusOptions.find((item) => item.value === status)?.label || status
const jobStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    PREVIEW_READY: 'warning',
    IMPORTING: 'primary',
    PARTIAL: 'warning',
    SUCCEEDED: 'success',
    FAILED: 'danger',
    VALIDATING: 'info'
  }
  return types[status] || 'info'
}
const itemStatusText = (status: string) =>
  itemStatusOptions.find((item) => item.value === status)?.label || status
const itemStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    VALID: 'success',
    INVALID: 'danger',
    SKIPPED: 'info',
    IMPORTED: 'success',
    FAILED: 'danger'
  }
  return types[status] || 'info'
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await PartyImportApi.getPartyImportPage({
      ...query,
      fileName: query.fileName || undefined,
      status: query.status || undefined
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  query.pageNo = 1
  getList()
}
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, fileName: '', status: '' })
  getList()
}

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref<UploadInstance>()
const uploadFile = ref<File>()
const uploadJobKey = ref('')
const nextRequestId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
const openUploadDialog = () => {
  uploadFile.value = undefined
  uploadJobKey.value = `PARTY-IMPORT-${nextRequestId()}`
  uploadRef.value?.clearFiles()
  uploadVisible.value = true
}
const handleUploadChange = (file: UploadFile) => {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!file.raw || !['xls', 'xlsx'].includes(extension || '') || file.size! > 10 * 1024 * 1024) {
    uploadFile.value = undefined
    uploadRef.value?.clearFiles()
    message.warning('请选择 10 MB 以内的 xls 或 xlsx 文件')
    return
  }
  uploadFile.value = file.raw
}
const handleUploadRemove = () => {
  uploadFile.value = undefined
}
const submitUpload = async () => {
  if (!uploadFile.value) return
  uploading.value = true
  try {
    const jobId = await PartyImportApi.uploadPartyImport(uploadFile.value, uploadJobKey.value)
    message.success('文件已上传，校验预览已生成')
    uploadVisible.value = false
    await getList()
    await openDetail(jobId)
  } finally {
    uploading.value = false
  }
}
const downloadTemplate = async () => {
  const data = await PartyImportApi.downloadPartyImportTemplate()
  download.excel(data, '相对方导入模板.xlsx')
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentJob = ref<PartyImportApi.PartyImportJobVO>({} as PartyImportApi.PartyImportJobVO)
const items = ref<PartyImportApi.PartyImportItemVO[]>([])
const itemsLoading = ref(false)
const itemTotal = ref(0)
const itemQuery = reactive({ pageNo: 1, pageSize: 20, status: '' })
const summaryItems = computed(() => [
  { label: '总行数', value: currentJob.value.totalCount || 0, type: '' },
  { label: '有效', value: currentJob.value.validCount || 0, type: 'success' },
  { label: '异常', value: currentJob.value.invalidCount || 0, type: 'danger' },
  { label: '跳过', value: currentJob.value.skippedCount || 0, type: '' },
  { label: '已导入', value: currentJob.value.successCount || 0, type: 'success' },
  { label: '失败', value: currentJob.value.failedCount || 0, type: 'danger' }
])
const canConfirm = computed(() =>
  ['PREVIEW_READY', 'PARTIAL', 'FAILED'].includes(currentJob.value.status)
)
const canRetry = computed(
  () =>
    currentJob.value.failedCount > 0 &&
    ['PARTIAL', 'FAILED', 'SUCCEEDED'].includes(currentJob.value.status)
)
const loadDetail = async () => {
  if (!currentJob.value.id) return
  detailLoading.value = true
  try {
    currentJob.value = await PartyImportApi.getPartyImportJob(currentJob.value.id)
  } finally {
    detailLoading.value = false
  }
}
const loadItems = async () => {
  if (!currentJob.value.id) return
  itemsLoading.value = true
  try {
    const data = await PartyImportApi.getPartyImportItemPage({
      ...itemQuery,
      jobId: currentJob.value.id,
      status: itemQuery.status || undefined
    })
    items.value = data?.list || []
    itemTotal.value = data?.total || 0
  } finally {
    itemsLoading.value = false
  }
}
const openDetail = async (id: string | number) => {
  currentJob.value = { id: String(id) } as PartyImportApi.PartyImportJobVO
  itemQuery.pageNo = 1
  itemQuery.status = ''
  detailVisible.value = true
  await Promise.all([loadDetail(), loadItems()])
}
const handleItemFilter = () => {
  itemQuery.pageNo = 1
  loadItems()
}
const refreshDetail = async () => {
  await Promise.all([loadDetail(), loadItems(), getList()])
}
const executing = ref(false)
const confirmImport = async () => {
  await message.confirm(
    '确认导入所有校验通过的行？重复或异常行会保留在任务中，不会写入相对方信息。'
  )
  executing.value = true
  try {
    await PartyImportApi.confirmPartyImport(currentJob.value.id, nextRequestId())
    message.success('有效行导入执行完成')
    await refreshDetail()
  } finally {
    executing.value = false
  }
}
const retryFailed = async () => {
  await message.confirm('仅重新校验并导入失败或异常行；已成功和重复跳过的行不会重复执行。')
  executing.value = true
  try {
    await PartyImportApi.retryFailedPartyImport(currentJob.value.id, nextRequestId())
    message.success('失败行重试完成')
    await refreshDetail()
  } finally {
    executing.value = false
  }
}
const downloadFailedFile = async () => {
  const data = await PartyImportApi.downloadPartyImportFailedFile(currentJob.value.id)
  download.excel(data, `相对方导入失败行-${currentJob.value.id}.xlsx`)
}

const itemEditorVisible = ref(false)
const itemSaving = ref(false)
const itemFormRef = ref<FormInstance>()
const itemForm = reactive<PartyImportApi.PartyImportItemUpdateReqVO>({
  id: '',
  name: '',
  unifiedCreditCode: '',
  contactName: '',
  contactPhone: ''
})
const itemRules: FormRules = {
  name: [{ required: true, message: '相对方名称不能为空', trigger: 'blur' }],
  unifiedCreditCode: [
    { required: true, message: '统一社会信用代码不能为空', trigger: 'blur' },
    {
      pattern: /^[0-9A-HJ-NPQRTUWXY]{18}$/,
      message: '请输入 18 位有效统一社会信用代码',
      trigger: 'blur'
    }
  ]
}
const openItemEditor = (row: PartyImportApi.PartyImportItemVO) => {
  Object.assign(itemForm, {
    id: row.id,
    name: row.name,
    unifiedCreditCode: row.unifiedCreditCode,
    contactName: row.contactName || '',
    contactPhone: row.contactPhone || ''
  })
  itemEditorVisible.value = true
  nextTick(() => itemFormRef.value?.clearValidate())
}
const saveItem = async () => {
  if (!(await itemFormRef.value?.validate().catch(() => false))) return
  itemSaving.value = true
  try {
    await PartyImportApi.updatePartyImportItem({
      ...itemForm,
      name: itemForm.name.trim(),
      unifiedCreditCode: itemForm.unifiedCreditCode.trim().toUpperCase(),
      contactName: itemForm.contactName?.trim() || undefined,
      contactPhone: itemForm.contactPhone?.trim() || undefined
    })
    message.success('该行已保存并重新校验')
    itemEditorVisible.value = false
    await refreshDetail()
  } finally {
    itemSaving.value = false
  }
}

onMounted(getList)
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.header-actions,
.detail-heading,
.detail-actions {
  display: flex;
  align-items: center;
}

.page-header,
.detail-heading,
.detail-actions {
  justify-content: space-between;
  gap: 16px;
}

.page-header {
  align-items: flex-start;
}

.page-header h2,
.detail-heading h3 {
  margin: 0 0 6px;
}

.page-header h2 {
  font-size: 22px;
}

.page-header p,
.detail-heading p {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.header-actions {
  flex-wrap: wrap;
  gap: 8px;
}

.count-text + .count-text {
  margin-left: 10px;
}

.success {
  color: var(--el-color-success);
}

.danger {
  color: var(--el-color-danger);
}

.muted {
  color: var(--el-text-color-secondary);
}

.detail-body {
  min-height: 260px;
}

.summary-row {
  margin: 16px 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 4px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.summary-item strong {
  font-size: 22px;
}

.summary-item span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.detail-actions {
  margin-bottom: 12px;
}
</style>
