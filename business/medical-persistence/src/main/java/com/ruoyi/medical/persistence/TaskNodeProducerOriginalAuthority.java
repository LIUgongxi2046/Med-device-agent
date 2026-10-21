package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskMapper;
import java.io.IOException;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Independently approved, immutable, bounded TASK delegation. This does not change literal ROLE semantics.
 * New governance actions require real grants; this service never creates grants or approves its own author. */
public final class TaskNodeProducerOriginalAuthority {
    public static final String PURPOSE="TASK_DELEGATION";
    private final EnterpriseAccess access;
    private final JdbcTemplate db;
    private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;
    private final FormalCapabilityIdentityAuthority roles;
    private final TaskMapper tasks;
    private final PermissionMapper permissions;
    private final RunCreateBudget budgets;
    private final PermissionAuthority grants;
    private final PlatformMemberIdentityStatus identities;
    private final ResourceReadAuthority resources;
    private final TaskDelegationSchema schema;
    private final PermissionSchema json;
    private final int maximumCandidates;
    private final TaskNodeProducerProfile profile;

    public TaskNodeProducerOriginalAuthority(EnterpriseAccess access,DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskMapper tasks,
            PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,int maximumCandidates){
        this(access,data,publications,policies,roles,tasks,permissions,budgets,grants,identities,resources,schema,json,maximumCandidates,TaskNodeProducerProfile.RESEARCH);
    }
    public TaskNodeProducerOriginalAuthority(EnterpriseAccess access,DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskMapper tasks,
            PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,int maximumCandidates,TaskNodeProducerProfile profile){
        this.profile=Objects.requireNonNull(profile);
        this.access=access;this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.publications=publications;
        this.policies=policies;this.roles=roles;this.tasks=tasks;this.permissions=permissions;this.budgets=budgets;this.grants=grants;
        this.identities=identities;this.resources=resources;this.schema=schema;this.json=json;
        if(maximumCandidates<1||maximumCandidates>100)throw new IllegalArgumentException("EXPLICIT_DELEGATION_CANDIDATE_BOUND_REQUIRED");
        this.maximumCandidates=maximumCandidates;
    }

    /** Bounded discovery returns choices only, never selects or mints producer authority. */
    JsonNode choices(EnterpriseAccess.Admission a,String projectId,String classification,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        TaskNodeProducerService.requireAdmission(a);if(a.formalWorker()||a.controlWorker()||a.recoveryWorker())deny();
        resources.require(a,project(a,projectId),"task.delegation.read",PURPOSE);
        var nodes=policy.definition().bindings().stream().filter(n->profile.node().equals(n.nodeKind())).toList();if(nodes.size()!=1)deny();
        var role=nodes.get(0).roleVersion();String release=publication.manifest().manifest().releaseId();
        var candidates=db.query("SELECT r.rule_id FROM task_delegation_rule r JOIN task_delegation_state s ON s.tenant_id=r.tenant_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.release_id=? AND r.role_version=? AND r.policy_version=? AND s.status='APPROVED' ORDER BY r.rule_id LIMIT ? FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),projectId,release,role.version_id(),policy.versionId(),maximumCandidates+1);
        requireCandidateBound(candidates.size(),maximumCandidates);var result=json.object().putArray("items");
        for(String id:candidates){
            var row=read(a,id);var request=row.request();
            if(!projectId.equals(row.project())||!release.equals(text(request,"release_id"))||!policy.versionId().equals(text(request.required("execution_policy"),"version_id")))bad();
            if(!classification.equals(text(request,"classification"))||!publication.approvalSha256().equals(text(request,"publication_approval_sha256"))||!json.tree(role).equals(request.required("role_version")))continue;
            try {
                var verified=preparation(a,projectId,id,publication,policy);var item=json.object();
                for(String field:List.of("original_rule_id","original_rule_sha256","original_approval_sha256"))item.set(field,verified.required(field).deepCopy());
                for(String field:List.of("reason","role_version","execution_policy","budget_revision","valid_from","valid_until"))item.set(field,request.required(field).deepCopy());
                result.add(item);
            }catch(TenantAccessException failure){
                if(!Set.of(ErrorCode.ACCESS_DENIED,ErrorCode.SCOPE_STALE,ErrorCode.BUDGET_EXCEEDED,ErrorCode.REVISION_CONFLICT).contains(failure.code()))throw failure;
            }
        }
        publication.requireUnchanged();policy.requireUnchanged();return result;
    }
    int candidateLimit(){return maximumCandidates;}
    static void requireCandidateBound(int count,int maximum){if(maximum<1||maximum>100||count>maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}

    /** A proposal projection, never a producer proof or an approval. */
    JsonNode preparation(EnterpriseAccess.Admission a,String projectId,String ruleId,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        TaskNodeProducerService.requireAdmission(a);
        if(a.formalWorker()||a.controlWorker()||a.recoveryWorker())deny();
        resources.require(a,project(a,projectId),"task.delegation.read",PURPOSE);
        var row=read(a,ruleId);if(!projectId.equals(row.project()))deny();
        requireCurrentAuthor(a,row);validatePins(a,row.request(),publication,policy,true);
        var decision=approval(a,row);var now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
        if(now.isBefore(Instant.parse(text(row.request(),"valid_from")))||!now.isBefore(Instant.parse(text(row.request(),"valid_until"))))deny();
        var result=json.object();result.put("original_rule_id",row.id()).put("original_rule_sha256",row.hash())
            .put("original_approval_sha256",schema.hash(decision));
        result.set("request",row.request().deepCopy());return result;
    }

    /** Read-only exact original-rule validation before RN01. No synthetic run identifier. */
    public Verified require(EnterpriseAccess.Admission a,JsonNode producer,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,boolean requireAvailable)throws Exception {
        TaskNodeProducerService.requireAdmission(a);
        var authority=check(a,producer,publication,policy,requireAvailable);
        return new Verified(a,producer.deepCopy(),publication,policy,authority,schema.hash(authority),requireAvailable);
    }
    private JsonNode check(EnterpriseAccess.Admission a,JsonNode producer,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,boolean requireAvailable)throws Exception {
        var row=read(a,text(producer,"original_rule_id"));
        if(!row.hash().equals(text(producer,"original_rule_sha256"))||!row.project().equals(text(producer,"project_id")))bad();
        requireCurrentAuthor(a,row); validatePins(a,row.request(),publication,policy,requireAvailable);
        var decision=approval(a,row);
        if(!schema.hash(decision).equals(text(producer,"original_approval_sha256")))bad();
        for(String pin:List.of("release_id","publication_approval_sha256","role_version","execution_policy","budget_revision","currency","classification"))
            if(!producer.required(pin).equals(row.request().required(pin)))bad();
        var now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
        if(now.isBefore(Instant.parse(text(producer,"valid_from")))||!now.isBefore(Instant.parse(text(producer,"valid_until")))
                ||Instant.parse(text(producer,"valid_from")).isBefore(Instant.parse(text(row.request(),"valid_from")))
                ||Instant.parse(text(producer,"valid_until")).isAfter(Instant.parse(text(row.request(),"valid_until"))))deny();
        for(String field:List.of("maximum_tokens","maximum_calls"))
            if(WireValues.revision(text(producer,field)).compareTo(WireValues.revision(text(row.request(),field)))>0)deny();
        if(new java.math.BigDecimal(text(producer,"maximum_amount")).compareTo(new java.math.BigDecimal(text(row.request(),"maximum_amount")))>0)deny();
        var current=resources.require(a,project(a,row.project()),"task.delegation.read",PURPOSE);
        var result=json.object();result.set("rule",row.fixed().deepCopy());result.set("approval",decision.deepCopy());
        result.set("read_grant",TaskFieldAuthorityWitness.tree(current));
        publication.requireUnchanged();policy.requireUnchanged();return result;
    }
    public final class Verified {
        private final EnterpriseAccess.Admission a;private final JsonNode producer,authority;
        private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final String hash;private final boolean requireAvailable;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private Verified(EnterpriseAccess.Admission a,JsonNode producer,AgentPublicationAuthority.VerifiedPublication publication,
                AgentExecutionPolicyAuthority.VerifiedPolicy policy,JsonNode authority,String hash,boolean requireAvailable){this.requireAvailable=requireAvailable;this.a=a;this.producer=producer;this.publication=publication;this.policy=policy;this.authority=authority;this.hash=hash;}
        private void active(){TaskFieldTargetInputAuthority.requireLifetime(a,a,thread,scope,transaction);}
        public String sha256(){active();return hash;}
        public JsonNode authorizations(){active();return authority.deepCopy();}
        public void requireUnchanged()throws Exception{active();if(!hash.equals(schema.hash(check(a,producer,publication,policy,requireAvailable))))stale();}
    }
    /** Full original immutable journal and current original actors, for an existing private formal admission. */
    JsonNode requireCurrentIdentity(EnterpriseAccess.Admission a,JsonNode producer)throws Exception {
        TaskNodeProducerService.requireAdmission(a);if(!a.formalWorker()&&!a.controlWorker())deny();
        var row=read(a,text(producer,"original_rule_id"));requireCurrentAuthor(a,row);var decision=approval(a,row);
        if(!row.hash().equals(text(producer,"original_rule_sha256"))||!schema.hash(decision).equals(text(producer,"original_approval_sha256")))bad();
        for(String field:List.of("project_id","release_id","publication_approval_sha256","role_version","execution_policy","budget_revision"))
            if(!producer.required(field).equals(row.request().required(field)))bad();
        var now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
        if(now.isBefore(Instant.parse(text(row.request(),"valid_from")))||!now.isBefore(Instant.parse(text(row.request(),"valid_until"))))deny();
        var result=json.object();result.set("rule",row.fixed().deepCopy());result.set("approval",decision.deepCopy());return result;
    }
    private void requireCurrentAuthor(EnterpriseAccess.Admission a,Row row)throws Exception{
        var member=one(permissions.targetMember(row.author()));var binding=one(permissions.targetBinding(row.author()));var now=permissions.databaseTime();
        if(!"ACTIVE".equals(member.status())||!row.author().equals(member.userId())||!a.context().tenant_id().equals(member.tenantId())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!row.author().equals(binding.userId())||!Set.of("local","oidc").contains(binding.mode())||!text(row.fixed(),"identity_mode").equals(binding.mode())||"local".equals(binding.mode())&&!binding.userId().equals(binding.localUserId())||"oidc".equals(binding.mode())&&binding.localUserId()!=null||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject())))deny();
        var grant=one(permissions.currentGrant(text(row.fixed().required("grant"),"grant_id")));
        if(!grant.equals(TaskFieldAuthorityWitness.grant(row.fixed().required("grant"))))stale();
        grants.requireResource(project(a,row.project()),row.author(),binding.mode(),"task.delegation.author",PURPOSE,List.of(grant),now);
    }

    private void validatePins(EnterpriseAccess.Admission a,JsonNode request,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,boolean requireAvailable)throws Exception{
        schema.validate("Author",request);
        if(!text(request,"release_id").equals(publication.manifest().manifest().releaseId())
                ||!text(request,"publication_approval_sha256").equals(publication.approvalSha256())
                ||!text(request.required("execution_policy"),"version_id").equals(policy.versionId())
                ||!text(request.required("execution_policy"),"content_sha256").equals(policy.contentSha256()))bad();
        policy.requireConsumer(a,text(request,"project_id"),"RUN_CREATION");
        var role=json.basis(request.required("role_version"));
        if(policy.definition().bindings().stream().filter(n->profile.node().equals(n.nodeKind())&&n.roleVersion().equals(role)).count()!=1)deny();
        var actual=roles.require(a,role,"ROLE",text(request,"project_id"),"RUN_CREATION");actual.requireUnchanged();
        if(!policy.definition().allowedClassifications().contains(text(request,"classification")))deny();
        if(policy.definition().networkPolicy().externalRequestsAllowed()||!"FIXED_SOURCE_VERSIONS".equals(policy.definition().sourceSelection().mode()))deny();
        for(var operation:request.required("operations"))if(!"TASK_EXECUTION".equals(text(operation,"purpose"))&&!policy.businessPurpose().equals(text(operation,"purpose")))deny();
        for(var op:request.required("operations")){
            String selector=text(op,"selector"),action=text(op,"action"),purpose=text(op,"purpose");
            if("TASK".equals(selector)&&!"artifact.register".equals(action)&&policy.definition().bindings().stream().filter(n->n.roleVersion().equals(role)).flatMap(n->n.actions().stream()).noneMatch(x->"TASK".equals(x.targetSelector())&&"TASK".equals(x.resourceKind())&&action.equals(x.action())&&purpose.equals(x.purpose())))deny();
            if("REGISTERED_ARTIFACT".equals(selector))for(var kind:op.required("artifact_kinds"))if(policy.definition().bindings().stream().filter(n->n.roleVersion().equals(role)).flatMap(n->n.actions().stream()).noneMatch(x->"RUN_REGISTERED_ARTIFACT".equals(x.targetSelector())&&"ARTIFACT".equals(x.resourceKind())&&action.equals(x.action())&&purpose.equals(x.purpose())&&x.artifactKinds().contains(kind.asText())))deny();
        }
        for(var op:request.required("operations"))if("MODEL_CONFIG".equals(text(op,"selector"))){var model=json.basis(op.required("model_version"));if(policy.definition().bindings().stream().noneMatch(n->n.roleVersion().equals(role)&&model.equals(n.modelVersion())))deny();}
        var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",text(request,"project_id"));values.put("budgetRevision",text(request,"budget_revision"));
        var budget=requireAvailable?budgets.require(a,values,permissions.databaseTime(),text(request,"currency")):budgets.requireWithinBounds(a,values,permissions.databaseTime(),text(request,"currency"));
        var limits=budget.required("approval").required("limits");
        if(new java.math.BigDecimal(text(request,"maximum_amount")).compareTo(new java.math.BigDecimal(text(limits,"amount")))>0
                ||WireValues.revision(text(request,"maximum_tokens")).compareTo(WireValues.revision(text(limits,"tokens")))>0
                ||WireValues.revision(text(request,"maximum_calls")).compareTo(WireValues.revision(text(limits,"calls")))>0
                ||Instant.parse(text(request,"valid_until")).isAfter(Instant.parse(text(limits,"deadline_at"))))deny();
        publication.requireUnchanged();policy.requireUnchanged();
    }

    private JsonNode approval(EnterpriseAccess.Admission a,Row row)throws Exception{
        var state=state(a,row);if(!"APPROVED".equals(state.status())||state.decision()==null)deny();
        var result=one(db.query("SELECT decision_json,content_sha256,created_by,operation_id,audit_id,event_id,receipt_json,request_sha256,idempotency_key,schema_version,schema_manifest_sha256,classification,created_at FROM task_delegation_decision WHERE tenant_id=? AND rule_id=? AND decision_id=? FOR UPDATE",(r,n)->new Decision(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getTimestamp(13).toLocalDateTime()),a.context().tenant_id(),row.id(),state.decision()));
        var d=schema.read(result.content());requireDecisionMetadata(result,d,row);
        schema.validate("Decide",d.required("request"));
        if(!"DRAFT".equals(text(d,"previous_status"))||!"1".equals(text(d,"previous_revision"))||!"2".equals(text(d,"revision"))
            ||!"2".equals(state.revision())||!row.id().equals(text(d.required("request"),"rule_id"))
            ||!row.hash().equals(text(d.required("request"),"content_sha256"))||!"1".equals(text(d.required("request"),"expected_revision")))bad();
        if(!schema.hash(d).equals(result.hash())||!row.id().equals(text(d,"rule_id"))||!row.hash().equals(text(d,"rule_sha256"))||!state.revision().equals(text(d,"revision"))||!"APPROVED".equals(text(d,"status"))||!"APPROVE".equals(text(d.required("request"),"decision"))||!result.actor().equals(text(d,"created_by"))||result.actor().equals(row.author()))bad();
        verifyJournal(a,row.project(),result.actor(),result.operation(),result.audit(),result.event(),result.key(),"DECIDE",row.id(),result.requestHash(),schema.read(result.receipt()),d);
        var member=one(permissions.targetMember(result.actor()));var binding=one(permissions.targetBinding(result.actor()));var now=permissions.databaseTime();
        if(!"ACTIVE".equals(member.status())||!result.actor().equals(member.userId())||!a.context().tenant_id().equals(member.tenantId())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!result.actor().equals(binding.userId())||!text(d,"identity_mode").equals(binding.mode())||!Set.of("local","oidc").contains(binding.mode())||"local".equals(binding.mode())&&!binding.userId().equals(binding.localUserId())||"oidc".equals(binding.mode())&&binding.localUserId()!=null||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject())))deny();
        var grant=one(permissions.currentGrant(text(d.required("grant"),"grant_id")));
        if(!grant.equals(TaskFieldAuthorityWitness.grant(d.required("grant"))))stale();
        grants.requireResource(project(a,row.project()),result.actor(),binding.mode(),"task.delegation.approve",PURPOSE,List.of(grant),now);
        return d;
    }
    private record ModelOwner(String project){}
    private record Row(String id,String project,String author,String hash,JsonNode fixed){JsonNode request(){return fixed.required("request");}}
    private record State(String status,String revision,String decision){}
    private record Decision(String content,String hash,String actor,String operation,String audit,String event,String receipt,String requestHash,String key,String schemaVersion,String schemaManifest,String classification,LocalDateTime createdAt){}
    private State state(EnterpriseAccess.Admission a,Row row){return one(db.query("SELECT status,CAST(revision AS CHAR),latest_decision_id,created_by,classification,created_at,updated_at FROM task_delegation_state WHERE tenant_id=? AND rule_id=? FOR UPDATE",(r,n)->{
        requireAuditMetadata(r.getString(4),row.author(),r.getString(5),text(row.request(),"classification"),r.getTimestamp(6).toLocalDateTime(),Instant.parse(text(row.fixed(),"created_at")));
        if(r.getTimestamp(7).toLocalDateTime().isBefore(r.getTimestamp(6).toLocalDateTime()))bad();
        return new State(r.getString(1),r.getString(2),r.getString(3));
    },a.context().tenant_id(),row.id()));}
    private void requireDecisionMetadata(Decision decision,JsonNode body,Row row){
        if(!"1.0.0".equals(decision.schemaVersion())||!schema.manifest().equals(decision.schemaManifest())||!"1.0.0".equals(text(body,"schema_version")))bad();
        requireAuditMetadata(decision.actor(),text(body,"created_by"),decision.classification(),text(row.request(),"classification"),decision.createdAt(),Instant.parse(text(body,"created_at")));
    }
    static void requireAuditMetadata(String actor,String expectedActor,String classification,String expectedClassification,LocalDateTime created,Instant expectedCreated){
        if(!Objects.equals(actor,expectedActor)||!Objects.equals(classification,expectedClassification)||created==null||!created.toInstant(ZoneOffset.UTC).equals(expectedCreated))bad();
    }
    private Row read(EnterpriseAccess.Admission a,String id)throws Exception{
        var rows=db.query("SELECT project_id,created_by,content_sha256,fixed_json,operation_id,audit_id,event_id,receipt_json,request_sha256,idempotency_key,schema_manifest_sha256,release_id,role_version,policy_version,budget_revision,classification,created_at,schema_version FROM task_delegation_rule WHERE tenant_id=? AND rule_id=? FOR UPDATE",(r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getString(13),r.getString(14),r.getString(15),r.getString(16),r.getTimestamp(17).toLocalDateTime().toInstant(ZoneOffset.UTC).toString(),r.getString(18)),a.context().tenant_id(),id);
        var r=one(rows);var fixed=schema.read(r.get(3));schema.validate("Author",fixed.required("request"));
        if(!id.equals(text(fixed,"rule_id"))||!a.context().tenant_id().equals(text(fixed,"tenant_id"))||!r.get(0).equals(text(fixed.required("request"),"project_id"))||!r.get(1).equals(text(fixed,"created_by"))||!r.get(2).equals(schema.hash(fixed))||!r.get(10).equals(schema.manifest()))bad();
        var request=fixed.required("request");
        if(!r.get(11).equals(text(request,"release_id"))||!r.get(12).equals(text(request.required("role_version"),"version_id"))||!r.get(13).equals(text(request.required("execution_policy"),"version_id"))||!r.get(14).equals(text(request,"budget_revision"))||!r.get(15).equals(text(request,"classification"))||!Instant.parse(r.get(16)).equals(Instant.parse(text(fixed,"created_at")))||!"1.0.0".equals(r.get(17)))bad();
        // JsonMapper follows the actual snake_case immutable grant encoding.
        var originalGrant=com.fasterxml.jackson.databind.json.JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build().treeToValue(fixed.required("grant"),PermissionProtocol.GrantRow.class);
        grants.requireResource(project(a,r.get(0)),r.get(1),text(fixed,"identity_mode"),"task.delegation.author",PURPOSE,List.of(originalGrant),LocalDateTime.ofInstant(Instant.parse(text(fixed,"created_at")),ZoneOffset.UTC));
        var registered=one(permissions.basis(id));if(!r.get(2).equals(registered.sha256())||!r.get(0).equals(registered.ownerProjectId()))bad();
        var expectedParents=new TreeSet<>(List.of(text(request,"release_id"),text(request.required("role_version"),"version_id"),text(request.required("execution_policy"),"version_id"),text(request,"budget_revision")));
        var actualParents=db.query("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? AND relation_kind='TASK_DELEGATION_INPUT' AND actor_id=? ORDER BY upstream_version_id",(rs,n)->rs.getString(1),a.context().tenant_id(),id,r.get(1));
        if(!List.copyOf(expectedParents).equals(actualParents)||one(db.query("SELECT COUNT(*) FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=?",(rs,n)->rs.getInt(1),a.context().tenant_id(),id))!=expectedParents.size())bad();
        verifyJournal(a,r.get(0),r.get(1),r.get(4),r.get(5),r.get(6),r.get(9),"AUTHOR",id,r.get(8),schema.read(r.get(7)),fixed);
        return new Row(id,r.get(0),r.get(1),r.get(2),fixed);
    }
    private void verifyJournal(EnterpriseAccess.Admission a,String project,String actor,String operation,String audit,String event,String key,String kind,String rule,String requestHash,JsonNode receipt,JsonNode witness)throws Exception {
        schema.validate("Receipt",receipt);
        if(!requestHash.equals(schema.hash(witness.required("request")))||!requestHash.equals(text(receipt,"request_sha256"))
            ||!operation.equals(text(receipt,"operation_id"))||!rule.equals(text(receipt,"rule_id")))bad();
        var op=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? FOR UPDATE",a.context().tenant_id(),operation));
        var au=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",a.context().tenant_id(),audit));
        var ev=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? FOR UPDATE",a.context().tenant_id(),event));
        String action="task.delegation."+kind.toLowerCase(Locale.ROOT);
        String classification=one(db.query("SELECT classification FROM task_delegation_rule WHERE tenant_id=? AND rule_id=? FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),rule));
        if(!actor.equals(str(op,"subject_id"))||!actor.equals(str(op,"created_by"))||!("TASK-DELEGATION-"+kind).equals(str(op,"operation_name"))
            ||!key.equals(str(op,"idempotency_key"))||!rule.equals(str(op,"target_id"))||!requestHash.equals(str(op,"request_sha256"))
            ||!"COMPLETED".equals(str(op,"state"))||!"200".equals(str(op,"http_status"))||op.get("error_code")!=null
            ||!"1.0.0".equals(str(op,"schema_version"))||!schema.manifest().equals(str(op,"schema_manifest_sha256"))
            ||!classification.equals(str(op,"classification"))||!"1".equals(str(op,"revision"))
            ||!receipt.equals(schema.read(str(op,"response_ref")))||!time(op,"created_at").equals(time(op,"updated_at"))
            ||!time(op,"created_at").toInstant(ZoneOffset.UTC).equals(Instant.parse(text(witness,"created_at"))))bad();
        if(!project.equals(str(au,"project_id"))||!actor.equals(str(au,"subject_id"))||!actor.equals(str(au,"created_by"))
            ||!action.equals(str(au,"action"))||!"SUCCESS".equals(str(au,"result"))||au.get("reason_code")!=null
            ||!operation.equals(str(au,"request_id"))||!operation.equals(str(au,"trace_id"))
            ||!"1.0.0".equals(str(au,"schema_version"))||!schema.manifest().equals(str(au,"schema_manifest_sha256"))
            ||!classification.equals(str(au,"classification"))||!witness.equals(schema.read(str(au,"resource_ref")))
            ||time(au,"created_at").isBefore(time(op,"created_at")))bad();
        if(!rule.equals(str(ev,"aggregate_id"))||!text(receipt,"revision").equals(str(ev,"aggregate_revision"))
            ||!action.equals(str(ev,"event_type"))||!witness.equals(schema.read(str(ev,"payload")))
            ||time(ev,"occurred_at").isBefore(time(op,"created_at")))bad();
        if("AUTHOR".equals(kind)&&(!"DRAFT".equals(text(receipt,"status"))||!"1".equals(text(receipt,"revision"))
            ||!schema.hash(witness).equals(text(receipt,"content_sha256"))))bad();
        if("DECIDE".equals(kind)&&(!text(witness,"status").equals(text(receipt,"status"))
            ||!text(witness,"revision").equals(text(receipt,"revision"))||!text(witness,"rule_sha256").equals(text(receipt,"content_sha256"))))bad();
    }
    private static String str(Map<String,Object> row,String field){Object v=row.get(field);if(v==null)bad();return v.toString();}
    private static LocalDateTime time(Map<String,Object> row,String field){Object v=row.get(field);if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();if(v instanceof LocalDateTime t)return t;bad();return null;}
    static void requireDecision(String status,String decision,String author,String actor){
        if(!Set.of("APPROVE","REJECT","DISABLE").contains(decision))bad();
        if("DISABLE".equals(decision)?!"APPROVED".equals(status):!"DRAFT".equals(status))
            throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        if(!"DISABLE".equals(decision)&&Objects.equals(author,actor))deny();
    }
    private static PermissionProtocol.Scope project(EnterpriseAccess.Admission a,String project){return new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project);}
    private static String decisionAction(JsonNode r){return "DISABLE".equals(text(r,"decision"))?"task.delegation.disable":"task.delegation.approve";}
    private static String text(JsonNode n,String f){var v=n.required(f);if(!v.isTextual())bad();return v.textValue();}
    private static void context(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static String id(){return UUID.randomUUID().toString();}
    private static <T>T checked(ThrowingSupplier<T> fn){return fn.get();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
    private static void changed(int rows){if(rows!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
