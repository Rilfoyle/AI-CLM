<template>
  <ContentWrap>
    <div class="mb-16px flex flex-wrap items-start justify-between gap-10px">
      <div>
        <div class="text-20px font-bold">合同查询</div>
        <div class="mt-5px text-13px text-[var(--el-text-color-secondary)]">
          按本人经办、参与、审批或组织授权范围查询合同；审批完成即为第一期终点。
        </div>
      </div>
      <el-button type="primary" v-hasPermi="['clm:contract:create']" @click="handleCreate">
        <Icon icon="ep:plus" class="mr-5px" /> 发起合同
      </el-button>
    </div>

    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="72px"
    >
      <el-form-item label="关键词" prop="title">
        <el-input
          v-model="queryParams.title"
          class="!w-220px"
          clearable
          placeholder="合同名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="合同编号" prop="contractNo">
        <el-input
          v-model="queryParams.contractNo"
          class="!w-200px"
          clearable
          placeholder="草稿可为空"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="合同分类" prop="contractTypeId">
        <el-select
          v-model="queryParams.contractTypeId"
          class="!w-180px"
          clearable
          placeholder="全部类型"
        >
          <el-option v-for="item in typeList" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="相对方" prop="counterpartyName">
        <el-input
          v-model="queryParams.counterpartyName"
          class="!w-180px"
          clearable
          placeholder="相对方名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="查询范围" prop="scope">
        <el-select v-model="queryParams.scope" class="!w-160px" placeholder="我的经办">
          <el-option label="我的经办" value="HANDLED" />
          <el-option label="我参与的" value="PARTICIPATED" />
          <el-option label="我审批的" value="APPROVED_BY_ME" />
          <el-option label="授权组织" value="AUTHORIZED_ORG" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>

    <div class="saved-filter-bar">
      <div class="saved-filter-picker">
        <span>我的筛选</span>
        <el-select
          v-model="activeSavedFilterId"
          placeholder="选择已保存筛选"
          clearable
          class="!w-240px"
          @change="applySavedFilterById"
        >
          <el-option
            v-for="filter in savedFilters"
            :key="filter.id"
            :label="filter.defaultFlag ? `${filter.name}（默认）` : filter.name"
            :value="filter.id"
          />
        </el-select>
        <el-button link type="primary" @click="saveFilterDialogVisible = true">
          <Icon icon="ep:collection-tag" class="mr-4px" />保存当前筛选
        </el-button>
        <el-button v-if="activeSavedFilterId" link type="danger" @click="handleDeleteSavedFilter">
          删除
        </el-button>
      </div>
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="list"><Icon icon="ep:list" class="mr-4px" />列表</el-radio-button>
        <el-radio-button value="progress">
          <Icon icon="ep:guide" class="mr-4px" />审批进度
        </el-radio-button>
      </el-radio-group>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-tabs v-model="activeTab" class="-mt-10px" @tab-change="handleTabChange">
      <el-tab-pane v-for="tab in stageTabs" :key="tab.key" :label="tab.label" :name="tab.key" />
    </el-tabs>

    <el-alert
      v-if="listError"
      class="mb-12px"
      type="warning"
      :closable="false"
      show-icon
      title="合同列表加载失败"
    >
      <template #default>
        <el-link type="primary" :underline="false" @click="getList">重新加载</el-link>
      </template>
    </el-alert>

    <el-table
      v-if="viewMode === 'list'"
      v-loading="loading"
      :data="list"
      stripe
      show-overflow-tooltip
    >
      <el-table-column label="合同编号" prop="contractNo" width="170">
        <template #default="scope">{{ scope.row.contractNo || '草稿未编号' }}</template>
      </el-table-column>
      <el-table-column label="合同名称" prop="title" min-width="230">
        <template #default="scope">
          <el-link type="primary" :underline="false" @click="handleDetail(scope.row)">
            {{ scope.row.title || '未命名合同' }}
          </el-link>
          <div
            class="mt-3px flex items-center gap-5px text-12px text-[var(--el-text-color-secondary)]"
          >
            <el-tag v-if="scope.row.sourceMode" size="small" type="info">
              {{ scope.row.sourceMode === 'TEMPLATE' ? '标准模板' : '文件上传' }}
            </el-tag>
            <span v-if="scope.row.currentRevisionNo"
              >当前修订 R{{ scope.row.currentRevisionNo }}</span
            >
          </div>
        </template>
      </el-table-column>
      <el-table-column label="合同分类" prop="typeName" width="150" />
      <el-table-column label="相对方" min-width="160">
        <template #default="scope">{{ counterpartyText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="金额" align="right" width="150">
        <template #default="scope">{{
          formatAmount(scope.row.currency, scope.row.amount)
        }}</template>
      </el-table-column>
      <el-table-column label="阶段" align="center" width="110">
        <template #default="scope">
          <el-tag :type="stageTagType(scope.row.stageCode)">{{ stageText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="负责人" prop="ownerUserName" align="center" width="110" />
      <el-table-column
        label="更新时间"
        prop="updateTime"
        align="center"
        width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleDetail(scope.row)">
            {{ scope.row.deleted ? '查看' : '打开' }}
          </el-button>
          <el-button
            v-if="scope.row.deleted"
            v-hasPermi="['clm:contract:restore']"
            link
            type="success"
            @click="handleRestore(scope.row)"
            >恢复</el-button
          >
          <el-button
            v-else-if="canDelete(scope.row)"
            v-hasPermi="['clm:contract:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <div v-else v-loading="loading" class="progress-list">
      <button
        v-for="contract in list"
        :key="contract.id"
        type="button"
        class="progress-card"
        @click="handleDetail(contract)"
      >
        <div class="progress-card__header">
          <div>
            <strong>{{ contract.title || '未命名合同' }}</strong>
            <span
              >{{ contract.contractNo || '草稿未编号' }} · {{ contract.typeName || '未分类' }}</span
            >
          </div>
          <el-tag :type="stageTagType(contract.stageCode)">{{ stageText(contract) }}</el-tag>
        </div>
        <el-steps :active="stageStep(contract)" finish-status="success" align-center>
          <el-step title="合同起草" />
          <el-step title="协同与校验" />
          <el-step title="合同审批" />
          <el-step title="审批完成" />
        </el-steps>
        <div class="progress-card__footer">
          <span>负责人：{{ contract.ownerUserName || '-' }}</span>
          <span
            >当前修订：{{
              contract.currentRevisionNo ? `R${contract.currentRevisionNo}` : '-'
            }}</span
          >
          <span>相对方：{{ counterpartyText(contract) }}</span>
        </div>
      </button>
    </div>

    <el-empty
      v-if="!loading && !listError && list.length === 0"
      :description="emptyText"
      :image-size="80"
    >
      <el-button v-if="activeTab !== 'deleted'" type="primary" @click="handleCreate"
        >发起合同</el-button
      >
    </el-empty>
    <Pagination
      v-if="total > 0"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <el-dialog v-model="saveFilterDialogVisible" title="保存当前筛选" width="460px">
    <el-form label-width="90px">
      <el-form-item label="筛选名称" required>
        <el-input
          v-model="savedFilterForm.name"
          maxlength="128"
          show-word-limit
          placeholder="例如：华东区审批中采购合同"
        />
      </el-form-item>
      <el-form-item label="设为默认">
        <el-switch v-model="savedFilterForm.defaultFlag" />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
          下次进入台账自动应用
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="saveFilterDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="savingFilter" @click="handleSaveFilter">保存</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as ContractApi from '@/api/clm/contract'
import * as ContractTypeApi from '@/api/clm/contractType'
import * as SavedFilterApi from '@/api/clm/savedFilter'

defineOptions({ name: 'ClmContract' })

type StageTabKey =
  | 'all'
  | 'draft'
  | 'collaboration'
  | 'approving'
  | 'approved'
  | 'needsAction'
  | 'deleted'

const message = useMessage()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const listError = ref(false)
const total = ref(0)
const list = ref<ContractApi.ContractVO[]>([])
const typeList = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  title: undefined as string | undefined,
  contractNo: undefined as string | undefined,
  contractTypeId: undefined as number | undefined,
  counterpartyName: undefined as string | undefined,
  scope: 'HANDLED',
  stageCode: undefined as string | undefined,
  approvalStatus: undefined as number | undefined,
  deletedOnly: false
})
const viewMode = ref<'list' | 'progress'>('list')
const savedFilters = ref<SavedFilterApi.SavedFilterVO[]>([])
const activeSavedFilterId = ref<string>()
const saveFilterDialogVisible = ref(false)
const savingFilter = ref(false)
const savedFilterForm = reactive({ name: '', defaultFlag: false })

const stageTabs: { key: StageTabKey; label: string; stageCode?: string; deletedOnly?: boolean }[] =
  [
    { key: 'all', label: '全部' },
    { key: 'draft', label: '草稿', stageCode: 'DRAFT' },
    { key: 'collaboration', label: '法务协同', stageCode: 'COLLABORATING' },
    { key: 'approving', label: '审批中', stageCode: 'APPROVING' },
    { key: 'approved', label: '审批完成', stageCode: 'APPROVED' },
    { key: 'needsAction', label: '需我处理', stageCode: 'NEEDS_CHANGE' },
    { key: 'deleted', label: '已删除草稿', deletedOnly: true }
  ]
const activeTab = ref<StageTabKey>('all')
const emptyText = computed(() =>
  activeTab.value === 'deleted' ? '暂无可恢复的已删除草稿' : '暂无符合条件的合同'
)

const getList = async () => {
  loading.value = true
  listError.value = false
  try {
    const data = await ContractApi.getContractPage(queryParams)
    const rows = data?.list || []
    list.value = queryParams.deletedOnly ? rows.filter((row) => row.deleted) : rows
    total.value =
      queryParams.deletedOnly && rows.some((row) => !row.deleted)
        ? list.value.length
        : data?.total || 0
  } catch {
    list.value = []
    total.value = 0
    listError.value = true
  } finally {
    loading.value = false
  }
}

const handleTabChange = (key: string | number) => {
  const tab = stageTabs.find((item) => item.key === key)
  if (!tab) return
  queryParams.stageCode = tab.stageCode
  queryParams.approvalStatus = legacyApprovalStatus(tab.stageCode)
  queryParams.deletedOnly = !!tab.deletedOnly
  queryParams.pageNo = 1
  getList()
}

const applyRouteQuery = () => {
  const stageCode = typeof route.query.stageCode === 'string' ? route.query.stageCode : undefined
  const matched = stageTabs.find((tab) => tab.stageCode === stageCode)
  activeTab.value = matched?.key || 'all'
  queryParams.stageCode = stageCode
  queryParams.approvalStatus = legacyApprovalStatus(stageCode)
  queryParams.deletedOnly = activeTab.value === 'deleted'
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  Object.assign(queryParams, {
    pageNo: 1,
    stageCode: undefined,
    approvalStatus: undefined,
    deletedOnly: false,
    scope: 'HANDLED'
  })
  activeTab.value = 'all'
  activeSavedFilterId.value = undefined
  getList()
}

const loadSavedFilters = async () => {
  try {
    savedFilters.value = (await SavedFilterApi.getSavedFilterList('CONTRACT_LEDGER')) || []
  } catch {
    savedFilters.value = []
  }
}

const savedFilterPayload = () => ({
  title: queryParams.title,
  contractNo: queryParams.contractNo,
  counterpartyName: queryParams.counterpartyName,
  typeId: queryParams.contractTypeId,
  scope: queryParams.scope,
  stageCode: queryParams.stageCode,
  approvalStatus: queryParams.approvalStatus,
  viewMode: viewMode.value
})

const applySavedFilter = (filter: SavedFilterApi.SavedFilterVO) => {
  try {
    const value = JSON.parse(filter.filterJson) as Record<string, any>
    Object.assign(queryParams, {
      pageNo: 1,
      title: value.title,
      contractNo: value.contractNo,
      counterpartyName: value.counterpartyName,
      contractTypeId: value.typeId,
      scope: value.scope || 'HANDLED',
      stageCode: value.stageCode,
      approvalStatus: value.approvalStatus,
      deletedOnly: false
    })
    viewMode.value = value.viewMode === 'progress' ? 'progress' : 'list'
    const matched = stageTabs.find((tab) => tab.stageCode === value.stageCode)
    activeTab.value = matched?.key || 'all'
    getList()
  } catch {
    message.error('已保存筛选内容无效，请删除后重新保存')
  }
}

const applySavedFilterById = (id?: string) => {
  if (!id) return
  const filter = savedFilters.value.find((item) => String(item.id) === String(id))
  if (filter) applySavedFilter(filter)
}

const handleSaveFilter = async () => {
  if (!savedFilterForm.name.trim()) {
    message.warning('请输入筛选名称')
    return
  }
  savingFilter.value = true
  try {
    const id = await SavedFilterApi.saveSavedFilter({
      sceneCode: 'CONTRACT_LEDGER',
      name: savedFilterForm.name.trim(),
      filterJson: JSON.stringify(savedFilterPayload()),
      defaultFlag: savedFilterForm.defaultFlag
    })
    message.success('当前筛选已保存')
    saveFilterDialogVisible.value = false
    savedFilterForm.name = ''
    savedFilterForm.defaultFlag = false
    await loadSavedFilters()
    activeSavedFilterId.value = String(id)
  } finally {
    savingFilter.value = false
  }
}

const handleDeleteSavedFilter = async () => {
  if (!activeSavedFilterId.value) return
  const filter = savedFilters.value.find(
    (item) => String(item.id) === String(activeSavedFilterId.value)
  )
  await message.confirm(`确认删除筛选“${filter?.name || ''}”？`)
  await SavedFilterApi.deleteSavedFilter(activeSavedFilterId.value)
  message.success('已保存筛选已删除')
  activeSavedFilterId.value = undefined
  await loadSavedFilters()
}

const handleCreate = () => router.push('/clm/drafting/draft-center')
const handleDetail = (row: ContractApi.ContractVO) => {
  if (row.id === undefined) return
  router.push({
    name: 'ClmContractDetail',
    params: { id: String(row.id) },
    query: row.deleted ? { deleted: '1' } : {}
  })
}

const handleDelete = async (row: ContractApi.ContractVO) => {
  if (!row.id) return
  try {
    await message.confirm('删除后草稿会进入“已删除草稿”，可由草稿所有者恢复。')
    await ContractApi.deleteContract(row.id)
    message.success('草稿已删除')
    await getList()
  } catch {}
}

const handleRestore = async (row: ContractApi.ContractVO) => {
  if (!row.id) return
  try {
    await message.confirm('确认恢复该草稿？')
    await ContractApi.restoreContractDraft(row.id)
    message.success('草稿已恢复')
    await getList()
  } catch {}
}

const legacyApprovalStatus = (stageCode?: string) => {
  const statuses: Record<string, number> = {
    DRAFT: 0,
    APPROVING: 1,
    APPROVED: 2,
    NEEDS_CHANGE: 3
  }
  return stageCode ? statuses[stageCode] : undefined
}
const canDelete = (row: ContractApi.ContractVO) =>
  (row.stageCode === 'DRAFT' || (!row.stageCode && row.approvalStatus === 0)) &&
  !!row.permissions?.canDelete
const stageText = (row: ContractApi.ContractVO) => {
  if (row.deleted) return '已删除'
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    COLLABORATING: '法务协同',
    APPROVING: '审批中',
    APPROVED: '审批完成',
    NEEDS_CHANGE: '需修改',
    REJECTED: '已拒绝',
    BLOCKED: '配置阻断'
  }
  if (row.stageCode) return labels[row.stageCode] || row.stageCode
  return (
    ({ 0: '草稿', 1: '审批中', 2: '审批完成', 3: '需修改' } as Record<number, string>)[
      row.approvalStatus ?? -1
    ] || '处理中'
  )
}
const stageTagType = (stageCode?: string) => {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    DRAFT: 'info',
    COLLABORATING: 'warning',
    APPROVING: 'primary',
    APPROVED: 'success',
    NEEDS_CHANGE: 'warning',
    REJECTED: 'danger',
    BLOCKED: 'danger'
  }
  return (stageCode && types[stageCode]) || 'info'
}
const stageStep = (row: ContractApi.ContractVO) => {
  const stage = row.stageCode || stageText(row)
  if (stage === 'APPROVED' || stage === '审批完成') return 4
  if (stage === 'APPROVING' || stage === '审批中') return 3
  if (stage === 'COLLABORATING' || stage === '法务协同') return 2
  return 1
}
const counterpartyText = (row: ContractApi.ContractVO) => {
  if (row.counterpartyNames?.length) return row.counterpartyNames.join('、')
  if (row.counterpartyName) return row.counterpartyName
  const parties = (row.parties || []) as ContractApi.ContractPartyVO[]
  const names = parties
    .filter((item) => item.roleCode === 'COUNTERPARTY')
    .map((item) => item.partyName || String(item.partySnapshot?.name || ''))
    .filter(Boolean)
  return names.join('、') || '-'
}
const formatAmount = (currency?: string, amount?: number) =>
  amount === undefined || amount === null
    ? '-'
    : `${currency || 'CNY'} ${Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

onActivated(() => {
  applyRouteQuery()
  getList()
})

onMounted(async () => {
  try {
    typeList.value = (await ContractTypeApi.getContractTypeSimpleList()) || []
  } catch {
    typeList.value = []
  }
  await loadSavedFilters()
  applyRouteQuery()
  if (!route.query.stageCode) {
    const defaultFilter = savedFilters.value.find((item) => item.defaultFlag)
    if (defaultFilter) {
      activeSavedFilterId.value = defaultFilter.id
      applySavedFilter(defaultFilter)
      return
    }
  }
  await getList()
})
</script>

<style scoped>
.saved-filter-bar,
.saved-filter-picker,
.progress-card__header,
.progress-card__footer {
  display: flex;
  align-items: center;
}

.saved-filter-bar,
.progress-card__header {
  justify-content: space-between;
}

.saved-filter-bar {
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
  gap: 16px;
}

.saved-filter-picker {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  flex-wrap: wrap;
  gap: 8px;
}

.progress-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
}

.progress-card {
  width: 100%;
  padding: 18px 20px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.progress-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--el-box-shadow-light);
}

.progress-card__header {
  gap: 16px;
  margin-bottom: 18px;
}

.progress-card__header div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-card__header span,
.progress-card__footer {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.progress-card__footer {
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 16px;
}

@media (width <= 900px) {
  .saved-filter-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
