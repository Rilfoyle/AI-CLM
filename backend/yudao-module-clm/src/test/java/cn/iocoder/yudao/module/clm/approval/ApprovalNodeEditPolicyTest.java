package cn.iocoder.yudao.module.clm.approval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalNodeEditPolicyTest {

    @Test
    void nodePolicyOverridesDefaultAndClassifiesMajorFields() {
        String json = "{\"default\":{\"enabled\":false},\"nodes\":{\"legalReview\":"
                + "{\"enabled\":true,\"editableFields\":[\"name\",\"parties\"],"
                + "\"majorFields\":[\"parties\"]}}}";
        ApprovalNodeEditPolicy policy = ApprovalNodeEditPolicy.parse(json, "legalReview");
        assertTrue(policy.enabled());
        assertTrue(policy.allows("name"));
        assertTrue(policy.allows("parties"));
        assertTrue(policy.isMajor("parties"));
        assertFalse(policy.allows("amount"));
    }

    @Test
    void missingOrMalformedPolicyDefaultsToDeny() {
        assertFalse(ApprovalNodeEditPolicy.parse(null, "task").enabled());
        assertFalse(ApprovalNodeEditPolicy.parse("not-json", "task").enabled());
    }
}
