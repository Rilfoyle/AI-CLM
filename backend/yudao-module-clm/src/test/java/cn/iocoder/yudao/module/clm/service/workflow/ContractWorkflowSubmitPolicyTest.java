package cn.iocoder.yudao.module.clm.service.workflow;

import cn.iocoder.yudao.module.clm.commitment.CommitmentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.template.TemplateVersionDO;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractWorkflowSubmitPolicyTest {

    @Test
    void unchangedPublishedStandardTemplate_canDirectSubmit() {
        assertTrue(ContractWorkflowServiceImpl.canDirectSubmitFromTemplate(
                new ContractDO().setSourceMode("TEMPLATE"),
                new ContractRevisionDO().setTemplateVersionId(1L).setMainDocumentVersionId(2L),
                new DocumentVersionDO().setId(2L).setChecksumSha256("same"),
                new TemplateVersionDO().setId(1L).setStatus("PUBLISHED").setChecksumSha256("same"),
                Collections.emptyList()));
    }

    @Test
    void uploadChangedInactiveOrHighRisk_requiresLegalConclusion() {
        ContractRevisionDO revision = new ContractRevisionDO().setTemplateVersionId(1L).setMainDocumentVersionId(2L);
        DocumentVersionDO document = new DocumentVersionDO().setId(2L).setChecksumSha256("changed");
        TemplateVersionDO template = new TemplateVersionDO().setId(1L).setStatus("PUBLISHED").setChecksumSha256("base");
        assertFalse(ContractWorkflowServiceImpl.canDirectSubmitFromTemplate(
                new ContractDO().setSourceMode("UPLOAD"), revision, document, template, Collections.emptyList()));
        assertFalse(ContractWorkflowServiceImpl.canDirectSubmitFromTemplate(
                new ContractDO().setSourceMode("TEMPLATE"), revision, document, template, Collections.emptyList()));
        assertFalse(ContractWorkflowServiceImpl.canDirectSubmitFromTemplate(
                new ContractDO().setSourceMode("TEMPLATE"), revision,
                new DocumentVersionDO().setId(2L).setChecksumSha256("base"),
                new TemplateVersionDO().setId(1L).setStatus("INACTIVE").setChecksumSha256("base"),
                Collections.emptyList()));
        assertFalse(ContractWorkflowServiceImpl.canDirectSubmitFromTemplate(
                new ContractDO().setSourceMode("TEMPLATE"), revision,
                new DocumentVersionDO().setId(2L).setChecksumSha256("base"),
                new TemplateVersionDO().setId(1L).setStatus("PUBLISHED").setChecksumSha256("base"),
                List.of(new CommitmentDO().setRiskLevel("HIGH"))));
    }
}
