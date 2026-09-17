package cn.iocoder.yudao.module.clm.processdesign;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelMetaInfoVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
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
import org.flowable.common.engine.impl.db.SuspensionState;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_KEY_PREFIX_INVALID;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_METADATA_INVALID;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_MANAGER_MISSING;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_NOT_CONTRACT;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_MODEL_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignErrors.PROCESS_SIMPLE_MODEL_INVALID;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignService.CONTRACT_CATEGORY;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignService.CONTRACT_CREATE_PATH;
import static cn.iocoder.yudao.module.clm.processdesign.ProcessDesignService.CONTRACT_VIEW_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessDesignServiceTest {

    private static final Long USER_ID = 7L;

    @InjectMocks
    private ProcessDesignService service;
    @Mock
    private BpmModelService bpmModelService;
    @Mock
    private BpmProcessDefinitionService processDefinitionService;
    @Mock
    private ContractTypeService contractTypeService;
    @Spy
    private ContractFormSchemaValidator contractFormSchemaValidator = new ContractFormSchemaValidator();

    @Test
    void list_onlyReturnsContractPrefixAndDeploymentSummary() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", "dep-1",
                canonicalMeta("contract approval"));
        when(contract.getLastUpdateTime()).thenReturn(new Date());
        Model foreign = model("m-2", "leave_approval", "请假审批", "dep-2", canonicalMeta("leave"));
        when(bpmModelService.getModelList("合同")).thenReturn(List.of(contract, foreign));
        ProcessDefinition definition = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(processDefinitionService.getProcessDefinitionByDeploymentId("dep-1")).thenReturn(definition);
        when(definition.getVersion()).thenReturn(3);

        List<ProcessDesignListItemRespVO> result = service.list("合同");

        assertEquals(1, result.size());
        assertEquals("m-1", result.get(0).getId());
        assertEquals("clm_contract_purchase", result.get(0).getKey());
        assertEquals("contract approval", result.get(0).getDescription());
        assertTrue(result.get(0).getDeployed());
        assertEquals(3, result.get(0).getDeployedVersion());
        verify(processDefinitionService, never()).getProcessDefinitionByDeploymentId("dep-2");
    }

    @Test
    void get_includesActiveProcessDefinitionVersionAndState() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", "dep-1",
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        ProcessDefinition definition = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(processDefinitionService.getActiveProcessDefinition("clm_contract_purchase")).thenReturn(definition);
        when(definition.getId()).thenReturn("definition-3");
        when(definition.getKey()).thenReturn("clm_contract_purchase");
        when(definition.getName()).thenReturn("采购合同审批");
        when(definition.getCategory()).thenReturn(CONTRACT_CATEGORY);
        when(definition.getDescription()).thenReturn("contract approval");
        when(definition.getVersion()).thenReturn(3);
        when(definition.isSuspended()).thenReturn(false);

        ProcessDesignDetailRespVO result = service.get("m-1");

        assertEquals("definition-3", result.getProcessDefinition().getId());
        assertEquals("clm_contract_purchase", result.getProcessDefinition().getKey());
        assertEquals(3, result.getProcessDefinition().getVersion());
        assertEquals(SuspensionState.ACTIVE.getStateCode(), result.getProcessDefinition().getSuspensionState());
        assertEquals(4, result.getModelVersion());
    }

    @Test
    void create_rejectsNonContractPrefix() {
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setKey("expense_approval");
        reqVO.setName("报销审批");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(USER_ID, reqVO));

        assertEquals(PROCESS_KEY_PREFIX_INVALID.getCode(), ex.getCode());
        verify(bpmModelService, never()).createModel(any());
    }

    @Test
    void create_forcesContractMetadataAndKeepsCallerAsManager() {
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setId("spoofed");
        reqVO.setKey("clm_contract_purchase");
        reqVO.setName("采购合同审批");
        reqVO.setCategory("leave");
        reqVO.setType(BpmModelTypeEnum.BPMN.getType());
        reqVO.setFormType(BpmModelFormTypeEnum.NORMAL.getType());
        reqVO.setFormId(99L);
        reqVO.setFormCustomCreatePath("/other/create");
        reqVO.setFormCustomViewPath("/other/view");
        reqVO.setVisible(false);
        reqVO.setStartUserIds(List.of(100L));
        reqVO.setStartDeptIds(List.of(200L));
        reqVO.setManagerUserIds(List.of(9L));
        reqVO.setBpmnXml("<xml/>");
        reqVO.setSimpleModel(validSimpleModel());
        reqVO.setProcessIdRule(new BpmModelMetaInfoVO.ProcessIdRule());
        reqVO.setAutoApprovalType(1);
        reqVO.setTitleSetting(new BpmModelMetaInfoVO.TitleSetting());
        reqVO.setSummarySetting(new BpmModelMetaInfoVO.SummarySetting());
        reqVO.setProcessBeforeTriggerSetting(new BpmModelMetaInfoVO.HttpRequestSetting());
        reqVO.setProcessAfterTriggerSetting(new BpmModelMetaInfoVO.HttpRequestSetting());
        reqVO.setTaskBeforeTriggerSetting(new BpmModelMetaInfoVO.HttpRequestSetting());
        reqVO.setTaskAfterTriggerSetting(new BpmModelMetaInfoVO.HttpRequestSetting());
        reqVO.setPrintTemplateSetting(new BpmModelMetaInfoVO.PrintTemplateSetting());
        when(bpmModelService.createModel(any())).thenReturn("m-1");

        assertEquals("m-1", service.create(USER_ID, reqVO));

        ArgumentCaptor<ProcessDesignSaveReqVO> captor = ArgumentCaptor.forClass(ProcessDesignSaveReqVO.class);
        verify(bpmModelService).createModel(captor.capture());
        ProcessDesignSaveReqVO normalized = captor.getValue();
        assertNull(normalized.getId());
        assertEquals(BpmModelTypeEnum.SIMPLE.getType(), normalized.getType());
        assertEquals(BpmModelFormTypeEnum.CUSTOM.getType(), normalized.getFormType());
        assertNull(normalized.getFormId());
        assertEquals(CONTRACT_CATEGORY, normalized.getCategory());
        assertEquals(CONTRACT_CREATE_PATH, normalized.getFormCustomCreatePath());
        assertEquals(CONTRACT_VIEW_PATH, normalized.getFormCustomViewPath());
        assertTrue(normalized.getVisible());
        assertTrue(normalized.getStartUserIds().isEmpty());
        assertTrue(normalized.getStartDeptIds().isEmpty());
        assertEquals(List.of(9L, USER_ID), normalized.getManagerUserIds());
        assertTrue(normalized.getAllowCancelRunningProcess());
        assertFalse(normalized.getAllowWithdrawTask());
        assertNull(normalized.getBpmnXml());
        assertNull(normalized.getProcessIdRule());
        assertNull(normalized.getAutoApprovalType());
        assertNull(normalized.getTitleSetting());
        assertNull(normalized.getSummarySetting());
        assertNull(normalized.getProcessBeforeTriggerSetting());
        assertNull(normalized.getProcessAfterTriggerSetting());
        assertNull(normalized.getTaskBeforeTriggerSetting());
        assertNull(normalized.getTaskAfterTriggerSetting());
        assertNull(normalized.getPrintTemplateSetting());
    }

    @Test
    void update_currentVersionForcesMetadataBeforeDelegatingToBpmService() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setId("m-1");
        reqVO.setKey("clm_contract_purchase");
        reqVO.setName("新名称");
        reqVO.setExpectedModelVersion(4);
        reqVO.setType(BpmModelTypeEnum.BPMN.getType());
        reqVO.setFormType(BpmModelFormTypeEnum.NORMAL.getType());
        reqVO.setManagerUserIds(List.of(8L));
        reqVO.setSimpleModel(validSimpleModel());

        service.update(USER_ID, reqVO);

        ArgumentCaptor<ProcessDesignSaveReqVO> captor = ArgumentCaptor.forClass(ProcessDesignSaveReqVO.class);
        verify(bpmModelService).updateModel(org.mockito.ArgumentMatchers.eq(USER_ID), captor.capture());
        ProcessDesignSaveReqVO normalized = captor.getValue();
        assertEquals(BpmModelTypeEnum.SIMPLE.getType(), normalized.getType());
        assertEquals(BpmModelFormTypeEnum.CUSTOM.getType(), normalized.getFormType());
        assertEquals(CONTRACT_CREATE_PATH, normalized.getFormCustomCreatePath());
        assertEquals(CONTRACT_VIEW_PATH, normalized.getFormCustomViewPath());
        assertEquals(List.of(USER_ID, 8L), normalized.getManagerUserIds());
        assertEquals(1L, normalized.getSort());
    }

    @Test
    void update_secondAuthorizedClmAdminUsesExistingBpmManagerAndJoinsManagerList() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setId("m-1");
        reqVO.setKey("clm_contract_purchase");
        reqVO.setName("第二管理员保存");
        reqVO.setExpectedModelVersion(4);
        reqVO.setManagerUserIds(List.of(USER_ID));
        reqVO.setSimpleModel(validSimpleModel());

        service.update(8L, reqVO);

        ArgumentCaptor<ProcessDesignSaveReqVO> captor = ArgumentCaptor.forClass(ProcessDesignSaveReqVO.class);
        verify(bpmModelService).updateModel(org.mockito.ArgumentMatchers.eq(USER_ID), captor.capture());
        assertEquals(List.of(USER_ID, 8L), captor.getValue().getManagerUserIds());
    }

    @Test
    void update_staleVersionIsRejectedWithoutUpdatingBpmModel() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setId("m-1");
        reqVO.setKey("clm_contract_purchase");
        reqVO.setName("过期页面保存");
        reqVO.setExpectedModelVersion(3);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(8L, reqVO));

        assertEquals(PROCESS_MODEL_VERSION_CONFLICT.getCode(), ex.getCode());
        verify(bpmModelService, never()).updateModel(any(), any());
    }

    @Test
    void update_rejectsModelWithoutExistingBpmManager() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMetaWithoutManagers("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setId("m-1");
        reqVO.setKey("clm_contract_purchase");
        reqVO.setName("无法保存");
        reqVO.setExpectedModelVersion(4);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(8L, reqVO));

        assertEquals(PROCESS_MODEL_MANAGER_MISSING.getCode(), ex.getCode());
        verify(bpmModelService, never()).updateModel(any(), any());
    }

    @Test
    void deploy_rejectsNonContractModel() {
        Model foreign = model("foreign", "leave_approval", "请假审批", null, canonicalMeta("leave"));
        when(bpmModelService.getModel("foreign")).thenReturn(foreign);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deploy(USER_ID, "foreign"));

        assertEquals(PROCESS_MODEL_NOT_CONTRACT.getCode(), ex.getCode());
        verify(bpmModelService, never()).deployModel(USER_ID, "foreign");
    }

    @Test
    void deploy_rejectsContractModelWithNonCanonicalMetadata() {
        Model invalid = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                "{\"type\":10,\"formType\":20,\"managerUserIds\":[7],\"sort\":1}");
        when(bpmModelService.getModel("m-1")).thenReturn(invalid);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.deploy(USER_ID, "m-1"));

        assertEquals(PROCESS_MODEL_METADATA_INVALID.getCode(), ex.getCode());
        verify(bpmModelService, never()).deployModel(USER_ID, "m-1");
    }

    @Test
    void deploy_publishesCanonicalContractModel() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        when(bpmModelService.getSimpleModel("m-1")).thenReturn(validSimpleModel());

        service.deploy(USER_ID, "m-1");

        verify(bpmModelService).deployModel(USER_ID, "m-1");
    }

    @Test
    void deploy_secondAuthorizedClmAdminUsesExistingBpmManager() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        when(bpmModelService.getSimpleModel("m-1")).thenReturn(validSimpleModel());

        service.deploy(8L, "m-1");

        verify(bpmModelService).deployModel(USER_ID, "m-1");
    }

    @Test
    void get_rejectsCrossTenantAndWrongCategoryModels() {
        Model crossTenant = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(crossTenant.getTenantId()).thenReturn("another-tenant");
        when(bpmModelService.getModel("m-1")).thenReturn(crossTenant);

        ServiceException tenantException = assertThrows(ServiceException.class, () -> service.get("m-1"));
        assertEquals(PROCESS_MODEL_NOT_CONTRACT.getCode(), tenantException.getCode());

        Model wrongCategory = model("m-2", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(wrongCategory.getCategory()).thenReturn("leave");
        when(bpmModelService.getModel("m-2")).thenReturn(wrongCategory);

        ServiceException categoryException = assertThrows(ServiceException.class, () -> service.get("m-2"));
        assertEquals(PROCESS_MODEL_NOT_CONTRACT.getCode(), categoryException.getCode());
    }

    @Test
    void create_rejectsMissingOrUnsupportedNodeModel() {
        ProcessDesignSaveReqVO missing = createRequest(null);
        assertSimpleModelRejected(() -> service.create(USER_ID, missing));

        for (int type : new int[]{13, 14, 15, 20, 54}) {
            BpmSimpleModelNodeVO simpleModel = validSimpleModel();
            simpleModel.getChildNode().setType(type);
            assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(simpleModel)));
        }
    }

    @Test
    void create_rejectsExecutableApprovalConfiguration() {
        BpmSimpleModelNodeVO skipModel = validSimpleModel();
        skipModel.getChildNode().setSkipExpression("");
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(skipModel)));

        BpmSimpleModelNodeVO listenerModel = validSimpleModel();
        listenerModel.getChildNode().setTaskCreateListener(new BpmSimpleModelNodeVO.ListenerHandler()
                .setEnable(false).setPath("https://example.invalid/callback"));
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(listenerModel)));

        BpmSimpleModelNodeVO autoApproveModel = validSimpleModel();
        autoApproveModel.getChildNode().setApproveType(2);
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(autoApproveModel)));

        BpmSimpleModelNodeVO expressionCandidateModel = validSimpleModel();
        expressionCandidateModel.getChildNode().setCandidateStrategy(
                BpmTaskCandidateStrategyEnum.EXPRESSION.getStrategy());
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(expressionCandidateModel)));

        BpmSimpleModelNodeVO signModel = validSimpleModel();
        signModel.getChildNode().setSignEnable(true);
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(signModel)));

        BpmSimpleModelNodeVO permissionModel = validSimpleModel();
        permissionModel.getChildNode().setFieldsPermission(List.of(Map.of("field", "amount")));
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(permissionModel)));
    }

    @Test
    void create_acceptsSafeBaseRuleAndRejectsExpressionsOrInjection() {
        when(bpmModelService.createModel(any())).thenReturn("m-safe");
        assertEquals("m-safe", service.create(USER_ID, createRequest(branchModel("amount", ">", "1000"))));

        BpmSimpleModelNodeVO expression = branchModel("amount", ">", "1000");
        expression.getChildNode().getChildNode().getConditionNodes().get(0).getConditionSetting()
                .setConditionType(BpmSimpleModeConditionTypeEnum.EXPRESSION.getType())
                .setConditionExpression("${true}");
        assertSimpleModelRejected(() -> service.create(USER_ID, createRequest(expression)));

        assertSimpleModelRejected(() -> service.create(USER_ID,
                createRequest(branchModel("amount)} || true || var:getOrDefault(x", ">", "1000"))));
        assertSimpleModelRejected(() -> service.create(USER_ID,
                createRequest(branchModel("amount", "matches", "1000"))));
        assertSimpleModelRejected(() -> service.create(USER_ID,
                createRequest(branchModel("amount", ">", "\" || true || \""))));
    }

    @Test
    void create_onlyAllowsRequiredCustomFieldsSharedByEveryPublishedType() {
        ContractTypeSimpleRespVO sales = new ContractTypeSimpleRespVO().setId(1L).setCurrentVersionId(11L);
        ContractTypeSimpleRespVO purchase = new ContractTypeSimpleRespVO().setId(2L).setCurrentVersionId(22L);
        when(contractTypeService.getContractTypeSimpleList()).thenReturn(List.of(sales, purchase));
        when(contractTypeService.getContractTypeVersion(11L)).thenReturn(new ContractTypeVersionDO()
                .setId(11L).setFormFields(List.of(
                        "{\"field\":\"sharedRequired\",\"$required\":true}",
                        "{\"field\":\"salesOnly\",\"$required\":true}")));
        when(contractTypeService.getContractTypeVersion(22L)).thenReturn(new ContractTypeVersionDO()
                .setId(22L).setFormFields(List.of(
                        "{\"field\":\"sharedRequired\",\"validate\":[{\"required\":true}]}",
                        "{\"field\":\"salesOnly\",\"$required\":false}")));
        when(bpmModelService.createModel(any())).thenReturn("m-shared");

        assertEquals("m-shared", service.create(USER_ID,
                createRequest(branchModel("sharedRequired", "==", "yes"))));
        assertSimpleModelRejected(() -> service.create(USER_ID,
                createRequest(branchModel("salesOnly", "==", "yes"))));
    }

    @Test
    void deploy_revalidatesStoredSimpleModelBeforePublishing() {
        Model contract = model("m-1", "clm_contract_purchase", "采购合同审批", null,
                canonicalMeta("contract approval"));
        when(bpmModelService.getModel("m-1")).thenReturn(contract);
        BpmSimpleModelNodeVO malicious = validSimpleModel();
        malicious.getChildNode().setTaskCompleteListener(new BpmSimpleModelNodeVO.ListenerHandler()
                .setEnable(true));
        when(bpmModelService.getSimpleModel("m-1")).thenReturn(malicious);

        assertSimpleModelRejected(() -> service.deploy(USER_ID, "m-1"));
        verify(bpmModelService, never()).deployModel(any(), any());
    }

    private Model model(String id, String key, String name, String deploymentId, String metaInfo) {
        Model model = org.mockito.Mockito.mock(Model.class);
        lenient().when(model.getId()).thenReturn(id);
        lenient().when(model.getKey()).thenReturn(key);
        lenient().when(model.getName()).thenReturn(name);
        lenient().when(model.getCategory()).thenReturn(CONTRACT_CATEGORY);
        lenient().when(model.getDeploymentId()).thenReturn(deploymentId);
        lenient().when(model.getMetaInfo()).thenReturn(metaInfo);
        lenient().when(model.getVersion()).thenReturn(4);
        lenient().when(model.getTenantId()).thenReturn(FlowableUtils.getTenantId());
        return model;
    }

    private ProcessDesignSaveReqVO createRequest(BpmSimpleModelNodeVO simpleModel) {
        ProcessDesignSaveReqVO reqVO = new ProcessDesignSaveReqVO();
        reqVO.setKey("clm_contract_test");
        reqVO.setName("合同审批测试");
        reqVO.setSimpleModel(simpleModel);
        return reqVO;
    }

    private BpmSimpleModelNodeVO validSimpleModel() {
        BpmSimpleModelNodeVO end = new BpmSimpleModelNodeVO()
                .setId("EndEvent").setName("结束")
                .setType(BpmSimpleModelNodeTypeEnum.END_NODE.getType());
        BpmSimpleModelNodeVO approve = new BpmSimpleModelNodeVO()
                .setId("Activity_approve").setName("法务审批")
                .setType(BpmSimpleModelNodeTypeEnum.APPROVE_NODE.getType())
                .setApproveType(BpmUserTaskApproveTypeEnum.USER.getType())
                .setCandidateStrategy(BpmTaskCandidateStrategyEnum.USER.getStrategy())
                .setCandidateParam("200002")
                .setFieldsPermission(List.of())
                .setTaskCreateListener(new BpmSimpleModelNodeVO.ListenerHandler().setEnable(false))
                .setTaskAssignListener(new BpmSimpleModelNodeVO.ListenerHandler().setEnable(false))
                .setTaskCompleteListener(new BpmSimpleModelNodeVO.ListenerHandler().setEnable(false))
                .setSignEnable(false)
                .setChildNode(end);
        return new BpmSimpleModelNodeVO()
                .setId("StartUserNode").setName("发起人")
                .setType(BpmSimpleModelNodeTypeEnum.START_USER_NODE.getType())
                .setFieldsPermission(List.of())
                .setChildNode(approve);
    }

    private BpmSimpleModelNodeVO branchModel(String leftSide, String opCode, String rightSide) {
        BpmSimpleModelNodeVO model = validSimpleModel();
        BpmSimpleModelNodeVO end = model.getChildNode().getChildNode();
        BpmSimpleModelNodeVO.ConditionRule rule = new BpmSimpleModelNodeVO.ConditionRule()
                .setLeftSide(leftSide).setOpCode(opCode).setRightSide(rightSide);
        BpmSimpleModelNodeVO.Condition condition = new BpmSimpleModelNodeVO.Condition()
                .setAnd(true).setRules(List.of(rule));
        BpmSimpleModelNodeVO.ConditionGroups groups = new BpmSimpleModelNodeVO.ConditionGroups()
                .setAnd(true).setConditions(List.of(condition));
        BpmSimpleModelNodeVO conditionNode = new BpmSimpleModelNodeVO()
                .setId("Flow_rule").setName("金额条件")
                .setType(BpmSimpleModelNodeTypeEnum.CONDITION_NODE.getType())
                .setConditionSetting(new BpmSimpleModelNodeVO.ConditionSetting()
                        .setDefaultFlow(false)
                        .setConditionType(BpmSimpleModeConditionTypeEnum.RULE.getType())
                        .setConditionGroups(groups));
        BpmSimpleModelNodeVO defaultNode = new BpmSimpleModelNodeVO()
                .setId("Flow_default").setName("其它情况")
                .setType(BpmSimpleModelNodeTypeEnum.CONDITION_NODE.getType())
                .setConditionSetting(new BpmSimpleModelNodeVO.ConditionSetting().setDefaultFlow(true));
        BpmSimpleModelNodeVO branch = new BpmSimpleModelNodeVO()
                .setId("Gateway_condition").setName("条件分支")
                .setType(BpmSimpleModelNodeTypeEnum.CONDITION_BRANCH_NODE.getType())
                .setConditionNodes(List.of(conditionNode, defaultNode))
                .setChildNode(end);
        model.getChildNode().setChildNode(branch);
        return model;
    }

    private void assertSimpleModelRejected(org.junit.jupiter.api.function.Executable executable) {
        ServiceException ex = assertThrows(ServiceException.class, executable);
        assertEquals(PROCESS_SIMPLE_MODEL_INVALID.getCode(), ex.getCode());
    }

    private String canonicalMeta(String description) {
        return "{\"description\":\"" + description + "\",\"type\":20,\"formType\":20,"
                + "\"formCustomCreatePath\":\"/clm/contract/create\","
                + "\"formCustomViewPath\":\"/clm/contract/bpm/index.vue\","
                + "\"managerUserIds\":[7],\"startUserIds\":[],\"startDeptIds\":[],\"sort\":1}";
    }

    private String canonicalMetaWithoutManagers(String description) {
        return "{\"description\":\"" + description + "\",\"type\":20,\"formType\":20,"
                + "\"formCustomCreatePath\":\"/clm/contract/create\","
                + "\"formCustomViewPath\":\"/clm/contract/bpm/index.vue\","
                + "\"managerUserIds\":[],\"startUserIds\":[],\"startDeptIds\":[],\"sort\":1}";
    }

}
