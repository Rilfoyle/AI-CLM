<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>模板管理</h2>
          <p>公司范本独立版本化；发布新版本不会静默改写已起草或已提交的合同。</p>
        </div>
        <el-button
          type="primary"
          @click="openDraftDialog()"
          v-hasPermi="['clm:governance:template:update']"
        >
          <Icon icon="ep:plus" class="mr-5px" />新建模板
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
        <el-form-item label="模板编码" prop="code">
          <el-input
            v-model="queryParams.code"
            placeholder="请输入编码"
            clearable
            class="!w-200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入名称"
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
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-150px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
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
        type="warning"
        :closable="false"
        title="模板治理服务暂时不可用"
        description="请确认后端已部署模板治理接口，然后重试。"
        show-icon
        class="mb-16px"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无模板">
        <el-table-column prop="code" label="模板编码" min-width="150" />
        <el-table-column prop="name" label="模板名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="合同分类" min-width="150">
          <template #default="scope">{{ typeName(scope.row.contractTypeId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
              {{ scope.row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前版本" width="110" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.currentVersionNo" type="success">
              V{{ scope.row.currentVersionNo }}
            </el-tag>
            <el-tag v-else type="info">未发布</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="currentFileName"
          label="当前文件"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openVersionDrawer(scope.row)">版本</el-button>
            <el-button
              link
              type="primary"
              @click="openDraftDialog(scope.row)"
              v-hasPermi="['clm:governance:template:update']"
            >
              {{ scope.row.draftVersionId ? '编辑草稿' : '新建版本' }}
            </el-button>
            <el-button
              v-if="scope.row.currentVersionId"
              link
              type="primary"
              @click="downloadVersion(scope.row.currentVersionId, scope.row.currentFileName)"
            >
              下载
            </el-button>
            <el-button
              v-if="scope.row.status === 1"
              link
              type="danger"
              @click="handleDisable(scope.row)"
              v-hasPermi="['clm:governance:template:publish']"
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

    <el-dialog v-model="draftDialogVisible" :title="draftTitle" width="620px" destroy-on-close>
      <el-form ref="draftFormRef" :model="draftForm" :rules="draftRules" label-width="100px">
        <el-form-item label="模板编码" prop="code">
          <el-input
            v-model="draftForm.code"
            maxlength="64"
            :disabled="Boolean(draftForm.templateId)"
          />
        </el-form-item>
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="draftForm.name" maxlength="120" />
        </el-form-item>
        <el-form-item label="合同分类" prop="contractTypeId">
          <el-select
            v-model="draftForm.contractTypeId"
            class="w-100%"
            :disabled="Boolean(draftForm.templateId)"
          >
            <el-option
              v-for="item in contractTypes"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="适用说明">
          <el-input
            v-model="draftForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="版本备注">
          <el-input v-model="draftForm.remark" maxlength="300" />
        </el-form-item>
        <el-form-item label="范本文件" :required="fileRequired">
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept=".doc,.docx,.pdf"
            :on-change="handleFileChange"
            :on-remove="() => (draftFile = undefined)"
          >
            <el-button><Icon icon="ep:upload" class="mr-5px" />选择文件</el-button>
            <template #tip>
              <div class="upload-tip">
                支持 DOC、DOCX、PDF；新模板或新版本必须上传，编辑现有草稿时可保留原文件。
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="draftDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="versionDrawerVisible" title="模板版本" size="720px">
      <el-skeleton v-if="versionLoading" :rows="6" animated />
      <template v-else>
        <el-descriptions v-if="currentTemplate" :column="2" border class="mb-16px">
          <el-descriptions-item label="模板">{{ currentTemplate.name }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ currentTemplate.code }}</el-descriptions-item>
          <el-descriptions-item label="适用类型">
            {{ typeName(currentTemplate.contractTypeId) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前发布版本">
            {{
              currentTemplate.currentVersionNo ? `V${currentTemplate.currentVersionNo}` : '未发布'
            }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="currentTemplate?.versions || []" empty-text="暂无版本">
          <el-table-column label="版本" width="80">
            <template #default="scope">V{{ scope.row.versionNo }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="versionTagType(scope.row.status)">
                {{ versionStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="fileName" label="文件" min-width="190" show-overflow-tooltip />
          <el-table-column prop="remark" label="版本备注" min-width="150" show-overflow-tooltip />
          <el-table-column label="发布时间" width="170">
            <template #default="scope">{{ formatNullableDate(scope.row.publishedTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                @click="downloadVersion(scope.row.id, scope.row.fileName)"
              >
                下载
              </el-button>
              <el-button
                v-if="scope.row.status === 'DRAFT'"
                link
                type="success"
                @click="handlePublish(scope.row.id)"
                v-hasPermi="['clm:governance:template:publish']"
              >
                发布
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { formatNullableDate } from '@/utils/formatTime'
import * as TemplateApi from '@/api/clm/governance/template'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmGovernanceTemplate' })

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<TemplateApi.GovernanceTemplateVO[]>([])
const total = ref(0)
const contractTypes = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<TemplateApi.GovernanceTemplatePageReqVO>({
  pageNo: 1,
  pageSize: 10
})

const draftDialogVisible = ref(false)
const saving = ref(false)
const draftFormRef = ref<FormInstance>()
const draftFile = ref<File>()
const draftForm = reactive<TemplateApi.GovernanceTemplateDraftForm>({
  code: '',
  name: '',
  contractTypeId: ''
})
const draftRules: FormRules = {
  code: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  contractTypeId: [{ required: true, message: '请选择合同分类', trigger: 'change' }]
}
const draftTitle = computed(() => {
  if (!draftForm.templateId) return '新建模板草稿'
  return draftForm.versionId ? '编辑模板草稿' : '新建模板版本'
})
const fileRequired = computed(() => !draftForm.templateId || !draftForm.versionId)

const versionDrawerVisible = ref(false)
const versionLoading = ref(false)
const currentTemplate = ref<TemplateApi.GovernanceTemplateVO>()

const typeName = (id?: string | number) => {
  return contractTypes.value.find((item) => String(item.id) === String(id))?.name || '-'
}

const versionStatusText = (status: TemplateApi.GovernanceTemplateVersionStatus) => {
  return { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已失效' }[status]
}

const versionTagType = (status: TemplateApi.GovernanceTemplateVersionStatus) => {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await TemplateApi.getGovernanceTemplatePage(queryParams)
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

const resetDraft = () => {
  Object.assign(draftForm, {
    templateId: undefined,
    versionId: undefined,
    code: '',
    name: '',
    contractTypeId: '',
    description: '',
    remark: ''
  })
  draftFile.value = undefined
}

const openDraftDialog = async (row?: TemplateApi.GovernanceTemplateVO) => {
  resetDraft()
  if (row) {
    let detail = row
    try {
      detail = await TemplateApi.getGovernanceTemplate(row.id)
    } catch {
      // 列表字段足够继续创建版本，详情失败不阻断。
    }
    Object.assign(draftForm, {
      templateId: detail.id,
      versionId: detail.draftVersionId,
      code: detail.code,
      name: detail.name,
      contractTypeId: detail.contractTypeId,
      description: detail.description || '',
      remark:
        detail.versions?.find((version) => String(version.id) === String(detail.draftVersionId))
          ?.remark || ''
    })
  }
  draftDialogVisible.value = true
  await nextTick()
  draftFormRef.value?.clearValidate()
}

const handleFileChange = (file: UploadFile) => {
  draftFile.value = file.raw
}

const saveDraft = async () => {
  if (!(await draftFormRef.value?.validate())) return
  if (fileRequired.value && !draftFile.value) {
    message.warning('请上传本次模板版本文件')
    return
  }
  saving.value = true
  try {
    await TemplateApi.saveGovernanceTemplateDraft({ ...draftForm, file: draftFile.value })
    message.success('模板草稿已保存')
    draftDialogVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const openVersionDrawer = async (row: TemplateApi.GovernanceTemplateVO) => {
  versionDrawerVisible.value = true
  versionLoading.value = true
  currentTemplate.value = undefined
  try {
    currentTemplate.value = await TemplateApi.getGovernanceTemplate(row.id)
  } finally {
    versionLoading.value = false
  }
}

const handlePublish = async (id: string | number) => {
  await message.confirm('发布后将成为新起草合同可选版本，历史合同不会被改写。确认发布？')
  await TemplateApi.publishGovernanceTemplateVersion(id)
  message.success('模板版本已发布')
  versionDrawerVisible.value = false
  await getList()
}

const handleDisable = async (row: TemplateApi.GovernanceTemplateVO) => {
  await message.confirm(`停用“${row.name}”后将不可用于新起草，确认继续？`)
  await TemplateApi.disableGovernanceTemplate(row.id)
  message.success('模板已停用')
  await getList()
}

const downloadVersion = async (versionId: string | number, fileName?: string) => {
  const blob = await TemplateApi.getGovernanceTemplateFile(versionId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || `template-${versionId}`
  link.click()
  URL.revokeObjectURL(url)
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
.upload-tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
