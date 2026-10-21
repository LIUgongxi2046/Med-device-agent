package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Deferred: source added under the explicit no-test-run instruction. */
class BusinessOperationsContractTest {
    @Test void zeroDenominatorNeverBecomesZeroPercent(){
        var metric=BusinessOperationsService.ratio(0,0,"cohort");
        assertTrue(metric.required("rate").isNull());
        assertEquals("INSUFFICIENT_EVIDENCE",metric.required("evidence_status").asText());
    }
    @Test void noActualAdoptionNeverInventsZeroAdoptionRate(){assertTrue(BusinessOperationsService.adoption(0,12).required("rate").isNull());}
    @Test void numeratorAndDenominatorRemainInspectable(){
        var metric=BusinessOperationsService.ratio(1,3,"cohort");
        assertEquals(1,metric.required("numerator").asInt());assertEquals(3,metric.required("denominator").asInt());
        assertEquals("0.33333333",metric.required("rate").asText());
    }
    @Test void feedbackCannotSkipActualMilestones(){
        assertThrows(TenantAccessException.class,()->OperationsFeedbackService.requireTransition("OPEN","CLOSED"));
        assertThrows(TenantAccessException.class,()->OperationsFeedbackService.requireTransition("REVISION_LINKED","PUBLISHED"));
        assertThrows(TenantAccessException.class,()->OperationsFeedbackService.requireTransition("CLOSED","REVIEWED"));
        assertThrows(TenantAccessException.class,()->OperationsFeedbackService.requireTransition("REJECTED","CLOSED"));
    }
    @Test void fullMilestoneSequenceIsExplicit(){
        String[] states={"OPEN","REVIEWED","REVISION_LINKED","EVALUATED","PUBLISHED","CLOSED"};
        for(int i=1;i<states.length;i++)OperationsFeedbackService.requireTransition(states[i-1],states[i]);
    }
    @Test void queriesAreBoundedAndProjectScoped(){
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->new BusinessOperationsService.Query(
            "11111111-1111-4111-8111-111111111111",java.time.Instant.parse("2026-01-01T00:00:00Z"),java.time.Instant.parse("2026-12-01T00:00:00Z"),null));
    }
    @Test void csvNeutralizesLeadingControlAndFormulaPrefixes(){
        assertEquals("\"'  \t=SUM(1,2)\"",BusinessOperationsController.cell("  \t=SUM(1,2)"));
        assertEquals("\"'@IMPORT\"",BusinessOperationsController.cell("@IMPORT"));
        assertEquals("\"12.50\"",BusinessOperationsController.cell("12.50"));
    }
    @Test void existingPermissionAuthoringAcceptsScopedOperationsActions()throws Exception{
        var schema=new PermissionSchema();String id="11111111-1111-4111-8111-111111111111";
        for(String action:java.util.List.of("report.read","report.export","feedback.create","feedback.read","feedback.manage")){
            String body="""
                {"schema_version":"1.0.0","decision":"GRANT","subject_id":"%s",
                "scope":{"tenant_id":"%s","project_id":"%s","resource_kind":"PROJECT","resource_id":"%s"},
                "action":"%s","grant_id":null,"condition":{"schema_version":"1.0.0","identity_modes":["local"],"purposes":["BUSINESS_OPERATIONS"],
                "delegation":{"actions":[],"role_ids":[],"resource_kinds":[],"project_ids":[],"resource_ids":[]}},
                "valid_from":null,"valid_to":null,"basis":{"version_id":"%s","content_sha256":"%s"},"expected_revision":"0","reason":"authorized operations scope"}
                """.formatted(id,id,id,id,action,id,"0".repeat(64));
            var request=schema.request(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));assertEquals(action,request.action());
            assertEquals(java.util.List.of("BUSINESS_OPERATIONS"),request.condition().purposes());
        }
    }
}
