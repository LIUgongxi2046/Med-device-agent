package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import com.ruoyi.medical.workflow.TaskCandidateAssemblyStageProtocolV1.CommandV1;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Human entry: physical reads outside SQL; short fresh-admission callbacks inside.
 * INITIAL and partial adoption share the same explicit one-use confirmation. */
public final class DocumentCandidateAdoptionEntryService {
    private final EnterpriseAccess access;private final JdbcTemplate db;private final ResourceReadAuthority resources;
    private final DocumentReadService documents;private final TaskCandidateAssemblyOriginalReader originals;
    private final TaskCandidateAssemblyStageJournal stages;private final DocumentCandidateAdoptionAuthority authority;
    private final DocumentCandidateAdoptionService actions;private final DocumentCandidateAdoptionSaveService saves;
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    private final JsonMapper mapper=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    public DocumentCandidateAdoptionEntryService(EnterpriseAccess access,DataSource data,ResourceReadAuthority resources,DocumentReadService documents,TaskCandidateAssemblyOriginalReader originals,TaskCandidateAssemblyStageJournal stages,DocumentCandidateAdoptionAuthority authority,DocumentCandidateAdoptionService actions,DocumentCandidateAdoptionSaveService saves){this.access=access;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.resources=resources;this.documents=documents;this.originals=originals;this.stages=stages;this.authority=authority;this.actions=actions;this.saves=saves;}
    private record Located(Reference reference,CommandV1 command,String document,String base){}
    public JsonNode materialize(ApplicationSessionIdentity.Verified subject,String context,String run)throws Exception {
        var located=tx(subject,a->locate(a,context,run,null));var base=documents.readFixed(subject,context,located.document(),located.base());
        return originals.readHuman(subject,context,located.reference(),located.command(),(original,family)->view(authority.require(original.admission(),original,family,base)));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String candidate)throws Exception {
        var located=tx(subject,a->locate(a,context,null,candidate));var base=documents.readFixed(subject,context,located.document(),located.base());
        return originals.readHuman(subject,context,located.reference(),located.command(),(original,family)->{var proof=authority.requireRead(original.admission(),original,family,base);same(candidate,proof.candidateId());return view(proof);});
    }
    public JsonNode candidateView(ApplicationSessionIdentity.Verified subject,String context,String candidate)throws Exception {
        var located=tx(subject,a->locate(a,context,null,candidate));var base=documents.readFixed(subject,context,located.document(),located.base());
        return originals.readHuman(subject,context,located.reference(),located.command(),(original,family)->{var proof=authority.requireRead(original.admission(),original,family,base);same(candidate,proof.candidateId());var view=json.tree(proof.view(original.admission()));SchemaCatalogue.fromClasspath().validate("interfaces",view,"CandidateView");return view;});
    }
    public JsonNode preview(ApplicationSessionIdentity.Verified subject,String context,String key,String candidate,DocumentCandidateAdoptionProtocol.PreviewInput input)throws Exception {
        var located=tx(subject,a->locate(a,context,null,candidate));var base=documents.readFixed(subject,context,located.document(),located.base());
        return originals.readHuman(subject,context,located.reference(),located.command(),(original,family)->{var proof=authority.require(original.admission(),original,family,base);same(candidate,proof.candidateId());return actions.preview(original.admission(),context,key,proof,input);});
    }
    public JsonNode confirm(ApplicationSessionIdentity.Verified subject,String context,String key,String candidate,DocumentCandidateAdoptionProtocol.ConfirmInput input)throws Exception {
        var located=tx(subject,a->locate(a,context,null,candidate));
        // Resolve original completion before current candidate/head eligibility; expiry is not renewed.
        var originalResult=tx(subject,a->{var rows=db.queryForList("SELECT response_ref,request_sha256 FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name='DC-03' AND idempotency_key=? AND state='COMPLETED' LIMIT 2",a.context().tenant_id(),a.actorId(),key);if(rows.isEmpty())return null;var row=one(rows);same(json.hash(Map.of("operation","DC-03","candidate_id",candidate,"input",input)),str(row,"request_sha256"));return json.read(str(row,"response_ref"));});
        if(originalResult!=null){String revision=originalResult.required("data").required("document_revision").asText();var saved=documents.readFixed(subject,context,located.document(),revision);return originals.readHuman(subject,context,located.reference(),located.command(),(original,family)->{stages.recovered(original,family);stages.committed(original).orElseThrow();return actions.original(original.admission(),context,key,candidate,input,saved,documents,resources);});}
        var base=documents.readFixed(subject,context,located.document(),located.base());
        var loaded=originals.prepareHuman(subject,context,located.reference(),located.command());
        return saves.save(subject,new DocumentCandidateAdoptionSaveService.ConfirmedProvider(){public <T>T withCurrent(DocumentCandidateAdoptionSaveService.ConfirmedWork<T> work)throws Exception{return loaded.withCurrent((original,family)->{var a=original.admission();var proof=authority.require(a,original,family,base);same(candidate,proof.candidateId());var confirmed=actions.confirm(a,context,key,proof,input);return work.apply(a,confirmed);});}});
    }
    @FunctionalInterface interface CandidateWork<T>{T apply(EnterpriseAccess.Admission admission,DocumentCandidateAdoptionAuthority.Verified candidate,DocumentReadService.FixedRead target)throws Exception;}
    final class ComparisonInputs {
        private final String candidateId;private final DocumentReadService.FixedRead base,target;private final TaskCandidateAssemblyOriginalReader.Loaded loaded;
        private ComparisonInputs(String candidateId,DocumentReadService.FixedRead base,DocumentReadService.FixedRead target,TaskCandidateAssemblyOriginalReader.Loaded loaded){this.candidateId=candidateId;this.base=base;this.target=target;this.loaded=loaded;}
        <T>T withCurrent(CandidateWork<T> work)throws Exception{return loaded.withCurrent((original,family)->{var a=original.admission();var candidate=authority.requireRead(a,original,family,base);same(candidateId,candidate.candidateId());documents.requireCurrent(a,target);same(target.header().documentId(),candidate.header().documentId());same(target.header().documentRevision(),target.header().currentDocumentRevision());return work.apply(a,candidate,target);});}
    }
    ComparisonInputs comparisonInputs(ApplicationSessionIdentity.Verified subject,String context,String candidate,String targetRevision)throws Exception{
        var located=tx(subject,a->locate(a,context,null,candidate));var base=documents.readFixed(subject,context,located.document(),located.base());var target=targetRevision.equals(located.base())?base:documents.readFixed(subject,context,located.document(),targetRevision);var loaded=originals.prepareHuman(subject,context,located.reference(),located.command());return new ComparisonInputs(candidate,base,target,loaded);
    }
    private Located locate(EnterpriseAccess.Admission a,String context,String run,String candidate)throws Exception {
        same(context,a.context().context_revision());if(candidate!=null){WireValues.uuid(candidate);var row=one(db.queryForList("SELECT project_id,run_id FROM candidate WHERE tenant_id=? AND candidate_id=? AND candidate_kind='DOCUMENT' LIMIT 2 FOR UPDATE",a.context().tenant_id(),candidate));resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),str(row,"project_id"),"CANDIDATE",candidate),"candidate.read","DOCUMENT_BODY");run=str(row,"run_id");}
        WireValues.uuid(run);var launch=one(db.queryForList("SELECT l.*,r.task_id FROM task_run_workflow_launch l JOIN task_run r ON r.tenant_id=l.tenant_id AND r.project_id=l.project_id AND r.run_id=l.run_id WHERE l.tenant_id=? AND l.run_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),run));
        resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),str(launch,"project_id"),"TASK",str(launch,"task_id")),"task.read","DOCUMENT_BODY");
        var reference=json.read(str(launch,"reference_json"));same(json.hash(reference),str(launch,"reference_sha256"));var intent=json.read(str(launch,"intent_json"));same(json.hash(intent),str(launch,"intent_sha256"));same(reference,intent.required("reference"));
        var ref=mapper.treeToValue(reference,Reference.class);same(run,ref.runId());same(a.context().tenant_id(),ref.tenantId());
        var original=originals.humanOriginal(a,ref);var command=original.command();stages.committed(original).orElseThrow(()->new TenantAccessException(ErrorCode.STEP_NOT_READY));
        return new Located(ref,command,original.input().required("document_id").asText(),original.input().required("base_document_revision").asText());
    }
    private JsonNode view(DocumentCandidateAdoptionAuthority.Verified proof)throws Exception {
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(Map.of("schema_version","1.0.0","candidate_id",proof.candidateId(),"candidate_sha256",proof.candidateSha256(),"document_id",proof.header().documentId(),"base_document_revision",proof.header().documentRevision(),"expected_document_revision",proof.header().revision().toString(),"target_node_ids",proof.targetNodes(),"check_ids",proof.checkIds(),"body",proof.body(),"professional_approval",false));value.set("base_body",proof.baseBody());value.put("new_evidence_path","/evidence_links");return value;
    }
    @FunctionalInterface private interface Work<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}
    private <T>T tx(ApplicationSessionIdentity.Verified subject,Work<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.apply(a)).get());}
    private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.CANDIDATE_STALE);return rows.get(0);}
    private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))throw new TenantAccessException(ErrorCode.CANDIDATE_STALE);}
}
