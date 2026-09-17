<template>
  <div class="clm-home">
    <el-card shadow="never" class="workbench-header-card">
      <div class="workbench-header">
        <div>
          <div class="workbench-eyebrow">TuriX 合同管理</div>
          <h1>你好，{{ nickname }}</h1>
          <p>{{ roleIntro }}</p>
        </div>
        <div class="workbench-header__meta">
          <span>今日工作台</span>
          <strong>{{ currentDate }}</strong>
        </div>
      </div>
    </el-card>

    <el-alert
      v-if="summaryError"
      type="warning"
      :closable="false"
      show-icon
      title="部分工作台统计暂时无法加载"
    >
      <template #default>
        <el-link type="primary" :underline="false" @click="reloadWorkbench">重新加载</el-link>
      </template>
    </el-alert>

    <div class="workbench-primary-grid">
      <el-card v-if="approvalMetrics.length" shadow="never" class="section-card approval-card">
        <template #header>
          <div class="section-heading">
            <div>
              <strong>审批中心</strong>
              <span>查看本人合同审批任务与处理记录</span>
            </div>
            <el-link
              type="primary"
              :underline="false"
              @click="goPath('/clm/approval-management/approval')"
            >
              进入审批中心
            </el-link>
          </div>
        </template>
        <div class="approval-grid" :class="`approval-grid--${approvalMetrics.length}`">
          <button
            v-for="metric in approvalMetrics"
            :key="metric.key"
            type="button"
            class="approval-metric"
            @click="openApprovalMetric(metric)"
          >
            <span class="approval-metric__icon" :style="{ color: metric.color }">
              <Icon :icon="metric.icon" :size="21" />
            </span>
            <span class="approval-metric__content">
              <strong>{{ metric.value }}</strong>
              <small>{{ metric.label }}</small>
            </span>
            <Icon icon="ep:arrow-right" :size="14" class="approval-metric__arrow" />
          </button>
        </div>
      </el-card>

      <el-card shadow="never" class="section-card quick-card">
        <template #header>
          <div class="section-heading">
            <div>
              <strong>快捷操作</strong>
              <span>只展示当前账号有权使用的入口</span>
            </div>
          </div>
        </template>
        <div v-if="quickActions.length" class="quick-grid">
          <button
            v-for="action in quickActions"
            :key="action.key"
            type="button"
            class="quick-action"
            :class="{ 'quick-action--primary': action.primary }"
            @click="goPath(action.path)"
          >
            <span class="quick-action__icon"><Icon :icon="action.icon" :size="19" /></span>
            <span>{{ action.label }}</span>
          </button>
        </div>
        <el-empty v-else description="当前账号暂无快捷操作" :image-size="54" />
      </el-card>
    </div>

    <el-card v-if="taskBuckets.length" shadow="never" class="section-card task-center-card">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>任务中心</strong>
            <span>一期仅覆盖起草、协同和合同审批</span>
          </div>
        </div>
      </template>
      <div class="task-bucket-grid">
        <button
          v-for="bucket in taskBuckets"
          :key="bucket.type"
          type="button"
          class="task-bucket"
          :class="{ 'task-bucket--active': activePanel?.type === bucket.type }"
          @click="activatePanel(bucket.type)"
        >
          <span class="task-bucket__icon" :style="{ color: bucket.color }">
            <Icon :icon="bucket.icon" :size="20" />
          </span>
          <span class="task-bucket__content">
            <small>{{ bucket.label }}</small>
            <strong>{{ bucket.value }}</strong>
          </span>
        </button>
      </div>
    </el-card>

    <el-card shadow="never" class="section-card task-list-card">
      <template #header>
        <div class="section-heading">
          <div>
            <strong>{{ activePanel?.title || '我的任务' }}</strong>
            <span>
              {{
                activePanel
                  ? '按更新时间展示最近 8 条，点击任务中心可切换队列'
                  : '当前视角暂无业务任务'
              }}
            </span>
          </div>
          <el-link
            v-if="activePanel"
            type="primary"
            :underline="false"
            @click="goPath(activePanel.path)"
          >
            查看全部
          </el-link>
        </div>
      </template>

      <el-alert
        v-if="activePanel?.error"
        type="warning"
        :closable="false"
        show-icon
        title="任务列表加载失败"
        class="mb-10px"
      >
        <template #default>
          <el-link type="primary" :underline="false" @click="loadPanel(activePanel)">重试</el-link>
        </template>
      </el-alert>

      <el-table
        v-if="activePanel && (activePanel.loading || activePanel.items.length)"
        v-loading="activePanel.loading"
        :data="activePanel.items"
        size="small"
        stripe
        show-overflow-tooltip
        class="workbench-table"
      >
        <el-table-column label="合同 / 任务" min-width="280">
          <template #default="scope">
            <button type="button" class="task-name" @click="openItem(activePanel.type, scope.row)">
              <strong>{{ scope.row.contractName || '未命名合同' }}</strong>
              <span>{{ scope.row.contractNo || taskSecondaryText(activePanel.type) }}</span>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag size="small" :type="statusTagType(scope.row.status)">
              {{ statusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前处理人" prop="handlerName" width="130">
          <template #default="scope">{{ scope.row.handlerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="等待时长" width="110">
          <template #default="scope">{{ waitingText(scope.row.waitingMinutes) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="scope">{{ formatUpdatedTime(scope.row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openItem(activePanel.type, scope.row)">
              {{ taskActionText(activePanel.type) }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        :description="activePanel?.emptyText || '当前视角暂无业务任务，请从快捷操作进入管理页面'"
        :image-size="68"
      >
        <el-button
          v-if="canCreateContract && activePanel?.type === 'DRAFT'"
          type="primary"
          @click="goPath('/clm/drafting/draft-center')"
        >
          合同起草
        </el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import * as WorkbenchApi from '@/api/clm/workbench'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'Home' })

interface WorkPanel {
  type: WorkbenchApi.WorkbenchItemType
  title: string
  emptyText: string
  path: string
  permission: string
  items: WorkbenchApi.WorkbenchItemVO[]
  loading: boolean
  error: boolean
}

interface ApprovalMetric {
  key: string
  label: string
  value: number
  icon: string
  color: string
  view: WorkbenchApi.WorkbenchItemType
  permission: string
}

interface QuickAction {
  key: string
  label: string
  icon: string
  path: string
  permission: string
  primary?: boolean
}

interface TaskBucket {
  type: WorkbenchApi.WorkbenchItemType
  label: string
  value: number
  icon: string
  color: string
}

const router = useRouter()
const userStore = useUserStore()

const nickname = computed(() => userStore.getUser.nickname)
const canCreateContract = computed(() => checkPermi(['clm:contract:create']))
const canViewApprovalHistory = computed(() => checkPermi(['clm:approval:history']))
const currentDate = formatDate(new Date(), 'YYYY年MM月DD日')

const roleIntro = computed(() => {
  if (
    !checkPermi(['clm:contract:query']) &&
    (checkPermi(['clm:user-scope:query']) || checkPermi(['clm:integration:query']))
  ) {
    return '管理组织、账号、产品角色和集成配置；系统管理视角默认不读取合同正文。'
  }
  if (checkPermi(['clm:governance:template:query'])) {
    return '查看合同全局台账，处理模板、规则、参与方与流程异常。'
  }
  if (!canCreateContract.value && checkPermi(['clm:collaboration:query'])) {
    return '从法务协同和审批任务进入，围绕精确合同修订完成审查。'
  }
  if (canCreateContract.value) {
    return '从合同起草、法务协同到合同审批完成，聚合今天需要处理的事项。'
  }
  return '查看并处理本人有权访问的合同审批任务。'
})

const summaryLoading = ref(true)
const summaryError = ref(false)
const summary = reactive<WorkbenchApi.WorkbenchSummaryVO>({
  draftCount: 0,
  collaborationTodoCount: 0,
  approvalTodoCount: 0,
  startedRunningCount: 0,
  governanceIssueCount: 0
})
const approvalHistoryCounts = reactive({ done: 0, copied: 0 })

const approvalMetrics = computed<ApprovalMetric[]>(() => {
  const metrics: ApprovalMetric[] = [
    {
      key: 'approval-todo',
      label: '待我审批',
      value: summary.approvalTodoCount,
      icon: 'ep:stamp',
      color: 'var(--el-color-danger)',
      view: 'APPROVAL',
      permission: 'clm:approval:query'
    },
    {
      key: 'approval-started',
      label: '我发起的',
      value: summary.startedRunningCount,
      icon: 'ep:promotion',
      color: 'var(--el-color-primary)',
      view: 'STARTED',
      permission: 'clm:approval:history'
    },
    {
      key: 'approval-done',
      label: '我已处理',
      value: approvalHistoryCounts.done,
      icon: 'ep:circle-check',
      color: 'var(--el-color-success)',
      view: 'APPROVAL_DONE',
      permission: 'clm:approval:history'
    },
    {
      key: 'approval-copied',
      label: '抄送给我',
      value: approvalHistoryCounts.copied,
      icon: 'ep:message',
      color: 'var(--el-color-warning)',
      view: 'COPIED',
      permission: 'clm:approval:history'
    }
  ]
  return metrics.filter((metric) => checkPermi([metric.permission]))
})

const quickActions = computed<QuickAction[]>(() => {
  const actions: QuickAction[] = [
    {
      key: 'create',
      label: '合同起草',
      icon: 'ep:plus',
      path: '/clm/drafting/draft-center',
      permission: 'clm:contract:create',
      primary: true
    },
    {
      key: 'ledger',
      label: '合同查询',
      icon: 'ep:tickets',
      path: '/clm/drafting/contract',
      permission: 'clm:contract:query'
    },
    {
      key: 'collaboration',
      label: '合同协同',
      icon: 'ep:chat-line-square',
      path: '/clm/drafting/collaboration',
      permission: 'clm:collaboration:query'
    },
    {
      key: 'approval',
      label: '审批中心',
      icon: 'ep:stamp',
      path: '/clm/approval-management/approval',
      permission: 'clm:approval:query'
    },
    {
      key: 'template',
      label: '模板管理',
      icon: 'ep:files',
      path: '/clm/template',
      permission: 'clm:governance:template:query'
    },
    {
      key: 'party',
      label: '相对方信息',
      icon: 'ep:office-building',
      path: '/clm/basic-data/directory',
      permission: 'clm:party:query'
    },
    {
      key: 'issues',
      label: '配置异常',
      icon: 'ep:warning',
      path: '/clm/exceptions/issues',
      permission: 'clm:governance-issue:query'
    },
    {
      key: 'user-scope',
      label: '产品角色',
      icon: 'ep:user',
      path: '/clm/system/user-scope',
      permission: 'clm:user-scope:query'
    },
    {
      key: 'sync',
      label: '组织同步',
      icon: 'ep:refresh',
      path: '/clm/system/sync',
      permission: 'clm:integration:query'
    },
    {
      key: 'integration',
      label: '钉钉集成',
      icon: 'ep:connection',
      path: '/clm/system/integration',
      permission: 'clm:system:dingtalk:query'
    },
    {
      key: 'users',
      label: '用户管理',
      icon: 'ep:user-filled',
      path: '/system/user',
      permission: 'system:user:query'
    }
  ]
  return actions.filter((action) => checkPermi([action.permission])).slice(0, 6)
})

const workPanels = reactive<WorkPanel[]>([
  {
    type: 'DRAFT',
    title: '待补充草稿',
    emptyText: '暂无待补充草稿',
    path: '/clm/drafting/contract?stageCode=DRAFT',
    permission: 'clm:contract:create',
    items: [],
    loading: true,
    error: false
  },
  {
    type: 'COLLABORATION',
    title: '待我协同',
    emptyText: '暂无待协同合同',
    path: '/clm/drafting/collaboration',
    permission: 'clm:collaboration:query',
    items: [],
    loading: true,
    error: false
  },
  {
    type: 'APPROVAL',
    title: '待我审批',
    emptyText: '暂无待审批合同',
    path: '/clm/approval-management/approval',
    permission: 'clm:approval:query',
    items: [],
    loading: true,
    error: false
  },
  {
    type: 'STARTED',
    title: '我发起进行中',
    emptyText: '暂无审批中的发起记录',
    path: '/clm/approval-management/approval?view=STARTED',
    permission: 'clm:approval:history',
    items: [],
    loading: true,
    error: false
  },
  {
    type: 'GOVERNANCE',
    title: '配置阻断',
    emptyText: '当前没有需要处理的配置阻断',
    path: '/clm/exceptions/issues',
    permission: 'clm:governance-issue:query',
    items: [],
    loading: true,
    error: false
  }
])

const visibleWorkPanels = computed(() =>
  workPanels.filter((panel) => checkPermi([panel.permission]))
)
const activePanelType = ref<WorkbenchApi.WorkbenchItemType>('APPROVAL')
const activePanel = computed(
  () =>
    visibleWorkPanels.value.find((panel) => panel.type === activePanelType.value) ||
    visibleWorkPanels.value[0]
)

watch(
  visibleWorkPanels,
  (panels) => {
    if (panels.length && !panels.some((panel) => panel.type === activePanelType.value)) {
      activePanelType.value = panels[0].type
    }
  },
  { immediate: true }
)

const taskBuckets = computed<TaskBucket[]>(() =>
  visibleWorkPanels.value.map((panel) => {
    const config: Record<string, Omit<TaskBucket, 'type' | 'value'>> = {
      DRAFT: { label: '待补充草稿', icon: 'ep:edit-pen', color: 'var(--el-color-primary)' },
      COLLABORATION: {
        label: '待我协同',
        icon: 'ep:chat-dot-round',
        color: 'var(--el-color-warning)'
      },
      APPROVAL: { label: '待我审批', icon: 'ep:stamp', color: 'var(--el-color-danger)' },
      STARTED: {
        label: '我发起进行中',
        icon: 'ep:clock',
        color: 'var(--el-color-success)'
      },
      GOVERNANCE: {
        label: '配置阻断',
        icon: 'ep:warning',
        color: 'var(--el-color-info)'
      }
    }
    const counts: Partial<Record<WorkbenchApi.WorkbenchItemType, number>> = {
      DRAFT: summary.draftCount,
      COLLABORATION: summary.collaborationTodoCount,
      APPROVAL: summary.approvalTodoCount,
      STARTED: summary.startedRunningCount,
      GOVERNANCE: summary.governanceIssueCount
    }
    return {
      type: panel.type,
      value: counts[panel.type] || 0,
      ...config[panel.type]
    }
  })
)

const loadSummary = async () => {
  summaryLoading.value = true
  summaryError.value = false
  try {
    Object.assign(summary, await WorkbenchApi.getWorkbenchSummary())
  } catch {
    summaryError.value = true
  } finally {
    summaryLoading.value = false
  }
}

const loadApprovalHistoryCounts = async () => {
  if (!canViewApprovalHistory.value) return
  const results = await Promise.allSettled([
    WorkbenchApi.getWorkbenchItems({ type: 'APPROVAL_DONE', pageNo: 1, pageSize: 1 }),
    WorkbenchApi.getWorkbenchItems({ type: 'COPIED', pageNo: 1, pageSize: 1 })
  ])
  if (results[0].status === 'fulfilled') {
    approvalHistoryCounts.done = results[0].value?.total || 0
  }
  if (results[1].status === 'fulfilled') {
    approvalHistoryCounts.copied = results[1].value?.total || 0
  }
}

const loadPanel = async (panel?: WorkPanel) => {
  if (!panel) return
  panel.loading = true
  panel.error = false
  try {
    const data = await WorkbenchApi.getWorkbenchItems({ type: panel.type, pageNo: 1, pageSize: 8 })
    panel.items = data?.list || []
  } catch {
    panel.items = []
    panel.error = true
  } finally {
    panel.loading = false
  }
}

const reloadWorkbench = () => {
  void loadSummary()
  void loadApprovalHistoryCounts()
  visibleWorkPanels.value.forEach((panel) => void loadPanel(panel))
}

const activatePanel = (type: WorkbenchApi.WorkbenchItemType) => {
  activePanelType.value = type
}

const goPath = (path: string) => router.push(path)
const openApprovalMetric = (metric: ApprovalMetric) =>
  router.push({ path: '/clm/approval-management/approval', query: { view: metric.view } })

const openItem = (type: WorkbenchApi.WorkbenchItemType, item: WorkbenchApi.WorkbenchItemVO) => {
  if (type === 'GOVERNANCE') {
    void goPath('/clm/exceptions/issues')
  } else if (type === 'COLLABORATION') {
    void router.push({ name: 'ClmCollaborationDetail', params: { id: item.id } })
  } else if (type === 'APPROVAL' && item.taskId) {
    void router.push({ name: 'ClmApprovalTask', params: { taskId: item.taskId } })
  } else if (item.contractId) {
    void router.push({ name: 'ClmContractDetail', params: { id: item.contractId } })
  }
}

const statusText = (status?: string) => {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    TODO: '待处理',
    OPEN: '待处理',
    RUNNING: '进行中',
    RETURNED: '已退回',
    REJECTED: '已拒绝',
    CANCELED: '已取消',
    COMPLETED: '已完成',
    APPROVED: '已通过'
  }
  return (status && labels[status]) || status || '待处理'
}

const statusTagType = (status?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' => {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    DRAFT: 'info',
    TODO: 'warning',
    OPEN: 'danger',
    RUNNING: 'primary',
    RETURNED: 'warning',
    REJECTED: 'danger',
    CANCELED: 'info',
    COMPLETED: 'success',
    APPROVED: 'success'
  }
  return (status && types[status]) || 'info'
}

const waitingText = (minutes?: number) => {
  if (minutes === undefined || minutes === null) return '-'
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟`
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时`
  return `${Math.floor(minutes / 1440)} 天`
}

const formatUpdatedTime = (value?: string) =>
  value ? formatDate(new Date(value), 'MM-DD HH:mm') : '-'

const taskSecondaryText = (type: WorkbenchApi.WorkbenchItemType) =>
  type === 'GOVERNANCE' ? '治理异常' : '草稿未编号'

const taskActionText = (type: WorkbenchApi.WorkbenchItemType) => {
  if (type === 'APPROVAL') return '处理'
  if (type === 'COLLABORATION') return '协同'
  if (type === 'GOVERNANCE') return '查看'
  return '打开'
}

onMounted(reloadWorkbench)
</script>

<style scoped>
.clm-home {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workbench-header-card :deep(.el-card__body) {
  padding: 15px 18px;
}

.workbench-header,
.section-heading,
.approval-metric,
.quick-action,
.task-bucket {
  display: flex;
  align-items: center;
}

.workbench-header,
.section-heading {
  justify-content: space-between;
  gap: 18px;
}

.workbench-eyebrow {
  margin-bottom: 3px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.workbench-header h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 21px;
  line-height: 1.35;
}

.workbench-header p,
.section-heading span {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.workbench-header__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workbench-header__meta strong {
  margin-top: 4px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.workbench-primary-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  gap: 12px;
}

.section-card {
  margin: 0;
}

.section-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.section-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.section-heading strong {
  display: block;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.approval-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.approval-grid--1 {
  grid-template-columns: minmax(0, 1fr);
}

.approval-metric {
  min-width: 0;
  padding: 17px 15px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  transition: background-color 0.16s ease;
}

.approval-metric:last-child {
  border-right: 0;
}

.approval-metric:hover {
  background: var(--el-fill-color-light);
}

.approval-metric__icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 35px;
  height: 35px;
  margin-right: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.approval-metric__content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.approval-metric__content strong {
  color: var(--el-text-color-primary);
  font-size: 23px;
  line-height: 1.1;
}

.approval-metric__content small {
  margin-top: 5px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-metric__arrow {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.quick-action {
  min-height: 43px;
  padding: 8px 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 7px;
  transition:
    border-color 0.16s ease,
    color 0.16s ease,
    background-color 0.16s ease;
}

.quick-action:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}

.quick-action--primary {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-8);
}

.quick-action__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-right: 7px;
  background: var(--el-bg-color);
  border-radius: 6px;
}

.task-bucket-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
}

.task-bucket {
  min-width: 0;
  padding: 11px 13px;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-fill-color-extra-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    border-color 0.16s ease,
    background-color 0.16s ease;
}

.task-bucket:hover,
.task-bucket--active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-6);
}

.task-bucket__icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-right: 10px;
  background: var(--el-bg-color);
  border-radius: 8px;
}

.task-bucket__content {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.task-bucket__content small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-bucket__content strong {
  color: var(--el-text-color-primary);
  font-size: 20px;
}

.task-list-card :deep(.el-card__body) {
  padding-top: 8px;
}

.task-name {
  display: flex;
  width: 100%;
  min-width: 0;
  padding: 3px 0;
  flex-direction: column;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.task-name strong {
  overflow: hidden;
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-name span {
  margin-top: 2px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbench-table :deep(.el-table__cell) {
  padding: 5px 0;
}

@media (width <= 1100px) {
  .workbench-primary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (width <= 760px) {
  .workbench-header {
    align-items: flex-start;
  }

  .workbench-header__meta {
    display: none;
  }

  .approval-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .approval-metric {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .approval-metric:nth-child(2n) {
    border-right: 0;
  }

  .approval-metric:nth-last-child(-n + 2) {
    border-bottom: 0;
  }
}

@media (width <= 520px) {
  .quick-grid,
  .task-bucket-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .approval-metric {
    padding: 13px 10px;
  }
}
</style>
