package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic immutable expected-item artifacts; no customer data or actual approvals are generated. */
class EvaluationDenominatorTest {
    private final PermissionSchema json=new PermissionSchema();private final EvaluationSchema schema=new EvaluationSchema(json);private final EvaluationVerdict engine=new EvaluationVerdict(schema);
    EvaluationDenominatorTest()throws Exception{}
    private EvaluationProtocol.Definition definition(String dimension)throws Exception{return EvaluationFixture.definition(schema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),List.of(dimension),2);}
    private List<EvaluationProtocol.Score> history(EvaluationProtocol.Definition d)throws Exception{
        var result=new ArrayList<EvaluationProtocol.Score>();int i=1;for(var cell:engine.matrix(d.request()))result.add(EvaluationFixture.score(2000+i,d,schema.hash(d),EvaluationFixture.append(d,schema.hash(d),cell,Integer.toString(i++),null,"9","10")));return result;
    }
    @Test void firstObservationCannotChooseASmallerDenominatorThanTheFrozenApplicableSet()throws Exception{
        var d=definition("EVAL-02");var input=EvaluationFixture.append(d,schema.hash(d),engine.matrix(d.request()).get(0),"1",null,"9","9");
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->engine.requireAppend(d,schema.hash(d),List.of(),input)).code());
        assertThrows(TenantAccessException.class,()->engine.compute(d,schema.hash(d),List.of(EvaluationFixture.score(2001,d,schema.hash(d),input))));
    }
    @Test void regressionNineOfTenCannotBecomePassBySupersedingWithNineOfNine()throws Exception{
        var d=definition("EVAL-02");var history=history(d);assertEquals("FAIL",engine.compute(d,schema.hash(d),history).verdict());
        var first=history.get(0);var correction=EvaluationFixture.append(d,schema.hash(d),first.observation().cell(),"5",first.score_id(),"9","9");
        assertThrows(TenantAccessException.class,()->engine.requireAppend(d,schema.hash(d),history,correction));
        var tampered=new ArrayList<>(history);tampered.add(EvaluationFixture.score(3000,d,schema.hash(d),correction));assertThrows(TenantAccessException.class,()->engine.compute(d,schema.hash(d),tampered));
    }
    @Test void numeratorCorrectionsAreAllowedWithTheSameApprovedApplicableSet()throws Exception{
        var d=definition("EVAL-02");var history=new ArrayList<>(history(d));int next=5;
        for(var original:List.copyOf(history)) {
            var correction=EvaluationFixture.append(d,schema.hash(d),original.observation().cell(),Integer.toString(next),original.score_id(),"10","10");
            assertDoesNotThrow(()->engine.requireAppend(d,schema.hash(d),history,correction));history.add(EvaluationFixture.score(3000+next++,d,schema.hash(d),correction));
        }
        var result=engine.compute(d,schema.hash(d),history);assertEquals("PASS",result.verdict());assertFalse(result.production_eligible());
    }
    @Test void everyCandidateAndRepeatSharesTheSameExpectedDenominator()throws Exception{
        var d=definition("EVAL-06");for(var cell:engine.matrix(d.request())) {
            var smaller=EvaluationFixture.append(d,schema.hash(d),cell,"1",null,"1","1");assertThrows(TenantAccessException.class,()->engine.requireAppend(d,schema.hash(d),List.of(),smaller));
            var equal=EvaluationFixture.append(d,schema.hash(d),cell,"1",null,"10","10");assertDoesNotThrow(()->engine.requireAppend(d,schema.hash(d),List.of(),equal));
        }
    }
    @Test void legalControlsAlsoUseTheirOwnFrozenApplicableSet()throws Exception{
        var d=definition("EVAL-11");var r=EvaluationFixture.append(d,schema.hash(d),engine.matrix(d.request()).get(0),"1",null,"10","10");
        var wrong=new EvaluationProtocol.Append(r.schema_version(),r.expected_revision(),r.definition_sha256(),r.cell(),r.input_ref(),r.output_ref(),r.evidence_refs(),r.numerator(),r.denominator(),r.unknown_count(),r.violation_count(),"1","1",r.status(),r.supersedes(),r.reason());
        assertThrows(TenantAccessException.class,()->engine.requireAppend(d,schema.hash(d),List.of(),wrong));
    }
    @Test void exactArtifactDigestAndApprovedRuleBindingAreBothRequired()throws Exception{
        var d=definition("EVAL-02");var r=d.request();var sample=r.samples().get(0);var changed=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(r);
        ((com.fasterxml.jackson.databind.node.ArrayNode)changed.required("samples").get(0).required("expectations").get(0).required("applicable_item_ids")).remove(0);
        // Even recomputing the outer sample-manifest hash cannot rewrite the existing expected-artifact digest.
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed.required("sample_manifest_artifact")).put("content_sha256",schema.hash(changed.required("samples")));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(json.canonical(changed).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var unapproved=new EvaluationProtocol.Definition(d.schema_version(),d.tenant_id(),d.evaluation_id(),r,d.candidate_snapshots(),List.of(),d.authority_grant_id(),d.authority_revision(),d.created_by(),d.created_at());
        var result=engine.compute(unapproved,schema.hash(unapproved),history(unapproved));assertEquals("INSUFFICIENT_EVIDENCE",result.verdict());assertTrue(result.missing_requirements().contains("UNAPPROVED_EXPECTED_ITEM_SET"));
    }
    @Test void approvedExpectedItemsCannotBeTransplantedOntoAnotherInputOrSample()throws Exception {
        var d=definition("EVAL-02");var original=d.request();
        assertEquals(original,schema.create(json.canonicalObject(original).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for(String field:List.of("input_ref","sample_id","source_family","document_type")) {
            var changed=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(original);var sample=(com.fasterxml.jackson.databind.node.ObjectNode)changed.required("samples").get(0);
            if(field.equals("input_ref"))sample.set(field,json.tree(EvaluationFixture.ref(999)));
            else sample.put(field,field.equals("document_type")?"DIFFERENT_DOCUMENT":EvaluationFixture.id(999));
            // Existing approved expected_ref is unchanged; a fresh outer manifest cannot authorize a different pairing.
            ((com.fasterxml.jackson.databind.node.ObjectNode)changed.required("sample_manifest_artifact")).put("content_sha256",schema.hash(changed.required("samples")));
            assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(json.canonical(changed).getBytes(java.nio.charset.StandardCharsets.UTF_8)),field);
        }
    }
    @Test void bothArtifactShapesHaveExactPublishedSchemasAndCanonicalBindings()throws Exception {
        var d=definition("EVAL-02");var catalogue=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
        var manifest=catalogue.bind("evaluation-results",json.tree(d.request().samples()),"SampleManifest");
        var expected=catalogue.bind("evaluation-results",schema.expectedItems(d.request().samples().get(0)),"ExpectedItems");
        assertEquals(EvaluationSchema.SAMPLE_MANIFEST_REF,manifest.schemaRef());assertEquals(EvaluationSchema.EXPECTED_ITEMS_REF,expected.schemaRef());
        assertEquals(d.request().sample_manifest_artifact().content_sha256(),com.ruoyi.medical.contracts.WireValues.sha256(manifest.canonicalBytes()));
        assertEquals(d.request().samples().get(0).expected_ref().content_sha256(),com.ruoyi.medical.contracts.WireValues.sha256(expected.canonicalBytes()));
    }
    @Test void ruleBindingRequiresExactExpectedArtifactIdAndHash()throws Exception {
        var d=definition("EVAL-02");var r=d.request();var sample=r.samples().get(0);
        var rs=new RuleVersionSchema(json,new TemplateApplicabilitySchema(json),new RuleExpression());var authority=new EvaluationRuleAuthority(null,null,rs,json,new com.ruoyi.medical.identity.IdentityDirectoryPolicy("TEST"),null);
        var expression=new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,"true",List.of(),List.of());
        for(var basis:List.of(sample.expected_ref(),new PermissionProtocol.Basis(sample.expected_ref().version_id(),"f".repeat(64)),EvaluationFixture.ref(999))) {
            var content=new TemplateApplicabilityProtocol.RuleContent("1.0.0",RuleExpression.ENGINE,expression,List.of(basis));
            var rule=rs.version(new RuleVersionProtocol.VersionInput("1.0.0",null,RuleVersionSchema.CONTENT_REF,content,"Synthetic expected-item binding"));
            assertEquals(basis.equals(sample.expected_ref())?List.of(sample.sample_id()):List.of(),authority.boundSamples(r,rule));
        }
    }
}
