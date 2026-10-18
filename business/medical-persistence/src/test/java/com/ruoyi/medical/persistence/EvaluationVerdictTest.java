package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationVerdictTest {
    private final EvaluationSchema schema=new EvaluationSchema(new PermissionSchema());private final EvaluationVerdict engine=new EvaluationVerdict(schema);
    EvaluationVerdictTest()throws Exception{}
    private EvaluationProtocol.Definition definition(List<String> dimensions,int repeats)throws Exception{return EvaluationFixture.definition(schema,List.of(EvaluationFixture.sample(1000,"REAL","ACCEPTANCE")),dimensions,repeats);}
    private List<EvaluationProtocol.Score> scores(EvaluationProtocol.Definition d,String numerator,String denominator)throws Exception{
        String hash=schema.hash(d);var result=new ArrayList<EvaluationProtocol.Score>();int i=1;
        for(var cell:engine.matrix(d.request())){result.add(EvaluationFixture.score(2000+i,d,hash,EvaluationFixture.append(d,hash,cell,Integer.toString(i),null,numerator,denominator)));i++;}return result;
    }
    @Test void exactCartesianMatrixIncludesEveryCandidateDimensionAndRepeat()throws Exception{
        var d=definition(List.of("EVAL-02","EVAL-06"),3);assertEquals(12,engine.matrix(d.request()).size());
        var all=scores(d,"10","10");var complete=engine.compute(d,schema.hash(d),all);assertEquals("PASS",complete.verdict());assertFalse(complete.production_eligible());
        assertEquals(6,complete.dimensions().get(0).expected_cells());assertEquals(6,complete.dimensions().get(0).passed_cells());
        var omitted=engine.compute(d,schema.hash(d),all.subList(0,11));assertEquals("INSUFFICIENT_EVIDENCE",omitted.verdict());assertTrue(omitted.missing_requirements().contains("MISSING_SCORE"));
    }
    @Test void failuresCannotBeAveragedAwayAndMissingCellsStillStayInsufficient()throws Exception{
        var d=definition(List.of("EVAL-02"),2);var all=new ArrayList<>(scores(d,"10","10"));var original=all.get(0);
        all.set(0,EvaluationFixture.score(2001,d,schema.hash(d),EvaluationFixture.append(d,schema.hash(d),original.observation().cell(),"1",null,"9","10")));
        assertEquals("FAIL",engine.compute(d,schema.hash(d),all).verdict());assertEquals("INSUFFICIENT_EVIDENCE",engine.compute(d,schema.hash(d),all.subList(0,3)).verdict());
    }
    @Test void unknownQualityThresholdsAndZeroDenominatorsCannotPass()throws Exception{
        for(String dim:List.of("EVAL-01","EVAL-03","EVAL-04","EVAL-05","EVAL-08","EVAL-09","EVAL-10")){
            var d=definition(List.of(dim),1);var result=engine.compute(d,schema.hash(d),scores(d,"10","10"));assertEquals("INSUFFICIENT_EVIDENCE",result.verdict(),dim);
            assertTrue(result.missing_requirements().contains("UNAPPROVED_STATISTICAL_THRESHOLD:"+dim));
        }
        var d=EvaluationFixture.withApplicableCount(schema,definition(List.of("EVAL-02"),1),0);var result=engine.compute(d,schema.hash(d),scores(d,"0","0"));assertEquals("INSUFFICIENT_EVIDENCE",result.verdict());assertNull(result.cells().get(0).value());
    }
    @Test void simulationsAndEmptyDatasetsNeverBecomeBusinessAcceptance()throws Exception{
        for(var samples:List.of(List.of(EvaluationFixture.sample(1000,"SIMULATION","ACCEPTANCE")),List.<EvaluationProtocol.Sample>of())){
            var d=EvaluationFixture.definition(schema,samples,List.of("EVAL-02"),1);var result=engine.compute(d,schema.hash(d),scores(d,"10","10"));
            assertEquals("INSUFFICIENT_EVIDENCE",result.verdict());assertFalse(result.production_eligible());
        }
    }
    @Test void correctionsAppendOneChainAndDoNotOverwriteHistoricalScores()throws Exception{
        var d=definition(List.of("EVAL-02"),1);var history=new ArrayList<>(scores(d,"8","10"));var first=history.get(0);String hash=schema.hash(d);
        var request=EvaluationFixture.append(d,hash,first.observation().cell(),"3",first.score_id(),"10","10");assertDoesNotThrow(()->engine.requireAppend(d,hash,history,request));
        var correction=EvaluationFixture.score(3000,d,hash,request);history.add(correction);var result=engine.compute(d,hash,history);
        assertEquals(2,result.effective_scores().size());assertEquals(correction,result.effective_scores().get(0));assertEquals("8",first.observation().numerator());
        var stale=EvaluationFixture.append(d,hash,first.observation().cell(),"4",first.score_id(),"10","10");
        assertEquals(ErrorCode.REVISION_CONFLICT,assertThrows(TenantAccessException.class,()->engine.requireAppend(d,hash,history,stale)).code());
        history.add(EvaluationFixture.score(3001,d,hash,stale));assertThrows(TenantAccessException.class,()->engine.compute(d,hash,history));
    }
    @Test void foreignCellInputOutputRuleAndBrokenAncestryAreRejected()throws Exception{
        var d=definition(List.of("EVAL-02","EVAL-06"),1);String hash=schema.hash(d);var history=scores(d,"10","10");var first=history.get(0);
        var foreign=new EvaluationProtocol.Cell(EvaluationFixture.id(9999),first.observation().cell().agent_version_id(),"EVAL-02",0);
        var bad=new EvaluationProtocol.Append("1.0.0","5",hash,foreign,first.observation().input_ref(),EvaluationFixture.ref(50),List.of(EvaluationFixture.ref(51)),"1","1","0","0",null,null,"SCORED",null,"Bad sample");
        assertThrows(TenantAccessException.class,()->engine.requireAppend(d,hash,history,bad));
        var r=EvaluationFixture.append(d,hash,first.observation().cell(),"5",first.score_id(),"1","1");
        var swapped=new EvaluationProtocol.Append(r.schema_version(),r.expected_revision(),r.definition_sha256(),r.cell(),r.input_ref(),EvaluationFixture.ref(99),r.evidence_refs(),r.numerator(),r.denominator(),r.unknown_count(),r.violation_count(),r.control_numerator(),r.control_denominator(),r.status(),r.supersedes(),r.reason());
        assertThrows(TenantAccessException.class,()->engine.requireAppend(d,hash,history,swapped));
        var broken=new ArrayList<>(history);broken.add(EvaluationFixture.score(3000,d,hash,EvaluationFixture.append(d,hash,first.observation().cell(),"5",EvaluationFixture.id(9998),"1","1")));
        assertThrows(TenantAccessException.class,()->engine.compute(d,hash,broken));
        var duplicate=new ArrayList<>(history);duplicate.add(EvaluationFixture.score(3000,d,hash,EvaluationFixture.append(d,hash,first.observation().cell(),"5",null,"1","1")));
        assertThrows(TenantAccessException.class,()->engine.compute(d,hash,duplicate));
    }
    @Test void permutationDoesNotChangeSelectedScoreSetOrResultHash()throws Exception{
        var d=definition(List.of("EVAL-02","EVAL-06"),2);var history=new ArrayList<>(scores(d,"10","10"));var original=engine.compute(d,schema.hash(d),history);
        Collections.reverse(history);assertEquals(original,engine.compute(d,schema.hash(d),history));assertEquals(schema.hash(original),schema.hash(engine.compute(d,schema.hash(d),history)));
    }
    @Test void legalControlIsRequiredForIsolationMetric()throws Exception{
        var d=definition(List.of("EVAL-11"),1);var history=new ArrayList<>(scores(d,"10","10"));var s=history.get(0);var r=s.observation();
        var missing=new EvaluationProtocol.Append(r.schema_version(),r.expected_revision(),r.definition_sha256(),r.cell(),r.input_ref(),r.output_ref(),r.evidence_refs(),r.numerator(),r.denominator(),r.unknown_count(),r.violation_count(),null,null,r.status(),r.supersedes(),r.reason());
        history.set(0,EvaluationFixture.score(2001,d,schema.hash(d),missing));assertEquals("INSUFFICIENT_EVIDENCE",engine.compute(d,schema.hash(d),history).verdict());
    }
}
