package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real published schemas and DocumentBodySemantics; no mocks, domain approval or model inference. */
class DocumentTechnicalCheckEngineTest {
    final DocumentTechnicalCheckSchema schema=new DocumentTechnicalCheckSchema();
    final DocumentTechnicalCheckEngine engine=new DocumentTechnicalCheckEngine(schema);
    final DocumentBodySemantics.Limits limits=new DocumentBodySemantics.Limits(16,8,64);
    DocumentTechnicalCheckEngineTest()throws Exception{}

    @Test void executesRealStructureCheckButNeverClaimsProfessionalReadiness()throws Exception{
        var body=body();var input=input(body);var before=body.deepCopy();var manifestBefore=input.deepCopy();
        var result=engine.execute(body,input,limits);
        assertEquals("COMPLETED",result.required("execution_status").textValue());
        assertEquals("PASS",result.required("technical_result").textValue());assertTrue(result.required("findings").isEmpty());
        assertEquals("UNKNOWN",result.required("professional_coverage").textValue());assertFalse(result.required("review_ready").booleanValue());
        assertEquals(schema.hash(input),result.required("input_sha256").textValue());
        assertEquals(input,result.required("input"));assertEquals(before,body);assertEquals(manifestBefore,input);
        assertEquals(schema.catalogue().bind("interfaces",body,"DocumentBody").canonicalJson(),schema.canonical(body));
        assertEquals(schema.hash(input),result.at("/coverage/selection_sha256").textValue());
        assertEquals(result.at("/coverage/selection_sha256"),result.at("/coverage/delivered_sha256"));
    }
    @Test void explicitV2PublicationChecksStructureWithoutUpgradingLegacyManifest()throws Exception{
        var body=body().put("schema_version","2.0.0");var declared=input(body);
        declared.put("body_schema_ref",schema.catalogue().schemaRef("document-body-v2","DocumentBody"));
        declared.put("body_schema_sha256",schema.catalogue().schemaDigest("document-body-v2"));
        assertEquals("PASS",engine.execute(body,declared,limits).path("technical_result").asText());
        assertEquals("FAIL",engine.execute(body,input(body),limits).path("technical_result").asText());
    }
    @Test void structureFailureIsCompletedTechnicalFindingNotProfessionalPass()throws Exception{
        var body=body();node(body,1).put("parent_id",id(99));
        var result=engine.execute(body,input(body),limits);
        assertEquals("COMPLETED",result.path("execution_status").textValue());assertEquals("FAIL",result.path("technical_result").textValue());
        assertEquals("DOCUMENT_STRUCTURE_INVALID",result.at("/findings/0/code").textValue());
        assertEquals("UNKNOWN",result.path("professional_coverage").textValue());assertFalse(result.path("review_ready").booleanValue());
    }
    @Test void duplicateCyclesAndForeignReferencesAreActuallyChecked()throws Exception{
        for(int mutation=0;mutation<4;mutation++){
            var body=body();
            if(mutation==0)node(body,1).put("node_id",id(10));
            if(mutation==1)node(body,1).put("parent_id",id(12));
            if(mutation==2)node(body,2).withArray("field_ids").add(id(98));
            if(mutation==3)node(body,2).withArray("evidence_link_ids").add(id(98));
            assertEquals("FAIL",engine.execute(body,input(body),limits).path("technical_result").textValue());
        }
    }
    @Test void malformedBodySchemaDoesNotBecomeAnEmptySuccessfulCheck()throws Exception{
        var body=body();body.put("unexpected",true);
        assertEquals("FAIL",engine.execute(body,input(body),limits).path("technical_result").textValue());
    }
    @Test void modifiedActualBodyCannotReuseFixedManifest()throws Exception{
        var body=body();var input=input(body);node(body,2).withObject("content").put("text","Changed");
        assertMismatch(()->engine.execute(body,input,limits));
    }
    @Test void bodySchemaIdentityMustBeTheActualPublishedDefinition()throws Exception{
        var body=body();var input=input(body);input.put("body_schema_sha256","e".repeat(64));
        assertMismatch(()->engine.execute(body,input,limits));
        var changed=input(body);changed.put("body_schema_ref",schema.catalogue().schemaRef("interfaces","Node"));
        assertMismatch(()->engine.execute(body,changed,limits));
    }
    @Test void callerLimitsMustMatchManifestAndCapacityFailureDoesNotProduceCompletion()throws Exception{
        var body=body();var input=input(body);
        assertMismatch(()->engine.execute(body,input,new DocumentBodySemantics.Limits(15,8,64)));
        var small=new DocumentBodySemantics.Limits(2,8,64);((ObjectNode)input.required("limits")).put("maximum_nodes",2);
        assertEquals(ErrorCode.CONTEXT_LIMIT,assertThrows(TenantAccessException.class,()->engine.execute(body,input,small)).code());
        assertThrows(NullPointerException.class,()->engine.execute(body,input,null));
        assertThrows(IllegalArgumentException.class,()->new DocumentBodySemantics.Limits(0,8,64));
    }
    @Test void noUnknownFieldsOrInventedProfessionalApprovalCanEnterContracts()throws Exception{
        var body=body();var input=input(body);input.put("rule_version",id(90));assertInvalid(()->schema.input(input));
        var result=engine.execute(body,input(body),limits);result.put("professional_coverage","COMPLETE");assertInvalid(()->schema.result(result));
        var ready=engine.execute(body,input(body),limits);ready.put("review_ready",true);assertInvalid(()->schema.result(ready));
    }
    @Test void resultHashesAndCoverageCannotBeAlteredIndependently()throws Exception{
        var body=body();var result=engine.execute(body,input(body),limits);result.put("input_sha256","e".repeat(64));assertMismatch(()->schema.result(result));
        var changed=engine.execute(body,input(body),limits);((ObjectNode)changed.required("coverage")).put("delivered_sha256","d".repeat(64));assertMismatch(()->schema.result(changed));
        var baseline=engine.execute(body,input(body),limits);((ObjectNode)baseline.required("input")).put("baseline_sha256","b".repeat(64));assertMismatch(()->schema.result(baseline));
    }
    @Test void completedPassCannotContainFailFindingsAndFailCannotOmitThem()throws Exception{
        var body=body();var result=engine.execute(body,input(body),limits);result.put("technical_result","FAIL");assertInvalid(()->schema.result(result));
        node(body,1).put("parent_id",id(99));var failed=engine.execute(body,input(body),limits);failed.put("technical_result","PASS");assertInvalid(()->schema.result(failed));
    }
    @Test void identityAndInputChangesChangeResultAndCoverageDigest()throws Exception{
        var body=body();var input=input(body);var original=engine.execute(body,input,limits);
        for(String key:List.of("tenant_id","project_id","document_id","document_revision","execution_manifest_id")){
            var changed=input.deepCopy().put(key,id(90));var result=engine.execute(body,changed,limits);
            assertNotEquals(original.path("input_sha256"),result.path("input_sha256"));
        }
        input.put("baseline_sha256","c".repeat(64));assertNotEquals(original.path("input_sha256"),engine.execute(body,input,limits).path("input_sha256"));
    }
    @Test void returnedInputsAreDefensiveCopiesAndDeterministic()throws Exception{
        var body=body();var input=input(body);var first=engine.execute(body,input,limits);var second=engine.execute(body,input,limits);
        assertEquals(first,second);var checked=schema.result(first);checked.withObject("input").put("document_id",id(99));assertNotEquals(checked,first);
        node(body,2).withObject("content").put("text","new");input.put("baseline_sha256","f".repeat(64));assertEquals(first,second);
    }
    @Test void requestParserRejectsDuplicatesTrailingValuesUnknownFieldsAndInvalidReasons()throws Exception{
        var request=request();assertEquals(request,schema.parse("Request",schema.canonical(request).getBytes(StandardCharsets.UTF_8),4096));
        String raw=schema.canonical(request);
        assertInvalid(()->schema.parse("Request",("{\"schema_version\":\"1.0.0\","+raw.substring(1)).getBytes(StandardCharsets.UTF_8),4096));
        assertInvalid(()->schema.parse("Request",(raw+" {}").getBytes(StandardCharsets.UTF_8),4096));
        request.put("reason"," padded ");assertInvalid(()->schema.validate("Request",request));
        var unknown=request();unknown.put("tenant_id",id(1));assertInvalid(()->schema.validate("Request",unknown));
        var overflow=request();overflow.put("expected_revision","18446744073709551616");assertThrows(RuntimeException.class,()->schema.validate("Request",overflow));
        assertEquals(ErrorCode.CONTEXT_LIMIT,assertThrows(TenantAccessException.class,()->schema.parse("Request",raw.getBytes(StandardCharsets.UTF_8),1)).code());
    }
    @Test void receiptAndReadCannotOverclaimCurrentnessOrProfessionalCoverage()throws Exception{
        var receipt=receipt();assertEquals(receipt,schema.validate("Receipt",receipt));
        var read=read();
        assertEquals(read,schema.validate("Read",read));read.put("currentness","STALE");assertInvalid(()->schema.validate("Read",read));
        read.withArray("invalidation_reasons").add("DOCUMENT_REVISION_CHANGED");schema.validate("Read",read);
        read.put("currentness","CURRENT");assertInvalid(()->schema.validate("Read",read));
        receipt.put("overall_review_ready",true);assertInvalid(()->schema.validate("Receipt",receipt));
    }
    @Test void executionManifestBindsActualImplementationBytesAndLocalSchema()throws Exception{
        byte[] implementation;
        try(var in=DocumentTechnicalCheckEngine.class.getResourceAsStream("DocumentTechnicalCheckEngine.class")){implementation=in.readAllBytes();}
        var m=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("manifest_kind","TECHNICAL_CHECK_MANIFEST")
            .put("checker_id",DocumentTechnicalCheckSchema.CHECKER).put("implementation_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(implementation))
            .put("schema_manifest_sha256",schema.manifest());m.set("limits",input(body()).required("limits"));
        assertEquals(m,schema.validate("ExecutionManifest",m));assertTrue(implementation.length>0);
        try(var in=DocumentTechnicalCheckSchema.class.getResourceAsStream("/document-technical-check.schema.json")){
            assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes()),schema.schemaDocumentSha256());
        }
        assertNotEquals(schema.schemaDocumentSha256(),schema.manifest());
        m.put("schema_manifest_sha256","f".repeat(64));assertMismatch(()->schema.validate("ExecutionManifest",m));
    }
    @Test void readRequiresActualFindingsAndExactReceiptAndCoverageIdentities()throws Exception{
        var original=read();assertEquals(original,schema.validate("Read",original));
        for(String required:List.of("result","coverage")){
            var missing=read();missing.remove(required);assertInvalid(()->schema.validate("Read",missing));
        }
        for(String key:List.of("document_id","document_revision","input_sha256","technical_result")){
            var changed=read();var receipt=changed.withObject("receipt");
            receipt.put(key,key.equals("technical_result")?"FAIL":key.equals("input_sha256")?"e".repeat(64):id(99));
            assertMismatch(()->schema.validate("Read",changed));
        }
        var coverage=read();coverage.withObject("coverage").put("selection_sha256","e".repeat(64)).put("delivered_sha256","e".repeat(64));
        assertMismatch(()->schema.validate("Read",coverage));
        for(String artifact:List.of("coverage_artifact","findings_artifact")){
            var changed=read();changed.withObject("receipt").withObject(artifact).put("content_sha256","e".repeat(64));
            assertMismatch(()->schema.validate("Read",changed));
        }
        var changed=read();changed.withObject("result").withObject("input").put("baseline_sha256","f".repeat(64));
        assertMismatch(()->schema.validate("Read",changed));
        var closed=read();closed.put("private_baseline","hidden");assertInvalid(()->schema.validate("Read",closed));
    }
    @Test void recoveryStatesRequireTheirExactSafeFieldsAndBindCompletedReceipt()throws Exception{
        var recovery=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("state","NOT_FOUND")
            .put("execution_mode","SYNC_REQUEST").put("overall_review_ready",false);
        recovery.putNull("request_body_sha256");recovery.putNull("check_id");recovery.putNull("receipt");
        assertEquals(recovery,schema.validate("Recovery",recovery));
        var wrong=recovery.deepCopy().put("check_id",id(20));assertInvalid(()->schema.validate("Recovery",wrong));
        recovery.put("state","PROCESSING");assertInvalid(()->schema.validate("Recovery",recovery));
        var receipt=receipt();recovery.set("check_id",receipt.required("check_id"));recovery.set("request_body_sha256",receipt.required("request_body_sha256"));
        schema.validate("Recovery",recovery);recovery.set("receipt",receipt);assertInvalid(()->schema.validate("Recovery",recovery));
        recovery.put("state","COMPLETED");assertEquals(recovery,schema.validate("Recovery",recovery));
        var check=recovery.deepCopy().put("check_id",id(99));assertMismatch(()->schema.validate("Recovery",check));
        var request=recovery.deepCopy().put("request_body_sha256","e".repeat(64));assertMismatch(()->schema.validate("Recovery",request));
        var leak=recovery.deepCopy().put("object_ref","private");assertInvalid(()->schema.validate("Recovery",leak));
    }
    private ObjectNode read()throws Exception{
        var body=body();var result=engine.execute(body,input(body),limits);var receipt=receipt();
        receipt.set("input_sha256",result.required("input_sha256"));
        receipt.withObject("coverage_artifact").put("content_sha256",schema.hash(result.required("coverage")));
        receipt.withObject("findings_artifact").put("content_sha256",schema.hash(result));
        var read=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("currentness","CURRENT");
        read.set("receipt",receipt);read.set("result",result);read.set("coverage",result.required("coverage").deepCopy());read.putArray("invalidation_reasons");return read;
    }
    private ObjectNode input(JsonNode body)throws Exception{
        var input=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("tenant_id",id(1)).put("project_id",id(2))
            .put("document_id",id(3)).put("document_revision",id(4)).put("body_sha256",schema.hash(body)).put("baseline_sha256","a".repeat(64))
            .put("body_schema_ref",schema.bodySchemaRef()).put("body_schema_sha256",schema.bodySchemaSha256()).put("execution_manifest_id",id(5))
            .put("check_kind",DocumentTechnicalCheckSchema.KIND).put("checker_id",DocumentTechnicalCheckSchema.CHECKER);
        input.putObject("limits").put("maximum_nodes",limits.maximumNodes()).put("maximum_table_cells",limits.maximumTableCells()).put("maximum_relationships",limits.maximumRelationships());return input;
    }
    private ObjectNode request(){return JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("document_revision",id(4)).put("expected_revision","1").put("reason","Check saved document");}
    private ObjectNode receipt(){
        var r=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("check_id",id(20)).put("job_id",id(21)).put("operation_id",id(22))
            .put("document_id",id(3)).put("document_revision",id(4)).put("input_sha256","a".repeat(64)).put("technical_execution","COMPLETE")
            .put("technical_result","PASS").put("professional_coverage","UNKNOWN").put("overall_review_ready",false).put("request_body_sha256","b".repeat(64));
        r.putObject("coverage_artifact").put("version_id",id(23)).put("content_sha256","c".repeat(64));r.putObject("findings_artifact").put("version_id",id(24)).put("content_sha256","d".repeat(64));return r;
    }
    private static ObjectNode body(){
        var body=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("template_version",id(6));
        body.putArray("nodes");body.putArray("fields");body.putArray("evidence_links");body.putArray("fact_usages");
        add(body,10,null,"DOCUMENT").withObject("content").put("title","Fixed test document");
        add(body,11,id(10),"PARAGRAPH").withObject("content").put("title","Text");
        add(body,12,id(11),"TEXT").withObject("content").put("text","A statement whose professional support has not been checked.").putArray("marks");return body;
    }
    private static ObjectNode add(ObjectNode body,int n,String parent,String kind){var node=body.withArray("nodes").addObject().put("node_id",id(n)).put("parent_id",parent).put("ordinal",0);node.putArray("field_ids");node.putArray("evidence_link_ids");node.putObject("content").put("kind",kind);return node;}
    private static ObjectNode node(JsonNode body,int i){return (ObjectNode)body.required("nodes").get(i);}
    private static String id(int n){return "00000000-0000-4000-8000-%012d".formatted(n);}
    @FunctionalInterface interface Checked{void run()throws Exception;}
    private static void assertMismatch(Checked work){assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,work::run).code());}
    private static void assertInvalid(Checked work){assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,work::run).code());}
}
