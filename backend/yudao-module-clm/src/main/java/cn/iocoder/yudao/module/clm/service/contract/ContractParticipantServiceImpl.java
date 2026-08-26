package cn.iocoder.yudao.module.clm.service.contract;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractParticipantRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractParticipantSaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractParticipantMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmParticipantRoleEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PARTICIPANT_ROLE_INVALID;

/**
 * CLM 合同参与人 Service 实现类
 */
@Service
@Validated
public class ContractParticipantServiceImpl implements ContractParticipantService {

    @Resource
    private ContractParticipantMapper contractParticipantMapper;
    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ContractAccessService contractAccessService;
    @Resource
    private ClmAuditService clmAuditService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public List<ContractParticipantRespVO> getParticipantList(Long contractId) {
        ContractDO contract = getRequiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        List<ContractParticipantDO> list = contractParticipantMapper.selectListByContractId(contractId);
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(list, ContractParticipantDO::getPrincipalId));
        return convertList(list, participant -> {
            ContractParticipantRespVO vo = BeanUtils.toBean(participant, ContractParticipantRespVO.class);
            AdminUserRespDTO user = userMap == null ? null : userMap.get(participant.getPrincipalId());
            vo.setPrincipalName(user == null ? null : user.getNickname());
            return vo;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveParticipants(ContractParticipantSaveReqVO reqVO) {
        ContractDO contract = getRequiredContract(reqVO.getContractId());
        contractAccessService.assertCanManage(contract, SecurityFrameworkUtils.getLoginUserId());
        // 1. 整理入参：忽略 OWNER 项；校验角色；按 principalId 去重
        List<ContractParticipantDO> newList = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ContractParticipantSaveReqVO.Item item : ObjUtil.defaultIfNull(reqVO.getParticipants(),
                Collections.<ContractParticipantSaveReqVO.Item>emptyList())) {
            if (ClmParticipantRoleEnum.OWNER.getCode().equals(item.getRoleCode())) {
                continue;
            }
            if (!ClmParticipantRoleEnum.COLLABORATOR.getCode().equals(item.getRoleCode())
                    && !ClmParticipantRoleEnum.VIEWER.getCode().equals(item.getRoleCode())) {
                throw exception(PARTICIPANT_ROLE_INVALID, item.getRoleCode());
            }
            if (!seen.add(item.getPrincipalId())) {
                continue;
            }
            newList.add(new ContractParticipantDO()
                    .setContractId(contract.getId())
                    .setPrincipalType(StrUtil.blankToDefault(item.getPrincipalType(),
                            ContractParticipantDO.PRINCIPAL_TYPE_USER))
                    .setPrincipalId(item.getPrincipalId())
                    .setRoleCode(item.getRoleCode())
                    .setCanView(ObjUtil.defaultIfNull(item.getCanView(), true))
                    .setCanEdit(BooleanUtil.isTrue(item.getCanEdit()))
                    .setCanDownload(BooleanUtil.isTrue(item.getCanDownload()))
                    .setCanManage(BooleanUtil.isTrue(item.getCanManage())));
        }
        // 2. 现有非 OWNER 行
        List<ContractParticipantDO> oldAll = contractParticipantMapper.selectListByContractId(contract.getId());
        List<ContractParticipantDO> oldList = filterList(oldAll,
                p -> !ClmParticipantRoleEnum.OWNER.getCode().equals(p.getRoleCode()));
        Set<Long> ownerIds = convertSet(filterList(oldAll,
                p -> ClmParticipantRoleEnum.OWNER.getCode().equals(p.getRoleCode())),
                ContractParticipantDO::getPrincipalId);
        // OWNER 用户不允许再以其他角色出现
        newList.removeIf(p -> ownerIds.contains(p.getPrincipalId()));
        // 3. 差异更新
        List<List<ContractParticipantDO>> diff = diffList(oldList, newList,
                (o, n) -> Objects.equals(o.getPrincipalType(), n.getPrincipalType())
                        && Objects.equals(o.getPrincipalId(), n.getPrincipalId()));
        List<ContractParticipantDO> createList = diff.get(0);
        List<ContractParticipantDO> updateList = diff.get(1);
        List<ContractParticipantDO> deleteList = diff.get(2);
        if (CollUtil.isNotEmpty(createList)) {
            createList.forEach(contractParticipantMapper::insert);
        }
        if (CollUtil.isNotEmpty(updateList)) {
            Map<Long, ContractParticipantDO> oldMap = convertMap(oldList, ContractParticipantDO::getPrincipalId);
            for (ContractParticipantDO n : updateList) {
                ContractParticipantDO old = oldMap.get(n.getPrincipalId());
                n.setId(old.getId());
                contractParticipantMapper.updateById(n);
            }
        }
        if (CollUtil.isNotEmpty(deleteList)) {
            contractParticipantMapper.deleteByIds(convertList(deleteList, ContractParticipantDO::getId));
        }
        // 4. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("added", convertList(createList, ContractParticipantDO::getPrincipalId));
        detail.put("updated", convertList(updateList, ContractParticipantDO::getPrincipalId));
        detail.put("removed", convertList(deleteList, ContractParticipantDO::getPrincipalId));
        clmAuditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.PARTICIPANT_UPDATE, detail);
    }

    @Override
    public void createOwnerParticipant(Long contractId, Long ownerUserId) {
        contractParticipantMapper.insert(buildOwner(contractId, ownerUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeOwner(Long contractId, Long oldOwnerUserId, Long newOwnerUserId) {
        if (Objects.equals(oldOwnerUserId, newOwnerUserId)) {
            return;
        }
        List<ContractParticipantDO> all = contractParticipantMapper.selectListByContractId(contractId);
        // 1. 旧 OWNER 行 -> COLLABORATOR（保留）
        for (ContractParticipantDO p : all) {
            if (ClmParticipantRoleEnum.OWNER.getCode().equals(p.getRoleCode())) {
                contractParticipantMapper.updateById(new ContractParticipantDO().setId(p.getId())
                        .setRoleCode(ClmParticipantRoleEnum.COLLABORATOR.getCode()));
            }
        }
        // 2. 新 owner：已有行则升级，否则插入
        ContractParticipantDO existing = findFirst(all, p -> Objects.equals(p.getPrincipalId(), newOwnerUserId)
                && ContractParticipantDO.PRINCIPAL_TYPE_USER.equals(p.getPrincipalType()));
        if (existing != null) {
            contractParticipantMapper.updateById(buildOwner(contractId, newOwnerUserId).setId(existing.getId()));
        } else {
            contractParticipantMapper.insert(buildOwner(contractId, newOwnerUserId));
        }
    }

    @Override
    public void deleteByContractId(Long contractId) {
        contractParticipantMapper.deleteByContractId(contractId);
    }

    @Override
    public List<ContractParticipantDO> getParticipantDOList(Long contractId) {
        return contractParticipantMapper.selectListByContractId(contractId);
    }

    // ========== 私有方法 ==========

    private ContractParticipantDO buildOwner(Long contractId, Long userId) {
        return new ContractParticipantDO()
                .setContractId(contractId)
                .setPrincipalType(ContractParticipantDO.PRINCIPAL_TYPE_USER)
                .setPrincipalId(userId)
                .setRoleCode(ClmParticipantRoleEnum.OWNER.getCode())
                .setCanView(true).setCanEdit(true).setCanDownload(true).setCanManage(true);
    }

    private ContractDO getRequiredContract(Long contractId) {
        ContractDO contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        return contract;
    }

}
