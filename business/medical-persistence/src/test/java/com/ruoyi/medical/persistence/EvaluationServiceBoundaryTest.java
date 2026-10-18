package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationServiceBoundaryTest {
    private final PermissionSchema json=new PermissionSchema();private final EvaluationSchema schema=new EvaluationSchema(json);
    EvaluationServiceBoundaryTest()throws Exception{}
    @Test void writeRoutesUseSeparateCommandsAndRejectUntrustedQueriesBeforeIdentity()throws Exception {
        var c=new EvaluationController(null,null,json);var http=http("tenant_id="+EvaluationFixture.id(1),false);
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->c.create(http,null,null,new byte[0],"1.0.0","1","safe-idempotency-key")).code());
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->c.score(http,null,null,EvaluationFixture.id(2),new byte[0],"1.0.0","1","safe-idempotency-key")).code());
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->c.finalizeResult(http,null,null,EvaluationFixture.id(2),new byte[0],"1.0.0","1","safe-idempotency-key")).code());
        var routes=Arrays.stream(EvaluationController.class.getMethods()).filter(m->m.getAnnotation(PostMapping.class)!=null).map(m->m.getAnnotation(PostMapping.class).value()[0]).toList();
        assertEquals(Set.of("/api/evaluation-batches","/api/evaluation-batches/{id}/scores","/api/evaluation-batches/{id}/finalize"),new HashSet<>(routes));
    }
    @Test void schemaContextAndIdempotencyAreRequiredBeforeIdentity(){
        var c=new EvaluationController(null,null,json);
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,assertThrows(SchemaRejection.class,()->c.create(null,null,null,new byte[0],null,"1","safe-idempotency-key")).code());
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->c.create(null,null,null,new byte[0],"1.0.0",null,"safe-idempotency-key")).code());
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->c.create(null,null,null,new byte[0],"1.0.0","1","short")).code());
    }
    @Test void duplicateBoundaryHeadersFailBeforeIdentity(){
        var c=new EvaluationController(null,null,json);
        assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->c.create(http(null,true),null,null,new byte[0],"1.0.0","1","safe-idempotency-key")).code());
    }
    @Test void serverOwnedDependencyPolicyCannotFallBackToClientEnvironment(){
        var p=new EvaluationDependencyPolicy(null,null,0,0,null);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,p::requireConfigured).code());
        assertThrows(IllegalArgumentException.class,()->new EvaluationDependencyPolicy.ArtifactProfile("SOURCE","source.external.process","urn:test","a".repeat(64),"b".repeat(64)));
    }
    @Test void idempotencyBindsOperationTargetBodyContextAndSession()throws Exception{
        var r=new EvaluationProtocol.Finalize("1.0.0","2","a".repeat(64));String hash=schema.requestHash("/api/evaluation-batches/one/finalize",r,"1","session");
        assertNotEquals(hash,schema.requestHash("/api/evaluation-batches/two/finalize",r,"1","session"));
        assertNotEquals(hash,schema.requestHash("/api/evaluation-batches/one/finalize",r,"2","session"));
        assertNotEquals(hash,schema.requestHash("/api/evaluation-batches/one/finalize",r,"1","other-session"));
        assertNotEquals(hash,schema.requestHash("/api/evaluation-batches/one/finalize",new EvaluationProtocol.Finalize("1.0.0","3","a".repeat(64)),"1","session"));
    }
    @Test void storedReceiptsCannotConvertCompletionIntoPublicationOrInjectClientVerdict()throws Exception{
        var receipt=json.object().put("schema_version","1.0.0").put("operation_id",EvaluationFixture.id(1)).put("evaluation_id",EvaluationFixture.id(2))
            .put("definition_sha256","a".repeat(64)).put("revision","3").put("status","COMPLETED").putNull("score_id").put("result_sha256","b".repeat(64)).put("verdict","PASS").put("production_eligible",false);
        var response=schema.envelope(receipt,EvaluationFixture.id(3),"3");assertEquals(response,schema.receipt(json.canonical(response)));
        receipt.put("production_eligible",true);assertThrows(SchemaRejection.class,()->schema.receipt(json.canonical(schema.envelope(receipt,EvaluationFixture.id(3),"3"))));
        receipt.put("production_eligible",false).put("status","RUNNING");assertThrows(SchemaRejection.class,()->schema.receipt(json.canonical(schema.envelope(receipt,EvaluationFixture.id(3),"3"))));
    }
    private static jakarta.servlet.http.HttpServletRequest http(String query,boolean duplicate){return (jakarta.servlet.http.HttpServletRequest)java.lang.reflect.Proxy.newProxyInstance(EvaluationServiceBoundaryTest.class.getClassLoader(),new Class[]{jakarta.servlet.http.HttpServletRequest.class},(object,method,args)->switch(method.getName()){
        case "getQueryString"->query;case "getHeaders"->Collections.enumeration(duplicate?List.of("1","2"):List.of("1"));default->throw new AssertionError(method.getName());});}
}
