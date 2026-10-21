package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred: authored, not executed during code-first development. */
class StrictFieldHumanSchemaTest {
 private static final String ID="12345678-1234-4234-8234-123456789abc";
 private ObjectNode request()throws Exception{return (ObjectNode)new TaskSourceVerifierSchema().read("{\"schema_version\":\"1.0.0\",\"document_revision\":\""+ID+"\",\"field_id\":\""+ID+"\",\"row_id\":null,\"expected_revision\":\"0\",\"baseline_sha256\":\""+"a".repeat(64)+"\",\"check_id\":\""+ID+"\",\"strict_artifact_id\":\""+ID+"\",\"decision\":\"CONFIRM\",\"reason\":\"Checked exact saved field\",\"question\":null}");}
 @Test void closedConfirmCannotSupplyApproval()throws Exception{var schema=new StrictFieldHumanSchema();var r=request();schema.validate(r);r.put("machine_pass",true);assertThrows(SchemaRejection.class,()->schema.validate(r));}
 @Test void confirmNeedsActualStrictOutput()throws Exception{var schema=new StrictFieldHumanSchema();var r=request().putNull("strict_artifact_id");assertThrows(SchemaRejection.class,()->schema.validate(r));}
 @Test void evidenceRequestNeedsRealQuestion()throws Exception{var schema=new StrictFieldHumanSchema();var r=request().put("decision","REQUEST_EVIDENCE").putNull("strict_artifact_id");assertThrows(SchemaRejection.class,()->schema.validate(r));r.put("question","Which approved method covers this condition?");schema.validate(r);}
 @Test void confirmationCannotMasqueradeAsQuestion()throws Exception{var schema=new StrictFieldHumanSchema();var r=request().put("question","please supply evidence");assertThrows(SchemaRejection.class,()->schema.validate(r));}
 @Test void rejectsNumericCasAndUnknownDecision()throws Exception{var schema=new StrictFieldHumanSchema();var r=request().put("expected_revision",0);assertThrows(SchemaRejection.class,()->schema.validate(r));var invalid=request().put("decision","APPROVE");assertThrows(SchemaRejection.class,()->schema.validate(invalid));}
 @Test void rejectsDuplicateAndTrailingJson()throws Exception{var schema=new StrictFieldHumanSchema();assertThrows(Exception.class,()->schema.request("{\"decision\":\"CONFIRM\",\"decision\":\"NOT_APPLICABLE\"}".getBytes()));assertThrows(Exception.class,()->schema.request((request().toString()+" {}").getBytes()));}
}
