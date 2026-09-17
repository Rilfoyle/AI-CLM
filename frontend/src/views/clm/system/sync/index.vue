<template>
  <div class="system-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>组织与用户同步</h2>
          <p>先在沙箱中生成钉钉组织差异报告；真实凭据未配置时绝不外发请求，也不会改动本地身份。</p>
        </div>
        <el-button
          type="primary"
          :loading="running"
          @click="runSandbox"
          v-hasPermi="['clm:integration:run']"
        >
          <Icon icon="ep:refresh-right" class="mr-5px" />运行同步检查
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        :title="statusTitle"
        :description="integrationStatus?.message || '正在读取钉钉集成状态'"
        :type="integrationStatus?.configured ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <div class="status-grid">
        <div>
          <span>配置状态</span>
          <strong>{{ integrationStatus?.configured ? '已配置' : '未配置' }}</strong>
        </div>
        <div>
          <span>运行模式</span>
          <strong>{{ integrationStatus?.mode || '-' }}</strong>
        </div>
        <div>
          <span>外部请求</span>
          <strong>{{ integrationStatus?.configured ? '按配置执行' : '不会外发' }}</strong>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap>
      <div class="section-header">
        <div>
          <h3>同步检查记录</h3>
          <p>报告只展示新增、更新、停用和身份冲突数量；经办人变更由后续受控业务流程处理。</p>
        </div>
        <el-button @click="getRuns"><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button>
      </div>
      <el-alert
        v-if="loadError"
        title="同步检查记录加载失败"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      />
      <el-table v-loading="loading" :data="runs" empty-text="暂无组织同步检查记录">
        <el-table-column prop="runKey" label="检查标识" min-width="220" show-overflow-tooltip />
        <el-table-column label="模式" width="100">
          <template #default="scope"
            ><el-tag type="info">{{ scope.row.mode }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="scope">
            <el-tag :type="runStatusType(scope.row.status)">{{
              runStatusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="差异摘要" min-width="260">
          <template #default="scope">{{ summaryText(scope.row.summaryJson) }}</template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column label="完成时间" width="180">
          <template #default="scope">{{ formatNullableDate(scope.row.finishedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showReport(scope.row)">报告</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getRuns"
      />
    </ContentWrap>

    <el-drawer v-model="reportVisible" title="组织同步检查报告" size="540px">
      <template v-if="currentRun">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="检查标识">{{ currentRun.runKey }}</el-descriptions-item>
          <el-descriptions-item label="运行模式">{{ currentRun.mode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{
            runStatusText(currentRun.status)
          }}</el-descriptions-item>
          <el-descriptions-item label="外部请求">
            {{ runSummary.externalRequestSent ? '已发送' : '未发送' }}
          </el-descriptions-item>
          <el-descriptions-item label="计划新增">{{ runSummary.add || 0 }}</el-descriptions-item>
          <el-descriptions-item label="计划更新">{{ runSummary.update || 0 }}</el-descriptions-item>
          <el-descriptions-item label="计划停用">{{
            runSummary.disable || 0
          }}</el-descriptions-item>
          <el-descriptions-item label="身份冲突">{{
            runSummary.conflict || 0
          }}</el-descriptions-item>
          <el-descriptions-item label="业务交接状态">
            <template v-if="runSummary.handoverCaseId">
              交接单 #{{ runSummary.handoverCaseId }} ·
              {{ runSummary.handoverStatus || '已创建' }} ·
              {{ runSummary.handoverPendingCount || 0 }}/{{ runSummary.handoverItemCount || 0 }}
              项待处理
            </template>
            <template v-else>本次检查未产生经办人变更单</template>
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ formatNullableDate(currentRun.finishedTime) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="!integrationStatus?.configured"
          title="当前是确定性本地沙箱报告：未读取钉钉数据、未写入本地用户、未发出外部请求。"
          type="warning"
          :closable="false"
          show-icon
          class="mt-18px"
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import * as IntegrationApi from '@/api/clm/system/integration'

defineOptions({ name: 'ClmSystemSync' })

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const running = ref(false)
const integrationStatus = ref<IntegrationApi.IntegrationStatusVO>()
const runs = ref<IntegrationApi.IntegrationRunVO[]>([])
const total = ref(0)
const queryParams = reactive({ pageNo: 1, pageSize: 10, type: 'DINGTALK_ORG_SYNC' })
const reportVisible = ref(false)
const currentRun = ref<IntegrationApi.IntegrationRunVO>()

const statusTitle = computed(() =>
  integrationStatus.value?.configured ? '钉钉组织同步已配置' : '钉钉组织同步处于本地沙箱模式'
)

const parseSummary = (json?: string) => {
  try {
    return JSON.parse(json || '{}') as Record<string, any>
  } catch {
    return {}
  }
}

const runSummary = computed(() => parseSummary(currentRun.value?.summaryJson))

const summaryText = (json?: string) => {
  const summary = parseSummary(json)
  return `新增 ${summary.add || 0} · 更新 ${summary.update || 0} · 停用 ${summary.disable || 0} · 冲突 ${summary.conflict || 0}`
}

const runStatusText = (status: string) => {
  return (
    {
      SUCCEEDED: '检查完成',
      PARTIAL: '部分完成',
      FAILED: '检查失败',
      NOT_CONFIGURED: '未配置，未外发'
    }[status] || status
  )
}

const runStatusType = (status: string) => {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'NOT_CONFIGURED') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const getStatus = async () => {
  try {
    integrationStatus.value = await IntegrationApi.getDingtalkStatus()
  } catch {
    integrationStatus.value = undefined
  }
}

const getRuns = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await IntegrationApi.getIntegrationRunPage(queryParams)
    runs.value = data.list || []
    total.value = data.total || 0
  } catch {
    runs.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const runSandbox = async () => {
  running.value = true
  try {
    await IntegrationApi.runDingtalkSandbox({
      integrationType: 'DINGTALK_ORG_SYNC',
      runKey: `org-sync:${crypto.randomUUID?.() || Date.now()}`
    })
    message.success(
      integrationStatus.value?.configured
        ? '组织同步检查已完成'
        : '本地沙箱检查已记录，未发出外部请求'
    )
    queryParams.pageNo = 1
    await getRuns()
  } finally {
    running.value = false
  }
}

const showReport = (row: IntegrationApi.IntegrationRunVO) => {
  currentRun.value = row
  reportVisible.value = true
}

onMounted(async () => {
  await Promise.all([getStatus(), getRuns()])
})
</script>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.page-header h2,
.section-header h3 {
  margin: 0 0 6px;
}

.page-header h2 {
  font-size: 22px;
}

.section-header h3 {
  font-size: 16px;
}

.page-header p,
.section-header p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.status-grid div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.status-grid span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (width <= 900px) {
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
