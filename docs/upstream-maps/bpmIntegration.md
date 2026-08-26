# BPM 业务表单（CUSTOM form）集成模式 — 参考文档

## 1. `yudao-module-bpm` 的 api 包

包路径 `yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/api/`，共 3 个子包：

### `api/task/BpmProcessInstanceApi.java`
```java
public interface BpmProcessInstanceApi {
    String createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO);
}
```
实现：`api/task/BpmProcessInstanceApiImpl.java`（`@Service @Validated`，委托 `BpmProcessInstanceService`）。

### `api/task/BpmProcessTaskApi.java`
```java
public interface BpmProcessTaskApi {
    void triggerTask(@NotEmpty(message = "流程实例的编号不能为空") String processInstanceId,
                     @NotEmpty(message = "任务 Key 不能为空") String taskDefineKey);
}
```
实现：`api/task/BpmProcessTaskApiImpl.java`（委托 `BpmTaskService#triggerTask`）。

### `api/task/dto/BpmProcessInstanceCreateReqDTO.java`（`@Data`，全部字段）
| 字段 | 类型 | 校验 |
|---|---|---|
| `processDefinitionKey` | `String` | `@NotEmpty("流程定义的标识不能为空")` |
| `variables` | `Map<String, Object>` | — |
| `businessKey` | `String` | `@NotEmpty("业务的唯一标识")` |
| `startUserSelectAssignees` | `Map<String, List<Long>>` | — （key=taskKey，value=审批人 userId 数组） |

注意：DTO 没有显式 `@Accessors(chain = true)`，但项目 lombok.config 使用链式 setter（`.setProcessDefinitionKey(...)` 可用，见 OA 示例）。

### `api/event/` — 见第 2 节。`api/package-info.java` 仅注释。
除以上两个接口外，api 包**没有**其它接口（无 form/model/definition 的 API）。

---

## 2. OA 请假示例

### 2.1 Controller — `yudao-module-bpm/.../controller/admin/oa/BpmOALeaveController.java`
`@RestController @RequestMapping("/bpm/oa/leave")`
| 端点 | 方法签名 | 权限 |
|---|---|---|
| `POST /create` | `CommonResult<Long> createLeave(@Valid @RequestBody BpmOALeaveCreateReqVO createReqVO)` | `bpm:oa-leave:create` |
| `GET /get?id=` | `CommonResult<BpmOALeaveRespVO> getLeave(@RequestParam("id") Long id)` | `bpm:oa-leave:query` |
| `GET /page` | `CommonResult<PageResult<BpmOALeaveRespVO>> getLeavePage(@Valid BpmOALeavePageReqVO pageVO)` | `bpm:oa-leave:query` |

`BpmOALeaveCreateReqVO` 关键字段：`startTime`/`endTime`(LocalDateTime)、`type`(Integer)、`reason`(String)、`startUserSelectAssignees` (`Map<String, List<Long>>`)。

DO `bpm_oa_leave`（`dal/dataobject/oa/BpmOALeaveDO.java`）列：`id, user_id, type, reason, start_time, end_time, day, status, process_instance_id` + BaseDO。`status` 复用 `BpmTaskStatusEnum` / `BpmProcessInstanceStatusEnum` 数值。

### 2.2 ServiceImpl — `.../service/oa/BpmOALeaveServiceImpl.java`（必须逐字照抄的模式）
```java
    /**
     * OA 请假对应的流程定义 KEY
     */
    public static final String PROCESS_KEY = "oa_leave";

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLeave(Long userId, BpmOALeaveCreateReqVO createReqVO) {
        // 插入 OA 请假单
        long day = LocalDateTimeUtil.between(createReqVO.getStartTime(), createReqVO.getEndTime()).toDays();
        BpmOALeaveDO leave = BeanUtils.toBean(createReqVO, BpmOALeaveDO.class)
                .setUserId(userId).setDay(day).setStatus(BpmTaskStatusEnum.RUNNING.getStatus());
        leaveMapper.insert(leave);

        // 发起 BPM 流程
        Map<String, Object> processInstanceVariables = new HashMap<>();
        processInstanceVariables.put("day", day);
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                        .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(leave.getId()))
                        .setStartUserSelectAssignees(createReqVO.getStartUserSelectAssignees()));

        // 将工作流的编号，更新到 OA 请假单中
        leaveMapper.updateById(new BpmOALeaveDO().setId(leave.getId()).setProcessInstanceId(processInstanceId));
        return leave.getId();
    }

    @Override
    public void updateLeaveStatus(Long id, Integer status) {
        validateLeaveExists(id);
        leaveMapper.updateById(new BpmOALeaveDO().setId(id).setStatus(status));
    }
```
关键点：先插业务单 → `businessKey = String.valueOf(业务主键)` → 回写 `processInstanceId`；整个方法在一个事务里。

### 2.3 状态监听器 — `.../service/oa/listener/BpmOALeaveStatusListener.java`（全文）
```java
package cn.iocoder.yudao.module.bpm.service.oa.listener;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOALeaveService;
import cn.iocoder.yudao.module.bpm.service.oa.BpmOALeaveServiceImpl;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
public class BpmOALeaveStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private BpmOALeaveService leaveService;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmOALeaveServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        leaveService.updateLeaveStatus(Long.parseLong(event.getBusinessKey()), event.getStatus());
    }

}
```

### 2.4 基类 — `api/event/BpmProcessInstanceStatusEventListener.java`（全文）
```java
public abstract class BpmProcessInstanceStatusEventListener
        implements ApplicationListener<BpmProcessInstanceStatusEvent> {

    @Override
    public final void onApplicationEvent(BpmProcessInstanceStatusEvent event) {
        if (!StrUtil.equals(event.getProcessDefinitionKey(), getProcessDefinitionKey())) {
            return;
        }
        onEvent(event);
    }

    protected abstract String getProcessDefinitionKey();

    protected abstract void onEvent(BpmProcessInstanceStatusEvent event);
}
```
匹配机制：Spring 把事件广播给**所有**监听器，基类用 `StrUtil.equals(event.getProcessDefinitionKey(), getProcessDefinitionKey())` 过滤；不相等直接 return。`onApplicationEvent` 是 `final`，子类只实现 2 个 protected 方法。

### 2.5 事件 — `api/event/BpmProcessInstanceStatusEvent.java`（extends `ApplicationEvent`，`@Data`）
字段：`String id`（流程实例编号，`@NotNull`）、`String processDefinitionKey`（`@NotNull`）、`Integer status`（`@NotNull`）、`String reason`、`String businessKey`。构造器仅 `BpmProcessInstanceStatusEvent(Object source)`。

### 2.6 businessKey 从哪来
1. 发起：`BpmProcessInstanceServiceImpl#createProcessInstance0`（`service/task/BpmProcessInstanceServiceImpl.java:823-826`）把它写进 Flowable：
```java
        ProcessInstanceBuilder processInstanceBuilder = runtimeService.createProcessInstanceBuilder()
                .processDefinitionId(definition.getId())
                .businessKey(businessKey)
                .variables(variables);
```
（走 DTO 路径时 `createProcessInstance(Long, BpmProcessInstanceCreateReqDTO)` 用 `getActiveProcessDefinition(key)` 查定义，并包在 `FlowableUtils.executeAuthenticatedUserId(userId, ...)` 内。）
2. 结束/状态变更：`BpmProcessInstanceServiceImpl.java:1019-1020`
```java
        processInstanceEventPublisher.sendProcessInstanceResultEvent(
                BpmProcessInstanceConvert.INSTANCE.buildProcessInstanceStatusEvent(this, instance, status, reason));
```
3. 组装：`convert/task/BpmProcessInstanceConvert.java:126-130`
```java
    default BpmProcessInstanceStatusEvent buildProcessInstanceStatusEvent(Object source, ProcessInstance instance,
                                                                          Integer status, String reason) {
        return new BpmProcessInstanceStatusEvent(source).setId(instance.getId()).setStatus(status).setReason(reason)
                .setProcessDefinitionKey(instance.getProcessDefinitionKey()).setBusinessKey(instance.getBusinessKey());
    }
```
即 businessKey 来自 Flowable `ProcessInstance#getBusinessKey()`，业务侧再 `Long.parseLong(...)` 还原为业务主键。发布者：`framework/flowable/core/event/BpmProcessInstanceEventPublisher.java`（同步 `publisher.publishEvent(event)`，与流程事务同线程）。

---

## 3. 前端如何打开业务表单流程

### 3.1 后端字段
`dal/dataobject/definition/BpmProcessDefinitionInfoDO.java`（表 `bpm_process_definition_info`）：`form_type`（枚举 `BpmModelFormTypeEnum`）、`form_id`、`form_conf`、`form_fields`、`form_custom_create_path`、`form_custom_view_path`、`simple_model`、`visible`、`sort`、`start_user_ids`、`start_dept_ids`、`manager_user_ids`、`allow_cancel_running_process`、`allow_withdraw_task`、`process_id_rule`、`auto_approval_type`、`title_setting`、`summary_setting`、`process_before/after_trigger_setting`、`task_before/after_trigger_setting`、`print_template_setting`。

`controller/admin/definition/vo/model/BpmModelMetaInfoVO.java`：
```java
    @Schema(description = "自定义表单的提交路径，使用 Vue 的路由地址", example = "/bpm/oa/leave/create")
    private String formCustomCreatePath; // 表单类型为 CUSTOM 时，必须非空
    @Schema(description = "自定义表单的查看路径，使用 Vue 的路由地址", example = "/bpm/oa/leave/view")
    private String formCustomViewPath; // 表单类型为 CUSTOM 时，必须非空
```

`enums/definition/BpmModelFormTypeEnum.java`：`NORMAL(10, "流程表单")`（对应 BpmFormDO）、`CUSTOM(20, "业务表单")`。前端常量 `src/utils/constants.ts:457` `BpmModelFormType = { NORMAL: 10, CUSTOM: 20 }`。

### 3.2 发起页（CUSTOM 跳转）
- `src/views/bpm/processInstance/create/ProcessDefinitionDetail.vue:126-161`
```js
  if (row.formType == BpmModelFormType.NORMAL) { ... }
  // 情况二：业务表单
  } else if (row.formCustomCreatePath) {
    await push({
      path: row.formCustomCreatePath
    })
    // 这里暂时无需加载流程图，因为跳出到另外个 Tab；
  }
```
- 重新发起（`src/views/bpm/processInstance/index.vue:277-293`）：
```js
    if (processDefinitionDetail.formType === 20) {
      await router.push({
        path: processDefinitionDetail.formCustomCreatePath,
        query: {
          id: row.businessKey            // 业务主键，供业务 create 页回填
        }
      })
    } else if (processDefinitionDetail.formType === 10) { ... name: 'BpmProcessInstanceCreate' ... }
```
- 流程定义列表 `src/views/bpm/model/definition/index.vue:143-145` 与 `src/views/bpm/model/CategoryDraggableModel.vue:150` 同样 `push({ path: row.formCustomCreatePath })`。

### 3.3 详情页渲染 formCustomViewPath（动态组件机制）
机制在 `src/utils/routerHelper.ts:13-26`（**唯一**的 `import.meta.glob` + `defineAsyncComponent` 注册器）：
```ts
const modules = import.meta.glob('../views/**/*.{vue,tsx}')
/**
 * 注册一个异步组件
 * @param componentPath 例:/bpm/oa/leave/detail
 */
export const registerComponent = (componentPath: string) => {
  for (const item in modules) {
    if (item.includes(componentPath)) {
      // 使用异步组件的方式来动态加载组件
      // @ts-ignore
      return defineAsyncComponent(modules[item])
    }
  }
}
```
调用处 `src/views/bpm/processInstance/detail/index.vue:203, 254-257`：
```js
const BusinessFormComponent = ref<any>(null) // 异步组件
...
    } else {
      // 注意：data.processDefinition.formCustomViewPath 是组件的全路径，例如说：/crm/contract/detail/index.vue
      BusinessFormComponent.value = registerComponent(data.processDefinition.formCustomViewPath)
    }
```
模板（同文件 `:63-66`）——**传入的 props 只有 `id`，值为 `processInstance.businessKey`**：
```html
                      <!-- 情况二：业务表单 -->
                      <div v-if="processDefinition?.formType === BpmModelFormType.CUSTOM">
                        <BusinessFormComponent :id="processInstance.businessKey" />
                      </div>
```
打印弹窗同样复用：`src/views/bpm/processInstance/detail/PrintDialog.vue:79-83`
```js
const initBusinessFormComponent = () => {
  const businessFormPath =
    printData.value?.processInstance?.processDefinition?.formCustomViewPath || ''
  BusinessFormComponent.value = businessFormPath ? registerComponent(businessFormPath) : undefined
}
```
任务详情（待办/已办）不单独渲染业务表单，统一路由到 `BpmProcessInstanceDetail`（`src/views/bpm/processInstance/detail/index.vue`）。

### 3.4 `src/views/bpm/oa/leave/detail.vue`（全文）
```vue
<template>
  <ContentWrap>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="请假类型">
        <dict-tag :type="DICT_TYPE.BPM_OA_LEAVE_TYPE" :value="detailData.type" />
      </el-descriptions-item>
      <el-descriptions-item label="开始时间">
        {{ formatDate(detailData.startTime, 'YYYY-MM-DD') }}
      </el-descriptions-item>
      <el-descriptions-item label="结束时间">
        {{ formatDate(detailData.endTime, 'YYYY-MM-DD') }}
      </el-descriptions-item>
      <el-descriptions-item label="原因">
        {{ detailData.reason }}
      </el-descriptions-item>
    </el-descriptions>
  </ContentWrap>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import { propTypes } from '@/utils/propTypes'
import * as LeaveApi from '@/api/bpm/leave'

defineOptions({ name: 'BpmOALeaveDetail' })

const { query } = useRoute() // 查询参数

const props = defineProps({
  id: propTypes.number.def(undefined)
})
const detailLoading = ref(false) // 表单的加载中
const detailData = ref<any>({}) // 详情数据
const queryId = query.id as unknown as number // 从 URL 传递过来的 id 编号

/** 获得数据 */
const getInfo = async () => {
  detailLoading.value = true
  try {
    detailData.value = await LeaveApi.getLeave(props.id || queryId)
  } finally {
    detailLoading.value = false
  }
}
defineExpose({ open: getInfo }) // 提供 open 方法，用于打开弹窗

/** 初始化 **/
onMounted(() => {
  getInfo()
})
</script>
```
要点：**必须同时支持 `props.id`（被 BusinessFormComponent 内嵌时）与 `route.query.id`（独立路由打开时）**：`props.id || queryId`。

### 3.5 `src/views/bpm/oa/leave/create.vue` 关键点（全文已核对，摘录 script 要害）
```js
const processDefineKey = 'oa_leave' // 流程定义 Key
const startUserSelectTasks = ref<StartUserSelectTask[]>([])      // 发起人需要选择审批人的用户任务列表
const startUserSelectAssignees = ref<Record<string, number[]>>({})
const tempStartUserSelectAssignees = ref<Record<string, number[]>>({})
const activityNodes = ref<ProcessInstanceApi.ApprovalNodeInfo[]>([])
const processDefinitionId = ref('')
```
- `onMounted`：`await DefinitionApi.getProcessDefinition(undefined, processDefineKey)` → 若为空 `message.error('OA 请假的流程模型未配置，请检查！')`；否则 `processDefinitionId.value = detail.id`、`startUserSelectTasks.value = detail.startUserSelectTasks`；`if (query.id) await getDetail(Number(query.id))`（重新发起回填）；最后 `await getApprovalDetail()`。
- `getApprovalDetail()`：`ProcessInstanceApi.getApprovalDetail({ processDefinitionId, activityId: NodeId.START_USER_NODE_ID, processVariablesStr: JSON.stringify({ day: daysDifference() }) })`，用返回的 `activityNodes` 喂 `<ProcessInstanceTimeline>`，并筛出 `CandidateStrategy.START_USER_SELECT === node.candidateStrategy` 的节点。
- `selectUserConfirm(id, userList)` → `startUserSelectAssignees.value[id] = userList?.map(item => item.id)`。
- `watch(formData.value, ...)`：表单任一字段变更即重新预测节点（先把已选审批人暂存到 `tempStartUserSelectAssignees`）。
- 提交与跳转：
```js
    const data = { ...formData.value } as unknown as LeaveCreateData
    // 审批相关：设置指定审批人
    if (startUserSelectTasks.value?.length > 0) {
      data.startUserSelectAssignees = startUserSelectAssignees.value
    }
    await LeaveApi.createLeave(data)
    message.success('发起成功')
    // 关闭当前 Tab
    delView(unref(currentRoute))
    await push({ name: 'BpmOALeave' })
```
（提交前逐个校验 `startUserSelectAssignees[userTask.id].length === 0` → `message.warning(\`请选择${userTask.name}的审批人\`)`。）

### 3.6 相关路由 / API
- 路由 `src/router/modules/remaining.ts:307-329`：`bpm/oa/leave/create` → name `OALeaveCreate`；`bpm/oa/leave/detail` → name `OALeaveDetail`（均 `hidden: true, activeMenu: '/bpm/oa/leave'`）。
- API `src/api/bpm/leave/index.ts`：`createLeave` POST `/bpm/oa/leave/create`、`getLeave` GET `/bpm/oa/leave/get?id=`、`getLeavePage` GET `/bpm/oa/leave/page`。
- 列表页 `src/views/bpm/oa/leave/index.vue:229-236, 255-261`：详情 `router.push({ name: 'OALeaveDetail', query: { id: row.id } })`；审批进度 `router.push({ name: 'BpmProcessInstanceDetail', query: { id: row.processInstanceId } })`；取消 `ProcessInstanceApi.cancelProcessInstanceByStartUser(row.id, value)`。

---

## 4. 状态枚举数值

`enums/task/BpmProcessInstanceStatusEnum.java`：`NOT_START(-1,"未开始")`、`RUNNING(1,"审批中")`、`APPROVE(2,"审批通过")`、`REJECT(3,"审批不通过")`、`CANCEL(4,"已取消")`。辅助方法：`isRejectStatus(Integer)`、`isProcessEndStatus(Integer)`（APPROVE/REJECT/CANCEL）、`valueOf(Integer)`。

`enums/task/BpmTaskStatusEnum.java`：`SKIP(-2)`、`NOT_START(-1)`、`WAIT(0,"待审批")`、`RUNNING(1)`、`APPROVE(2)`、`REJECT(3)`、`CANCEL(4)`、`RETURN(5,"已退回")`、`APPROVING(7,"审批通过中")`。辅助：`isRejectStatus`、`isEndStatus`（APPROVE/REJECT/CANCEL/RETURN/APPROVING）、`isCancelStatus`、`valueOf`。

前端镜像：`src/utils/constants.ts` `BpmProcessInstanceStatus = { NOT_START: -1, RUNNING: 1, APPROVE: 2, REJECT: 3, CANCEL: 4 }`。

---

## 5. 模型保存 + 发布

### 5.1 后端端点（`controller/admin/definition/BpmModelController.java`，`@RequestMapping("/bpm/model")`）
| 端点 | 签名 | 权限 |
|---|---|---|
| `POST /create` | `CommonResult<String> createModel(@Valid @RequestBody BpmModelSaveReqVO createRetVO)` | `bpm:model:create` |
| `PUT /update` | `CommonResult<Boolean> updateModel(@Valid @RequestBody BpmModelSaveReqVO modelVO)`（内部 `modelService.updateModel(getLoginUserId(), modelVO)`） | `bpm:model:update` |
| `POST /deploy?id=` | `CommonResult<Boolean> deployModel(@RequestParam("id") String id)` → `modelService.deployModel(getLoginUserId(), id)` | `bpm:model:deploy` |
| `GET /get?id=`、`GET /list`、`PUT /update-sort-batch`、`PUT /update-state`、`DELETE /delete`、`DELETE /clean`、`GET /simple/get`、`PUT /update-bpmn`(@Deprecated)、`POST /simple/update`(@Deprecated) | | |

`BpmModelSaveReqVO extends BpmModelMetaInfoVO`，追加：`id`、`key`(`@NotEmpty`)、`name`(`@NotEmpty`)、`category`、`bpmnXml`、`simpleModel`(`BpmSimpleModelNodeVO`, `@Valid`)。

### 5.2 CUSTOM（formType=20）模型所需字段
Bean 校验层（`BpmModelMetaInfoVO`）必填：`type`(`@InEnum BpmModelTypeEnum`,`@NotNull`)、`formType`(`@InEnum BpmModelFormTypeEnum`,`@NotNull`)、`visible`(`@NotNull`)、`managerUserIds`(`@NotEmpty "可管理用户编号数组不能为空"`)；`icon` 若填必须是合法 URL（`@URL`）。
业务校验层（发布时 `validateFormConfig`）对 CUSTOM 追加必填 `formCustomCreatePath` + `formCustomViewPath`：
```java
        } else {
            if (StrUtil.isEmpty(metaInfo.getFormCustomCreatePath())
                    || StrUtil.isEmpty(metaInfo.getFormCustomViewPath())) {
                throw exception(MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG);
            }
            return null;
        }
```
（NORMAL 分支则要求 `formId` 非空且 `BpmFormDO` 存在。）
注意：`BpmModelMetaInfoVO` **没有** `startUserType` 字段——它只是前端本地表单字段（`src/views/bpm/model/form/index.vue:155`），后端只接收 `startUserIds` / `startDeptIds`（空表示全员可发起）。`sort` 由后端在 create 时 `createReqVO.setSort(System.currentTimeMillis())`。

前端 CUSTOM 表单项与校验：`src/views/bpm/model/form/FormDesign.vue:23-56, 128-133`
```js
const rules = {
  formType: [{ required: true, message: '表单类型不能为空', trigger: 'blur' }],
  formId: [{ required: true, message: '流程表单不能为空', trigger: 'blur' }],
  formCustomCreatePath: [{ required: true, message: '表单提交路由不能为空', trigger: 'blur' }],
  formCustomViewPath: [{ required: true, message: '表单查看地址不能为空', trigger: 'blur' }]
}
```
提示文案示例值：提交路由 `bpm/oa/leave/create.vue`，查看地址 `bpm/oa/leave/detail.vue`。

### 5.3 前端 SIMPLE 设计器保存/发布流程（`src/views/bpm/model/form/index.vue`）
- `validateAllSteps()`：步骤 0 基本信息 → 步骤 1 表单设计（`validateForm`）→ 步骤 2 流程设计（`validateProcess`），任一失败切回对应 step 并抛错。
- `handleSave()`：`actionType` 为 `definition`/`update` → `ModelApi.updateModel(modelData)`；`copy`/新增 → `formData.value.id = await ModelApi.createModel(modelData)`；非 `update` 场景保存后 `router.push({ name: 'BpmModel' })`。
- `handleDeploy()`：
```js
    // 先保存所有数据
    if (formData.value.id) {
      await ModelApi.updateModel(modelData)
    } else {
      const result = await ModelApi.createModel(modelData)
      formData.value.id = result.id
    }
    // 发布
    await ModelApi.deployModel(formData.value.id)
```
（注意：`createModel` 返回的是 String id 本身，这里写成 `result.id` — 与 `handleSave` 的 `formData.value.id = await ModelApi.createModel(...)` 不一致，是既有代码的可疑点。）
- `watch(formData.value.type)`：`BpmModelType.BPMN(10)` → `processData = formData.bpmnXml`；`SIMPLE(20)` → `processData = formData.simpleModel`。
- API：`src/api/bpm/model/index.ts` — `createModel` POST `/bpm/model/create`；`updateModel` PUT `/bpm/model/update`；`deployModel` POST `/bpm/model/deploy?id=` + `id`。

### 5.4 `BpmModelServiceImpl` 保存与发布（`service/definition/BpmModelServiceImpl.java`）
`saveModel(Model, BpmModelSaveReqVO)`（:141-159）——SIMPLE 与 BPMN 的分叉：
```java
        if (ObjUtil.equals(BpmModelTypeEnum.BPMN.getType(), saveReqVO.getType())
                && StrUtil.isNotEmpty(saveReqVO.getBpmnXml())) {
            updateModelBpmnXml(model.getId(), saveReqVO.getBpmnXml());
        } else if (ObjUtil.equals(BpmModelTypeEnum.SIMPLE.getType(), saveReqVO.getType())
                && saveReqVO.getSimpleModel() != null) {
            // JSON 转换成 bpmnModel
            BpmnModel bpmnModel = SimpleModelUtils.buildBpmnModel(model.getKey(), model.getName(),
                    saveReqVO.getSimpleModel());
            // 保存 Bpmn XML
            updateModelBpmnXml(model.getId(), BpmnModelUtils.getBpmnXml(bpmnModel));
            // 保存 JSON 数据
            updateModelSimpleJson(model.getId(), saveReqVO.getSimpleModel());
        }
```
即：**SIMPLE 模型在保存时就把 simpleModel JSON 编译成 BPMN XML 存为 model 的 source，JSON 另存为 source-extra**；部署时只读 BPMN XML。

`deployModel(Long userId, String id)`（:213-240）依次校验：
1. `validateModelManager(id, userId)` — model 存在且 `metaInfo.managerUserIds` 含当前用户，否则 `MODEL_UPDATE_FAIL_NOT_MANAGER`；
2. `validateBpmnXml(bpmnBytes, metaInfo.getType())` — BPMN 可解析（否则 `MODEL_NOT_EXISTS`）、必须有 `StartEvent`（`MODEL_DEPLOY_FAIL_BPMN_START_EVENT_NOT_EXISTS`）、所有 `UserTask` 的 name 非空（`MODEL_DEPLOY_FAIL_BPMN_USER_TASK_NAME_NOT_EXISTS`）、第一个用户任务（BPMN 取 index 0，SIMPLE 取 index 1，因为 SIMPLE 首节点固定是发起人）的候选策略不能是 `APPROVE_USER_SELECT`（`MODEL_DEPLOY_FAIL_FIRST_USER_TASK_CANDIDATE_STRATEGY_ERROR`）；
3. `validateFormConfig(metaInfo)`（见 5.2）；
4. `taskCandidateInvoker.validateBpmnConfig(bpmnBytes)`；
5. `getModelSimpleJson(model.getId())` 取 SIMPLE 快照；
6. `processDefinitionService.createProcessDefinition(model, metaInfo, bpmnBytes, simpleJson, form)` → 挂起旧版本 `updateProcessDefinitionSuspended(model.getDeploymentId())` → 回写 `model.setDeploymentId(...)` 并 `repositoryService.saveModel(model)`。
整个方法 `@Transactional(rollbackFor = Exception.class)`。

---

## 6. 流程标识（key）的正则与唯一性

存在，位于 `createModel`（`BpmModelServiceImpl.java:101-109`）：
```java
        if (!ValidationUtils.isXmlNCName(createReqVO.getKey())) {
            throw exception(MODEL_KEY_VALID);
        }
        // 1. 校验流程标识已经存在
        Model keyModel = getModelByKey(createReqVO.getKey());
        if (keyModel != null) {
            throw exception(MODEL_KEY_EXISTS, createReqVO.getKey());
        }
```
正则常量在 `yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/util/validation/ValidationUtils.java:25`：
```java
    private static final Pattern PATTERN_XML_NCNAME = Pattern.compile("[a-zA-Z_][\\-_.0-9_a-zA-Z$]*");
```
`isXmlNCName(String str)` = `StringUtils.hasText(str) && PATTERN_XML_NCNAME.matcher(str).matches()`。

错误码（`yudao-module-bpm/.../enums/ErrorCodeConstants.java`）：
- `MODEL_KEY_EXISTS = 1_009_002_000, "已经存在流程标识为【{}】的流程"`
- `MODEL_KEY_VALID = 1_009_002_002, "流程标识格式不正确，需要以字母或下划线开头，后接任意字母、数字、中划线、下划线、句点！"`
- `MODEL_DEPLOY_FAIL_FORM_NOT_CONFIG = 1_009_002_003, "部署流程失败，原因：流程表单未配置，请点击【修改流程】按钮进行配置"`

注意：唯一性与正则**只在 create 时校验**，`updateModel` 不再校验 key（Flowable 的 `Model#key` 仍可被 `copyToModel` 覆盖）。唯一性按租户维度（`getModelByKey` 走 `modelTenantId(FlowableUtils.getTenantId())`）。数据库层无额外唯一索引声明在本仓库 Java 代码中。