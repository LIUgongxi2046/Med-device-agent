package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.ruoyi.medical.persistence.EvaluationAcceptanceStatisticsProtocol.*;

/**
 * Predeclared familywise inference over the FULL matrix. Families receive equal weight, after all
 * sample/repeat ratios inside each family receive equal weight. Bonferroni covers every candidate ×
 * statistical dimension, including groups that fail or have missing data. No best-repeat selection.
 *
 * Independence/random sampling of source families is an assumption requiring actual approved design
 * evidence; merely supplying a Basis cannot establish it. The caller verifies that policy approval,
 * all input/output/run/expert facts and current authorizations are authentic. No model or I/O occurs.
 */
public final class EvaluationAcceptanceStatisticsEngine {
    public static final String DETERMINISTIC="DETERMINISTIC_ALL_REQUIRED_RUNS",STATISTICAL="SOURCE_FAMILY_HOEFFDING_V1";
    private static final Set<String> INVARIANTS=Set.of("EVAL-02","EVAL-06","EVAL-07","EVAL-11","EVAL-12");
    private final PermissionSchema json;private final EvaluationSchema schema;private final EvaluationAcceptanceEvidenceSchema local;
    public EvaluationAcceptanceStatisticsEngine(PermissionSchema json,EvaluationSchema schema,EvaluationAcceptanceEvidenceSchema local) {
        this.json=Objects.requireNonNull(json);this.schema=Objects.requireNonNull(schema);this.local=Objects.requireNonNull(local);
    }
    public Result compute(EvaluationProtocol.Definition definition,EvaluationProtocol.Result original,
            List<EvaluationProtocol.Score> history,Policy policy)throws IOException {
        if(definition==null||original==null||history==null||policy==null)mismatch();
        if(history.size()>50000)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        policy=local.policy(json.canonicalObject(policy).getBytes(StandardCharsets.UTF_8));
        definition=schema.definition(json.canonicalObject(definition));schema.validate("Result",json.tree(original));
        String hash=schema.hash(definition);var engine=new EvaluationVerdict(schema);
        for(var score:history)schema.score(json.canonicalObject(score));
        var recomputed=engine.compute(definition,hash,history);
        if(!json.canonicalObject(recomputed).equals(json.canonicalObject(original)))mismatch();
        requirePolicy(definition,policy);
        var effective=engine.effective(definition,hash,history);var outcomes=new ArrayList<Outcome>();
        int comparisons=policy.candidate_refs().size()*(int)policy.dimensions().stream().filter(r->STATISTICAL.equals(r.method())).count();
        // Round alpha down so no comparison spends more than its preapproved familywise error budget.
        BigDecimal alpha=comparisons==0?null:BigDecimal.ONE.subtract(new BigDecimal(policy.familywise_confidence()))
            .divide(BigDecimal.valueOf(comparisons),40,RoundingMode.FLOOR);
        if(alpha!=null&&alpha.signum()<=0)invalid();String alphaText=alpha==null?null:alpha.stripTrailingZeros().toPlainString();
        var allMissing=new TreeSet<String>();
        for(var candidate:policy.candidate_refs().stream().sorted(Comparator.comparing(PermissionProtocol.Basis::version_id)).toList())
            for(var rule:policy.dimensions().stream().sorted(Comparator.comparing(Rule::dimension)).toList()) {
                var outcome=group(definition,candidate.version_id(),rule,effective,alpha,alphaText);
                outcomes.add(outcome);if(outcome.verdict().equals("INSUFFICIENT_EVIDENCE"))allMissing.addAll(outcome.reasons());
            }
        String verdict=!allMissing.isEmpty()?"INSUFFICIENT_EVIDENCE":outcomes.stream().anyMatch(o->o.verdict().equals("FAIL"))?"FAIL":"PASS";
        var result=new Result("1.0.0","PREAPPROVED_FAMILYWISE_EVALUATION","1.0.0",schema.hash(policy),hash,schema.hash(original),
            original.score_set_sha256(),comparisons,policy.familywise_confidence(),alphaText,outcomes,verdict,List.copyOf(allMissing));
        local.validate("StatisticsResult",json.tree(result));return result;
    }
    private void requirePolicy(EvaluationProtocol.Definition d,Policy p) {
        var request=d.request();
        if(!d.tenant_id().equals(p.tenant_id())||!Objects.equals(request.project_id(),p.project_id())
            ||!request.rule_revision().equals(p.rule_revision())||!request.dataset().equals(p.dataset())
            ||!request.sample_manifest_artifact().equals(p.sample_manifest_artifact())||!request.candidates().equals(p.candidate_refs()))mismatch();
        var created=Instant.parse(p.created_at());var approved=Instant.parse(p.approved_at());var evaluation=Instant.parse(d.created_at());
        if(approved.isBefore(created)||!created.isBefore(evaluation)||!approved.isBefore(evaluation))mismatch();
        var confidence=new BigDecimal(p.familywise_confidence());
        if(confidence.signum()<=0||confidence.compareTo(BigDecimal.ONE)>=0||confidence.scale()>18)invalid();
        var dims=new HashSet<String>();
        for(var rule:p.dimensions()) {
            if(!dims.add(rule.dimension())||rule.repeat_count()!=request.variance_policy().repeat_count())mismatch();
            var threshold=new BigDecimal(rule.threshold());
            if(threshold.signum()<0||threshold.compareTo(BigDecimal.ONE)>0||threshold.scale()>18)invalid();
            if(INVARIANTS.contains(rule.dimension())) {
                // Documented zero-violation invariants cannot be weakened to a statistical average or MAX metric.
                if(!DETERMINISTIC.equals(rule.method())||!"MIN".equals(rule.direction())||threshold.compareTo(BigDecimal.ONE)!=0)invalid();
            } else if(!STATISTICAL.equals(rule.method())||rule.minimum_families()<2)invalid();
        }
        if(!dims.equals(new HashSet<>(request.required_dimensions())))mismatch();
    }
    private Outcome group(EvaluationProtocol.Definition d,String candidate,Rule rule,
            Map<EvaluationProtocol.Cell,EvaluationProtocol.Score> effective,BigDecimal alpha,String alphaText) {
        var missing=new TreeSet<String>();var families=new TreeMap<String,List<CellValue>>();int observed=0;boolean failed=false;
        if(!d.request().environment().equals("PRODUCTION")||d.request().samples().stream().anyMatch(s->!s.evidence_class().equals("REAL")))missing.add("REAL_PRODUCTION_EVIDENCE_REQUIRED");
        if(d.request().samples().isEmpty())missing.add("NO_SAMPLES");
        for(var sample:d.request().samples()) {
            if(!sample.split().equals("ACCEPTANCE"))missing.add("ACCEPTANCE_SPLIT_REQUIRED");
            if(!d.approved_expectation_samples().contains(sample.sample_id()))missing.add("UNAPPROVED_EXPECTED_ITEM_SET");
            if(sample.expectations().stream().noneMatch(e->e.dimension().equals(rule.dimension())))missing.add("MISSING_EXPECTED_ITEM_SET");
            for(int i=0;i<rule.repeat_count();i++) {
                var cell=new EvaluationProtocol.Cell(sample.sample_id(),candidate,rule.dimension(),i);var score=effective.get(cell);
                if(score==null){missing.add("MISSING_SCORE");continue;}observed++;
                var o=score.observation();
                if(!o.status().equals("SCORED")){missing.add("NON_SCORED_OBSERVATION");continue;}
                if(o.output_ref()==null||o.evidence_refs().isEmpty()){missing.add("MISSING_ACTUAL_OUTPUT_OR_EVIDENCE");continue;}
                if(o.numerator()==null||o.denominator()==null||new BigInteger(o.denominator()).signum()==0){missing.add("ZERO_OR_MISSING_DENOMINATOR");continue;}
                if(o.unknown_count()==null||o.violation_count()==null){missing.add("MISSING_INVARIANT_COUNTS");continue;}
                if(!o.unknown_count().equals("0"))missing.add("UNKNOWN_OBSERVATIONS");
                if(!o.violation_count().equals("0"))failed=true;
                double[] ratio=ratio(o.numerator(),o.denominator());
                families.computeIfAbsent(sample.source_family(),x->new ArrayList<>()).add(new CellValue(cell,score.score_id(),o.numerator(),o.denominator(),decimal(ratio[0]),decimal(ratio[1])));
                if(DETERMINISTIC.equals(rule.method())) {
                    if(!o.numerator().equals(o.denominator()))failed=true;
                    if(rule.dimension().equals("EVAL-11")) {
                        if(o.control_denominator()==null||new BigInteger(o.control_denominator()).signum()==0)missing.add("MISSING_LEGAL_CONTROL_DENOMINATOR");
                        else if(!o.control_denominator().equals(o.control_numerator()))failed=true;
                    }
                }
            }
        }
        var summaries=new ArrayList<Family>();
        for(var entry:families.entrySet()) {
            var observations=entry.getValue().stream().sorted(Comparator.comparing((CellValue c)->c.cell().sample_id()).thenComparingInt(c->c.cell().repeat_index())).toList();
            double lower=mean(observations.stream().map(CellValue::ratio_lower).toList(),false);
            double upper=mean(observations.stream().map(CellValue::ratio_upper).toList(),true);
            summaries.add(new Family(entry.getKey(),observations,decimal(lower),decimal(upper)));
        }
        if(summaries.size()<rule.minimum_families())missing.add("INSUFFICIENT_SOURCE_FAMILIES");
        String lo=null,hi=null,radius=null,meanLo=null,meanHi=null;
        if(!summaries.isEmpty()) {
            double ml=mean(summaries.stream().map(Family::mean_lower).toList(),false),mu=mean(summaries.stream().map(Family::mean_upper).toList(),true);
            meanLo=decimal(ml);meanHi=decimal(mu);
            if(STATISTICAL.equals(rule.method())) {
                double r=radiusUpper(alpha,summaries.size());radius=decimal(r);
                lo=decimal(Math.max(0,down(ml-r)));hi=decimal(Math.min(1,up(mu+r)));
            } else {lo=meanLo;hi=meanHi;}
        }
        String verdict;
        if(!missing.isEmpty())verdict="INSUFFICIENT_EVIDENCE";
        else if(failed)verdict="FAIL";
        else if(DETERMINISTIC.equals(rule.method()))verdict="PASS";
        else {
            int lower=new BigDecimal(lo).compareTo(new BigDecimal(rule.threshold()));
            int upper=new BigDecimal(hi).compareTo(new BigDecimal(rule.threshold()));
            // Equality is deliberately inconclusive; no floating-point tie may manufacture a PASS.
            boolean pass=rule.direction().equals("MIN")?lower>0:upper<0;
            boolean fail=rule.direction().equals("MIN")?upper<0:lower>0;
            verdict=pass?"PASS":fail?"FAIL":"INSUFFICIENT_EVIDENCE";
            if(!pass&&!fail)missing.add("CONFIDENCE_BOUND_INCONCLUSIVE");
        }
        return new Outcome(candidate,rule.dimension(),rule.method(),rule.direction(),rule.threshold(),rule.repeat_count(),observed,
            d.request().samples().size()*rule.repeat_count(),rule.minimum_families(),summaries,meanLo,meanHi,radius,lo,hi,
            STATISTICAL.equals(rule.method())?alphaText:null,verdict,List.copyOf(missing));
    }
    private static double[] ratio(String numerator,String denominator) {
        var n=new BigDecimal(numerator);var d=new BigDecimal(denominator);
        if(n.signum()==0)return new double[]{0,0};if(n.compareTo(d)==0)return new double[]{1,1};
        double lower=n.divide(d,48,RoundingMode.FLOOR).doubleValue(),upper=n.divide(d,48,RoundingMode.CEILING).doubleValue();
        return new double[]{Math.max(0,down(lower)),Math.min(1,up(upper))};
    }
    private static double mean(List<String> values,boolean upper) {
        if(values.stream().allMatch(v->new BigDecimal(v).signum()==0))return 0;
        if(values.stream().allMatch(v->new BigDecimal(v).compareTo(BigDecimal.ONE)==0))return 1;
        double sum=0;for(var value:values)sum=upper?up(sum+Double.parseDouble(value)):down(sum+Double.parseDouble(value));
        return Math.max(0,Math.min(1,upper?up(sum/values.size()):down(sum/values.size())));
    }
    /** StrictMath is deterministic; widen at every arithmetic/transcendental boundary, never shrink the CI. */
    static double radiusUpper(BigDecimal alpha,int families) {
        if(alpha==null||alpha.signum()<=0||alpha.compareTo(BigDecimal.ONE)>=0||families<1)invalid();
        double a=alpha.doubleValue();while(new BigDecimal(a).compareTo(alpha)>0)a=Math.nextDown(a);
        a=Math.nextDown(a);if(!Double.isFinite(a)||a<=0)invalid();
        // Two-sided interval: P(|mean-E mean| >= r) <= 2 exp(-2 N r^2).
        double logarithm=up(up(StrictMath.log(2.0))+up(-StrictMath.log(a)));double radius=up(StrictMath.sqrt(up(logarithm/(2.0*families))));
        if(!Double.isFinite(radius))invalid();return radius;
    }
    private static double up(double value){return Math.nextUp(Math.nextUp(value));}
    private static double down(double value){return Math.nextDown(Math.nextDown(value));}
    private static String decimal(double value){return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
