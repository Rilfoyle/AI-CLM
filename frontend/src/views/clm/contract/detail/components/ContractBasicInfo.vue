<template>
  <el-descriptions :column="2" size="small" border class="mb-20px">
    <el-descriptions-item label="合同编号">{{ contract.contractNo || '-' }}</el-descriptions-item>
    <el-descriptions-item label="标题">{{ contract.title || '-' }}</el-descriptions-item>
    <el-descriptions-item label="类型">
      {{ contract.typeName || '-' }}
      <el-tag v-if="contract.typeVersionNo" size="small" type="info" class="ml-5px">
        v{{ contract.typeVersionNo }}
      </el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="金额">{{ formatAmount(contract.amount) }}</el-descriptions-item>
    <el-descriptions-item label="币种">{{ contract.currency || '-' }}</el-descriptions-item>
    <el-descriptions-item label="签订日期">{{ contract.signDate || '-' }}</el-descriptions-item>
    <el-descriptions-item label="生效日期">
      {{ contract.effectiveDate || '-' }}
    </el-descriptions-item>
    <el-descriptions-item label="到期日期">{{ contract.expiryDate || '-' }}</el-descriptions-item>
    <el-descriptions-item label="负责人">{{ contract.ownerUserName || '-' }}</el-descriptions-item>
    <el-descriptions-item label="部门">{{ contract.ownerDeptName || '-' }}</el-descriptions-item>
    <el-descriptions-item label="创建时间">
      {{ contract.createTime ? formatDate(contract.createTime) : '-' }}
    </el-descriptions-item>
    <el-descriptions-item v-if="contract.relationType" label="关联合同" :span="2">
      {{ contract.relationType === 'RENEWAL' ? '续签' : '复制' }}自
      <el-link
        v-if="contract.sourceContractId"
        type="primary"
        class="ml-5px"
        @click="goSourceContract"
      >
        {{ contract.sourceContractNo || contract.sourceContractTitle || '源合同' }}
      </el-link>
      <span v-else class="ml-5px">{{ contract.sourceContractNo || '-' }}</span>
    </el-descriptions-item>
    <el-descriptions-item label="说明" :span="2">
      {{ contract.description || '-' }}
    </el-descriptions-item>
  </el-descriptions>

  <el-divider content-position="left">签约方</el-divider>
  <el-table :data="parties" border stripe class="mb-20px">
    <el-table-column label="角色" align="center" width="140">
      <template #default="scope">
        <dict-tag :type="DICT_TYPE.CLM_CONTRACT_PARTY_ROLE" :value="scope.row.roleCode" />
      </template>
    </el-table-column>
    <el-table-column label="签约方" prop="partyName" min-width="200">
      <template #default="scope">
        {{ scope.row.partyName || scope.row.partySnapshot?.name || '-' }}
      </template>
    </el-table-column>
    <el-table-column label="统一社会信用代码" min-width="200">
      <template #default="scope">
        {{ scope.row.partySnapshot?.unifiedCreditCode || '-' }}
      </template>
    </el-table-column>
    <el-table-column label="主体类型" align="center" width="120">
      <template #default="scope">
        <dict-tag
          v-if="scope.row.partySnapshot?.partyType"
          :type="DICT_TYPE.CLM_PARTY_TYPE"
          :value="scope.row.partySnapshot.partyType"
        />
        <span v-else>-</span>
      </template>
    </el-table-column>
  </el-table>

  <el-divider content-position="left">扩展字段</el-divider>
  <div v-loading="schemaLoading">
    <form-create
      v-if="detailForm.rule.length"
      :rule="detailForm.rule"
      :option="detailForm.option"
      v-model="customData"
      v-model:api="fApi"
    />
    <el-empty v-else description="无扩展字段" :image-size="60" />
  </div>
</template>

<script lang="ts" setup>
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { setConfAndFields2 } from '@/utils/formCreate'
import * as ContractApi from '@/api/clm/contract'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmContractBasicInfo' })

const props = defineProps<{ contract: ContractApi.ContractVO }>()
defineEmits<{ refresh: [] }>()

const { push } = useRouter()

/** 跳转源合同详情 */
const goSourceContract = () => {
  if (props.contract.sourceContractId) {
    push({ name: 'ClmContractDetail', params: { id: props.contract.sourceContractId } })
  }
}

const schemaLoading = ref(false)
const detailForm = ref<{ rule: any[]; option: any }>({ rule: [], option: {} })
const customData = ref<Record<string, any>>({})
const fApi = ref<FormCreateApi>()

const parties = computed(() => (props.contract.parties || []) as ContractApi.ContractPartyVO[])

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

/** 加载扩展字段（只读） */
const loadSchema = async () => {
  detailForm.value = { rule: [], option: {} }
  customData.value = { ...(props.contract.customData || {}) }
  if (!props.contract.typeVersionId) {
    return
  }
  schemaLoading.value = true
  try {
    const version = await ContractTypeApi.getContractTypeVersion(props.contract.typeVersionId)
    const fields = version?.formFields || []
    if (fields.length === 0) {
      return
    }
    setConfAndFields2(detailForm, version.formConf || '{}', fields)
    await nextTick()
    fApi.value?.btn.show(false)
    fApi.value?.resetBtn.show(false)
    fApi.value?.disabled(true)
  } finally {
    schemaLoading.value = false
  }
}

watch(
  () => [props.contract.typeVersionId, props.contract.updateTime],
  () => {
    loadSchema()
  },
  { immediate: true }
)
</script>
