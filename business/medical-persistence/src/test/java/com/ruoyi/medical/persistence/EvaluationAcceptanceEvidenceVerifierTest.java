package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.EvaluationAcceptanceEvidenceVerifier.*;
import static com.ruoyi.medical.persistence.EvaluationAcceptanceStatisticsProtocol.*;

/** Synthetic arithmetic/shape fixtures only. These tests do not create or mock production permissions. */
final class EvaluationAcceptanceEvidenceVerifierTest {
    private final PermissionSchema json=new PermissionSchema();
    private final EvaluationSchema evaluations=new EvaluationSchema(json);
    private final EvaluationAcceptanceEvidenceSchema local=new EvaluationAcceptanceEvidenceSchema(json);
    private final EvaluationVerdict verdicts=new EvaluationVerdict(evaluations);
    private final EvaluationAcceptanceEvidenceVerifier verifier=new EvaluationAcceptanceEvidenceVerifier(json,evaluations,local);
    private final EvaluationAcceptanceStatisticsEngine statistics=new EvaluationAcceptanceStatisticsEngine(json,evaluations,local);
    private final JsonMapper mapper=JsonMapper.builder().build();
    EvaluationAcceptanceEvidenceVerifierTest()throws Exception { }
    record Fixture(EvaluationProtocol.Definition definition,List<EvaluationProtocol.Score> history,EvaluationProtocol.Result result,Policy policy) { }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    static PermissionProtocol.Basis basis(int n){return new PermissionProtocol.Basis(id(n),"a".repeat(64));}
    Fixture fixture()throws Exception{return fixture(java.util.stream.IntStream.range(0,20).toArray(),2,List.of("EVAL-01","EVAL-02"),2,"ACCEPTANCE",false,(s,d)->10);}
    Fixture fixture(int[] groups,int repeats,List<String> dims,int candidateCount,String split,boolean simulation,BiFunction<Integer,String,Integer> numerator)throws Exception {
        var samples=new ArrayList<EvaluationProtocol.Sample>();
        for(int i=0;i<groups.length;i++) {
            var expectations=dims.stream().map(d->new EvaluationProtocol.Expectation(d,
                java.util.stream.IntStream.range(0,10).mapToObj(n->"item-"+n).toList(),d.equals("EVAL-11")?List.of("control-0","control-1"):List.of())).toList();
            var provisional=new EvaluationProtocol.Sample(id(100+i),id(300+groups[i]),split,"PTR",simulation?"SIMULATION":"REAL",basis(500+i),basis(700+i),expectations);
            samples.add(new EvaluationProtocol.Sample(provisional.sample_id(),provisional.source_family(),split,"PTR",provisional.evidence_class(),
                provisional.input_ref(),new PermissionProtocol.Basis(provisional.expected_ref().version_id(),evaluations.hash(evaluations.expectedItems(provisional))),expectations));
        }
        var candidates=java.util.stream.IntStream.range(0,candidateCount).mapToObj(n->basis(20+n)).toList();
        var request=new EvaluationProtocol.Create("1.0.0",id(2),basis(10),new PermissionProtocol.Basis(id(11),evaluations.hash(samples)),samples,candidates,
            basis(12),dims,new EvaluationProtocol.Variance(repeats,"ALL_REQUIRED_RUNS",null),basis(13),simulation?"SIMULATION":"PRODUCTION","Synthetic pure-verifier fixture");
        var snapshots=candidates.stream().map(c->new EvaluationProtocol.Candidate(c.version_id(),c.content_sha256(),id(30),id(31),
            json.object().put("fixed_dependency","synthetic-test"),json.object().put("maximum_calls","10"),"1.0.0","b".repeat(64))).toList();
        var definition=new EvaluationProtocol.Definition("1.0.0",id(1),id(3),request,snapshots,
            samples.stream().map(EvaluationProtocol.Sample::sample_id).sorted().toList(),id(4),"1",id(5),"2026-10-17T00:00:00Z");
        String hash=evaluations.hash(definition);var history=new ArrayList<EvaluationProtocol.Score>();
        for(var cell:verdicts.matrix(request)) {
            int sampleIndex=Integer.parseInt(cell.sample_id().substring(24))-100;
            int outputId=2000+sampleIndex*100+Integer.parseInt(cell.agent_version_id().substring(24))*10+cell.repeat_index();
            var observation=new EvaluationProtocol.Append("1.0.0",Integer.toString(history.size()+1),hash,cell,samples.get(sampleIndex).input_ref(),basis(outputId),List.of(basis(900)),
                numerator.apply(sampleIndex,cell.dimension()).toString(),"10","0","0",cell.dimension().equals("EVAL-11")?"2":null,cell.dimension().equals("EVAL-11")?"2":null,"SCORED",null,"Synthetic arithmetic observation");
            history.add(new EvaluationProtocol.Score("1.0.0",id(10000+history.size()),id(3),hash,basis(12),observation,id(40),id(41),"1","2026-10-17T00:01:00Z"));
        }
        var rules=dims.stream().map(d->new Rule(d,Set.of("EVAL-02","EVAL-06","EVAL-07","EVAL-11","EVAL-12").contains(d)
            ?EvaluationAcceptanceStatisticsEngine.DETERMINISTIC:EvaluationAcceptanceStatisticsEngine.STATISTICAL,"MIN",d.equals("EVAL-01")||d.equals("EVAL-03")?"0.5":"1",repeats,1+(!Set.of("EVAL-02","EVAL-06","EVAL-07","EVAL-11","EVAL-12").contains(d)?1:0))).toList();
        var policy=new Policy("1.0.0",id(50),id(1),id(2),basis(12),basis(10),request.sample_manifest_artifact(),candidates,"c".repeat(64),"d".repeat(64),
            "0.95",basis(51),"2026-10-16T00:00:00Z","2026-10-16T01:00:00Z",rules);
        return new Fixture(definition,List.copyOf(history),verdicts.compute(definition,hash,history),policy);
    }
    Target target(Fixture f,Policy policy)throws Exception {
        var c=f.definition().candidate_snapshots().get(0);
        return new Target(id(1),id(2),id(3),evaluations.hash(f.definition()),evaluations.hash(f.result()),evaluations.hash(f.history()),basis(20),
            evaluations.hash(c.dependency_manifest()),evaluations.hash(c.limits()),c.schema_manifest_sha256(),"c".repeat(64),"e".repeat(64),"d".repeat(64),policy==null?null:evaluations.hash(policy));
    }
    DecisionIdentity human(){return new DecisionIdentity(id(60),id(61),"1",id(62),"2026-10-17T00:02:00Z");}
    ExpectedBinding expected(Fixture f,Policy p)throws Exception {
        String status=switch(f.result().verdict()){case "PASS"->"COMPLETED";case "FAIL"->"FAILED";default->"INSUFFICIENT_EVIDENCE";};
        return new ExpectedBinding(target(f,p),status,Integer.toString(f.history().size()+2),human());
    }
    AcceptanceDecision decision(Fixture f,Policy p,String kind)throws Exception {
        return new AcceptanceDecision("1.0.0",id(63),ACCEPTANCE_SCOPE,kind,target(f,p),human(),p==null?MISSING_PRODUCTION_EVIDENCE:MISSING_PRODUCTION_EVIDENCE_WITH_POLICY,"Recorded fixture decision, not an actual permission");
    }
    VerifiedEvidence verify(Fixture f,Policy p,String kind)throws Exception {return verifier.verify(f.definition(),f.result(),f.history(),decision(f,p,kind),expected(f,p),p);}
    <T> T changed(Object source,String field,Object value,Class<T> type)throws Exception {
        ObjectNode node=json.tree(source).deepCopy();node.set(field,json.tree(value));return mapper.treeToValue(node,type);
    }
    Fixture history(Fixture f,List<EvaluationProtocol.Score> h)throws Exception {
        return new Fixture(f.definition(),List.copyOf(h),verdicts.compute(f.definition(),evaluations.hash(f.definition()),h),f.policy());
    }
    Fixture scoreChange(Fixture f,int index,String field,Object value)throws Exception {
        var h=new ArrayList<>(f.history());var old=h.get(index);var observation=changed(old.observation(),field,value,EvaluationProtocol.Append.class);
        h.set(index,new EvaluationProtocol.Score(old.schema_version(),old.score_id(),old.evaluation_id(),old.definition_sha256(),old.rule_revision(),observation,old.scorer_id(),old.authority_grant_id(),old.authority_revision(),old.created_at()));return history(f,h);
    }
    void mismatch(org.junit.jupiter.api.function.Executable action) {
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,action).code());
    }
    @Test void completeDeterministicEvidenceNeverBecomesPublicationPermission()throws Exception {
        var f=fixture(new int[]{0,1},2,List.of("EVAL-02","EVAL-11"),1,"ACCEPTANCE",false,(s,d)->10);
        var proof=verify(f,null,"ACCEPT");assertEquals("PASS",f.result().verdict());assertFalse(proof.productionEligible());assertFalse(proof.report().production_eligible());
        assertEquals(MISSING_PRODUCTION_EVIDENCE,proof.stillMissingProductionEvidence());assertNull(proof.statistics());
        assertTrue(Arrays.stream(VerifiedEvidence.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        assertThrows(UnsupportedOperationException.class,()->proof.stillMissingProductionEvidence().clear());
    }
    @Test void realPolicySupplementDoesNotRewriteOldInsufficientResult()throws Exception {
        var f=fixture();String before=json.canonicalObject(f.result());var proof=verify(f,f.policy(),"ACCEPT");
        assertEquals("INSUFFICIENT_EVIDENCE",f.result().verdict());assertEquals(before,json.canonicalObject(f.result()));assertFalse(f.result().production_eligible());
        assertEquals("PASS",proof.statistics().verdict());assertFalse(proof.productionEligible());assertEquals(MISSING_PRODUCTION_EVIDENCE_WITH_POLICY,proof.stillMissingProductionEvidence());
        assertEquals(evaluations.hash(proof.statistics()),proof.report().statistics_sha256());
    }
    @Test void statisticalResultCannotBeAcceptedWithoutPolicy()throws Exception {
        var f=fixture();assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->verify(f,null,"ACCEPT")).code());
    }
    @Test void genuineRejectionPreservesInsufficientLedger()throws Exception {var f=fixture();assertEquals("REJECT",verify(f,null,"REJECT").report().decision());}
    @Test void modifiedProductionFlagRejectedByFrozenSchema()throws Exception {
        var f=fixture();var result=changed(f.result(),"production_eligible",true,EvaluationProtocol.Result.class);
        assertThrows(SchemaRejection.class,()->verifier.verify(f.definition(),result,f.history(),decision(f,null,"REJECT"),expected(f,null)));
    }
    @Test void removedScoreAndRehashedTargetCannotFakeUnchangedResult()throws Exception {
        var f=fixture();var h=new ArrayList<>(f.history());h.remove(h.size()-1);var changed=new Fixture(f.definition(),h,f.result(),f.policy());
        mismatch(()->verify(changed,f.policy(),"ACCEPT"));
    }
    @Test void truncatedResultAndRehashedTargetFailsFullRecomputation()throws Exception {
        var f=fixture();var result=changed(f.result(),"cells",f.result().cells().subList(0,f.result().cells().size()-1),EvaluationProtocol.Result.class);
        mismatch(()->verify(new Fixture(f.definition(),f.history(),result,f.policy()),f.policy(),"ACCEPT"));
    }
    @Test void forgedSummaryAndRehashedTargetRejected()throws Exception {
        var f=fixture();var dimensions=new ArrayList<>(f.result().dimensions());var first=dimensions.get(0);
        dimensions.set(0,new EvaluationProtocol.DimensionSummary(first.dimension(),first.expected_cells(),first.observed_cells(),1,0,0));
        var result=changed(f.result(),"dimensions",dimensions,EvaluationProtocol.Result.class);mismatch(()->verify(new Fixture(f.definition(),f.history(),result,f.policy()),f.policy(),"ACCEPT"));
    }
    @Test void scoreLedgerOrderIsNotEditable()throws Exception {
        var f=fixture();var h=new ArrayList<>(f.history());Collections.swap(h,0,1);mismatch(()->verify(new Fixture(f.definition(),h,f.result(),f.policy()),f.policy(),"ACCEPT"));
    }
    @Test void fullSupersessionHistoryRequiredEvenWhenEffectiveScoresSame()throws Exception {
        var f=fixture();var h=new ArrayList<>(f.history());var old=h.get(0);
        var o=changed(old.observation(),"expected_revision",Integer.toString(h.size()+1),EvaluationProtocol.Append.class);
        o=changed(o,"supersedes",old.score_id(),EvaluationProtocol.Append.class);
        h.add(new EvaluationProtocol.Score("1.0.0",id(20000),id(3),old.definition_sha256(),old.rule_revision(),o,id(40),id(41),"1","2026-10-17T00:01:30Z"));
        var full=history(f,h);assertEquals(h.size(),verify(full,full.policy(),"ACCEPT").report().score_history_size());
        var cut=new ArrayList<>(h);cut.remove(0);mismatch(()->verifier.verify(full.definition(),full.result(),cut,decision(full,full.policy(),"ACCEPT"),expected(full,full.policy()),full.policy()));
    }
    @Test void targetTenantProjectCandidateAndEveryDigestAreBound()throws Exception {
        var f=fixture();var d=decision(f,f.policy(),"ACCEPT");
        for(String field:List.of("tenant_id","project_id","evaluation_id","definition_sha256","result_sha256","score_history_sha256",
            "candidate_dependency_manifest_sha256","candidate_limits_sha256","candidate_schema_manifest_sha256","proposed_runtime_manifest_sha256",
            "proposed_release_manifest_sha256","proposed_release_scope_sha256","acceptance_policy_sha256")) {
            Object value=field.endsWith("_id")?id(999):"f".repeat(64);var t=changed(d.target(),field,value,Target.class);
            var bad=changed(d,"target",t,AcceptanceDecision.class);mismatch(()->verifier.verify(f.definition(),f.result(),f.history(),bad,expected(f,f.policy()),f.policy()));
        }
        var target=changed(d.target(),"candidate",basis(999),Target.class);var bad=changed(d,"target",target,AcceptanceDecision.class);
        mismatch(()->verifier.verify(f.definition(),f.result(),f.history(),bad,expected(f,f.policy()),f.policy()));
    }
    @Test void actualCallerIdentityCannotBeReplacedByDecisionClaim()throws Exception {
        var f=fixture();var d=decision(f,null,"REJECT");
        for(String field:List.of("actor_id","authority_grant_id","authority_revision","decision_operation_id","decided_at")) {
            Object value=field.equals("authority_revision")?"2":field.equals("decided_at")?"2026-10-17T00:03:00Z":id(999);
            var human=changed(d.human_decision(),field,value,DecisionIdentity.class);var bad=changed(d,"human_decision",human,AcceptanceDecision.class);
            mismatch(()->verifier.verify(f.definition(),f.result(),f.history(),bad,expected(f,null)));
        }
    }
    @Test void terminalStateAndRevisionMustMatchCompleteLedger()throws Exception {
        var f=fixture();var e=expected(f,null);
        mismatch(()->verifier.verify(f.definition(),f.result(),f.history(),decision(f,null,"REJECT"),changed(e,"evaluation_revision","1",ExpectedBinding.class)));
        mismatch(()->verifier.verify(f.definition(),f.result(),f.history(),decision(f,null,"REJECT"),changed(e,"evaluation_status","COMPLETED",ExpectedBinding.class)));
    }
    @Test void malformedUnknownDuplicateAndTrailingJsonRejected()throws Exception {
        var f=fixture();String valid=json.canonicalObject(decision(f,null,"REJECT"));
        assertNotNull(local.decision(valid.getBytes(StandardCharsets.UTF_8)));
        for(String bad:List.of("{}","{",valid+" {}",valid.replaceFirst("\\{","{\"extra\":true,"),valid.replaceFirst("\\{","{\"schema_version\":\"1.0.0\",")))
            assertThrows(SchemaRejection.class,()->local.decision(bad.getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class,()->local.decision(new byte[]{'{','"','x','"',':','"',(byte)0xc3,'"','}'}));
    }
    @Test void nestedIdentityAndScopeFieldsAreClosed()throws Exception {
        var f=fixture();ObjectNode node=(ObjectNode)json.tree(decision(f,null,"REJECT"));((ObjectNode)node.get("human_decision")).put("permission_granted",true);
        assertThrows(SchemaRejection.class,()->local.decision(node.toString().getBytes(StandardCharsets.UTF_8)));
        var d=changed(decision(f,null,"REJECT"),"acceptance_scope","PRODUCTION_PUBLICATION",AcceptanceDecision.class);
        assertThrows(SchemaRejection.class,()->verifier.verify(f.definition(),f.result(),f.history(),d,expected(f,null)));
    }
    @Test void omittedAcknowledgedLimitationsCannotDisappear()throws Exception {
        var f=fixture();var d=changed(decision(f,null,"REJECT"),"acknowledged_limitations",List.of(MISSING_PRODUCTION_EVIDENCE.get(0)),AcceptanceDecision.class);
        mismatch(()->verifier.verify(f.definition(),f.result(),f.history(),d,expected(f,null)));
    }
    @Test void revisionOverflowAndBlankReasonRejected()throws Exception {
        var f=fixture();var d=decision(f,null,"REJECT");
        var human=changed(d.human_decision(),"authority_revision","18446744073709551616",DecisionIdentity.class);
        assertThrows(SchemaRejection.class,()->local.decision(json.canonicalObject(changed(d,"human_decision",human,AcceptanceDecision.class)).getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class,()->local.decision(json.canonicalObject(changed(d,"reason"," \t ",AcceptanceDecision.class)).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void countsAndByteLimitsFailClosed()throws Exception {
        var f=fixture();for(var limits:List.of(new Limits(1,10000,33554432),new Limits(50000,1,33554432),new Limits(50000,10000,1))) {
            var bounded=new EvaluationAcceptanceEvidenceVerifier(json,evaluations,local,limits);
            assertEquals(ErrorCode.CONTEXT_LIMIT,assertThrows(TenantAccessException.class,()->bounded.verify(f.definition(),f.result(),f.history(),decision(f,null,"REJECT"),expected(f,null))).code());
        }
    }
    @Test void bonferroniCountsAllCandidatesAndStatisticalDimensions()throws Exception {
        var f=fixture(java.util.stream.IntStream.range(0,50).toArray(),2,List.of("EVAL-01","EVAL-03","EVAL-02"),2,"ACCEPTANCE",false,(s,d)->10);
        var result=statistics.compute(f.definition(),f.result(),f.history(),f.policy());assertEquals(4,result.statistical_comparisons());assertEquals("0.0125",result.adjusted_alpha());
        assertEquals(6,result.outcomes().size());assertEquals("PASS",result.verdict());
    }
    @Test void knownFiftyFamilyHoeffdingReferenceIsConservativelyBounded()throws Exception {
        // Independent reference: sqrt(ln(80)/(2*50)) = 0.2093329079402921... .
        double radius=EvaluationAcceptanceStatisticsEngine.radiusUpper(new BigDecimal("0.025"),50);
        assertTrue(radius>=0.2093329079402921);assertTrue(radius<0.20933290794030);
        var f=fixture(java.util.stream.IntStream.range(0,50).toArray(),2,List.of("EVAL-01"),2,"ACCEPTANCE",false,(s,d)->10);
        var result=statistics.compute(f.definition(),f.result(),f.history(),f.policy());var o=result.outcomes().get(0);
        assertTrue(new BigDecimal(o.lower_bound()).compareTo(new BigDecimal("0.7906670920597079"))<=0);
        assertEquals(50,o.families().size());assertEquals(100,o.expected_cells());assertEquals(100,o.observed_cells());assertEquals("PASS",o.verdict());
    }
    @Test void twoSidedRadiusCannotSpendTheOtherTailTwice()throws Exception {
        double oneSided=StrictMath.sqrt(StrictMath.log(40.0)/100.0);
        double actual=EvaluationAcceptanceStatisticsEngine.radiusUpper(new BigDecimal("0.025"),50);
        assertTrue(actual>oneSided);
        var f=fixture(java.util.stream.IntStream.range(0,50).toArray(),2,List.of("EVAL-01"),2,"ACCEPTANCE",false,(s,d)->10);
        var rules=f.policy().dimensions().stream().map(r->new Rule(r.dimension(),r.method(),r.direction(),"0.8",r.repeat_count(),r.minimum_families())).toList();
        var p=changed(f.policy(),"dimensions",rules,Policy.class);
        assertEquals("INSUFFICIENT_EVIDENCE",statistics.compute(f.definition(),f.result(),f.history(),p).verdict());
    }
    @Test void unequalFamilySizesUseEqualFamilyWeights()throws Exception {
        var f=fixture(new int[]{0,0,0,0,0,0,0,0,0,1},2,List.of("EVAL-01"),1,"ACCEPTANCE",false,(s,d)->s==9?0:10);
        var o=statistics.compute(f.definition(),f.result(),f.history(),f.policy()).outcomes().get(0);
        assertTrue(new BigDecimal(o.mean_lower()).compareTo(new BigDecimal("0.5"))<=0);assertTrue(new BigDecimal(o.mean_upper()).compareTo(new BigDecimal("0.5"))>=0);
        assertTrue(new BigDecimal(o.mean_upper()).compareTo(new BigDecimal("0.50000000000001"))<0);assertEquals(18,o.families().get(0).observations().size());
    }
    @Test void allRepetitionsContributeRatherThanBestRepeat()throws Exception {
        var f=fixture();int index=0;while(!f.history().get(index).observation().cell().dimension().equals("EVAL-01"))index++;
        f=scoreChange(f,index,"numerator","0");var result=statistics.compute(f.definition(),f.result(),f.history(),f.policy());
        var o=result.outcomes().stream().filter(v->v.dimension().equals("EVAL-01")&&v.agent_version_id().equals(id(20))).findFirst().orElseThrow();
        var family=o.families().get(0);assertEquals(2,family.observations().size());assertTrue(new BigDecimal(family.mean_upper()).compareTo(new BigDecimal("0.50000000000001"))<0);
    }
    @Test void deterministicFailureInAnyRepeatBlocksAcceptance()throws Exception {
        var f=fixture();int index=0;while(!f.history().get(index).observation().cell().dimension().equals("EVAL-02"))index++;
        var failed=scoreChange(f,index,"numerator","9");assertEquals("FAIL",statistics.compute(failed.definition(),failed.result(),failed.history(),failed.policy()).verdict());
        assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->verify(failed,failed.policy(),"ACCEPT")).code());
    }
    @Test void deterministicDimensionsCannotBeWeakenedToStatisticalAverage()throws Exception {
        var f=fixture();var rules=new ArrayList<>(f.policy().dimensions());int i=rules.get(0).dimension().equals("EVAL-02")?0:1;
        rules.set(i,new Rule("EVAL-02",EvaluationAcceptanceStatisticsEngine.STATISTICAL,"MIN","0.1",2,2));var policy=changed(f.policy(),"dimensions",rules,Policy.class);
        assertThrows(SchemaRejection.class,()->statistics.compute(f.definition(),f.result(),f.history(),policy));
    }
    @Test void missingRepeatCannotShrinkBonferroniOrDenominator()throws Exception {
        var f=fixture();var h=new ArrayList<>(f.history());h.remove(h.size()-1);var reduced=history(f,h);
        var result=statistics.compute(reduced.definition(),reduced.result(),reduced.history(),reduced.policy());assertEquals(2,result.statistical_comparisons());assertEquals("INSUFFICIENT_EVIDENCE",result.verdict());
        assertTrue(result.missing_requirements().contains("MISSING_SCORE"));
    }
    @Test void noFamilyOrTooFewFamiliesIsInsufficient()throws Exception {
        for(int[] groups:List.of(new int[]{},new int[]{0,0})) {
            var f=fixture(groups,2,List.of("EVAL-01"),1,"ACCEPTANCE",false,(s,d)->10);
            assertEquals("INSUFFICIENT_EVIDENCE",statistics.compute(f.definition(),f.result(),f.history(),f.policy()).verdict());
        }
    }
    @Test void minimumFamiliesPolicyIsEnforced()throws Exception {
        var f=fixture();var rules=f.policy().dimensions().stream().map(r->new Rule(r.dimension(),r.method(),r.direction(),r.threshold(),r.repeat_count(),30)).toList();
        var p=changed(f.policy(),"dimensions",rules,Policy.class);var result=statistics.compute(f.definition(),f.result(),f.history(),p);
        assertEquals("INSUFFICIENT_EVIDENCE",result.verdict());assertTrue(result.missing_requirements().contains("INSUFFICIENT_SOURCE_FAMILIES"));
    }
    @Test void developmentAndSimulationNeverBecomeFormalEvidence()throws Exception {
        for(String split:List.of("DEVELOPMENT","TUNING","ACCEPTANCE")) {
            var f=fixture(new int[]{0,1},2,List.of("EVAL-02"),1,split,split.equals("ACCEPTANCE"),(s,d)->10);
            assertEquals("INSUFFICIENT_EVIDENCE",statistics.compute(f.definition(),f.result(),f.history(),f.policy()).verdict());
            assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->verify(f,f.policy(),"ACCEPT")).code());
        }
    }
    @Test void policyMustBeApprovedStrictlyBeforeEvaluationAndAfterItsCreation()throws Exception {
        var f=fixture();for(String time:List.of("2026-10-17T00:00:00Z","2026-10-18T00:00:00Z","2026-10-15T00:00:00Z")) {
            var policy=changed(f.policy(),"approved_at",time,Policy.class);mismatch(()->statistics.compute(f.definition(),f.result(),f.history(),policy));
        }
    }
    @Test void policyEveryInputIdentityAndDimensionIsBound()throws Exception {
        var f=fixture();for(String field:List.of("tenant_id","project_id","rule_revision","dataset","sample_manifest_artifact","candidate_refs")) {
            Object value=field.endsWith("_id")?id(999):field.equals("candidate_refs")?List.of(basis(999)):basis(999);
            var p=changed(f.policy(),field,value,Policy.class);mismatch(()->statistics.compute(f.definition(),f.result(),f.history(),p));
        }
        var p=changed(f.policy(),"dimensions",List.of(f.policy().dimensions().get(0)),Policy.class);mismatch(()->statistics.compute(f.definition(),f.result(),f.history(),p));
    }
    @Test void runtimeAndScopePolicyBindingsCannotBeAlteredWithRehashedPolicy()throws Exception {
        var f=fixture();for(String field:List.of("runtime_manifest_sha256","scope_sha256")) {
            var p=changed(f.policy(),field,"f".repeat(64),Policy.class);mismatch(()->verify(f,p,"ACCEPT"));
        }
    }
    @Test void confidenceThresholdAndRepeatBoundsRejected()throws Exception {
        var f=fixture();for(String confidence:List.of("0","1","1.1","-0.1","0.9999999999999999999")) {
            var p=changed(f.policy(),"familywise_confidence",confidence,Policy.class);assertThrows(SchemaRejection.class,()->statistics.compute(f.definition(),f.result(),f.history(),p));
        }
        var rules=f.policy().dimensions().stream().map(r->new Rule(r.dimension(),r.method(),r.direction(),r.threshold(),3,r.minimum_families())).toList();
        var p=changed(f.policy(),"dimensions",rules,Policy.class);mismatch(()->statistics.compute(f.definition(),f.result(),f.history(),p));
    }
    @Test void maxMetricLowValuesPassButHighValuesFail()throws Exception {
        for(int numerator:List.of(0,10)) {
            var f=fixture(java.util.stream.IntStream.range(0,50).toArray(),2,List.of("EVAL-01"),1,"ACCEPTANCE",false,(s,d)->numerator);
            var p=changed(f.policy(),"dimensions",List.of(new Rule("EVAL-01",EvaluationAcceptanceStatisticsEngine.STATISTICAL,"MAX","0.5",2,2)),Policy.class);
            assertEquals(numerator==0?"PASS":"FAIL",statistics.compute(f.definition(),f.result(),f.history(),p).verdict());
        }
    }
    @Test void exactThresholdBoundaryAndOverlappingIntervalStayInsufficient()throws Exception {
        var f=fixture(java.util.stream.IntStream.range(0,50).toArray(),2,List.of("EVAL-01"),1,"ACCEPTANCE",false,(s,d)->0);
        var p=changed(f.policy(),"dimensions",List.of(new Rule("EVAL-01",EvaluationAcceptanceStatisticsEngine.STATISTICAL,"MIN","0",2,2)),Policy.class);
        assertEquals("INSUFFICIENT_EVIDENCE",statistics.compute(f.definition(),f.result(),f.history(),p).verdict());
        var g=fixture(java.util.stream.IntStream.range(0,50).toArray(),2,List.of("EVAL-01"),1,"ACCEPTANCE",false,(s,d)->5);
        assertEquals("INSUFFICIENT_EVIDENCE",statistics.compute(g.definition(),g.result(),g.history(),g.policy()).verdict());
    }
    @Test void resultRecomputeIsDeterministicAndClosed()throws Exception {
        var f=fixture();var one=statistics.compute(f.definition(),f.result(),f.history(),f.policy());var two=statistics.compute(f.definition(),f.result(),f.history(),f.policy());
        assertEquals(json.canonicalObject(one),json.canonicalObject(two));ObjectNode output=(ObjectNode)json.tree(one);output.put("production_eligible",true);
        assertThrows(SchemaRejection.class,()->local.validate("StatisticsResult",output));
    }
    @Test void policyUnknownFieldsAndDuplicateDimensionsRejected()throws Exception {
        var f=fixture();ObjectNode p=(ObjectNode)json.tree(f.policy());p.put("independent_sampling_proven",true);
        assertThrows(SchemaRejection.class,()->local.policy(p.toString().getBytes(StandardCharsets.UTF_8)));
        var policy=changed(f.policy(),"dimensions",List.of(f.policy().dimensions().get(0),f.policy().dimensions().get(0)),Policy.class);
        assertThrows(SchemaRejection.class,()->statistics.compute(f.definition(),f.result(),f.history(),policy));
    }
    @Test void failedAndUnknownObservationsRemainBlocking()throws Exception {
        var f=fixture();var unknown=scoreChange(f,0,"unknown_count","1");assertEquals("INSUFFICIENT_EVIDENCE",statistics.compute(unknown.definition(),unknown.result(),unknown.history(),unknown.policy()).verdict());
        var violation=scoreChange(f,0,"violation_count","1");assertEquals("FAIL",statistics.compute(violation.definition(),violation.result(),violation.history(),violation.policy()).verdict());
    }
    @Test void selfScoringAndFutureScoreCannotBeAccepted()throws Exception {
        var f=fixture();for(String field:List.of("scorer_id","created_at")) {
            var h=new ArrayList<>(f.history());var score=changed(h.get(0),field,field.equals("scorer_id")?id(31):"2026-10-18T00:00:00Z",EvaluationProtocol.Score.class);h.set(0,score);
            var bad=history(f,h);mismatch(()->verify(bad,bad.policy(),"ACCEPT"));
        }
    }
}
