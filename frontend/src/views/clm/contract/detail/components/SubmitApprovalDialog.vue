<template>
  <Dialog v-model="dialogVisible" title="提交审批" width="800px">
    <div v-loading="loading">
      <el-alert
        v-if="!contract.currentDocumentVersion"
        type="warning"
        :closable="false"
        show-icon
        title="尚未上传合同正文，请先上传正文后再提交审批。"
        class="mb-15px"
      />
      <el-descriptions :column="1" border class="mb-15px" title="将绑定的正文版本">
        <el-descriptions-item label="版本">
          <span v-if="contract.currentDocumentVersion">
            v{{ contract.currentDocumentVersion.versionNo }}
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="文件名">
          {{ contract.currentDocumentVersion?.fileName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="SHA-256">
          <span class="font-mono text-xs">
            {{ contract.currentDocumentVersion?.checksumSha256 || '-' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="审批流程">
          <span v-if="preview.processDefinitionId">
            {{ preview.processDefinitionName || preview.processDefinitionKey }}
            <el-tag size="small" type="info" class="ml-5px">
              {{ preview.processDefinitionKey }}
            </el-tag>
          </span>
          <el-tag v-else type="danger" size="small">
            流程模型未发布（{{ preview.processDefinitionKey || '未配置' }}）
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="80px">
        <el-form-item label="提交说明">
          <el-input
            v-model="remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="可选，填写本次提交审批的说明"
          />
        </el-form-item>
      </el-form>

      <el-divider content-position="left">审批流程预览</el-divider>
      <ProcessInstanceTimeline
        v-if="activityNodes.length > 0"
        :activity-nodes="activityNodes"
        :show-status-icon="false"
        @select-user-confirm="selectUserConfirm"
      />
      <el-empty v-else description="暂无审批节点预览" :image-size="60" />
    </div>
    <template #footer>
      <el-button
        type="primary"
        :disabled="loading || submitting || !contract.currentDocumentVersion"
        :loading="submitting"
        @click="submitForm"
      >
        确认提交
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import ProcessInstanceTimeline from '@/views/bpm/processInstance/detail/ProcessInstanceTimeline.vue'
import * as ProcessInstanceApi from '@/api/bpm/processInstance'
import { CandidateStrategy, NodeId } from '@/components/SimpleProcessDesignerV2/src/consts'
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmSubmitApprovalDialog' })

const emit = defineEmits<{ success: [] }>()
const message = useMessage()

type StartUserSelectTask = { id: string; name: string }

const dialogVisible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const contract = ref<ContractApi.ContractVO>({} as ContractApi.ContractVO)
const preview = ref<ContractApi.ApprovalPreviewVO>({})
const remark = ref('')
const activityNodes = ref<ProcessInstanceApi.ApprovalNodeInfo[]>([]) // 审批节点信息
const startUserSelectTasks = ref<StartUserSelectTask[]>([]) // 发起人需要选择审批人的用户任务列表
const startUserSelectAssignees = ref<Record<string, number[]>>({}) // 发起人选择审批人的数据

/** 打开弹窗 */
const open = async (data: ContractApi.ContractVO) => {
  contract.value = data
  preview.value = {}
  remark.value = ''
  activityNodes.value = []
  startUserSelectTasks.value = []
  startUserSelectAssignees.value = {}
  dialogVisible.value = true
  loading.value = true
  try {
    preview.value = (await ContractApi.getApprovalPreview(data.id!)) || {}
    if (preview.value.processDefinitionId) {
      await getApprovalDetail()
    }
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

/** 审批相关：获取审批详情（节点预测） */
const getApprovalDetail = async () => {
  const data = await ProcessInstanceApi.getApprovalDetail({
    processDefinitionId: preview.value.processDefinitionId,
    activityId: NodeId.START_USER_NODE_ID,
    processVariablesStr: JSON.stringify({
      amount: contract.value.amount || 0,
      ownerDeptId: contract.value.ownerDeptId,
      contractTypeCode: contract.value.typeCode
    })
  })
  if (!data) {
    message.error('查询不到审批详情信息！')
    return
  }
  activityNodes.value = data.activityNodes || []
  // 获取发起人自选的任务
  startUserSelectTasks.value = (data.activityNodes || []).filter(
    (node: ProcessInstanceApi.ApprovalNodeInfo) =>
      CandidateStrategy.START_USER_SELECT === node.candidateStrategy
  )
  for (const node of startUserSelectTasks.value) {
    startUserSelectAssignees.value[node.id] = []
  }
}

/** 审批相关：选择发起人 */
const selectUserConfirm = (id: string, userList: any[]) => {
  startUserSelectAssignees.value[id] = userList?.map((item: any) => item.id)
}

/** 提交 */
const submitForm = async () => {
  if (!contract.value.currentDocumentVersion) {
    message.warning('尚未上传合同正文')
    return
  }
  if (!preview.value.processDefinitionId) {
    message.warning('审批流程模型未发布，无法提交')
    return
  }
  // 校验指定审批人
  for (const userTask of startUserSelectTasks.value) {
    const assignees = startUserSelectAssignees.value[userTask.id]
    if (!Array.isArray(assignees) || assignees.length === 0) {
      message.warning(`请选择${userTask.name}的审批人`)
      return
    }
  }
  submitting.value = true
  try {
    const data: ContractApi.ContractSubmitReqVO = {
      id: contract.value.id!,
      remark: remark.value || undefined
    }
    if (startUserSelectTasks.value.length > 0) {
      data.startUserSelectAssignees = startUserSelectAssignees.value
    }
    await ContractApi.submitContract(data)
    message.success('已提交审批')
    dialogVisible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
