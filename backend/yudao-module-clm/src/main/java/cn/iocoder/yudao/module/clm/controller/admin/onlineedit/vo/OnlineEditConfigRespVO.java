package cn.iocoder.yudao.module.clm.controller.admin.onlineedit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Schema(description = "管理后台 - CLM 在线编辑（ONLYOFFICE）编辑器配置 Response VO")
@Data
@Accessors(chain = true)
public class OnlineEditConfigRespVO {

    @Schema(description = "是否启用在线编辑", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean enabled;

    @Schema(description = "浏览器访问 Document Server 的地址", example = "http://127.0.0.1:8090")
    private String documentServerUrl;

    @Schema(description = "DocsAPI.DocEditor 的 config（documentType / document / editorConfig）")
    private Map<String, Object> config;

    @Schema(description = "对 config 签名的 HS256 JWT（Document Server 开启 JWT 时必需）")
    private String token;

}
