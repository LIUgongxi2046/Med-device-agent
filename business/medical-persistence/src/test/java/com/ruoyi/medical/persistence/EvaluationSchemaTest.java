package com.ruoyi.medical.persistence;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationSchemaTest {
    private final PermissionSchema json=new PermissionSchema();private final EvaluationSchema schema=new EvaluationSchema(json);
    EvaluationSchemaTest()throws Exception{}
    private byte[] bytes(Object value)throws Exception{return json.canonicalObject(value).getBytes(StandardCharsets.UTF_8);}
    @Test void frozenSampleArtifactMustBindExactListAndCandidateIdsCannotRepeat()throws Exception {
        var good=EvaluationFixture.create(schema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),List.of("EVAL-02"),2);
        assertEquals(good,schema.create(bytes(good)));var modified=json.tree(good).deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)modified.required("samples")).removeAll();
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(bytes(modified)));
        var duplicate=json.tree(good).deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.required("candidates")).add(duplicate.required("candidates").get(0));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(bytes(duplicate)));
    }
    @Test void sourceFamilyCannotLeakAcrossSplits()throws Exception{
        var a=EvaluationFixture.sample(1000,"REAL","DEVELOPMENT");var b=new EvaluationProtocol.Sample(EvaluationFixture.id(1001),a.source_family(),"ACCEPTANCE","PTR","REAL",a.input_ref(),a.expected_ref(),a.expectations());
        var request=EvaluationFixture.create(schema,List.of(a,b),List.of("EVAL-02"),1);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(bytes(request)));
    }
    @Test void clientCannotSupplyPassScorerTenantOrProductionEligibility()throws Exception{
        var d=EvaluationFixture.definition(schema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),List.of("EVAL-02"),1);
        var a=EvaluationFixture.append(d,schema.hash(d),new EvaluationVerdict(schema).matrix(d.request()).get(0),"1",null,"2","2");
        assertEquals(a,schema.append(bytes(a)));
        for(String field:List.of("verdict","scorer_id","tenant_id","production_eligible")){
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(a);node.put(field,"PASS");
            assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.append(bytes(node)),field);
        }
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(a);node.put("status","PASS");
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.append(bytes(node)));
    }
    @Test void denominatorZeroIsMissingEvidenceRatherThanInventedZeroScore()throws Exception{
        var d=EvaluationFixture.definition(schema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),List.of("EVAL-02"),1);
        var a=EvaluationFixture.append(d,schema.hash(d),new EvaluationVerdict(schema).matrix(d.request()).get(0),"1",null,"0","0");assertEquals(a,schema.append(bytes(a)));
        var bad=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(a);bad.put("numerator","1");assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.append(bytes(bad)));
    }
    @Test void rejectsDuplicateKeysTrailingTokensAndUnboundedMatrix()throws Exception{
        var request=EvaluationFixture.create(schema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),List.of("EVAL-02"),1);
        String valid=json.canonicalObject(request);assertThrows(Exception.class,()->schema.create((valid+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class,()->schema.create(valid.replace("\"schema_version\":\"1.0.0\"","\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"").getBytes(StandardCharsets.UTF_8)));
        var large=EvaluationFixture.create(schema,java.util.stream.IntStream.range(1000,1100).mapToObj(n->EvaluationFixture.sample(n,"REAL","ACCEPTANCE")).toList(),List.of("EVAL-02","EVAL-06"),100);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(bytes(large)));
    }
}
