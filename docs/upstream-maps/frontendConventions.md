# Frontend conventions for a new `clm` feature area (`D:\dev\clm\frontend`)

All paths relative to frontend repo root.

---

## 1. API helper — `src/config/axios`

Files: `src/config/axios/index.ts` (default export `request`), `service.ts` (axios instance + interceptors), `config.ts`, `errorCode.ts`.

**`src/config/axios/index.ts` — full method set (quoted):**
```ts
export default {
  get:          async <T = any>(option: any) => { const res = await request({ method: 'GET', ...option });  return res.data as unknown as T },
  post:         async <T = any>(option: any) => { const res = await request({ method: 'POST', ...option }); return res.data as unknown as T },
  postOriginal: async (option: any)          => { const res = await request({ method: 'POST', ...option }); return res },
  delete:       async <T = any>(option: any) => { const res = await request({ method: 'DELETE', ...option }); return res.data as unknown as T },
  put:          async <T = any>(option: any) => { const res = await request({ method: 'PUT', ...option });  return res.data as unknown as T },
  download:     async <T = any>(option: any) => { const res = await request({ method: 'GET', responseType: 'blob', ...option }); return res as unknown as Promise<T> },
  upload:       async <T = any>(option: any) => { option.headersType = 'multipart/form-data'; const res = await request({ method: 'POST', ...option }); return res as unknown as Promise<T> }
}
```
Option shape (`types/global.d.ts` `AxiosConfig`): `{ url, params, data, method, headersType, responseType, headers }`. `get/post/put/delete` unwrap to `res.data` (i.e. the `data` field of `CommonResult`); `download`/`upload` return the raw response (blob / `{code,data,msg}`).

**Base URL** — `src/config/axios/config.ts`:
```ts
base_url: import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL,
result_code: 200,
request_timeout: 30000,
default_headers: 'application/json'
```
`.env.local` (exact values):
```
VITE_BASE_URL='http://localhost:48080'
VITE_API_URL=/admin-api
VITE_UPLOAD_TYPE=server
```
→ effective baseURL `http://localhost:48080/admin-api`. Api modules therefore write `url: '/clm/xxx/page'` (no `/admin-api` prefix). Vite dev proxy is commented out in `vite.config.ts` (backend handles CORS).

**Auth / tenant headers** — `service.ts` request interceptor:
```ts
if (getAccessToken() && isToken) config.headers.Authorization = 'Bearer ' + getAccessToken()
if (tenantEnable && tenantEnable === 'true') {
  const tenantId = getTenantId(); if (tenantId) config.headers['tenant-id'] = tenantId
  const visitTenantId = getVisitTenantId()
  if (config.headers.Authorization && visitTenantId) config.headers['visit-tenant-id'] = visitTenantId
}
```
- `tenantEnable = import.meta.env.VITE_APP_TENANT_ENABLE` (`.env`: `VITE_APP_TENANT_ENABLE=true`).
- whitelist skipping token: `const whiteList: string[] = ['/login', '/refresh-token']`; per-request opt-out via `headers: { isToken: false }`.
- params serialized with `qs.stringify(params, { allowDots: true })`.
- Response interceptor: 401 → silent refresh via `/system/auth/refresh-token`; `code !== 0 && code !== 200` → `ElNotification.error` + reject; blob/arraybuffer passthrough for exports.
- Optional API encryption via header `isEncrypt` (`src/utils/encrypt.ts`, `.env` `VITE_APP_API_ENCRYPT_*`).

---

## 2. Example API module — `src/api/infra/config/index.ts` (quoted in full)

```ts
import request from '@/config/axios'

export interface ConfigVO {
  id: number | undefined
  category: string
  name: string
  key: string
  value: string
  type: number
  visible: boolean
  remark: string
  createTime: Date
}

// 查询参数列表
export const getConfigPage = (params: PageParam) => {
  return request.get({ url: '/infra/config/page', params })
}
// 查询参数详情
export const getConfig = (id: number) => {
  return request.get({ url: '/infra/config/get?id=' + id })
}
// 新增参数
export const createConfig = (data: ConfigVO) => {
  return request.post({ url: '/infra/config/create', data })
}
// 修改参数
export const updateConfig = (data: ConfigVO) => {
  return request.put({ url: '/infra/config/update', data })
}
// 删除参数
export const deleteConfig = (id: number) => {
  return request.delete({ url: '/infra/config/delete?id=' + id })
}
// 批量删除参数
export const deleteConfigList = (ids: number[]) => {
  return request.delete({ url: '/infra/config/delete-list', params: { ids: ids.join(',') } })
}
// 导出参数
export const exportConfig = (params) => {
  return request.download({ url: '/infra/config/export-excel', params })
}
```
`PageParam` is a global type (`types/global.d.ts`): `{ pageSize?: number; pageNo?: number }`; `PageResult<T> = { list: T; total: number }`. Smaller variant: `src/api/bpm/leave/index.ts` uses `export type LeaveVO = {...}` and `async` + `await request.get(...)`.

---

## 3. List page + Form dialog skeleton — `src/views/infra/config/index.vue` + `ConfigForm.vue`

**`index.vue` template skeleton:**
```vue
<template>
  <doc-alert title="配置中心" url="https://doc.iocoder.cn/config-center/" />
  <ContentWrap>
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="68px">
      <el-form-item label="参数名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入参数名称" clearable
                  @keyup.enter="handleQuery" class="!w-240px" />
      </el-form-item>
      <el-form-item label="系统内置" prop="type">
        <el-select v-model="queryParams.type" placeholder="请选择系统内置" clearable class="!w-240px">
          <el-option v-for="dict in getIntDictOptions(DICT_TYPE.INFRA_CONFIG_TYPE)"
                     :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker v-model="queryParams.createTime" value-format="YYYY-MM-DD HH:mm:ss" type="daterange"
          start-placeholder="开始日期" end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]" class="!w-240px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['infra:config:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button type="success" plain @click="handleExport" :loading="exportLoading"
                   v-hasPermi="['infra:config:export']">
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="参数名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="系统内置" align="center" prop="type">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.INFRA_CONFIG_TYPE" :value="scope.row.type" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="openForm('update', scope.row.id)"
                     v-hasPermi="['infra:config:update']">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(scope.row.id)"
                     v-hasPermi="['infra:config:delete']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo"
                v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <ConfigForm ref="formRef" @success="getList" />
</template>
```
**`index.vue` script skeleton:**
```ts
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import * as ConfigApi from '@/api/infra/config'
import ConfigForm from './ConfigForm.vue'

defineOptions({ name: 'InfraConfig' })       // must match backend menu componentName / keepAlive

const message = useMessage()                  // auto-imported
const { t } = useI18n()                       // auto-imported

const loading = ref(true); const total = ref(0); const list = ref([])
const queryParams = reactive({ pageNo: 1, pageSize: 10, name: undefined, type: undefined, createTime: [] })
const queryFormRef = ref(); const exportLoading = ref(false)

const getList = async () => {
  loading.value = true
  try { const data = await ConfigApi.getConfigPage(queryParams); list.value = data.list; total.value = data.total }
  finally { loading.value = false }
}
const handleQuery = () => { queryParams.pageNo = 1; getList() }
const resetQuery = () => { queryFormRef.value.resetFields(); handleQuery() }

const formRef = ref()
const openForm = (type: string, id?: number) => { formRef.value.open(type, id) }

const handleDelete = async (id: number) => {
  try { await message.delConfirm(); await ConfigApi.deleteConfig(id)
        message.success(t('common.delSuccess')); await getList() } catch {}
}
const handleExport = async () => {
  try { await message.exportConfirm(); exportLoading.value = true
        const data = await ConfigApi.exportConfig(queryParams); download.excel(data, '参数配置.xls')
  } catch {} finally { exportLoading.value = false }
}
onMounted(() => { getList() })
</script>
```
**`ConfigForm.vue` skeleton (dialog convention):**
```vue
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="80px">
      <el-form-item label="参数名称" prop="name"><el-input v-model="formData.name" placeholder="请输入参数名称" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
<script lang="ts" setup>
defineOptions({ name: 'InfraConfigForm' })
const { t } = useI18n(); const message = useMessage()
const dialogVisible = ref(false); const dialogTitle = ref(''); const formLoading = ref(false)
const formType = ref(''); const formData = ref({ id: undefined, name: '' })
const formRules = reactive({ name: [{ required: true, message: '参数名称不能为空', trigger: 'blur' }] })
const formRef = ref()

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)     // 'action.create' / 'action.update'
  formType.value = type
  resetForm()
  if (id) { formLoading.value = true
    try { formData.value = await ConfigApi.getConfig(id) } finally { formLoading.value = false } }
}
defineExpose({ open })
const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef) return
  const valid = await formRef.value.validate(); if (!valid) return
  formLoading.value = true
  try {
    const data = formData.value as ConfigApi.ConfigVO
    if (formType.value === 'create') { await ConfigApi.createConfig(data); message.success(t('common.createSuccess')) }
    else { await ConfigApi.updateConfig(data); message.success(t('common.updateSuccess')) }
    dialogVisible.value = false; emit('success')
  } finally { formLoading.value = false }
}
</script>
```
Dict helpers: `getIntDictOptions`, `getStrDictOptions`, `getBoolDictOptions`, `getDictOptions`, `getDictObj`, `getDictLabel`, enum `DICT_TYPE` — all in `src/utils/dict.ts` (`DICT_TYPE` is auto-imported globally; the getters must be imported explicitly).

---

## 4. Routing — dynamic from backend menus

- Static routes: `src/router/index.ts` (`createWebHistory(import.meta.env.VITE_BASE_PATH)`) with `src/router/modules/remaining.ts`.
- Dynamic routes: `src/store/modules/permission.ts` → `generateRoutes()` reads menus from `wsCache.get(CACHE_KEY.ROLE_ROUTERS)` and calls `generateRoute(res)`.

**Glob + component mapping — `src/utils/routerHelper.ts`:**
```ts
const modules = import.meta.glob('../views/**/*.{vue,tsx}')
...
export const generateRoute = (routes: AppCustomRouteRecordRaw[]): AppRouteRecordRaw[] => {
  const modulesRoutesKeys = Object.keys(modules)
  ...
      // 对后端传 component 组件路径和不传做兼容
      const index = route?.component
        ? modulesRoutesKeys.findIndex((ev) => ev.includes(route.component))
        : modulesRoutesKeys.findIndex((ev) => ev.includes(route.path))
      data.component = modules[modulesRoutesKeys[index]]
```
So it is **substring matching on the glob keys** (`../views/infra/config/index.vue`), *not* string concat `'views/' + component + '.vue'`. Backend `system_menu.component` = e.g. `clm/contract/index` → matches `../views/clm/contract/index.vue`. `system_menu.component_name` maps to `route.componentName` and is used as the route `name` (keepAlive requires it to equal the page's `defineOptions({ name })`); otherwise the name is derived by `toCamelCase(route.path, true)`.

Also in the same file:
```ts
export const registerComponent = (componentPath: string) => {   // 例:/bpm/oa/leave/detail
  for (const item in modules) if (item.includes(componentPath)) return defineAsyncComponent(modules[item])
}
```
Meta from menu: `title: route.name, icon: route.icon, hidden: !route.visible, noCache: !route.keepAlive, alwaysShow`. `route.path` may carry `?query`/`#hash`/`:params` (parsed by `src/utils/routeParams.ts` → `meta.query`/`meta.params`/`meta.hash`).

**Non-menu / hidden detail pages are static** in `src/router/modules/remaining.ts` under the `/bpm` parent (`meta: { hidden: true }`), e.g.:
```ts
{
  path: 'process-instance/detail',
  component: () => import('@/views/bpm/processInstance/detail/index.vue'),
  name: 'BpmProcessInstanceDetail',
  meta: { noCache: true, hidden: true, canTo: true, title: '流程详情', activeMenu: '/bpm/task/my' },
  props: (route) => ({ id: route.query.id, taskId: route.query.taskId, activityId: route.query.activityId })
},
{ path: 'oa/leave/create', component: () => import('@/views/bpm/oa/leave/create.vue'), name: 'OALeaveCreate',
  meta: { noCache: true, hidden: true, canTo: true, title: '发起 OA 请假', activeMenu: '/bpm/oa/leave' } },
{ path: 'oa/leave/detail', component: () => import('@/views/bpm/oa/leave/detail.vue'), name: 'OALeaveDetail',
  meta: { noCache: true, hidden: true, canTo: true, title: '查看 OA 请假', activeMenu: '/bpm/oa/leave' } },
{ path: 'manager/model/:type/:id', component: () => import('@/views/bpm/model/form/index.vue'),
  name: 'BpmModelUpdate', meta: { noCache: true, hidden: true, canTo: true, title: '修改流程', activeMenu: '/bpm/manager/model' } }
```
Note: `oa/leave/detail` uses **query** (`?id=`), not a path param; path params are used only by `manager/model/:type/:id`. `meta.activeMenu` keeps the parent menu highlighted; `canTo: true` allows navigation to a hidden route. For a `clm` area, add an analogous `{ path: '/clm', component: Layout, name: 'clm', meta: { hidden: true }, children: [...] }` block in `remaining.ts`.

---

## 5. FormCreate

**(a) Designer — `src/views/bpm/form/editor/index.vue`**
```vue
<fc-designer class="my-designer" ref="designer" :config="designerConfig">
  <template #handle>
    <el-button size="small" type="success" plain @click="handleSave">
      <Icon class="mr-5px" icon="ep:plus" /> 保存
    </el-button>
  </template>
</fc-designer>
```
```ts
import FcDesigner from '@form-create/designer'
import { encodeConf, encodeFields, setConfAndFields } from '@/utils/formCreate'
import { useFormCreateDesigner } from '@/components/FormCreate'

const designerConfig = ref({ switchType: [], autoActive: true, useTemplate: false,
  formOptions: { form: { labelWidth: '100px' } }, fieldReadonly: false, hiddenDragMenu: false,
  hiddenMenu: [], hiddenItem: [], showSaveBtn: false, showConfig: true, /* ... */ appendConfigData: [] })
const designer = ref()
useFormCreateDesigner(designer)     // 表单设计器增强（注入上传/字典/用户/部门/富文本等规则）

// 保存
data.conf = encodeConf(designer)     // 表单配置 → JSON string
data.fields = encodeFields(designer) // 表单字段 → string[]
// 加载
setConfAndFields(designer, data.conf, data.fields)
```
`useFormCreateDesigner(designer: Ref)` is exported from `src/components/FormCreate/index.ts` (impl `src/components/FormCreate/src/useFormCreateDesigner.ts`); it calls `designer.value?.removeMenuItem('upload')`, `removeMenuItem('fcEditor')` and appends custom rules from `src/components/FormCreate/src/config`.

**(b) Runtime render — `src/views/bpm/processInstance/create/ProcessDefinitionDetail.vue`**
```vue
<form-create
  v-if="detailForm.rule.length"
  :rule="detailForm.rule"
  v-model:api="fApi"
  v-model="detailForm.value"
  :option="detailForm.option"
  @submit="submitForm"
/>
```
```ts
import { decodeFields, setConfAndFields2 } from '@/utils/formCreate'
import type { Api as FormCreateApi } from '@form-create/element-ui'

const detailForm: any = ref({ rule: [], option: {}, value: {} })
const fApi = ref<FormCreateApi>()
...
setConfAndFields2(detailForm, row.formConf, row.formFields, formVariables)
await nextTick()
fApi.value?.btn.show(false)            // 隐藏提交按钮
// 字段权限：fApi.value?.disabled(true, field) / fApi.value?.hidden(true, field)
// 提交：await fApi.value.validate(); variables: detailForm.value.value
```
**`src/utils/formCreate.ts` — all exports (quoted fully):**
```ts
import { isRef } from 'vue'
import formCreate from '@form-create/element-ui'

/** 编码表单 Conf */
export const encodeConf = (designerRef: object) => {
  // @ts-ignore
  return formCreate.toJson(designerRef.value.getOption())
}
/** 解码表单 Conf */
export const decodeConf = (conf: string) => { return formCreate.parseJson(conf) }
/** 编码表单 Fields */
export const encodeFields = (designerRef: object) => {
  // @ts-ignore
  const rule = designerRef.value.getRule()
  const fields: string[] = []
  rule.forEach((item: any) => { fields.push(formCreate.toJson(item)) })
  return fields
}
/** 解码表单 Fields */
export const decodeFields = (fields: string[]) => {
  const rule: object[] = []
  fields.forEach((item) => { rule.push(formCreate.parseJson(item)) })
  return rule
}
/** 设置表单的 Conf 和 Fields，适用 FcDesigner 场景 */
export const setConfAndFields = (designerRef: object, conf: string, fields: string[]) => {
  // @ts-ignore
  designerRef.value.setOption(decodeConf(conf))
  // @ts-ignore
  designerRef.value.setRule(decodeFields(fields))
}
/** 设置表单的 Conf 和 Fields，适用 form-create 场景 */
export const setConfAndFields2 = (detailPreview: object, conf: string, fields: string[], value?: object) => {
  if (isRef(detailPreview)) { /* @ts-ignore */ detailPreview = detailPreview.value }
  // @ts-ignore
  detailPreview.option = decodeConf(conf)
  // @ts-ignore
  detailPreview.rule = decodeFields(fields)
  if (value) { /* @ts-ignore */ detailPreview.value = value }
}
```

**(c) Global registration — `src/plugins/formCreate/index.ts`**
```ts
export const setupFormCreate = (app: App<Element>) => {
  components.forEach((component) => { app.component(component.name!, component) })
  formCreate.use(install)     // '@form-create/element-ui/auto-import'
  app.use(formCreate)         // <form-create>
  app.use(FcDesigner)         // <fc-designer>
}
```
Custom components registered for FormCreate: `UploadImg`, `UploadImgs`, `UploadFile`, `DictSelect`, `UserSelect` (`useApiSelect({ name:'UserSelect', labelField:'nickname', valueField:'id', url:'/system/user/simple-list' })`), `DeptSelect`, `ApiSelect` (`useApiSelect({ name: 'ApiSelect' })`), `Editor`, `IframeComponent`, `AreaSelect`, plus assorted Element Plus components (`ElAlert, ElTransfer, ElTable, ElTabs, ElTreeSelect, ElCollapse, ElCard, …`).

---

## 6. UploadFile — `src/components/UploadFile/src/UploadFile.vue`

`defineOptions({ name: 'UploadFile' })`; barrel `src/components/UploadFile/index.ts` exports `{ UploadImg, UploadImgs, UploadFile }`.

Props (`propTypes` from `@/utils/propTypes`):
```ts
modelValue: propTypes.oneOfType<string | string[]>([String, Array<String>]).isRequired,
fileType:   propTypes.array.def(['doc', 'xls', 'ppt', 'txt', 'pdf']),
fileSize:   propTypes.number.def(5),      // MB
limit:      propTypes.number.def(5),
autoUpload: propTypes.bool.def(true),
drag:       propTypes.bool.def(false),
isShowTip:  propTypes.bool.def(true),
disabled:   propTypes.bool.def(false),
directory:  propTypes.string.def(undefined)
```
Emits: `['update:modelValue']` only (no `success`/`change`).

**Value shape** — `emitUpdateModelValue()`:
```ts
let result: string | string[] = fileList.value.map((file) => file.url!)
if (props.limit === 1 || isString(props.modelValue)) { result = result.join(',') }
emit('update:modelValue', result)
```
→ with `:limit="1"` (or when bound to a string) the model is a **comma-joined URL string** (single URL); otherwise a `string[]` of URLs. The watcher accepts both (splits on `,`). Only the URL is stored — never a raw `File`.

Real usages: `src/views/erp/finance/payment/FinancePaymentForm.vue:76` `<UploadFile :is-show-tip="false" v-model="formData.fileUrl" :limit="1" />`; `src/views/crm/followup/FollowUpRecordForm.vue:46` `<UploadFile v-model="formData.fileUrls" class="min-w-80px" />`.

**Upload path / custom upload** — `src/components/UploadFile/src/useUpload.ts`:
```ts
export const getUploadUrl = (): string =>
  import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL + '/infra/file/upload'
export const useUpload = (directory?: string) => { ... return { uploadUrl, httpRequest } }
```
`UploadFile` binds `:action="uploadUrl"` **and** `:http-request="httpRequest"` — so el-upload's default XHR is overridden. `httpRequest` branches on `VITE_UPLOAD_TYPE`: `client` → `FileApi.getFilePresignedUrl(fileName, directory)` + `axios.put(presignedInfo.uploadUrl, options.file, ...)` then `FileApi.createFile(...)`, resolving `{ data: presignedInfo.url }`; `server` (the `.env.local` default) → `FileApi.updateFile({ file: options.file, directory }, uploadProgressHandler)` → `POST /admin-api/infra/file/upload`. There is **no prop to inject a custom `http-request` or to get the raw `File` back** — to do that, import `useUpload` directly in your own `<el-upload>` (pattern used by `src/views/ai/knowledge/document/form/UploadStep.vue`, `src/views/ai/chat/index/components/message/MessageFileUpload.vue`), or use `el-upload` with `:auto-upload="false"` + `@change` (pattern in `src/views/bpm/model/ModelImportForm.vue`).

---

## 7. Download — `src/utils/download.ts`

Default export object; methods: `excel(data: Blob, fileName: string)`, `word`, `zip`, `html`, `markdown`, `json` (all `(data: Blob, fileName: string)`), `image({ url, canvasWidth?, canvasHeight?, drawWithImageSize? })`, `base64Image(base64: string, fileName: string)`, `base64ToFile(base64: any, fileName: string): File`. Internal `download0(data, fileName, mineType)` builds a Blob + anchor click.

Convention:
```ts
// api
export const exportConfig = (params) => request.download({ url: '/infra/config/export-excel', params })
// view
import download from '@/utils/download'
const data = await ConfigApi.exportConfig(queryParams)
download.excel(data, '参数配置.xls')
```
Wrap in `await message.exportConfirm()` and toggle `exportLoading`.

---

## 8. Global components / hooks / helpers

- **Auto-registration is by unplugin-vue-components**, not `src/components/index.ts`. `build/vite/index.ts`:
```ts
Components({
  dts: !isBuild && 'src/types/auto-components.d.ts',
  resolvers: [ElementPlusResolver()],
  globs: ['src/components/**/**.{vue, md}', '!src/components/DiyEditor/components/mobile/**']
})
```
  So every `.vue` under `src/components/**` is usable in templates with no import (`ContentWrap`, `Dialog`, `Pagination`, `DictTag`/`dict-tag`, `UploadFile`, `Editor`, `Descriptions`, `Table`, `Form`, `DocAlert`/`doc-alert`, …). `src/components/index.ts` only registers `Icon` explicitly:
```ts
export const setupGlobCom = (app: App<Element>): void => { app.component('Icon', Icon) }
```
- **Prop names:**
  - `ContentWrap`: `title: string = ''`, `message: string = ''`, `bodyStyle: object = { padding: '10px', overflow: 'hidden' }`.
  - `Dialog`: `modelValue: bool = false`, `title: string = 'Dialog'`, `fullscreen: bool = true`, `width: string|number = '40%'`, `scroll: bool = false`, `maxHeight: string|number = '400px'`, `loading: bool = false`; slots `default` + `#footer`.
  - `Pagination`: `total (required Number)`, `page = 1`, `limit = 20`, `pagerCount`; emits `['update:page','update:limit','pagination']` → use `v-model:page` / `v-model:limit` / `@pagination="getList"`.
  - `DictTag`: `type: String (required)`, `value: [String,Number,Boolean,Array] (required)`, `separator = ','`, `gutter = '5px'`.
  - `Icon`: `icon="ep:search"` (Iconify) or `icon="svg-icon:xxx"`.
- **Auto-imported functions** (`build/vite/index.ts` AutoImport `imports`): all of `vue` and `vue-router` (`ref/reactive/computed/watch/onMounted/useRouter/useRoute/…`), plus `useI18n` (`@/hooks/web/useI18n`), `useMessage` (`@/hooks/web/useMessage`), `useTable`, `useCrudSchemas`, `required` (`@/utils/formRules`), `DICT_TYPE` (`@/utils/dict`). Generated d.ts: `src/types/auto-imports.d.ts` / `src/types/auto-components.d.ts` (dev only; not committed).
- **`useMessage()`** (`src/hooks/web/useMessage.ts`) returns: `info, error, success, warning, alert, alertError, alertSuccess, alertWarning, notify, notifyError, notifySuccess, notifyWarning, confirm(content, tip?)`, plus (further down the file) `delConfirm`, `exportConfirm`, `prompt` — usage: `const message = useMessage()`.
- **Router**: `const { push, currentRoute } = useRouter()` or `const router = useRouter()`; `const { query } = useRoute()` / `const route = useRoute()` (auto-imported; some files still `import { useRoute } from 'vue-router'`).
- **`src/utils/formatTime.ts`** exports: `formatDate(date: dayjs.ConfigType, format?: string): string`, `formatNullableDate`, `dateFormatter(_row, _column: TableColumnCtx<any>, cellValue): string` (default `YYYY-MM-DD HH:mm:ss`), `dateFormatter2` (date only), `getNowDateTime`, `formatPast`, `formatPast2`, `formatSeconds`, `beginOfDay`, `endOfDay`, `betweenDay`, `addTime`, `convertDate`, `isSameDay`, `getDayRange`, `getLast7Days`, `getLast30Days`, `getLast1Year`, `getDateRange`, `defaultShortcuts`. Table usage: `:formatter="dateFormatter"`.
- **Directives** (`src/directives/index.ts`): `setupAuth(app)` registers `v-hasPermi` (button permissions, array of permission strings) and `v-hasRole`; `setupMountedFocus` registers `v-mountedFocus`.

---

## 9. BPM entry points & navigation

| Purpose | File | Route |
|---|---|---|
| Model list | `src/views/bpm/model/index.vue` | dynamic menu, `/bpm/manager/model` |
| Model create/edit (tabs incl. SIMPLE designer) | `src/views/bpm/model/form/index.vue` (+ `BasicInfo.vue`, `FormDesign.vue`, `ProcessDesign.vue`, `ExtraSettings.vue`, `PrintTemplate/Index.vue`) | static `/bpm/manager/model/create` (`BpmModelCreate`), `/bpm/manager/model/:type/:id` (`BpmModelUpdate`) |
| BPMN editor | `src/views/bpm/model/form/editor/index.vue` (`MyProcessDesigner` + `MyProcessPenal` from `@/components/bpmnProcessDesigner/package`) | embedded in `ProcessDesign.vue` |
| SIMPLE designer components | `src/components/SimpleProcessDesignerV2/` | — |
| Form designer (FcDesigner) | `src/views/bpm/form/editor/index.vue` | static `/bpm/manager/form/edit?id=` (`BpmFormEditor`) |
| Form list | `src/views/bpm/form/index.vue` | `/bpm/manager/form` |
| Todo tasks | `src/views/bpm/task/todo/index.vue` (also `done/`, `copy/`, `manager/`) | dynamic menu `/bpm/task/my` etc. |
| Process instance detail | `src/views/bpm/processInstance/detail/index.vue` | static `/bpm/process-instance/detail?id=&taskId=&activityId=` (`BpmProcessInstanceDetail`) |
| Start a process | `src/views/bpm/processInstance/create/index.vue` + `ProcessDefinitionDetail.vue` | dynamic menu |
| My/manager instances | `src/views/bpm/processInstance/index.vue`, `manager/index.vue`, `report/index.vue` | — |

Navigation is **by route `name` + `query`**, e.g. `src/views/bpm/task/todo/index.vue`:
```ts
/** 处理审批按钮 */
const handleAudit = (row: any) => {
  push({
    name: 'BpmProcessInstanceDetail',
    query: { id: row.processInstance.id, taskId: row.id }
  })
}
```
and `src/views/bpm/model/index.vue`:
```ts
const openForm = (type: string, id?: number) => {
  if (type === 'create') { push({ name: 'BpmModelCreate' }) }
  else { push({ name: 'BpmModelUpdate', params: { id } }) }
}
```
and `src/views/bpm/oa/leave/index.vue`: `router.push({ name: 'OALeaveDetail', query: { id: row.id } })`, `router.push({ name: 'OALeaveCreate' })`, re-submit `router.push({ name: 'OALeaveCreate', query: { id: row.id } })`. The URL path form is `/bpm/process-instance/detail?id=...&taskId=...`.

---

## 10. Lint / TS

- **`eslint.config.mjs`** (flat config, `typescript-eslint` + `eslint-plugin-vue` `flat/recommended` + `@unocss/eslint-config/flat`). Deliberately permissive:
  - `'@typescript-eslint/no-unused-vars': 'off'` and `'no-unused-vars': 'off'` → **unused vars are NOT an error**.
  - Also off: `no-explicit-any`, `ban-ts-comment`, `no-empty-function`, `no-non-null-assertion`, `explicit-function-return-type`, `no-unused-expressions`, `vue/multi-word-component-names`, `vue/require-default-prop`, `vue/no-v-html`, `vue/html-indent`, `@unocss/order`.
  - Errors that remain: `vue/html-self-closing` (`html.void: 'always'`, `html.normal: 'never'`, `component: 'always'`, `svg/math: 'always'`) plus the rest of `flat/recommended`.
  - Ignored: `build/`, `config/`, `dist*/`, `*.js`, `*.mjs` (except `eslint.config.mjs`), `node_modules/`, `src/main.ts`, `src/types/auto-components.d.ts`, `src/components/Tinyflow/ui/**`.
- **`prettier.config.js`**: `printWidth: 100`, `tabWidth: 2`, `useTabs: false`, **`semi: false`**, **`singleQuote: true`**, `jsxSingleQuote: false`, `quoteProps: 'as-needed'`, `bracketSpacing: true`, `trailingComma: 'none'`, `arrowParens: 'always'`, `proseWrap: 'never'`, `htmlWhitespaceSensitivity: 'strict'`, `endOfLine: 'auto'`, `vueIndentScriptAndStyle: false`.
- **Scripts** (`package.json`):
  - `"lint": "pnpm lint:eslint:check && pnpm lint:style:check && pnpm lint:format:check"` (ESLint on `./src` + Stylelint on `./src/**/*.{vue,less,postcss,css,scss}` + `prettier --check` on `src/**/*.{js,ts,json,tsx,css,less,scss,vue,html,md}`).
  - `"lint:eslint": "eslint --fix ./src …"`, `"lint:format": "prettier --write …"`, `"lint:style": "stylelint --fix …"`, `"lint:lint-staged": "lint-staged"` (`lint-staged.config.mjs`).
  - `"ts:check": "node --max_old_space_size=8192 ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit --incremental --tsBuildInfoFile node_modules/.cache/vue-tsc/tsconfig.tsbuildinfo"`.
  - Stylelint config: `stylelint.config.js` (standard + scss/html + `stylelint-order`).
- **`tsconfig.json`**: `strict: true`, but `noImplicitAny: false`, `strictFunctionTypes: false`, `skipLibCheck: true`; **`noUnusedLocals: true` and `noUnusedParameters: true`** → unused locals *are* errors under `pnpm ts:check` (not under ESLint). Paths `@/* → src/*`.
- **Does `pnpm lint` pass on a clean checkout?** Not verified — I did not run it (read-only task). Configuration-wise it should: the repo is a vendored yudao-ui-admin-vue3 v2026.07 checkout formatted with these exact prettier/eslint configs, all noisy TS rules are disabled, and caches are written to `node_modules/.cache/*`. No CI config or lint report file exists in the repo to confirm, and `git` metadata shows no local modifications tracked here. Treat "passes" as unverified.