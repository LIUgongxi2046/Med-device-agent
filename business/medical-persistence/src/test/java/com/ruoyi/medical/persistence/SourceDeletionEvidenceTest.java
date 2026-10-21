package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SourceDeletionEvidenceTest {
    private final PermissionSchema json=new PermissionSchema();
    SourceDeletionEvidenceTest()throws Exception{}
    @Test void legacyAggregateCannotBecomePerCopyReceipt(){
        var proof=json.object().put("schema_version","1.0.0");
        var target=new SourceDeletionTarget("ORIGINAL","s",null,null,"key","v","a".repeat(64));
        assertThrows(TenantAccessException.class,()->SourceDeletionEvidence.requirePrimary(proof,target,"hash","revision"));
    }
    @Test void recoveryRequiresProviderAcknowledgementForClaimedDeletion(){
        String revision="11111111-1111-4111-8111-111111111111";
        var proof=json.object().put("schema_version","2.0.0").put("target_sha256","hash").put("config_revision",revision)
            .put("worker_id",revision).put("generation","1").put("deleted_at","2026-10-21T00:00:00Z")
            .put("query_confirmed_at","2026-10-21T00:00:01Z");
        var stores=proof.putObject("stores").put("kind","ORIGINAL").putArray("stores");
        var store=stores.addObject().put("store_id","original").put("remaining","0").put("object_key","key")
            .put("deleted_versions","1").put("observation","DELETE_ACKNOWLEDGED_AND_ABSENT");
        var receipt=store.putArray("copy_receipts").addObject().put("object_key","key").put("object_version","v")
            .put("delete_acknowledged",true).put("absence_confirmed",true);
        var target=new SourceDeletionTarget("ORIGINAL","s",null,null,"key","v","a".repeat(64));
        assertThrows(TenantAccessException.class,()->SourceDeletionEvidence.requirePrimary(proof,target,"hash",revision));
        receipt.put("provider_request_id","actual-provider-request");
        assertDoesNotThrow(()->SourceDeletionEvidence.requirePrimary(proof,target,"hash",revision));
    }
}
