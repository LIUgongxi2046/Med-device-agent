package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred: schema acceptance is not an approved source-context capability. */
class SharedFactScopeContractTest {
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 private ObjectNode proposal()throws Exception{return (ObjectNode)json.read("""
 {"expected_revision":"0","content":{"fact_key":"volume","fact_class":"PRODUCT",
 "raw_value":"5","decimal_value":"5","unit":null,"model_scope":"NOT_APPLICABLE",
 "condition_scope":"NOT_APPLICABLE","document_id":"00000000-0000-4000-8000-000000000001",
 "document_revision":"00000000-0000-4000-8000-000000000002",
 "evidence_link_ids":["00000000-0000-4000-8000-000000000003"]}}
 """);}
 private void shape(ObjectNode value)throws Exception{new SharedFactLifecycleCodec().request("Proposal",json.canonical(value).getBytes(StandardCharsets.UTF_8));}
 @Test void legacyExplicitUnscopedShapeRemainsSupported()throws Exception{assertDoesNotThrow(()->shape(proposal()));}
 @Test void scopedModelsCannotOmitSourceContext()throws Exception{var p=proposal();((ObjectNode)p.required("content")).put("model_scope","SCOPED");assertThrows(RuntimeException.class,()->shape(p));}
 @Test void contextReferenceShapeDoesNotItselfGrantApproval()throws Exception{var p=proposal();var c=(ObjectNode)p.required("content");c.put("model_scope","SCOPED");c.putObject("scope_context").put("version_id","00000000-0000-4000-8000-000000000004").put("content_sha256","a".repeat(64));assertDoesNotThrow(()->shape(p));}
 @Test void numericalEvidenceCannotSelectAnUndeclaredLink()throws Exception{var c=(ObjectNode)proposal().required("content");c.putArray("value_evidence_link_ids").add("00000000-0000-4000-8000-000000000005");var rule=json.read("{\"operator\":\"DECIMAL_EXACT\",\"scale\":0,\"unit\":null}");assertThrows(IllegalArgumentException.class,()->SharedFactExactCheck.evaluate(c,rule,Map.of()));}
}
