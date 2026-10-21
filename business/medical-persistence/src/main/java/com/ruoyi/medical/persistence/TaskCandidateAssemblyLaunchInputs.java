package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskCandidateAssemblyCrossFileProtocol.*;

/** New-run-only two-phase preflight. Exact independently-approved external document
 * selectors become additional immutable replay dependencies before RN01 is created.
 * An existing run is never expanded. No physical reads occur in a SQL transaction. */
public final class TaskCandidateAssemblyLaunchInputs {
    private final EnterpriseAccess access;private final JdbcTemplate db;private final TaskSourceVerifierSchema json;
    private final AgentPublicationAuthority publications;private final AgentExecutionPolicyAuthority policies;
    private final IdentityDirectoryPolicy identity;private final TaskNodeProducerService producers;
    private final TemplateVersionStore templates;private final DocumentReadService documents;private final DependencyLineage lineage;
    private final TemplateDependencyPolicy limits;
    private final JsonMapper parser=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).build();
    public TaskCandidateAssemblyLaunchInputs(EnterpriseAccess access,DataSource data,TaskSourceVerifierSchema json,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,IdentityDirectoryPolicy identity,TaskNodeProducerService producers,TemplateVersionStore templates,
            DocumentReadService documents,DependencyLineage lineage,TemplateDependencyPolicy limits){this.access=access;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.publications=publications;this.policies=policies;this.identity=identity;this.producers=producers;this.templates=templates;this.documents=documents;this.lineage=lineage;this.limits=limits;}
    public Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request)throws Exception {
        RunCreateSchema.requireReady(task.head(),request);return prepareBound(subject,context,task,request);
    }
    Prepared prepareContinuation(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request,RunContinuationService.Authority continuation)throws Exception {
        continuation.requirePrepared(task,request);return prepareBound(subject,context,task,request);
    }
    private Prepared prepareBound(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())deny();
        var publication=publications.prepare(subject,context,task.head().agentReleaseId(),"RUN_CREATION",false);
        var selected=access.execute(subject,a->ThrowingSupplier.of(()->{
            var pub=publications.require(a,task.head().agentReleaseId(),"RUN_CREATION",publication);var policy=policies.requireByVersion(a,pub.executionPolicyId(),pub.executionPolicySha256(),task.head().projectId(),identity.classification(),"RUN_CREATION");return select(a,task,pub,policy);
        }).get());
        var reads=new LinkedHashMap<String,DocumentReadService.FixedRead>();
        for(var document:selected.input.documents()){
            var fixed=documents.readFixed(subject,context,document.documentId(),document.documentRevision().version_id());
            validate(document,fixed,task.head().tenantId(),task.head().projectId());reads.put(document.documentRevision().version_id(),fixed);
        }
        var result=new Prepared(subject.user().user_id(),context,task,request,selected,reads);
        access.execute(subject,a->ThrowingSupplier.of(()->{var pub=publications.require(a,task.head().agentReleaseId(),"RUN_CREATION",publication);var policy=policies.requireByVersion(a,pub.executionPolicyId(),pub.executionPolicySha256(),task.head().projectId(),identity.classification(),"RUN_CREATION");require(a,task,pub,policy,result).requireUnchanged();return null;}).get());return result;
    }
    public Verified require(EnterpriseAccess.Admission a,TaskService.VerifiedTask task,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,Prepared prepared)throws Exception {
        if(prepared==null||!a.actorId().equals(prepared.actor)||!task.head().equals(prepared.task.head())||!task.input().equals(prepared.task.input()))deny();
        var current=select(a,task,publication,policy);same(prepared.selected.pins,current.pins);
        var versions=new LinkedHashMap<String,PermissionProtocol.Basis>();var witnesses=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("node_kind","N06").put("task_revision",task.head().taskRevision()).put("status",current.required?"FIXED_INPUTS_VERIFIED":"NOT_REQUIRED");witnesses.set("selection",current.pins);var physical=witnesses.putArray("physical_reads");
        if(current.producer!=null)add(versions,new PermissionProtocol.Basis(current.producer.capabilityId(),current.producer.capabilityHash()));
        var snapshot=one(db.queryForList("SELECT s.manifest,s.manifest_sha256,v.content_sha256 FROM scope_snapshot s JOIN version_registry v ON v.tenant_id=s.tenant_id AND v.version_id=s.snapshot_id WHERE s.tenant_id=? AND s.project_id=? AND s.snapshot_id=? LIMIT 2 FOR UPDATE",task.head().tenantId(),task.head().projectId(),task.head().snapshotId()));var scope=json.read(str(snapshot,"manifest"));same(json.hash(scope),str(snapshot,"manifest_sha256"));same(str(snapshot,"manifest_sha256"),str(snapshot,"content_sha256"));
        var sources=new HashMap<String,String>();for(var source:scope.required("members"))if(sources.putIfAbsent(text(source,"source_version"),text(source,"rights_revision"))!=null)bad();
        for(var selected:current.input.documents()){
            var read=prepared.reads.get(selected.documentRevision().version_id());if(read==null)deny();documents.requireCurrent(a,read);validate(selected,read,task.head().tenantId(),task.head().projectId());
            add(versions,selected.documentRevision());add(versions,selected.bodyArtifact());add(versions,selected.templateVersion());selected.factVersions().forEach(f->add(versions,f));
            // The document reader checks its own source rights. In addition, every content
            // ancestor must belong to this NEW task's fixed scope, not just the old document.
            for(var member:read.dependencies().members()){
                var graph=lineage.inspect(member.upstreamVersionId(),limits.limits());boolean allowed=false;
                for(var source:sources.entrySet())if(member.rightsRevision().equals(source.getValue())&&graph.versions().containsKey(source.getKey()))allowed=true;
                if(!allowed)throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            }
            var pin=physical.addObject().put("document_id",selected.documentId()).put("document_revision",read.header().documentRevision()).put("body_artifact_id",read.header().bodyArtifactId()).put("body_sha256",read.header().bodySha256()).put("read_digest",read.digest());pin.set("watermarks",json.tree(read.watermarks()));pin.set("dependencies",json.tree(read.dependencies()));
        }
        for(var binding:current.input.rules()){add(versions,binding.ruleRevision());for(var selector:binding.inputs())if(selector.factVersion()!=null)add(versions,selector.factVersion());}
        // Exact root registry bytes are checked now; RuntimeReplayAssembler additionally
        // reads and saves their complete real lineage in the new replay.
        for(var version:versions.values()){var graph=lineage.inspect(version.version_id(),limits.limits());var actual=graph.versions().get(version.version_id());if(actual==null||!version.content_sha256().equals(actual.contentSha256())||actual.projectId()!=null&&!task.head().projectId().equals(actual.projectId()))bad();}
        publication.requireUnchanged();policy.requireUnchanged();return new Verified(a,task,publication,policy,prepared,witnesses,List.copyOf(versions.values()));
    }
    private Selected select(EnterpriseAccess.Admission a,TaskService.VerifiedTask task,AgentPublicationAuthority.VerifiedPublication pub,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        var template=templates.applicationTarget(a,task.head().templateVersion(),"RUN_CREATION");var cross=template.content().cross_document_rules();
        var role=one(policy.definition().bindings().stream().filter(b->"N06".equals(b.nodeKind())).toList()).roleVersion();
        var rows=db.queryForList("SELECT r.rule_id,r.content_sha256 FROM task_candidate_assembly_producer_rule r JOIN task_candidate_assembly_producer_state s ON s.tenant_id=r.tenant_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.plan_revision=? AND s.status='APPROVED' LIMIT 2 FOR UPDATE",task.head().tenantId(),task.head().projectId(),task.head().taskId(),task.head().planRevision());
        if(rows.isEmpty()&&cross.isEmpty())return new Selected(false,new Input("1.0.0",List.of(),List.of()),null,json.tree(Map.of("not_required",true,"template_version",template.template_version(),"template_sha256",template.content_sha256())));
        var row=one(rows);
        var producer=producers.requireCurrent(a,pub,policy,role,str(row,"rule_id"),str(row,"content_sha256"),task.head().taskId(),null,null);
        if(!producer.manifest().has("cross_file_inputs")&& !cross.isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var input=producer.manifest().has("cross_file_inputs")?parser.treeToValue(producer.manifest().required("cross_file_inputs"),Input.class):new Input("1.0.0",List.of(),List.of());var byId=new HashMap<String,Rule>();for(var r:input.rules())if(byId.putIfAbsent(r.ruleId(),r)!=null)bad();
        var types=new HashSet<String>();for(var rule:cross){var actual=byId.get(rule.rule_id());if(actual==null)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);same(rule.rule_revision(),actual.ruleRevision());types.addAll(rule.related_document_types());}
        same(cross.size(),byId.size());var ids=new HashSet<String>();var revisions=new HashSet<String>();var actualTypes=new HashSet<String>();
        for(var d:input.documents()){
            if(!ids.add(d.documentId())||!revisions.add(d.documentRevision().version_id()))bad();actualTypes.add(d.documentType());
            var type=one(db.queryForList("SELECT t.document_type,v.content_sha256 FROM template_version t JOIN version_registry v ON v.tenant_id=t.tenant_id AND v.version_id=t.template_version WHERE t.tenant_id=? AND t.template_version=? LIMIT 2 FOR UPDATE",task.head().tenantId(),d.templateVersion().version_id()));same(d.documentType(),str(type,"document_type"));same(d.templateVersion().content_sha256(),str(type,"content_sha256"));
        }
        if(!actualTypes.containsAll(types))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var heads=db.queryForList("SELECT h.document_id,h.current_document_revision,t.document_type FROM document_head h JOIN document_revision_meta m ON m.tenant_id=h.tenant_id AND m.project_id=h.project_id AND m.document_id=h.document_id AND m.document_revision=h.current_document_revision JOIN template_version t ON t.tenant_id=m.tenant_id AND t.template_version=m.template_version WHERE h.tenant_id=? AND h.project_id=? ORDER BY h.document_id LIMIT 1025 FOR UPDATE",task.head().tenantId(),task.head().projectId());if(heads.size()>1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        for(var h:heads)if(types.contains(str(h,"document_type"))){var d=one(input.documents().stream().filter(v->v.documentId().equals(str(h,"document_id"))).toList());same(d.documentRevision().version_id(),str(h,"current_document_revision"));}
        for(var r:input.rules()){var keys=new HashSet<String>();for(var selector:r.inputs())if(!keys.add(selector.inputKey())||"DOCUMENT".equals(selector.sourceKind())&&!revisions.contains(selector.documentRevision()))bad();}
        var pins=JsonNodeFactory.instance.objectNode().put("producer_rule_id",producer.capabilityId()).put("producer_sha256",producer.capabilityHash()).put("producer_decision_id",producer.decisionId()).put("producer_decision_sha256",producer.decisionHash()).put("authorizations_sha256",producer.fingerprint());pins.set("cross_file_inputs",json.tree(input));pins.put("current_related_documents_sha256",json.hash(heads));return new Selected(true,input,producer,pins);
    }
    private record Selected(boolean required,Input input,TaskNodeProducerService.Checked producer,JsonNode pins){}
    public final class Prepared {
        private final String actor,context;private final TaskService.VerifiedTask task;private final RunCreateSchema.Create request;private final Selected selected;private final Map<String,DocumentReadService.FixedRead> reads;
        private Prepared(String actor,String context,TaskService.VerifiedTask task,RunCreateSchema.Create request,Selected selected,Map<String,DocumentReadService.FixedRead> reads){this.actor=actor;this.context=context;this.task=task;this.request=request;this.selected=selected;this.reads=Map.copyOf(reads);}
    }
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final TaskService.VerifiedTask task;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final Prepared prepared;private final JsonNode witnesses;private final List<PermissionProtocol.Basis> versions;
        private Verified(EnterpriseAccess.Admission a,TaskService.VerifiedTask t,AgentPublicationAuthority.VerifiedPublication pub,AgentExecutionPolicyAuthority.VerifiedPolicy policy,Prepared p,JsonNode w,List<PermissionProtocol.Basis> v){admission=a;task=t;publication=pub;this.policy=policy;prepared=p;witnesses=w.deepCopy();versions=List.copyOf(v);}
        public JsonNode authorizations(){return witnesses.deepCopy();}public List<PermissionProtocol.Basis> dependencyVersions(){return versions;}
        public void requireUnchanged()throws Exception{var fresh=require(admission,task,publication,policy,prepared);same(witnesses,fresh.witnesses);same(versions,fresh.versions);}
    }
    private static void validate(Document d,DocumentReadService.FixedRead r,String tenant,String project)throws Exception {var h=r.header();same(tenant,h.tenantId());same(project,h.projectId());same(d.documentId(),h.documentId());same(d.documentRevision().version_id(),h.documentRevision());same(h.documentRevision(),h.currentDocumentRevision());same(d.documentRevision().content_sha256(),h.contentSha256());same(d.bodyArtifact().version_id(),h.bodyArtifactId());same(d.bodyArtifact().content_sha256(),h.bodySha256());same(d.templateVersion().version_id(),h.templateVersion());same(h.bodySha256(),WireValues.sha256(r.bodyBytes()));same(new HashSet<>(d.factVersions().stream().map(PermissionProtocol.Basis::version_id).toList()),new HashSet<>(r.body().factUsages().stream().map(InterfaceProtocol.FactUsage::factVersionId).toList()));}
    private static void add(Map<String,PermissionProtocol.Basis> map,PermissionProtocol.Basis basis){var old=map.putIfAbsent(basis.version_id(),basis);if(old!=null&&!old.equals(basis))bad();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return rows.get(0);}private static String str(Map<String,Object> row,String k){return Objects.requireNonNull(row.get(k)).toString();}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).asText();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
