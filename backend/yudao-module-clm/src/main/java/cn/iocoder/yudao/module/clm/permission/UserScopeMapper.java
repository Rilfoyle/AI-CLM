package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserScopeMapper extends BaseMapperX<UserScopeDO> {
    default UserScopeDO selectByUserAndRole(Long userId, String roleCode) {
        return selectOne(new LambdaQueryWrapperX<UserScopeDO>()
                .eq(UserScopeDO::getUserId, userId).eq(UserScopeDO::getRoleCode, roleCode).last("LIMIT 1"));
    }

    default List<UserScopeDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<UserScopeDO>()
                .eq(UserScopeDO::getUserId, userId).orderByAsc(UserScopeDO::getRoleCode));
    }

    @Select("SELECT COUNT(*) > 0 FROM clm_user_scope s "
            + "WHERE s.user_id = #{userId} AND s.role_code <> 'clm_system_admin' "
            + "AND s.status = 'ACTIVE' AND s.deleted = FALSE "
            + "AND (s.effective_from IS NULL OR s.effective_from <= CURRENT_TIMESTAMP) "
            + "AND (s.effective_to IS NULL OR s.effective_to > CURRENT_TIMESTAMP) "
            + "AND EXISTS (SELECT 1 FROM clm_user_scope_item i WHERE i.user_scope_id = s.id "
            + "AND i.scope_type = 'ORG' AND i.scope_id = #{orgId} AND i.deleted = FALSE) "
            + "AND EXISTS (SELECT 1 FROM clm_user_scope_item i WHERE i.user_scope_id = s.id "
            + "AND i.scope_type = 'CONTRACT_TYPE' AND i.scope_id = #{typeId} AND i.deleted = FALSE)")
    boolean hasViewScope(@Param("userId") Long userId, @Param("orgId") Long orgId, @Param("typeId") Long typeId);

    @Select("SELECT COUNT(*) > 0 FROM clm_user_scope s "
            + "WHERE s.user_id = #{userId} AND s.role_code = 'clm_contract_admin' "
            + "AND s.status = 'ACTIVE' AND s.deleted = FALSE "
            + "AND (s.effective_from IS NULL OR s.effective_from <= CURRENT_TIMESTAMP) "
            + "AND (s.effective_to IS NULL OR s.effective_to > CURRENT_TIMESTAMP) "
            + "AND EXISTS (SELECT 1 FROM clm_user_scope_item i WHERE i.user_scope_id = s.id "
            + "AND i.scope_type = 'ORG' AND i.scope_id = #{orgId} AND i.deleted = FALSE) "
            + "AND EXISTS (SELECT 1 FROM clm_user_scope_item i WHERE i.user_scope_id = s.id "
            + "AND i.scope_type = 'CONTRACT_TYPE' AND i.scope_id = #{typeId} AND i.deleted = FALSE)")
    boolean hasManageScope(@Param("userId") Long userId, @Param("orgId") Long orgId, @Param("typeId") Long typeId);
}
