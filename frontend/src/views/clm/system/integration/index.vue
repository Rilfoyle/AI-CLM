<template>
  <div class="system-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>钉钉集成</h2>
          <p>检查 SSO 映射与合同待办消息投递；密钥仅显示配置状态，失败投递可按幂等键安全重试。</p>
        </div>
        <el-tag :type="integrationStatus?.configured ? 'success' : 'warning'" size="large">
          {{ integrationStatus?.configured ? '已配置' : '本地沙箱' }}
        </el-tag>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        :title="integrationStatus?.configured ? '钉钉凭据已配置' : '真实钉钉凭据未配置'"
        :description="integrationStatus?.message || '正在读取集成状态'"
        :type="integrationStatus?.configured ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <el-descriptions :column="3" border class="mt-16px">
        <el-descriptions-item label="运行模式">{{
          integrationStatus?.mode || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="密钥状态">
          {{ integrationStatus?.configured ? '已安全配置（不显示）' : '未配置' }}
        </el-descriptions-item>
        <el-descriptions-item label="外部调用">
          {{ integrationStatus?.configured ? '按配置执行' : '未外发' }}
        </el-descriptions-item>
      </el-descriptions>
    </ContentWrap>

    <ContentWrap>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="SSO 映射检查" name="sso">
          <div class="tab-toolbar">
            <div>
              <h3>SSO 身份映射</h3>
              <p>检查外部用户标识与本地身份映射；沙箱模式只记录检查结果。</p>
            </div>
            <el-button
              type="primary"
              :loading="checkingSso"
              @click="runSsoCheck"
              v-hasPermi="['clm:integration:run']"
            >
              运行 SSO 检查
            </el-button>
          </div>
          <el-table v-loading="runLoading" :data="ssoRuns" empty-text="暂无 SSO 检查记录">
            <el-table-column prop="runKey" label="检查标识" min-width="220" show-overflow-tooltip />
            <el-table-column prop="mode" label="模式" width="100" />
            <el-table-column label="状态" width="140">
              <template #default="scope">
                <el-tag :type="runStatusType(scope.row.status)">{{
                  runStatusText(scope.row.status)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="检查摘要" min-width="250">
              <template #default="scope">{{ ssoSummary(scope.row.summaryJson) }}</template>
            </el-table-column>
            <el-table-column
              prop="errorMessage"
              label="说明"
              min-width="220"
              show-overflow-tooltip
            />
            <el-table-column label="完成时间" width="180">
              <template #default="scope">{{ formatNullableDate(scope.row.finishedTime) }}</template>
            </el-table-column>
          </el-table>
          <Pagination
            v-model:page="runQuery.pageNo"
            v-model:limit="runQuery.pageSize"
            :total="runTotal"
            @pagination="getSsoRuns"
          />
        </el-tab-pane>

        <el-tab-pane label="消息投递" name="delivery">
          <div class="tab-toolbar">
            <div>
              <h3>合同待办与结果消息</h3>
              <p>按投递幂等键查看状态和失败原因；重试不会产生重复业务消息。</p>
            </div>
            <div class="delivery-filter">
              <el-select
                v-model="deliveryQuery.status"
                clearable
                placeholder="全部状态"
                class="!w-170px"
                @change="handleDeliveryFilter"
              >
                <el-option label="待发送" value="PENDING" />
                <el-option label="发送成功" value="SUCCEEDED" />
                <el-option label="发送失败" value="FAILED" />
                <el-option label="未配置" value="NOT_CONFIGURED" />
              </el-select>
              <el-button @click="getDeliveries"
                ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
              >
            </div>
          </div>
          <el-table v-loading="deliveryLoading" :data="deliveries" empty-text="暂无消息投递记录">
            <el-table-column
              prop="deliveryKey"
              label="投递标识"
              min-width="220"
              show-overflow-tooltip
            />
            <el-table-column prop="messageType" label="消息类型" min-width="140" />
            <el-table-column prop="recipientUserId" label="接收用户" width="110" />
            <el-table-column label="业务上下文" min-width="170">
              <template #default="scope">
                <span v-if="scope.row.contractId">合同 #{{ scope.row.contractId }}</span>
                <span v-if="scope.row.taskId" class="ml-8px">任务 {{ scope.row.taskId }}</span>
                <span v-if="!scope.row.contractId && !scope.row.taskId">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="scope">
                <el-tag :type="deliveryStatusType(scope.row.status)">
                  {{ deliveryStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="attemptCount" label="尝试次数" width="90" align="center" />
            <el-table-column
              prop="lastError"
              label="最近失败"
              min-width="230"
              show-overflow-tooltip
            />
            <el-table-column label="下次重试" width="180">
              <template #default="scope">{{
                formatNullableDate(scope.row.nextRetryTime)
              }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="scope">
                <el-button
                  v-if="scope.row.status !== 'SUCCEEDED'"
                  link
                  type="primary"
                  :loading="retryingId === scope.row.id"
                  @click="retryDelivery(scope.row)"
                  v-hasPermi="['clm:integration:run']"
                >
                  重试
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            v-model:page="deliveryQuery.pageNo"
            v-model:limit="deliveryQuery.pageSize"
            :total="deliveryTotal"
            @pagination="getDeliveries"
          />
        </el-tab-pane>
      </el-tabs>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import * as IntegrationApi from '@/api/clm/system/integration'

defineOptions({ name: 'ClmSystemIntegration' })

const message = useMessage()
const activeTab = ref('sso')
const integrationStatus = ref<IntegrationApi.IntegrationStatusVO>()
const checkingSso = ref(false)
const runLoading = ref(false)
const ssoRuns = ref<IntegrationApi.IntegrationRunVO[]>([])
const runTotal = ref(0)
const runQuery = reactive({ pageNo: 1, pageSize: 10, type: 'DINGTALK_SSO_CHECK' })
const deliveryLoading = ref(false)
const deliveries = ref<IntegrationApi.IntegrationDeliveryVO[]>([])
const deliveryTotal = ref(0)
const deliveryQuery = reactive({ pageNo: 1, pageSize: 10, status: undefined as string | undefined })
const retryingId = ref<string>()

const parseSummary = (json?: string) => {
  try {
    return JSON.parse(json || '{}') as Record<string, any>
  } catch {
    return {}
  }
}

const ssoSummary = (json?: string) => {
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

const deliveryStatusText = (status: string) => {
  return (
    {
      PENDING: '待发送',
      SUCCEEDED: '发送成功',
      FAILED: '发送失败',
      NOT_CONFIGURED: '未配置'
    }[status] || status
  )
}

const deliveryStatusType = (status: string) => {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'NOT_CONFIGURED') return 'warning'
  return 'info'
}

const getStatus = async () => {
  try {
    integrationStatus.value = await IntegrationApi.getDingtalkStatus()
  } catch {
    integrationStatus.value = undefined
  }
}

const getSsoRuns = async () => {
  runLoading.value = true
  try {
    const data = await IntegrationApi.getIntegrationRunPage(runQuery)
    ssoRuns.value = data.list || []
    runTotal.value = data.total || 0
  } finally {
    runLoading.value = false
  }
}

const getDeliveries = async () => {
  deliveryLoading.value = true
  try {
    const data = await IntegrationApi.getIntegrationDeliveryPage(deliveryQuery)
    deliveries.value = data.list || []
    deliveryTotal.value = data.total || 0
  } finally {
    deliveryLoading.value = false
  }
}

const runSsoCheck = async () => {
  checkingSso.value = true
  try {
    await IntegrationApi.runDingtalkSandbox({
      integrationType: 'DINGTALK_SSO_CHECK',
      runKey: `sso-check:${crypto.randomUUID?.() || Date.now()}`
    })
    message.success(
      integrationStatus.value?.configured
        ? 'SSO 映射检查已完成'
        : '本地沙箱检查已记录，未发出外部请求'
    )
    runQuery.pageNo = 1
    await getSsoRuns()
  } finally {
    checkingSso.value = false
  }
}

const handleDeliveryFilter = () => {
  deliveryQuery.pageNo = 1
  getDeliveries()
}

const retryDelivery = async (row: IntegrationApi.IntegrationDeliveryVO) => {
  retryingId.value = row.id
  try {
    await IntegrationApi.retryIntegrationDelivery(row.id)
    message.success(
      integrationStatus.value?.configured ? '消息已进入重试' : '沙箱重试已记录，真实消息未外发'
    )
    await getDeliveries()
  } finally {
    retryingId.value = undefined
  }
}

onMounted(async () => {
  await Promise.all([getStatus(), getSsoRuns(), getDeliveries()])
})
</script>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.tab-toolbar,
.delivery-filter {
  display: flex;
  align-items: center;
}

.page-header,
.tab-toolbar {
  justify-content: space-between;
  gap: 24px;
}

.page-header {
  align-items: flex-start;
}

.page-header h2,
.tab-toolbar h3 {
  margin: 0 0 6px;
}

.page-header h2 {
  font-size: 22px;
}

.tab-toolbar h3 {
  font-size: 16px;
}

.page-header p,
.tab-toolbar p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.tab-toolbar {
  margin-bottom: 16px;
}

.delivery-filter {
  gap: 8px;
}
</style>
