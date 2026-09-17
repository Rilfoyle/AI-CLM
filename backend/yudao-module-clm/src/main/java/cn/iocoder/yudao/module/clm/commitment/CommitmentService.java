package cn.iocoder.yudao.module.clm.commitment;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class CommitmentService {
    @Resource private CommitmentMapper commitmentMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractAccessService contractAccessService;
    @Resource private ContractRevisionService revisionService;

    public List<CommitmentRespVO> getList(Long contractId) {
        ContractDO contract = requiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        List<CommitmentRespVO> result = new ArrayList<>();
        for (CommitmentDO item : commitmentMapper.selectListByContractId(contractId)) {
            result.add(BeanUtils.toBean(item, CommitmentRespVO.class));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommitmentMutationRespVO create(CommitmentSaveReqVO reqVO) {
        ContractDO contract = editableContract(reqVO.getContractId());
        Long base = reqVO.getBaseRevisionId() == null ? contract.getCurrentRevisionId() : reqVO.getBaseRevisionId();
        CommitmentDO item = BeanUtils.toBean(reqVO, CommitmentDO.class).setId(null);
        commitmentMapper.insert(item);
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setNoCommitmentConfirmed(Boolean.FALSE));
        Long revisionId = revisionService.createSnapshot(contract.getId(), base, "COMMITMENT_ADD", "新增重大承诺");
        return new CommitmentMutationRespVO(item.getId(), revisionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommitmentMutationRespVO update(CommitmentSaveReqVO reqVO) {
        if (reqVO.getId() == null) throw exception(COMMITMENT_NOT_EXISTS);
        CommitmentDO old = requiredCommitment(reqVO.getId());
        if (!old.getContractId().equals(reqVO.getContractId())) throw exception(COMMITMENT_CONTRACT_MISMATCH);
        ContractDO contract = editableContract(old.getContractId());
        Long base = reqVO.getBaseRevisionId() == null ? contract.getCurrentRevisionId() : reqVO.getBaseRevisionId();
        commitmentMapper.updateById(BeanUtils.toBean(reqVO, CommitmentDO.class));
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setNoCommitmentConfirmed(Boolean.FALSE));
        Long revisionId = revisionService.createSnapshot(contract.getId(), base, "COMMITMENT_UPDATE", "修改重大承诺");
        return new CommitmentMutationRespVO(old.getId(), revisionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long delete(Long id, Long baseRevisionId) {
        CommitmentDO item = requiredCommitment(id);
        ContractDO contract = editableContract(item.getContractId());
        Long base = baseRevisionId == null ? contract.getCurrentRevisionId() : baseRevisionId;
        commitmentMapper.deleteById(id);
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setNoCommitmentConfirmed(Boolean.FALSE));
        return revisionService.createSnapshot(contract.getId(), base, "COMMITMENT_DELETE", "删除重大承诺");
    }

    @Transactional(rollbackFor = Exception.class)
    public Long confirmNone(Long contractId, Long baseRevisionId) {
        ContractDO contract = editableContract(contractId);
        if (commitmentMapper.selectCountByContractId(contractId) > 0) throw exception(COMMITMENT_NONE_CONFLICT);
        Long base = baseRevisionId == null ? contract.getCurrentRevisionId() : baseRevisionId;
        contractMapper.updateById(new ContractDO().setId(contractId).setNoCommitmentConfirmed(Boolean.TRUE));
        return revisionService.createSnapshot(contractId, base, "COMMITMENT_NONE", "确认无重大承诺");
    }

    private ContractDO editableContract(Long id) {
        ContractDO contract = requiredContract(id);
        contractAccessService.assertCanEdit(contract, SecurityFrameworkUtils.getLoginUserId());
        return contract;
    }
    private ContractDO requiredContract(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        return contract;
    }
    private CommitmentDO requiredCommitment(Long id) {
        CommitmentDO item = commitmentMapper.selectById(id);
        if (item == null) throw exception(COMMITMENT_NOT_EXISTS);
        return item;
    }
}
