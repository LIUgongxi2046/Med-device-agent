package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;

/** Computes only documented assertion coverage. Statistical thresholds are never guessed. */
@Component
public final class EvaluationVerdict {
    private final EvaluationSchema schema;
    private static final Set<String> DETERMINISTIC=Set.of("EVAL-02","EVAL-06","EVAL-07","EVAL-11","EVAL-12");
    private static final Comparator<EvaluationProtocol.Cell> ORDER=Comparator.comparing(EvaluationProtocol.Cell::sample_id)
        .thenComparing(EvaluationProtocol.Cell::agent_version_id).thenComparing(EvaluationProtocol.Cell::dimension).thenComparingInt(EvaluationProtocol.Cell::repeat_index);
    public EvaluationVerdict(EvaluationSchema schema){this.schema=schema;}

    List<EvaluationProtocol.Cell> matrix(EvaluationProtocol.Create definition) {
        var result=new ArrayList<EvaluationProtocol.Cell>();
        for(var sample:definition.samples())for(var candidate:definition.candidates())for(var dimension:definition.required_dimensions())
            for(int i=0;i<definition.variance_policy().repeat_count();i++) result.add(new EvaluationProtocol.Cell(sample.sample_id(),candidate.version_id(),dimension,i));
        result.sort(ORDER);return List.copyOf(result);
    }
    void requireAppend(EvaluationProtocol.Definition definition,String definitionHash,List<EvaluationProtocol.Score> history,EvaluationProtocol.Append request) throws IOException {
        if(!definitionHash.equals(request.definition_sha256())||!matrix(definition.request()).contains(request.cell())) mismatch();
        var sample=definition.request().samples().stream().filter(s->s.sample_id().equals(request.cell().sample_id())).findFirst().orElseThrow();
        if(!sample.input_ref().equals(request.input_ref())) mismatch();
        requireExpectedCounts(definition,request);
        var latest=effective(definition,definitionHash,history);var previous=latest.get(request.cell());
        if(previous==null ? request.supersedes()!=null : !previous.score_id().equals(request.supersedes())) conflict();
        // One actual output per frozen sample/candidate/repetition; different dimensions cannot silently score different outputs.
        for(var score:history) {
            var cell=score.observation().cell();
            if(cell.sample_id().equals(request.cell().sample_id())&&cell.agent_version_id().equals(request.cell().agent_version_id())
                    &&cell.repeat_index()==request.cell().repeat_index()&&score.observation().output_ref()!=null&&request.output_ref()!=null
                    &&!score.observation().output_ref().equals(request.output_ref())) mismatch();
        }
    }
    Map<EvaluationProtocol.Cell,EvaluationProtocol.Score> effective(EvaluationProtocol.Definition definition,String hash,List<EvaluationProtocol.Score> history) throws IOException {
        var matrix=new HashSet<>(matrix(definition.request()));var ids=new HashMap<String,EvaluationProtocol.Score>();var children=new HashMap<String,String>();
        var grouped=new HashMap<EvaluationProtocol.Cell,List<EvaluationProtocol.Score>>();
        for(var score:history) {
            var request=score.observation();var cell=request.cell();
            if(!definition.evaluation_id().equals(score.evaluation_id())||!hash.equals(score.definition_sha256())||!hash.equals(request.definition_sha256())
                    ||!definition.request().rule_revision().equals(score.rule_revision())||!matrix.contains(cell)||ids.putIfAbsent(score.score_id(),score)!=null) mismatch();
            var sample=definition.request().samples().stream().filter(s->s.sample_id().equals(cell.sample_id())).findFirst().orElseThrow();
            if(!sample.input_ref().equals(request.input_ref())) mismatch();
            requireExpectedCounts(definition,request);
            grouped.computeIfAbsent(cell,k->new ArrayList<>()).add(score);
            if(request.supersedes()!=null&&children.putIfAbsent(request.supersedes(),score.score_id())!=null) mismatch();
        }
        for(var score:history)if(score.observation().supersedes()!=null) {
            var parent=ids.get(score.observation().supersedes());
            if(parent==null||!parent.observation().cell().equals(score.observation().cell())
                    ||new BigInteger(parent.observation().expected_revision()).compareTo(new BigInteger(score.observation().expected_revision()))>=0) mismatch();
        }
        var result=new TreeMap<EvaluationProtocol.Cell,EvaluationProtocol.Score>(ORDER);
        for(var entry:grouped.entrySet()) {
            var roots=entry.getValue().stream().filter(s->s.observation().supersedes()==null).toList();
            if(roots.size()!=1) mismatch();var current=roots.get(0);int count=1;
            while(children.containsKey(current.score_id())) { current=ids.get(children.get(current.score_id()));if(++count>entry.getValue().size()) mismatch(); }
            if(count!=entry.getValue().size()) mismatch();result.put(entry.getKey(),current);
        }
        // Re-read checks the same output invariant, including superseded observations.
        var outputs=new HashMap<String,PermissionProtocol.Basis>();
        for(var score:history)if(score.observation().output_ref()!=null) {
            var c=score.observation().cell();String k=c.sample_id()+":"+c.agent_version_id()+":"+c.repeat_index();
            var existing=outputs.putIfAbsent(k,score.observation().output_ref());if(existing!=null&&!existing.equals(score.observation().output_ref())) mismatch();
        }
        return Collections.unmodifiableMap(result);
    }
    EvaluationProtocol.Result compute(EvaluationProtocol.Definition definition,String hash,List<EvaluationProtocol.Score> history)throws IOException {
        var selected=effective(definition,hash,history);var cells=new ArrayList<EvaluationProtocol.CellResult>();var missing=new TreeSet<String>();
        var dimensions=new ArrayList<EvaluationProtocol.DimensionSummary>();
        if(definition.request().samples().isEmpty()) missing.add("NO_SAMPLES");
        if(definition.request().environment().equals("SIMULATION")||definition.request().samples().stream().anyMatch(s->s.evidence_class().equals("SIMULATION"))) missing.add("SIMULATION_IS_NOT_BUSINESS_ACCEPTANCE");
        for(var cell:matrix(definition.request())) {
            var score=selected.get(cell);var result=score==null?new EvaluationProtocol.CellResult(cell,null,"INSUFFICIENT_EVIDENCE","MISSING_SCORE",null):evaluate(definition,score);
            cells.add(result);if(result.status().equals("INSUFFICIENT_EVIDENCE")) missing.add(result.reason());
        }
        for(String dimension:new TreeSet<>(definition.request().required_dimensions())) {
            var group=cells.stream().filter(c->c.cell().dimension().equals(dimension)).toList();
            dimensions.add(new EvaluationProtocol.DimensionSummary(dimension,group.size(),(int)group.stream().filter(c->c.score_id()!=null).count(),
                (int)group.stream().filter(c->c.status().equals("PASS")).count(),(int)group.stream().filter(c->c.status().equals("FAIL")).count(),
                (int)group.stream().filter(c->c.status().equals("INSUFFICIENT_EVIDENCE")).count()));
        }
        String verdict=!missing.isEmpty()?"INSUFFICIENT_EVIDENCE":cells.stream().anyMatch(c->c.status().equals("FAIL"))?"FAIL":"PASS";
        var effective=List.copyOf(selected.values());
        return new EvaluationProtocol.Result("1.0.0",definition.evaluation_id(),hash,schema.hash(effective),effective,cells,dimensions,List.copyOf(missing),verdict,false);
    }
    private EvaluationProtocol.Expectation expectation(EvaluationProtocol.Definition definition,EvaluationProtocol.Cell cell) {
        return definition.request().samples().stream().filter(s->s.sample_id().equals(cell.sample_id())).flatMap(s->s.expectations().stream())
            .filter(e->e.dimension().equals(cell.dimension())).findFirst().orElse(null);
    }
    private void requireExpectedCounts(EvaluationProtocol.Definition definition,EvaluationProtocol.Append observation) {
        var expected=expectation(definition,observation.cell());
        // A missing expected set is retained as insufficient evidence, never filled from the first scorer.
        if(expected==null||!observation.status().equals("SCORED"))return;
        if(!Integer.toString(expected.applicable_item_ids().size()).equals(observation.denominator()))mismatch();
        if(observation.cell().dimension().equals("EVAL-11")&&observation.control_denominator()!=null
            &&!Integer.toString(expected.control_item_ids().size()).equals(observation.control_denominator()))mismatch();
    }
    private EvaluationProtocol.CellResult evaluate(EvaluationProtocol.Definition definition,EvaluationProtocol.Score score) {
        var r=score.observation();String reason=null;String value=null;
        if(expectation(definition,r.cell())==null)reason="MISSING_EXPECTED_ITEM_SET";
        else if(!definition.approved_expectation_samples().contains(r.cell().sample_id()))reason="UNAPPROVED_EXPECTED_ITEM_SET";
        else if(!r.status().equals("SCORED")) reason=r.status()+"_SCORE";
        else if(r.denominator()==null||new BigInteger(r.denominator()).signum()==0) reason="ZERO_OR_MISSING_DENOMINATOR";
        else if(r.output_ref()==null||r.evidence_refs().isEmpty()) reason="MISSING_ACTUAL_OUTPUT_OR_EVIDENCE";
        else {
            value=new BigDecimal(r.numerator()).divide(new BigDecimal(r.denominator()),4,RoundingMode.HALF_UP).toPlainString();
            if(!DETERMINISTIC.contains(r.cell().dimension())) reason="UNAPPROVED_STATISTICAL_THRESHOLD:"+r.cell().dimension();
            else if(r.unknown_count()==null||r.violation_count()==null) reason="MISSING_INVARIANT_COUNTS";
            else if(r.cell().dimension().equals("EVAL-11")&&(r.control_denominator()==null||new BigInteger(r.control_denominator()).signum()==0)) reason="MISSING_LEGAL_CONTROL_DENOMINATOR";
        }
        if(reason!=null)return new EvaluationProtocol.CellResult(r.cell(),score.score_id(),"INSUFFICIENT_EVIDENCE",reason,value);
        boolean pass=r.numerator().equals(r.denominator())&&r.unknown_count().equals("0")&&r.violation_count().equals("0")
            &&(!r.cell().dimension().equals("EVAL-11")||r.control_numerator().equals(r.control_denominator()));
        return new EvaluationProtocol.CellResult(r.cell(),score.score_id(),pass?"PASS":"FAIL",pass?"DOCUMENTED_INVARIANTS_MET":"DOCUMENTED_INVARIANT_FAILED",value);
    }
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void conflict(){throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
}
