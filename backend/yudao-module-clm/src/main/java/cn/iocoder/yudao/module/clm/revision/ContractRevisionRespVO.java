package cn.iocoder.yudao.module.clm.revision;

import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ContractRevisionRespVO {
    private Long id;
    private Long contractId;
    private Integer revisionNo;
    private Long baseRevisionId;
    private Long contractTypeVersionId;
    private Long templateVersionId;
    private Long mainDocumentVersionId;
    /** 兼容前端命名：主文档版本。 */
    private Long documentVersionId;
    private List<Long> documentVersionIds;
    private Map<String, Object> fields;
    private String name;
    private BigDecimal amount;
    private String currency;
    private String startDate;
    private String endDate;
    private Long ourPartyId;
    private List<Long> counterpartyIds;
    private List<Map<String, Object>> parties;
    private List<Map<String, Object>> commitments;
    private Boolean noCommitment;
    private String changeSource;
    /** 兼容前端命名。 */
    private String sourceType;
    private String changeReason;
    private String creator;
    private String creatorName;
    private LocalDateTime createTime;
}
