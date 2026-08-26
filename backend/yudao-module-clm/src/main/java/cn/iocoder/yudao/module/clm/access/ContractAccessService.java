package cn.iocoder.yudao.module.clm.access;

import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;

/**
 * 合同对象级权限 Service 接口
 *
 * 只看 owner / participant / 流程参与人，不存在"超级管理员旁路"
 */
public interface ContractAccessService {

    /**
     * 是否可查看：owner || participant.can_view || 流程参与人
     */
    boolean canView(ContractDO contract, Long userId);

    /**
     * 是否可编辑：(owner || participant.can_edit) && approvalStatus != RUNNING && lifecycle in (DRAFT, APPROVED)
     */
    boolean canEdit(ContractDO contract, Long userId);

    /**
     * 是否可下载：owner || participant.can_download || 流程参与人
     */
    boolean canDownload(ContractDO contract, Long userId);

    /**
     * 是否可管理参与人：owner || participant.can_manage
     */
    boolean canManage(ContractDO contract, Long userId);

    /**
     * 编辑/提交的主体条件：owner || participant.can_edit（不含状态判断）
     */
    boolean hasEditPrincipal(ContractDO contract, Long userId);

    /**
     * 当前用户是否参与过该合同的任一审批流程（发起人 / 任务处理人 / 涉及人）
     */
    boolean isProcessParticipant(ContractDO contract, Long userId);

    /**
     * 当前用户是否参与过指定流程实例
     */
    boolean isProcessInstanceParticipant(String processInstanceId, Long userId);

    void assertCanView(ContractDO contract, Long userId);

    void assertCanEdit(ContractDO contract, Long userId);

    void assertCanDownload(ContractDO contract, Long userId);

    void assertCanManage(ContractDO contract, Long userId);

    /**
     * 提交审批的主体条件校验（同 canEdit 的主体条件）；状态校验在提交流程中单独处理
     */
    void assertCanSubmit(ContractDO contract, Long userId);

    /**
     * 流程绑定详情的可见性：合同可见 或 当前用户是该流程实例的参与人
     */
    void assertCanViewBinding(ContractDO contract, String processInstanceId, Long userId);

}
