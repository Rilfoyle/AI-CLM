package cn.iocoder.yudao.module.clm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合同参与人角色
 */
@Getter
@AllArgsConstructor
public enum ClmParticipantRoleEnum {

    OWNER("OWNER", "负责人"),
    COLLABORATOR("COLLABORATOR", "协作人"),
    VIEWER("VIEWER", "查看人");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmParticipantRoleEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
