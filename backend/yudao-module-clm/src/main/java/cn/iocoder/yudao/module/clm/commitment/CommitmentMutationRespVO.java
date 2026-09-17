package cn.iocoder.yudao.module.clm.commitment;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommitmentMutationRespVO {
    private Long commitmentId;
    private Long revisionId;
}
