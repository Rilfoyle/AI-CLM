<template>
  <ContentWrap>
    <div class="flex flex-wrap items-start justify-between gap-10px">
      <div>
        <div class="text-20px font-bold">合同协同</div>
        <div class="mt-5px text-13px text-[var(--el-text-color-secondary)]">
          协同意见绑定精确合同修订；出现新修订时，旧完成结论会明确标记为过期。
        </div>
      </div>
      <el-button @click="router.push('/clm/drafting/contract')"
        ><Icon icon="ep:tickets" class="mr-5px" />合同查询</el-button
      >
    </div>
  </ContentWrap>

  <el-row :gutter="12">
    <el-col :xs="24" :sm="24" :md="9" :lg="8" :xl="7">
      <ContentWrap>
        <el-tabs v-model="activeView" @tab-change="handleViewChange">
          <el-tab-pane label="待我协同" name="TODO" />
          <el-tab-pane label="我发起的" name="STARTED" />
          <el-tab-pane label="已完成" name="COMPLETED" />
        </el-tabs>

        <el-alert
          v-if="listError"
          class="mb-10px"
          type="warning"
          :closable="false"
          show-icon
          title="协同列表加载失败"
        >
          <template #default
            ><el-link type="primary" :underline="false" @click="getList">重试</el-link></template
          >
        </el-alert>

        <div v-loading="listLoading" class="case-list">
          <div
            v-for="item in list"
            :key="item.id"
            class="case-item"
            :class="{ 'case-item--active': item.id === selectedId }"
            @click="selectCase(item.id)"
          >
            <div class="flex items-center justify-between gap-6px">
              <span class="min-w-0 flex-1 truncate font-500">{{
                item.contractName || '未命名合同'
              }}</span>
              <el-tag size="small" :type="statusType(item.status)">{{
                statusText(item.status)
              }}</el-tag>
            </div>
            <div class="mt-5px truncate text-12px text-[var(--el-text-color-secondary)]">{{
              item.contractNo || '草稿未编号'
            }}</div>
            <div
              class="mt-5px flex items-center justify-between text-12px text-[var(--el-text-color-secondary)]"
            >
              <span>修订 R{{ revisionLabel(item.revisionId) }}</span>
              <span>{{ item.legalUserName || item.starterUserName || '' }}</span>
            </div>
            <el-tag v-if="item.conclusionExpired" class="mt-6px" size="small" type="warning"
              >结论已因新修订过期</el-tag
            >
          </div>
          <el-empty
            v-if="!listError && list.length === 0"
            description="暂无协同任务"
            :image-size="64"
          />
        </div>
        <Pagination
          v-if="total > 0"
          v-model:page="query.pageNo"
          v-model:limit="query.pageSize"
          :total="total"
          small
          layout="total, prev, pager, next"
          @pagination="getList"
        />
      </ContentWrap>
    </el-col>

    <el-col :xs="24" :sm="24" :md="15" :lg="16" :xl="17">
      <ContentWrap v-loading="detailLoading">
        <el-alert
          v-if="detailError"
          type="warning"
          :closable="false"
          show-icon
          title="协同详情加载失败"
        >
          <template #default
            ><el-link type="primary" :underline="false" @click="loadDetail">重试</el-link></template
          >
        </el-alert>
        <template v-else-if="detail.id">
          <div class="mb-12px flex flex-wrap items-start justify-between gap-10px">
            <div>
              <div class="flex flex-wrap items-center gap-8px">
                <span class="text-18px font-bold">{{
                  detail.contractName || contract.title || '未命名合同'
                }}</span>
                <el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
                <el-tag v-if="detail.conclusionExpired" type="warning">结论已过期</el-tag>
              </div>
              <div class="mt-5px text-12px text-[var(--el-text-color-secondary)]">
                协同修订：{{ detail.revisionId || '-' }}
                <span class="ml-10px"
                  >当前修订：{{
                    detail.currentRevisionId || contract.currentRevisionId || '-'
                  }}</span
                >
                <span class="ml-10px">法务：{{ detail.legalUserName || '-' }}</span>
              </div>
            </div>
            <div class="flex gap-8px">
              <el-button v-if="detail.contractId" @click="openWorkspace"
                ><Icon icon="ep:link" class="mr-5px" />打开合同工作区</el-button
              >
              <el-button v-if="canCancel" type="danger" plain @click="openAction('CANCEL')"
                >取消协同</el-button
              >
              <el-button
                v-if="canRequestChange"
                type="warning"
                @click="openAction('REQUEST_CHANGE')"
                >要求修改</el-button
              >
              <el-button v-if="canComplete" type="success" @click="openAction('COMPLETE')"
                >完成协同</el-button
              >
            </div>
          </div>

          <el-alert
            v-if="detail.conclusionExpired"
            class="mb-12px"
            type="warning"
            :closable="false"
            show-icon
            title="合同已形成新修订，本协同结论不再满足提交校验；请对当前修订重新确认。"
          />
          <el-alert
            v-if="detail.reason"
            class="mb-12px"
            type="info"
            :closable="false"
            :title="`协同说明：${detail.reason}`"
          />

          <el-tabs v-model="detailTab">
            <el-tab-pane label="合同正文" name="document">
              <DocumentPreview v-if="contract.id" :contract="contract" @refresh="loadContract" />
              <el-empty v-else description="合同正文上下文暂时无法加载" :image-size="70" />
            </el-tab-pane>
            <el-tab-pane label="意见记录" name="comments">
              <div v-if="detail.comments?.length" class="comment-list">
                <div
                  v-for="itemComment in detail.comments"
                  :key="itemComment.id"
                  class="comment-item"
                >
                  <div class="flex items-center justify-between gap-8px">
                    <span class="font-500">{{ itemComment.userName || '协同参与人' }}</span>
                    <span class="text-12px text-[var(--el-text-color-secondary)]">{{
                      formatTime(itemComment.createTime)
                    }}</span>
                  </div>
                  <div class="mt-6px whitespace-pre-wrap text-14px">{{ itemComment.content }}</div>
                  <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]"
                    >基于修订 {{ itemComment.revisionId }}</div
                  >
                </div>
              </div>
              <el-empty v-else description="暂无协同意见" :image-size="60" />

              <el-form v-if="canComment" class="mt-14px" @submit.prevent>
                <el-form-item
                  ><el-input
                    v-model="comment"
                    type="textarea"
                    :rows="3"
                    maxlength="1000"
                    show-word-limit
                    placeholder="补充与当前修订相关的协同意见"
                /></el-form-item>
                <el-form-item
                  ><el-button
                    type="primary"
                    :disabled="!comment.trim()"
                    :loading="actionLoading"
                    @click="submitComment"
                    >发送意见</el-button
                  ></el-form-item
                >
              </el-form>
            </el-tab-pane>
            <el-tab-pane label="处理结论" name="conclusion">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="协同状态">{{
                  statusText(detail.status)
                }}</el-descriptions-item>
                <el-descriptions-item label="完成修订">{{
                  detail.completedRevisionId || '-'
                }}</el-descriptions-item>
                <el-descriptions-item label="处理结论">{{
                  detail.conclusion || '尚未形成结论'
                }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty
          v-else-if="!detailLoading"
          description="从左侧选择一条协同任务"
          :image-size="80"
        />
      </ContentWrap>
    </el-col>
  </el-row>

  <Dialog v-model="actionVisible" :title="actionTitle" width="520px">
    <el-form label-width="80px">
      <el-form-item :label="actionType === 'COMPLETE' ? '协同结论' : '处理说明'">
        <el-input
          v-model="actionContent"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          :placeholder="actionPlaceholder"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="actionVisible = false">取消</el-button>
      <el-button
        :type="
          actionType === 'COMPLETE' ? 'success' : actionType === 'CANCEL' ? 'danger' : 'warning'
        "
        :disabled="!actionContent.trim()"
        :loading="actionLoading"
        @click="submitAction"
        >确认</el-button
      >
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as CollaborationApi from '@/api/clm/collaboration'
import * as ContractApi from '@/api/clm/contract'
import DocumentPreview from '@/views/clm/contract/detail/components/DocumentPreview.vue'

defineOptions({ name: 'ClmCollaboration' })
const route = useRoute()
const router = useRouter()
const message = useMessage()
const activeView = ref<CollaborationApi.CollaborationView>(
  (route.query.view as CollaborationApi.CollaborationView) || 'TODO'
)
const query = reactive({ pageNo: 1, pageSize: 10 })
const list = ref<CollaborationApi.CollaborationCaseVO[]>([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref(false)
const selectedId = ref('')
const detail = ref<CollaborationApi.CollaborationCaseVO>({} as CollaborationApi.CollaborationCaseVO)
const contract = ref<ContractApi.ContractVO>({} as ContractApi.ContractVO)
const detailLoading = ref(false)
const detailError = ref(false)
const detailTab = ref('document')
const comment = ref('')
const actionVisible = ref(false)
const actionLoading = ref(false)
const actionType = ref<'REQUEST_CHANGE' | 'COMPLETE' | 'CANCEL'>('REQUEST_CHANGE')
const actionContent = ref('')

const getList = async () => {
  listLoading.value = true
  listError.value = false
  try {
    const data = await CollaborationApi.getCollaborationPage({ ...query, view: activeView.value })
    list.value = data?.list || []
    total.value = data?.total || 0
    const routeId = String(route.params.id || route.query.caseId || '')
    if (routeId) selectedId.value = routeId
    else if (!selectedId.value && list.value.length) selectedId.value = list.value[0].id
    if (selectedId.value) await loadDetail()
  } catch {
    list.value = []
    total.value = 0
    listError.value = true
  } finally {
    listLoading.value = false
  }
}

const loadContract = async () => {
  if (!detail.value.contractId) return
  try {
    contract.value =
      detail.value.contract || (await ContractApi.getContract(detail.value.contractId))
  } catch {
    contract.value = {} as ContractApi.ContractVO
  }
}

const loadDetail = async () => {
  if (!selectedId.value) return
  detailLoading.value = true
  detailError.value = false
  try {
    detail.value = await CollaborationApi.getCollaboration(selectedId.value)
    await loadContract()
  } catch {
    detail.value = {} as CollaborationApi.CollaborationCaseVO
    contract.value = {} as ContractApi.ContractVO
    detailError.value = true
  } finally {
    detailLoading.value = false
  }
}

const selectCase = async (id: string) => {
  selectedId.value = id
  detailTab.value = 'document'
  await loadDetail()
}
const handleViewChange = async () => {
  query.pageNo = 1
  selectedId.value = ''
  detail.value = {} as CollaborationApi.CollaborationCaseVO
  await router.replace({ name: 'ClmCollaboration', query: { view: activeView.value } })
  await getList()
}

const allowed = (action: string) => {
  if (Array.isArray(detail.value.availableActions)) {
    return detail.value.availableActions.includes(action)
  }
  if (action === 'CANCEL') {
    return ['TODO', 'RUNNING', 'PENDING'].includes(detail.value.status)
  }
  return ['TODO', 'RUNNING', 'PENDING', 'CHANGE_REQUESTED'].includes(detail.value.status)
}
const canRequestChange = computed(() => activeView.value === 'TODO' && allowed('REQUEST_CHANGE'))
const canComplete = computed(() => activeView.value === 'TODO' && allowed('COMPLETE'))
const canComment = computed(() => allowed('COMMENT'))
const canCancel = computed(() => activeView.value === 'STARTED' && allowed('CANCEL'))

const submitComment = async () => {
  if (!comment.value.trim() || !detail.value.id) return
  actionLoading.value = true
  try {
    await CollaborationApi.commentCollaboration({
      caseId: detail.value.id,
      revisionId: detail.value.currentRevisionId || detail.value.revisionId,
      content: comment.value.trim()
    })
    message.success('意见已发送')
    comment.value = ''
    await loadDetail()
  } finally {
    actionLoading.value = false
  }
}

const openAction = (type: 'REQUEST_CHANGE' | 'COMPLETE' | 'CANCEL') => {
  actionType.value = type
  actionContent.value = ''
  actionVisible.value = true
}
const actionTitle = computed(
  () =>
    ({ REQUEST_CHANGE: '要求业务修改', COMPLETE: '完成法务协同', CANCEL: '取消协同' })[
      actionType.value
    ]
)
const actionPlaceholder = computed(
  () =>
    ({
      REQUEST_CHANGE: '请明确需要修改的内容',
      COMPLETE: '请填写对当前修订的审查结论',
      CANCEL: '请填写取消原因'
    })[actionType.value]
)
const submitAction = async () => {
  if (!detail.value.id || !actionContent.value.trim()) return
  actionLoading.value = true
  try {
    if (actionType.value === 'CANCEL')
      await CollaborationApi.cancelCollaboration({
        caseId: detail.value.id,
        reason: actionContent.value.trim()
      })
    else {
      const data = {
        caseId: detail.value.id,
        revisionId: detail.value.currentRevisionId || detail.value.revisionId,
        content: actionContent.value.trim()
      }
      if (actionType.value === 'COMPLETE') await CollaborationApi.completeCollaboration(data)
      else await CollaborationApi.requestCollaborationChange(data)
    }
    message.success(
      actionType.value === 'COMPLETE'
        ? '协同已完成'
        : actionType.value === 'CANCEL'
          ? '协同已取消'
          : '修改要求已发送'
    )
    actionVisible.value = false
    await getList()
  } finally {
    actionLoading.value = false
  }
}

const openWorkspace = () =>
  router.push({ name: 'ClmContractDetail', params: { id: detail.value.contractId } })
const revisionLabel = (id?: string) => (id ? id : '-')
const statusText = (status?: string) =>
  (
    ({
      TODO: '待协同',
      PENDING: '待协同',
      RUNNING: '协同中',
      COMPLETED: '已完成',
      CHANGE_REQUESTED: '要求修改',
      CANCELED: '已取消'
    }) as Record<string, string>
  )[status || ''] ||
  status ||
  '-'
const statusType = (status?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' =>
  (
    ({
      TODO: 'warning',
      PENDING: 'warning',
      RUNNING: 'primary',
      COMPLETED: 'success',
      CHANGE_REQUESTED: 'warning',
      CANCELED: 'info'
    }) as Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'>
  )[status || ''] || 'info'
const formatTime = (value?: string) =>
  value ? formatDate(new Date(value), 'YYYY-MM-DD HH:mm') : '-'

watch(
  () => route.params.id,
  (id) => {
    if (id) {
      selectedId.value = String(id)
      loadDetail()
    }
  }
)
onMounted(getList)
</script>

<style lang="scss" scoped>
.case-list {
  max-height: calc(100vh - 355px);
  min-height: 360px;
  overflow: auto;
}

.case-item {
  padding: 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.case-item:hover,
.case-item--active {
  background: var(--el-fill-color-light);
}

.case-item--active {
  border-left: 3px solid var(--el-color-primary);
}

.comment-list {
  max-height: 440px;
  overflow: auto;
}

.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
