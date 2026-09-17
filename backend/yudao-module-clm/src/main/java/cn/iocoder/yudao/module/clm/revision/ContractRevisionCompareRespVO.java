package cn.iocoder.yudao.module.clm.revision;

import lombok.Data;

import java.util.List;

@Data
public class ContractRevisionCompareRespVO {
    private ContractRevisionRespVO fromRevision;
    private ContractRevisionRespVO toRevision;
    private List<String> changedFields;
    private Boolean partiesChanged;
    private Boolean commitmentsChanged;
    private Boolean documentChanged;
    private String summary;
    private List<RevisionChangeRespVO> fieldChanges;
    private List<RevisionChangeRespVO> partyChanges;
    private List<RevisionChangeRespVO> commitmentChanges;
    private List<RevisionChangeRespVO> documentChanges;
}
