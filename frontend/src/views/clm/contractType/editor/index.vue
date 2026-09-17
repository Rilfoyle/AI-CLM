<template>
  <ContentWrap class="!mb-10px" :body-style="{ padding: '10px 16px' }">
    <el-form ref="formRef" :model="formData" :inline="true" class="-mb-15px">
      <el-form-item label="合同分类">
        <span class="font-bold">{{ typeName }}</span>
      </el-form-item>
      <el-form-item label="版本号">
        <el-tag v-if="version.versionNo" :type="readonly ? 'success' : 'info'">
          V{{ version.versionNo }}
        </el-tag>
        <dict-tag
          v-if="version.status !== undefined"
          class="ml-5px"
          :type="DICT_TYPE.CLM_TYPE_VERSION_STATUS"
          :value="version.status"
        />
      </el-form-item>
      <el-form-item label="说明" prop="remark">
        <el-input
          v-model="formData.remark"
          placeholder="请输入版本说明"
          :disabled="readonly"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          v-if="!readonly"
          type="primary"
          :loading="saving"
          @click="handleSave(false)"
          v-hasPermi="['clm:contract-type:update']"
        >
          <Icon icon="ep:check" class="mr-5px" /> 保存
        </el-button>
        <el-button
          v-if="!readonly"
          type="success"
          :loading="saving"
          @click="handleSave(true)"
          v-hasPermi="['clm:contract-type:publish']"
        >
          <Icon icon="ep:upload" class="mr-5px" /> 保存并发布
        </el-button>
        <el-button @click="close"><Icon icon="ep:back" class="mr-5px" /> 返回</el-button>
      </el-form-item>
    </el-form>
    <el-alert class="mt-10px" type="info" :closable="false" show-icon>
      <template #title>
        此页只维护合同页面字段；审批节点在“流程定义”中设计，合同选用哪套流程由“业务单据流程配置”统一决定。
        <el-button
          link
          type="primary"
          @click="push('/clm/approval-management/workflow-settings/process')"
        >
          去配置流程定义
        </el-button>
      </template>
    </el-alert>
  </ContentWrap>

  <ContentWrap :body-style="{ padding: '0px' }" class="!mb-0">
    <!-- 表单设计器 -->
    <div
      v-loading="loading"
      class="h-[calc(100vh-var(--top-tool-height)-var(--tags-view-height)-var(--app-content-padding)-var(--app-content-padding)-80px)]"
    >
      <fc-designer class="my-designer" ref="designer" :config="designerConfig" />
    </div>
  </ContentWrap>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import * as ContractTypeApi from '@/api/clm/contractType'
import FcDesigner from '@form-create/designer'
import { decodeFields, encodeConf, encodeFields, setConfAndFields } from '@/utils/formCreate'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { useFormCreateDesigner } from '@/components/FormCreate'

defineOptions({ name: 'ClmContractTypeEditor' })

/** 版本状态：1 已发布（与 ClmTypeVersionStatusEnum 一致） */
const VERSION_STATUS_PUBLISHED = 1

const message = useMessage() // 消息
const { push, currentRoute } = useRouter() // 路由
const { query } = useRoute() // 路由信息
const { delView } = useTagsViewStore() // 视图操作

// 只读：已发布版本仅预览。query.readonly 在挂载前即可确定，用于初始化设计器配置
const readonly = ref(query.readonly === '1' || query.readonly === 'true')

// 表单设计器配置
const designerConfig = ref({
  switchType: [], // 是否可以切换组件类型,或者可以相互切换的字段
  autoActive: true, // 是否自动选中拖入的组件
  useTemplate: false, // 是否生成vue2语法的模板组件
  formOptions: {
    form: {
      labelWidth: '100px' // 设置默认的 label 宽度为 100px
    }
  }, // 定义表单配置默认值
  fieldReadonly: readonly.value, // 配置field是否可以编辑
  hiddenDragMenu: readonly.value, // 隐藏拖拽操作按钮
  hiddenDragBtn: readonly.value, // 隐藏拖拽按钮
  hiddenMenu: [], // 隐藏部分菜单
  hiddenItem: [], // 隐藏部分组件
  hiddenItemConfig: {}, // 隐藏组件的部分配置项
  disabledItemConfig: {}, // 禁用组件的部分配置项
  showSaveBtn: false, // 是否显示保存按钮
  showConfig: !readonly.value, // 是否显示右侧的配置界面
  showBaseForm: true, // 是否显示组件的基础配置表单
  showControl: true, // 是否显示组件联动
  showPropsForm: true, // 是否显示组件的属性配置表单
  showEventForm: true, // 是否显示组件的事件配置表单
  showValidateForm: true, // 是否显示组件的验证配置表单
  showFormConfig: true, // 是否显示表单配置
  showInputData: true, // 是否显示录入按钮
  showDevice: true, // 是否显示多端适配选项
  appendConfigData: [] // 定义渲染规则所需的formData
})
const designer = ref() // 表单设计器
useFormCreateDesigner(designer) // 表单设计器增强

const loading = ref(false) // 加载中
const saving = ref(false) // 保存中
const version = ref<ContractTypeApi.ContractTypeVersionVO>({ processDefinitionKey: '' }) // 当前版本
const typeName = ref('') // 合同分类名称
const formRef = ref() // 顶部表单 Ref
const formData = ref({
  remark: ''
})

/** 保存（可选：保存后发布） */
const handleSave = async (publish: boolean) => {
  if (!version.value.id) return
  const valid = await formRef.value.validate()
  if (!valid) return
  if (publish) {
    try {
      await message.confirm('确认保存并发布该版本吗？发布后该版本将成为当前版本，且不可再修改。')
    } catch {
      return
    }
  }
  saving.value = true
  try {
    await ContractTypeApi.updateContractTypeVersion({
      id: version.value.id,
      formConf: encodeConf(designer), // 表单配置
      formFields: encodeFields(designer), // 表单字段
      // 兼容历史数据结构；流程定义的权威选择已经迁移到业务单据流程配置。
      processDefinitionKey: version.value.processDefinitionKey || 'clm_contract_approval_v1',
      remark: formData.value.remark
    })
    if (!publish) {
      message.success('保存成功')
      return
    }
    await ContractTypeApi.publishContractTypeVersion(version.value.id)
    message.success('发布成功')
    close()
  } finally {
    saving.value = false
  }
}

/** 返回页面布局配置列表 */
const close = () => {
  delView(unref(currentRoute))
  push('/clm/base-settings/page-layout')
}

/** 初始化 **/
onMounted(async () => {
  const id = Number(query.id)
  if (!id) {
    message.error('缺少版本编号参数')
    close()
    return
  }
  loading.value = true
  try {
    const data = await ContractTypeApi.getContractTypeVersion(id)
    version.value = data
    if (data.status === VERSION_STATUS_PUBLISHED) {
      readonly.value = true
    }
    formData.value.remark = data.remark || ''
    if (data.typeId) {
      const type = await ContractTypeApi.getContractType(data.typeId)
      typeName.value = type.name
    }
    // 回填设计器（首个草稿版本 formConf 为空时跳过）
    if (data.formConf) {
      setConfAndFields(designer, data.formConf, data.formFields || [])
    } else if (data.formFields?.length) {
      designer.value?.setRule(decodeFields(data.formFields))
    }
  } finally {
    loading.value = false
  }
})
</script>

<style>
.my-designer {
  ._fc-l,
  ._fc-m,
  ._fc-r {
    border-top: none;
  }
}
</style>
