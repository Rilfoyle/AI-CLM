<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>经办人变更</h2>
          <p
            >处理人员离职或身份停用后遗留的经办合同和活动审批任务；系统管理员不在此读取合同正文。</p
          >
        </div>
        <el-button @click="getList"><Icon icon="ep:refresh" class="mr-5px" />刷新清单</el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        class="mb-16px"
        type="info"
        :closable="false"
        show-icon
        title="变更前会校验接手人的有效产品角色、组织范围和合同分类范围；无覆盖范围时服务端会阻断。"
      />
      <el-form :model="query" inline class="-mb-15px">
        <el-form-item label="原经办人">
          <el-select
            v-model="query.sourceUserId"
            clearable
            filterable
            class="!w-220px"
            placeholder="全部用户"
          >
            <el-option
              v-for="item in users"
              :key="item.id"
              :label="`${item.nickname}（${item.username}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="交接状态">
          <el-select v-model="query.status" clearable class="!w-160px" placeholder="全部状态">
            <el-option
              v-for="item in statusOptions"
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
        title="经办人变更清单加载失败"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无待处理经办人变更">
        <el-table-column label="交接单" width="95">
          <template #default="scope">#{{ scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="原经办人" min-width="160">
          <template #default="scope">{{ userText(scope.row.sourceUserId) }}</template>
        </el-table-column>
        <el-table-column label="接手人" min-width="160">
          <template #default="scope">{{ userText(scope.row.targetUserId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="caseStatusType(scope.row.status)">{{
              caseStatusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="事项" width="130">
          <template #default="scope">
            {{ scope.row.itemCount || 0 }} 项 / 待处理 {{ scope.row.pendingCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="交接原因" min-width="210" show-overflow-tooltip />
        <el-table-column label="更新时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openDetail(scope.row.id)">
              {{ scope.row.pendingCount ? '处理' : '查看结果' }}
            </el-button>
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

    <el-drawer v-model="detailVisible" title="经办人变更详情" size="960px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail.id">
          <div class="detail-heading">
            <div>
              <h3>交接单 #{{ detail.id }}</h3>
              <p>原经办人：{{ userText(detail.sourceUserId) }}</p>
            </div>
            <el-tag :type="caseStatusType(detail.status)" size="large">
              {{ caseStatusText(detail.status) }}
            </el-tag>
          </div>
          <el-descriptions :column="3" border class="mt-14px mb-14px">
            <el-descriptions-item label="原经办/审批人">{{
              userText(detail.sourceUserId)
            }}</el-descriptions-item>
            <el-descriptions-item label="接手人">{{
              userText(detail.targetUserId)
            }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{
              formatNullableDate(detail.finishedTime)
            }}</el-descriptions-item>
            <el-descriptions-item label="交接原因" :span="3">{{
              detail.reason || '-'
            }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="lastResult"
            class="mb-14px"
            :type="
              lastResult.items.some((item) => item.status === 'FAILED') ? 'warning' : 'success'
            "
            :closable="false"
            show-icon
            :title="resultSummary"
          />

          <section v-if="outstandingItems.length" class="reassign-panel">
            <div class="section-header">
              <div>
                <h4>受控重分配</h4>
                <p>可一次处理全部待办，或只处理勾选项；失败项会保留并可再次重试。</p>
              </div>
            </div>
            <el-form label-width="100px">
              <el-form-item label="处理范围">
                <el-radio-group v-model="selectionMode">
                  <el-radio value="ALL">全部待处理项（{{ outstandingItems.length }}）</el-radio>
                  <el-radio value="PARTIAL">仅勾选项（{{ selectedItemIds.length }}）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="接手人" required>
                <el-select
                  v-model="targetUserId"
                  filterable
                  class="!w-420px"
                  placeholder="选择具备合同域有效授权的启用用户"
                >
                  <el-option
                    v-for="item in targetUsers"
                    :key="item.id"
                    :label="`${item.nickname}（${item.deptName || '未分配部门'}）`"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="交接原因" required>
                <el-input
                  v-model="reason"
                  type="textarea"
                  :rows="3"
                  maxlength="1000"
                  show-word-limit
                  placeholder="说明离职身份与交接依据"
                />
              </el-form-item>
              <el-form-item>
                <el-button
                  v-hasPermi="['clm:handover:reassign-task']"
                  type="primary"
                  :disabled="!reassignValid"
                  :loading="reassigning"
                  @click="reassign"
                >
                  确认重分配
                </el-button>
                <span class="scope-tip">接手人范围不覆盖任一合同组织或类型时，整批不会执行。</span>
              </el-form-item>
            </el-form>
          </section>

          <div class="section-header item-header">
            <div>
              <h4>交接事项与逐项结果</h4>
              <p>仅展示合同标识、活动任务标识、原/新处理人和执行结果，不展示正文或审批意见。</p>
            </div>
          </div>
          <el-table
            ref="itemTableRef"
            :data="detail.items || []"
            border
            size="small"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="46" :selectable="itemSelectable" />
            <el-table-column label="类型" width="100">
              <template #default="scope">
                {{ scope.row.itemType === 'CONTRACT' ? '经办合同' : '活动审批任务' }}
              </template>
            </el-table-column>
            <el-table-column label="合同" min-width="220">
              <template #default="scope">
                <strong>{{ scope.row.contractName || `合同 #${scope.row.contractId}` }}</strong>
                <div class="secondary">{{ scope.row.contractNo || '草稿未编号' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="任务标识" prop="taskId" min-width="150">
              <template #default="scope">{{ scope.row.taskId || '-' }}</template>
            </el-table-column>
            <el-table-column label="原处理人" width="130">
              <template #default="scope">{{ userText(scope.row.originalAssignee) }}</template>
            </el-table-column>
            <el-table-column label="新处理人" width="130">
              <template #default="scope">{{ userText(scope.row.targetUserId) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="scope">
                <el-tag :type="itemStatusType(scope.row.status)" size="small">
                  {{ itemStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="逐项结果"
              prop="resultMessage"
              min-width="210"
              show-overflow-tooltip
            >
              <template #default="scope">{{ scope.row.resultMessage || '等待处理' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import type { TableInstance } from 'element-plus'
import { formatNullableDate } from '@/utils/formatTime'
import * as HandoverApi from '@/api/clm/handover'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ClmGovernanceHandover' })
const message = useMessage()
const users = ref<UserApi.UserVO[]>([])
const loading = ref(false)
const loadError = ref(false)
const list = ref<HandoverApi.HandoverCaseSummaryVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  sourceUserId: undefined as number | undefined,
  status: ''
})
const statusOptions = [
  { label: '待处理', value: 'OPEN' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELED' }
]
const caseStatusText = (status: string) =>
  statusOptions.find((item) => item.value === status)?.label || status
const caseStatusType = (status: string): 'success' | 'warning' | 'info' | 'primary' => {
  const types: Record<string, 'success' | 'warning' | 'info' | 'primary'> = {
    OPEN: 'warning',
    PROCESSING: 'primary',
    COMPLETED: 'success',
    CANCELED: 'info'
  }
  return types[status] || 'info'
}
const itemStatusText = (status: string) =>
  ({ PENDING: '待处理', TRANSFERRED: '已交接', SKIPPED: '已跳过', FAILED: '失败' })[status] ||
  status
const itemStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'warning',
    TRANSFERRED: 'success',
    SKIPPED: 'info',
    FAILED: 'danger'
  }
  return types[status] || 'info'
}
const userText = (id?: string | number) => {
  if (!id) return '-'
  const user = users.value.find((item) => String(item.id) === String(id))
  return user ? `${user.nickname}（${user.username}）` : `用户 #${id}`
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await HandoverApi.getHandoverPage({
      ...query,
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
  Object.assign(query, { pageNo: 1, sourceUserId: undefined, status: '' })
  getList()
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<HandoverApi.HandoverDetailVO>({} as HandoverApi.HandoverDetailVO)
const itemTableRef = ref<TableInstance>()
const selectionMode = ref<'ALL' | 'PARTIAL'>('ALL')
const selectedItemIds = ref<string[]>([])
const targetUserId = ref<number>()
const reason = ref('')
const reassigning = ref(false)
const lastResult = ref<HandoverApi.HandoverReassignRespVO>()
const outstandingItems = computed(() =>
  (detail.value.items || []).filter((item) => ['PENDING', 'FAILED'].includes(item.status))
)
const targetUsers = computed(() =>
  users.value.filter((item) => String(item.id) !== String(detail.value.sourceUserId))
)
const reassignValid = computed(
  () =>
    !!targetUserId.value &&
    !!reason.value.trim() &&
    (selectionMode.value === 'ALL' || selectedItemIds.value.length > 0)
)
const resultSummary = computed(() => {
  if (!lastResult.value) return ''
  const counts = lastResult.value.items.reduce<Record<string, number>>((result, item) => {
    result[item.status] = (result[item.status] || 0) + 1
    return result
  }, {})
  return `本次逐项结果：交接 ${counts.TRANSFERRED || 0}，跳过 ${counts.SKIPPED || 0}，失败 ${counts.FAILED || 0}；剩余 ${lastResult.value.pendingCount} 项。`
})

const loadDetail = async () => {
  if (!detail.value.id) return
  detailLoading.value = true
  try {
    detail.value = await HandoverApi.getHandoverDetail(detail.value.id)
    targetUserId.value = detail.value.targetUserId ? Number(detail.value.targetUserId) : undefined
    reason.value = detail.value.reason || ''
    selectedItemIds.value = []
    itemTableRef.value?.clearSelection()
  } finally {
    detailLoading.value = false
  }
}
const openDetail = async (id: string | number) => {
  detail.value = { id: String(id) } as HandoverApi.HandoverDetailVO
  selectionMode.value = 'ALL'
  lastResult.value = undefined
  detailVisible.value = true
  await loadDetail()
}
const itemSelectable = (item: HandoverApi.HandoverItemVO) =>
  ['PENDING', 'FAILED'].includes(item.status)
const handleSelectionChange = (rows: HandoverApi.HandoverItemVO[]) => {
  selectedItemIds.value = rows.map((item) => item.id)
  if (rows.length) selectionMode.value = 'PARTIAL'
}
const reassign = async () => {
  if (!reassignValid.value) return
  const itemIds = selectionMode.value === 'PARTIAL' ? selectedItemIds.value : undefined
  await message.confirm(
    `确认将${itemIds ? `选中的 ${itemIds.length} 项` : `全部 ${outstandingItems.value.length} 项`}重分配给“${userText(targetUserId.value)}”？`
  )
  reassigning.value = true
  try {
    lastResult.value = await HandoverApi.reassignHandover({
      caseId: detail.value.id,
      targetUserId: targetUserId.value!,
      reason: reason.value.trim(),
      itemIds
    })
    message.success('交接执行完成，请查看逐项结果')
    await Promise.all([loadDetail(), getList()])
  } finally {
    reassigning.value = false
  }
}

onMounted(async () => {
  const [userResult] = await Promise.allSettled([UserApi.getSimpleUserList()])
  users.value = userResult.status === 'fulfilled' ? userResult.value || [] : []
  await getList()
})
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.detail-heading,
.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-header h2,
.detail-heading h3,
.section-header h4 {
  margin: 0 0 6px;
}

.page-header h2 {
  font-size: 22px;
}

.page-header p,
.detail-heading p,
.section-header p,
.scope-tip,
.secondary {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.page-header p {
  font-size: 13px;
}

.detail-body {
  min-height: 260px;
}

.reassign-panel {
  padding: 16px 18px 2px;
  margin-bottom: 20px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.section-header {
  margin-bottom: 12px;
}

.item-header {
  margin-top: 4px;
}

.scope-tip {
  margin-left: 10px;
}
</style>
