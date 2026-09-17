<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="140px"
    >
      <el-form-item label="主体归属" prop="internalFlag">
        <el-radio-group v-model="formData.internalFlag">
          <el-radio :value="true">我方主体</el-radio>
          <el-radio :value="false">相对方</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="签约方类型" prop="partyType">
        <el-radio-group v-model="formData.partyType">
          <el-radio
            v-for="dict in getIntDictOptions(DICT_TYPE.CLM_PARTY_TYPE)"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入企业名称或个人姓名" />
      </el-form-item>
      <el-form-item v-if="formData.internalFlag" label="我方主体简称" prop="shortName">
        <el-input
          v-model="formData.shortName"
          maxlength="64"
          placeholder="必填，将作为合同编号中的主体简称"
        />
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          已发布编码规则会直接使用该简称；请使用稳定且唯一的内部简称。
        </div>
      </el-form-item>
      <el-form-item label="统一社会信用代码" prop="unifiedCreditCode">
        <el-input
          v-model="formData.unifiedCreditCode"
          placeholder="企业请输入统一社会信用代码；个人可填证件号"
        />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input v-model="formData.contactName" placeholder="请输入联系人" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
      </el-form-item>
      <el-form-item label="地址" prop="address">
        <el-input v-model="formData.address" placeholder="请输入地址" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" placeholder="请输入备注" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import * as PartyApi from '@/api/clm/party'

defineOptions({ name: 'ClmPartyForm' })

/** 签约方类型：1 企业（与 ClmPartyTypeEnum 一致） */
const PARTY_TYPE_COMPANY = 1

const { t } = useI18n() // 国际化
const message = useMessage() // 消息弹窗

const dialogVisible = ref(false) // 弹窗的是否展示
const dialogTitle = ref('') // 弹窗的标题
const formLoading = ref(false) // 表单的加载中：1）修改时的数据加载；2）提交的按钮禁用
const formType = ref('') // 表单的类型：create - 新增；update - 修改
const formData = ref<PartyApi.PartyVO>({
  id: undefined,
  partyType: PARTY_TYPE_COMPANY,
  name: '',
  shortName: '',
  unifiedCreditCode: '',
  internalFlag: false,
  contactName: '',
  contactPhone: '',
  address: '',
  status: CommonStatusEnum.ENABLE,
  remark: ''
})
const formRules = reactive({
  internalFlag: [{ required: true, message: '主体归属不能为空', trigger: 'change' }],
  partyType: [{ required: true, message: '签约方类型不能为空', trigger: 'change' }],
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  shortName: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (formData.value.internalFlag && !value?.trim()) {
          callback(new Error('我方主体简称不能为空'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})
const formRef = ref() // 表单 Ref

/** 打开弹窗 */
const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  // 修改时，设置数据
  if (id) {
    formLoading.value = true
    try {
      formData.value = await PartyApi.getParty(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调
const submitForm = async () => {
  // 校验表单
  if (!formRef) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    const data = formData.value as PartyApi.PartyVO
    if (formType.value === 'create') {
      await PartyApi.createParty(data)
      message.success(t('common.createSuccess'))
    } else {
      await PartyApi.updateParty(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    // 发送操作成功的事件
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = {
    id: undefined,
    partyType: PARTY_TYPE_COMPANY,
    name: '',
    shortName: '',
    unifiedCreditCode: '',
    internalFlag: false,
    contactName: '',
    contactPhone: '',
    address: '',
    status: CommonStatusEnum.ENABLE,
    remark: ''
  }
  formRef.value?.resetFields()
}
</script>
