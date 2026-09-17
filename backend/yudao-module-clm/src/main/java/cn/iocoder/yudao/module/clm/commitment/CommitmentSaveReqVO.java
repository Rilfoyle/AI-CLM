package cn.iocoder.yudao.module.clm.commitment;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CommitmentSaveReqVO {
    private Long id;
    @NotNull(message = "合同编号不能为空")
    private Long contractId;
    private Long baseRevisionId;
    @NotBlank(message = "承诺分类不能为空")
    private String category;
    @NotBlank(message = "承诺内容不能为空")
    private String content;
    private Long ownerUserId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
    private String riskLevel;
}
