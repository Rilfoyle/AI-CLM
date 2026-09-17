<template>
  <ContentWrap>
    <div class="flex flex-wrap items-start justify-between gap-10px">
      <div>
        <div class="text-20px font-bold">合同审批</div>
        <div class="mt-5px text-13px text-[var(--el-text-color-secondary)]">
          每个审批决定都绑定实际审阅的合同修订；合同审批动作仅通过 CLM 审批接口执行。
        </div>
      </div>
      <div class="flex gap-8px">
        <el-button v-if="mode !== 'list'" @click="backToList"
          ><Icon icon="ep:arrow-left" class="mr-5px" />返回审批列表</el-button
        >
        <el-button
          v-hasPermi="['clm:contract:query']"
          @click="router.push('/clm/drafting/contract')"
          ><Icon icon="ep:tickets" class="mr-5px" />合同查询</el-button
        >
      </div>
    </div>
  </ContentWrap>

  <template v-if="mode === 'list'">
    <ContentWrap>
      <el-tabs v-model="listView" @tab-change="handleListViewChange">
        <el-tab-pane label="待我审批" name="APPROVAL" />
        <el-tab-pane v-if="canViewApprovalHistory" label="我已处理" name="APPROVAL_DONE" />
        <el-tab-pane v-if="canViewApprovalHistory" label="我发起的" name="STARTED" />
        <el-tab-pane v-if="canViewApprovalHistory" label="抄送我的" name="COPIED" />
      </el-tabs>
      <el-alert
        v-if="listError"
        class="mb-10px"
        type="warning"
        :closable="false"
        show-icon
        title="审批列表加载失败"
      >
        <template #default
          ><el-link type="primary" :underline="false" @click="getList">重试</el-link></template
        >
      </el-alert>
      <el-table v-loading="listLoading" :data="items" stripe show-overflow-tooltip>
        <el-table-column label="合同编号" prop="contractNo" width="170"
          ><template #default="scope">{{
            scope.row.contractNo || '草稿未编号'
          }}</template></el-table-column
        >
        <el-table-column label="合同名称" prop="contractName" min-width="230" />
        <el-table-column label="状态 / 当前任务" min-width="150"
          ><template #default="scope">{{
            approvalItemStatusText(scope.row.status)
          }}</template></el-table-column
        >
        <el-table-column label="当前处理人" prop="handlerName" width="120" />
        <el-table-column label="等待时长" width="110"
          ><template #default="scope">{{
            waitingText(scope.row.waitingMinutes)
          }}</template></el-table-column
        >
        <el-table-column label="更新时间" width="170"
          ><template #default="scope">{{
            formatTime(scope.row.updatedTime)
          }}</template></el-table-column
        >
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openItem(scope.row)">
              {{ scope.row.taskId ? '处理' : '查看历史' }}
            </el-button>
            <el-button
              v-if="listView === 'STARTED' && scope.row.status === 'RUNNING'"
              link
              type="warning"
              @click="withdrawStartedCase(scope.row)"
              v-hasPermi="['clm:approval:withdraw-case']"
            >
              撤回整单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!listLoading && !listError && items.length === 0"
        :description="emptyListText"
        :image-size="80"
      />
      <Pagination
        v-if="listTotal > 0"
        v-model:page="listQuery.pageNo"
        v-model:limit="listQuery.pageSize"
        :total="listTotal"
        @pagination="getList"
      />
    </ContentWrap>
  </template>

  <template v-else-if="mode === 'task'">
    <ContentWrap v-loading="taskLoading">
      <el-alert
        v-if="taskError"
        type="warning"
        :closable="false"
        show-icon
        title="审批任务加载失败或当前用户无权查看"
      >
        <template #default
          ><el-link type="primary" :underline="false" @click="loadTask">重试</el-link></template
        >
      </el-alert>
      <template v-else-if="task.taskId">
        <div class="flex flex-wrap items-start justify-between gap-10px">
          <div>
            <div class="flex flex-wrap items-center gap-8px">
              <span class="text-18px font-bold">{{ task.contract?.title || '合同审批任务' }}</span>
              <el-tag type="primary">{{ task.taskName || '待审批' }}</el-tag>
              <el-tag v-if="actionCompleted" type="success">本次操作已完成</el-tag>
            </div>
            <div class="mt-6px text-12px text-[var(--el-text-color-secondary)]">
              合同编号：{{ task.contract?.contractNo || '-' }}
              <span class="ml-12px">提交修订：{{ task.submittedRevisionId }}</span>
              <span class="ml-12px">当前修订：{{ task.currentRevisionId }}</span>
            </div>
          </div>
          <div class="flex flex-wrap gap-8px">
            <el-button
              v-if="(task.nodeEditPolicy?.canEdit || task.editPolicy?.canEdit) && !actionCompleted"
              v-hasPermi="['clm:approval:edit']"
              @click="openApprovalEdit"
              ><Icon icon="ep:edit" class="mr-5px" />审批中编辑</el-button
            >
            <el-button
              v-if="canEditApprovalDocument && !actionCompleted"
              v-hasPermi="['clm:approval:edit']"
              @click="openApprovalDocumentEdit"
            >
              <Icon icon="ep:upload" class="mr-5px" />上传修订正文
            </el-button>
            <el-dropdown
              v-if="hasCollaborationAction && !actionCompleted"
              trigger="click"
              @command="openCollaboration"
            >
              <el-button v-hasPermi="['clm:approval:collaborate']">
                协作<Icon icon="ep:arrow-down" class="ml-5px" />
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canAction('TRANSFER')" command="TRANSFER">
                    转交任务
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canAction('COPY')" command="COPY">
                    抄送知会
                  </el-dropdown-item>
                  <el-dropdown-item v-if="canAction('ADD_SIGN')" command="ADD_SIGN">
                    增加审批人
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button
              v-if="canAction('WITHDRAW_TASK') && !actionCompleted"
              v-hasPermi="['clm:approval:collaborate']"
              @click="withdrawCompletedTask"
            >
              撤回已办
            </el-button>
            <el-button
              v-if="canAction('RETURN') && !actionCompleted"
              v-hasPermi="['clm:approval:return']"
              type="warning"
              @click="openAction('RETURN')"
              >退回</el-button
            >
            <el-button
              v-if="canAction('REJECT') && !actionCompleted"
              v-hasPermi="['clm:approval:reject']"
              type="danger"
              @click="openAction('REJECT')"
              >拒绝</el-button
            >
            <el-button
              v-if="canAction('APPROVE') && !actionCompleted"
              v-hasPermi="['clm:approval:approve']"
              type="success"
              @click="openAction('APPROVE')"
              >同意</el-button
            >
          </div>
        </div>
        <el-alert
          v-if="task.submittedRevisionId !== task.currentRevisionId"
          class="mt-12px"
          type="warning"
          :closable="false"
          show-icon
          title="当前合同已产生新修订。作出决定前请确认节点编辑政策和本任务实际决定修订。"
        />
      </template>
    </ContentWrap>

    <el-row v-if="task.taskId && !taskError" :gutter="12">
      <el-col :xs="24" :sm="24" :md="24" :lg="15" :xl="15">
        <ContentWrap>
          <el-tabs v-model="taskTab">
            <el-tab-pane label="合同正文" name="document">
              <el-alert
                class="mb-10px"
                type="info"
                :closable="false"
                :title="`本任务提交修订：${task.submittedRevisionId}；审批决定将记录实际 revisionId。`"
              />
              <DocumentPreview
                v-if="approvalContract.id && canDownloadApprovalContext"
                ref="approvalPreviewRef"
                :contract="approvalContract"
              />
              <el-empty v-else description="当前角色无审批合同正文查看权限" :image-size="70" />
            </el-tab-pane>
            <el-tab-pane label="修订差异" name="revisions">
              <RevisionPanel
                v-if="task.contractId && canViewApprovalRevisions"
                :contract-id="task.contractId"
                :current-revision-id="task.currentRevisionId"
              />
              <el-empty v-else description="当前角色无合同修订查看权限" :image-size="70" />
            </el-tab-pane>
          </el-tabs>
        </ContentWrap>
      </el-col>
      <el-col :xs="24" :sm="24" :md="24" :lg="9" :xl="9">
        <ContentWrap>
          <el-tabs v-model="contextTab">
            <el-tab-pane label="审批上下文" name="context">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="合同名称">{{
                  task.contract?.title || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="合同分类">{{
                  task.contract?.typeName || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="合同金额">{{
                  formatAmount(task.contract?.currency, task.contract?.amount)
                }}</el-descriptions-item>
                <el-descriptions-item label="发起人">{{
                  task.contract?.ownerUserName || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="提交修订">{{
                  task.submittedRevisionId
                }}</el-descriptions-item>
                <el-descriptions-item label="决定修订">{{
                  task.currentRevisionId
                }}</el-descriptions-item>
              </el-descriptions>
              <el-divider content-position="left">扩展字段</el-divider>
              <ApprovalCustomFields
                :type-version-id="task.contract?.typeVersionId"
                :model-value="task.contract?.customData || {}"
              />
              <el-divider content-position="left">参与方快照</el-divider>
              <el-table :data="task.parties || []" size="small">
                <el-table-column label="角色" prop="roleName" width="100"
                  ><template #default="scope">{{
                    scope.row.roleName || scope.row.roleCode || '-'
                  }}</template></el-table-column
                >
                <el-table-column label="参与方" min-width="150"
                  ><template #default="scope">{{
                    scope.row.name || scope.row.partyName || '-'
                  }}</template></el-table-column
                >
              </el-table>
              <el-divider content-position="left">重大承诺快照</el-divider>
              <el-table :data="task.commitments || []" size="small">
                <el-table-column label="类别" prop="category" width="90" />
                <el-table-column
                  label="内容"
                  prop="content"
                  min-width="160"
                  show-overflow-tooltip
                />
                <el-table-column label="责任人" prop="ownerUserName" width="90" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="审批意见" name="opinions">
              <ApprovalOpinionList :opinions="task.opinions || []" />
            </el-tab-pane>
          </el-tabs>
        </ContentWrap>
      </el-col>
    </el-row>
  </template>

  <template v-else>
    <ContentWrap v-loading="historyLoading">
      <el-alert
        v-if="historyError"
        type="warning"
        :closable="false"
        show-icon
        title="审批历史加载失败或当前用户无权查看"
      >
        <template #default
          ><el-link type="primary" :underline="false" @click="loadHistory">重试</el-link></template
        >
      </el-alert>
      <template v-else>
        <div class="mb-14px flex items-center justify-between gap-8px">
          <div
            ><span class="text-18px font-bold">{{ historyContract.title || '合同审批历史' }}</span
            ><span class="ml-10px text-13px text-[var(--el-text-color-secondary)]">{{
              historyContract.contractNo || ''
            }}</span></div
          >
          <el-button
            v-if="historyContract.id"
            v-hasPermi="['clm:contract:query']"
            @click="openHistoryWorkspace"
            >打开合同工作区</el-button
          >
        </div>
        <el-timeline v-if="history.cases?.length">
          <el-timeline-item
            v-for="item in history.cases"
            :key="item.id"
            :timestamp="formatTime(item.startTime)"
            placement="top"
          >
            <el-card shadow="never">
              <div class="flex flex-wrap items-center justify-between gap-8px">
                <div class="flex items-center gap-8px">
                  <span class="font-500">提交修订 {{ item.submittedRevisionId }}</span>
                  <el-tag :type="historyStatusType(item.status)">{{
                    historyStatusText(item.status, item.cancelReason)
                  }}</el-tag>
                  <span
                    v-if="item.approvedRevisionId"
                    class="text-12px text-[var(--el-text-color-secondary)]"
                    >获批修订 {{ item.approvedRevisionId }}</span
                  >
                </div>
                <div class="flex items-center gap-8px">
                  <el-button
                    v-if="withdrawableOpinion(item)"
                    v-hasPermi="['clm:approval:collaborate']"
                    link
                    type="warning"
                    @click="withdrawHistoryTask(withdrawableOpinion(item)!)"
                  >
                    撤回我的最近已办
                  </el-button>
                  <span class="text-12px text-[var(--el-text-color-secondary)]">{{
                    formatTime(item.endTime)
                  }}</span>
                </div>
              </div>
              <ApprovalOpinionList class="mt-10px" :opinions="item.opinions || []" />
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无合同审批记录" :image-size="80" />
      </template>
    </ContentWrap>
  </template>

  <Dialog v-model="actionVisible" :title="actionTitle" width="520px">
    <el-alert
      class="mb-12px"
      type="info"
      :closable="false"
      :title="`本次决定将绑定修订 ${task.currentRevisionId}`"
    />
    <el-form label-width="80px">
      <el-form-item v-if="actionType === 'RETURN'" label="退回节点">
        <el-select v-model="targetActivityId" class="w-full" placeholder="请选择退回节点">
          <el-option
            v-for="target in task.returnTargets || []"
            :key="target.activityId"
            :label="target.name"
            :value="target.activityId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="审批意见">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          :placeholder="actionType === 'APPROVE' ? '可选，填写同意意见' : '请填写处理原因'"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="actionVisible = false">取消</el-button>
      <el-button
        :type="
          actionType === 'APPROVE' ? 'success' : actionType === 'REJECT' ? 'danger' : 'warning'
        "
        :disabled="!actionValid"
        :loading="actionLoading"
        @click="submitAction"
        >确认{{ actionTitle }}</el-button
      >
    </template>
  </Dialog>

  <ApprovalEditDialog ref="approvalEditDialogRef" @completed="handleApprovalEditCompleted" />
  <ApprovalDocumentEditDialog
    ref="approvalDocumentEditDialogRef"
    @completed="handleApprovalEditCompleted"
  />
  <ApprovalCollaborateDialog ref="collaborateDialogRef" @completed="handleCollaborationCompleted" />
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as ApprovalApi from '@/api/clm/approval'
import * as ContractApi from '@/api/clm/contract'
import * as WorkbenchApi from '@/api/clm/workbench'
import { useUserStore } from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import DocumentPreview from '@/views/clm/contract/detail/components/DocumentPreview.vue'
import RevisionPanel from '@/views/clm/contract/detail/components/RevisionPanel.vue'
import ApprovalCustomFields from './components/ApprovalCustomFields.vue'
import ApprovalOpinionList from './components/ApprovalOpinionList.vue'
import ApprovalEditDialog from './components/ApprovalEditDialog.vue'
import ApprovalDocumentEditDialog from './components/ApprovalDocumentEditDialog.vue'
import ApprovalCollaborateDialog from './components/ApprovalCollaborateDialog.vue'

defineOptions({ name: 'ClmApproval' })
const route = useRoute()
const router = useRouter()
const message = useMessage()
const userStore = useUserStore()
const taskId = computed(() => String(route.params.taskId || route.query.taskId || ''))
const historyContractId = computed(() =>
  String(route.params.contractId || route.query.contractId || '')
)
const mode = computed<'list' | 'task' | 'history'>(() =>
  taskId.value ? 'task' : historyContractId.value ? 'history' : 'list'
)

type ApprovalListView = 'APPROVAL' | 'APPROVAL_DONE' | 'STARTED' | 'COPIED'
const canViewApprovalHistory = computed(() => checkPermi(['clm:approval:history']))
const requestedListView = (route.query.view as ApprovalListView) || 'APPROVAL'
const listView = ref<ApprovalListView>(
  requestedListView === 'APPROVAL' || canViewApprovalHistory.value ? requestedListView : 'APPROVAL'
)
const listQuery = reactive({ pageNo: 1, pageSize: 10 })
const items = ref<WorkbenchApi.WorkbenchItemVO[]>([])
const listTotal = ref(0)
const listLoading = ref(false)
const listError = ref(false)
const emptyListText = computed(
  () =>
    ({
      APPROVAL: '暂无待审批合同',
      APPROVAL_DONE: '暂无本人已处理的审批决定',
      STARTED: '暂无本人发起的审批记录',
      COPIED: '暂无抄送给我的合同审批'
    })[listView.value]
)
const approvalItemStatusText = (status?: string) =>
  ({
    TODO: '待审批',
    APPROVE: '已同意',
    REJECT: '已拒绝',
    RETURN: '已退回',
    RUNNING: '审批中',
    APPROVED: '审批完成',
    REJECTED: '已拒绝',
    CANCELED: '已取消'
  })[status || ''] ||
  status ||
  '-'

const getList = async () => {
  listLoading.value = true
  listError.value = false
  try {
    const data = await WorkbenchApi.getWorkbenchItems({ ...listQuery, type: listView.value })
    items.value = data?.list || []
    listTotal.value = data?.total || 0
  } catch {
    items.value = []
    listTotal.value = 0
    listError.value = true
  } finally {
    listLoading.value = false
  }
}
const handleListViewChange = async () => {
  listQuery.pageNo = 1
  await router.replace({ name: 'ClmApproval', query: { view: listView.value } })
  await getList()
}
const openItem = (item: WorkbenchApi.WorkbenchItemVO) =>
  item.taskId
    ? router.push({ name: 'ClmApprovalTask', params: { taskId: item.taskId } })
    : router.push({ name: 'ClmApprovalHistory', params: { contractId: item.contractId } })

const task = ref<ApprovalApi.ApprovalTaskDetailVO>({} as ApprovalApi.ApprovalTaskDetailVO)
const taskLoading = ref(false)
const taskError = ref(false)
const taskTab = ref('document')
const contextTab = ref('context')
const actionCompleted = ref(false)
const approvalPreviewRef = ref<InstanceType<typeof DocumentPreview>>()
const approvalEditDialogRef = ref<InstanceType<typeof ApprovalEditDialog>>()
const approvalDocumentEditDialogRef = ref<InstanceType<typeof ApprovalDocumentEditDialog>>()
const collaborateDialogRef = ref<InstanceType<typeof ApprovalCollaborateDialog>>()
const canDownloadApprovalContext = computed(
  () => !!task.value.contract?.permissions?.canDownload && checkPermi(['clm:contract:download'])
)
const canViewApprovalRevisions = computed(
  () => canDownloadApprovalContext.value && checkPermi(['clm:revision:query'])
)
const approvalContract = computed<ContractApi.ContractVO>(() => ({
  ...(task.value.contract || ({} as ContractApi.ContractVO)),
  id: task.value.contract?.id || (task.value.contractId as unknown as number),
  permissions: {
    canView: true,
    canDownload: canDownloadApprovalContext.value,
    canEdit: false,
    canManage: false,
    canSubmit: false,
    canDelete: false,
    canCancelApproval: false
  }
}))

const loadTask = async () => {
  if (!taskId.value) return
  taskLoading.value = true
  taskError.value = false
  try {
    task.value = await ApprovalApi.getApprovalTask(taskId.value)
    actionCompleted.value = false
    await nextTick()
    const submitted = task.value.revisions?.find(
      (item) => item.id === task.value.submittedRevisionId
    )
    if (submitted?.documentVersionId && canDownloadApprovalContext.value)
      await approvalPreviewRef.value?.loadVersion(Number(submitted.documentVersionId))
  } catch {
    task.value = {} as ApprovalApi.ApprovalTaskDetailVO
    taskError.value = true
  } finally {
    taskLoading.value = false
  }
}
const canAction = (action: string) => task.value.availableActions?.includes(action) || false
const nodeEditPolicy = computed(() => task.value.nodeEditPolicy || task.value.editPolicy)
const canEditApprovalDocument = computed(
  () =>
    !!nodeEditPolicy.value?.canEdit &&
    (nodeEditPolicy.value.editableFields?.includes('*') ||
      nodeEditPolicy.value.editableFields?.includes('document'))
)
const hasCollaborationAction = computed(() =>
  ['TRANSFER', 'COPY', 'ADD_SIGN'].some((action) => canAction(action))
)
const openApprovalEdit = () => approvalEditDialogRef.value?.open(task.value)
const openApprovalDocumentEdit = () => approvalDocumentEditDialogRef.value?.open(task.value)
const openCollaboration = (action: 'TRANSFER' | 'COPY' | 'ADD_SIGN') =>
  collaborateDialogRef.value?.open(task.value.taskId, action)
const handleApprovalEditCompleted = async (result: ApprovalApi.ApprovalEditRespVO) => {
  if (result.majorChange) {
    actionCompleted.value = true
    return
  }
  await loadTask()
}
const handleCollaborationCompleted = async (action: 'TRANSFER' | 'COPY' | 'ADD_SIGN') => {
  if (action === 'TRANSFER') {
    actionCompleted.value = true
    return
  }
  await loadTask()
}

const withdrawCompletedTask = async () => {
  await message.confirm('撤回后流程将返回到本任务重新处理，确认撤回这条已办任务？')
  await ApprovalApi.withdrawApprovalTask(task.value.taskId)
  message.success('已办任务已撤回')
  await loadTask()
}

const withdrawStartedCase = async (item: WorkbenchApi.WorkbenchItemVO) => {
  const result = await message.prompt('请输入整单撤回原因', '撤回审批业务单')
  const withdrawReason = result.value.trim()
  if (!withdrawReason) {
    message.warning('撤回原因不能为空')
    return
  }
  await ApprovalApi.withdrawApprovalCase({ approvalCaseId: item.id, reason: withdrawReason })
  message.success('审批业务单已撤回')
  await getList()
}

const actionVisible = ref(false)
const actionLoading = ref(false)
const actionType = ref<'APPROVE' | 'REJECT' | 'RETURN'>('APPROVE')
const reason = ref('')
const targetActivityId = ref('')
const actionTitle = computed(
  () => ({ APPROVE: '同意', REJECT: '拒绝', RETURN: '退回' })[actionType.value]
)
const actionValid = computed(
  () =>
    actionType.value === 'APPROVE' ||
    (!!reason.value.trim() && (actionType.value !== 'RETURN' || !!targetActivityId.value))
)
const openAction = (type: 'APPROVE' | 'REJECT' | 'RETURN') => {
  actionType.value = type
  reason.value = ''
  targetActivityId.value = ''
  actionVisible.value = true
}
const requestId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`
const submitAction = async () => {
  if (!actionValid.value) return
  actionLoading.value = true
  try {
    const data: ApprovalApi.ApprovalActionReqVO = {
      taskId: task.value.taskId,
      revisionId: task.value.currentRevisionId,
      reason: reason.value.trim() || undefined,
      requestId: requestId(),
      targetActivityId: targetActivityId.value || undefined
    }
    if (actionType.value === 'APPROVE') await ApprovalApi.approveApprovalTask(data)
    else if (actionType.value === 'REJECT') await ApprovalApi.rejectApprovalTask(data)
    else await ApprovalApi.returnApprovalTask(data)
    message.success(`审批已${actionTitle.value}`)
    actionVisible.value = false
    actionCompleted.value = true
  } finally {
    actionLoading.value = false
  }
}

const history = ref<ApprovalApi.ApprovalHistoryVO>({ contractId: '', cases: [] })
const historyContract = ref<ContractApi.ContractVO>({} as ContractApi.ContractVO)
const historyLoading = ref(false)
const historyError = ref(false)
const loadHistory = async () => {
  if (!historyContractId.value) return
  historyLoading.value = true
  historyError.value = false
  try {
    const historyData = await ApprovalApi.getApprovalHistory(historyContractId.value)
    history.value = historyData || { contractId: historyContractId.value, cases: [] }
    historyContract.value = {} as ContractApi.ContractVO
    if (checkPermi(['clm:contract:query'])) {
      try {
        historyContract.value = await ContractApi.getContract(historyContractId.value)
      } catch {
        historyContract.value = {} as ContractApi.ContractVO
      }
    }
  } catch {
    history.value = { contractId: historyContractId.value, cases: [] }
    historyContract.value = {} as ContractApi.ContractVO
    historyError.value = true
  } finally {
    historyLoading.value = false
  }
}

const backToList = () => router.push({ name: 'ClmApproval' })
const openHistoryWorkspace = () =>
  router.push({ name: 'ClmContractDetail', params: { id: historyContractId.value } })
const withdrawableOpinion = (item: ApprovalApi.ApprovalHistoryCaseVO) => {
  if (item.status !== 'RUNNING' || !item.opinions?.length) return undefined
  const ordered = [...item.opinions].sort((left, right) =>
    String(left.createTime || '').localeCompare(String(right.createTime || ''))
  )
  const latest = ordered.at(-1)
  return latest?.taskId &&
    latest.action === 'APPROVE' &&
    String(latest.userName) === String(userStore.getUser.id)
    ? latest
    : undefined
}
const withdrawHistoryTask = async (opinion: ApprovalApi.ApprovalOpinionVO) => {
  if (!opinion.taskId) return
  await message.confirm('确认撤回本人最近完成的审批任务？流程会返回该节点重新处理。')
  await ApprovalApi.withdrawApprovalTask(opinion.taskId)
  message.success('已办任务已撤回')
  await loadHistory()
}
const historyStatusText = (status?: string, reason?: string) =>
  reason === 'REVISION_SUPERSEDED'
    ? '因新修订取消'
    : (
        {
          RUNNING: '审批中',
          APPROVED: '审批完成',
          REJECTED: '已拒绝',
          CANCELED: '已取消'
        } as Record<string, string>
      )[status || ''] ||
      status ||
      '-'
const historyStatusType = (
  status?: string
): 'success' | 'warning' | 'danger' | 'info' | 'primary' =>
  (
    ({ RUNNING: 'primary', APPROVED: 'success', REJECTED: 'danger', CANCELED: 'info' }) as Record<
      string,
      'success' | 'warning' | 'danger' | 'info' | 'primary'
    >
  )[status || ''] || 'info'
const waitingText = (minutes?: number) =>
  minutes === undefined
    ? '-'
    : minutes < 60
      ? `${minutes} 分钟`
      : minutes < 1440
        ? `${Math.floor(minutes / 60)} 小时`
        : `${Math.floor(minutes / 1440)} 天`
const formatTime = (value?: string) =>
  value ? formatDate(new Date(value), 'YYYY-MM-DD HH:mm') : '-'
const formatAmount = (currency?: string, amount?: number) =>
  amount === undefined || amount === null
    ? '-'
    : `${currency || 'CNY'} ${Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

watch(
  [mode, taskId, historyContractId],
  () => {
    if (mode.value === 'task') loadTask()
    else if (mode.value === 'history') loadHistory()
    else getList()
  },
  { immediate: true }
)
</script>
