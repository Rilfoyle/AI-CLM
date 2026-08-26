package cn.iocoder.yudao.module.clm.service.contract;

import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractParticipantRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractParticipantSaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * CLM 合同参与人 Service 接口
 */
public interface ContractParticipantService {

    /**
     * 获得合同参与人列表；要求合同可见
     */
    List<ContractParticipantRespVO> getParticipantList(Long contractId);

    /**
     * 保存参与人（差异更新，不含 OWNER）；要求可管理
     */
    void saveParticipants(@Valid ContractParticipantSaveReqVO reqVO);

    /**
     * 创建 OWNER 行（全部权限）
     */
    void createOwnerParticipant(Long contractId, Long ownerUserId);

    /**
     * 负责人变更：旧 OWNER 行降为 COLLABORATOR（保留），新 owner 插入/升级为 OWNER
     */
    void changeOwner(Long contractId, Long oldOwnerUserId, Long newOwnerUserId);

    /**
     * 逻辑删除合同的全部参与人
     */
    void deleteByContractId(Long contractId);

    List<ContractParticipantDO> getParticipantDOList(Long contractId);

}
