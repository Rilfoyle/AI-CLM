package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserScopeItemMapper extends BaseMapperX<UserScopeItemDO> {
    /**
     * 范围明细是 UserScopeDO 的派生索引。更新范围时必须物理替换，否则逻辑删除行仍占用
     * uk_user_scope_item，下一次写入相同组织/合同类型会触发唯一键冲突。
     */
    @Delete("DELETE FROM clm_user_scope_item WHERE user_scope_id = #{userScopeId}")
    int deletePhysicallyByUserScopeId(@Param("userScopeId") Long userScopeId);
}
