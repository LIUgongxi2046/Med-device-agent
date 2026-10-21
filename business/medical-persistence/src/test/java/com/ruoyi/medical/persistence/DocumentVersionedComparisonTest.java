package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred version-boundary regressions. Not executed in the implementation-only session. */
class DocumentVersionedComparisonTest {
    private final SchemaCatalogue catalogue = SchemaCatalogue.fromClasspath();
    private final DocumentStructuralComparisonSchema schema = new DocumentStructuralComparisonSchema();
    private final DocumentStructuralComparisonEngine engine = new DocumentStructuralComparisonEngine(schema);
    private final DocumentStructuralComparisonEngine.Limits limits = new DocumentStructuralComparisonEngine.Limits(10,10,50,100000,100000,50,1000,100000,60000);
    private final InterfaceProtocol.CompareRequest request = new InterfaceProtocol.CompareRequest(id(1),id(2),id(3));
    DocumentVersionedComparisonTest() throws Exception { }

    @Test void declaredV2ProducesAdditiveCoreAndRetainsBothPublications() throws Exception {
        var left = typed("1.0.0","interfaces"); var right = typed("2.0.0","document-body-v2");
        var result = engine.compare(request,left,right,limits,ref("interfaces"),sha("interfaces"),ref("document-body-v2"),sha("document-body-v2"));
        assertEquals("2.0.0",result.path("schema_version").asText());
        assertEquals(ref("interfaces"),result.path("left_body_schema_ref").asText());
        assertEquals(sha("document-body-v2"),result.path("right_body_schema_sha256").asText());
        assertFalse(result.has("body_schema_sha256"));
        assertEquals(result,schema.validate(result));
        assertThrows(RuntimeException.class,()->engine.compare(request,left,right,limits));
        assertThrows(RuntimeException.class,()->engine.compare(request,left,right,limits,ref("interfaces"),sha("interfaces"),ref("interfaces"),sha("interfaces")));
    }

    @Test void originalV1OverloadAndExplicitV1ProduceIdenticalContract() throws Exception {
        var body=typed("1.0.0","interfaces");
        assertEquals(engine.compare(request,body,body,limits),engine.compare(request,body,body,limits,ref("interfaces"),sha("interfaces"),ref("interfaces"),sha("interfaces")));
    }

    @Test void storedHistoricalBuildProvenanceIsRetainedButChangedRecordsAreRejected() throws Exception {
        var body=typed("1.0.0","interfaces");var original=engine.compare(request,body,body,limits);
        original.put("implementation_sha256","a".repeat(64));rehash(original);
        assertEquals(original,engine.verifyRegisteredAgainstInputs(request,body,body,original,limits,ref("interfaces"),sha("interfaces"),ref("interfaces"),sha("interfaces")));
        assertThrows(TenantAccessException.class,()->engine.verifyAgainstInputs(request,body,body,original,limits));
        var forged=original.deepCopy();((ObjectNode)forged.path("records").get(0)).put("identity","forged");
        ((ObjectNode)forged.required("coverage")).put("delivered_sha256",schema.hash(forged.required("records")));
        assertThrows(TenantAccessException.class,()->engine.verifyRegisteredAgainstInputs(request,body,body,forged,limits,ref("interfaces"),sha("interfaces"),ref("interfaces"),sha("interfaces")));
    }

    @Test void uploadSyntaxCannotUseV1RequestPublicationToDeclareV2Body() throws Exception {
        var request=new JsonMapper().createObjectNode().put("purpose","DOCUMENT_BODY").put("document_id",id(1))
            .put("expected_revision","1").put("media_type","application/json").put("size_bytes","100")
            .put("sha256","a".repeat(64)).put("schema_ref",ref("document-body-v2")).put("dependency_access_set_id",id(6));
        var accepted=TypedSchemaBindings.bind(catalogue,InterfaceBindings.UPLOAD_REQUEST_V2,request);
        assertEquals(sha("document-body-v2"),accepted.payload().schemaSha256());
        assertEquals(ref("document-body-v2"),accepted.read().schemaRef());
        assertThrows(RuntimeException.class,()->TypedSchemaBindings.bind(catalogue,InterfaceBindings.UPLOAD_REQUEST,request));
        request.put("schema_ref","interfaces.schema.yaml#/$defs/DocumentBody");
        assertThrows(RuntimeException.class,()->TypedSchemaBindings.bind(catalogue,InterfaceBindings.UPLOAD_REQUEST_V2,request));
        assertEquals(sha("interfaces"),TypedSchemaBindings.bind(catalogue,InterfaceBindings.UPLOAD_REQUEST,request).payload().schemaSha256());
    }

    private void rehash(ObjectNode core)throws Exception{
        var input=core.deepCopy();for(String key:List.of("input_sha256","record_count","records","affected_node_ids","left_affected_section_ids","right_affected_section_ids","coverage"))input.remove(key);
        core.put("input_sha256",schema.hash(input));((ObjectNode)core.required("coverage")).put("selection_sha256",schema.hash(input));
    }
    private InterfaceProtocol.DocumentBody typed(String version,String publication)throws Exception{
        var body=new JsonMapper().createObjectNode().put("schema_version",version).put("template_version",id(4));
        for(String key:List.of("nodes","fields","evidence_links","fact_usages"))body.putArray(key);
        var root=body.withArray("nodes").addObject().put("node_id",id(5)).putNull("parent_id").put("ordinal",0);
        root.putArray("field_ids");root.putArray("evidence_link_ids");root.putObject("content").put("kind","DOCUMENT").put("title","Example");
        return DocumentEvidenceBindings.bind(catalogue,InterfaceBindings.DOCUMENT_BODY,body,ref(publication),sha(publication)).read();
    }
    private String ref(String publication){return catalogue.schemaRef(publication,"DocumentBody");}
    private String sha(String publication){return catalogue.schemaDigest(publication);}
    private static String id(int n){return "00000000-0000-4000-8000-%012d".formatted(n);}
}
