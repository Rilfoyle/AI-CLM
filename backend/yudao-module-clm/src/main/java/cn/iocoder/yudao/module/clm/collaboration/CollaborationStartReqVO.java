package cn.iocoder.yudao.module.clm.collaboration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CollaborationStartReqVO {
    @NotNull private Long contractId;
    @NotNull private Long revisionId;
    @NotNull private Long legalUserId;
    @NotBlank @Size(max = 1000) private String reason;
}
