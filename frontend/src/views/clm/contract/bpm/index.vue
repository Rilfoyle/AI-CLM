<template>
  <ContentWrap v-loading="loading">
    <template v-if="detail.binding">
      <div class="mb-10px flex items-center justify-between">
        <span class="text-base font-bold">
          合同审批：{{ snapshotContract.title || detail.contract?.title || '-' }}
        </span>
        <el-button link type="primary" @click="openContractDetail">
          <Icon icon="ep:link" class="mr-5px" /> 打开合同详情
        </el-button>
      </div>

      <el-descriptions :column="3" border class="mb-15px">
        <el-descriptions-item label="合同编号">
          {{ snapshotContract.contractNo || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">
          {{ snapshotContract.title || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="类型">
          {{ snapshotContract.typeName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="金额">
          {{ formatAmount(snapshotContract.amount) }}
        </el-descriptions-item>
        <el-descriptions-item label="币种">
          {{ snapshotContract.currency || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="签订日期">
          {{ snapshotContract.signDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="生效日期">
          {{ snapshotContract.effectiveDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="到期日期">
          {{ snapshotContract.expiryDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="负责人">
          {{ snapshotContract.ownerUserName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="提交说明" :span="2">
          {{ detail.formSnapshot?.submitRemark || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="说明" :span="3">
          {{ snapshotContract.description || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">签约方</el-divider>
      <el-table :data="snapshotParties" border stripe class="mb-15px">
        <el-table-column label="角色" align="center" width="140">
          <template #default="scope">
            <dict-tag :type="DICT_TYPE.CLM_CONTRACT_PARTY_ROLE" :value="scope.row.roleCode" />
          </template>
        </el-table-column>
        <el-table-column label="签约方" prop="name" min-width="200" />
        <el-table-column label="统一社会信用代码" min-width="200">
          <template #default="scope">{{ scope.row.unifiedCreditCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="主体类型" align="center" width="120">
          <template #default="scope">
            <dict-tag
              v-if="scope.row.partyType"
              :type="DICT_TYPE.CLM_PARTY_TYPE"
              :value="scope.row.partyType"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">绑定正文版本</el-divider>
      <el-descriptions :column="1" border class="mb-15px">
        <el-descriptions-item label="版本">
          <span v-if="documentVersion.versionNo">v{{ documentVersion.versionNo }}</span>
          <span v-else>-</span>
          <el-tag v-if="detail.documentVersion?.frozen" type="warning" size="small" class="ml-5px">
            已冻结
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件名">
          {{ documentVersion.fileName || '-' }}
          <span v-if="documentVersion.fileSize" class="ml-5px text-gray-500">
            ({{ formatFileSize(documentVersion.fileSize) }})
          </span>
          <el-button
            v-if="canDownload && detail.documentVersion?.id"
            link
            type="primary"
            class="ml-10px"
            @click="handleDownload"
          >
            <Icon icon="ep:download" class="mr-5px" /> 下载
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="SHA-256">
          <span class="font-mono text-xs">
            {{ documentVersion.checksumSha256 || detail.binding?.checksumSha256 || '-' }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">扩展字段</el-divider>
      <form-create
        v-if="detailForm.rule.length"
        :rule="detailForm.rule"
        :option="detailForm.option"
        v-model="customData"
        v-model:api="fApi"
      />
      <el-empty v-else description="无扩展字段" :image-size="60" />
    </template>
    <el-empty v-else-if="!loading" description="未找到流程绑定信息" />
  </ContentWrap>
</template>

<script lang="ts" setup>
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { DICT_TYPE } from '@/utils/dict'
import { formatFileSize } from '@/utils/file'
import { setConfAndFields2 } from '@/utils/formCreate'
import * as ContractApi from '@/api/clm/contract'
import * as WorkflowBindingApi from '@/api/clm/workflowBinding'

defineOptions({ name: 'ClmContractBpmView' })

const props = defineProps<{ id?: number | string }>()

const { query } = useRoute() // 查询参数（独立路由打开时）
const { push } = useRouter()

const loading = ref(false)
const detail = ref<Partial<WorkflowBindingApi.WorkflowBindingDetailVO>>({})
const detailForm = ref<{ rule: any[]; option: any }>({ rule: [], option: {} })
const customData = ref<Record<string, any>>({})
const fApi = ref<FormCreateApi>()

const bindingId = computed(() => props.id || (query.id as unknown as string))
const snapshotContract = computed<Record<string, any>>(
  () => detail.value.formSnapshot?.contract || {}
)
const snapshotParties = computed<Record<string, any>[]>(
  () => detail.value.formSnapshot?.parties || []
)
const documentVersion = computed<Record<string, any>>(
  () => detail.value.documentVersion || detail.value.formSnapshot?.documentVersion || {}
)
const canDownload = computed(() => !!detail.value.contract?.permissions?.canDownload)

/** 金额格式化 */
const formatAmount = (amount?: number) => {
  if (amount === undefined || amount === null) {
    return '-'
  }
  return Number(amount).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/** 获得数据 */
const getInfo = async () => {
  if (!bindingId.value) return
  loading.value = true
  try {
    const data = await WorkflowBindingApi.getWorkflowBinding(bindingId.value)
    detail.value = data || {}
    // 只读渲染扩展字段
    detailForm.value = { rule: [], option: {} }
    customData.value = { ...(snapshotContract.value.customData || {}) }
    const fields = data?.formFields || []
    if (fields.length > 0) {
      setConfAndFields2(detailForm, data.formConf || '{}', fields)
      await nextTick()
      fApi.value?.btn.show(false)
      fApi.value?.resetBtn.show(false)
      fApi.value?.disabled(true)
    }
  } finally {
    loading.value = false
  }
}
defineExpose({ open: getInfo })

/** 下载绑定的正文版本 */
const handleDownload = async () => {
  const version = detail.value.documentVersion
  if (!version?.id) return
  await ContractApi.downloadVersionFile(version.id, version.fileName)
}

/** 打开合同详情 */
const openContractDetail = () => {
  const contractId = detail.value.binding?.contractId || detail.value.contract?.id
  if (!contractId) return
  push({ name: 'ClmContractDetail', params: { id: contractId } })
}

watch(
  () => props.id,
  (val, old) => {
    if (val && val !== old) {
      getInfo()
    }
  }
)

/** 初始化 */
onMounted(() => {
  getInfo()
})
</script>
