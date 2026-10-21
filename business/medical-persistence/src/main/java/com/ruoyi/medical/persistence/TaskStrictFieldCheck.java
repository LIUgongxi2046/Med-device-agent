package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import static com.ruoyi.medical.persistence.StrictFieldComparisonProtocol.*;
import static com.ruoyi.medical.persistence.StrictComparisonPolicyDefinition.*;

/** N03 policy-bound calculation. Public values remain data, never execution/approval proofs. */
public final class TaskStrictFieldCheck {
    public enum Disposition { CHECKED, WAITING_INPUT, DENIED }
    public record Policy(String revision, String sha256, Definition definition) {
        public Policy { com.ruoyi.medical.contracts.WireValues.uuid(revision); com.ruoyi.medical.contracts.WireValues.hash(sha256); Objects.requireNonNull(definition); }
    }
    public record Evaluation(Disposition disposition, Result comparison, List<String> waitingEntries) {
        public Evaluation { waitingEntries = List.copyOf(waitingEntries); }
    }
    private final StrictFieldComparisonSchema schema;
    private final StrictFieldComparisonEngine engine;
    private final Version implementation;
    public TaskStrictFieldCheck(StrictFieldComparisonSchema schema, Version implementation) {
        this.schema=Objects.requireNonNull(schema);this.engine=new StrictFieldComparisonEngine(schema);this.implementation=Objects.requireNonNull(implementation);
    }
    public Evaluation evaluate(Target supplied, Request suppliedRequest, List<Policy> policies, StrictFieldComparisonEngine.Limits limits) throws Exception {
        // Validate closed records before grouping so duplicate entries cannot collapse into a map.
        Target target=schema.target(schema.tree(supplied)); Request request=schema.request(schema.tree(suppliedRequest));
        if(target.entries().isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        Map<String,Policy> byRevision=new HashMap<>();
        for(Policy policy:List.copyOf(policies))if(byRevision.put(policy.revision(),policy)!=null)bad();
        Map<String,EntryRequest> requests=new HashMap<>();for(var entry:request.entries())requests.put(entry.entryId(),entry);
        if(requests.size()!=target.entries().size())bad();
        Map<String,List<Combination>> inventory=new HashMap<>(); Set<String> used=new HashSet<>(); List<String> waiting=new ArrayList<>();
        for(TargetEntry entry:target.entries()) {
            EntryRequest input=requests.remove(entry.entryId());if(input==null)bad();
            Policy policy=byRevision.get(input.rule().ruleRevision());if(policy==null)bad();used.add(policy.revision());
            Definition d=policy.definition();d.requireImplementation(implementation);
            if(!policy.sha256().equals(input.rule().ruleSha256())||!d.operator().name().equals(input.rule().operator()))bad();
            // A comparison's public rule_id is the separately governed policy revision, not a Boolean rule.
            if(!policy.revision().equals(input.rule().ruleId()))bad();
            if(entry.modelContext()==ContextState.UNRESOLVED||entry.conditionContext()==ContextState.UNRESOLVED
                    ||d.applicability().state()==StrictComparisonPolicyDefinition.Applicability.UNKNOWN) {
                waiting.add(entry.entryId());continue;
            }
            Combination combination=new Combination(entry.fieldDefinitionId(),context(entry.modelContext(),entry.modelIds()),context(entry.conditionContext(),entry.conditionIds()));
            var combinations=inventory.computeIfAbsent(policy.revision(),ignored->new ArrayList<>());
            // Repeated occurrences are checked individually; policy combination inventory is a set.
            if(combinations.stream().noneMatch(c->equivalent(c,combination)))combinations.add(combination);
            requireValuePolicy(d,entry.candidateValue());
            if(input.sourceObservation()!=null)requireValuePolicy(d,input.sourceObservation().value());
        }
        if(!requests.isEmpty()||!used.equals(byRevision.keySet()))bad();
        if(!waiting.isEmpty())return new Evaluation(Disposition.WAITING_INPUT,null,waiting);
        for(var policy:policies)policy.definition().requireCoverage(inventory.getOrDefault(policy.revision(),List.of()));
        Result result=engine.compare(target,request,limits);
        for(var entry:result.entries())if(entry.outcome()==Outcome.UNSUPPORTED||entry.result()==ArtifactProtocol.ComparisonResult.MISSING
                ||entry.result()==ArtifactProtocol.ComparisonResult.UNKNOWN_CONDITION)waiting.add(entry.entryId());
        // CHECKED means complete calculation, including conflicts. It never means PASS or human confirmation.
        return new Evaluation(waiting.isEmpty()?Disposition.CHECKED:Disposition.WAITING_INPUT,result,waiting);
    }
    private static boolean equivalent(Combination a,Combination b){return a.fieldDefinitionId().equals(b.fieldDefinitionId())&&a.models().mode()==b.models().mode()
        &&a.conditions().mode()==b.conditions().mode()&&new HashSet<>(a.models().ids()).equals(new HashSet<>(b.models().ids()))&&new HashSet<>(a.conditions().ids()).equals(new HashSet<>(b.conditions().ids()));}
    private static Context context(ContextState state,List<String> ids){return new Context(state==ContextState.KNOWN_GLOBAL?ContextMode.GLOBAL:ContextMode.SCOPED,ids);}
    private static void requireValuePolicy(Definition definition,ArtifactProtocol.FieldValue value){
        if(value==null)return;
        if(!Objects.equals(definition.unit().literal(),value.unit()))bad();
        Long expected=definition.precision().scale()==null?null:definition.precision().scale().longValue();
        if(!Objects.equals(expected,value.precision()))bad();
        if(!definition.operator().name().startsWith(value.valueKind().name()+"_"))bad();
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
