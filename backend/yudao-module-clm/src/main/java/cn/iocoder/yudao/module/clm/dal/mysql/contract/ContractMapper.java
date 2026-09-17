package cn.iocoder.yudao.module.clm.dal.mysql.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import cn.hutool.core.util.StrUtil;

/**
 * CLM 合同 Mapper
 */
@Mapper
public interface ContractMapper extends BaseMapperX<ContractDO> {

    /**
     * 分页查询合同，并在 SQL 层面按对象权限过滤：
     * owner_user_id = userId OR id IN (参与人 can_view 的合同)
     *
     * @param reqVO  查询条件
     * @param userId 当前登录用户编号
     * @return 分页结果
     */
    default PageResult<ContractDO> selectPage(ContractPageReqVO reqVO, Long userId) {
        String participantSql = "SELECT contract_id FROM clm_contract_participant"
                + " WHERE principal_type = '" + ContractParticipantDO.PRINCIPAL_TYPE_USER + "'"
                + " AND principal_id = " + userId
                + " AND can_view = TRUE AND deleted = FALSE";
        String authorizedScopeSql = "SELECT scoped.id FROM clm_contract scoped"
                + " JOIN clm_user_scope user_scope ON user_scope.tenant_id = scoped.tenant_id"
                + " AND user_scope.user_id = " + userId
                + " AND user_scope.role_code <> 'clm_system_admin'"
                + " AND user_scope.status = 'ACTIVE' AND user_scope.deleted = FALSE"
                + " AND (user_scope.effective_from IS NULL OR user_scope.effective_from <= CURRENT_TIMESTAMP)"
                + " AND (user_scope.effective_to IS NULL OR user_scope.effective_to > CURRENT_TIMESTAMP)"
                + " WHERE scoped.deleted = FALSE"
                + " AND EXISTS (SELECT 1 FROM clm_user_scope_item org_match"
                + " WHERE org_match.user_scope_id = user_scope.id AND org_match.scope_type = 'ORG'"
                + " AND org_match.scope_id = scoped.owner_dept_id AND org_match.deleted = FALSE)"
                + " AND EXISTS (SELECT 1 FROM clm_user_scope_item type_match"
                + " WHERE type_match.user_scope_id = user_scope.id AND type_match.scope_type = 'CONTRACT_TYPE'"
                + " AND type_match.scope_id = scoped.type_id AND type_match.deleted = FALSE)";
        String collaborationSql = "SELECT contract_id FROM clm_collaboration_case"
                + " WHERE (initiator_user_id = " + userId + " OR legal_user_id = " + userId + ")"
                + " AND deleted = FALSE";
        String approvalDecisionSql = "SELECT contract_id FROM clm_approval_task_revision_binding"
                + " WHERE creator = '" + userId + "' AND deleted = FALSE";
        LambdaQueryWrapperX<ContractDO> query = new LambdaQueryWrapperX<ContractDO>()
                .likeIfPresent(ContractDO::getTitle, reqVO.getTitle())
                .likeIfPresent(ContractDO::getContractNo, reqVO.getContractNo())
                .eqIfPresent(ContractDO::getTypeId,
                        reqVO.getContractTypeId() == null ? reqVO.getTypeId() : reqVO.getContractTypeId())
                .eqIfPresent(ContractDO::getStageCode, reqVO.getStageCode())
                .eqIfPresent(ContractDO::getOwnerDeptId, reqVO.getOrgId())
                .eqIfPresent(ContractDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(ContractDO::getLifecycleStatus, reqVO.getLifecycleStatus())
                .eqIfPresent(ContractDO::getOwnerUserId, reqVO.getOwnerUserId())
                .betweenIfPresent(ContractDO::getCreateTime, reqVO.getCreateTime());
        query.apply(StrUtil.isNotBlank(reqVO.getCounterpartyName()),
                "EXISTS (SELECT 1 FROM clm_contract_party cp JOIN clm_party p ON p.id = cp.party_id "
                        + "WHERE cp.contract_id = clm_contract.id AND cp.role_code = 'COUNTERPARTY' "
                        + "AND cp.deleted = FALSE AND p.deleted = FALSE AND p.name LIKE CONCAT('%', {0}, '%'))",
                reqVO.getCounterpartyName());
        switch (StrUtil.nullToEmpty(reqVO.getScope())) {
            case "HANDLED" -> query.eq(ContractDO::getOwnerUserId, userId);
            case "PARTICIPATED" -> query.and(w -> w.inSql(ContractDO::getId, participantSql)
                    .or().inSql(ContractDO::getId, collaborationSql));
            case "APPROVED_BY_ME" -> query.inSql(ContractDO::getId, approvalDecisionSql);
            case "AUTHORIZED_ORG" -> query.inSql(ContractDO::getId, authorizedScopeSql);
            default -> query.and(w -> w.eq(ContractDO::getOwnerUserId, userId)
                    .or().inSql(ContractDO::getId, participantSql)
                    .or().inSql(ContractDO::getId, collaborationSql)
                    .or().inSql(ContractDO::getId, approvalDecisionSql)
                    .or().inSql(ContractDO::getId, authorizedScopeSql));
        }
        return selectPage(reqVO, query.orderByDesc(ContractDO::getId));
    }

    default Long selectCountByTypeId(Long typeId) {
        return selectCount(ContractDO::getTypeId, typeId);
    }

    default PageResult<ContractDO> selectDeletedPage(ContractPageReqVO reqVO, Long userId) {
        long total = selectDeletedCount(reqVO, userId);
        if (total == 0L) {
            return PageResult.empty();
        }
        long offset = (long) (reqVO.getPageNo() - 1) * reqVO.getPageSize();
        return new PageResult<>(selectDeletedList(reqVO, userId, offset), total);
    }

    @Select({"<script>",
            "SELECT * FROM clm_contract WHERE deleted = TRUE",
            "AND owner_user_id = #{userId}",
            "<if test='reqVO.title != null and reqVO.title != \"\"'> AND title LIKE CONCAT('%', #{reqVO.title}, '%')</if>",
            "<if test='reqVO.contractNo != null and reqVO.contractNo != \"\"'> AND contract_no LIKE CONCAT('%', #{reqVO.contractNo}, '%')</if>",
            "<if test='reqVO.contractTypeId != null'> AND type_id = #{reqVO.contractTypeId}</if>",
            "<if test='reqVO.contractTypeId == null and reqVO.typeId != null'> AND type_id = #{reqVO.typeId}</if>",
            "<if test='reqVO.stageCode != null and reqVO.stageCode != \"\"'> AND stage_code = #{reqVO.stageCode}</if>",
            "<if test='reqVO.orgId != null'> AND owner_dept_id = #{reqVO.orgId}</if>",
            "<if test='reqVO.approvalStatus != null'> AND approval_status = #{reqVO.approvalStatus}</if>",
            "<if test='reqVO.lifecycleStatus != null'> AND lifecycle_status = #{reqVO.lifecycleStatus}</if>",
            "ORDER BY id DESC LIMIT #{offset}, #{reqVO.pageSize}",
            "</script>"})
    @ResultMap("mybatis-plus_ContractDO")
    List<ContractDO> selectDeletedList(@Param("reqVO") ContractPageReqVO reqVO,
                                       @Param("userId") Long userId,
                                       @Param("offset") long offset);

    @Select({"<script>",
            "SELECT COUNT(*) FROM clm_contract WHERE deleted = TRUE",
            "AND owner_user_id = #{userId}",
            "<if test='reqVO.title != null and reqVO.title != \"\"'> AND title LIKE CONCAT('%', #{reqVO.title}, '%')</if>",
            "<if test='reqVO.contractNo != null and reqVO.contractNo != \"\"'> AND contract_no LIKE CONCAT('%', #{reqVO.contractNo}, '%')</if>",
            "<if test='reqVO.contractTypeId != null'> AND type_id = #{reqVO.contractTypeId}</if>",
            "<if test='reqVO.contractTypeId == null and reqVO.typeId != null'> AND type_id = #{reqVO.typeId}</if>",
            "<if test='reqVO.stageCode != null and reqVO.stageCode != \"\"'> AND stage_code = #{reqVO.stageCode}</if>",
            "<if test='reqVO.orgId != null'> AND owner_dept_id = #{reqVO.orgId}</if>",
            "<if test='reqVO.approvalStatus != null'> AND approval_status = #{reqVO.approvalStatus}</if>",
            "<if test='reqVO.lifecycleStatus != null'> AND lifecycle_status = #{reqVO.lifecycleStatus}</if>",
            "</script>"})
    long selectDeletedCount(@Param("reqVO") ContractPageReqVO reqVO, @Param("userId") Long userId);

    @Select("SELECT * FROM clm_contract WHERE id = #{id} AND deleted = TRUE")
    @ResultMap("mybatis-plus_ContractDO")
    ContractDO selectDeletedById(@Param("id") Long id);

    @Update("UPDATE clm_contract SET deleted = FALSE, updater = #{updater}, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = TRUE")
    int restoreDeletedById(@Param("id") Long id, @Param("updater") String updater);

    /** 串行化同一合同的修订与首次编号分配。 */
    @Select("SELECT * FROM clm_contract WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    @ResultMap("mybatis-plus_ContractDO")
    ContractDO selectByIdForUpdate(Long id);

}
