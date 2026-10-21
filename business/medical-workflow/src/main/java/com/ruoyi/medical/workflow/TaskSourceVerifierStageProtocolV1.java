package com.ruoyi.medical.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Additive N05 contract. AT07 identity is returned by its real transaction, never
 * predicted from a command, and is neither an N02 artifact nor document adoption. */
public final class TaskSourceVerifierStageProtocolV1 {
    private TaskSourceVerifierStageProtocolV1(){}
    public record ReadyStepV1(String stepId,String inputSha256,List<String> chapterIds){
        public ReadyStepV1{WireValues.uuid(stepId);WireValues.hash(inputSha256);chapterIds=chapters(chapterIds);}
    }
    public record CommandV1(String runId,String stepId,String inputSha256,String commandId,List<String> chapterIds){
        public CommandV1{ids(runId,stepId,commandId);WireValues.hash(inputSha256);chapterIds=chapters(chapterIds);}
        public void require(Reference r){if(r.executionKind()!=ExecutionKind.TASK||!r.runId().equals(runId))invalid();}
        public ReadyStepV1 ready(){return new ReadyStepV1(stepId,inputSha256,chapterIds);}
    }
    public record DispatchedV1(CommandV1 command,Dispatch dispatch){
        public DispatchedV1{Objects.requireNonNull(command);Objects.requireNonNull(dispatch);if(!command.runId().equals(dispatch.runId())||!command.stepId().equals(dispatch.stepId())||!command.commandId().equals(dispatch.commandId())||!command.inputSha256().equals(dispatch.inputSha256()))invalid();}
    }
    /** Exact independently produced AT07 receipt; never an N02 completion or professional approval. */
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=ReviewCompletedDeserializer.class)
    public record ReviewCompletedV1(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("node_kind") String nodeKind,
        @JsonProperty("tenant_id") String tenantId,
        @JsonProperty("run_id") String runId,
        @JsonProperty("step_id") String stepId,
        @JsonProperty("action_id") String actionId,
        @JsonProperty("input_sha256") String inputSha256,
        @JsonProperty("check_id") String checkId,
        @JsonProperty("target_kind") String targetKind,
        @JsonProperty("candidate_id") String candidateId,
        @JsonProperty("target_revision") String targetRevision,
        @JsonProperty("target_sha256") String targetSha256,
        @JsonProperty("findings_artifact_id") String findingsArtifactId,
        @JsonProperty("findings_receipt_sha256") String findingsReceiptSha256,
        @JsonProperty("coverage_artifact_id") String coverageArtifactId,
        @JsonProperty("coverage_receipt_sha256") String coverageReceiptSha256,
        @JsonProperty("coverage_complete") boolean coverageComplete,
        @JsonProperty("status") String status,
        @JsonProperty("professional_approval") boolean professionalApproval,
        @JsonProperty("audit_id") String auditId,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("issue_proposal_ids") List<String> issueProposalIds){
        public ReviewCompletedV1{version(schemaVersion);if(!"N05".equals(nodeKind)||!"CANDIDATE".equals(targetKind)||!Set.of("SUCCEEDED","WAITING_INPUT").contains(status)||professionalApproval)invalid();ids(tenantId,runId,stepId,actionId,checkId,candidateId,targetRevision,findingsArtifactId,coverageArtifactId,auditId,eventId);hashes(inputSha256,targetSha256,findingsReceiptSha256,coverageReceiptSha256);issueProposalIds=List.copyOf(issueProposalIds);issueProposalIds.forEach(WireValues::uuid);if(new HashSet<>(issueProposalIds).size()!=issueProposalIds.size())invalid();if("SUCCEEDED".equals(status)&&(!coverageComplete||!issueProposalIds.isEmpty()))invalid();}
        public boolean passed(){return "SUCCEEDED".equals(status)&&coverageComplete&&issueProposalIds.isEmpty();}
        public void require(Reference r,DispatchedV1 original){original.command().require(r);var d=original.dispatch();if(!tenantId.equals(r.tenantId())||!runId.equals(d.runId())||!stepId.equals(d.stepId())||!inputSha256.equals(d.inputSha256()))invalid();}
    }
    public static final class ReviewCompletedDeserializer extends com.fasterxml.jackson.databind.deser.std.StdDeserializer<ReviewCompletedV1>{
        public ReviewCompletedDeserializer(){super(ReviewCompletedV1.class);}
        @Override public ReviewCompletedV1 deserialize(com.fasterxml.jackson.core.JsonParser p,com.fasterxml.jackson.databind.DeserializationContext context)throws java.io.IOException{
            com.fasterxml.jackson.databind.JsonNode n=p.getCodec().readTree(p);
            var fields=List.of("schema_version","node_kind","tenant_id","run_id","step_id","action_id","input_sha256","check_id","target_kind","candidate_id","target_revision","target_sha256","findings_artifact_id","findings_receipt_sha256","coverage_artifact_id","coverage_receipt_sha256","coverage_complete","status","professional_approval","audit_id","event_id","issue_proposal_ids");
            if(!n.isObject()||n.size()!=fields.size()||fields.stream().anyMatch(f->!n.has(f)))throw com.fasterxml.jackson.databind.JsonMappingException.from(p,"N05_ORIGINAL_AT07_RECEIPT_REQUIRED");
            for(String f:fields)if(!Set.of("coverage_complete","professional_approval","issue_proposal_ids").contains(f)&&!n.get(f).isTextual())throw com.fasterxml.jackson.databind.JsonMappingException.from(p,"N05_RECEIPT_TYPE");
            if(!n.get("coverage_complete").isBoolean()||!n.get("professional_approval").isBoolean()||!n.get("issue_proposal_ids").isArray())throw com.fasterxml.jackson.databind.JsonMappingException.from(p,"N05_RECEIPT_TYPE");
            var issues=new ArrayList<String>();for(var issue:n.get("issue_proposal_ids")){if(!issue.isTextual())throw com.fasterxml.jackson.databind.JsonMappingException.from(p,"N05_ISSUE_TYPE");issues.add(issue.textValue());}
            return new ReviewCompletedV1(n.get("schema_version").textValue(),n.get("node_kind").textValue(),n.get("tenant_id").textValue(),n.get("run_id").textValue(),n.get("step_id").textValue(),n.get("action_id").textValue(),n.get("input_sha256").textValue(),n.get("check_id").textValue(),n.get("target_kind").textValue(),n.get("candidate_id").textValue(),n.get("target_revision").textValue(),n.get("target_sha256").textValue(),n.get("findings_artifact_id").textValue(),n.get("findings_receipt_sha256").textValue(),n.get("coverage_artifact_id").textValue(),n.get("coverage_receipt_sha256").textValue(),n.get("coverage_complete").booleanValue(),n.get("status").textValue(),n.get("professional_approval").booleanValue(),n.get("audit_id").textValue(),n.get("event_id").textValue(),issues);
        }
    }
    public record CommitV1(String schemaVersion,DispatchedV1 original,ReviewCompletedV1 review,String receiptId,String receiptSha256){
        public CommitV1{version(schemaVersion);Objects.requireNonNull(original);Objects.requireNonNull(review);ids(receiptId);hashes(receiptSha256);var d=original.dispatch();if(!review.runId().equals(d.runId())||!review.stepId().equals(d.stepId())||!review.inputSha256().equals(d.inputSha256()))invalid();}
        public void require(Reference r){review.require(r,original);}
    }
    /** assigned is the complete durable assignment set, not merely a ready-page.
     * Backend verifies its original producer/graph before returning this snapshot. */
    public record InventoryV1(String schemaVersion,String runId,String graphId,String graphSha256,String obligationSha256,List<String> requiredChapterIds,List<ReadyStepV1> assigned,List<CommandV1> commands,List<DispatchedV1> dispatched,List<CommitV1> commits){
        public InventoryV1{version(schemaVersion);ids(runId,graphId);hashes(graphSha256,obligationSha256);requiredChapterIds=chapters(requiredChapterIds);assigned=List.copyOf(assigned);commands=List.copyOf(commands);dispatched=List.copyOf(dispatched);commits=List.copyOf(commits);
            var nodes=new HashMap<String,ReadyStepV1>();var chapters=new HashSet<String>();for(var s:assigned){if(nodes.put(s.stepId(),s)!=null||s.chapterIds().stream().anyMatch(c->!chapters.add(c)))invalid();}
            if(!new HashSet<>(requiredChapterIds).containsAll(chapters))invalid();
            var admitted=new HashMap<String,CommandV1>();var commandIds=new HashSet<String>();for(var c:commands){if(!runId.equals(c.runId())||!c.ready().equals(nodes.get(c.stepId()))||admitted.put(c.stepId(),c)!=null||!commandIds.add(c.commandId()))invalid();}
            var originals=new HashMap<String,DispatchedV1>();for(var d:dispatched){if(!runId.equals(d.command().runId())||!d.command().ready().equals(nodes.get(d.command().stepId()))||originals.put(d.command().stepId(),d)!=null||!d.command().equals(admitted.get(d.command().stepId())))invalid();}
            var done=new HashSet<String>();for(var c:commits){if(!c.original().equals(originals.get(c.original().command().stepId()))||!done.add(c.original().command().stepId()))invalid();}}
        public void require(Reference r,Plan plan){if(r.executionKind()!=ExecutionKind.TASK||!runId.equals(r.runId())||!Objects.equals(graphId,plan.graphReceiptId())||!Objects.equals(graphSha256,plan.graphSha256())||plan.requiredStages().stream().filter(s->"N05".equals(s.stageKind())&&obligationSha256.equals(s.obligationSha256())).count()!=1||(long)assigned.size()+plan.plannedSteps().size()>r.maximumSteps())invalid();commits.forEach(c->c.require(r));}
        public boolean satisfied(){return !assigned.isEmpty()&&commits.size()==assigned.size()&&commits.stream().allMatch(c->c.review().passed())&&assigned.stream().mapToInt(s->s.chapterIds().size()).sum()==requiredChapterIds.size();}
        public List<ReadyStepV1> ready(){var taken=new HashSet<String>();commands.forEach(c->taken.add(c.stepId()));return assigned.stream().filter(s->!taken.contains(s.stepId())).toList();}
    }
    public record RecoveryV1(String schemaVersion,String state,CommandV1 command,DispatchedV1 original,ReviewCompletedV1 review,CommitV1 receipt){
        public RecoveryV1{version(schemaVersion);Objects.requireNonNull(command);if(!Set.of("PENDING","PRODUCED","COMMITTED").contains(state)||(review!=null)!=state.equals("PRODUCED")||(receipt!=null)!=state.equals("COMMITTED")||!state.equals("PENDING")&&original==null)invalid();if(original!=null&&!command.equals(original.command())||receipt!=null&&!receipt.original().equals(original))invalid();if(review!=null&&(!review.runId().equals(command.runId())||!review.stepId().equals(command.stepId())||!review.inputSha256().equals(command.inputSha256())))invalid();}
        public void require(Reference r,CommandV1 expected){command.require(r);if(!command.equals(expected))invalid();if(review!=null)review.require(r,original);if(receipt!=null)receipt.require(r);}
    }
    private static List<String> chapters(List<String> values){var copy=List.copyOf(values);if(copy.isEmpty()||new HashSet<>(copy).size()!=copy.size())invalid();copy.forEach(WireValues::uuid);return copy;}
    private static void ids(String...values){for(var v:values)WireValues.uuid(v);}private static void hashes(String...values){for(var v:values)WireValues.hash(v);}
    private static void version(String v){if(!"1.0.0".equals(v))invalid();}private static void invalid(){throw new IllegalArgumentException("TASK_SOURCE_VERIFIER_STAGE_V1_INVALID");}
}
