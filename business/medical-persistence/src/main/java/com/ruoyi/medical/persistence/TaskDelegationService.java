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
public final class TaskDelegationService {
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

    public TaskDelegationService(EnterpriseAccess access,DataSource data,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskMapper tasks,
            PermissionMapper permissions,RunCreateBudget budgets,PermissionAuthority grants,PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources,TaskDelegationSchema schema,PermissionSchema json,int maximumCandidates){
        this.access=access;this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.publications=publications;
        this.policies=policies;this.roles=roles;this.tasks=tasks;this.permissions=permissions;this.budgets=budgets;this.grants=grants;
        this.identities=identities;this.resources=resources;this.schema=schema;this.json=json;
        if(maximumCandidates<1||maximumCandidates>100)throw new IllegalArgumentException("EXPLICIT_DELEGATION_CANDIDATE_BOUND_REQUIRED");
        this.maximumCandidates=maximumCandidates;
    }

    public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception{
        TaskDelegationSchema.key(key);var request=schema.request("Author",bytes);
        var original=access.execute(subject,a->checked(()->{
            context(a,context);resources.require(a,project(a,text(request,"project_id")),"task.delegation.author",PURPOSE);
            return recover(a,key,"AUTHOR",request);
        }));
        if(original!=null)return original;
        var prepared=publications.prepare(subject,context,text(request,"release_id"),"RUN_CREATION",false);
        return access.execute(subject,a->checked(()->{
            context(a,context);var scope=project(a,text(request,"project_id"));
            var grant=resources.require(a,scope,"task.delegation.author",PURPOSE);
            var replay=recover(a,key,"AUTHOR",request);if(replay!=null)return replay;
            var publication=publications.require(a,text(request,"release_id"),"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),scope.project_id(),"RUN_CREATION");
            validatePins(a,request,publication,policy);
            var now=permissions.databaseTime();if(!now.toInstant(ZoneOffset.UTC).isBefore(Instant.parse(text(request,"valid_until"))))deny();
            String rule=id(),operation=id(),audit=id(),event=id();
            var fixed=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).put("rule_id",rule)
                    .put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
            fixed.set("request",request);fixed.set("grant",AgentPublicationWitness.tree(grant));fixed.put("identity_mode",a.subject().binding().view().mode());
            String digest=schema.hash(fixed);var receipt=receipt(rule,operation,digest,schema.hash(request),"DRAFT","1");
            version(a,scope.project_id(),rule,digest);
            journal(a,scope.project_id(),operation,audit,event,key,"AUTHOR",rule,request,receipt,fixed,text(request,"classification"),now);
            changed(db.update("INSERT INTO task_delegation_rule(tenant_id,project_id,rule_id,release_id,role_version,policy_version,budget_revision,fixed_json,content_sha256,request_sha256,idempotency_key,operation_id,audit_id,event_id,receipt_json,schema_version,schema_manifest_sha256,classification,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",
                    a.context().tenant_id(),scope.project_id(),rule,text(request,"release_id"),text(request.required("role_version"),"version_id"),text(request.required("execution_policy"),"version_id"),text(request,"budget_revision"),schema.canonical(fixed),digest,schema.hash(request),key,operation,audit,event,schema.canonical(receipt),schema.manifest(),text(request,"classification"),a.actorId(),now));
            changed(db.update("INSERT INTO task_delegation_state(tenant_id,rule_id,status,revision,latest_decision_id,created_by,classification,created_at,updated_at) VALUES(?,?,'DRAFT',1,NULL,?,?,?,?)",a.context().tenant_id(),rule,a.actorId(),text(request,"classification"),now,now));
            for(String upstream:new TreeSet<>(List.of(text(request,"release_id"),text(request.required("role_version"),"version_id"),text(request.required("execution_policy"),"version_id"),text(request,"budget_revision"))))
                changed(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,created_at) VALUES(?,?,?,'TASK_DELEGATION_INPUT',?,?)",a.context().tenant_id(),rule,upstream,a.actorId(),now));
            read(a,rule);publication.requireUnchanged();policy.requireUnchanged();
            if(!grant.equals(resources.require(a,scope,"task.delegation.author",PURPOSE)))stale();advance(a);return receipt;
        }));
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception{
        TaskDelegationSchema.key(key);var request=schema.request("Decide",bytes);
        var original=access.execute(subject,a->checked(()->{context(a,context);var row=read(a,text(request,"rule_id"));
            resources.require(a,project(a,row.project()),decisionAction(request),PURPOSE);return recover(a,key,"DECIDE",request);}));
        if(original!=null)return original;
        var fixed=access.execute(subject,a->checked(()->{context(a,context);var row=read(a,text(request,"rule_id"));
            resources.require(a,project(a,row.project()),decisionAction(request),PURPOSE);return row;}));
        // Disabling/rejecting never needs to renew a disabled publication or model eligibility.
        var prepared="APPROVE".equals(text(request,"decision"))?publications.prepare(subject,context,text(fixed.request(),"release_id"),"RUN_CREATION",false):null;
        return access.execute(subject,a->checked(()->{
            context(a,context);var row=read(a,text(request,"rule_id"));var scope=project(a,row.project());
            var grant=resources.require(a,scope,decisionAction(request),PURPOSE);
            var replay=recover(a,key,"DECIDE",request);if(replay!=null)return replay;
            if(!row.hash().equals(text(request,"content_sha256")))bad();var state=state(a,row);
            if(!state.revision().equals(text(request,"expected_revision")))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            String decision=text(request,"decision");
            requireDecision(state.status(),decision,row.author(),a.actorId());
            AgentPublicationAuthority.VerifiedPublication publication=null;AgentExecutionPolicyAuthority.VerifiedPolicy policy=null;
            if("APPROVE".equals(decision)){
                publication=publications.require(a,text(row.request(),"release_id"),"RUN_CREATION",prepared);
                policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),row.project(),"RUN_CREATION");
                requireCurrentAuthor(a,row);validatePins(a,row.request(),publication,policy);
            }
            var now=permissions.databaseTime();String status=switch(decision){case "APPROVE"->"APPROVED";case "REJECT"->"REJECTED";default->"DISABLED";};
            String revision=RunCreateSchema.next(state.revision()),record=id(),operation=id(),audit=id(),event=id();
            var body=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).put("rule_id",row.id())
                    .put("rule_sha256",row.hash()).put("decision_id",record).put("created_by",a.actorId())
                    .put("created_at",now.toInstant(ZoneOffset.UTC).toString()).put("identity_mode",a.subject().binding().view().mode())
                    .put("previous_status",state.status()).put("previous_revision",state.revision()).put("status",status).put("revision",revision);
            body.set("request",request);body.set("grant",AgentPublicationWitness.tree(grant));
            var receipt=receipt(row.id(),operation,row.hash(),schema.hash(request),status,revision);
            journal(a,row.project(),operation,audit,event,key,"DECIDE",row.id(),request,receipt,body,text(row.request(),"classification"),now);
            changed(db.update("INSERT INTO task_delegation_decision(tenant_id,rule_id,decision_id,decision_json,content_sha256,request_sha256,idempotency_key,operation_id,audit_id,event_id,receipt_json,created_by,created_at,schema_version,schema_manifest_sha256,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?)",a.context().tenant_id(),row.id(),record,schema.canonical(body),schema.hash(body),schema.hash(request),key,operation,audit,event,schema.canonical(receipt),a.actorId(),now,schema.manifest(),text(row.request(),"classification")));
            changed(db.update("UPDATE task_delegation_state SET status=?,revision=?,latest_decision_id=?,updated_at=? WHERE tenant_id=? AND rule_id=? AND status=? AND revision=?",status,revision,record,now,a.context().tenant_id(),row.id(),state.status(),state.revision()));
            if(publication!=null){publication.requireUnchanged();policy.requireUnchanged();}
            if(!grant.equals(resources.require(a,scope,decisionAction(request),PURPOSE)))stale();advance(a);return receipt;
        }));
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String ruleId){
        WireValues.uuid(ruleId);return access.execute(subject,a->checked(()->{
            context(a,context);var row=read(a,ruleId);resources.require(a,project(a,row.project()),"task.delegation.read",PURPOSE);
            var state=state(a,row);var result=json.object().put("schema_version","1.0.0").put("rule_id",ruleId)
                    .put("content_sha256",row.hash()).put("status",state.status()).put("revision",state.revision());
            result.set("fixed",row.fixed().deepCopy());return result;
        }));
    }

    public Verified requireForTask(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,String task,String run,String budget)throws Exception{
        return requireForTask(a,publication,policy,role,task,run,budget,null,true);
    }
    /** Runtime selects the original immutable rule, never discovers a replacement approval. */
    Verified requireOriginalForTask(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,String task,String run,String budget,
            JsonNode original,boolean requireAvailable)throws Exception{
        String id=text(original.required("rule"),"rule_id");WireValues.uuid(id);
        var proof=requireForTask(a,publication,policy,role,task,run,budget,id,requireAvailable);
        if(!FormalTaskNodeAuthority.sameEvidence(original,proof.authorizations()))stale();
        return proof;
    }
    private Verified requireForTask(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,String task,String run,String budget,
            String originalRule,boolean requireAvailable)throws Exception{
        if(!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.getResourceMap().isEmpty())deny();
        var scope=TenantScope.requireCurrent();if(!scope.tenantId().equals(a.context().tenant_id())||!scope.subjectId().equals(a.actorId()))deny();
        WireValues.uuid(task);WireValues.uuid(run);WireValues.uuid(budget);
        var head=one(tasks.head(a.actorId(),task));String project=head.projectId();
        if(!a.context().tenant_id().equals(head.tenantId())||!head.agentReleaseId().equals(publication.manifest().manifest().releaseId()))bad();
        policy.requireConsumer(a,project,"RUN_CREATION");publication.requireUnchanged();
        var candidates=originalRule!=null?List.of(originalRule):db.query("SELECT r.rule_id FROM task_delegation_rule r JOIN task_delegation_state s ON s.tenant_id=r.tenant_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.release_id=? AND r.role_version=? AND r.policy_version=? AND r.budget_revision=? AND s.status='APPROVED' ORDER BY r.rule_id LIMIT ? FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),project,head.agentReleaseId(),role.version_id(),policy.versionId(),budget,maximumCandidates+1);
        if(candidates.size()>maximumCandidates)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var eligible=new ArrayList<Verified>();
        for(String id:candidates){var row=read(a,id);var request=row.request();
            if(!project.equals(row.project())||!head.agentReleaseId().equals(text(request,"release_id"))
                    ||!policy.versionId().equals(text(request.required("execution_policy"),"version_id"))
                    ||!budget.equals(text(request,"budget_revision")))bad();
            if(!text(request,"publication_approval_sha256").equals(publication.approvalSha256())||!json.tree(role).equals(request.required("role_version")))continue;
            requireCurrentAuthor(a,row);validatePins(a,request,publication,policy,requireAvailable);var approval=approval(a,row);var now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
            if(now.isBefore(Instant.parse(text(request,"valid_from")))||!now.isBefore(Instant.parse(text(request,"valid_until"))))continue;
            resources.require(a,project(a,project),"task.delegation.read",PURPOSE);
            eligible.add(new Verified(a,publication,policy,role,task,run,budget,row,approval,originalRule,requireAvailable));
        }
        if(eligible.isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if(eligible.size()!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        return eligible.get(0);
    }

    public final class Verified {
        private final EnterpriseAccess.Admission a;private final AgentPublicationAuthority.VerifiedPublication publication;
        private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final PermissionProtocol.Basis role;
        private final String task,run,budget,originalRule;private final boolean requireAvailable;private final Row row;private final JsonNode approval;
        private final Thread thread=Thread.currentThread();
        private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());
        private void active(){if(thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive()||transaction.isEmpty())deny();var actual=TransactionSynchronizationManager.getResourceMap();if(actual.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->actual.get(e.getKey())!=e.getValue()))deny();if(!scope.tenantId().equals(a.context().tenant_id())||!scope.subjectId().equals(a.actorId()))deny();}
        private Verified(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
                AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,String task,String run,String budget,Row row,JsonNode approval,String originalRule,boolean requireAvailable){
            this.a=a;this.publication=publication;this.policy=policy;this.role=role;this.task=task;this.run=run;this.budget=budget;this.row=row;this.approval=approval.deepCopy();this.originalRule=originalRule;this.requireAvailable=requireAvailable;}
        public String ruleId(){active();return row.id();}public String contentSha256(){active();return row.hash();}
        public String approvalSha256()throws IOException{active();return schema.hash(approval);}
        public JsonNode authorizations(){active();var result=json.object();result.set("rule",row.fixed().deepCopy());result.set("approval",approval.deepCopy());return result;}
        public List<RunTokenAuthoritySchema.Permission> deriveTaskPermissions()throws Exception{
            requireUnchanged();var result=new ArrayList<RunTokenAuthoritySchema.Permission>();
            for(var operation:row.request().required("operations"))if("TASK".equals(text(operation,"selector"))&&!"artifact.register".equals(text(operation,"action")))
                result.add(deriveTaskPermission(text(operation,"action"),text(operation,"purpose")));
            return List.copyOf(result);
        }
        public RunTokenAuthoritySchema.Permission deriveTaskPermission(String action,String purpose)throws Exception{
            requireUnchanged();if(!Set.of("task.read","task.run").contains(action))deny();requireOperation("TASK",action,purpose,null,null);
            var permission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(a.context().tenant_id(),row.project(),"TASK",task),action,purpose);
            resources.require(a,permission.scope(),action,purpose);return permission;
        }
        /** Bootstrap-only proof; never inserted into a run token or generic TASK permission list. */
        public ReplayRegistration requireReplayRegistration(String purpose)throws Exception{requireUnchanged();requireOperation("TASK","artifact.register",purpose,"REPLAY_MANIFEST",null);currentRegistrationGrant(a,row.project(),task,run,purpose);return new ReplayRegistration(this,purpose);}
        public RunTokenAuthoritySchema.Permission deriveModelPermission(ModelEligibilityAuthority.Verified model,String purpose)throws Exception{
            requireUnchanged();model.requireUnchanged();
            var ref=new PermissionProtocol.Basis(model.modelRevision(),model.contentSha256());
            requireOperation("MODEL_CONFIG","model.send",purpose,null,ref);
            var owner=one(db.query("SELECT project_id FROM model_config_revision WHERE tenant_id=? AND model_config_revision=? AND config_id=? AND content_sha256=? AND execution_mode='EXTERNAL' FOR UPDATE",
                    (r,n)->new ModelOwner(r.getString(1)),a.context().tenant_id(),model.modelRevision(),model.configId(),model.contentSha256()));
            if(owner.project()!=null&&!row.project().equals(owner.project()))deny();
            var result=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(a.context().tenant_id(),owner.project(),"CONFIGURATION",model.configId()),"model.send",purpose);
            resources.require(a,result.scope(),result.action(),result.purpose());model.requireUnchanged();return result;
        }
        public void requireBudget(String currency,String amount,String tokens,String calls)throws Exception{
            requireUnchanged();var r=row.request();
            if(!text(r,"currency").equals(currency)||new java.math.BigDecimal(amount).signum()<0||new java.math.BigDecimal(amount).compareTo(new java.math.BigDecimal(text(r,"maximum_amount")))>0
                    ||WireValues.revision(tokens).compareTo(WireValues.revision(text(r,"maximum_tokens")))>0||WireValues.revision(calls).compareTo(WireValues.revision(text(r,"maximum_calls")))>0)deny();
        }
        public void requireUnchanged()throws Exception{
            active();var fresh=requireForTask(a,publication,policy,role,task,run,budget,originalRule,requireAvailable);
            if(!row.hash().equals(fresh.row.hash())||!approval.equals(fresh.approval)||!row.id().equals(fresh.row.id()))stale();
        }
        private void requireOperation(String selector,String action,String purpose,String kind,PermissionProtocol.Basis model){
            var matches=new ArrayList<JsonNode>();for(var op:row.request().required("operations"))if(selector.equals(text(op,"selector"))&&action.equals(text(op,"action"))&&purpose.equals(text(op,"purpose"))
                    &&(kind==null||java.util.stream.StreamSupport.stream(op.required("artifact_kinds").spliterator(),false).anyMatch(v->kind.equals(v.asText())))
                    &&(model==null||json.tree(model).equals(op.required("model_version"))))matches.add(op);
            if(matches.size()!=1)deny();
        }
    }

    public final class ReplayRegistration {
        private final Verified owner;private final String purpose;
        private ReplayRegistration(Verified owner,String purpose){this.owner=owner;this.purpose=purpose;}
        public void require(EnterpriseAccess.Admission admission,String taskId,String runId,String artifactKind)throws Exception{
            owner.requireUnchanged();if(admission!=owner.a||!owner.task.equals(taskId)||!owner.run.equals(runId)||!"REPLAY_MANIFEST".equals(artifactKind))deny();
            owner.requireOperation("TASK","artifact.register",purpose,"REPLAY_MANIFEST",null);
            currentRegistrationGrant(admission,owner.row.project(),taskId,runId,purpose);
        }
    }
    /** Only the run-owned START_RUN bootstrap can use the replay-only registration gate.
     * This does not expand the generic control proof or ResourceReadAuthority. */
    private PermissionProtocol.GrantRow currentRegistrationGrant(EnterpriseAccess.Admission admission,String projectId,String taskId,String runId,String purpose)throws Exception{
        var scope=new PermissionProtocol.Scope(admission.context().tenant_id(),projectId,"TASK",taskId);
        if(!admission.controlWorker())return resources.require(admission,scope,"artifact.register",purpose);
        admission.controlProof().requireBootstrap(taskId,runId);
        schemaScope(scope);
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants");
        TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants",Map.of("userId",admission.actorId()));
        var owners=permissions.resourceOwner("TASK",taskId);
        if(owners.size()!=1||!taskId.equals(owners.get(0).id())||!projectId.equals(owners.get(0).projectId()))deny();
        return grants.requireResource(scope,admission.actorId(),admission.bindingView().mode(),"artifact.register",purpose,permissions.resourceGrants(admission.actorId(),"artifact.register"),permissions.databaseTime());
    }
    private void schemaScope(PermissionProtocol.Scope scope)throws IOException{
        json.scope(json.tree(scope));if(!TenantScope.requireCurrent().tenantId().equals(scope.tenant_id()))deny();
    }
    private void requireCurrentAuthor(EnterpriseAccess.Admission a,Row row)throws Exception{
        var member=one(permissions.targetMember(row.author()));var binding=one(permissions.targetBinding(row.author()));var now=permissions.databaseTime();
        if(!"ACTIVE".equals(member.status())||!row.author().equals(member.userId())||!a.context().tenant_id().equals(member.tenantId())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!row.author().equals(binding.userId())||!Set.of("local","oidc").contains(binding.mode())||!text(row.fixed(),"identity_mode").equals(binding.mode())||"local".equals(binding.mode())&&!binding.userId().equals(binding.localUserId())||"oidc".equals(binding.mode())&&binding.localUserId()!=null||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject())))deny();
        var grant=one(permissions.currentGrant(text(row.fixed().required("grant"),"grant_id")));
        if(!AgentPublicationWitness.tree(grant).equals(row.fixed().required("grant")))stale();
        grants.requireResource(project(a,row.project()),row.author(),binding.mode(),"task.delegation.author",PURPOSE,List.of(grant),now);
    }

    private void validatePins(EnterpriseAccess.Admission a,JsonNode request,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception{
        validatePins(a,request,publication,policy,true);
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
        if(policy.definition().bindings().stream().noneMatch(n->n.roleVersion().equals(role)))deny();
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
        var budget=requireAvailable?budgets.require(a,values,permissions.databaseTime(),text(request,"currency"))
            :budgets.requireWithinBounds(a,values,permissions.databaseTime(),text(request,"currency"));
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
        if(!schema.hash(d).equals(result.hash())||!row.id().equals(text(d,"rule_id"))||!row.hash().equals(text(d,"rule_sha256"))||!state.revision().equals(text(d,"revision"))||!"APPROVED".equals(text(d,"status"))||!"APPROVE".equals(text(d.required("request"),"decision"))||!result.actor().equals(text(d,"created_by"))||result.actor().equals(row.author()))bad();
        verifyJournal(a,row.project(),result.actor(),result.operation(),result.audit(),result.event(),result.key(),"DECIDE",row.id(),result.requestHash(),schema.read(result.receipt()),d);
        var member=one(permissions.targetMember(result.actor()));var binding=one(permissions.targetBinding(result.actor()));var now=permissions.databaseTime();
        if(!"ACTIVE".equals(member.status())||!result.actor().equals(member.userId())||!a.context().tenant_id().equals(member.tenantId())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!result.actor().equals(binding.userId())||!Set.of("local","oidc").contains(binding.mode())||"local".equals(binding.mode())&&!binding.userId().equals(binding.localUserId())||"oidc".equals(binding.mode())&&binding.localUserId()!=null||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject())))deny();
        var grant=one(permissions.currentGrant(text(d.required("grant"),"grant_id")));
        if(!AgentPublicationWitness.tree(grant).equals(d.required("grant")))stale();
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
    private JsonNode recover(EnterpriseAccess.Admission a,String key,String kind,JsonNode request)throws Exception{
        var rows=db.query("SELECT request_sha256,response_ref,target_id,state,CAST(http_status AS CHAR),schema_manifest_sha256 FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? FOR UPDATE",(r,n)->Arrays.asList(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),a.context().tenant_id(),a.actorId(),"TASK-DELEGATION-"+kind,key);
        if(rows.size()>1)bad();if(rows.isEmpty())return null;var row=rows.get(0);if(!"COMPLETED".equals(row.get(3))||!"200".equals(row.get(4))||!schema.manifest().equals(row.get(5)))bad();if(!schema.hash(request).equals(row.get(0)))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        var receipt=schema.validate("Receipt",schema.read(row.get(1)));if(!row.get(2).equals(text(receipt,"rule_id")))bad();var rule=read(a,row.get(2));
        if("DECIDE".equals(kind)){
            var original=one(db.query("SELECT decision_json,content_sha256,created_by,operation_id,audit_id,event_id,receipt_json,request_sha256,idempotency_key,schema_version,schema_manifest_sha256,classification,created_at FROM task_delegation_decision WHERE tenant_id=? AND rule_id=? AND created_by=? AND idempotency_key=? FOR UPDATE",
                    (r,n)->new Decision(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getTimestamp(13).toLocalDateTime()),a.context().tenant_id(),rule.id(),a.actorId(),key));
            var body=schema.read(original.content());requireDecisionMetadata(original,body,rule);if(!original.hash().equals(schema.hash(body))||!request.equals(body.required("request"))||!receipt.equals(schema.read(original.receipt())))bad();
            verifyJournal(a,rule.project(),a.actorId(),original.operation(),original.audit(),original.event(),key,kind,rule.id(),original.requestHash(),receipt,body);
        }
        return receipt;
    }
    private void journal(EnterpriseAccess.Admission a,String project,String operation,String audit,String event,String key,String kind,String rule,JsonNode request,JsonNode receipt,JsonNode witness,String classification,LocalDateTime now)throws Exception{
        String raw=schema.canonical(receipt),action="task.delegation."+kind.toLowerCase(Locale.ROOT),name="TASK-DELEGATION-"+kind;
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,error_code,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',200,?,?,NULL,'1.0.0',?,?,?,?,1,?)",a.context().tenant_id(),operation,a.actorId(),name,key,schema.hash(request),raw,rule,schema.manifest(),a.actorId(),now,classification,now));
        changed(permissions.saveAudit(a.actorId(),project,audit,action,schema.canonical(witness),operation,schema.manifest(),classification));
        changed(permissions.saveEvent(a.actorId(),event,rule,text(receipt,"revision"),action,schema.canonical(witness)));
    }
    private void verifyJournal(EnterpriseAccess.Admission a,String project,String actor,String operation,String audit,String event,String key,String kind,String rule,String requestHash,JsonNode receipt,JsonNode witness)throws Exception{
        schema.validate("Receipt",receipt);if(!requestHash.equals(schema.hash(witness.required("request")))||!operation.equals(text(receipt,"operation_id"))||!rule.equals(text(receipt,"rule_id")))bad();
        String saved=one(db.query("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? AND request_sha256=? AND target_id=? AND state='COMPLETED' AND http_status=200 AND schema_manifest_sha256=?",(r,n)->r.getString(1),a.context().tenant_id(),operation,actor,"TASK-DELEGATION-"+kind,key,requestHash,rule,schema.manifest()));
        if(!receipt.equals(schema.read(saved))||!requestHash.equals(text(receipt,"request_sha256")))bad();String action="task.delegation."+kind.toLowerCase(Locale.ROOT);
        var auditRaw=one(db.query("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND action=? AND request_id=? AND result='SUCCESS'",(r,n)->r.getString(1),a.context().tenant_id(),project,audit,actor,action,operation));
        var eventRaw=one(db.query("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type=?",(r,n)->r.getString(1),a.context().tenant_id(),event,rule,action));
        if(!witness.equals(schema.read(auditRaw))||!witness.equals(schema.read(eventRaw)))bad();
    }
    private void advance(EnterpriseAccess.Admission a){
        RunCreateSchema.next(a.watermarks().governanceEpoch().toString());
        changed(db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=? AND auth_epoch=? AND governance_epoch=?",a.context().tenant_id(),a.watermarks().authEpoch(),a.watermarks().governanceEpoch()));
    }
    private void version(EnterpriseAccess.Admission a,String project,String id,String hash){changed(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,'TASK_DELEGATION_RULE',?)",a.context().tenant_id(),project,id,hash));}
    private JsonNode receipt(String rule,String operation,String hash,String request,String status,String revision){return schema.validate("Receipt",json.object().put("schema_version","1.0.0").put("rule_id",rule).put("operation_id",operation).put("content_sha256",hash).put("request_sha256",request).put("status",status).put("revision",revision));}
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
