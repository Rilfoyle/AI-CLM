<template>
  <ContentWrap v-loading="loading">
    <el-alert
      v-if="loadError"
      type="warning"
      :closable="false"
      show-icon
      title="合同工作区加载失败"
    >
      <template #default
        ><el-link type="primary" :underline="false" @click="getContractData"
          >重新加载</el-link
        ></template
      >
    </el-alert>

    <template v-else-if="contract.id">
      <div class="flex flex-wrap items-start justify-between gap-12px">
        <div class="flex min-w-0 items-start gap-8px">
          <el-button link class="mt-2px" @click="handleBack"
            ><Icon icon="ep:arrow-left" :size="18"
          /></el-button>
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-8px">
              <span class="truncate text-20px font-bold">{{ contract.title || '未命名合同' }}</span>
              <el-tag :type="stageTagType">{{ stageText }}</el-tag>
              <el-tag v-if="currentRevisionNo" type="info"
                >当前修订 R{{ currentRevisionNo }}</el-tag
              >
              <el-tag v-if="contract.deleted" type="danger">已删除草稿</el-tag>
            </div>
            <div class="mt-5px text-13px text-[var(--el-text-color-secondary)]">
              合同编号：{{ contract.contractNo || '提交成功后分配' }}
              <span class="ml-14px">类型：{{ contract.typeName || '-' }}</span>
              <span class="ml-14px">负责人：{{ contract.ownerUserName || '-' }}</span>
            </div>
          </div>
        </div>
        <div class="flex flex-wrap justify-end gap-8px">
          <el-button
            v-if="contract.deleted"
            v-hasPermi="['clm:contract:restore']"
            type="success"
            @click="handleRestore"
          >
            <Icon icon="ep:refresh-left" class="mr-5px" /> 恢复草稿
          </el-button>
          <template v-else>
            <el-button v-if="canEdit && currentRevisionId" @click="openCollaboration">
              <Icon icon="ep:chat-dot-round" class="mr-5px" /> 发起法务协同
            </el-button>
            <el-button
              v-if="canSubmit"
              type="primary"
              :disabled="!currentRevisionId"
              @click="openSubmit"
            >
              <Icon icon="ep:position" class="mr-5px" /> 提交审批
            </el-button>
            <el-button
              v-if="contract.permissions?.canDelete"
              v-hasPermi="['clm:contract:delete']"
              type="danger"
              plain
              @click="handleDelete"
            >
              <Icon icon="ep:delete" class="mr-5px" /> 删除草稿
            </el-button>
          </template>
        </div>
      </div>

      <el-alert
        v-if="approvalTaskId"
        class="mt-12px"
        type="warning"
        :closable="false"
        show-icon
        title="审批任务内编辑请返回审批中心执行；合同工作区不会绕过节点编辑政策保存普通修订。"
      />

      <el-alert
        v-if="templateUpgrade?.upgradeAvailable && canEdit"
        class="mt-12px"
        type="info"
        :closable="false"
        show-icon
      >
        <template #title>
          模板已有新版本：V{{ templateUpgrade.oldTemplateVersionNo || '-' }} → V{{
            templateUpgrade.newTemplateVersionNo
          }}
        </template>
        <template #default>
          <div class="flex flex-wrap items-center justify-between gap-8px">
            <span>升级会显式生成新合同修订与正文版本，不会覆盖当前修订。</span>
            <el-button
              link
              type="primary"
              :loading="upgradingTemplate"
              :disabled="!currentRevisionId"
              @click="handleTemplateUpgrade"
            >
              预览确认并升级
            </el-button>
          </div>
        </template>
      </el-alert>

      <el-steps :active="stepActive" align-center finish-status="success" class="mt-16px">
        <el-step title="合同起草" />
        <el-step title="协同与校验" />
        <el-step title="合同审批" />
        <el-step title="审批完成" />
      </el-steps>
    </template>
  </ContentWrap>

  <el-row v-if="contract.id && !loadError" :gutter="12">
    <el-col :xs="24" :sm="24" :md="24" :lg="15" :xl="15">
      <ContentWrap>
        <el-tabs v-model="documentTab">
          <el-tab-pane label="合同正文" name="preview">
            <DocumentPreview
              v-if="canViewDocument"
              ref="previewRef"
              :contract="workspaceContract"
              @refresh="getContractData"
            />
            <el-empty v-else description="当前角色无合同正文查看权限" :image-size="72" />
          </el-tab-pane>
          <el-tab-pane label="正文版本与附件" name="documents">
            <ContractDocuments
              v-if="canViewDocument"
              :contract="workspaceContract"
              @refresh="getContractData"
              @preview="handlePreviewVersion"
            />
            <el-empty v-else description="当前角色无正文版本与附件查看权限" :image-size="72" />
          </el-tab-pane>
        </el-tabs>
      </ContentWrap>
    </el-col>

    <el-col :xs="24" :sm="24" :md="24" :lg="9" :xl="9">
      <ContentWrap>
        <el-tabs v-model="infoTab">
          <el-tab-pane label="合同信息" name="basic">
            <el-form
              ref="revisionFormRef"
              :model="revisionForm"
              :rules="revisionRules"
              label-width="88px"
              :disabled="!canEdit"
            >
              <el-form-item label="合同名称" prop="name"
                ><el-input v-model="revisionForm.name" maxlength="200"
              /></el-form-item>
              <el-row :gutter="10">
                <el-col :span="14"
                  ><el-form-item label="合同金额" prop="amount"
                    ><el-input-number
                      v-model="revisionForm.amount"
                      :min="0"
                      :precision="2"
                      :controls="false"
                      class="!w-full" /></el-form-item
                ></el-col>
                <el-col :span="10"
                  ><el-form-item label-width="0" prop="currency"
                    ><el-select v-model="revisionForm.currency" class="w-full"
                      ><el-option label="CNY" value="CNY" /><el-option
                        label="USD"
                        value="USD" /><el-option
                        label="EUR"
                        value="EUR" /></el-select></el-form-item
                ></el-col>
              </el-row>
              <el-form-item label="开始日期" prop="startDate"
                ><el-date-picker
                  v-model="revisionForm.startDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="!w-full"
              /></el-form-item>
              <el-form-item label="结束日期" prop="endDate"
                ><el-date-picker
                  v-model="revisionForm.endDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  class="!w-full"
              /></el-form-item>
              <el-form-item label="我方主体" prop="ourPartyId">
                <el-select
                  v-model="revisionForm.ourPartyId"
                  filterable
                  class="w-full"
                  placeholder="请选择我方主体"
                >
                  <el-option
                    v-for="item in ourPartyList"
                    :key="item.id"
                    :label="item.name"
                    :value="String(item.id)"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="相对方" prop="counterpartyIds">
                <el-select
                  v-model="revisionForm.counterpartyIds"
                  multiple
                  filterable
                  collapse-tags
                  class="w-full"
                  placeholder="请选择相对方"
                >
                  <el-option
                    v-for="item in counterpartyList"
                    :key="item.id"
                    :label="item.name"
                    :value="String(item.id)"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="canEdit" label="变更说明" prop="changeReason">
                <el-input
                  v-model="revisionForm.changeReason"
                  type="textarea"
                  :rows="2"
                  maxlength="300"
                  show-word-limit
                  placeholder="说明本次修订原因"
                />
              </el-form-item>
              <el-form-item v-if="canEdit">
                <el-button
                  type="primary"
                  :loading="saving"
                  :disabled="!currentRevisionId"
                  @click="saveRevision"
                >
                  保存为新修订
                </el-button>
                <span
                  v-if="!currentRevisionId"
                  class="ml-8px text-12px text-[var(--el-color-danger)]"
                  >修订基线未就绪</span
                >
              </el-form-item>
            </el-form>
            <el-alert
              v-if="saveError"
              type="warning"
              :closable="false"
              show-icon
              :title="saveError"
            />
          </el-tab-pane>

          <el-tab-pane label="修订" name="revisions">
            <RevisionPanel
              v-if="canViewRevisions"
              ref="revisionPanelRef"
              :contract-id="contract.id"
              :current-revision-id="currentRevisionId"
              @loaded="handleRevisionsLoaded"
            />
            <el-empty v-else description="当前角色无合同修订查看权限" :image-size="64" />
          </el-tab-pane>

          <el-tab-pane label="重大承诺" name="commitments">
            <CommitmentPanel
              :contract-id="contract.id"
              :current-revision-id="currentRevisionId"
              :editable="canEdit"
              @changed="handleRevisionChanged"
            />
          </el-tab-pane>

          <el-tab-pane label="AI 审阅" name="ai" v-hasPermi="['clm:ai-review:run']">
            <AiReviewPanel
              :contract-id="contract.id"
              :revision-id="currentRevisionId"
              :revision-no="currentRevisionNo"
              :editable="canEdit"
            />
          </el-tab-pane>

          <el-tab-pane label="审批" name="approval">
            <el-empty description="审批任务与跨实例历史在审批中心统一查看" :image-size="60">
              <el-button type="primary" @click="openApprovalHistory">查看审批历史</el-button>
            </el-empty>
          </el-tab-pane>

          <el-tab-pane label="时间线" name="timeline">
            <ContractAuditEvents :contract="contract" />
          </el-tab-pane>
        </el-tabs>
      </ContentWrap>
    </el-col>
  </el-row>

  <SubmitApprovalDialog ref="submitDialogRef" @success="handleSubmitSuccess" />
  <CollaborationStartDialog ref="collaborationDialogRef" @success="handleCollaborationSuccess" />
</template>

<script lang="ts" setup>
import * as ContractApi from '@/api/clm/contract'
import * as PartyApi from '@/api/clm/party'
import * as RevisionApi from '@/api/clm/revision'
import * as TemplateApi from '@/api/clm/template'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { checkPermi } from '@/utils/permission'
import ContractDocuments from './components/ContractDocuments.vue'
import DocumentPreview from './components/DocumentPreview.vue'
import RevisionPanel from './components/RevisionPanel.vue'
import CommitmentPanel from './components/CommitmentPanel.vue'
import ContractAuditEvents from './components/ContractAuditEvents.vue'
import SubmitApprovalDialog from './components/SubmitApprovalDialog.vue'
import CollaborationStartDialog from './components/CollaborationStartDialog.vue'
import AiReviewPanel from './components/AiReviewPanel.vue'

defineOptions({ name: 'ClmContractDetail' })
const props = defineProps<{ id?: number | string }>()
const route = useRoute()
const router = useRouter()
const { delView } = useTagsViewStore()
const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)
const upgradingTemplate = ref(false)
const saveError = ref('')
const contract = ref<ContractApi.ContractVO>({} as ContractApi.ContractVO)
const ourPartyList = ref<PartyApi.PartySimpleVO[]>([])
const counterpartyList = ref<PartyApi.PartySimpleVO[]>([])
const revisions = ref<RevisionApi.ContractRevisionVO[]>([])
const documentTab = ref('preview')
const infoTab = ref('basic')
const previewRef = ref<InstanceType<typeof DocumentPreview>>()
const revisionPanelRef = ref<InstanceType<typeof RevisionPanel>>()
const submitDialogRef = ref<InstanceType<typeof SubmitApprovalDialog>>()
const collaborationDialogRef = ref<InstanceType<typeof CollaborationStartDialog>>()
const revisionFormRef = ref()
const templateUpgrade = ref<TemplateApi.TemplateUpgradePreviewVO>()

const contractId = computed(() => String(props.id || route.params.id || ''))
const isDeletedView = computed(() => String(route.query.deleted || '') === '1')
const approvalTaskId = computed(() =>
  String(route.query.taskId || route.query.approvalTaskId || '')
)
const currentRevision = computed(() => revisions.value[0])
const currentRevisionId = computed(
  () => currentRevision.value?.id || contract.value.currentRevisionId || ''
)
const currentRevisionNo = computed(
  () => currentRevision.value?.revisionNo || contract.value.currentRevisionNo
)
const canEdit = computed(
  () => !contract.value.deleted && !approvalTaskId.value && !!contract.value.permissions?.canEdit
)
const canSubmit = computed(() => !contract.value.deleted && !!contract.value.permissions?.canSubmit)
const canViewDocument = computed(
  () =>
    !contract.value.deleted &&
    !!contract.value.permissions?.canDownload &&
    checkPermi(['clm:contract:download'])
)
const canViewRevisions = computed(() => canViewDocument.value && checkPermi(['clm:revision:query']))
const workspaceContract = computed<ContractApi.ContractVO>(() => ({
  ...contract.value,
  permissions: {
    canView: true,
    canEdit: canEdit.value,
    canDownload: !!contract.value.permissions?.canDownload,
    canManage: !!contract.value.permissions?.canManage,
    canSubmit: canSubmit.value,
    canDelete: !contract.value.deleted && !!contract.value.permissions?.canDelete,
    canCancelApproval: !!contract.value.permissions?.canCancelApproval
  }
}))

const revisionForm = reactive({
  name: '',
  amount: undefined as number | undefined,
  currency: 'CNY',
  startDate: '',
  endDate: '',
  ourPartyId: '',
  counterpartyIds: [] as string[],
  changeReason: ''
})
const revisionRules = {
  name: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
  ourPartyId: [{ required: true, message: '请选择我方主体', trigger: 'change' }],
  counterpartyIds: [
    { required: true, type: 'array', min: 1, message: '至少选择一个相对方', trigger: 'change' }
  ],
  changeReason: [{ required: true, message: '请说明本次修订原因', trigger: 'blur' }]
}

const fillRevisionForm = () => {
  const parties = (contract.value.parties || []) as ContractApi.ContractPartyVO[]
  Object.assign(revisionForm, {
    name: contract.value.title || '',
    amount: contract.value.amount,
    currency: contract.value.currency || 'CNY',
    startDate: contract.value.startDate || contract.value.effectiveDate || '',
    endDate: contract.value.endDate || contract.value.expiryDate || '',
    ourPartyId: String(parties.find((item) => item.roleCode === 'OUR_SIDE')?.partyId || ''),
    counterpartyIds: parties
      .filter((item) => item.roleCode === 'COUNTERPARTY')
      .map((item) => String(item.partyId)),
    changeReason: ''
  })
}

const getContractData = async () => {
  if (!contractId.value) return
  loading.value = true
  loadError.value = false
  try {
    contract.value = isDeletedView.value
      ? await ContractApi.getDeletedContract(contractId.value)
      : await ContractApi.getContract(contractId.value)
    fillRevisionForm()
    await loadTemplateUpgradePreview()
  } catch {
    contract.value = {} as ContractApi.ContractVO
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const loadTemplateUpgradePreview = async () => {
  templateUpgrade.value = undefined
  if (contract.value.sourceMode !== 'TEMPLATE' || contract.value.deleted) return
  try {
    templateUpgrade.value = await TemplateApi.getTemplateUpgradePreview(contractId.value)
  } catch {
    templateUpgrade.value = undefined
  }
}

const loadParties = async () => {
  try {
    const [ours, counters] = await Promise.all([
      PartyApi.getPartySimpleList(true),
      PartyApi.getPartySimpleList(false)
    ])
    ourPartyList.value = ours || []
    counterpartyList.value = counters || []
  } catch {
    ourPartyList.value = []
    counterpartyList.value = []
  }
}

const handleRevisionsLoaded = (items: RevisionApi.ContractRevisionVO[]) => {
  revisions.value = items
}

const saveRevision = async () => {
  if (!(await revisionFormRef.value?.validate().catch(() => false)) || !currentRevisionId.value)
    return
  saving.value = true
  saveError.value = ''
  try {
    const result = await RevisionApi.saveContractRevision({
      contractId: contractId.value,
      baseRevisionId: currentRevisionId.value,
      name: revisionForm.name.trim(),
      amount: revisionForm.amount,
      currency: revisionForm.currency,
      startDate: revisionForm.startDate || undefined,
      endDate: revisionForm.endDate || undefined,
      ourPartyId: revisionForm.ourPartyId,
      counterpartyIds: revisionForm.counterpartyIds,
      changeReason: revisionForm.changeReason.trim()
    })
    message.success(`已保存为修订 R${result.revisionNo}`)
    revisionForm.changeReason = ''
    await getContractData()
    await revisionPanelRef.value?.reload()
  } catch {
    saveError.value = '保存失败。若合同已被其他人更新，请刷新后基于最新修订重新提交。'
  } finally {
    saving.value = false
  }
}

const handleTemplateUpgrade = async () => {
  if (!currentRevisionId.value || !templateUpgrade.value?.newTemplateVersionId) return
  await message.confirm(
    `确认将模板从 V${templateUpgrade.value.oldTemplateVersionNo || '-'} 升级到 V${templateUpgrade.value.newTemplateVersionNo}？系统会创建新修订并保留差异。`
  )
  upgradingTemplate.value = true
  try {
    await TemplateApi.upgradeContractTemplate({
      contractId: contractId.value,
      baseRevisionId: currentRevisionId.value,
      targetTemplateVersionId: templateUpgrade.value.newTemplateVersionId
    })
    message.success('模板已升级并生成新合同修订')
    await getContractData()
    await revisionPanelRef.value?.reload()
    infoTab.value = 'revisions'
  } finally {
    upgradingTemplate.value = false
  }
}

const handleRevisionChanged = async () => {
  await getContractData()
  await revisionPanelRef.value?.reload()
}
const handlePreviewVersion = async (versionId: number, fileName: string) => {
  if (!canViewDocument.value) return
  documentTab.value = 'preview'
  await nextTick()
  await previewRef.value?.loadVersion(versionId, fileName)
}
const openCollaboration = () =>
  currentRevisionId.value &&
  collaborationDialogRef.value?.open(contractId.value, currentRevisionId.value)
const openSubmit = () => submitDialogRef.value?.open(contract.value, currentRevisionId.value)
const handleSubmitSuccess = async () => {
  await getContractData()
  infoTab.value = 'approval'
}
const handleCollaborationSuccess = async (caseId: string) => {
  await router.push({ name: 'ClmCollaborationDetail', params: { id: caseId } })
}
const openApprovalHistory = () =>
  router.push({ name: 'ClmApprovalHistory', params: { contractId: contractId.value } })

const handleDelete = async () => {
  if (!contract.value.id) return
  try {
    await message.confirm('删除后草稿将进入“已删除草稿”，草稿所有者可以恢复。')
    await ContractApi.deleteContract(contract.value.id)
    message.success('草稿已删除')
    await getContractData()
  } catch {}
}
const handleRestore = async () => {
  try {
    await message.confirm('确认恢复该草稿？')
    await ContractApi.restoreContractDraft(contractId.value)
    message.success('草稿已恢复')
    const query = { ...route.query }
    delete query.deleted
    await router.replace({ query })
    await Promise.all([getContractData(), loadParties()])
  } catch {}
}
const handleBack = () => {
  delView(unref(router.currentRoute))
  router.push({ name: 'ClmContract' })
}

const stageText = computed(() => {
  if (contract.value.deleted) return '已删除草稿'
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    COLLABORATING: '法务协同',
    APPROVING: '审批中',
    APPROVED: '审批完成',
    NEEDS_CHANGE: '需修改',
    REJECTED: '已拒绝',
    BLOCKED: '配置阻断'
  }
  if (contract.value.stageCode) return labels[contract.value.stageCode] || contract.value.stageCode
  return (
    ({ 0: '草稿', 1: '审批中', 2: '审批完成', 3: '已拒绝' } as Record<number, string>)[
      contract.value.approvalStatus ?? -1
    ] || '处理中'
  )
})
const stageTagType = computed<'success' | 'warning' | 'danger' | 'info' | 'primary'>(
  () =>
    (
      ({
        APPROVED: 'success',
        APPROVING: 'primary',
        COLLABORATING: 'warning',
        NEEDS_CHANGE: 'warning',
        REJECTED: 'danger',
        BLOCKED: 'danger',
        DRAFT: 'info'
      }) as const
    )[contract.value.stageCode || ''] || 'info'
)
const stepActive = computed(
  () =>
    (
      ({
        DRAFT: 0,
        COLLABORATING: 1,
        NEEDS_CHANGE: 1,
        APPROVING: 2,
        REJECTED: 2,
        APPROVED: 4
      }) as Record<string, number>
    )[contract.value.stageCode || ''] ??
    (contract.value.approvalStatus === 2 ? 4 : contract.value.approvalStatus === 1 ? 2 : 0)
)

watch(contractId, (value, old) => {
  if (value && value !== old) getContractData()
})
onMounted(async () => {
  await Promise.all([getContractData(), isDeletedView.value ? Promise.resolve() : loadParties()])
})
</script>
