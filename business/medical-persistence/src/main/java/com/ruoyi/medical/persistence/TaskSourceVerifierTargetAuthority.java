package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Genuine saved N04 target identity, re-authorized for a different N05 session. Reading
 * this proof does not endorse writer claims and is not an independent source-read receipt. */
public final class TaskSourceVerifierTargetAuthority {
    private TaskRunResumeOriginalAuthority originals;private org.springframework.beans.factory.ObjectProvider<TaskSectionWriterInputAuthority> writers;private org.springframework.beans.factory.ObjectProvider<TaskSectionWriterStageCommitService> commits;
    public TaskSourceVerifierTargetAuthority withOriginalReaders(TaskRunResumeOriginalAuthority originals,org.springframework.beans.factory.ObjectProvider<TaskSectionWriterInputAuthority> writers,org.springframework.beans.factory.ObjectProvider<TaskSectionWriterStageCommitService> commits){this.originals=Objects.requireNonNull(originals);this.writers=Objects.requireNonNull(writers);this.commits=Objects.requireNonNull(commits);return this;}

    private final JdbcTemplate db;private final TaskSourceVerifierSchema json;private final ArtifactRegistryMapper registry;
    private final TaskRunReplayContentAuthority contents;private final ResourceReadAuthority resources;private final SchemaCatalogue schemas;
    public TaskSourceVerifierTargetAuthority(DataSource data,TaskSourceVerifierSchema json,ArtifactRegistryMapper registry,
            TaskRunReplayContentAuthority contents,ResourceReadAuthority resources)throws Exception{
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.registry=registry;this.contents=contents;this.resources=resources;schemas=SchemaCatalogue.fromClasspath();
    }
    public Verified require(TaskNodeProductionAuthority.Verified production,String candidateId)throws Exception {
        return require(production,candidateId,null,null);
    }
    public Verified require(TaskNodeProductionAuthority.Verified p,String candidateId,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        WireValues.uuid(candidateId);return new Verified(p,candidateId,publication,policy,inspect(p,candidateId,publication,policy));
    }
    private State inspect(TaskNodeProductionAuthority.Verified p,String candidateId,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        p.requireUnchanged();var closure=contents.require(p);var replay=p.replay().content();
        resources.require(p.admission(),new PermissionProtocol.Scope(p.tenantId(),p.projectId(),"TASK",p.taskId()),"task.read","TASK_READING");
        String originalRun=p.runId();TaskRunResumeOriginalAuthority.Source source=null;
        var aliases=db.queryForList("SELECT r.step_id FROM task_run_success_reuse r JOIN candidate c ON c.tenant_id=r.tenant_id AND c.project_id=r.project_id AND c.run_id=r.original_run_id AND c.step_id=r.original_step_id WHERE r.tenant_id=? AND r.run_id=? AND r.stage_kind='N04' AND c.candidate_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId(),candidateId);
        if(!aliases.isEmpty()){if(originals==null||publication==null||policy==null)deny();source=originals.requireSaved(p,str(one(aliases),"step_id"));source.requireUnchanged();originalRun=source.originalRunId();}
        var c=one(db.queryForList("SELECT * FROM candidate WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND candidate_id=? AND candidate_kind='DOCUMENT' AND status='READY' LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),originalRun,candidateId));
        String writerStep=str(c,"step_id"),action=str(c,"action_id");
        var submitted=one(db.queryForList("SELECT * FROM task_section_writer_submission WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),originalRun,writerStep,action,candidateId));
        var receipt=json.read(str(submitted,"receipt_json"));same(json.hash(receipt),str(submitted,"receipt_sha256"));
        for(String key:List.of("candidate_id","run_id","step_id","action_id","input_sha256","base_document_revision"))same(str(c,key),text(receipt,key));
        same(candidateId,text(receipt,"candidate_id"));same(p.actorId(),str(submitted,"created_by"));journal(p,receipt);
        var artifact=object(p,originalRun,str(c,"new_evidence_artifact_id"),"SECTION_CANDIDATE",writerStep,action);
        var body=object(p,originalRun,str(c,"content_artifact_id"),"SECTION_BODY",writerStep,action);
        same(artifact.id,text(receipt,"candidate_artifact_id"));same(artifact.receiptHash,text(receipt,"candidate_receipt_sha256"));
        same(body.id,text(receipt,"body_artifact_id"));same(body.object.sha256(),text(receipt,"body_sha256"));same(body.object.sha256(),str(c,"content_sha256"));same(json.tree(body.object),json.read(str(c,"content_ref")));same(text(replay,"plan_revision"),str(c,"plan_revision"));same(text(replay,"snapshot_id"),str(c,"snapshot_id"));
        if(writers==null||publication==null||policy==null)deny();
        var writerInput=source==null?writers.getObject().require(p.admission(),p.runId(),writerStep,publication,policy,p):writers.getObject().requireOriginal(source,publication,policy);
        var contract=writerInput.evidenceContract();
        same(contract.artifactRef(null),artifact.schemaRef);same(contract.artifactSha(),artifact.schemaSha);
        same(contract.bodyRef(),body.schemaRef);same(contract.bodySha(),body.schemaSha);writerInput.requireUnchanged();
        var full=ArtifactEvidenceBindings.bind(schemas,ArtifactBindings.ARTIFACT,artifact.payload,artifact.schemaRef,artifact.schemaSha).read();
        if(!(full.output() instanceof ArtifactProtocol.SectionOutput))bad();var section=((ArtifactProtocol.SectionOutput)full.output()).content();
        same(p.tenantId(),full.tenantId());same(p.projectId(),full.projectId());same(originalRun,full.runId());same(writerStep,full.stepId());
        same(str(c,"input_sha256"),full.inputManifestSha256());same(text(replay,"plan_revision"),full.planRevision());same(text(replay,"snapshot_id"),full.snapshotId());
        same(str(c,"base_document_revision"),section.baseDocumentRevision());same(json.tree(section.targetSections()),json.read(str(c,"target_sections")));
        same(json.hash(full.output()),full.contentSha256());same(artifact.accessSet,full.dependencyAccessSetId());same(full.targetSectionIds(),section.targetSections());
        var bodyRef=section.bodyObjectRef();same(body.object.objectKey(),bodyRef.objectKey());same(body.object.objectVersion(),bodyRef.objectVersion());same(body.object.sha256(),bodyRef.sha256());same(Long.parseLong(body.object.sizeBytes()),bodyRef.sizeBytes());
        DocumentBodySemantics.verify(schemas,body.payload,new DocumentBodySemantics.Limits(10000,10000,100000),body.schemaRef,body.schemaSha);
        var nodes=new LinkedHashMap<String,JsonNode>();for(var n:body.payload.required("nodes"))if(nodes.put(text(n,"node_id"),n)!=null)bad();
        var manifested=new HashSet<String>();for(var item:section.nodeManifest()){
            var n=nodes.get(item.nodeId());if(n==null||!manifested.add(item.nodeId())||!json.hash(n).equals(item.contentSha256())||!section.targetSections().contains(item.sectionId()))bad();
        }if(!manifested.equals(nodes.keySet()))bad();
        var claims=new LinkedHashMap<String,ArtifactProtocol.ClaimRecord>();var evidence=new LinkedHashSet<DatasetProtocol.EvidenceRef>();
        for(var claim:section.claimRecords()){if(claims.putIfAbsent(claim.claimId(),claim)!=null)bad();claim.evidenceLinks().forEach(link->evidence.add(link.evidenceRef()));}
        DocumentEvidenceBindings.bind(schemas,InterfaceBindings.DOCUMENT_BODY,body.payload,body.schemaRef,body.schemaSha).read().evidenceLinks().forEach(l->evidence.add(l.evidence()));section.fieldAssignments().forEach(f->evidence.addAll(f.evidenceRefs()));section.attachmentBindings().forEach(f->evidence.addAll(f.evidenceRefs()));
        var permittedSources=new HashSet<String>();for(var fixedSource:replay.required("scope_snapshot").required("members"))permittedSources.add(text(fixedSource,"source_version"));
        for(var ref:evidence)if(!p.tenantId().equals(ref.tenantId())||!p.projectId().equals(ref.projectId())||!permittedSources.contains(ref.sourceVersion()))deny();
        for(var obj:List.of(artifact,body))if(!new HashSet<>(closure.members()).containsAll(registry.members(obj.accessSet)))deny();
        var writerSessions=new LinkedHashSet<String>();for(var obj:List.of(artifact,body)){
            var provenance=obj.receipt.required("provenance");same("N04",text(provenance,"node_kind"));same(action,text(provenance,"action_id"));
            same(str(c,"input_sha256"),text(provenance,"input_sha256"));same(obj.object.sha256(),text(provenance,"payload_sha256"));String session=text(provenance,"session_id");WireValues.uuid(session);writerSessions.add(session);
            var stored=one(db.queryForList("SELECT start_sha256 FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? AND created_by=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),originalRun,writerStep,session,p.actorId()));
        }
        if(writerSessions.size()!=1)bad();
        if(source!=null){var input=writers.getObject().requireOriginal(source,publication,policy);var commit=commits.getObject().requireForConsumer(input);same(candidateId,commit.receipt().candidate().candidateId());commit.requireUnchanged();source.requireUnchanged();}
        closure.requireUnchanged();p.requireUnchanged();
        var witness=json.tree(Map.of("candidate",c,"submission",submitted,"artifact",artifact.receipt,"body",body.receipt,"content_members",closure.members()));
        return new State(candidateId,writerStep,section,body.payload,artifact,body,Collections.unmodifiableMap(claims),Set.copyOf(evidence),Set.copyOf(writerSessions),closure.roots(),closure.members(),json.hash(witness));
    }
    private Saved object(TaskNodeProductionAuthority.Verified p,String originalRun,String artifactId,String kind,String step,String action)throws Exception {
        var row=one(db.queryForList("SELECT * FROM task_section_writer_object WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND action_id=? AND artifact_id=? AND kind=? AND state='REGISTERED' LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),originalRun,step,action,artifactId,kind));
        var payload=json.read(str(row,"canonical_payload"));var receipt=json.read(str(row,"receipt_json"));same(json.hash(payload),str(row,"payload_sha256"));same(json.hash(receipt),str(row,"receipt_sha256"));same(p.actorId(),str(row,"created_by"));
        var ar=one(registry.artifact(artifactId));same(p.tenantId(),ar.tenantId());same(p.projectId(),ar.projectId());same(kind,ar.artifactKind());same(json.hash(payload),ar.reference().sha256());same(ar.contentSha256(),ar.reference().sha256());StoredObjectReferences.decode(ar.storedObject(),ar.reference());same(json.tree(ar.reference()),receipt.required("object_ref"));
        var intent=one(db.queryForList("SELECT * FROM task_section_writer_object_intent WHERE tenant_id=? AND write_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.tenantId(),str(row,"write_id"),artifactId));
        same(str(row,"canonical_payload"),str(intent,"canonical_payload"));same(str(row,"payload_sha256"),str(intent,"payload_sha256"));same(json.hash(json.read(str(intent,"intent_json"))),str(intent,"intent_sha256"));same(str(intent,"intent_sha256"),text(receipt,"intent_sha256"));
        var attempt=one(db.queryForList("SELECT intent_sha256 FROM task_section_writer_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",p.tenantId(),str(row,"write_id")));same(str(intent,"intent_sha256"),str(attempt,"intent_sha256"));journal(p,receipt);
        var binding=one(db.queryForList("SELECT schema_ref,schema_document_sha256 FROM artifact_content_binding WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),artifactId));
        String declaredRef=str(binding,"schema_ref"),declaredSha=str(binding,"schema_document_sha256");schemas.requirePublished(declaredRef,declaredSha);
        same(declaredSha,str(row,"schema_manifest_sha256"));same(declaredSha,str(intent,"schema_manifest_sha256"));
        return new Saved(artifactId,ar.reference(),payload,receipt,str(row,"receipt_sha256"),ar.accessSetId(),declaredRef,declaredSha);
    }
    private void journal(TaskNodeProductionAuthority.Verified p,JsonNode receipt)throws Exception {
        var audit=one(db.queryForList("SELECT resource_ref,result,subject_id FROM audit_event WHERE tenant_id=? AND audit_id=? LIMIT 2 FOR UPDATE",p.tenantId(),text(receipt,"audit_id")));
        same("SUCCESS",str(audit,"result"));same(p.actorId(),str(audit,"subject_id"));same(receipt,json.read(str(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? LIMIT 2 FOR UPDATE",p.tenantId(),text(receipt,"event_id")));same(receipt,json.read(str(event,"payload")));
    }
    private record Saved(String id,InterfaceProtocol.ObjectRef object,JsonNode payload,JsonNode receipt,String receiptHash,String accessSet,String schemaRef,String schemaSha){}
    private record State(String id,String writerStep,ArtifactProtocol.SectionCandidate candidate,JsonNode body,Saved artifact,Saved bodyObject,Map<String,ArtifactProtocol.ClaimRecord> claims,Set<DatasetProtocol.EvidenceRef> evidence,Set<String> writerSessions,List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members,String hash){}
    public final class Verified {
        private final TaskNodeProductionAuthority.Verified production;private final String candidateId;private final State state;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;
        private Verified(TaskNodeProductionAuthority.Verified p,String id,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,State state){production=p;candidateId=id;this.publication=publication;this.policy=policy;this.state=state;}
        public void requireUnchanged()throws Exception{production.requireUnchanged();if(!state.hash.equals(inspect(production,candidateId,publication,policy).hash))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
        public TaskNodeProductionAuthority.Verified production(){production.admission();return production;}
        public TaskSectionWriterInputAuthority.Verified writerInput(AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
            var rows=db.queryForList("SELECT step_id FROM task_run_success_reuse WHERE tenant_id=? AND run_id=? AND stage_kind='N04' AND original_step_id=? LIMIT 2 FOR UPDATE",production.tenantId(),production.runId(),writerStepId());
            if(rows.isEmpty())return writers.getObject().require(production.admission(),production.runId(),writerStepId(),publication,policy,production);
            return writers.getObject().requireOriginal(originals.requireSaved(production,str(one(rows),"step_id")),publication,policy);
        }
        public String candidateId(){production.admission();return candidateId;}public String writerStepId(){production.admission();return state.writerStep;}
        public ArtifactProtocol.SectionCandidate candidate(){production.admission();return state.candidate;}public JsonNode body(){production.admission();return state.body.deepCopy();}
        public String bodySha256(){return state.bodyObject.object.sha256();}public String candidateArtifactId(){return state.artifact.id;}public String candidateReceiptSha256(){return state.artifact.receiptHash;}
        public String bodyArtifactId(){return state.bodyObject.id;}public InterfaceProtocol.ObjectRef bodyObject(){return state.bodyObject.object;}
        public Map<String,ArtifactProtocol.ClaimRecord> claims(){return state.claims;}public Set<DatasetProtocol.EvidenceRef> evidenceRefs(){return state.evidence;}public Set<String> writerSessionIds(){return state.writerSessions;}
        public List<String> contentRoots(){return state.roots;}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return state.members;}
        public Map<String,InterfaceProtocol.ObjectRef> readObjects(){return Map.of(state.artifact.id,state.artifact.object,state.bodyObject.id,state.bodyObject.object);}
        public Map<String,JsonNode> expectedReadPayloads(){return Map.of(state.artifact.id,state.artifact.payload.deepCopy(),state.bodyObject.id,state.bodyObject.payload.deepCopy());}
    }
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}
    private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
