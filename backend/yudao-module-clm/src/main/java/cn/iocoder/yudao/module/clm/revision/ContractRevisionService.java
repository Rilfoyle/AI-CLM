package cn.iocoder.yudao.module.clm.revision;

import java.util.List;

public interface ContractRevisionService {
    ContractRevisionSaveRespVO save(ContractRevisionSaveReqVO reqVO);
    /** 仅供已完成 Flowable assignee + 节点政策校验的审批门面调用。 */
    ContractRevisionSaveRespVO saveForApproval(ContractRevisionSaveReqVO reqVO);
    Long createInitialRevision(Long contractId, Long templateVersionId, String changeSource);
    Long createSnapshot(Long contractId, Long baseRevisionId, String changeSource, String changeReason);
    /** 供已完成令牌校验、但没有 HTTP 登录态的可信回调传播真实操作者。 */
    Long createSnapshot(Long contractId, Long baseRevisionId, String changeSource, String changeReason,
                        Long actorUserId);
    List<ContractRevisionRespVO> getList(Long contractId);
    ContractRevisionCompareRespVO compare(Long fromRevisionId, Long toRevisionId);
    ContractRevisionDO getRequiredRevision(Long id);
}
