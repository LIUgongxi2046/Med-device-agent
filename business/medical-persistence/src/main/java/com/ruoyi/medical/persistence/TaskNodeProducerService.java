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

/** Independently approved bounded N02 producer rule for one exact confirmed task plan.
 * This service does not grant task.read/task.run, alter ROLE/078/093, or create a future-artifact selector.
 * There is intentionally no component annotation or runtime consumer wiring in this implementation. */
public final class TaskNodeProducerService {
    public static final String PURPOSE = "TASK_NODE_PRODUCTION";
    public static final String WRITE = "task.node_producer.write";
    public static final String APPROVE = "task.node_producer.approve";
    public static final String DISABLE = "task.node_producer.disable";
    public static final String READ = "task.node_producer.read";
    private final String CAPABILITY;
    private final String STATE;
    private final String DECISION;
    private final String OPERATION;
    private final TaskNodeProducerProfile profile;
    private static final String LINEAGE = "TASK_NODE_PRODUCTION";
    private static final List<String> PINS = List.of("task_revision", "task_input_sha256", "plan_revision",
            "plan_sha256", "template_version", "template_sha256");
    private static final List<String> DIRECT_COLUMNS = List.of("project_id", "task_id", "task_revision",
        "task_input_sha256", "plan_revision", "plan_sha256", "template_version", "template_sha256",
        "scope_snapshot", "source_manifest_sha256", "release_id", "publication_approval_sha256",
        "original_rule_id", "original_rule_sha256", "original_approval_sha256", "budget_revision",
        "maximum_bytes", "maximum_artifacts", "maximum_tokens", "maximum_calls", "maximum_amount", "currency");

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
    private final TaskNodeProducerOriginalAuthority originals;
    private final TaskNodeProducerSchema schema;
    private final PermissionSchema json;

    public TaskNodeProducerService(EnterpriseAccess access, DataSource data,
            AgentPublicationAuthority publications, AgentExecutionPolicyAuthority policies,
            FormalCapabilityIdentityAuthority roles, TaskService tasks, TaskPlanService plans,
            PermissionMapper permissions, PermissionAuthority grants, PlatformMemberIdentityStatus identities,
            ResourceReadAuthority resources, TaskNodeProducerOriginalAuthority originals,
            TaskNodeProducerSchema schema, PermissionSchema json) {
        this.profile=schema.profile();this.CAPABILITY=profile.table()+"rule";this.STATE=profile.table()+"state";this.DECISION=profile.table()+"decision";this.OPERATION=profile.operation();
        this.access=Objects.requireNonNull(access); this.db=new JdbcTemplate(Objects.requireNonNull(data));
        db.setQueryTimeout(5); this.publications=Objects.requireNonNull(publications); this.policies=Objects.requireNonNull(policies);
        this.roles=Objects.requireNonNull(roles); this.tasks=Objects.requireNonNull(tasks); this.plans=Objects.requireNonNull(plans);
        this.permissions=Objects.requireNonNull(permissions); this.grants=Objects.requireNonNull(grants);
        this.identities=Objects.requireNonNull(identities); this.resources=Objects.requireNonNull(resources);
        this.originals=Objects.requireNonNull(originals); this.schema=Objects.requireNonNull(schema); this.json=Objects.requireNonNull(json);
    }

    public JsonNode originalRules(ApplicationSessionIdentity.Verified subject,String context,String taskId)throws Exception {
        WireValues.uuid(taskId);
        String release=access.execute(subject,a->checked(()->{
            human(a,context);var task=tasks.fixedTask(a,taskId);
            resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),task.head().projectId(),"TASK",taskId),READ,PURPOSE);
            return task.head().agentReleaseId();
        }));
        var prepared=publications.prepare(subject,context,release,"RUN_CREATION",false);
        return access.execute(subject,a->checked(()->{
            human(a,context);var task=tasks.fixedTask(a,taskId);var head=task.head();
            resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),head.projectId(),"TASK",taskId),READ,PURPOSE);
            if(!release.equals(head.agentReleaseId()))stale();
            String classification=one(db.query("SELECT classification FROM task WHERE tenant_id=? AND project_id=? AND task_id=? FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),head.projectId(),taskId));
            var publication=publications.require(a,release,"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),head.projectId(),classification,"RUN_CREATION");
            var result=json.object().put("task_id",taskId).put("project_id",head.projectId()).put("release_id",release);
            result.set("items",originals.choices(a,head.projectId(),classification,publication,policy));return result;
        }));
    }

    /** Read-only proposal for an explicitly selected original rule. No authoring choices are selected. */
    public JsonNode preparation(ApplicationSessionIdentity.Verified subject,String context,String taskId,String originalRuleId)throws Exception {
        WireValues.uuid(taskId);WireValues.uuid(originalRuleId);
        String release=access.execute(subject,a->checked(()->{
            human(a,context);var task=tasks.fixedTask(a,taskId);
            resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),task.head().projectId(),"TASK",taskId),READ,PURPOSE);
            return task.head().agentReleaseId();
        }));
        var prepared=publications.prepare(subject,context,release,"RUN_CREATION",false);
        return access.execute(subject,a->checked(()->{
            human(a,context);var task=tasks.fixedTask(a,taskId);var head=task.head();
            resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),head.projectId(),"TASK",taskId),READ,PURPOSE);
            if(!release.equals(head.agentReleaseId())||head.planRevision()==null)stale();
            String classification=one(db.query("SELECT classification FROM task WHERE tenant_id=? AND project_id=? AND task_id=? FOR UPDATE",
                (r,n)->r.getString(1),a.context().tenant_id(),head.projectId(),taskId));
            var publication=publications.require(a,release,"RUN_CREATION",prepared);
            var policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),head.projectId(),classification,"RUN_CREATION");
            var original=originals.preparation(a,head.projectId(),originalRuleId,publication,policy);var request=original.required("request");
            requireClassification(text(request,"classification"),classification);
            var role=json.basis(request.required("role_version"));
            if(policy.definition().bindings().stream().filter(n->profile.node().equals(n.nodeKind())&&role.equals(n.roleVersion())).count()!=1)denied();
            var plan=plans.readFixed(a,task,head.planRevision(),true);
            if(!"CONFIRMED".equals(plan.status()))stale();plans.execution(a,task,plan);
            var pins=json.object();pins.put("schema_version","1.0.0").put("project_id",head.projectId()).put("task_id",taskId)
                .put("task_revision",head.taskRevision()).put("task_input_sha256",task.input().requestHash())
                .put("plan_revision",plan.planRevision()).put("plan_sha256",plan.contentSha256())
                .put("template_version",head.templateVersion()).put("scope_snapshot",head.snapshotId()).put("classification",classification);
            String templateSha256=one(db.query("SELECT content_sha256 FROM template_version WHERE tenant_id=? AND template_version=? FOR UPDATE",
                (r,n)->r.getString(1),a.context().tenant_id(),head.templateVersion()));
            pins.put("template_sha256",templateSha256);
            var snapshot=one(db.queryForList("SELECT s.manifest_sha256,v.content_sha256 FROM scope_snapshot s JOIN version_registry v ON v.tenant_id=s.tenant_id AND v.version_id=s.snapshot_id AND v.owner_project_id=s.project_id WHERE s.tenant_id=? AND s.project_id=? AND s.snapshot_id=? FOR UPDATE",a.context().tenant_id(),head.projectId(),head.snapshotId()));
            if(!string(snapshot,"manifest_sha256").equals(string(snapshot,"content_sha256")))mismatch();
            pins.put("source_manifest_sha256",string(snapshot,"manifest_sha256"));
            for(String field:List.of("release_id","publication_approval_sha256","role_version","execution_policy","budget_revision","currency"))pins.set(field,request.required(field).deepCopy());
            for(String field:List.of("original_rule_id","original_rule_sha256","original_approval_sha256"))pins.set(field,original.required(field).deepCopy());
            var questions=db.queryForList("SELECT q.question_id,r.question_key,r.question_key_sha256,q.question,q.section_id FROM plan_question q JOIN research_question r ON r.tenant_id=q.tenant_id AND r.project_id=q.project_id AND r.task_id=q.task_id AND r.question_id=q.question_id WHERE q.tenant_id=? AND q.project_id=? AND q.task_id=? AND q.plan_revision=? ORDER BY q.position FOR UPDATE",a.context().tenant_id(),head.projectId(),taskId,plan.planRevision());
            if(questions.size()!=plan.content().questions().size()||questions.size()>256)mismatch();
            var ids=pins.putArray(profile.scope());
            for(int i=0;i<questions.size();i++){
                var q=questions.get(i);var expected=plan.content().questions().get(i);
                if(!expected.questionKey().equals(string(q,"question_key"))||!expected.question().equals(string(q,"question"))||!expected.sectionId().equals(string(q,"section_id"))||!org.apache.commons.codec.digest.DigestUtils.sha256Hex(expected.questionKey()).equals(string(q,"question_key_sha256")))mismatch();
                if(profile==TaskNodeProducerProfile.RESEARCH)ids.add(string(q,"question_id"));
            }
            if(profile!=TaskNodeProducerProfile.RESEARCH)plan.content().sections().forEach(s->ids.add(s.sectionId()));
            requireCoverage(ids,profile==TaskNodeProducerProfile.RESEARCH?questions.stream().map(q->string(q,"question_id")).toList():plan.content().sections().stream().map(s->s.sectionId()).toList());
            var ceilings=json.object();for(String field:List.of("maximum_tokens","maximum_calls","maximum_amount","valid_from","valid_until"))ceilings.set(field,request.required(field).deepCopy());
            ceilings.put("maximum_artifacts","1024");
            var result=json.object();result.set("pins",pins);result.set("ceilings",ceilings);result.set("choices",preparationChoices(json,profile));
            publication.requireUnchanged();policy.requireUnchanged();return result;
        }));
    }
    static JsonNode preparationChoices(PermissionSchema json){return preparationChoices(json,TaskNodeProducerProfile.RESEARCH);}
    static JsonNode preparationChoices(PermissionSchema json,TaskNodeProducerProfile profile){
        var choices=json.object();var kinds=choices.putArray("artifact_kinds");profile.kinds().stream().sorted().forEach(kinds::add);
        var operations=choices.putArray("operations");(profile==TaskNodeProducerProfile.ASSEMBLY?Set.of("NODE_PRODUCE","HOST_PREDECESSOR_CONSUME"):TaskNodeProducerProtocol.OPERATIONS).stream().sorted().forEach(operations::add);
        var required=choices.putArray("required_selections");for(String name:List.of("maximum_bytes","maximum_artifacts","maximum_tokens","maximum_calls","maximum_amount","valid_from","valid_until","artifact_kinds","operations","reason","enterprise_budget_basis"))required.add(name);
        choices.put("enterprise_budget_guidance","Select an existing approved enterprise budget revision and its content hash. No enterprise budget is inferred from project budget history; this selection does not grant reservation or dispatch authority.");
        return choices;
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
            fixed.put("original_authority_sha256",registration.sha256());
            fixed.set("enterprise_budget_authority",requireEnterpriseBudget(a,request,true));
            fixed.put("auth_epoch",a.watermarks().authEpoch().toString()).put("governance_epoch",a.watermarks().governanceEpoch().toString());
            String hash=schema.hash(manifest);
            var receipt=receipt(a,request,capability,hash,null,"DRAFT","1",operation,audit,event,request,now);
            changed(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,'TASK_NODE_PRODUCER_RULE',?)",
                    a.context().tenant_id(),text(request,"project_id"),capability,hash));
            journal(a,key,"AUTHOR",capability,request,receipt,fixed);
            var row=metadata(a,request,now); row.put("rule_id",capability);
            for(String field:DIRECT_COLUMNS) row.put(field,text(request,field));
            row.put("role_version",text(request.required("role_version"),"version_id"));
            row.put("role_sha256",text(request.required("role_version"),"content_sha256"));
            row.put("policy_version",text(request.required("execution_policy"),"version_id"));
            row.put("policy_sha256",text(request.required("execution_policy"),"content_sha256"));
            row.put(profile.scope(),schema.canonical(request.required(profile.scope())));
            row.put("fixed_json",schema.canonical(fixed)); row.put("content_sha256",hash);
            records(row,key,operation,audit,event,receipt,request); authority(row,a,grant); insert(CAPABILITY,row);
            var state=metadata(a,request,now); state.put("rule_id",capability); state.put("status","DRAFT");
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
            if(!row.hash().equals(text(request,"rule_sha256"))) mismatch();
            var previous=state(a,row);
            if(!previous.revision().equals(text(request,"expected_revision"))) conflict();
            requireDecision(previous.status(),text(request,"decision"),row.author(),a.actorId());
            AgentPublicationAuthority.VerifiedPublication publication=null;
            AgentExecutionPolicyAuthority.VerifiedPolicy policy=null;
            TaskNodeProducerOriginalAuthority.Verified registration=null;
            if("APPROVE".equals(text(request,"decision"))) {
                publication=publications.require(a,text(row.request(),"release_id"),"RUN_CREATION",prepared);
                policy=policies.requireByVersion(a,publication.executionPolicyId(),publication.executionPolicySha256(),
                        row.project(),text(row.request(),"classification"),"RUN_CREATION");
                requireCurrentActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("grant"),row.request(),WRITE);
                registration=validatePins(a,row.request(),publication,policy,APPROVE);
                requireEnterpriseActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("enterprise_budget_authority"),row.request());
            }
            var now=permissions.databaseTime(); String decision=id(),operation=id(),audit=id(),event=id();
            String status="APPROVE".equals(text(request,"decision"))?"APPROVED":"DISABLED";
            String revision=RunCreateSchema.next(previous.revision());
            var body=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id())
                    .put("rule_id",row.id()).put("rule_sha256",row.hash()).put("decision_id",decision)
                    .put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString())
                    .put("identity_mode",a.bindingView().mode()).put("previous_status",previous.status())
                    .put("previous_revision",previous.revision()).put("status",status).put("revision",revision);
            body.set("request",request); body.set("grant",TaskFieldAuthorityWitness.tree(grant));
            if("APPROVED".equals(status))body.set("enterprise_budget_authority",requireEnterpriseBudget(a,row.request(),true));
            body.put("auth_epoch",a.watermarks().authEpoch().toString()).put("governance_epoch",a.watermarks().governanceEpoch().toString());
            var receipt=receipt(a,row.request(),row.id(),row.hash(),decision,status,revision,operation,audit,event,request,now);
            journal(a,key,"DECIDE",row.id(),request,receipt,body);
            var record=metadata(a,row.request(),now); record.put("rule_id",row.id()); record.put("decision_id",decision);
            record.put("decision_json",schema.canonical(body)); record.put("content_sha256",schema.hash(body));
            record.put("expected_revision",previous.revision()); record.put("decision",text(request,"decision"));
            records(record,key,operation,audit,event,receipt,request); authority(record,a,grant); insert(DECISION,record);
            changed(db.update("UPDATE "+STATE+" SET status=?,revision=?,latest_decision_id=?,updated_at=? WHERE tenant_id=? AND rule_id=? AND status=? AND revision=? AND latest_decision_id <=> ?",
                    status,revision,decision,now,a.context().tenant_id(),row.id(),previous.status(),previous.revision(),previous.decisionId()));
            readDecision(a,row,decision);
            if(registration!=null) { registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged(); }
            if(!grant.equals(resources.require(a,taskScope(a,row.request()),decisionAction(request),PURPOSE))) stale();
            advance(a); return receipt;
        }));
    }

    /** Governance view only. Reading a DRAFT/DISABLED record never produces a data-read proof. */
    /** Ordinary governance discovery only. This does not choose a rule or derive production authority. */
    public JsonNode list(ApplicationSessionIdentity.Verified subject,String context,String taskId)throws Exception {
        WireValues.uuid(taskId);
        return access.execute(subject,a->checked(()->{
            human(a,context);var task=tasks.fixedTask(a,taskId);var head=task.head();
            var scope=new PermissionProtocol.Scope(a.context().tenant_id(),head.projectId(),"TASK",taskId);
            var grant=resources.require(a,scope,READ,PURPOSE);int limit=originals.candidateLimit();
            var candidates=db.query("SELECT rule_id FROM "+CAPABILITY+" WHERE tenant_id=? AND project_id=? AND task_id=? ORDER BY rule_id LIMIT ? FOR UPDATE",
                (r,i)->r.getString(1),a.context().tenant_id(),head.projectId(),taskId,limit+1);
            TaskNodeProducerOriginalAuthority.requireCandidateBound(candidates.size(),limit);
            var result=json.object().put("task_id",taskId).put("project_id",head.projectId());var items=result.putArray("items");
            for(String id:candidates){
                var row=load(a,id);requireRouteTask(row,taskId);if(!head.projectId().equals(row.project()))mismatch();
                // Same historical journal and current read checks as the single-rule route, not execution authority.
                var item=(ObjectNode)listItem(view(a,row),json);item.put("reason",text(row.request(),"reason"));items.add(item);
            }
            if(!grant.equals(resources.require(a,scope,READ,PURPOSE)))stale();return result;
        }));
    }
    static JsonNode listItem(JsonNode view,PermissionSchema json)throws Exception {
        var manifest=view.required("manifest");var item=json.object();
        item.put("rule_id",text(manifest,"rule_id")).put("rule_sha256",text(view,"rule_sha256"))
            .put("status",text(view,"status")).put("revision",text(view,"revision")).put("created_by",text(manifest,"created_by"));
        return item;
    }

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
        var result=json.object().put("schema_version","1.0.0").put("rule_sha256",row.hash())
                .put("status",current.status()).put("revision",current.revision());
        result.set("manifest",row.manifest().deepCopy()); result.set("latest_decision_id",json.tree(current.decisionId()));
        var actions=result.putArray("allowed_actions"); actions.add("READ");
        if("DRAFT".equals(current.status())&&!row.author().equals(a.actorId())&&resources.find(a,scope,APPROVE,PURPOSE).isPresent()) actions.add("APPROVE");
        if(!"DISABLED".equals(current.status())&&resources.find(a,scope,DISABLE,PURPOSE).isPresent()) actions.add("DISABLE");
        if(!grant.equals(resources.require(a,scope,READ,PURPOSE))) stale();
        return schema.validate("InputView",result);
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
        var inputGrant=a.controlWorker()?hostRead(a,row.request(),((FormalRunControlAccess.ReadIdentity)a.controlProof().identity()).runId()):resources.require(a,taskScope(a,row.request()),READ,PURPOSE);
        var current=state(a,row); if(!"APPROVED".equals(current.status())||current.decisionId()==null) denied();
        if(originalDecisionId!=null&&!originalDecisionId.equals(current.decisionId())) stale();
        var decision=readDecision(a,row,current.decisionId());
        if(!"APPROVED".equals(text(decision.body(),"status"))||!"APPROVE".equals(text(decision.body().required("request"),"decision"))
                ||!current.revision().equals(text(decision.body(),"revision"))||row.author().equals(decision.actor())) mismatch();
        if(originalDecisionHash!=null&&!originalDecisionHash.equals(decision.hash())) stale();
        requireCurrentActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("grant"),row.request(),WRITE);
        requireCurrentActor(a,decision.actor(),text(decision.body(),"identity_mode"),decision.body().required("grant"),row.request(),APPROVE);
        requireEnterpriseActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("enterprise_budget_authority"),row.request());
        requireEnterpriseActor(a,decision.actor(),text(decision.body(),"identity_mode"),decision.body().required("enterprise_budget_authority"),row.request());
        var registration=validatePins(a,row.request(),publication,policy,READ);
        var auth=json.object(); auth.set("capability",row.fixed()); auth.set("approval",decision.body());
        auth.set("input_read",TaskFieldAuthorityWitness.tree(inputGrant));
        auth.put("original_authority_sha256",registration.sha256());
        auth.set("publication",publication.authorizations()); auth.set("execution_policy",policy.authorizations());
        registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged();
        return new Checked(row.id(),row.hash(),decision.id(),decision.hash(),row.manifest().deepCopy(),auth,schema.hash(auth));
    }
    record Checked(String capabilityId,String capabilityHash,String decisionId,String decisionHash,JsonNode manifest,JsonNode authorizations,String fingerprint) { }

    /** Current governance recheck inside an already verified formal admission. This does not grant I/O. */
    Checked requireRegisteredRule(EnterpriseAccess.Admission a,String id,String hash,String decisionId,String decisionHash,JsonNode input)throws Exception {
        requireAdmission(a);if(!a.formalWorker()&&!a.controlWorker())denied();
        String taskId=text(input,"task_id"),runId=text(input,"run_id"),stepId=text(input,"step_id");
        if(a.controlWorker())a.controlProof().requireBootstrap(taskId,runId);
        else {var identity=a.formalProof().identity();if(!identity.run().equals(runId)||!identity.step().equals(stepId)
            ||!identity.project().equals(text(input,"project_id"))||!"TASK".equals(identity.executionKind()))denied();}
        var row=load(a,id);if(!row.hash().equals(hash)||!taskId.equals(text(row.request(),"task_id")))mismatch();
        var current=state(a,row);if(!"APPROVED".equals(current.status())||!Objects.equals(decisionId,current.decisionId()))denied();
        var decision=readDecision(a,row,decisionId);if(!decision.hash().equals(decisionHash))stale();
        requireCurrentActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("grant"),row.request(),WRITE);
        requireCurrentActor(a,decision.actor(),text(decision.body(),"identity_mode"),decision.body().required("grant"),row.request(),APPROVE);
        requireEnterpriseActor(a,row.author(),text(row.fixed(),"identity_mode"),row.fixed().required("enterprise_budget_authority"),row.request());
        requireEnterpriseActor(a,decision.actor(),text(decision.body(),"identity_mode"),decision.body().required("enterprise_budget_authority"),row.request());
        var now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
        if(now.isBefore(Instant.parse(text(row.request(),"valid_from")))||!now.isBefore(Instant.parse(text(row.request(),"valid_until"))))denied();
        for(String pin:List.of("project_id","task_id","plan_revision","plan_sha256","role_version"))
            if(!input.required(pin).equals(row.request().required(pin)))mismatch();
        if(!input.required("execution_policy_version").equals(row.request().required("execution_policy"))
            ||!text(input,"snapshot_id").equals(text(row.request(),"scope_snapshot"))
            ||!text(input,"snapshot_sha256").equals(text(row.request(),"source_manifest_sha256"))
            ||!coversInput(row.request(),input))mismatch();
        var runtime=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' FOR UPDATE",a.context().tenant_id(),row.project(),taskId,runId,a.actorId()));
        requireClassification(text(row.request(),"classification"),string(runtime,"classification"));
        requireReplayPins(row.manifest(),schema.read(string(runtime,"replay_manifest")));
        var grant=hostRead(a,row.request(),runId);
        var original=originals.requireCurrentIdentity(a,row.request());
        var enterprise=requireEnterpriseBudget(a,row.request(),false);
        var auth=json.object();auth.set("rule",row.fixed().deepCopy());auth.set("approval",decision.body().deepCopy());
        auth.set("original",original);auth.set("read_grant",TaskFieldAuthorityWitness.tree(grant));auth.set("enterprise_budget_authority",enterprise);
        return new Checked(id,hash,decisionId,decisionHash,row.manifest().deepCopy(),auth,schema.hash(auth));
    }
    private boolean coversInput(JsonNode rule,JsonNode input){
        if(profile==TaskNodeProducerProfile.RESEARCH)return TaskNodeProducerAuthority.contains(rule.required(profile.scope()),text(input,"question_id"));
        var sections=input.required("target_sections");if(!sections.isArray()||sections.isEmpty())return false;
        for(var section:sections)if(!TaskNodeProducerAuthority.contains(rule.required(profile.scope()),section.asText()))return false;return true;
    }
    /** Compare to the exact original N02 rule frozen in this real RN01 receipt; never choose another approved rule. */
    void requireReplayPins(JsonNode rule,JsonNode replay)throws Exception {
        var witness=schema.read(text(replay.required("authorizations"),"canonical_witnesses"));
        var original=witness.required("additional_authorizations").required(profile.node());
        if(!text(rule,"original_rule_id").equals(text(original.required("rule"),"rule_id"))
            ||!text(rule,"original_rule_sha256").equals(schema.hash(original.required("rule")))
            ||!text(rule,"original_approval_sha256").equals(schema.hash(original.required("approval")))
            ||!text(rule,"budget_revision").equals(text(replay,"budget_revision")))mismatch();
    }
    private PermissionProtocol.GrantRow hostRead(EnterpriseAccess.Admission a,JsonNode request,String run)throws Exception {
        if(!a.controlWorker()&&!a.formalWorker())return resources.require(a,taskScope(a,request),READ,PURPOSE);
        if(a.controlWorker())a.controlProof().requireBootstrap(text(request,"task_id"),run);
        else {var identity=a.formalProof().identity();if(!identity.run().equals(run)||!identity.project().equals(text(request,"project_id")))denied();}
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants");
        TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants",Map.of("userId",a.actorId()));
        return grants.requireResource(taskScope(a,request),a.actorId(),a.bindingView().mode(),READ,PURPOSE,
            permissions.resourceGrants(a.actorId(),READ),permissions.databaseTime());
    }

    private TaskNodeProducerOriginalAuthority.Verified validatePins(EnterpriseAccess.Admission a,JsonNode request,
            AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String action) throws Exception {
        schema.validate("InputAuthor",request);
        requireEnterpriseBudget(a,request,!READ.equals(action));
        publication.requireConsumer(a,text(request,"release_id"),"RUN_CREATION");
        policy.requireConsumer(a,text(request,"project_id"),"RUN_CREATION");
        if(!text(request,"publication_approval_sha256").equals(publication.approvalSha256())
                ||!text(request.required("execution_policy"),"version_id").equals(publication.executionPolicyId())
                ||!text(request.required("execution_policy"),"content_sha256").equals(publication.executionPolicySha256())
                ||!text(request.required("execution_policy"),"version_id").equals(policy.versionId())
                ||!text(request.required("execution_policy"),"content_sha256").equals(policy.contentSha256())) mismatch();
        var role=json.basis(request.required("role_version"));
        // Approval of 078 proves this role's N02 association, not permission for this new selector/purpose.
        if(policy.definition().bindings().stream().filter(n->profile.node().equals(n.nodeKind())&&role.equals(n.roleVersion())).count()!=1
                ||!policy.definition().allowedClassifications().contains(text(request,"classification"))) denied();
        var actualRole=roles.require(a,role,"ROLE",text(request,"project_id"),"RUN_CREATION");
        var task=tasks.fixedTask(a,text(request,"task_id")); var head=task.head();
        var actualClassification=one(db.query("SELECT classification FROM task WHERE tenant_id=? AND project_id=? AND task_id=? FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),head.projectId(),head.taskId()));
        requireClassification(text(request,"classification"),actualClassification);
        if(!head.projectId().equals(text(request,"project_id"))||!head.taskRevision().equals(text(request,"task_revision"))
                ||!task.input().requestHash().equals(text(request,"task_input_sha256"))
                ||!Objects.equals(head.planRevision(),text(request,"plan_revision"))
                ||!head.templateVersion().equals(text(request,"template_version"))
                ||!head.agentReleaseId().equals(text(request,"release_id"))) stale();
        var plan=plans.readFixed(a,task,text(request,"plan_revision"),true);
        if(!"CONFIRMED".equals(plan.status())||!plan.contentSha256().equals(text(request,"plan_sha256"))) stale();
        if(!head.snapshotId().equals(text(request,"scope_snapshot"))) stale();
        String templateHash=one(db.query("SELECT content_sha256 FROM template_version WHERE tenant_id=? AND template_version=? FOR UPDATE",
            (r,n)->r.getString(1),a.context().tenant_id(),head.templateVersion()));
        if(!templateHash.equals(text(request,"template_sha256")))stale();
        var snapshot=one(db.queryForList("SELECT s.manifest,s.manifest_sha256,v.content_sha256 FROM scope_snapshot s JOIN version_registry v ON v.tenant_id=s.tenant_id AND v.version_id=s.snapshot_id AND v.owner_project_id=s.project_id WHERE s.tenant_id=? AND s.project_id=? AND s.snapshot_id=? FOR UPDATE",
            a.context().tenant_id(),head.projectId(),head.snapshotId()));
        if(!text(request,"source_manifest_sha256").equals(string(snapshot,"manifest_sha256"))
                ||!string(snapshot,"manifest_sha256").equals(string(snapshot,"content_sha256")))stale();
        var questions=db.queryForList("SELECT q.question_id,r.question_key,r.question_key_sha256,q.question,q.section_id FROM plan_question q JOIN research_question r ON r.tenant_id=q.tenant_id AND r.project_id=q.project_id AND r.task_id=q.task_id AND r.question_id=q.question_id WHERE q.tenant_id=? AND q.project_id=? AND q.task_id=? AND q.plan_revision=? ORDER BY q.position FOR UPDATE",
            a.context().tenant_id(),head.projectId(),head.taskId(),plan.planRevision());
        if(questions.size()!=plan.content().questions().size())mismatch();
        for(int i=0;i<questions.size();i++) {
            var q=questions.get(i);var expected=plan.content().questions().get(i);
            if(!expected.questionKey().equals(string(q,"question_key"))||!expected.question().equals(string(q,"question"))
                    ||!expected.sectionId().equals(string(q,"section_id"))
                    ||!org.apache.commons.codec.digest.DigestUtils.sha256Hex(expected.questionKey()).equals(string(q,"question_key_sha256")))mismatch();
        }
        requireCoverage(request.required(profile.scope()),profile==TaskNodeProducerProfile.RESEARCH?questions.stream().map(q->string(q,"question_id")).toList():plan.content().sections().stream().map(s->s.sectionId()).toList());
        // readFixed above revalidates source membership and current source rights. No READY-only check on a running consumer.
        if(!READ.equals(action))plans.execution(a,task,plan);
        var registration=originals.require(a,request,publication,policy,!READ.equals(action));
        actualRole.requireUnchanged(); registration.requireUnchanged(); publication.requireUnchanged(); policy.requireUnchanged();
        return registration;
    }

    /** Governance-only enterprise selection. Shared 076 must independently authorize read/reserve and dispatch. */
    private JsonNode requireEnterpriseBudget(EnterpriseAccess.Admission a,JsonNode producer,boolean available)throws Exception {
        requireAdmission(a);var basis=json.basis(producer.required("enterprise_budget_basis"));
        var row=one(db.queryForList("SELECT r.tenant_id,r.project_id,r.budget_id,r.budget_revision,r.currency,r.limits,r.approver_id,r.created_by,r.reason,r.content_sha256,r.schema_version,r.schema_manifest_sha256,b.project_id AS account_project_id,b.currency AS account_currency,b.current_budget_revision,b.spent,b.reserved,b.spent_tokens,b.reserved_tokens,b.spent_calls,b.reserved_calls,v.owner_project_id,v.entity_kind,v.content_sha256 AS registry_sha256 FROM budget_revision r JOIN budget_account b ON b.tenant_id=r.tenant_id AND b.budget_id=r.budget_id JOIN version_registry v ON v.tenant_id=r.tenant_id AND v.version_id=r.budget_revision WHERE r.tenant_id=? AND r.budget_revision=? FOR UPDATE",a.context().tenant_id(),basis.version_id()));
        var budgets=new BudgetSchema(json);var approval=budgets.approval(string(row,"limits"));
        requireEnterprisePins(producer,row,approval,budgets.manifestSha256(),a.context().tenant_id(),json);
        var approvalBasis=one(permissions.basis(approval.approval_basis().version_id()));
        if(approvalBasis.ownerProjectId()!=null||!approvalBasis.sha256().equals(approval.approval_basis().content_sha256()))mismatch();
        var totals=json.object();for(String f:List.of("spent","reserved","spent_tokens","reserved_tokens","spent_calls","reserved_calls"))totals.put(f,string(row,f));
        if(available)RunCreateBudget.requireAvailable(totals,approval.limits(),permissions.databaseTime());else RunCreateBudget.requireWithinBounds(totals,approval.limits(),permissions.databaseTime());
        var scope=new PermissionProtocol.Scope(a.context().tenant_id(),null,"BUDGET",approval.budget_id());
        PermissionProtocol.GrantRow grant;
        if(a.formalWorker()||a.controlWorker()){
            if(a.formalWorker()){var identity=a.formalProof().identity();if(!"TASK".equals(identity.executionKind())||!identity.project().equals(text(producer,"project_id")))denied();}
            else {var identity=(FormalRunControlAccess.ReadIdentity)a.controlProof().identity();a.controlProof().requireBootstrap(text(producer,"task_id"),identity.runId());}
            TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants");
            TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.PermissionMapper.resourceGrants",Map.of("userId",a.actorId()));
            grant=grants.requireResource(scope,a.actorId(),a.bindingView().mode(),"budget.read","BUDGET_READING",permissions.resourceGrants(a.actorId(),"budget.read"),permissions.databaseTime());
        }else grant=resources.require(a,scope,"budget.read","BUDGET_READING");
        var witness=json.object();witness.set("basis",producer.required("enterprise_budget_basis").deepCopy());witness.put("budget_id",approval.budget_id());
        witness.set("approval",json.tree(approval));witness.set("read_grant",TaskFieldAuthorityWitness.tree(grant));return witness;
    }
    static void requireEnterprisePins(JsonNode producer,Map<String,Object> row,BudgetProtocol.Approval approval,String schemaManifest,String tenant,PermissionSchema json)throws Exception {
        var basis=json.basis(producer.required("enterprise_budget_basis"));
        String hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonicalObject(approval));
        if(!tenant.equals(string(row,"tenant_id"))||!tenant.equals(approval.tenant_id())||row.get("project_id")!=null||row.get("account_project_id")!=null||row.get("owner_project_id")!=null||approval.project_id()!=null
            ||!"BUDGET".equals(string(row,"entity_kind"))||!basis.version_id().equals(string(row,"budget_revision"))||!basis.version_id().equals(string(row,"current_budget_revision"))
            ||!basis.content_sha256().equals(hash)||!hash.equals(string(row,"content_sha256"))||!hash.equals(string(row,"registry_sha256"))
            ||!"1.0.0".equals(string(row,"schema_version"))||!schemaManifest.equals(string(row,"schema_manifest_sha256"))
            ||!approval.budget_id().equals(string(row,"budget_id"))||!approval.currency().equals(string(row,"currency"))||!approval.currency().equals(string(row,"account_currency"))||!approval.currency().equals(text(producer,"currency"))
            ||!approval.approver_id().equals(string(row,"approver_id"))||!approval.approver_id().equals(string(row,"created_by"))||!approval.reason().equals(string(row,"reason"))
            ||basis.version_id().equals(approval.based_on_budget_revision()))mismatch();
        if(new java.math.BigDecimal(text(producer,"maximum_amount")).compareTo(new java.math.BigDecimal(approval.limits().amount()))>0
            ||WireValues.revision(text(producer,"maximum_tokens")).compareTo(WireValues.revision(approval.limits().tokens()))>0
            ||WireValues.revision(text(producer,"maximum_calls")).compareTo(WireValues.revision(approval.limits().calls()))>0
            ||Instant.parse(text(producer,"valid_until")).isAfter(Instant.parse(approval.limits().deadline_at())))denied();
    }
    private void requireEnterpriseWitness(EnterpriseAccess.Admission a,String actor,String mode,JsonNode witness,JsonNode producer,LocalDateTime at)throws Exception {
        if(!witness.required("basis").equals(producer.required("enterprise_budget_basis")))mismatch();
        var budgets=new BudgetSchema(json);var approval=budgets.approval(json.canonical(witness.required("approval")));
        if(!a.context().tenant_id().equals(approval.tenant_id())||approval.project_id()!=null||!text(witness,"budget_id").equals(approval.budget_id())
            ||!text(witness.required("basis"),"content_sha256").equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonicalObject(approval))))mismatch();
        grants.requireResource(new PermissionProtocol.Scope(a.context().tenant_id(),null,"BUDGET",approval.budget_id()),actor,mode,"budget.read","BUDGET_READING",List.of(TaskFieldAuthorityWitness.grant(witness.required("read_grant"))),at);
    }
    private void requireEnterpriseActor(EnterpriseAccess.Admission a,String actor,String mode,JsonNode witness,JsonNode producer)throws Exception {
        requireEnterpriseWitness(a,actor,mode,witness,producer,permissions.databaseTime());
        var grant=one(permissions.currentGrant(text(witness.required("read_grant"),"grant_id")));
        if(!grant.equals(TaskFieldAuthorityWitness.grant(witness.required("read_grant"))))stale();
        grants.requireResource(new PermissionProtocol.Scope(a.context().tenant_id(),null,"BUDGET",text(witness,"budget_id")),actor,mode,"budget.read","BUDGET_READING",List.of(grant),permissions.databaseTime());
    }

    static void requireClassification(String approved,String actual){if(!Objects.equals(approved,actual)||actual==null)mismatch();}
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
        var result=(ObjectNode)manifest; result.put("tenant_id",a.context().tenant_id()).put("rule_id",capability)
                .put("producer_node_kind",profile.node()).put("purpose",PURPOSE)
                .put("created_by",a.actorId()).put("created_at",now.toInstant(ZoneOffset.UTC).toString());
        return (ObjectNode)schema.validate("InputManifest",result);
    }

    private record Row(String id,String project,String author,String hash,JsonNode fixed) {
        JsonNode request(){return fixed.required("request");} JsonNode manifest(){return fixed.required("manifest");}
    }
    private record State(String status,String revision,String decisionId) { }
    private record Decision(String id,String hash,String actor,JsonNode body) { }

    private Row load(EnterpriseAccess.Admission a,String capability) throws Exception {
        var r=one(db.queryForList("SELECT * FROM "+CAPABILITY+" WHERE tenant_id=? AND rule_id=? FOR UPDATE",a.context().tenant_id(),capability));
        var fixed=schema.read(string(r,"fixed_json")); var request=schema.validate("InputAuthor",fixed.required("request"));
        var manifest=schema.validate("InputManifest",fixed.required("manifest"));
        var expected=manifest(a,capability,request,time(r,"created_at")); expected.put("created_by",string(r,"created_by"));
        if(!expected.equals(manifest)||!string(r,"content_sha256").equals(schema.hash(manifest))
                ) mismatch();
        metadata(r,manifest); for(String field:DIRECT_COLUMNS) if("maximum_amount".equals(field)?new java.math.BigDecimal(string(r,field)).compareTo(new java.math.BigDecimal(text(request,field)))!=0:!string(r,field).equals(text(request,field))) mismatch();
        if(!string(r,"role_version").equals(text(request.required("role_version"),"version_id"))
                ||!string(r,"role_sha256").equals(text(request.required("role_version"),"content_sha256"))
                ||!string(r,"policy_version").equals(text(request.required("execution_policy"),"version_id"))
                ||!string(r,"policy_sha256").equals(text(request.required("execution_policy"),"content_sha256"))
                ||!schema.read(string(r,profile.scope())).equals(request.required(profile.scope()))) mismatch();
        var row=new Row(capability,text(request,"project_id"),text(manifest,"created_by"),string(r,"content_sha256"),fixed);
        requireHistoricalGrant(a,row.author(),text(fixed,"identity_mode"),fixed.required("grant"),request,WRITE,Instant.parse(text(manifest,"created_at")));
        requireEnterpriseWitness(a,row.author(),text(fixed,"identity_mode"),fixed.required("enterprise_budget_authority"),request,time(r,"created_at"));
        var version=one(permissions.basis(capability));
        if(!row.hash().equals(version.sha256())||!row.project().equals(version.ownerProjectId())) mismatch();
        String entity=one(db.query("SELECT entity_kind FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",
                (rs,n)->rs.getString(1),a.context().tenant_id(),capability));
        if(!"TASK_NODE_PRODUCER_RULE".equals(entity)) mismatch();
        var actualParents=db.query("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? AND relation_kind=? AND actor_id=? ORDER BY upstream_version_id",
                (rs,n)->rs.getString(1),a.context().tenant_id(),capability,LINEAGE,row.author());
        if(!List.copyOf(parents(request)).equals(actualParents)||one(db.query("SELECT COUNT(*) FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=?",(rs,n)->rs.getInt(1),a.context().tenant_id(),capability))!=actualParents.size()) mismatch();
        authority(r,fixed); verifyJournal(a,row,r,"AUTHOR",request,fixed);
        var receipt=schema.read(string(r,"receipt_json"));
        if(!"DRAFT".equals(text(receipt,"status"))||!"1".equals(text(receipt,"revision"))||!receipt.required("decision_id").isNull()) mismatch();
        return row;
    }

    private State state(EnterpriseAccess.Admission a,Row row) throws Exception {
        var r=one(db.queryForList("SELECT * FROM "+STATE+" WHERE tenant_id=? AND rule_id=? FOR UPDATE",a.context().tenant_id(),row.id()));
        metadata(r,row.manifest()); if(time(r,"updated_at").isBefore(time(r,"created_at"))) mismatch();
        String status=string(r,"status"),revision=string(r,"revision"); String decision=nullable(r,"latest_decision_id");
        if(!Set.of("DRAFT","APPROVED","DISABLED").contains(status)||("DRAFT".equals(status)?!"1".equals(revision)||decision!=null:decision==null)
                ||"APPROVED".equals(status)&&!"2".equals(revision)||"DISABLED".equals(status)&&!Set.of("2","3").contains(revision)) mismatch();
        if(decision!=null) { var d=readDecision(a,row,decision); if(!status.equals(text(d.body(),"status"))||!revision.equals(text(d.body(),"revision"))) mismatch(); }
        return new State(status,revision,decision);
    }

    private Decision readDecision(EnterpriseAccess.Admission a,Row row,String id) throws Exception {
        var r=one(db.queryForList("SELECT * FROM "+DECISION+" WHERE tenant_id=? AND rule_id=? AND decision_id=? FOR UPDATE",a.context().tenant_id(),row.id(),id));
        var body=schema.read(string(r,"decision_json")); var request=schema.validate("InputDecide",body.required("request"));
        metadata(r,body,row.request());
        if(!"1.0.0".equals(text(body,"schema_version"))||!row.id().equals(text(body,"rule_id"))||!row.hash().equals(text(body,"rule_sha256"))
                ||!id.equals(text(body,"decision_id"))||!string(r,"content_sha256").equals(schema.hash(body))
                ||!string(r,"expected_revision").equals(text(request,"expected_revision"))
                ||!string(r,"decision").equals(text(request,"decision"))||!row.hash().equals(text(request,"rule_sha256"))
                ||!row.project().equals(text(request,"project_id"))
                ||!text(body,"previous_revision").equals(text(request,"expected_revision"))
                ||!RunCreateSchema.next(text(body,"previous_revision")).equals(text(body,"revision"))) mismatch();
        requireDecision(text(body,"previous_status"),text(request,"decision"),row.author(),text(body,"created_by"));
        if(!("DRAFT".equals(text(body,"previous_status"))?"1":"2").equals(text(body,"previous_revision"))) mismatch();
        if(!("APPROVE".equals(text(request,"decision"))?"APPROVED":"DISABLED").equals(text(body,"status"))) mismatch();
        requireHistoricalGrant(a,text(body,"created_by"),text(body,"identity_mode"),body.required("grant"),row.request(),decisionAction(request),Instant.parse(text(body,"created_at")));
        if("APPROVED".equals(text(body,"status")))requireEnterpriseWitness(a,text(body,"created_by"),text(body,"identity_mode"),body.required("enterprise_budget_authority"),row.request(),time(r,"created_at"));
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
        if(!id.equals(text(receipt,"rule_id"))) mismatch();
        if("AUTHOR".equals(kind)) {
            if(!row.author().equals(a.actorId())||!request.equals(row.request())) mismatch();
            var original=one(db.queryForList("SELECT receipt_json,idempotency_key FROM "+CAPABILITY+" WHERE tenant_id=? AND rule_id=? FOR UPDATE",a.context().tenant_id(),id));
            if(!key.equals(string(original,"idempotency_key"))||!receipt.equals(schema.read(string(original,"receipt_json")))) mismatch();
        } else {
            String decisionId=text(receipt,"decision_id"); var decision=readDecision(a,row,decisionId);
            if(!decision.actor().equals(a.actorId())||!request.equals(decision.body().required("request"))) mismatch();
            var original=one(db.queryForList("SELECT receipt_json,idempotency_key FROM "+DECISION+" WHERE tenant_id=? AND rule_id=? AND decision_id=? FOR UPDATE",a.context().tenant_id(),id,decisionId));
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
                ||!text(row.request(),"task_id").equals(text(receipt,"task_id"))||!row.id().equals(text(receipt,"rule_id"))
                ||!row.hash().equals(text(receipt,"rule_sha256"))||!schema.hash(request).equals(text(receipt,"request_sha256"))
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
                .put("classification",text(pins,"classification")).put("rule_id",capability)
                .put("rule_sha256",hash).put("status",status).put("revision",revision);
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
                text(request,"scope_snapshot"),text(request,"original_rule_id"),text(request,"budget_revision"),text(request,"release_id"),
                text(request.required("role_version"),"version_id"),text(request.required("execution_policy"),"version_id"),
                text(request.required("enterprise_budget_basis"),"version_id")));
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
