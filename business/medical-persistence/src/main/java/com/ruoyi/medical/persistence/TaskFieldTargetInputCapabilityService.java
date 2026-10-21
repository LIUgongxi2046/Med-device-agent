package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Explicit, independently approved read capability for one already registered pre-RN01 field target.
 * This service does not grant task.read/task.run, alter ROLE/078/093, or create a future-artifact selector.
 * There is intentionally no component annotation or runtime consumer wiring in this implementation. */
public final class TaskFieldTargetInputCapabilityService {
    public static final String PURPOSE = "TASK_FIELD_TARGET_INPUT";
    public static final String WRITE = "task.field_target_input.write";
    public static final String APPROVE = "task.field_target_input.approve";
    public static final String DISABLE = "task.field_target_input.disable";
    public static final String READ = "task.field_target_input.read";
    private static final String CAPABILITY = "task_field_target_input_capability";
    private static final String STATE = "task_field_target_input_capability_state";
    private static final String DECISION = "task_field_target_input_capability_decision";
    private static final String OPERATION = "TASK-FIELD-TARGET-INPUT-";
    private static final String LINEAGE = "TASK_FIELD_TARGET_INPUT";
    private static final List<String> PINS = List.of("task_revision", "task_input_sha256", "plan_revision",
            "plan_sha256", "template_version", "template_sha256");
    private static final List<String> DIRECT_COLUMNS = List.of("project_id", "task_id", "task_revision",
            "task_input_sha256", "plan_revision", "plan_sha256", "template_version", "template_sha256",
            "artifact_id", "artifact_sha256", "object_version", "artifact_schema_sha256", "registration_id",
            "producing_receipt_sha256", "release_id", "publication_approval_sha256", "maximum_read_bytes");

    private final EnterpriseAccess access;
    private final JdbcTemplate db;
    private final AgentPublicationAuthority publications;
    private final AgentExecutionPolicyAuthority policies;
    private final FormalCapabilityIdentityAuthority roles;
    private final TaskService tasks;
    private final TaskPlanService plans;
    private final PermissionMapper permissions;
    private final PermissionAuthority grants;
    private final PlatformMemberIdentityStatus identities;
    private final ResourceReadAuthority resources;
    private final TaskFieldTargetRegistration registrations;
    private final TaskFieldTargetSchema schema;
    private final PermissionSchema json;

    public TaskFieldTargetInputCapabilityService(EnterpriseAccess access, DataSource data,
            AgentPublicationAuthority publications, AgentExecutionPolicyAuthority policies,
            FormalCapabilityIdentityAuthority roles, TaskService tasks, TaskPlanService plans,
            PermissionMapper permissions, PermissionAuthority grants, PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources, TaskFieldTargetRegistration registrations,
            TaskFieldTargetSchema schema, PermissionSchema json) {
        this.access=Objects.requireNonNull(access); this.db=new JdbcTemplate(Objects.requireNonNull(data));
        db.setQueryTimeout(5); this.publications=Objects.requireNonNull(publications); this.policies=Objects.requireNonNull(policies);
        this.roles=Objects.requireNonNull(roles); this.tasks=Objects.requireNonNull(tasks); this.plans=Objects.requireNonNull(plans);
        this.permissions=Objects.requireNonNull(permissions); this.grants=Objects.requireNonNull(grants);
        this.identities=Objects.requireNonNull(identities); this.resources=Objects.requireNonNull(resources);
        this.registrations=Objects.requireNonNull(registrations); this.schema=Objects.requireNonNull(schema); this.json=Objects.requireNonNull(json);
    }

    public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String taskId,String key,byte[] bytes) throws Exception {
        WireValues.uuid(taskId); var request=schema.request("InputAuthor",bytes);
        if(!taskId.equals(text(request,"task_id"))) denied();
        return author(subject,context,key,bytes);
    }

    public JsonNode author(ApplicationSessionIdentity.Verified subject, String context, String key, byte[] bytes) throws Exception {
        key(key); var request=schema.request("InputAuthor",bytes);
        var recovered=access.execute(subject,a->checked(()->{
            human(a,context); resources.require(a,taskScope(a,request),WRITE,PURPOSE);
            return recover(a,key,"AUTHOR",request,null);
        }));
        if(recovered!=null) return recovered;
        var prepared=publications.prepare(subject,context,text(request,"release_id"),"RUN_CREATION",false);
        return access.execute(subject,a->checked(()->{
            human(a,context); var grant=resources.require(a,taskScope(a,request),WRITE,PURPOSE);
            var replay=recover(a,key,"AUTHOR",request,null); if(replay!=null) return replay;
            var publication=publications.require(a,text(request,"release_id"),"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),
                    text(request,"project_id"),text(request,"classification"),"RUN_CREATION");
            var registration=validatePins(a,request,publication,policy,WRITE);
            var now=permissions.databaseTime(); String capability=id(),operation=id(),audit=id(),event=id();
            var manifest=manifest(a,capability,request,now);
            var fixed=json.object(); fixed.set("manifest",manifest); fixed.set("request",request);
            fixed.set("grant",TaskFieldAuthorityWitness.tree(grant)); fixed.put("identity_mode",a.bindingView().mode());
            fixed.put("registration_receipt_sha256",registration.receiptSha256());
            fixed.put("auth_epoch",a.watermarks().authEpoch().toString()).put("governance_epoch",a.watermarks().governanceEpoch().toString());
            String hash=schema.hash(manifest);
            var receipt=receipt(a,request,capability,hash,null,"DRAFT","1",operation,audit,event,request,now);
            changed(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,'TASK_FIELD_TARGET_INPUT_CAPABILITY',?)",
                    a.context().tenant_id(),text(request,"project_id"),capability,hash));
            journal(a,key,"AUTHOR",capability,request,receipt,fixed);
            var row=metadata(a,request,now); row.put("capability_id",capability);
            for(String field:DIRECT_COLUMNS) row.put(field,text(request,field));
            row.put("mapping_revision",text(request,"assignment_id")); row.put("mapping_sha256",text(request,"assignment_sha256"));
            row.put("role_version",text(request.required("role_version"),"version_id"));
            row.put("role_sha256",text(request.required("role_version"),"content_sha256"));
            row.put("policy_version",text(request.required("execution_policy"),"version_id"));
            row.put("policy_sha256",text(request.required("execution_policy"),"content_sha256"));
            row.put("occurrence_ids",schema.canonical(request.required("occurrence_ids")));
            row.put("fixed_json",schema.canonical(fixed)); row.put("content_sha256",hash);
            records(row,key,operation,audit,event,receipt,request); authority(row,a,grant); insert(CAPABILITY,row);
            var state=metadata(a,request,now); state.put("capability_id",capability); state.put("status","DRAFT");
            state.put("revision","1"); state.put("latest_decision_id",null); state.put("updated_at",now); insert(STATE,state);
            for(String upstream:parents(request)) changed(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,created_at) VALUES(?,?,?,?,?,?)",
                    a.context().tenant_id(),capability,upstream,LINEAGE,a.actorId(),now));
            load(a,capability); registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged();
            if(!grant.equals(resources.require(a,taskScope(a,request),WRITE,PURPOSE))) stale();
            advance(a); return receipt;
        }));
    }

    /** The capability identity is part of the endpoint, never inferred from an unrelated request body. */
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String capabilityId,
            String key,byte[] bytes) throws Exception {
        return decide(subject,context,null,capabilityId,key,bytes);
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String taskId,String capabilityId,
            String key, byte[] bytes) throws Exception {
        if(taskId!=null) WireValues.uuid(taskId);
        WireValues.uuid(capabilityId); key(key); var request=schema.request("InputDecide",bytes);
        var original=access.execute(subject,a->checked(()->{
            human(a,context); var row=load(a,capabilityId); requireRouteTask(row,taskId); requireRequestProject(row,request);
            resources.require(a,taskScope(a,row.request()),decisionAction(request),PURPOSE);
            return recover(a,key,"DECIDE",request,capabilityId);
        }));
        if(original!=null) return original;
        var loaded=access.execute(subject,a->checked(()->{
            human(a,context); var row=load(a,capabilityId); requireRouteTask(row,taskId); requireRequestProject(row,request);
            resources.require(a,taskScope(a,row.request()),decisionAction(request),PURPOSE); return row;
        }));
        var prepared="APPROVE".equals(text(request,"decision"))
                ?publications.prepare(subject,context,text(loaded.request(),"release_id"),"RUN_CREATION",false):null;
        return access.execute(subject,a->checked(()->{
            human(a,context); var row=load(a,capabilityId); requireRouteTask(row,taskId); requireRequestProject(row,request);
            var grant=resources.require(a,taskScope(a,row.request()),decisionAction(request),PURPOSE);
            var replay=recover(a,key,"DECIDE",request,capabilityId); if(replay!=null) return replay;
            if(!row.hash().equals(text(request,"capability_sha256"))) mismatch();
            var previous=state(a,row);
            if(!previous.revision().equals(text(request,"expected_revision"))) conflict();
            requireDecision(previous.status(),text(request,"decision"),row.author(),a.actorId());
            AgentPublicationAuthority.VerifiedPublication publication=null;
            AgentExecutionPolicyAuthority.VerifiedPolicy policy=null;
            TaskFieldTargetRegistration.Verified registration=null;
            if("APPROVE".equals(text(request,"decision"))) {
                publication=publications.require(a,text(row.request(),"release_id"),"RUN_CREATION",prepared);
                policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),
                        row.project(),text(row.request(),"classification"),"RUN_CREATION");
                requireCurrentActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("grant"),row.request(),WRITE);
                registration=validatePins(a,row.request(),publication,policy,APPROVE);
            }
            var now=permissions.databaseTime(); String decision=id(),operation=id(),audit=id(),event=id();
            String status="APPROVE".equals(text(request,"decision"))?"APPROVED":"DISABLED";
            String revision=RunCreateSchema.next(previous.revision());
            var body=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id())
                    .put("capability_id",row.id()).put("capability_sha256",row.hash()).put("decision_id",decision)
                    .put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString())
                    .put("identity_mode",a.bindingView().mode()).put("previous_status",previous.status())
                    .put("previous_revision",previous.revision()).put("status",status).put("revision",revision);
            body.set("request",request); body.set("grant",TaskFieldAuthorityWitness.tree(grant));
            body.put("auth_epoch",a.watermarks().authEpoch().toString()).put("governance_epoch",a.watermarks().governanceEpoch().toString());
            var receipt=receipt(a,row.request(),row.id(),row.hash(),decision,status,revision,operation,audit,event,request,now);
            journal(a,key,"DECIDE",row.id(),request,receipt,body);
            var record=metadata(a,row.request(),now); record.put("capability_id",row.id()); record.put("decision_id",decision);
            record.put("decision_json",schema.canonical(body)); record.put("content_sha256",schema.hash(body));
            record.put("expected_revision",previous.revision()); record.put("decision",text(request,"decision"));
            records(record,key,operation,audit,event,receipt,request); authority(record,a,grant); insert(DECISION,record);
            changed(db.update("UPDATE "+STATE+" SET status=?,revision=?,latest_decision_id=?,updated_at=? WHERE tenant_id=? AND capability_id=? AND status=? AND revision=? AND latest_decision_id <=> ?",
                    status,revision,decision,now,a.context().tenant_id(),row.id(),previous.status(),previous.revision(),previous.decisionId()));
            readDecision(a,row,decision);
            if(registration!=null) { registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged(); }
            if(!grant.equals(resources.require(a,taskScope(a,row.request()),decisionAction(request),PURPOSE))) stale();
            advance(a); return receipt;
        }));
    }

    /** Governance view only. Reading a DRAFT/DISABLED record never produces a data-read proof. */
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String capabilityId) {
        return read(subject,context,null,capabilityId);
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String taskId,String capabilityId) {
        if(taskId!=null) WireValues.uuid(taskId); WireValues.uuid(capabilityId);
        return access.execute(subject,a->checked(()->{
            human(a,context); var row=load(a,capabilityId); requireRouteTask(row,taskId); return view(a,row);
        }));
    }

    private JsonNode view(EnterpriseAccess.Admission a,Row row) throws Exception {
        var scope=taskScope(a,row.request()); var grant=resources.require(a,scope,READ,PURPOSE); var current=state(a,row);
        var result=json.object().put("schema_version","1.0.0").put("capability_sha256",row.hash())
                .put("status",current.status()).put("revision",current.revision());
        result.set("manifest",row.manifest().deepCopy()); result.set("latest_decision_id",json.tree(current.decisionId()));
        var actions=result.putArray("allowed_actions"); actions.add("READ");
        if("DRAFT".equals(current.status())&&!row.author().equals(a.actorId())&&resources.find(a,scope,APPROVE,PURPOSE).isPresent()) actions.add("APPROVE");
        if(!"DISABLED".equals(current.status())&&resources.find(a,scope,DISABLE,PURPOSE).isPresent()) actions.add("DISABLE");
        if(!grant.equals(resources.require(a,scope,READ,PURPOSE))) stale();
        return schema.validate("InputView",result);
    }

    /** Bounded normal authoring context. Candidate JSON is a proposal, never an issued capability. */
    public JsonNode context(ApplicationSessionIdentity.Verified subject,String context,String taskId) throws Exception {
        WireValues.uuid(taskId);
        var initial=access.execute(subject,a->checked(()->{
            human(a,context); var task=tasks.fixedTask(a,taskId); var head=task.head();
            var scope=new PermissionProtocol.Scope(a.context().tenant_id(),head.projectId(),"TASK",taskId);
            resources.require(a,scope,READ,PURPOSE);
            boolean canAuthor=resources.find(a,scope,WRITE,PURPOSE).isPresent();
            var registered=registrationIds(a,head.projectId(),taskId);
            return new ContextPins(head.projectId(),head.agentReleaseId(),canAuthor&&!registered.isEmpty());
        }));
        var prepared=initial.prepare()?publications.prepare(subject,context,initial.release(),"RUN_CREATION",false):null;
        return access.execute(subject,a->checked(()->{
            human(a,context); var task=tasks.fixedTask(a,taskId); var head=task.head();
            if(!initial.project().equals(head.projectId())||!initial.release().equals(head.agentReleaseId())) stale();
            var scope=new PermissionProtocol.Scope(a.context().tenant_id(),head.projectId(),"TASK",taskId);
            var read=resources.require(a,scope,READ,PURPOSE); var write=resources.find(a,scope,WRITE,PURPOSE);
            var result=json.object().put("schema_version","1.0.0").put("project_id",head.projectId()).put("task_id",taskId);
            var candidates=result.putArray("candidates"); var capabilities=result.putArray("capabilities");
            var ids=db.query("SELECT capability_id FROM "+CAPABILITY+" WHERE tenant_id=? AND project_id=? AND task_id=? ORDER BY capability_id LIMIT 257 FOR UPDATE",
                    (r,n)->r.getString(1),a.context().tenant_id(),head.projectId(),taskId);
            if(ids.size()>256) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            for(String id:ids) capabilities.add(view(a,load(a,id)));
            var actualRegistrations=registrationIds(a,head.projectId(),taskId);
            if(write.isPresent()&&!actualRegistrations.isEmpty()) {
                if(prepared==null) stale();
                var publication=publications.require(a,head.agentReleaseId(),"RUN_CREATION",prepared);
                var policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),head.projectId(),"RUN_CREATION");
                var n03=policy.definition().bindings().stream().filter(n->"N03".equals(n.nodeKind())).toList();
                if(n03.size()!=1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
                for(String artifact:actualRegistrations) {
                    var registered=registrations.requireRegistered(a,artifact,WRITE);
                    var candidate=json.object().put("schema_version","1.0.0").put("project_id",head.projectId()).put("task_id",taskId);
                    for(String pin:PINS) candidate.set(pin,registered.pins().required(pin).deepCopy());
                    candidate.put("assignment_id",registered.mappingRevision()).put("assignment_sha256",registered.mappingSha256())
                            .put("artifact_id",registered.artifactId()).put("artifact_sha256",registered.targetSha256())
                            .put("object_version",registered.reference().objectVersion()).put("registration_id",registered.registrationId())
                            .put("producing_receipt_sha256",registered.receiptSha256()).put("release_id",head.agentReleaseId())
                            .put("publication_approval_sha256",publication.approvalSha256()).put("artifact_schema_sha256",registered.targetSchemaSha256())
                            .put("consumer_node_kind","N03").put("maximum_read_bytes",registered.reference().sizeBytes())
                            .put("expected_revision","0").put("classification",text(registered.receipt(),"classification"))
                            .put("reason","Read this exact registered field target at N03.");
                    candidate.set("role_version",json.tree(n03.get(0).roleVersion()));
                    candidate.set("execution_policy",json.tree(new PermissionProtocol.Basis(policy.versionId(),policy.contentSha256())));
                    candidate.set("occurrence_ids",json.tree(registered.occurrenceIds())); candidate.set("object_ref",json.tree(registered.reference()));
                    validatePins(a,candidate,publication,policy,WRITE); candidates.add(schema.validate("InputAuthor",candidate));
                }
                publication.requireUnchanged(); policy.requireUnchanged();
            }
            var actions=result.putArray("allowed_actions"); actions.add("READ"); if(write.isPresent()) actions.add("AUTHOR");
            if(!read.equals(resources.require(a,scope,READ,PURPOSE))||!write.equals(resources.find(a,scope,WRITE,PURPOSE))) stale();
            return schema.validate("InputContext",result);
        }));
    }
    private record ContextPins(String project,String release,boolean prepare) { }
    private List<String> registrationIds(EnterpriseAccess.Admission a,String project,String task) {
        var ids=db.query("SELECT artifact_id FROM task_field_target_registration WHERE tenant_id=? AND project_id=? AND task_id=? ORDER BY artifact_id LIMIT 257 FOR UPDATE",
                (r,n)->r.getString(1),a.context().tenant_id(),project,task);
        if(ids.size()>256) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); return ids;
    }

    /** Read-only uncertain-outcome recovery. It never manufactures or replays an authorization decision. */
    public JsonNode recover(ApplicationSessionIdentity.Verified subject,String context,String taskId,byte[] bytes) throws Exception {
        WireValues.uuid(taskId); var request=schema.request("Recovery",bytes); String originalKey=text(request,"original_idempotency_key");
        return access.execute(subject,a->checked(()->{
            human(a,context); var scope=new PermissionProtocol.Scope(a.context().tenant_id(),text(request,"project_id"),"TASK",taskId);
            resources.require(a,scope,READ,PURPOSE);
            var matches=db.queryForList("SELECT operation_name,target_id,response_ref FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name IN (?,?) AND idempotency_key=? FOR UPDATE",
                    a.context().tenant_id(),a.actorId(),OPERATION+"AUTHOR",OPERATION+"DECIDE",originalKey);
            if(matches.isEmpty()) return null;
            // Reusing one key across two different endpoint operations is ambiguous, never silently selected.
            if(matches.size()!=1) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            var found=matches.get(0); var row=load(a,string(found,"target_id"));
            if(!taskId.equals(text(row.request(),"task_id"))||!text(request,"project_id").equals(row.project())) denied();
            String kind=string(found,"operation_name").substring(OPERATION.length());
            JsonNode original=row.request();
            if("DECIDE".equals(kind)) {
                var receipt=schema.validate("InputReceipt",schema.read(string(found,"response_ref")));
                original=readDecision(a,row,text(receipt,"decision_id")).body().required("request");
            }
            return recover(a,originalKey,kind,original,row.id());
        }));
    }

    /** Package-private verified loading, called only by the separate input authority. Explicit ID, never candidate selection. */
    Checked requireCurrent(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,
            String capabilityId,String capabilityHash,String taskId,String originalDecisionId,String originalDecisionHash) throws Exception {
        requireAdmission(a); WireValues.uuid(capabilityId); WireValues.hash(capabilityHash); WireValues.uuid(taskId);
        var row=load(a,capabilityId);
        if(!row.hash().equals(capabilityHash)||!taskId.equals(text(row.request(),"task_id"))) mismatch();
        if(!json.tree(role).equals(row.request().required("role_version"))) denied();
        var inputGrant=resources.require(a,taskScope(a,row.request()),READ,PURPOSE);
        var artifactScope=new PermissionProtocol.Scope(a.context().tenant_id(),row.project(),"ARTIFACT",text(row.request(),"artifact_id"));
        var artifactGrant=resources.require(a,artifactScope,"artifact.read",PURPOSE);
        var current=state(a,row); if(!"APPROVED".equals(current.status())||current.decisionId()==null) denied();
        if(originalDecisionId!=null&&!originalDecisionId.equals(current.decisionId())) stale();
        var decision=readDecision(a,row,current.decisionId());
        if(!"APPROVED".equals(text(decision.body(),"status"))||!"APPROVE".equals(text(decision.body().required("request"),"decision"))
                ||!current.revision().equals(text(decision.body(),"revision"))||row.author().equals(decision.actor())) mismatch();
        if(originalDecisionHash!=null&&!originalDecisionHash.equals(decision.hash())) stale();
        requireCurrentActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("grant"),row.request(),WRITE);
        requireCurrentActor(a,decision.actor(),text(decision.body(),"identity_mode"),decision.body().required("grant"),row.request(),APPROVE);
        var registration=validatePins(a,row.request(),publication,policy,READ);
        var auth=json.object(); auth.set("capability",row.fixed()); auth.set("approval",decision.body());
        auth.set("input_read",TaskFieldAuthorityWitness.tree(inputGrant)); auth.set("artifact_read",TaskFieldAuthorityWitness.tree(artifactGrant));
        auth.put("registration_receipt_sha256",registration.receiptSha256());
        auth.set("publication",publication.authorizations()); auth.set("execution_policy",policy.authorizations());
        registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged();
        return new Checked(row.id(),row.hash(),decision.id(),decision.hash(),row.manifest().deepCopy(),auth,schema.hash(auth));
    }
    /** Dedicated running N03 consumer. Existing author/pre-run registration guards stay unchanged. */
    Checked requireFixedRun(EnterpriseAccess.Admission a,AgentPublicationAuthority.VerifiedPublication publication,
            AgentExecutionPolicyAuthority.VerifiedPolicy policy,PermissionProtocol.Basis role,
            String capabilityId,String capabilityHash,String taskId,String originalDecisionId,String originalDecisionHash,
            TaskStrictFieldTargetAuthority.Verified target) throws Exception {
        requireAdmission(a);Objects.requireNonNull(target).requireFor(a,taskId);
        WireValues.uuid(capabilityId);WireValues.hash(capabilityHash);WireValues.uuid(originalDecisionId);WireValues.hash(originalDecisionHash);
        var row=load(a,capabilityId);if(!row.hash().equals(capabilityHash)||!taskId.equals(text(row.request(),"task_id")))mismatch();
        var request=row.request();if(!json.tree(role).equals(request.required("role_version")))denied();
        publication.requireConsumer(a,text(request,"release_id"),"RUN_CREATION");policy.requireConsumer(a,text(request,"project_id"),"RUN_CREATION");
        if(!publication.approvalSha256().equals(text(request,"publication_approval_sha256"))
                ||!policy.versionId().equals(text(request.required("execution_policy"),"version_id"))
                ||!policy.contentSha256().equals(text(request.required("execution_policy"),"content_sha256"))
                ||!publication.executionPolicyId().equals(policy.versionId())||!publication.executionPolicySha256().equals(policy.contentSha256())
                ||policy.definition().bindings().stream().filter(n->"N03".equals(n.nodeKind())&&role.equals(n.roleVersion())).count()!=1
                ||!policy.definition().allowedClassifications().contains(text(request,"classification")))denied();
        var roleProof=roles.require(a,role,"ROLE",row.project(),"RUN_CREATION");
        var inputGrant=resources.require(a,taskScope(a,request),READ,PURPOSE);
        var artifactGrant=resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),row.project(),"ARTIFACT",target.artifactId()),"artifact.read",PURPOSE);
        var current=state(a,row);if(!"APPROVED".equals(current.status())||!originalDecisionId.equals(current.decisionId()))denied();
        var decision=readDecision(a,row,originalDecisionId);
        if(!originalDecisionHash.equals(decision.hash())||!"APPROVED".equals(text(decision.body(),"status"))
                ||!"APPROVE".equals(text(decision.body().required("request"),"decision"))||row.author().equals(decision.actor()))mismatch();
        requireCurrentActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("grant"),request,WRITE);
        requireCurrentActor(a,decision.actor(),text(decision.body(),"identity_mode"),decision.body().required("grant"),request,APPROVE);
        for(var pin:Map.of("artifact_id",target.artifactId(),"project_id",target.projectId(),"task_id",target.taskId(),
                "assignment_id",target.mappingRevision(),"assignment_sha256",target.mappingSha256(),"artifact_sha256",target.targetSha256(),
                "artifact_schema_sha256",target.targetSchemaSha256(),"registration_id",target.registrationId(),"producing_receipt_sha256",target.receiptSha256()).entrySet())
            if(!pin.getValue().equals(text(request,pin.getKey())))mismatch();
        if(!json.tree(target.reference()).equals(request.required("object_ref"))||!target.reference().objectVersion().equals(text(request,"object_version"))
                ||!text(target.receipt(),"classification").equals(text(request,"classification")))mismatch();
        for(String pin:PINS)if(!request.required(pin).equals(target.pins().required(pin)))mismatch();
        requireCoverage(request.required("occurrence_ids"),target.occurrenceIds());requireReadBound(text(request,"maximum_read_bytes"),target.reference().sizeBytes());
        var auth=json.object();auth.set("capability",row.fixed());auth.set("approval",decision.body());
        auth.set("input_read",TaskFieldAuthorityWitness.tree(inputGrant));auth.set("artifact_read",TaskFieldAuthorityWitness.tree(artifactGrant));
        auth.put("registration_receipt_sha256",target.receiptSha256());auth.set("publication",publication.authorizations());auth.set("execution_policy",policy.authorizations());
        target.requireUnchanged();roleProof.requireUnchanged();publication.requireUnchanged();policy.requireUnchanged();
        return new Checked(row.id(),row.hash(),decision.id(),decision.hash(),row.manifest().deepCopy(),auth,schema.hash(auth));
    }
    record Checked(String capabilityId,String capabilityHash,String decisionId,String decisionHash,JsonNode manifest,JsonNode authorizations,String fingerprint) { }

    private TaskFieldTargetRegistration.Verified validatePins(EnterpriseAccess.Admission a,JsonNode request,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String action) throws Exception {
        schema.validate("InputAuthor",request);
        publication.requireConsumer(a,text(request,"release_id"),"RUN_CREATION");
        policy.requireConsumer(a,text(request,"project_id"),"RUN_CREATION");
        if(!text(request,"publication_approval_sha256").equals(publication.approvalSha256())
                ||!text(request.required("execution_policy"),"version_id").equals(publication.executionPolicyId())
                ||!text(request.required("execution_policy"),"content_sha256").equals(publication.executionPolicySha256())
                ||!text(request.required("execution_policy"),"version_id").equals(policy.versionId())
                ||!text(request.required("execution_policy"),"content_sha256").equals(policy.contentSha256())) mismatch();
        var role=json.basis(request.required("role_version"));
        // Approval of 078 proves this role's N03 association, not permission for this new selector/purpose.
        if(policy.definition().bindings().stream().filter(n->"N03".equals(n.nodeKind())&&role.equals(n.roleVersion())).count()!=1
                ||!policy.definition().allowedClassifications().contains(text(request,"classification"))) denied();
        var actualRole=roles.require(a,role,"ROLE",text(request,"project_id"),"RUN_CREATION");
        var task=tasks.fixedTask(a,text(request,"task_id")); var head=task.head();
        if(!head.projectId().equals(text(request,"project_id"))||!head.taskRevision().equals(text(request,"task_revision"))
                ||!task.input().requestHash().equals(text(request,"task_input_sha256"))
                ||!Objects.equals(head.planRevision(),text(request,"plan_revision"))
                ||!head.templateVersion().equals(text(request,"template_version"))
                ||!head.agentReleaseId().equals(text(request,"release_id"))) stale();
        var plan=plans.readFixed(a,task,text(request,"plan_revision"),true);
        if(!"CONFIRMED".equals(plan.status())||!plan.contentSha256().equals(text(request,"plan_sha256"))) stale();
        var registration=registrations.requireRegistered(a,text(request,"artifact_id"),action);
        requireRegisteredPins(request,registration);
        actualRole.requireUnchanged(); registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged();
        return registration;
    }

    private void requireRegisteredPins(JsonNode request,TaskFieldTargetRegistration.Verified registration) throws Exception {
        if(!text(request,"artifact_id").equals(registration.artifactId())||!text(request,"project_id").equals(registration.projectId())
                ||!text(request,"task_id").equals(registration.taskId())||!text(request,"assignment_id").equals(registration.mappingRevision())
                ||!text(request,"assignment_sha256").equals(registration.mappingSha256())
                ||!text(request,"artifact_sha256").equals(registration.targetSha256())
                ||!text(request,"artifact_schema_sha256").equals(registration.targetSchemaSha256())
                ||!text(request,"registration_id").equals(registration.registrationId())
                ||!text(request,"producing_receipt_sha256").equals(registration.receiptSha256())) mismatch();
        var reference=json.tree(registration.reference());
        if(!reference.equals(request.required("object_ref"))||!text(request,"object_version").equals(text(reference,"object_version"))
                ||!text(request,"artifact_sha256").equals(text(reference,"sha256"))
                ||!text(request,"classification").equals(text(registration.receipt(),"classification"))) mismatch();
        for(String pin:PINS) if(!request.required(pin).equals(registration.pins().required(pin))) mismatch();
        requireCoverage(request.required("occurrence_ids"),registration.occurrenceIds());
        requireReadBound(text(request,"maximum_read_bytes"),text(reference,"size_bytes"));
    }

    static void requireCoverage(JsonNode requested,Collection<String> actual) {
        var expected=new TreeSet<String>(); for(var id:requested) { if(!id.isTextual()||!expected.add(id.textValue())) mismatch(); }
        var registered=new TreeSet<>(actual);
        if(registered.size()!=actual.size()||!expected.equals(registered)) mismatch();
    }
    static void requireReadBound(String maximum,String actual) {
        var bound=WireValues.revision(maximum); var size=WireValues.revision(actual);
        if(bound.signum()<=0||size.signum()<=0||size.compareTo(bound)>0) denied();
    }

    private ObjectNode manifest(EnterpriseAccess.Admission a,String capability,JsonNode request,LocalDateTime now) throws Exception {
        var manifest=request.deepCopy(); ((ObjectNode)manifest).remove(List.of("expected_revision","reason"));
        var result=(ObjectNode)manifest; result.put("tenant_id",a.context().tenant_id()).put("capability_id",capability)
                .put("artifact_kind","STRICT_FIELD_TARGET").put("action","artifact.read").put("purpose",PURPOSE)
                .put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        return (ObjectNode)schema.validate("InputManifest",result);
    }

    private record Row(String id,String project,String author,String hash,JsonNode fixed) {
        JsonNode request(){return fixed.required("request");} JsonNode manifest(){return fixed.required("manifest");}
    }
    private record State(String status,String revision,String decisionId) { }
    private record Decision(String id,String hash,String actor,JsonNode body) { }

    private Row load(EnterpriseAccess.Admission a,String capability) throws Exception {
        var r=one(db.queryForList("SELECT * FROM "+CAPABILITY+" WHERE tenant_id=? AND capability_id=? FOR UPDATE",a.context().tenant_id(),capability));
        var fixed=schema.read(string(r,"fixed_json")); var request=schema.validate("InputAuthor",fixed.required("request"));
        var manifest=schema.validate("InputManifest",fixed.required("manifest"));
        var expected=manifest(a,capability,request,time(r,"created_at")); expected.put("created_by",string(r,"created_by"));
        if(!expected.equals(manifest)||!string(r,"content_sha256").equals(schema.hash(manifest))
                ||!text(request,"producing_receipt_sha256").equals(text(fixed,"registration_receipt_sha256"))) mismatch();
        metadata(r,manifest); for(String field:DIRECT_COLUMNS) if(!string(r,field).equals(text(request,field))) mismatch();
        if(!string(r,"mapping_revision").equals(text(request,"assignment_id"))||!string(r,"mapping_sha256").equals(text(request,"assignment_sha256"))
                ||!string(r,"role_version").equals(text(request.required("role_version"),"version_id"))
                ||!string(r,"role_sha256").equals(text(request.required("role_version"),"content_sha256"))
                ||!string(r,"policy_version").equals(text(request.required("execution_policy"),"version_id"))
                ||!string(r,"policy_sha256").equals(text(request.required("execution_policy"),"content_sha256"))
                ||!schema.read(string(r,"occurrence_ids")).equals(request.required("occurrence_ids"))) mismatch();
        var row=new Row(capability,text(request,"project_id"),text(manifest,"created_by"),string(r,"content_sha256"),fixed);
        requireHistoricalGrant(a,row.author(),text(fixed,"identity_mode"),fixed.required("grant"),request,WRITE,Instant.parse(text(manifest,"created_at")));
        var version=one(permissions.basis(capability));
        if(!row.hash().equals(version.sha256())||!row.project().equals(version.ownerProjectId())) mismatch();
        String entity=one(db.query("SELECT entity_kind FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",
                (rs,n)->rs.getString(1),a.context().tenant_id(),capability));
        if(!"TASK_FIELD_TARGET_INPUT_CAPABILITY".equals(entity)) mismatch();
        var actualParents=db.query("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? AND relation_kind=? AND actor_id=? ORDER BY upstream_version_id",
                (rs,n)->rs.getString(1),a.context().tenant_id(),capability,LINEAGE,row.author());
        if(!List.copyOf(parents(request)).equals(actualParents)||one(db.query("SELECT COUNT(*) FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=?",(rs,n)->rs.getInt(1),a.context().tenant_id(),capability))!=actualParents.size()) mismatch();
        authority(r,fixed); verifyJournal(a,row,r,"AUTHOR",request,fixed);
        var receipt=schema.read(string(r,"receipt_json"));
        if(!"DRAFT".equals(text(receipt,"status"))||!"1".equals(text(receipt,"revision"))||!receipt.required("decision_id").isNull()) mismatch();
        return row;
    }

    private State state(EnterpriseAccess.Admission a,Row row) throws Exception {
        var r=one(db.queryForList("SELECT * FROM "+STATE+" WHERE tenant_id=? AND capability_id=? FOR UPDATE",a.context().tenant_id(),row.id()));
        metadata(r,row.manifest()); if(time(r,"updated_at").isBefore(time(r,"created_at"))) mismatch();
        String status=string(r,"status"),revision=string(r,"revision"); String decision=nullable(r,"latest_decision_id");
        if(!Set.of("DRAFT","APPROVED","DISABLED").contains(status)||("DRAFT".equals(status)?!"1".equals(revision)||decision!=null:decision==null)
                ||"APPROVED".equals(status)&&!"2".equals(revision)||"DISABLED".equals(status)&&!Set.of("2","3").contains(revision)) mismatch();
        if(decision!=null) { var d=readDecision(a,row,decision); if(!status.equals(text(d.body(),"status"))||!revision.equals(text(d.body(),"revision"))) mismatch(); }
        return new State(status,revision,decision);
    }

    private Decision readDecision(EnterpriseAccess.Admission a,Row row,String id) throws Exception {
        var r=one(db.queryForList("SELECT * FROM "+DECISION+" WHERE tenant_id=? AND capability_id=? AND decision_id=? FOR UPDATE",a.context().tenant_id(),row.id(),id));
        var body=schema.read(string(r,"decision_json")); var request=schema.validate("InputDecide",body.required("request"));
        metadata(r,body,row.request());
        if(!"1.0.0".equals(text(body,"schema_version"))||!row.id().equals(text(body,"capability_id"))||!row.hash().equals(text(body,"capability_sha256"))
                ||!id.equals(text(body,"decision_id"))||!string(r,"content_sha256").equals(schema.hash(body))
                ||!string(r,"expected_revision").equals(text(request,"expected_revision"))
                ||!string(r,"decision").equals(text(request,"decision"))||!row.hash().equals(text(request,"capability_sha256"))
                ||!row.project().equals(text(request,"project_id"))
                ||!text(body,"previous_revision").equals(text(request,"expected_revision"))
                ||!RunCreateSchema.next(text(body,"previous_revision")).equals(text(body,"revision"))) mismatch();
        requireDecision(text(body,"previous_status"),text(request,"decision"),row.author(),text(body,"created_by"));
        if(!("DRAFT".equals(text(body,"previous_status"))?"1":"2").equals(text(body,"previous_revision"))) mismatch();
        if(!("APPROVE".equals(text(request,"decision"))?"APPROVED":"DISABLED").equals(text(body,"status"))) mismatch();
        requireHistoricalGrant(a,text(body,"created_by"),text(body,"identity_mode"),body.required("grant"),row.request(),decisionAction(request),Instant.parse(text(body,"created_at")));
        authority(r,body); verifyJournal(a,row,r,"DECIDE",request,body);
        var receipt=schema.read(string(r,"receipt_json"));
        if(!id.equals(text(receipt,"decision_id"))||!text(body,"status").equals(text(receipt,"status"))||!text(body,"revision").equals(text(receipt,"revision"))) mismatch();
        return new Decision(id,string(r,"content_sha256"),string(r,"created_by"),body);
    }

    /** Only the exact persisted actor, identity mode and immutable original grant are accepted. */
    private void requireCurrentActor(EnterpriseAccess.Admission a,String actor,String mode,JsonNode original,
            JsonNode request,String action) throws Exception {
        var member=one(permissions.targetMember(actor)); var binding=one(permissions.targetBinding(actor)); var now=permissions.databaseTime();
        if(!"ACTIVE".equals(member.status())||!actor.equals(member.userId())||!a.context().tenant_id().equals(member.tenantId())
                ||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)
                ||!"ACTIVE".equals(binding.status())||!actor.equals(binding.userId())||!mode.equals(binding.mode())
                ||!Set.of("local","oidc").contains(mode)||"local".equals(mode)&&!actor.equals(binding.localUserId())
                ||"oidc".equals(mode)&&binding.localUserId()!=null
                ||!identities.active(new AuthenticatedIdentityBinding.View(mode,actor,binding.issuer(),binding.subject()))) denied();
        var grant=one(permissions.currentGrant(text(original,"grant_id")));
        // Numeric node classes change on JSON decoding; typed equality preserves exact BigInteger revisions.
        if(!grant.equals(TaskFieldAuthorityWitness.grant(original))) stale();
        grants.requireResource(taskScope(a,request),actor,mode,action,PURPOSE,List.of(grant),now);
    }

    private void requireHistoricalGrant(EnterpriseAccess.Admission a,String actor,String mode,JsonNode witness,
            JsonNode request,String action,Instant at) throws Exception {
        grants.requireResource(taskScope(a,request),actor,mode,action,PURPOSE,List.of(TaskFieldAuthorityWitness.grant(witness)),LocalDateTime.ofInstant(at,ZoneOffset.UTC));
    }

    private JsonNode recover(EnterpriseAccess.Admission a,String key,String kind,JsonNode request,String expectedCapability) throws Exception {
        var rows=db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? FOR UPDATE",
                a.context().tenant_id(),a.actorId(),OPERATION+kind,key);
        if(rows.isEmpty()) return null; var operation=one(rows);
        if(!schema.hash(request).equals(string(operation,"request_sha256"))) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        if(!"COMPLETED".equals(string(operation,"state"))||!"200".equals(string(operation,"http_status"))
                ||!schema.manifest().equals(string(operation,"schema_manifest_sha256"))) mismatch();
        String id=string(operation,"target_id");
        if(expectedCapability!=null&&!expectedCapability.equals(id)) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        var row=load(a,id); var receipt=schema.validate("InputReceipt",schema.read(string(operation,"response_ref")));
        if(!id.equals(text(receipt,"capability_id"))) mismatch();
        if("AUTHOR".equals(kind)) {
            if(!row.author().equals(a.actorId())||!request.equals(row.request())) mismatch();
            var original=one(db.queryForList("SELECT receipt_json,idempotency_key FROM "+CAPABILITY+" WHERE tenant_id=? AND capability_id=? FOR UPDATE",a.context().tenant_id(),id));
            if(!key.equals(string(original,"idempotency_key"))||!receipt.equals(schema.read(string(original,"receipt_json")))) mismatch();
        } else {
            String decisionId=text(receipt,"decision_id"); var decision=readDecision(a,row,decisionId);
            if(!decision.actor().equals(a.actorId())||!request.equals(decision.body().required("request"))) mismatch();
            var original=one(db.queryForList("SELECT receipt_json,idempotency_key FROM "+DECISION+" WHERE tenant_id=? AND capability_id=? AND decision_id=? FOR UPDATE",a.context().tenant_id(),id,decisionId));
            if(!key.equals(string(original,"idempotency_key"))||!receipt.equals(schema.read(string(original,"receipt_json")))) mismatch();
        }
        // Return the immutable original receipt, not current status. In particular approval replay cannot re-enable.
        return receipt;
    }

    private void journal(EnterpriseAccess.Admission a,String key,String kind,String capability,JsonNode request,
            JsonNode receipt,JsonNode witness) throws Exception {
        String action="AUTHOR".equals(kind)?WRITE:decisionAction(request),operation=text(receipt,"operation_id");
        var now=LocalDateTime.ofInstant(Instant.parse(text(receipt,"created_at")),ZoneOffset.UTC);
        changed(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,target_id,error_code,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',200,?,?,NULL,'1.0.0',?,?,?,?,1,?)",
                a.context().tenant_id(),operation,a.actorId(),OPERATION+kind,key,schema.hash(request),schema.canonical(receipt),capability,
                schema.manifest(),a.actorId(),now,text(receipt,"classification"),now));
        changed(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',NULL,?,?,'1.0.0',?,?,?,?)",
                a.context().tenant_id(),text(receipt,"project_id"),text(receipt,"audit_id"),a.actorId(),action,schema.canonical(witness),
                operation,operation,schema.manifest(),a.actorId(),now,text(receipt,"classification")));
        changed(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at) VALUES(?,?,?,?,?,?,?,NULL)",
                a.context().tenant_id(),text(receipt,"event_id"),capability,text(receipt,"revision"),action,schema.canonical(witness),now));
    }

    private void verifyJournal(EnterpriseAccess.Admission a,Row row,Map<String,Object> record,String kind,
            JsonNode request,JsonNode witness) throws Exception {
        var receipt=schema.validate("InputReceipt",schema.read(string(record,"receipt_json")));
        String action="AUTHOR".equals(kind)?WRITE:decisionAction(request);
        for(String field:List.of("operation_id","audit_id","event_id","request_sha256","schema_manifest_sha256","created_by","classification"))
            if(!string(record,field).equals(text(receipt,field))) mismatch();
        if(!a.context().tenant_id().equals(text(receipt,"tenant_id"))||!row.project().equals(text(receipt,"project_id"))
                ||!text(row.request(),"task_id").equals(text(receipt,"task_id"))||!row.id().equals(text(receipt,"capability_id"))
                ||!row.hash().equals(text(receipt,"capability_sha256"))||!schema.hash(request).equals(text(receipt,"request_sha256"))
                ||!time(record,"created_at").toInstant(ZoneOffset.UTC).equals(Instant.parse(text(receipt,"created_at")))) mismatch();
        var operation=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? FOR UPDATE",
                a.context().tenant_id(),text(receipt,"operation_id"),text(receipt,"created_by"),OPERATION+kind,string(record,"idempotency_key")));
        if(!"COMPLETED".equals(string(operation,"state"))||!"200".equals(string(operation,"http_status"))
                ||!row.id().equals(string(operation,"target_id"))||!text(receipt,"request_sha256").equals(string(operation,"request_sha256"))
                ||!schema.manifest().equals(string(operation,"schema_manifest_sha256"))||!"1.0.0".equals(string(operation,"schema_version"))
                ||!text(receipt,"created_by").equals(string(operation,"created_by"))
                ||!text(receipt,"classification").equals(string(operation,"classification"))
                ||!time(record,"created_at").equals(time(operation,"created_at"))
                ||!time(record,"created_at").equals(time(operation,"updated_at"))
                ||!"1".equals(string(operation,"revision"))||operation.get("error_code")!=null
                ||!receipt.equals(schema.read(string(operation,"response_ref")))) mismatch();
        var audit=one(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=? FOR UPDATE",
                a.context().tenant_id(),text(receipt,"audit_id")));
        var event=one(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=? FOR UPDATE",
                a.context().tenant_id(),text(receipt,"event_id")));
        for(String field:List.of("tenant_id","project_id","created_by","classification","schema_manifest_sha256"))
            if(!text(receipt,field).equals(string(audit,field))) mismatch();
        if(!"1.0.0".equals(string(audit,"schema_version"))||!text(receipt,"created_by").equals(string(audit,"subject_id"))
                ||!action.equals(string(audit,"action"))||!"SUCCESS".equals(string(audit,"result"))||audit.get("reason_code")!=null
                ||!text(receipt,"operation_id").equals(string(audit,"request_id"))||!text(receipt,"operation_id").equals(string(audit,"trace_id"))
                ||!time(record,"created_at").equals(time(audit,"created_at"))||!witness.equals(schema.read(string(audit,"resource_ref")))) mismatch();
        if(!a.context().tenant_id().equals(string(event,"tenant_id"))||!row.id().equals(string(event,"aggregate_id"))
                ||!text(receipt,"revision").equals(string(event,"aggregate_revision"))||!action.equals(string(event,"event_type"))
                ||!time(record,"created_at").equals(time(event,"occurred_at"))||!witness.equals(schema.read(string(event,"payload")))) mismatch();
    }

    private JsonNode receipt(EnterpriseAccess.Admission a,JsonNode pins,String capability,String hash,String decision,
            String status,String revision,String operation,String audit,String event,JsonNode request,LocalDateTime now) throws Exception {
        var result=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id())
                .put("project_id",text(pins,"project_id")).put("task_id",text(pins,"task_id"))
                .put("operation_id",operation).put("audit_id",audit).put("event_id",event)
                .put("request_sha256",schema.hash(request)).put("schema_manifest_sha256",schema.manifest())
                .put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString())
                .put("classification",text(pins,"classification")).put("capability_id",capability)
                .put("capability_sha256",hash).put("status",status).put("revision",revision);
        result.set("decision_id",json.tree(decision)); return schema.validate("InputReceipt",result);
    }
    private LinkedHashMap<String,Object> metadata(EnterpriseAccess.Admission a,JsonNode request,LocalDateTime now) {
        var result=new LinkedHashMap<String,Object>(); result.put("tenant_id",a.context().tenant_id());
        result.put("project_id",text(request,"project_id")); result.put("task_id",text(request,"task_id"));
        result.put("schema_version","1.0.0"); result.put("schema_manifest_sha256",schema.manifest());
        result.put("created_by",a.actorId()); result.put("created_at",now); result.put("classification",text(request,"classification")); return result;
    }
    private void metadata(Map<String,Object> row,JsonNode manifest) { metadata(row,manifest,manifest); }
    private void metadata(Map<String,Object> row,JsonNode body,JsonNode pins) {
        if(!"1.0.0".equals(string(row,"schema_version"))||!schema.manifest().equals(string(row,"schema_manifest_sha256"))
                ||!string(row,"tenant_id").equals(text(body,"tenant_id"))||!string(row,"project_id").equals(text(pins,"project_id"))
                ||!string(row,"task_id").equals(text(pins,"task_id"))||!string(row,"created_by").equals(text(body,"created_by"))
                ||!string(row,"classification").equals(text(pins,"classification"))
                ||!time(row,"created_at").toInstant(ZoneOffset.UTC).equals(Instant.parse(text(body,"created_at")))) mismatch();
    }
    private void records(Map<String,Object> row,String key,String operation,String audit,String event,JsonNode receipt,JsonNode request) throws Exception {
        row.put("request_sha256",schema.hash(request)); row.put("idempotency_key",key); row.put("operation_id",operation);
        row.put("audit_id",audit); row.put("event_id",event); row.put("receipt_json",schema.canonical(receipt));
    }
    private void authority(Map<String,Object> row,EnterpriseAccess.Admission a,PermissionProtocol.GrantRow grant) {
        row.put("authority_grant",grant.grantId()); row.put("authority_revision",grant.revision().toString());
        row.put("auth_epoch",a.watermarks().authEpoch().toString()); row.put("governance_epoch",a.watermarks().governanceEpoch().toString());
    }
    private void authority(Map<String,Object> row,JsonNode witness) {
        var grant=witness.required("grant");
        if(!string(row,"authority_grant").equals(text(grant,"grant_id"))
                ||!string(row,"authority_revision").equals(grant.required("revision").asText())) mismatch();
        if(!string(row,"auth_epoch").equals(text(witness,"auth_epoch"))
                ||!string(row,"governance_epoch").equals(text(witness,"governance_epoch"))) mismatch();
        WireValues.revision(string(row,"auth_epoch")); WireValues.revision(string(row,"governance_epoch"));
    }
    private void insert(String table,LinkedHashMap<String,Object> row) {
        if(!Set.of(CAPABILITY,STATE,DECISION).contains(table)) throw new IllegalArgumentException("Unsupported capability table");
        String sql="INSERT INTO "+table+"("+String.join(",",row.keySet())+") VALUES("+String.join(",",Collections.nCopies(row.size(),"?"))+")";
        changed(db.update(sql,row.values().toArray()));
    }
    private SortedSet<String> parents(JsonNode request) {
        return new TreeSet<>(List.of(text(request,"task_revision"),text(request,"plan_revision"),text(request,"template_version"),
                text(request,"assignment_id"),text(request,"artifact_id"),text(request,"release_id"),
                text(request.required("role_version"),"version_id"),text(request.required("execution_policy"),"version_id")));
    }
    private void advance(EnterpriseAccess.Admission a) {
        RunCreateSchema.next(a.watermarks().governanceEpoch().toString());
        changed(db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=? AND auth_epoch=? AND governance_epoch=?",
                a.context().tenant_id(),a.watermarks().authEpoch(),a.watermarks().governanceEpoch()));
    }
    static void requireDecision(String status,String decision,String author,String actor) {
        if(!Set.of("APPROVE","DISABLE").contains(decision)) mismatch();
        if("APPROVE".equals(decision)) { if(!"DRAFT".equals(status)) conflict(); if(Objects.equals(author,actor)) denied(); }
        else if(!Set.of("DRAFT","APPROVED").contains(status)) conflict();
    }
    static void requireAdmission(EnterpriseAccess.Admission a) {
        if(a==null||!TransactionSynchronizationManager.isActualTransactionActive()||TransactionSynchronizationManager.getResourceMap().isEmpty()) denied();
        var scope=TenantScope.requireCurrent();
        if(!scope.tenantId().equals(a.context().tenant_id())||!scope.subjectId().equals(a.actorId())||a.simulationWorker()) denied();
    }
    private static void human(EnterpriseAccess.Admission a,String context) {
        requireAdmission(a);
        if(a.formalWorker()||a.controlWorker()||a.recoveryWorker()) denied();
        if(!a.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static void requireRouteTask(Row row,String task) { if(task!=null&&!task.equals(text(row.request(),"task_id"))) denied(); }
    private static void requireRequestProject(Row row,JsonNode request) { if(!row.project().equals(text(request,"project_id"))) denied(); }
    private static PermissionProtocol.Scope taskScope(EnterpriseAccess.Admission a,JsonNode request) {
        return new PermissionProtocol.Scope(a.context().tenant_id(),text(request,"project_id"),"TASK",text(request,"task_id"));
    }
    private static String decisionAction(JsonNode request) { return "DISABLE".equals(text(request,"decision"))?DISABLE:APPROVE; }
    private static void key(String key) { if(key==null||!key.matches("[!-~]{16,128}")) mismatch(); }
    private static String text(JsonNode node,String field) { var value=node.required(field); if(!value.isTextual()) mismatch(); return value.textValue(); }
    private static String string(Map<String,Object> row,String field) { var value=row.get(field); if(value==null) mismatch(); return value.toString(); }
    private static String nullable(Map<String,Object> row,String field) { var value=row.get(field); return value==null?null:value.toString(); }
    private static LocalDateTime time(Map<String,Object> row,String field) { var value=row.get(field); if(value instanceof java.sql.Timestamp t) return t.toLocalDateTime(); if(value instanceof LocalDateTime t) return t; mismatch(); return null; }
    private static String id(){return UUID.randomUUID().toString();}
    private static <T>T checked(ThrowingSupplier<T> operation){return operation.get();}
    private static <T>T one(List<T> rows){if(rows.size()!=1) mismatch();return rows.get(0);}
    private static void changed(int rows){if(rows!=1) conflict();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void conflict(){throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
}
