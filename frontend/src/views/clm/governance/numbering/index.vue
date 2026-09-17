<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>编码规则设置</h2>
          <p>合同编号在首次成功提交时永久分配；退回、拒绝或撤回后重提沿用且不回收。</p>
        </div>
        <el-button
          type="primary"
          @click="openRuleDialog()"
          v-hasPermi="['clm:governance:numbering:update']"
        >
          <Icon icon="ep:plus" class="mr-5px" />新建规则
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input
            v-model="queryParams.ruleCode"
            placeholder="请输入编码"
            clearable
            class="!w-220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="合同分类" prop="contractTypeId">
          <el-select
            v-model="queryParams.contractTypeId"
            placeholder="全部类型"
            clearable
            class="!w-220px"
          >
            <el-option
              v-for="item in contractTypes"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-160px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已失效" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        title="编号治理服务暂时不可用"
        description="请确认后端已部署编码规则接口，然后重试。"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无编码规则">
        <el-table-column prop="ruleCode" label="规则编码" min-width="160" />
        <el-table-column label="适用合同分类" min-width="160">
          <template #default="scope">{{ typeName(scope.row.contractTypeId) }}</template>
        </el-table-column>
        <el-table-column label="编号结构" min-width="230">
          <template #default="scope">
            <span class="formula-text">{{ formulaText(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="样例" min-width="180">
          <template #default="scope">
            <code class="sample-code">{{ scope.row.sample || '-' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80" align="center">
          <template #default="scope">V{{ scope.row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{
              statusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.effectiveTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="openRuleDialog(scope.row)"
              v-hasPermi="['clm:governance:numbering:update']"
            >
              {{ scope.row.status === 'DRAFT' ? '编辑草稿' : '新建版本' }}
            </el-button>
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              link
              type="success"
              @click="handlePublish(scope.row)"
              v-hasPermi="['clm:governance:numbering:publish']"
            >
              发布
            </el-button>
            <el-button
              v-if="scope.row.status === 'PUBLISHED'"
              link
              type="danger"
              @click="handleDisable(scope.row)"
              v-hasPermi="['clm:governance:numbering:publish']"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </ContentWrap>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="650px" destroy-on-close>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="rules" label-width="110px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input v-model="ruleForm.ruleCode" maxlength="64" :disabled="Boolean(sourceRule)" />
        </el-form-item>
        <el-form-item label="合同分类" prop="contractTypeId">
          <el-select
            v-model="ruleForm.contractTypeId"
            class="!w-100%"
            clearable
            placeholder="全部类型"
          >
            <el-option
              v-for="item in contractTypes"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <div class="field-tip">留空表示全局兜底规则；发布时后端会校验冲突。</div>
        </el-form-item>
        <el-form-item label="固定前缀" prop="prefix">
          <el-input v-model="ruleForm.prefix" maxlength="20" placeholder="例如 HT" />
        </el-form-item>
        <el-form-item label="我方主体简称">
          <div class="party-segment-row">
            <el-switch v-model="ruleForm.includePartyShortName" disabled />
            <span>必选号段；正式提交时从合同我方主体读取简称，缺失会明确阻断。</span>
          </div>
        </el-form-item>
        <el-form-item label="日期格式" prop="datePattern">
          <el-select
            v-model="ruleForm.datePattern"
            class="!w-100%"
            clearable
            placeholder="不使用日期段"
          >
            <el-option label="年份（yyyy）" value="yyyy" />
            <el-option label="年月（yyyyMM）" value="yyyyMM" />
            <el-option label="年月日（yyyyMMdd）" value="yyyyMMdd" />
          </el-select>
        </el-form-item>
        <el-form-item label="分隔符" prop="separator">
          <el-radio-group v-model="ruleForm.separator">
            <el-radio-button value="-">短横线 -</el-radio-button>
            <el-radio-button value="_">下划线 _</el-radio-button>
            <el-radio-button value="">无</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="流水号长度" prop="sequenceLength">
          <el-input-number v-model="ruleForm.sequenceLength" :min="3" :max="10" />
        </el-form-item>
        <el-form-item label="序列重置" prop="resetPeriod">
          <el-radio-group v-model="ruleForm.resetPeriod">
            <el-radio value="NONE">不重置</el-radio>
            <el-radio value="YEAR">每年</el-radio>
            <el-radio value="MONTH">每月</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="编号样例">
          <div class="preview-box">
            <div class="preview-content">
              <el-input
                v-model="previewPartyShortName"
                maxlength="20"
                placeholder="样例主体简称"
                class="!w-150px"
              />
              <code>{{ previewSample || '点击“预览样例”由服务端生成' }}</code>
            </div>
            <el-button link type="primary" :loading="previewing" @click="handlePreview">
              预览样例
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="previewing" @click="handlePreview">预览样例</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { formatNullableDate } from '@/utils/formatTime'
import * as NumberingApi from '@/api/clm/governance/numbering'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmGovernanceNumbering' })

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<NumberingApi.NumberingRuleVO[]>([])
const total = ref(0)
const contractTypes = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<NumberingApi.NumberingRulePageReqVO>({ pageNo: 1, pageSize: 10 })

const dialogVisible = ref(false)
const saving = ref(false)
const previewing = ref(false)
const previewSample = ref('')
const sourceRule = ref<NumberingApi.NumberingRuleVO>()
const ruleFormRef = ref<FormInstance>()
const ruleForm = reactive<NumberingApi.NumberingRuleDraftReqVO>({
  ruleCode: '',
  contractTypeId: undefined,
  prefix: 'HT',
  datePattern: 'yyyyMM',
  separator: '-',
  sequenceLength: 5,
  resetPeriod: 'MONTH',
  includePartyShortName: true
})
const previewPartyShortName = ref('灵犀')
const rules: FormRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  prefix: [{ required: true, message: '请输入固定前缀', trigger: 'blur' }],
  sequenceLength: [{ required: true, message: '请输入流水号长度', trigger: 'change' }],
  resetPeriod: [{ required: true, message: '请选择重置周期', trigger: 'change' }]
}
const dialogTitle = computed(() => {
  if (!sourceRule.value) return '新建编码规则'
  return sourceRule.value.status === 'DRAFT' ? '编辑编码规则草稿' : '新建编码规则版本'
})

const typeName = (id?: string | number) => {
  if (!id) return '全部类型'
  return contractTypes.value.find((item) => String(item.id) === String(id))?.name || `类型 ${id}`
}

const statusText = (status: NumberingApi.NumberingRuleStatus) => {
  return { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已失效' }[status]
}

const statusTagType = (status: NumberingApi.NumberingRuleStatus) => {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'
}

const formulaText = (row: NumberingApi.NumberingRuleVO) => {
  const parts = [
    row.prefix,
    row.includePartyShortName ? '主体简称' : '',
    row.datePattern,
    '#'.repeat(row.sequenceLength)
  ].filter(Boolean)
  return parts.join(row.separator)
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await NumberingApi.getNumberingRulePage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } catch {
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const openRuleDialog = async (row?: NumberingApi.NumberingRuleVO) => {
  sourceRule.value = row
  previewSample.value = row?.sample || ''
  Object.assign(ruleForm, {
    id: row?.status === 'DRAFT' ? row.id : undefined,
    ruleCode: row?.ruleCode || '',
    contractTypeId: row?.contractTypeId,
    prefix: row?.prefix || 'HT',
    datePattern: row?.datePattern || 'yyyyMM',
    separator: row?.separator ?? '-',
    sequenceLength: row?.sequenceLength || 5,
    resetPeriod: row?.resetPeriod || 'MONTH',
    includePartyShortName: true
  })
  dialogVisible.value = true
  await nextTick()
  ruleFormRef.value?.clearValidate()
}

const handlePreview = async () => {
  if (!(await ruleFormRef.value?.validate())) return
  previewing.value = true
  try {
    previewSample.value = (
      await NumberingApi.previewNumberingRule({
        ...ruleForm,
        ourPartyShortName: previewPartyShortName.value.trim()
      })
    ).sample
  } finally {
    previewing.value = false
  }
}

const saveDraft = async () => {
  if (!(await ruleFormRef.value?.validate())) return
  saving.value = true
  try {
    await NumberingApi.saveNumberingRuleDraft({ ...ruleForm })
    message.success('编码规则草稿已保存')
    dialogVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const handlePublish = async (row: NumberingApi.NumberingRuleVO) => {
  await message.confirm(`发布规则“${row.ruleCode}”V${row.versionNo}？发布时将校验适用范围冲突。`)
  await NumberingApi.publishNumberingRule(row.id)
  message.success('编码规则已发布')
  await getList()
}

const handleDisable = async (row: NumberingApi.NumberingRuleVO) => {
  await message.confirm(`停用规则“${row.ruleCode}”后，新提交将不再命中该版本。确认继续？`)
  await NumberingApi.disableNumberingRule(row.id)
  message.success('编码规则已停用')
  await getList()
}

onMounted(async () => {
  try {
    contractTypes.value = (await ContractTypeApi.getContractTypeSimpleList()) || []
  } catch {
    contractTypes.value = []
  }
  await getList()
})
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-header p,
.field-tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.formula-text,
.sample-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.sample-code {
  color: var(--el-color-primary);
}

.preview-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.preview-content,
.party-segment-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.party-segment-row span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
