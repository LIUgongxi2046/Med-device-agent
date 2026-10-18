package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentReviewReturnSchemaTest {
    private final PermissionSchema json=new PermissionSchema();
    private final DocumentReviewReturnSchema schema=new DocumentReviewReturnSchema(json);
    DocumentReviewReturnSchemaTest() throws Exception { }
    @Test void frozenFormalBaselineStillRequiresARealNonemptyCheckSet() throws Exception {
        var shared=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
        var baseline=json.object().put("document_revision",id(1)).put("expected_revision","1")
                .put("evidence_watermark","0").put("issue_watermark","0");baseline.putArray("check_ids");
        assertThrows(SchemaRejection.class,()->shared.validate("interfaces",baseline,"ReviewBaseline"));
        baseline.withArray("check_ids").add(id(2));assertDoesNotThrow(()->shared.validate("interfaces",baseline,"ReviewBaseline"));
    }
    @Test void independentCommandNeverAcceptsFormalBatchOrConfirmationFields() {
        var request=json.object();request.putObject("review_batch").put("decision","CONFIRM");
        assertThrows(SchemaRejection.class,()->schema.validate("Request",request));
        var property=json.object().put("review_id",id(1)).put("decision","CONFIRM");
        assertThrows(SchemaRejection.class,()->schema.validate("Decision",property));
    }
    @Test void malformedDuplicateAndOversizeRequestsNeverReachAService() {
        assertThrows(Exception.class,()->schema.request("{\"basis\":{},\"basis\":{}}".getBytes()));
        assertThrows(Exception.class,()->schema.request(new byte[262145]));
        assertThrows(Exception.class,()->schema.request("{} {}".getBytes()));
    }
    @Test void basisDigestDetectsEditedProof() throws Exception {
        ObjectNode basis=json.object().put("document_id",id(1)); basis.put("basis_sha256",schema.hash(basis));
        assertDoesNotThrow(()->schema.verifyDigest(basis,"basis_sha256"));
        basis.put("document_id",id(2)); assertThrows(TenantAccessException.class,()->schema.verifyDigest(basis,"basis_sha256"));
    }
    static String id(int n) { return "00000000-0000-4000-8000-"+String.format("%012d",n); }
}
