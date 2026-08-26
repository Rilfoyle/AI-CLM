<template>
  <!-- 顶部横条：标题 / 状态 / 动作 / 阶段条 -->
  <ContentWrap v-loading="loading">
    <div class="flex items-start justify-between">
      <div class="flex items-start gap-10px">
        <el-button link class="mt-3px" @click="handleBack">
          <Icon icon="ep:arrow-left" :size="18" />
        </el-button>
        <div>
          <div class="flex flex-wrap items-center gap-10px">
            <span class="text-xl font-bold">{{ contract.title || '-' }}</span>
            <template v-if="contract.id">
              <dict-tag :type="DICT_TYPE.CLM_APPROVAL_STATUS" :value="contract.approvalStatus!" />
              <dict-tag :type="DICT_TYPE.CLM_LIFECYCLE_STATUS" :value="contract.lifecycleStatus!" />
            </template>
          </div>
          <div class="mt-5px text-sm text-gray-500">
            合同编号：{{ contract.contractNo || '-' }}
            <span class="ml-15px">类型：{{ contract.typeName || '-' }}</span>
            <span class="ml-15px">负责人：{{ contract.ownerUserName || '-' }}</span>
          </div>
        </div>
      </div>
      <div class="shrink-0">
        <el-button
          v-if="permissions.canEdit"
          v-hasPermi="['clm:contract:update']"
          @click="handleEdit"
        >
          <Icon icon="ep:edit" class="mr-5px" /> 编辑
        </el-button>
        <el-button
          v-if="permissions.canSubmit"
          v-hasPermi="['clm:contract:submit']"
          type="primary"
          @click="handleSubmit"
        >
          <Icon icon="ep:position" class="mr-5px" /> 提交审批
        </el-button>
        <el-button
          v-if="permissions.canCancelApproval"
          v-hasPermi="['clm:contract:submit']"
          type="warning"
          @click="handleCancelApproval"
        >
          <Icon icon="ep:close" class="mr-5px" /> 撤销审批
        </el-button>
        <el-button
          v-if="permissions.canArchive"
          v-hasPermi="['clm:contract:update']"
          type="success"
          @click="handleArchive"
        >
          <Icon icon="ep:folder-checked" class="mr-5px" /> 上传盖章件归档
        </el-button>
        <el-button
          v-if="permissions.canCopy"
          v-hasPermi="['clm:contract:create']"
          @click="handleCopy('COPY')"
        >
          <Icon icon="ep:copy-document" class="mr-5px" /> 复制
        </el-button>
        <el-button
          v-if="permissions.canCopy"
          v-hasPermi="['clm:contract:create']"
          @click="handleCopy('RENEWAL')"
        >
          <Icon icon="ep:refresh-right" class="mr-5px" /> 续签
        </el-button>
        <el-button
          v-if="permissions.canDelete"
          v-hasPermi="['clm:contract:delete']"
          type="danger"
          @click="handleDelete"
        >
          <Icon icon="ep:delete" class="mr-5px" /> 删除
        </el-button>
      </div>
    </div>
    <!-- 阶段条 -->
    <el-steps
      v-if="contract.id"
      :active="stepActive"
      align-center
      finish-status="success"
      class="clm-detail-steps mt-15px"
    >
      <el-step title="草稿" />
      <el-step
        title="审批中"
        :status="isRejected ? 'error' : undefined"
        :description="isRejected ? '已驳回' : undefined"
      />
      <el-step title="审批通过" />
      <el-step title="已签订" />
    </el-steps>
  </ContentWrap>

  <!-- 主体两栏：左文档预览 / 右信息面板 -->
  <el-row :gutter="16">
    <el-col :xs="24" :sm="24" :md="24" :lg="24" :xl="15">
      <ContentWrap>
        <DocumentPreview
          v-if="contract.id"
          ref="previewRef"
          :contract="contract"
          @refresh="getContractData"
        />
      </ContentWrap>
    </el-col>
    <el-col :xs="24" :sm="24" :md="24" :lg="24" :xl="9">
      <ContentWrap>
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基础信息" name="basic">
            <ContractBasicInfo v-if="contract.id" :contract="contract" @refresh="getContractData" />
          </el-tab-pane>
          <el-tab-pane label="文档版本" name="documents">
            <ContractDocuments
              v-if="contract.id"
              :contract="contract"
              @refresh="getContractData"
              @preview="handlePreviewVersion"
            />
          </el-tab-pane>
          <el-tab-pane label="参与人" name="participants">
            <ContractParticipants
              v-if="contract.id"
              :contract="contract"
              @refresh="getContractData"
            />
          </el-tab-pane>
          <el-tab-pane label="审批记录" name="approvals">
            <ContractApprovals v-if="contract.id" :contract="contract" @refresh="getContractData" />
          </el-tab-pane>
          <el-tab-pane label="审计轨迹" name="audit">
            <ContractAuditEvents
              v-if="contract.id"
              :contract="contract"
              @refresh="getContractData"
            />
          </el-tab-pane>
        </el-tabs>
      </ContentWrap>
    </el-col>
  </el-row>

  <!-- 提交审批弹窗 -->
  <SubmitApprovalDialog ref="submitDialogRef" @success="getContractData" />
  <!-- 归档弹窗 -->
  <ArchiveDialog ref="archiveDialogRef" @success="getContractData" />
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { DICT_TYPE } from '@/utils/dict'
import { useTagsViewStore } from '@/store/modules/tagsView'
import * as ContractApi from '@/api/clm/contract'
import * as ProcessInstanceApi from '@/api/bpm/processInstance'
import ContractBasicInfo from './components/ContractBasicInfo.vue'
import ContractDocuments from './components/ContractDocuments.vue'
import ContractParticipants from './components/ContractParticipants.vue'
import ContractApprovals from './components/ContractApprovals.vue'
import ContractAuditEvents from './components/ContractAuditEvents.vue'
import SubmitApprovalDialog from './components/SubmitApprovalDialog.vue'
import ArchiveDialog from './components/ArchiveDialog.vue'
import DocumentPreview from './components/DocumentPreview.vue'

defineOptions({ name: 'ClmContractDetail' })

const props = defineProps<{ id?: number | string }>()

const route = useRoute()
const { push, currentRoute } = useRouter()
const { delView } = useTagsViewStore()
const message = useMessage()
const { t } = useI18n()

const loading = ref(true) // 加载中
const activeTab = ref('basic')
const contract = ref<ContractApi.ContractVO>({} as ContractApi.ContractVO) // 详情
const submitDialogRef = ref()
const archiveDialogRef = ref()
const previewRef = ref<InstanceType<typeof DocumentPreview>>()

const contractId = computed(() => Number(props.id || route.params.id))
const permissions = computed<Partial<ContractApi.ContractPermissionsVO>>(
  () => contract.value.permissions || {}
)

/** 是否已驳回 */
const isRejected = computed(() => contract.value.approvalStatus === 3)

/** 阶段条 active：已签订→4；审批通过→3；审批中→2；否则（草稿/驳回）→1 */
const stepActive = computed(() => {
  if (contract.value.lifecycleStatus === 3) return 4
  if (contract.value.approvalStatus === 2) return 3
  if (contract.value.approvalStatus === 1) return 2
  return 1
})

/** 获取详情 */
const getContractData = async () => {
  loading.value = true
  try {
    contract.value = await ContractApi.getContract(contractId.value)
  } finally {
    loading.value = false
  }
}

/** 返回台账 */
const handleBack = () => {
  delView(unref(currentRoute))
  push({ name: 'ClmContract' })
}

/** 编辑 */
const handleEdit = () => {
  push({ name: 'ClmContractCreate', query: { id: contract.value.id } })
}

/** 提交审批 */
const handleSubmit = () => {
  submitDialogRef.value?.open(contract.value)
}

/** 撤销审批 */
const handleCancelApproval = async () => {
  const processInstanceId = contract.value.currentBinding?.processInstanceId
  if (!processInstanceId) {
    message.error('未找到审批中的流程实例')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入撤销原因', '撤销审批', {
    confirmButtonText: t('common.ok'),
    cancelButtonText: t('common.cancel'),
    inputPattern: /^[\s\S]*.*\S[\s\S]*$/, // 判断非空，且非空格
    inputErrorMessage: '撤销原因不能为空'
  })
  await ProcessInstanceApi.cancelProcessInstanceByStartUser(
    processInstanceId as unknown as number,
    value
  )
  message.success('撤销成功')
  await getContractData()
}

/** 上传盖章件归档 */
const handleArchive = () => {
  archiveDialogRef.value?.open(contract.value)
}

/** 复制 / 续签 */
const handleCopy = async (relationType: 'COPY' | 'RENEWAL') => {
  const isRenewal = relationType === 'RENEWAL'
  const { value } = await ElMessageBox.prompt(
    '请确认新合同标题，创建后将复制类型、金额、签约方与当前正文（如有）',
    isRenewal ? '合同续签' : '复制合同',
    {
      confirmButtonText: t('common.ok'),
      cancelButtonText: t('common.cancel'),
      inputValue: `${contract.value.title || ''}${isRenewal ? '（续签）' : '（复制）'}`,
      inputPattern: /^[\s\S]*.*\S[\s\S]*$/, // 判断非空，且非空格
      inputErrorMessage: '标题不能为空'
    }
  )
  const newId = await ContractApi.copyContract({
    sourceContractId: contract.value.id!,
    relationType,
    title: value
  })
  message.success(isRenewal ? '续签合同已创建' : '合同已复制')
  push({ name: 'ClmContractDetail', params: { id: newId } })
}

/** 版本表"预览"联动左侧预览器 */
const handlePreviewVersion = (versionId: number, fileName: string) => {
  previewRef.value?.loadVersion(versionId, fileName)
}

/** 删除 */
const handleDelete = async () => {
  try {
    await message.delConfirm()
    await ContractApi.deleteContract(contract.value.id!)
    message.success(t('common.delSuccess'))
    delView(unref(currentRoute))
    push({ name: 'ClmContract' })
  } catch {}
}

/** 合同编号变化时重新加载（同一页面切换不同合同） */
watch(contractId, (val, old) => {
  if (val && val !== old) {
    getContractData()
  }
})

/** 初始化 */
onMounted(() => {
  getContractData()
})
</script>

<style lang="scss" scoped>
.clm-detail-steps {
  max-width: 720px;

  :deep(.el-step__title) {
    font-size: 13px;
    line-height: 28px;
  }

  :deep(.el-step__description) {
    font-size: 12px;
  }
}
</style>
