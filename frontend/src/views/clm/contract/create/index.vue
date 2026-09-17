<template>
  <ContentWrap :title="isEdit ? '编辑合同' : '新建合同'">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="合同标题" prop="title">
            <el-input v-model="formData.title" placeholder="请输入合同标题" maxlength="200" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="合同分类" prop="typeId">
            <el-select
              v-model="formData.typeId"
              placeholder="请选择合同分类"
              class="w-full"
              :disabled="typeLocked"
              @change="handleTypeChange"
            >
              <el-option
                v-for="item in typeList"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="合同金额" prop="amount">
            <el-input-number
              v-model="formData.amount"
              :precision="2"
              :min="0"
              :controls="false"
              placeholder="请输入合同金额"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="币种" prop="currency">
            <el-select v-model="formData.currency" placeholder="请选择币种" class="w-full">
              <el-option
                v-for="item in currencyOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="签订日期" prop="signDate">
            <el-date-picker
              v-model="formData.signDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择签订日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker
              v-model="formData.effectiveDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择生效日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="到期日期" prop="expiryDate">
            <el-date-picker
              v-model="formData.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择到期日期"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="我方主体" prop="ourSideIds">
            <el-select
              v-model="formData.ourSideIds"
              multiple
              filterable
              placeholder="请选择我方主体"
              class="w-full"
            >
              <el-option
                v-for="item in ourSideList"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="相对方" prop="counterpartyIds">
            <el-select
              v-model="formData.counterpartyIds"
              multiple
              filterable
              placeholder="请选择相对方"
              class="w-full"
            >
              <el-option
                v-for="item in counterpartyList"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="说明" prop="description">
            <el-input
              v-model="formData.description"
              type="textarea"
              :rows="3"
              placeholder="请输入合同说明"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 扩展字段（按合同分类版本动态渲染） -->
    <el-divider content-position="left">扩展字段</el-divider>
    <div v-loading="schemaLoading">
      <form-create
        v-if="detailForm.rule.length"
        :rule="detailForm.rule"
        :option="detailForm.option"
        v-model="customData"
        v-model:api="fApi"
      />
      <el-empty v-else description="该合同分类无扩展字段" :image-size="60" />
    </div>

    <div class="mt-20px">
      <el-button type="primary" :loading="formLoading" @click="submitForm">
        {{ isEdit ? '保 存' : '保存草稿' }}
      </el-button>
      <el-button @click="handleBack">取 消</el-button>
    </div>
  </ContentWrap>
</template>

<script lang="ts" setup>
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { setConfAndFields2 } from '@/utils/formCreate'
import { useTagsViewStore } from '@/store/modules/tagsView'
import * as ContractApi from '@/api/clm/contract'
import * as ContractTypeApi from '@/api/clm/contractType'
import * as PartyApi from '@/api/clm/party'

defineOptions({ name: 'ClmContractCreate' })

const message = useMessage() // 消息弹窗
const { delView } = useTagsViewStore() // 视图操作
const { push, currentRoute } = useRouter() // 路由
const { query } = useRoute() // 查询参数

const editId = query.id ? Number(query.id) : undefined // 编辑时的合同编号
const isEdit = computed(() => !!editId)
// 合同起草跳转参数：预选分类 / 用分类范本生成正文 / 上传文件起草
const presetTypeId = !editId && query.typeId ? Number(query.typeId) : undefined
const useTemplate = !editId && String(query.useTemplate) === '1'
const uploadMode = !editId && String(query.upload) === '1'

const formLoading = ref(false) // 表单的加载中
const schemaLoading = ref(false) // 扩展字段的加载中
const formRef = ref() // 表单 Ref
const typeList = ref<ContractTypeApi.ContractTypeSimpleVO[]>([]) // 合同分类选项
const ourSideList = ref<PartyApi.PartySimpleVO[]>([]) // 我方主体选项
const counterpartyList = ref<PartyApi.PartySimpleVO[]>([]) // 相对方选项
const typeLocked = ref(false) // 编辑时，审批状态非"未提交"则不允许修改类型
const currencyOptions = [
  { label: '人民币 CNY', value: 'CNY' },
  { label: '美元 USD', value: 'USD' },
  { label: '欧元 EUR', value: 'EUR' }
]

const formData = ref({
  id: undefined as number | undefined,
  title: '',
  typeId: undefined as number | undefined,
  typeVersionId: undefined as number | undefined,
  approvalStatus: undefined as number | undefined,
  amount: undefined as number | undefined,
  currency: 'CNY',
  signDate: undefined as string | undefined,
  effectiveDate: undefined as string | undefined,
  expiryDate: undefined as string | undefined,
  ourSideIds: [] as number[],
  counterpartyIds: [] as number[],
  description: undefined as string | undefined
})
const formRules = reactive({
  title: [{ required: true, message: '合同标题不能为空', trigger: 'blur' }],
  typeId: [{ required: true, message: '合同分类不能为空', trigger: 'change' }],
  currency: [{ required: true, message: '币种不能为空', trigger: 'change' }],
  ourSideIds: [
    { required: true, type: 'array', min: 1, message: '至少选择一个我方主体', trigger: 'change' }
  ],
  counterpartyIds: [
    { required: true, type: 'array', min: 1, message: '至少选择一个相对方', trigger: 'change' }
  ]
})

// 扩展字段（form-create）
const detailForm = ref<{ rule: any[]; option: any }>({ rule: [], option: {} })
const customData = ref<Record<string, any>>({})
const fApi = ref<FormCreateApi>()

/** 按类型版本加载扩展字段 */
const loadSchema = async (versionId?: number, value?: Record<string, any>) => {
  detailForm.value = { rule: [], option: {} }
  customData.value = value ? { ...value } : {}
  if (!versionId) {
    return
  }
  schemaLoading.value = true
  try {
    const version = await ContractTypeApi.getContractTypeVersion(versionId)
    const fields = version?.formFields || []
    if (fields.length === 0) {
      return
    }
    setConfAndFields2(detailForm, version.formConf || '{}', fields)
    await nextTick()
    fApi.value?.btn.show(false) // 隐藏提交按钮
    fApi.value?.resetBtn.show(false)
  } finally {
    schemaLoading.value = false
  }
}

/** 合同分类变更：按当前发布版本渲染扩展字段 */
const handleTypeChange = async (typeId: number) => {
  const type = typeList.value.find((item) => item.id === typeId)
  await loadSchema(type?.currentVersionId)
}

/** 加载合同详情（编辑） */
const loadContract = async (id: number) => {
  formLoading.value = true
  try {
    const data = await ContractApi.getContract(id)
    const parties = (data.parties || []) as ContractApi.ContractPartyVO[]
    formData.value = {
      id: data.id,
      title: data.title,
      typeId: data.typeId,
      typeVersionId: data.typeVersionId,
      approvalStatus: data.approvalStatus,
      amount: data.amount,
      currency: data.currency || 'CNY',
      signDate: data.signDate,
      effectiveDate: data.effectiveDate,
      expiryDate: data.expiryDate,
      ourSideIds: parties.filter((p) => p.roleCode === 'OUR_SIDE').map((p) => p.partyId),
      counterpartyIds: parties.filter((p) => p.roleCode === 'COUNTERPARTY').map((p) => p.partyId),
      description: data.description
    }
    // 编辑既有合同时始终按创建时绑定的类型版本渲染，避免旧草稿静默切换到最新版本
    typeLocked.value = data.approvalStatus !== 0
    await loadSchema(data.typeVersionId, data.customData)
  } finally {
    formLoading.value = false
  }
}

/** 组装签约方 */
const buildParties = (): ContractApi.ContractPartyItemVO[] => {
  const parties: ContractApi.ContractPartyItemVO[] = []
  formData.value.ourSideIds.forEach((partyId, index) => {
    parties.push({ partyId, roleCode: 'OUR_SIDE', sort: index })
  })
  formData.value.counterpartyIds.forEach((partyId, index) => {
    parties.push({ partyId, roleCode: 'COUNTERPARTY', sort: index })
  })
  return parties
}

/** 提交表单 */
const submitForm = async () => {
  // 1. 校验表单
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (fApi.value) {
    try {
      await fApi.value.validate()
    } catch {
      message.warning('请完善扩展字段')
      return
    }
  }
  // 2. 提交请求
  formLoading.value = true
  try {
    const data: ContractApi.ContractVO & { useTypeTemplate?: boolean } = {
      id: formData.value.id,
      title: formData.value.title,
      typeId: formData.value.typeId,
      amount: formData.value.amount,
      currency: formData.value.currency,
      signDate: formData.value.signDate,
      effectiveDate: formData.value.effectiveDate,
      expiryDate: formData.value.expiryDate,
      description: formData.value.description,
      customData: detailForm.value.rule.length ? customData.value : {},
      parties: buildParties()
    }
    let id: number
    if (isEdit.value) {
      await ContractApi.updateContract(data)
      id = formData.value.id!
      message.success('保存成功')
    } else {
      if (useTemplate) {
        data.useTypeTemplate = true
      }
      id = await ContractApi.createContract(data)
      if (useTemplate) {
        message.success('创建成功，已用类型范本生成正文 v1')
      } else if (uploadMode) {
        message.success('创建成功，请在详情页上传合同正文')
      } else {
        message.success('创建成功')
      }
    }
    // 关闭当前 Tab，跳转详情
    delView(unref(currentRoute))
    await push({ name: 'ClmContractDetail', params: { id } })
  } finally {
    formLoading.value = false
  }
}

/** 返回 */
const handleBack = () => {
  delView(unref(currentRoute))
  if (isEdit.value) {
    push({ name: 'ClmContractDetail', params: { id: editId } })
  } else {
    push({ name: 'ClmContract' })
  }
}

/** 初始化 */
onMounted(async () => {
  formLoading.value = true
  try {
    const [types, ours, counters] = await Promise.all([
      ContractTypeApi.getContractTypeSimpleList(),
      PartyApi.getPartySimpleList(true),
      PartyApi.getPartySimpleList(false)
    ])
    typeList.value = types || []
    ourSideList.value = ours || []
    counterpartyList.value = counters || []
  } finally {
    formLoading.value = false
  }
  if (editId) {
    await loadContract(editId)
  } else if (presetTypeId && typeList.value.some((item) => item.id === presetTypeId)) {
    // 合同起草预选分类，并按分类当前发布版本加载扩展字段
    formData.value.typeId = presetTypeId
    await handleTypeChange(presetTypeId)
  }
})
</script>
