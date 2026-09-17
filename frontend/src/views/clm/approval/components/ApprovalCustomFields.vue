<template>
  <div v-loading="schemaLoading" class="min-h-50px">
    <el-alert v-if="schemaError" type="warning" :closable="false" show-icon>
      <template #title>扩展字段加载失败</template>
      <template #default>
        <el-link type="primary" :underline="false" @click="loadSchema">重试</el-link>
      </template>
    </el-alert>
    <form-create
      v-else-if="detailForm.rule.length"
      v-model="modelValue"
      v-model:api="fApi"
      :rule="detailForm.rule"
      :option="detailForm.option"
    />
    <el-empty v-else-if="!schemaLoading" :description="emptyText" :image-size="50" />
  </div>
</template>

<script lang="ts" setup>
import type { Api as FormCreateApi } from '@form-create/element-ui'
import { setConfAndFields2 } from '@/utils/formCreate'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmApprovalCustomFields' })

const props = withDefaults(
  defineProps<{
    typeVersionId?: number
    editable?: boolean
    emptyText?: string
  }>(),
  {
    editable: false,
    emptyText: '该合同分类无扩展字段'
  }
)
const modelValue = defineModel<Record<string, any>>({ default: () => ({}) })

const schemaLoading = ref(false)
const schemaError = ref(false)
const detailForm = ref<{ rule: any[]; option: any }>({ rule: [], option: {} })
const fApi = ref<FormCreateApi>()
let schemaRequestId = 0

const applyEditability = async () => {
  await nextTick()
  fApi.value?.btn.show(false)
  fApi.value?.resetBtn.show(false)
  fApi.value?.disabled(!props.editable)
}

/** 按合同实际绑定的类型版本加载扩展字段。 */
const loadSchema = async () => {
  const requestId = ++schemaRequestId
  detailForm.value = { rule: [], option: {} }
  fApi.value = undefined
  schemaError.value = false
  if (!props.typeVersionId) {
    schemaLoading.value = false
    return
  }

  schemaLoading.value = true
  try {
    const version = await ContractTypeApi.getContractTypeVersion(props.typeVersionId)
    if (requestId !== schemaRequestId) return
    const fields = version?.formFields || []
    if (fields.length === 0) return
    setConfAndFields2(detailForm, version.formConf || '{}', fields)
    await applyEditability()
  } catch {
    if (requestId === schemaRequestId) {
      schemaError.value = true
    }
  } finally {
    if (requestId === schemaRequestId) {
      schemaLoading.value = false
    }
  }
}

/** 审批编辑时由弹窗显式校验扩展字段。 */
const validate = async () => {
  if (!props.editable) return true
  if (schemaLoading.value || schemaError.value) return false
  if (!detailForm.value.rule.length || !fApi.value) return true
  try {
    await fApi.value.validate()
    return true
  } catch {
    return false
  }
}

watch(() => props.typeVersionId, loadSchema, { immediate: true })
watch(() => props.editable, applyEditability)

defineExpose({ validate, reload: loadSchema })
</script>
