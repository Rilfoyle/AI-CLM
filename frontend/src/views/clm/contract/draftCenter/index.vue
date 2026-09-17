<template>
  <ContentWrap>
    <div class="flex flex-wrap items-start justify-between gap-12px">
      <div>
        <div class="text-20px font-bold text-[var(--el-text-color-primary)]">合同起草</div>
        <div class="mt-6px text-14px text-[var(--el-text-color-secondary)]">
          使用已发布标准模板，或上传一份已有合同正文创建草稿。草稿创建时不分配合同编号。
        </div>
      </div>
      <div class="flex gap-12px">
        <el-link type="primary" :underline="false" @click="goMyDrafts">
          <Icon icon="ep:edit-pen" class="mr-4px" /> 我的草稿
        </el-link>
        <el-link type="primary" :underline="false" @click="goLedger">
          <Icon icon="ep:tickets" class="mr-4px" /> 合同查询
        </el-link>
      </div>
    </div>

    <el-row :gutter="16" class="mt-20px">
      <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24" class="mb-16px">
        <el-card shadow="hover" class="draft-card" @click="scrollToTemplates">
          <div class="flex items-center gap-16px">
            <div class="draft-card-icon bg-[var(--el-color-primary-light-9)]">
              <Icon icon="ep:document-copy" :size="32" class="text-[var(--el-color-primary)]" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-16px font-bold">标准模板起草</div>
              <div class="mt-6px text-13px text-[var(--el-text-color-secondary)]">
                明确选择模板版本，生成合同草稿、主文档和初始修订。
              </div>
            </div>
            <Icon icon="ep:arrow-down" :size="18" class="text-[var(--el-text-color-placeholder)]" />
          </div>
        </el-card>
      </el-col>
      <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24" class="mb-16px">
        <el-card shadow="hover" class="draft-card" @click="openUploadDialog">
          <div class="flex items-center gap-16px">
            <div class="draft-card-icon bg-[var(--el-color-success-light-9)]">
              <Icon icon="ep:upload-filled" :size="32" class="text-[var(--el-color-success)]" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-16px font-bold">上传文件起草</div>
              <div class="mt-6px text-13px text-[var(--el-text-color-secondary)]">
                上传一份主正文，校验成功后直接进入合同工作区继续完善。
              </div>
            </div>
            <Icon
              icon="ep:arrow-right"
              :size="18"
              class="text-[var(--el-text-color-placeholder)]"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap ref="templateWrapRef">
    <div class="mb-16px flex flex-wrap items-center justify-between gap-8px">
      <div>
        <div class="text-16px font-bold">已发布模板</div>
        <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
          仅显示当前有效的已发布版本，创建后不会被后续模板升级静默覆盖。
        </div>
      </div>
      <el-form :inline="true" :model="templateQuery" class="-mb-18px" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="templateQuery.name"
            class="!w-220px"
            clearable
            placeholder="模板名称或编码"
            @keyup.enter="handleTemplateQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="templateQuery.contractTypeId"
            class="!w-190px"
            clearable
            placeholder="合同分类"
          >
            <el-option
              v-for="item in typeList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleTemplateQuery"
            ><Icon icon="ep:search" class="mr-5px" />查询</el-button
          >
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-if="templateError"
      class="mb-12px"
      type="warning"
      :closable="false"
      show-icon
      title="模板服务暂时无法加载"
    >
      <template #default>
        <el-link type="primary" :underline="false" @click="getTemplateList">重新加载</el-link>
      </template>
    </el-alert>

    <div v-loading="templateLoading">
      <el-row v-if="templateList.length" :gutter="12">
        <el-col
          v-for="item in templateList"
          :key="item.id"
          :xl="8"
          :lg="8"
          :md="12"
          :sm="24"
          :xs="24"
          class="mb-12px"
        >
          <el-card shadow="hover" class="h-full">
            <div class="flex h-full flex-col">
              <div class="flex items-start gap-10px">
                <Icon
                  icon="ep:document"
                  :size="24"
                  class="mt-2px shrink-0 text-[var(--el-color-primary)]"
                />
                <div class="min-w-0 flex-1">
                  <div class="truncate text-14px font-bold" :title="item.name">{{ item.name }}</div>
                  <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
                    {{ item.code }} · 版本 v{{ item.currentVersionNo }}
                  </div>
                  <div
                    class="mt-4px truncate text-12px text-[var(--el-text-color-secondary)]"
                    :title="item.fileName || ''"
                  >
                    {{ item.fileName || '模板正文' }}
                  </div>
                </div>
              </div>
              <div class="mt-14px text-right">
                <el-button type="primary" plain @click="openTemplateCreate(item)"
                  >使用此模板</el-button
                >
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else-if="!templateError" description="暂无可用的已发布模板" :image-size="80" />
    </div>
    <Pagination
      v-if="templateTotal > 0"
      v-model:page="templateQuery.pageNo"
      v-model:limit="templateQuery.pageSize"
      :total="templateTotal"
      @pagination="getTemplateList"
    />
  </ContentWrap>

  <Dialog v-model="templateCreateVisible" title="从标准模板创建草稿" width="520px">
    <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-width="90px">
      <el-form-item label="模板">
        <div>
          <div>{{ selectedTemplate?.name }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            {{ selectedTemplate?.code }} · v{{ selectedTemplate?.currentVersionNo }}
          </div>
        </div>
      </el-form-item>
      <el-form-item label="合同名称" prop="name">
        <el-input
          v-model="templateForm.name"
          maxlength="200"
          show-word-limit
          placeholder="请输入合同名称"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="templateCreateVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="createFromTemplate">创建草稿</el-button>
    </template>
  </Dialog>

  <Dialog v-model="uploadVisible" title="上传文件创建草稿" width="600px">
    <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="90px">
      <el-form-item label="合同名称" prop="name">
        <el-input
          v-model="uploadForm.name"
          maxlength="200"
          show-word-limit
          placeholder="请输入合同名称"
        />
      </el-form-item>
      <el-form-item label="合同分类" prop="contractTypeId">
        <el-select
          v-model="uploadForm.contractTypeId"
          class="w-full"
          filterable
          placeholder="请选择合同分类"
        >
          <el-option v-for="item in typeList" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="合同正文" prop="file">
        <el-upload
          drag
          class="w-full"
          :auto-upload="false"
          :limit="1"
          :file-list="uploadFileList"
          accept=".doc,.docx,.pdf"
          :on-change="handleUploadChange"
          :on-remove="handleUploadRemove"
        >
          <Icon
            icon="ep:upload-filled"
            :size="40"
            class="text-[var(--el-text-color-placeholder)]"
          />
          <div class="el-upload__text">拖入文件，或 <em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip"
              >支持 doc、docx、pdf，单文件不超过 50MB；加密或损坏文件由服务端校验。</div
            >
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="uploadVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="createFromUpload">创建草稿</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import * as ContractApi from '@/api/clm/contract'
import * as TemplateApi from '@/api/clm/template'
import * as ContractTypeApi from '@/api/clm/contractType'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'ClmContractDraftCenter' })

const message = useMessage()
const router = useRouter()
const userStore = useUserStore()
const templateWrapRef = ref()
const typeList = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])

const templateLoading = ref(false)
const templateError = ref(false)
const templateList = ref<TemplateApi.PublishedTemplateVO[]>([])
const templateTotal = ref(0)
const templateQuery = reactive<TemplateApi.PublishedTemplatePageReqVO>({
  pageNo: 1,
  pageSize: 9,
  name: undefined,
  contractTypeId: undefined
})

const getTemplateList = async () => {
  templateLoading.value = true
  templateError.value = false
  try {
    const data = await TemplateApi.getPublishedTemplatePage(templateQuery)
    templateList.value = data?.list || []
    templateTotal.value = data?.total || 0
  } catch {
    templateList.value = []
    templateTotal.value = 0
    templateError.value = true
  } finally {
    templateLoading.value = false
  }
}

const handleTemplateQuery = () => {
  templateQuery.pageNo = 1
  getTemplateList()
}

const scrollToTemplates = () => {
  const el = templateWrapRef.value?.$el as HTMLElement | undefined
  el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const selectedTemplate = ref<TemplateApi.PublishedTemplateVO>()
const templateCreateVisible = ref(false)
const templateFormRef = ref()
const templateForm = reactive({ name: '' })
const templateRules = { name: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }] }
const creating = ref(false)

const openTemplateCreate = (template: TemplateApi.PublishedTemplateVO) => {
  selectedTemplate.value = template
  templateForm.name = template.name
  templateCreateVisible.value = true
}

const createFromTemplate = async () => {
  if (!(await templateFormRef.value?.validate().catch(() => false)) || !selectedTemplate.value)
    return
  creating.value = true
  try {
    const contractId = await ContractApi.createContractFromTemplate({
      templateVersionId: selectedTemplate.value.currentVersionId,
      name: templateForm.name.trim(),
      ownerUserId: String(userStore.getUser.id)
    })
    message.success('草稿已创建')
    templateCreateVisible.value = false
    await router.push({ name: 'ClmContractDetail', params: { id: contractId } })
  } finally {
    creating.value = false
  }
}

const uploadVisible = ref(false)
const uploadFormRef = ref()
const uploadForm = reactive({
  name: '',
  contractTypeId: undefined as number | undefined,
  file: undefined as File | undefined
})
const uploadFileList = ref<UploadUserFile[]>([])
const uploadRules = {
  name: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }],
  contractTypeId: [{ required: true, message: '请选择合同分类', trigger: 'change' }],
  file: [{ required: true, message: '请选择合同正文', trigger: 'change' }]
}

const openUploadDialog = () => {
  uploadForm.name = ''
  uploadForm.contractTypeId = undefined
  uploadForm.file = undefined
  uploadFileList.value = []
  uploadVisible.value = true
}

const handleUploadChange = (file: UploadFile, files: UploadFiles) => {
  if (!file.raw) return
  if (file.raw.size > 50 * 1024 * 1024) {
    message.error('文件大小不能超过 50MB')
    uploadFileList.value = []
    uploadForm.file = undefined
    return
  }
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !['doc', 'docx', 'pdf'].includes(ext)) {
    message.error('仅支持 doc、docx、pdf 文件')
    uploadFileList.value = []
    uploadForm.file = undefined
    return
  }
  uploadForm.file = file.raw
  uploadFileList.value = files.slice(-1)
  if (!uploadForm.name) uploadForm.name = file.name.replace(/\.[^.]+$/, '')
  uploadFormRef.value?.validateField('file').catch(() => undefined)
}

const handleUploadRemove = () => {
  uploadForm.file = undefined
  uploadFileList.value = []
}

const createFromUpload = async () => {
  const valid = await uploadFormRef.value?.validate().catch(() => false)
  if (!valid || !uploadForm.file || !uploadForm.contractTypeId) return
  creating.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    formData.append('name', uploadForm.name.trim())
    formData.append('contractTypeId', String(uploadForm.contractTypeId))
    formData.append('ownerUserId', String(userStore.getUser.id))
    const contractId = await ContractApi.createContractFromUpload(formData)
    message.success('草稿已创建')
    uploadVisible.value = false
    await router.push({ name: 'ClmContractDetail', params: { id: contractId } })
  } finally {
    creating.value = false
  }
}

const goMyDrafts = () =>
  router.push({ path: '/clm/drafting/contract', query: { stageCode: 'DRAFT' } })
const goLedger = () => router.push('/clm/drafting/contract')

onMounted(async () => {
  try {
    typeList.value = (await ContractTypeApi.getContractTypeSimpleList()) || []
  } catch {
    typeList.value = []
  }
  getTemplateList()
})
</script>

<style lang="scss" scoped>
.draft-card {
  cursor: pointer;
  transition:
    box-shadow 0.2s,
    transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }
}

.draft-card-icon {
  display: flex;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}
</style>
