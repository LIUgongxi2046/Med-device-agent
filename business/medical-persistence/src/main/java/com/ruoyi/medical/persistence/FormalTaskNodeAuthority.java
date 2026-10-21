package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** TASK-specific role authority. A DRAFT author identity is not publication authority:
 * the actual published release, approved policy, original delegation, registered replay
 * and registered immutable node input must all agree. No external I/O runs in SQL. */
public final class FormalTaskNodeAuthority {
    private final JdbcTemplate db;
    private final FormalRunControlAccess controls;
    private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;
    private final FormalCapabilityIdentityAuthority roles;
    private final TaskDelegationService delegations;
    private final ModelEligibilityAuthority models;
    private final TaskNodeInputAuthority inputs;
    private final RunCreateBudget budgets;
    private final PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final VersionResourceMapper owners;
    private final RunTokenAuthoritySchema schema;
    private final PermissionSchema json;
    private final org.springframework.beans.factory.ObjectProvider<TaskStrictFieldInputAuthority> strictInputs;
    private final org.springframework.beans.factory.ObjectProvider<TaskSectionWriterInputAuthority> writerInputs;
    private org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierInputAuthority> verifierInputs;
    private final TaskNodeProductionAuthority strictProduction;
    private final RuntimeRetentionAuthority strictRetention;

    public FormalTaskNodeAuthority(DataSource data,FormalRunControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskDelegationService delegations,ModelEligibilityAuthority models,
            TaskNodeInputAuthority inputs,RunCreateBudget budgets,PermissionMapper permissions,PermissionAuthority grants,
            VersionResourceMapper owners,RunTokenAuthoritySchema schema,PermissionSchema json) {
        this(data,controls,publications,policies,roles,delegations,models,inputs,budgets,permissions,grants,owners,schema,json,null,null,null,null);
    }
    public FormalTaskNodeAuthority(DataSource data,FormalRunControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskDelegationService delegations,ModelEligibilityAuthority models,
            TaskNodeInputAuthority inputs,RunCreateBudget budgets,PermissionMapper permissions,PermissionAuthority grants,
            VersionResourceMapper owners,RunTokenAuthoritySchema schema,PermissionSchema json,
            org.springframework.beans.factory.ObjectProvider<TaskStrictFieldInputAuthority> strictInputs,
            TaskNodeProductionAuthority strictProduction,RuntimeRetentionAuthority strictRetention,org.springframework.beans.factory.ObjectProvider<TaskSectionWriterInputAuthority> writerInputs) {
        this.writerInputs=writerInputs;this.strictInputs=strictInputs;this.strictProduction=strictProduction;this.strictRetention=strictRetention;
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.controls=controls;this.publications=publications;
        this.policies=policies;this.roles=roles;this.delegations=delegations;this.models=models;this.inputs=inputs;this.budgets=budgets;
        this.permissions=permissions;this.grants=grants;this.owners=owners;this.schema=schema;this.json=json;
    }
    public FormalTaskNodeAuthority(DataSource data,FormalRunControlAccess controls,AgentPublicationAuthority publications,
            AgentExecutionPolicyAuthority policies,FormalCapabilityIdentityAuthority roles,TaskDelegationService delegations,ModelEligibilityAuthority models,
            TaskNodeInputAuthority inputs,RunCreateBudget budgets,PermissionMapper permissions,PermissionAuthority grants,
            VersionResourceMapper owners,RunTokenAuthoritySchema schema,PermissionSchema json,
            org.springframework.beans.factory.ObjectProvider<TaskStrictFieldInputAuthority> strictInputs,
            TaskNodeProductionAuthority strictProduction,RuntimeRetentionAuthority strictRetention,org.springframework.beans.factory.ObjectProvider<TaskSectionWriterInputAuthority> writerInputs,org.springframework.beans.factory.ObjectProvider<TaskSourceVerifierInputAuthority> verifierInputs) {this(data,controls,publications,policies,roles,delegations,models,inputs,budgets,permissions,grants,owners,schema,json,strictInputs,strictProduction,strictRetention,writerInputs);this.verifierInputs=verifierInputs;}
    boolean supports(String tenant,String actor,String run) {
        WireValues.uuid(tenant);WireValues.uuid(actor);WireValues.uuid(run);
        return !db.query("SELECT run_id FROM task_run WHERE tenant_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2",
            (r,n)->r.getString(1),tenant,run,actor).isEmpty();
    }
    <T>T execute(HttpServletRequest http,String tenant,String actor,String run,String step,BigInteger expectedAuth,boolean requireAvailable,Operation<T> operation)throws Exception {
        if(TransactionSynchronizationManager.isActualTransactionActive())deny();
        String command=one(db.query("SELECT o.payload->>'$.command.command_id' FROM task_run r JOIN outbox_event o ON o.tenant_id=r.tenant_id AND o.aggregate_id=r.run_id AND o.event_type='run.start.requested' WHERE r.tenant_id=? AND r.run_id=? AND r.created_by=? AND r.execution_kind='TASK' LIMIT 2",
            (r,n)->r.getString(1),tenant,run,actor));
        return controls.withReads(http,tenant,run,command,reads->ThrowingSupplier.of(()->{
            if(!actor.equals(reads.actorId()))deny();
            var prepared=publications.prepareControl(reads,text(reads.replay(),"agent_release_id"),"RUN_CREATION");
            return reads.execute(a->ThrowingSupplier.of(()->{
                var current=a.watermarks();
                if(expectedAuth!=null&&!expectedAuth.equals(current.authEpoch()))stale();
                var proof=new Verified(a,reads,prepared,run,step,read(a,reads,prepared,run,step,requireAvailable));
                try {var result=operation.apply(current,proof);proof.requireUnchanged();return result;}
                finally {proof.active=false;}
            }).get());
        }).get());
    }
    @FunctionalInterface interface Operation<T>{T apply(TenantWatermarks current,Verified proof)throws Exception;}
    private Snapshot read(EnterpriseAccess.Admission a,FormalRunControlAccess.Reads reads,
            AgentPublicationAuthority.PreparedPublication prepared,String run,String step,boolean requireAvailable)throws Exception {
        var replay=reads.replay();String tenant=a.context().tenant_id(),project=text(replay,"project_id"),task=text(replay,"task_id");
        var publication=publications.require(a,text(replay,"agent_release_id"),"RUN_CREATION",prepared);
        var policy=policies.requireByVersion(a,reads.policyVersion(),reads.policySha256(),project,"RUN_CREATION");
        if(!publication.approvalSha256().equals(text(replay,"publication_approval_sha256"))
                ||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256())
                ||!json.tree(policy.definition()).equals(replay.required("execution_policy"))
                ||!json.tree(publication.manifest().manifest()).equals(replay.required("agent_release")))bad();
        AgentExecutionPolicyAuthority.requireManifest(policy.definition(),publication.manifest().manifest());
        String nodeKind=one(db.query("SELECT node_kind FROM runtime_node_definition WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND created_by=? LIMIT 2 FOR UPDATE",
                (r,n)->r.getString(1),tenant,project,run,step,a.actorId()));
        InputSnapshot input;
        if("N03".equals(nodeKind)) {
            var strict=requireStrict(a,reads,publication,policy,run,step);
            input=new InputSnapshot(strict.manifest(),strict.receiptSha256(),strict.inputSha256(),strict.graphSha256(),strict::requireUnchanged);
        } else if("N04".equals(nodeKind)) {
            var writer=requireWriter(a,reads,publication,policy,run,step);
            input=new InputSnapshot(writer.manifest(),writer.receiptSha256(),writer.inputSha256(),writer.input().graphSha256(),writer::requireUnchanged);
        } else if("N05".equals(nodeKind)) {
            var verifier=requireVerifier(a,reads,publication,policy,run,step);
            input=new InputSnapshot(verifier.manifest(),verifier.receiptSha256(),verifier.inputSha256(),verifier.input().graphSha256(),verifier::requireUnchanged);
        } else {
            var research=inputs.require(a,run,step,publication,policy);
            input=new InputSnapshot(research.manifest(),research.receiptSha256(),research.inputSha256(),research.graphSha256(),research::requireUnchanged);
        }
        input.requireUnchanged();var fixed=input.manifest();
        if(!tenant.equals(text(fixed,"tenant_id"))||!project.equals(text(fixed,"project_id"))||!task.equals(text(fixed,"task_id"))||!run.equals(text(fixed,"run_id"))||!step.equals(text(fixed,"step_id")))bad();
        var roleBasis=json.basis(fixed.required("role_version"));
        if(!json.tree(new PermissionProtocol.Basis(policy.versionId(),policy.contentSha256())).equals(fixed.required("execution_policy_version"))
                ||!schema.sha256(replay).equals(Set.of("N03","N04","N05").contains(nodeKind)?text(fixed,"replay_sha256"):text(fixed.required("replay_artifact"),"sha256")))bad();
        var node=one(db.query("SELECT node_kind,role_version,role_sha256,policy_version,policy_sha256 FROM runtime_node_definition WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND created_by=? LIMIT 2 FOR UPDATE",
            (r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),tenant,project,run,step,a.actorId()));
        if(!roleBasis.version_id().equals(node.get(1))||!roleBasis.content_sha256().equals(node.get(2))||!policy.versionId().equals(node.get(3))||!policy.contentSha256().equals(node.get(4)))bad();
        var worker=policy.definition().worker();var binding=policy.requireNode(node.get(0),roleBasis,worker.serviceUri(),worker.audience());
        var role=roles.require(a,roleBasis,"ROLE",project,"RUN_CREATION");
        var literal=schema.permissions(schema.read("RoleScope",json.canonical(role.canonicalDefinition().required("request").required("tool_scope"))));
        var allowed=new LinkedHashSet<>(literal);
        ModelEligibilityAuthority.Verified model=null;
        if(binding.modelVersion()!=null){
            model=models.require(a,binding.modelVersion().version_id(),"RUN_CREATION",project);
            if(!binding.modelVersion().content_sha256().equals(model.contentSha256()))bad();
            model.requireParameters(binding.generationParameters());
        }
        var original=parse(text(replay.required("authorizations"),"canonical_witnesses")).required("additional_authorizations");
        JsonNode delegationWitness=json.object();
        var limits=publication.manifest().manifest().limits();
        if(original.has(node.get(0))) {
            var approved=delegations.requireOriginalForTask(a,publication,policy,roleBasis,task,run,text(replay,"budget_revision"),original.required(node.get(0)),requireAvailable);
            delegationWitness=approved.authorizations();
            if(!sameEvidence(original.required(node.get(0)),delegationWitness))stale();
            approved.requireBudget(limits.currency(),limits.maxCostAmount(),limits.maxTotalTokens(),limits.maxModelRequests());
            for(var op:delegationWitness.required("rule").required("request").required("operations")) {
                if("TASK".equals(text(op,"selector"))&&Set.of("task.read","task.run").contains(text(op,"action")))
                    allowed.add(new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,project,"TASK",task),text(op,"action"),text(op,"purpose")));

            }
        }
        var requested=new LinkedHashSet<RunTokenAuthoritySchema.Permission>();
        for(var action:binding.actions()) {
            var targets=new ArrayList<PermissionProtocol.Scope>();
            switch(action.targetSelector()) {
                case "TASK"->targets.add(new PermissionProtocol.Scope(tenant,project,"TASK",task));
                case "PROJECT"->targets.add(new PermissionProtocol.Scope(tenant,project,"PROJECT",project));
                case "CURRENT_EXECUTION_POLICY"->targets.add(new PermissionProtocol.Scope(tenant,policy.definition().projectId(),"CONFIGURATION",policy.policyId()));
                case "FIXED_VERSION"->targets.add(owner(tenant,project,action.fixedVersion(),action.resourceKind()));
                case "SELECTED_SOURCE"->{for(var source:replay.required("scope_snapshot").required("members"))
                    targets.add(owner(tenant,project,new PermissionProtocol.Basis(text(source,"source_version"),text(source,"original_sha256")),"SOURCE"));}
                case "RUN_REGISTERED_ARTIFACT"->{
                    // Artifact input/output is authorized by its separate exact receipt-bound
                    // host authority. A registration never expands the immutable token principal.
                }
                // Future artifacts and broad selectors never become runtime rights.
                default->deny();
            }
            for(var target:targets){if(!action.resourceKind().equals(target.resource_kind()))bad();requested.add(new RunTokenAuthoritySchema.Permission(target,action.action(),action.purpose()));}
        }
        if(model!=null){
            var target=owner(tenant,project,binding.modelVersion(),"CONFIGURATION");
            if(!model.configId().equals(target.resource_id()))bad();
            var declared=TaskModelNodePermissions.declared(binding.modelVersion(),target,delegationWitness);
            requested.addAll(declared);allowed.addAll(declared);
        }
        var exact=FormalTaskPermissionAuthority.narrow(List.copyOf(allowed),List.copyOf(requested));
        if(!node.get(0).equals(text(fixed,"node_kind"))||!sameEvidence(json.tree(exact),fixed.required("node_permissions")))bad();
        var principal=replay.required("principal_manifest");
        RuntimeNodePermissions.intersect(schema.permissions(principal),exact,exact.stream().map(RunTokenAuthoritySchema.Permission::action).distinct().toList());
        var account=one(permissions.currentBinding(a.actorId()));if(!a.actorId().equals(account.userId())||!"ACTIVE".equals(account.status()))deny();
        var witnesses=json.object().arrayNode();
        for(var permission:exact)witnesses.add(AgentPublicationWitness.tree(grants.requireResource(permission.scope(),a.actorId(),account.mode(),permission.action(),permission.purpose(),permissions.resourceGrants(a.actorId(),permission.action()),permissions.databaseTime())));
        var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",project);values.put("budgetRevision",text(replay,"budget_revision"));
        var budget=requireAvailable?budgets.require(a,values,permissions.databaseTime(),limits.currency())
            :budgets.requireWithinBounds(a,values,permissions.databaseTime(),limits.currency());
        requireCumulative(budget,limits.maxCostAmount(),limits.maxTotalTokens(),limits.maxModelRequests(),requireAvailable);
        if(!delegationWitness.isEmpty()){var r=delegationWitness.required("rule").required("request");requireCumulative(budget,text(r,"maximum_amount"),text(r,"maximum_tokens"),text(r,"maximum_calls"),requireAvailable);}
        if(model!=null)model.requireUnchanged();
        role.requireUnchanged();policy.requireUnchanged();publication.requireUnchanged();input.requireUnchanged();
        var identity=json.object();identity.set("publication",publication.authorizations());identity.set("policy",policy.authorizations());identity.set("role",role.authorizations());identity.set("delegation",delegationWitness);identity.set("grants",witnesses);
        if(model!=null){identity.set("model",json.tree(model.safeReplayBinding()));identity.set("model_grants",json.tree(model.authorizations()));}
        identity.put("input_receipt",input.receiptSha256()).put("input_sha256",input.inputSha256()).put("graph",input.graphSha256());
        return new Snapshot(tenant,project,task,run,step,roleBasis,worker.serviceUri(),worker.audience(),exact,identity.deepCopy());
    }
    /** Lossless typed witness comparison: never route grant revisions through floating-point JCS. */
    static boolean sameEvidence(JsonNode left,JsonNode right){
        if(left==null||right==null)return left==right;
        if(left.isIntegralNumber()&&right.isIntegralNumber())return left.bigIntegerValue().equals(right.bigIntegerValue());
        if(left.isObject()&&right.isObject()){
            if(left.size()!=right.size())return false;
            var fields=left.fields();while(fields.hasNext()){var f=fields.next();if(!right.has(f.getKey())||!sameEvidence(f.getValue(),right.get(f.getKey())))return false;}return true;
        }
        if(left.isArray()&&right.isArray()){if(left.size()!=right.size())return false;for(int i=0;i<left.size();i++)if(!sameEvidence(left.get(i),right.get(i)))return false;return true;}
        return left.equals(right);
    }
    static void requireCumulative(JsonNode account,String amount,String tokens,String calls){requireCumulative(account,amount,tokens,calls,true);}
    static void requireCumulative(JsonNode account,String amount,String tokens,String calls,boolean available){
        int exhausted=available?0:1;
        if(new BigDecimal(amount).signum()<0||new BigInteger(tokens).signum()<0||new BigInteger(calls).signum()<0)bad();
        for(String field:List.of("spent","reserved"))if(new BigDecimal(text(account,field)).signum()<0)bad();
        for(String field:List.of("spent_tokens","reserved_tokens","spent_calls","reserved_calls"))if(new BigInteger(text(account,field)).signum()<0)bad();
        if(new BigDecimal(text(account,"spent")).add(new BigDecimal(text(account,"reserved"))).compareTo(new BigDecimal(amount))>=exhausted
                ||new BigInteger(text(account,"spent_tokens")).add(new BigInteger(text(account,"reserved_tokens"))).compareTo(new BigInteger(tokens))>=exhausted
                ||new BigInteger(text(account,"spent_calls")).add(new BigInteger(text(account,"reserved_calls"))).compareTo(new BigInteger(calls))>=exhausted)
            throw new TenantAccessException(ErrorCode.BUDGET_EXCEEDED);
    }
    private PermissionProtocol.Scope owner(String tenant,String project,PermissionProtocol.Basis basis,String kind){
        if(basis==null)deny();var row=one(owners.ownersAcrossDomains(List.of(basis.version_id()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList())).owner();
        if(!tenant.equals(row.tenantId())||!basis.version_id().equals(row.versionId())||row.contentSha256()!=null&&!basis.content_sha256().equals(row.contentSha256())||!kind.equals(row.resourceKind())
                ||row.resourceProjectId()!=null&&!project.equals(row.resourceProjectId())||row.versionProjectId()!=null&&!project.equals(row.versionProjectId())||row.conversationId()!=null)bad();
        if(one(db.query("SELECT COUNT(*) FROM version_registry WHERE tenant_id=? AND version_id=? AND content_sha256=? AND owner_project_id <=> ? FOR UPDATE",
            (r,n)->r.getInt(1),tenant,basis.version_id(),basis.content_sha256(),row.versionProjectId()))!=1)bad();
        return new PermissionProtocol.Scope(tenant,row.resourceProjectId(),kind,row.resourceId());
    }
    @FunctionalInterface private interface CurrentInput {void require()throws Exception;}
    private record InputSnapshot(JsonNode manifest,String receiptSha256,String inputSha256,String graphSha256,CurrentInput current){void requireUnchanged()throws Exception{current.require();}}
    private TaskStrictFieldInputAuthority.Verified requireStrict(EnterpriseAccess.Admission a,FormalRunControlAccess.Reads reads,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String run,String step)throws Exception {
        if(strictInputs==null||strictProduction==null||strictRetention==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var authority=strictInputs.getIfAvailable();if(authority==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var replay=reads.replay();var fixed=replay.required("retention");String project=text(replay,"project_id");
        String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? LIMIT 2 FOR UPDATE",
                (r,n)->r.getString(1),a.context().tenant_id(),project,run,a.actorId()));
        var retained=strictRetention.requireFixedForRun(a,text(fixed,"policy_revision"),text(fixed,"content_sha256"),project,classification,"RUN_CREATION");
        var production=strictProduction.require(a,publication,policy,retained,run);
        return authority.require(a,run,step,publication,policy,production);
    }
    private TaskSectionWriterInputAuthority.Verified requireWriter(EnterpriseAccess.Admission a,FormalRunControlAccess.Reads reads,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String run,String step)throws Exception {
        if(writerInputs==null||strictProduction==null||strictRetention==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var authority=writerInputs.getIfAvailable();if(authority==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var replay=reads.replay();var fixed=replay.required("retention");String project=text(replay,"project_id");
        String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? LIMIT 2 FOR UPDATE",
                (r,n)->r.getString(1),a.context().tenant_id(),project,run,a.actorId()));
        var retained=strictRetention.requireFixedForRun(a,text(fixed,"policy_revision"),text(fixed,"content_sha256"),project,classification,"RUN_CREATION");
        var production=strictProduction.require(a,publication,policy,retained,run);
        return authority.require(a,run,step,publication,policy,production);
    }
    private TaskSourceVerifierInputAuthority.Verified requireVerifier(EnterpriseAccess.Admission a,FormalRunControlAccess.Reads reads,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String run,String step)throws Exception {
        if(verifierInputs==null||strictProduction==null||strictRetention==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var authority=verifierInputs.getIfAvailable();if(authority==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var replay=reads.replay();var fixed=replay.required("retention");String project=text(replay,"project_id");
        String classification=one(db.query("SELECT classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? LIMIT 2 FOR UPDATE",
                (r,n)->r.getString(1),a.context().tenant_id(),project,run,a.actorId()));
        var retained=strictRetention.requireFixedForRun(a,text(fixed,"policy_revision"),text(fixed,"content_sha256"),project,classification,"RUN_CREATION");
        var production=strictProduction.require(a,publication,policy,retained,run);
        return authority.require(a,run,step,publication,policy,production);
    }
    private record Snapshot(String tenant,String project,String task,String run,String step,PermissionProtocol.Basis role,String service,String audience,List<RunTokenAuthoritySchema.Permission> permissions,JsonNode evidence){}
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final FormalRunControlAccess.Reads reads;
        private final AgentPublicationAuthority.PreparedPublication prepared;private final String run,step;private final Snapshot snapshot;
        private final Thread thread=Thread.currentThread();private final TenantScope scope=TenantScope.requireCurrent();
        private final Map<Object,Object> transaction=Map.copyOf(TransactionSynchronizationManager.getResourceMap());private boolean active=true;
        private Verified(EnterpriseAccess.Admission a,FormalRunControlAccess.Reads reads,AgentPublicationAuthority.PreparedPublication prepared,String run,String step,Snapshot snapshot){
            admission=a;this.reads=reads;this.prepared=prepared;this.run=run;this.step=step;this.snapshot=snapshot;if(transaction.isEmpty())deny();
        }
        private void active(){if(!active||thread!=Thread.currentThread()||scope!=TenantScope.requireCurrent()||!TransactionSynchronizationManager.isActualTransactionActive())deny();
            var current=TransactionSynchronizationManager.getResourceMap();if(current.size()!=transaction.size()||transaction.entrySet().stream().anyMatch(e->current.get(e.getKey())!=e.getValue()))deny();}
        public List<RunTokenAuthoritySchema.Permission> nodePermissions(){active();return snapshot.permissions();}
        /** Package-private current control inputs, never a detached or caller-minted admission. */
        EnterpriseAccess.Admission controlAdmission(){active();admission.controlProof().requireActive();return admission;}
        TaskNodeInputAuthority.Verified currentInput()throws Exception {
            requireUnchanged();
            var publication=publications.require(admission,text(reads.replay(),"agent_release_id"),"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(admission,reads.policyVersion(),reads.policySha256(),snapshot.project(),"RUN_CREATION");
            var input=inputs.require(admission,run,step,publication,policy);input.requireUnchanged();requireUnchanged();return input;
        }
        TaskStrictFieldInputAuthority.Verified currentStrictFieldInput()throws Exception {
            requireUnchanged();
            String kind=one(db.query("SELECT node_kind FROM runtime_node_definition WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",
                    (r,n)->r.getString(1),snapshot.tenant(),run,step));if(!"N03".equals(kind))deny();
            var publication=publications.require(admission,text(reads.replay(),"agent_release_id"),"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(admission,reads.policyVersion(),reads.policySha256(),snapshot.project(),"RUN_CREATION");
            var input=requireStrict(admission,reads,publication,policy,run,step);input.requireUnchanged();requireUnchanged();return input;
        }
        TaskSectionWriterInputAuthority.Verified currentWriterInput()throws Exception {
            requireUnchanged();
            String kind=one(db.query("SELECT node_kind FROM runtime_node_definition WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",
                    (r,n)->r.getString(1),snapshot.tenant(),run,step));if(!"N04".equals(kind))deny();
            var publication=publications.require(admission,text(reads.replay(),"agent_release_id"),"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(admission,reads.policyVersion(),reads.policySha256(),snapshot.project(),"RUN_CREATION");
            var input=requireWriter(admission,reads,publication,policy,run,step);input.requireUnchanged();requireUnchanged();return input;
        }
        TaskSourceVerifierInputAuthority.Verified currentVerifierInput()throws Exception {
            requireUnchanged();
            String kind=one(db.query("SELECT node_kind FROM runtime_node_definition WHERE tenant_id=? AND run_id=? AND step_id=? LIMIT 2 FOR UPDATE",
                    (r,n)->r.getString(1),snapshot.tenant(),run,step));if(!"N05".equals(kind))deny();
            var publication=publications.require(admission,text(reads.replay(),"agent_release_id"),"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(admission,reads.policyVersion(),reads.policySha256(),snapshot.project(),"RUN_CREATION");
            var input=requireVerifier(admission,reads,publication,policy,run,step);input.requireUnchanged();requireUnchanged();return input;
        }
        void requireIdentity(String tenant,String actor,String run,String step,String roleSha,String service,String audience)throws Exception{
            active();if(!snapshot.tenant().equals(tenant)||!admission.actorId().equals(actor)||!snapshot.run().equals(run)||!snapshot.step().equals(step)
                ||!snapshot.role().content_sha256().equals(roleSha)||!snapshot.service().equals(service)||!snapshot.audience().equals(audience))deny();requireUnchanged();
        }
        public void requireUnchanged()throws Exception{active();var fresh=read(admission,reads,prepared,run,step,false);if(!snapshot.equals(fresh)||!sameEvidence(snapshot.evidence(),fresh.evidence()))stale();}
    }
    private JsonNode parse(String raw)throws Exception{return com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(raw);}
    private static String text(JsonNode value,String field){var node=value.required(field);if(!node.isTextual())bad();return node.textValue();}
    private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
