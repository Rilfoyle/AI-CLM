<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>审批异常</h2>
          <p>
            通过流程对账识别审批引擎与合同状态差异：合同管理员确认业务事实，系统管理员依据引擎权威事实执行技术恢复。
          </p>
        </div>
        <el-button v-if="canReplay" type="primary" :loading="running" @click="handleRun('MANUAL')">
          <Icon icon="ep:refresh-right" class="mr-5px" />立即检查
        </el-button>
      </div>
    </ContentWrap>

    <div class="metric-grid">
      <ContentWrap v-for="metric in metrics" :key="metric.label" class="metric-card">
        <span>{{ metric.label }}</span>
        <strong :class="metric.tone">{{ metric.value }}</strong>
      </ContentWrap>
    </div>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        title="审批异常检查服务暂时不可用"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无对账记录">
        <el-table-column prop="runKey" label="运行标识" min-width="210" show-overflow-tooltip />
        <el-table-column label="触发方式" width="110">
          <template #default="scope">{{ triggerText(scope.row.triggerType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{
              statusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scannedCount" label="扫描" width="80" align="center" />
        <el-table-column prop="issueCount" label="异常" width="80" align="center">
          <template #default="scope">
            <span :class="{ 'danger-text': scope.row.issueCount > 0 }">{{
              scope.row.issueCount
            }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="repairedCount" label="已恢复" width="90" align="center">
          <template #default="scope">
            <span :class="{ 'success-text': scope.row.repairedCount > 0 }">{{
              scope.row.repairedCount
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="180">
          <template #default="scope">{{ formatNullableDate(scope.row.finishedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showReport(scope.row)">查看报告</el-button>
            <el-button
              v-if="canReplay && (scope.row.status === 'FAILED' || scope.row.status === 'PARTIAL')"
              link
              type="warning"
              :loading="replayingId === scope.row.id"
              @click="handleReplay(scope.row)"
            >
              幂等重放
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </ContentWrap>

    <el-drawer v-model="reportVisible" title="审批异常检查报告" size="760px">
      <template v-if="currentRun">
        <el-descriptions :column="2" border class="mb-18px">
          <el-descriptions-item label="运行标识">{{ currentRun.runKey }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{
            statusText(currentRun.status)
          }}</el-descriptions-item>
          <el-descriptions-item label="扫描">{{ currentRun.scannedCount }}</el-descriptions-item>
          <el-descriptions-item label="异常 / 恢复">
            {{ currentRun.issueCount }} / {{ currentRun.repairedCount }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          type="info"
          :closable="false"
          title="对账报告不展示合同正文或审批意见；业务确认也不会触发外部系统、推进审批或修改合同。"
          show-icon
          class="mb-16px"
        />

        <section v-if="canConfirm" class="report-section">
          <div class="section-heading">
            <div>
              <h3>业务确认区</h3>
              <p>确认业务对象及审批引擎权威事实；确认记录不可覆盖，不代表异常已被技术修复。</p>
            </div>
            <el-tag type="warning" effect="plain">合同管理员</el-tag>
          </div>
          <el-table :data="businessReportRows" empty-text="本次运行没有待确认的差异或异常">
            <el-table-column prop="contractId" label="合同标识" min-width="100" />
            <el-table-column prop="authoritativeStatus" label="权威状态" min-width="100" />
            <el-table-column prop="projectionStatus" label="业务状态" min-width="100" />
            <el-table-column label="差异结果" min-width="170">
              <template #default="scope">{{ resultText(scope.row.result) }}</template>
            </el-table-column>
            <el-table-column label="业务确认" min-width="210">
              <template #default="scope">
                <template v-if="scope.row.confirmationStatus === 'CONFIRMED'">
                  <el-tag type="success" size="small">已确认</el-tag>
                  <div class="confirmation-meta">
                    用户 {{ scope.row.confirmedBy }} ·
                    {{ formatNullableDate(scope.row.confirmedTime) }}
                  </div>
                  <div class="confirmation-opinion">{{ scope.row.confirmationOpinion }}</div>
                </template>
                <el-tag v-else type="warning" size="small">待确认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="scope">
                <el-button
                  v-if="scope.row.confirmationStatus !== 'CONFIRMED'"
                  link
                  type="primary"
                  @click="openConfirm(scope.row)"
                >
                  业务确认
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-if="canReplay" class="report-section">
          <div class="section-heading">
            <div>
              <h3>技术对账区</h3>
              <p>仅展示定位与重放所需信息，重放只能依据审批引擎权威事实恢复投影。</p>
            </div>
            <el-tag type="info" effect="plain">系统管理员</el-tag>
          </div>
          <el-table :data="reportRows" empty-text="本次运行没有逐项报告">
            <el-table-column prop="bindingId" label="审批业务单" min-width="120" />
            <el-table-column prop="contractId" label="合同标识" min-width="100" />
            <el-table-column prop="authoritativeStatus" label="权威状态" min-width="110" />
            <el-table-column prop="projectionStatus" label="投影状态" min-width="110" />
            <el-table-column label="检查结果" min-width="170">
              <template #default="scope">{{ resultText(scope.row.result) }}</template>
            </el-table-column>
            <el-table-column prop="error" label="失败类型" min-width="130" />
          </el-table>
        </section>

        <el-empty
          v-if="!canConfirm && !canReplay"
          description="当前账号没有业务确认或技术重放权限"
        />
      </template>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
        <el-button
          v-if="currentRun && canReplay && ['FAILED', 'PARTIAL'].includes(currentRun.status)"
          type="primary"
          :loading="replayingId === currentRun.id"
          @click="handleReplay(currentRun)"
        >
          幂等重放
        </el-button>
      </template>
    </el-drawer>

    <Dialog v-model="confirmVisible" title="业务确认" width="560px">
      <template v-if="confirmTarget">
        <el-descriptions :column="2" border class="mb-16px">
          <el-descriptions-item label="合同标识">{{
            confirmTarget.contractId
          }}</el-descriptions-item>
          <el-descriptions-item label="差异结果">
            {{ resultText(confirmTarget.result) }}
          </el-descriptions-item>
          <el-descriptions-item label="权威状态">
            {{ confirmTarget.authoritativeStatus ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="业务状态">
            {{ confirmTarget.projectionStatus ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="请确认业务对象与权威事实。提交后记录确认人、时间和意见，不执行技术修复。"
          class="mb-16px"
        />
        <el-form label-position="top">
          <el-form-item label="确认意见" required>
            <el-input
              v-model="confirmOpinion"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="请说明已核对的业务事实"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="confirming"
          :disabled="!confirmOpinion.trim()"
          @click="submitConfirm"
        >
          确认业务事实
        </el-button>
      </template>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import * as ReconciliationApi from '@/api/clm/governance/reconciliation'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'ClmGovernanceReconciliation' })

interface ReportRow {
  bindingId?: string | number
  contractId?: string | number
  authoritativeStatus?: string | number
  projectionStatus?: string | number
  result?: string
  error?: string
  confirmationStatus?: 'CONFIRMED'
  confirmedBy?: string | number
  confirmedTime?: string
  confirmationOpinion?: string
}

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const loadError = ref(false)
const running = ref(false)
const replayingId = ref<string>()
const list = ref<ReconciliationApi.ReconciliationRunVO[]>([])
const total = ref(0)
const queryParams = reactive({ pageNo: 1, pageSize: 10 })
const reportVisible = ref(false)
const currentRun = ref<ReconciliationApi.ReconciliationRunVO>()
const confirmVisible = ref(false)
const confirmTarget = ref<ReportRow>()
const confirmOpinion = ref('')
const confirming = ref(false)

const hasPermission = (permission: string) =>
  userStore.getPermissions.has('*:*:*') || userStore.getPermissions.has(permission)
const canConfirm = computed(() => hasPermission('clm:reconciliation:confirm'))
const canReplay = computed(() => hasPermission('clm:reconciliation:replay'))
const confirmableResults = new Set(['ISSUE_OPENED', 'PROJECTION_REPAIRED', 'CHECK_FAILED'])

const latest = computed(() => list.value[0])
const metrics = computed(() => [
  { label: '最近扫描业务单', value: latest.value?.scannedCount ?? '-', tone: '' },
  { label: '最近发现异常', value: latest.value?.issueCount ?? '-', tone: 'danger-text' },
  { label: '最近自动恢复', value: latest.value?.repairedCount ?? '-', tone: 'success-text' }
])

const reportRows = computed<ReportRow[]>(() => {
  if (!currentRun.value?.reportJson) return []
  try {
    const rows = JSON.parse(currentRun.value.reportJson)
    return Array.isArray(rows) ? rows : []
  } catch {
    return []
  }
})
const businessReportRows = computed(() =>
  reportRows.value.filter((row) => confirmableResults.has(row.result || ''))
)

const statusText = (status: ReconciliationApi.ReconciliationStatus) => {
  return {
    RUNNING: '运行中',
    SUCCEEDED: '一致',
    PARTIAL: '部分恢复',
    FAILED: '需处理'
  }[status]
}

const statusTagType = (status: ReconciliationApi.ReconciliationStatus) => {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const triggerText = (trigger: ReconciliationApi.ReconciliationRunVO['triggerType']) => {
  return { MANUAL: '人工触发', SCHEDULED: '定时任务', REPLAY: '幂等重放' }[trigger]
}

const resultText = (result?: string) => {
  return (
    {
      CONSISTENT: '一致，无需处理',
      PROJECTION_REPAIRED: '投影已按权威事实恢复',
      ISSUE_OPENED: '已生成配置异常',
      CHECK_FAILED: '检查失败，可安全重试'
    }[result || ''] ||
    result ||
    '-'
  )
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await ReconciliationApi.getReconciliationPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } catch {
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const makeRunKey = (prefix: string) => {
  const unique = typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : `${Date.now()}`
  return `${prefix}:${unique}`
}

const handleRun = async (triggerType: 'MANUAL' | 'REPLAY') => {
  running.value = true
  try {
    await ReconciliationApi.runReconciliation({
      runKey: makeRunKey(triggerType.toLowerCase()),
      triggerType
    })
    message.success('审批异常检查已完成')
    queryParams.pageNo = 1
    await getList()
  } finally {
    running.value = false
  }
}

const handleReplay = async (row: ReconciliationApi.ReconciliationRunVO) => {
  await message.confirm(
    '重放只会依据审批引擎权威事实幂等恢复绑定和投影，不会修改审批决定。确认继续？'
  )
  replayingId.value = row.id
  try {
    await ReconciliationApi.runReconciliation({
      runKey: makeRunKey(`replay:${row.id}`),
      triggerType: 'REPLAY'
    })
    message.success('幂等重放已完成')
    reportVisible.value = false
    queryParams.pageNo = 1
    await getList()
  } finally {
    replayingId.value = undefined
  }
}

const showReport = (row: ReconciliationApi.ReconciliationRunVO) => {
  currentRun.value = row
  reportVisible.value = true
}

const openConfirm = (row: ReportRow) => {
  confirmTarget.value = row
  confirmOpinion.value = ''
  confirmVisible.value = true
}

const submitConfirm = async () => {
  if (!currentRun.value || !confirmTarget.value?.bindingId || !confirmOpinion.value.trim()) return
  confirming.value = true
  const runId = currentRun.value.id
  try {
    await ReconciliationApi.confirmReconciliation({
      runId,
      bindingId: confirmTarget.value.bindingId,
      opinion: confirmOpinion.value.trim()
    })
    message.success('业务确认已记录')
    confirmVisible.value = false
    await getList()
    currentRun.value = list.value.find((item) => item.id === runId)
    if (!currentRun.value) reportVisible.value = false
  } finally {
    confirming.value = false
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

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-header p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-card span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-card strong {
  font-size: 28px;
}

.danger-text {
  color: var(--el-color-danger);
}

.success-text {
  color: var(--el-color-success);
}

.report-section + .report-section {
  padding-top: 22px;
  margin-top: 22px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.section-heading p,
.confirmation-meta,
.confirmation-opinion {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.section-heading p {
  font-size: 13px;
  line-height: 1.6;
}

.confirmation-meta {
  margin-top: 5px;
  font-size: 12px;
}

.confirmation-opinion {
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 900px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
