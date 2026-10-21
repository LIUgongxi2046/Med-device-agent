package com.ruoyi.medical.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.medical.contracts.WireValues;
import java.util.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;

/** Additive N04 contract. AT06 identity is returned by its real transaction, never
 * predicted from a command, and is neither an N02 artifact nor document adoption. */
public final class TaskSectionWriterStageProtocolV1 {
    private TaskSectionWriterStageProtocolV1(){}
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
    /** Exact Python/AT06 snake_case payload. Explicit properties override all naming policies. */
    @JsonIgnoreProperties(ignoreUnknown=false)
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=CandidateReadyDeserializer.class)
    public record CandidateReadyV1(
        @JsonProperty("schema_version") String schemaVersion,@JsonProperty("candidate_id") String candidateId,
        @JsonProperty("status") String status,@JsonProperty("revision") String revision,
        @JsonProperty("tenant_id") String tenantId,@JsonProperty("run_id") String runId,
        @JsonProperty("step_id") String stepId,@JsonProperty("action_id") String actionId,
        @JsonProperty("input_sha256") String inputSha256,@JsonProperty("candidate_artifact_id") String candidateArtifactId,
        @JsonProperty("candidate_receipt_sha256") String candidateReceiptSha256,@JsonProperty("body_artifact_id") String bodyArtifactId,
        @JsonProperty("body_sha256") String bodySha256,@JsonProperty("base_document_revision") String baseDocumentRevision,
        @JsonProperty("audit_id") String auditId,@JsonProperty("event_id") String eventId){
        public CandidateReadyV1{version(schemaVersion);if(!"READY".equals(status)||!"1".equals(revision))invalid();ids(candidateId,tenantId,runId,stepId,actionId,candidateArtifactId,bodyArtifactId,baseDocumentRevision,auditId,eventId);hashes(inputSha256,candidateReceiptSha256,bodySha256);}
        public void require(Reference r,DispatchedV1 original){original.command().require(r);var d=original.dispatch();if(!tenantId.equals(r.tenantId())||!runId.equals(d.runId())||!stepId.equals(d.stepId())||!inputSha256.equals(d.inputSha256()))invalid();}
    }
    /** Temporal's shared converter may be permissive for old messages; enforce this new
     * result's closed shape locally without changing old payload converter behavior. */
    public static final class CandidateReadyDeserializer extends com.fasterxml.jackson.databind.deser.std.StdDeserializer<CandidateReadyV1>{
        public CandidateReadyDeserializer(){super(CandidateReadyV1.class);}
        @Override public CandidateReadyV1 deserialize(com.fasterxml.jackson.core.JsonParser p,com.fasterxml.jackson.databind.DeserializationContext context)throws java.io.IOException{
            com.fasterxml.jackson.databind.JsonNode n=p.getCodec().readTree(p);
            var fields=List.of("schema_version","candidate_id","status","revision","tenant_id","run_id","step_id","action_id","input_sha256","candidate_artifact_id","candidate_receipt_sha256","body_artifact_id","body_sha256","base_document_revision","audit_id","event_id");
            if(!n.isObject()||n.size()!=fields.size()||fields.stream().anyMatch(f->!n.has(f)||!n.get(f).isTextual()))throw com.fasterxml.jackson.databind.JsonMappingException.from(p,"N04_ORIGINAL_AT06_RECEIPT_REQUIRED");
            return new CandidateReadyV1(n.get("schema_version").textValue(),n.get("candidate_id").textValue(),n.get("status").textValue(),n.get("revision").textValue(),n.get("tenant_id").textValue(),n.get("run_id").textValue(),n.get("step_id").textValue(),n.get("action_id").textValue(),n.get("input_sha256").textValue(),n.get("candidate_artifact_id").textValue(),n.get("candidate_receipt_sha256").textValue(),n.get("body_artifact_id").textValue(),n.get("body_sha256").textValue(),n.get("base_document_revision").textValue(),n.get("audit_id").textValue(),n.get("event_id").textValue());
        }
    }
    public record CommitV1(String schemaVersion,DispatchedV1 original,CandidateReadyV1 candidate,String receiptId,String receiptSha256){
        public CommitV1{version(schemaVersion);Objects.requireNonNull(original);Objects.requireNonNull(candidate);ids(receiptId);hashes(receiptSha256);var d=original.dispatch();if(!candidate.runId().equals(d.runId())||!candidate.stepId().equals(d.stepId())||!candidate.inputSha256().equals(d.inputSha256()))invalid();}
        public void require(Reference r){candidate.require(r,original);}
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
        public void require(Reference r,Plan plan){if(r.executionKind()!=ExecutionKind.TASK||!runId.equals(r.runId())||!Objects.equals(graphId,plan.graphReceiptId())||!Objects.equals(graphSha256,plan.graphSha256())||plan.requiredStages().stream().filter(s->"N04".equals(s.stageKind())&&obligationSha256.equals(s.obligationSha256())).count()!=1||(long)assigned.size()+plan.plannedSteps().size()>r.maximumSteps())invalid();commits.forEach(c->c.require(r));}
        public boolean satisfied(){return !assigned.isEmpty()&&commits.size()==assigned.size()&&assigned.stream().mapToInt(s->s.chapterIds().size()).sum()==requiredChapterIds.size();}
        public List<ReadyStepV1> ready(){var taken=new HashSet<String>();commands.forEach(c->taken.add(c.stepId()));return assigned.stream().filter(s->!taken.contains(s.stepId())).toList();}
    }
    public record RecoveryV1(String schemaVersion,String state,CommandV1 command,DispatchedV1 original,CandidateReadyV1 candidate,CommitV1 receipt){
        public RecoveryV1{version(schemaVersion);Objects.requireNonNull(command);if(!Set.of("PENDING","PRODUCED","COMMITTED").contains(state)||(candidate!=null)!=state.equals("PRODUCED")||(receipt!=null)!=state.equals("COMMITTED")||!state.equals("PENDING")&&original==null)invalid();if(original!=null&&!command.equals(original.command())||receipt!=null&&!receipt.original().equals(original))invalid();if(candidate!=null&&(!candidate.runId().equals(command.runId())||!candidate.stepId().equals(command.stepId())||!candidate.inputSha256().equals(command.inputSha256())))invalid();}
        public void require(Reference r,CommandV1 expected){command.require(r);if(!command.equals(expected))invalid();if(candidate!=null)candidate.require(r,original);if(receipt!=null)receipt.require(r);}
    }
    private static List<String> chapters(List<String> values){var copy=List.copyOf(values);if(copy.isEmpty()||new HashSet<>(copy).size()!=copy.size())invalid();copy.forEach(WireValues::uuid);return copy;}
    private static void ids(String...values){for(var v:values)WireValues.uuid(v);}private static void hashes(String...values){for(var v:values)WireValues.hash(v);}
    private static void version(String v){if(!"1.0.0".equals(v))invalid();}private static void invalid(){throw new IllegalArgumentException("TASK_SECTION_WRITER_STAGE_V1_INVALID");}
}
