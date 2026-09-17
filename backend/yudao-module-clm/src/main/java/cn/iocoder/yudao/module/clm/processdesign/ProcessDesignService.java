package cn.iocoder.yudao.module.clm.processdesign;

import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmModelConvert;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmConditionOpCodeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelFormTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmModelTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModeConditionTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmSimpleModelNodeTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmUserTaskApproveTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.clm.controller.admin.contracttype.vo.ContractTypeSimpleRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.processdesign.vo.ProcessDesignDetailRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.processdesign.vo.ProcessDesignSaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractFormSchemaValidator;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import jakarta.annotation.Resource;
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_KEY_IMMUTABLE;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_KEY_PREFIX_INVALID;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_METADATA_INVALID;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_MANAGER_MISSING;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_NOT_CONTRACT;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_SIMPLE_MODEL_INVALID;

/**
 * CLM 合同审批方案治理门面。
 *
 * <p>只暴露 CLM 合同模型，并在写入前覆盖通用 BPM 元数据，避免合同审批方案
 * 改用 BPMN 设计器或绑定到其他业务表单。发布仅创建新流程定义，不迁移已运行实例。</p>
 */
@Service
public class ProcessDesignService {

    public static final String CONTRACT_PROCESS_KEY_PREFIX = "clm_contract_";
    public static final String CONTRACT_CATEGORY = "contract";
    public static final String CONTRACT_CREATE_PATH = "/clm/contract/create";
    public static final String CONTRACT_VIEW_PATH = "/clm/contract/bpm/index.vue";

    private static final Set<Integer> ALLOWED_NODE_TYPES = Set.of(
            BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.END_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.COPY_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.CONDITION_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.CONDITION_BRANCH_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE.getType(),
            BpmSimpleModelNodeTypeEnum.INCLUSIVE_BRANCH_NODE.getType());
    private static final Set<Integer> ALLOWED_CANDIDATE_STRATEGIES = Set.of(
            BpmTaskCandidateStrategyEnum.ROLE.getStrategy(),
            BpmTaskCandidateStrategyEnum.DEPT_MEMBER.getStrategy(),
            BpmTaskCandidateStrategyEnum.DEPT_LEADER.getStrategy(),
            BpmTaskCandidateStrategyEnum.MULTI_DEPT_LEADER_MULTI.getStrategy(),
            BpmTaskCandidateStrategyEnum.POST.getStrategy(),
            BpmTaskCandidateStrategyEnum.USER.getStrategy(),
            BpmTaskCandidateStrategyEnum.START_USER.getStrategy(),
            BpmTaskCandidateStrategyEnum.START_USER_DEPT_LEADER.getStrategy(),
            BpmTaskCandidateStrategyEnum.START_USER_DEPT_LEADER_MULTI.getStrategy(),
            BpmTaskCandidateStrategyEnum.USER_GROUP.getStrategy());
    private static final Set<String> BASE_RULE_FIELDS = Set.of(
            "contractId", "bindingId", "amount", "ownerDeptId", "contractTypeCode",
            "contractTitle", "contractNo", "revisionId");
    private static final Pattern SAFE_VARIABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    @Resource
    private BpmModelService bpmModelService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    private ContractTypeService contractTypeService;
    @Resource
    private ContractFormSchemaValidator contractFormSchemaValidator;

    public List<ProcessDesignListItemRespVO> list(String name) {
        List<Model> models = bpmModelService.getModelList(name);
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProcessDesignListItemRespVO> result = new ArrayList<>();
        for (Model model : models) {
            if (!isContractProcessKey(model.getKey())) {
                continue;
            }
            BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
            ProcessDefinition definition = model.getDeploymentId() == null ? null
                    : processDefinitionService.getProcessDefinitionByDeploymentId(model.getDeploymentId());
            ProcessDesignListItemRespVO item = new ProcessDesignListItemRespVO()
                    .setId(model.getId())
                    .setKey(model.getKey())
                    .setName(model.getName())
                    .setDescription(metaInfo == null ? null : metaInfo.getDescription())
                    .setDeployed(definition != null)
                    .setDeployedVersion(definition == null ? null : definition.getVersion())
                    .setUpdateTime(model.getLastUpdateTime() == null ? null : DateUtils.of(model.getLastUpdateTime()));
            result.add(item);
        }
        return result;
    }

    public ProcessDesignDetailRespVO get(String id) {
        Model model = validateContractModel(id);
        BpmModelRespVO bpmModel = BpmModelConvert.INSTANCE.buildModel(model, bpmModelService.getModelBpmnXML(id),
                bpmModelService.getSimpleModel(id));
        ProcessDesignDetailRespVO result = BeanUtils.toBean(bpmModel, ProcessDesignDetailRespVO.class);
        result.setModelVersion(model.getVersion());
        ProcessDefinition definition = processDefinitionService.getActiveProcessDefinition(model.getKey());
        if (definition != null) {
            BpmProcessDefinitionRespVO definitionVO = BeanUtils.toBean(definition, BpmProcessDefinitionRespVO.class);
            definitionVO.setId(definition.getId())
                    .setVersion(definition.getVersion())
                    .setName(definition.getName())
                    .setKey(definition.getKey())
                    .setCategory(definition.getCategory())
                    .setSuspensionState(definition.isSuspended()
                    ? SuspensionState.SUSPENDED.getStateCode() : SuspensionState.ACTIVE.getStateCode());
            definitionVO.setDescription(definition.getDescription());
            result.setProcessDefinition(definitionVO);
        }
        return result;
    }

    public String create(Long userId, ProcessDesignSaveReqVO reqVO) {
        validateContractProcessKey(reqVO.getKey());
        reqVO.setId(null);
        normalizeContractMetadata(reqVO, userId, null);
        validateSimpleModel(reqVO.getSimpleModel());
        return bpmModelService.createModel(reqVO);
    }

    public void update(Long userId, ProcessDesignSaveReqVO reqVO) {
        Model model = validateContractModel(reqVO.getId());
        if (!Objects.equals(model.getKey(), reqVO.getKey())) {
            throw exception(PROCESS_KEY_IMMUTABLE);
        }
        if (!Objects.equals(model.getVersion(), reqVO.getExpectedModelVersion())) {
            throw exception(PROCESS_MODEL_VERSION_CONFLICT);
        }
        BpmModelMetaInfoVO currentMetaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
        Long bpmManagerUserId = resolveBpmManagerUserId(userId, currentMetaInfo);
        normalizeContractMetadata(reqVO, userId, currentMetaInfo);
        validateSimpleModel(reqVO.getSimpleModel());
        bpmModelService.updateModel(bpmManagerUserId, reqVO);
    }

    public void deploy(Long userId, String id) {
        Model model = validateContractModel(id);
        BpmModelMetaInfoVO metaInfo = BpmModelConvert.INSTANCE.parseMetaInfo(model);
        if (!isCanonicalContractMetadata(model, metaInfo)) {
            throw exception(PROCESS_MODEL_METADATA_INVALID);
        }
        validateSimpleModel(bpmModelService.getSimpleModel(id));
        bpmModelService.deployModel(resolveBpmManagerUserId(userId, metaInfo), id);
    }

    private Model validateContractModel(String id) {
        Model model = bpmModelService.getModel(id);
        if (model == null || !isContractProcessKey(model.getKey())
                || !Objects.equals(CONTRACT_CATEGORY, model.getCategory())
                || !Objects.equals(FlowableUtils.getTenantId(), model.getTenantId())) {
            throw exception(PROCESS_MODEL_NOT_CONTRACT);
        }
        return model;
    }

    private void validateContractProcessKey(String key) {
        if (!isContractProcessKey(key)) {
            throw exception(PROCESS_KEY_PREFIX_INVALID);
        }
    }

    private boolean isContractProcessKey(String key) {
        return key != null && key.startsWith(CONTRACT_PROCESS_KEY_PREFIX);
    }

    private void normalizeContractMetadata(BpmModelSaveReqVO reqVO, Long userId,
                                           BpmModelMetaInfoVO currentMetaInfo) {
        reqVO.setType(BpmModelTypeEnum.SIMPLE.getType());
        reqVO.setFormType(BpmModelFormTypeEnum.CUSTOM.getType());
        reqVO.setFormId(null);
        reqVO.setFormCustomCreatePath(CONTRACT_CREATE_PATH);
        reqVO.setFormCustomViewPath(CONTRACT_VIEW_PATH);
        reqVO.setCategory(CONTRACT_CATEGORY);
        reqVO.setVisible(true);
        reqVO.setStartUserIds(Collections.emptyList());
        reqVO.setStartDeptIds(Collections.emptyList());
        reqVO.setAllowCancelRunningProcess(true);
        reqVO.setAllowWithdrawTask(false);
        reqVO.setBpmnXml(null);
        reqVO.setProcessIdRule(null);
        reqVO.setAutoApprovalType(null);
        reqVO.setTitleSetting(null);
        reqVO.setSummarySetting(null);
        reqVO.setProcessBeforeTriggerSetting(null);
        reqVO.setProcessAfterTriggerSetting(null);
        reqVO.setTaskBeforeTriggerSetting(null);
        reqVO.setTaskAfterTriggerSetting(null);
        reqVO.setPrintTemplateSetting(null);
        if (reqVO.getSort() == null && currentMetaInfo != null) {
            reqVO.setSort(currentMetaInfo.getSort());
        }

        Set<Long> managers = new LinkedHashSet<>();
        if (currentMetaInfo != null && currentMetaInfo.getManagerUserIds() != null) {
            currentMetaInfo.getManagerUserIds().stream().filter(Objects::nonNull).forEach(managers::add);
        }
        List<Long> requestedManagers = reqVO.getManagerUserIds();
        if (requestedManagers != null) {
            requestedManagers.stream().filter(Objects::nonNull).forEach(managers::add);
        }
        managers.add(Objects.requireNonNull(userId, "login user id must not be null"));
        reqVO.setManagerUserIds(new ArrayList<>(managers));
    }

    /**
     * 通用 BPM 服务仍按模型 managerUserIds 校验。CLM 接口已经按角色权限授权，因此同角色管理员
     * 可共同维护方案；调用通用服务时沿用一个真实的现有 manager 通过其内部一致性校验，并将实际
     * 操作人写回 managerUserIds，后续即可直接通过通用校验。
     */
    private Long resolveBpmManagerUserId(Long callerUserId, BpmModelMetaInfoVO currentMetaInfo) {
        List<Long> managerUserIds = currentMetaInfo == null ? null : currentMetaInfo.getManagerUserIds();
        if (managerUserIds == null || managerUserIds.isEmpty()) {
            throw exception(PROCESS_MODEL_MANAGER_MISSING);
        }
        if (managerUserIds.contains(callerUserId)) {
            return callerUserId;
        }
        return managerUserIds.stream().filter(Objects::nonNull).findFirst()
                .orElseThrow(() -> exception(PROCESS_MODEL_MANAGER_MISSING));
    }

    private boolean isCanonicalContractMetadata(Model model, BpmModelMetaInfoVO metaInfo) {
        return metaInfo != null
                && Objects.equals(BpmModelTypeEnum.SIMPLE.getType(), metaInfo.getType())
                && Objects.equals(BpmModelFormTypeEnum.CUSTOM.getType(), metaInfo.getFormType())
                && Objects.equals(CONTRACT_CATEGORY, model.getCategory())
                && Objects.equals(CONTRACT_CREATE_PATH, metaInfo.getFormCustomCreatePath())
                && Objects.equals(CONTRACT_VIEW_PATH, metaInfo.getFormCustomViewPath())
                && metaInfo.getProcessIdRule() == null
                && metaInfo.getAutoApprovalType() == null
                && metaInfo.getTitleSetting() == null
                && metaInfo.getSummarySetting() == null
                && metaInfo.getProcessBeforeTriggerSetting() == null
                && metaInfo.getProcessAfterTriggerSetting() == null
                && metaInfo.getTaskBeforeTriggerSetting() == null
                && metaInfo.getTaskAfterTriggerSetting() == null
                && metaInfo.getPrintTemplateSetting() == null;
    }

    /**
     * 合同审批方案只接受一期可视化设计器需要的声明式节点，禁止任意表达式、HTTP 回调和自动化节点。
     */
    private void validateSimpleModel(BpmSimpleModelNodeVO simpleModel) {
        if (simpleModel == null) {
            throw invalidSimpleModel("流程图不能为空");
        }
        if (!Objects.equals(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType(), simpleModel.getType())) {
            throw invalidSimpleModel("根节点必须为发起人");
        }
        ValidationState state = new ValidationState();
        validateNode(simpleModel, true, false, state, new IdentityHashMap<>());
        if (state.humanApproveCount == 0) {
            throw invalidSimpleModel("至少需要一个人工审批节点");
        }
        if (!endsWithEndNode(simpleModel, new IdentityHashMap<>())) {
            throw invalidSimpleModel("主流程必须以结束节点收尾");
        }
    }

    private void validateNode(BpmSimpleModelNodeVO node, boolean root, boolean branchCondition,
                              ValidationState state, Map<BpmSimpleModelNodeVO, Boolean> visited) {
        if (node == null) {
            return;
        }
        if (++state.nodeCount > 256) {
            throw invalidSimpleModel("流程图节点数量不能超过 256 个");
        }
        if (visited.put(node, Boolean.TRUE) != null) {
            throw invalidSimpleModel("流程图不能包含循环引用");
        }
        if (node.getId() == null || node.getId().isBlank()) {
            throw invalidSimpleModel("节点编号不能为空");
        }
        if (!state.nodeIds.add(node.getId())) {
            throw invalidSimpleModel("节点编号不能重复：" + node.getId());
        }
        Integer type = node.getType();
        if (!ALLOWED_NODE_TYPES.contains(type)) {
            throw invalidSimpleModel("包含一期不支持的节点类型：" + type);
        }
        if (Objects.equals(type, BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType()) != root) {
            throw invalidSimpleModel("发起人节点只能作为根节点");
        }
        if (Objects.equals(type, BpmSimpleModelNodeTypeEnum.CONDITION_NODE.getType()) != branchCondition) {
            throw invalidSimpleModel("条件节点只能位于分支节点内");
        }
        if (!branchCondition && node.getConditionSetting() != null) {
            throw invalidSimpleModel("非条件节点不能携带条件配置");
        }
        validateNoExecutableNodeConfiguration(node);
        validateCandidateConfiguration(node);

        if (Objects.equals(type, BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType())) {
            if (node.getApproveType() != null
                    && !Objects.equals(node.getApproveType(), BpmUserTaskApproveTypeEnum.USER.getType())) {
                throw invalidSimpleModel("审批节点只允许人工审批");
            }
            if (Objects.equals(node.getApproveType(), BpmUserTaskApproveTypeEnum.USER.getType())) {
                state.humanApproveCount++;
            }
        }

        if (Objects.equals(type, BpmSimpleModelNodeTypeEnum.END_NODE.getType())) {
            if (node.getChildNode() != null || hasItems(node.getConditionNodes())) {
                throw invalidSimpleModel("结束节点不能包含后续节点");
            }
            return;
        }

        if (isBranchType(type)) {
            validateBranchNode(node, state, visited);
        } else if (hasItems(node.getConditionNodes())) {
            throw invalidSimpleModel("非分支节点不能包含条件分支");
        }
        validateNode(node.getChildNode(), false, false, state, visited);
    }

    private void validateNoExecutableNodeConfiguration(BpmSimpleModelNodeVO node) {
        if (node.getSkipExpression() != null) {
            throw invalidSimpleModel("不允许配置跳过表达式");
        }
        validateListener(node.getTaskCreateListener());
        validateListener(node.getTaskAssignListener());
        validateListener(node.getTaskCompleteListener());
        if (Boolean.TRUE.equals(node.getSignEnable())) {
            throw invalidSimpleModel("一期不支持签名审批");
        }
        if (hasItems(node.getFieldsPermission())) {
            throw invalidSimpleModel("一期不支持节点级表单权限");
        }
        if (node.getDelaySetting() != null || node.getTriggerSetting() != null
                || node.getChildProcessSetting() != null || hasItems(node.getRouterGroups())
                || node.getAttachNodeId() != null || node.getRouterDefaultFlowId() != null) {
            throw invalidSimpleModel("节点不能携带延迟、触发、子流程或路由配置");
        }
    }

    private void validateListener(BpmSimpleModelNodeVO.ListenerHandler listener) {
        if (listener == null) {
            return;
        }
        if (!Boolean.FALSE.equals(listener.getEnable()) || listener.getPath() != null
                || listener.getHeader() != null || listener.getBody() != null) {
            throw invalidSimpleModel("不允许启用或携带任务 HTTP 监听器");
        }
    }

    private void validateCandidateConfiguration(BpmSimpleModelNodeVO node) {
        Integer strategy = node.getCandidateStrategy();
        if (strategy != null && !ALLOWED_CANDIDATE_STRATEGIES.contains(strategy)) {
            throw invalidSimpleModel("一期不支持候选人策略：" + strategy);
        }
    }

    private void validateBranchNode(BpmSimpleModelNodeVO node, ValidationState state,
                                    Map<BpmSimpleModelNodeVO, Boolean> visited) {
        List<BpmSimpleModelNodeVO> conditions = node.getConditionNodes();
        if (conditions == null || conditions.size() < 2) {
            throw invalidSimpleModel("分支节点至少需要两个分支");
        }
        boolean ruleBranch = !Objects.equals(node.getType(),
                BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE.getType());
        int defaultFlowCount = 0;
        for (BpmSimpleModelNodeVO condition : conditions) {
            if (condition == null) {
                throw invalidSimpleModel("条件分支不能为空");
            }
            BpmSimpleModelNodeVO.ConditionSetting setting = condition.getConditionSetting();
            if (ruleBranch) {
                if (setting == null) {
                    throw invalidSimpleModel("条件分支配置不能为空");
                }
                if (Boolean.TRUE.equals(setting.getDefaultFlow())) {
                    defaultFlowCount++;
                    validateDefaultCondition(setting);
                } else {
                    validateRuleCondition(setting, getAllowedRuleFields(state));
                }
            } else if (setting != null) {
                throw invalidSimpleModel("并行分支不能携带条件配置");
            }
            validateNode(condition, false, true, state, visited);
        }
        if (ruleBranch && defaultFlowCount != 1) {
            throw invalidSimpleModel("条件或包容分支必须且只能包含一个默认分支");
        }
    }

    private void validateRuleCondition(BpmSimpleModelNodeVO.ConditionSetting setting,
                                       Set<String> allowedRuleFields) {
        validateNoExpression(setting);
        if (!Objects.equals(setting.getConditionType(), BpmSimpleModeConditionTypeEnum.RULE.getType())) {
            throw invalidSimpleModel("条件分支只允许规则条件");
        }
        BpmSimpleModelNodeVO.ConditionGroups groups = setting.getConditionGroups();
        if (groups == null || groups.getAnd() == null || !hasItems(groups.getConditions())) {
            throw invalidSimpleModel("规则条件组不能为空");
        }
        for (BpmSimpleModelNodeVO.Condition condition : groups.getConditions()) {
            if (condition == null || condition.getAnd() == null || !hasItems(condition.getRules())) {
                throw invalidSimpleModel("规则条件不能为空");
            }
            for (BpmSimpleModelNodeVO.ConditionRule rule : condition.getRules()) {
                validateConditionRule(rule, allowedRuleFields);
            }
        }
    }

    private void validateNoExpression(BpmSimpleModelNodeVO.ConditionSetting setting) {
        if (Objects.equals(setting.getConditionType(), BpmSimpleModeConditionTypeEnum.EXPRESSION.getType())
                || (setting.getConditionExpression() != null && !setting.getConditionExpression().isEmpty())) {
            throw invalidSimpleModel("不允许使用条件表达式");
        }
    }

    private void validateDefaultCondition(BpmSimpleModelNodeVO.ConditionSetting setting) {
        validateNoExpression(setting);
        if (setting.getConditionType() != null || setting.getConditionGroups() != null) {
            throw invalidSimpleModel("默认分支不能携带规则配置");
        }
    }

    private void validateConditionRule(BpmSimpleModelNodeVO.ConditionRule rule,
                                       Set<String> allowedRuleFields) {
        if (rule == null || rule.getLeftSide() == null
                || !SAFE_VARIABLE_NAME.matcher(rule.getLeftSide()).matches()) {
            throw invalidSimpleModel("规则左值必须是安全变量名");
        }
        if (!allowedRuleFields.contains(rule.getLeftSide())) {
            throw invalidSimpleModel("规则字段未在所有已发布合同类型中声明为必填：" + rule.getLeftSide());
        }
        try {
            BpmConditionOpCodeEnum.fromCode(rule.getOpCode());
        } catch (RuntimeException ex) {
            throw invalidSimpleModel("规则运算符不受支持");
        }
        String rightSide = rule.getRightSide();
        if (rightSide == null || rightSide.isEmpty() || rightSide.indexOf('"') >= 0
                || rightSide.indexOf('\\') >= 0 || rightSide.indexOf('\n') >= 0
                || rightSide.indexOf('\r') >= 0) {
            throw invalidSimpleModel("规则右值包含不安全字符");
        }
    }

    private Set<String> loadAllowedRuleFields() {
        Set<String> allowedFields = new LinkedHashSet<>(BASE_RULE_FIELDS);
        List<ContractTypeSimpleRespVO> types = contractTypeService.getContractTypeSimpleList();
        if (types == null || types.isEmpty()) {
            return allowedFields;
        }
        Set<String> requiredIntersection = null;
        for (ContractTypeSimpleRespVO type : types) {
            ContractTypeVersionDO version = type.getCurrentVersionId() == null ? null
                    : contractTypeService.getContractTypeVersion(type.getCurrentVersionId());
            Set<String> requiredFields = new LinkedHashSet<>();
            if (version != null) {
                Map<String, ContractFormSchemaValidator.FieldRule> rules;
                try {
                    rules = contractFormSchemaValidator.parseRules(version.getFormFields());
                } catch (RuntimeException ex) {
                    throw invalidSimpleModel("已发布合同类型字段定义无法安全解析");
                }
                rules.values().stream().filter(ContractFormSchemaValidator.FieldRule::isRequired)
                        .map(ContractFormSchemaValidator.FieldRule::getField).forEach(requiredFields::add);
            }
            if (requiredIntersection == null) {
                requiredIntersection = requiredFields;
            } else {
                requiredIntersection.retainAll(requiredFields);
            }
        }
        if (requiredIntersection != null) {
            allowedFields.addAll(requiredIntersection);
        }
        return allowedFields;
    }

    private Set<String> getAllowedRuleFields(ValidationState state) {
        if (state.allowedRuleFields == null) {
            state.allowedRuleFields = loadAllowedRuleFields();
        }
        return state.allowedRuleFields;
    }

    private boolean endsWithEndNode(BpmSimpleModelNodeVO node,
                                    Map<BpmSimpleModelNodeVO, Boolean> visited) {
        if (node == null || visited.put(node, Boolean.TRUE) != null) {
            return false;
        }
        if (Objects.equals(node.getType(), BpmSimpleModelNodeTypeEnum.END_NODE.getType())) {
            return true;
        }
        return endsWithEndNode(node.getChildNode(), visited);
    }

    private boolean isBranchType(Integer type) {
        return Objects.equals(type, BpmSimpleModelNodeTypeEnum.CONDITION_BRANCH_NODE.getType())
                || Objects.equals(type, BpmSimpleModelNodeTypeEnum.PARALLEL_BRANCH_NODE.getType())
                || Objects.equals(type, BpmSimpleModelNodeTypeEnum.INCLUSIVE_BRANCH_NODE.getType());
    }

    private boolean hasItems(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private RuntimeException invalidSimpleModel(String reason) {
        return exception(PROCESS_SIMPLE_MODEL_INVALID, reason);
    }

    private static final class ValidationState {

        private Set<String> allowedRuleFields;
        private final Set<String> nodeIds = new LinkedHashSet<>();
        private int nodeCount;
        private int humanApproveCount;
    }

}
