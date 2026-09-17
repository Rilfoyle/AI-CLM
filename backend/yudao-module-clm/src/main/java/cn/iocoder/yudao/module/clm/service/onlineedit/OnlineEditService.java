package cn.iocoder.yudao.module.clm.service.onlineedit;

import cn.iocoder.yudao.module.clm.controller.admin.onlineedit.vo.OnlineEditConfigRespVO;
import cn.iocoder.yudao.module.clm.service.document.DocumentDownloadResult;

import java.util.Map;

/**
 * CLM 在线编辑（ONLYOFFICE）Service 接口
 */
public interface OnlineEditService {

    /**
     * 构建编辑器配置（受登录保护）
     *
     * 校验：版本存在；mode=edit 需 clm:contract:update + assertCanEdit + 版本未冻结 + 为文档当前版本；
     * mode=view 需 clm:contract:query + assertCanView；文件类型仅 docx/doc/xlsx/xls/pptx/ppt。审计 ONLINE_EDIT_OPEN。
     *
     * @param versionId 文档版本编号
     * @param mode      edit / view
     * @param userId    当前用户编号
     * @return 编辑器配置
     */
    OnlineEditConfigRespVO buildConfig(Long versionId, String mode, Long userId);

    /**
     * Document Server 拉取文件（开放接口，令牌鉴权）；使用 token 时重新校验用户当前查看 ACL，撤权立即失效；
     * 不写下载审计
     *
     * @param token file 用途的紧凑令牌
     * @return 版本元数据 + 内容
     */
    DocumentDownloadResult loadFile(String token);

    /**
     * Document Server 保存回调（开放接口，令牌鉴权）
     *
     * 所有状态先校验 callback key 与 token 目标版本一致；status ∈ {2,6} 时仅从 configured Document Server
     * 同源 URL 拉取新文件（不跟随重定向、限制响应大小），并按内容幂等地创建 ONLINE_EDIT 新版本；
     * 其它 status 不拉取文件。
     * 令牌 / JWT 校验失败抛 ONLINE_EDIT_TOKEN_INVALID；拉取失败抛 ONLINE_EDIT_CALLBACK_FETCH_FAILED。
     *
     * @param token   callback 用途的紧凑令牌
     * @param body    回调 body（status / url / key / users / forcesavetype / token）
     * @param jwt     Document Server 的 JWT（body.token 或 Authorization 头），可空
     * @return 新建的版本编号；未新建（幂等 / 非保存状态）返回 null
     */
    Long handleCallback(String token, Map<String, Object> body, String jwt);

}
