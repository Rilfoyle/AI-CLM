<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>配置异常</h2>
          <p
            >集中处理模板失效、编码冲突、业务单据流程配置零/多命中和审批人缺失；修复后通知原经办人重试。</p
          >
        </div>
        <el-button @click="getList"><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
        <el-form-item label="异常类型" prop="issueType">
          <el-select
            v-model="queryParams.issueType"
            placeholder="全部类型"
            clearable
            filterable
            class="!w-230px"
          >
            <el-option
              v-for="item in issueTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" class="!w-150px">
            <el-option label="待处理" value="OPEN" />
            <el-option label="已恢复" value="RESOLVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同 ID" prop="contractId">
          <el-input
            v-model="queryParams.contractId"
            clearable
            placeholder="按合同定位"
            class="!w-180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        title="配置异常服务暂时不可用"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="当前没有配置异常">
        <el-table-column label="异常类型" min-width="190">
          <template #default="scope">
            <el-tag :type="issueTagType(scope.row.issueType)">{{
              issueTypeText(scope.row.issueType)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="异常原因" min-width="300" show-overflow-tooltip />
        <el-table-column label="关联合同" width="120">
          <template #default="scope">
            <el-link
              v-if="scope.row.contractId"
              type="primary"
              @click="openContract(scope.row.contractId)"
            >
              #{{ scope.row.contractId }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'OPEN' ? 'danger' : 'success'">
              {{ scope.row.status === 'OPEN' ? '待处理' : '已恢复' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="恢复时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.resolvedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showDetail(scope.row)">详情</el-button>
            <el-button
              v-if="scope.row.status === 'OPEN'"
              link
              type="primary"
              @click="goToRepair(scope.row)"
            >
              定位修复
            </el-button>
            <el-button
              v-if="scope.row.status === 'OPEN'"
              link
              type="success"
              @click="handleResolve(scope.row)"
              v-hasPermi="['clm:governance-issue:update']"
            >
              标记已恢复
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

    <el-drawer v-model="detailVisible" title="配置异常详情" size="560px">
      <template v-if="currentIssue">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="异常类型">{{
            issueTypeText(currentIssue.issueType)
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ currentIssue.status === 'OPEN' ? '待处理' : '已恢复' }}
          </el-descriptions-item>
          <el-descriptions-item label="原因">{{ currentIssue.summary }}</el-descriptions-item>
          <el-descriptions-item label="合同">{{
            currentIssue.contractId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{
            currentIssue.sourceRef || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">
            {{ formatNullableDate(currentIssue.createTime) }}
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="detailRows.length" class="detail-section">
          <h3>诊断信息</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item v-for="row in detailRows" :key="row.label" :label="row.label">
              {{ row.value }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <el-alert
          type="info"
          :closable="false"
          title="先进入对应配置页修复规则，再回到这里标记已恢复；原合同由经办人重新提交。"
          show-icon
          class="mt-18px"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="currentIssue?.status === 'OPEN'"
          type="primary"
          @click="goToRepair(currentIssue)"
        >
          定位修复
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import { formatNullableDate } from '@/utils/formatTime'
import * as IssueApi from '@/api/clm/governance/issue'

defineOptions({ name: 'ClmGovernanceIssues' })

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<IssueApi.GovernanceIssueVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<IssueApi.GovernanceIssuePageReqVO>({
  pageNo: 1,
  pageSize: 10,
  status: 'OPEN'
})
const detailVisible = ref(false)
const currentIssue = ref<IssueApi.GovernanceIssueVO>()

const issueTypeOptions = [
  { value: 'TEMPLATE_INVALID', label: '模板失效' },
  { value: 'NUMBERING_CONFLICT', label: '编码规则冲突' },
  { value: 'ROUTING_ZERO_MATCH', label: '业务单据流程配置零命中' },
  { value: 'ROUTING_MULTIPLE_MATCH', label: '业务单据流程配置多命中' },
  { value: 'APPROVER_MISSING', label: '审批人缺失' },
  { value: 'PROCESS_START_INCOMPLETE', label: '审批启动不完整' },
  { value: 'PROCESS_INSTANCE_MISSING', label: '流程实例缺失' },
  { value: 'RECONCILIATION_FAILED', label: '审批异常检查失败' }
]

const detailRows = computed(() => {
  if (!currentIssue.value?.detailJson) return []
  try {
    const detail = JSON.parse(currentIssue.value.detailJson) as Record<string, unknown>
    const labels: Record<string, string> = {
      bindingId: '审批业务单',
      contractId: '合同',
      processInstanceId: '流程实例',
      authoritativeStatus: '权威状态',
      projectionStatus: '合同投影状态',
      result: '检查结果',
      error: '失败类型'
    }
    return Object.entries(detail).map(([key, value]) => ({
      label: labels[key] || key,
      value: typeof value === 'object' ? JSON.stringify(value) : String(value ?? '-')
    }))
  } catch {
    return [{ label: '诊断信息', value: currentIssue.value.detailJson }]
  }
})

const issueTypeText = (type: string) => {
  return issueTypeOptions.find((item) => item.value === type)?.label || type
}

const issueTagType = (type: string) => {
  if (type.includes('NUMBERING') || type.includes('ROUTING')) return 'warning'
  if (type.includes('PROCESS') || type.includes('RECONCILIATION')) return 'danger'
  return 'info'
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await IssueApi.getGovernanceIssuePage(queryParams)
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

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.status = 'OPEN'
  handleQuery()
}

const showDetail = (row: IssueApi.GovernanceIssueVO) => {
  currentIssue.value = row
  detailVisible.value = true
}

const repairPath = (type: string) => {
  if (type.includes('TEMPLATE')) return '/clm/template'
  if (type.includes('NUMBERING')) return '/clm/base-settings/numbering'
  if (type.includes('ROUTING') || type.includes('APPROVER')) {
    return '/clm/approval-management/workflow-settings/routing'
  }
  return '/clm/exceptions/reconciliation'
}

const goToRepair = (row: IssueApi.GovernanceIssueVO) => {
  router.push(repairPath(row.issueType))
}

const openContract = (contractId: string | number) => {
  router.push(`/clm/contract/detail/${contractId}`)
}

const handleResolve = async (row: IssueApi.GovernanceIssueVO) => {
  await message.confirm('请确认对应规则或依赖已经修复。标记后原经办人可重新提交，是否继续？')
  await IssueApi.resolveGovernanceIssue(row.id)
  message.success('配置异常已标记恢复')
  await getList()
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

.detail-section {
  margin-top: 24px;
}

.detail-section h3 {
  margin: 0 0 12px;
  font-size: 16px;
}
</style>
