package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PermissionPolicyMapper extends BaseMapperX<PermissionPolicyVersionDO> {
    default PermissionPolicyVersionDO selectPublished() {
        return selectOne(new LambdaQueryWrapperX<PermissionPolicyVersionDO>()
                .eq(PermissionPolicyVersionDO::getStatus, "PUBLISHED")
                .orderByDesc(PermissionPolicyVersionDO::getVersionNo).last("LIMIT 1"));
    }

    default PermissionPolicyVersionDO selectDraft() {
        return selectOne(new LambdaQueryWrapperX<PermissionPolicyVersionDO>()
                .eq(PermissionPolicyVersionDO::getStatus, "DRAFT").last("LIMIT 1"));
    }

    default Integer selectMaxVersionNo() {
        List<PermissionPolicyVersionDO> list = selectList(new LambdaQueryWrapperX<PermissionPolicyVersionDO>()
                .select(PermissionPolicyVersionDO::getVersionNo)
                .orderByDesc(PermissionPolicyVersionDO::getVersionNo).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getVersionNo();
    }
}
