package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Read-only proposed mapping preparation. SourceExact choices are existing project-bound
 * publication metadata, not physical read receipts or approved field values. */
public final class TaskFieldTargetPreparationService {
    private final EnterpriseAccess access; private final TaskFieldTargetBasis basis;
    private final TaskFieldMappingService mappings; private final TaskPlanRelationStore relations;
    private final TaskFieldTargetSchema schema; private final PermissionSchema json;
    private final TaskFieldTargetPolicy policy; private final ResourceReadAuthority resources;
    private final SourceExactService exact; private final SourceExactMapper exactRows;
    private final TaskFieldTargetLedger ledger; private final TaskFieldTargetRegistration registrations;
    private final JdbcTemplate db;
    private final boolean transportAvailable;
    public TaskFieldTargetPreparationService(EnterpriseAccess access,TaskFieldTargetBasis basis,
            TaskFieldMappingService mappings,TaskPlanRelationStore relations,TaskFieldTargetSchema schema,
            PermissionSchema json,TaskFieldTargetPolicy policy,ResourceReadAuthority resources,
            SourceExactService exact,SourceExactMapper exactRows,TaskFieldTargetLedger ledger,
            TaskFieldTargetRegistration registrations,DataSource data) {
        this(access,basis,mappings,relations,schema,json,policy,resources,exact,exactRows,ledger,registrations,data,false);
    }
    public TaskFieldTargetPreparationService(EnterpriseAccess access,TaskFieldTargetBasis basis,
            TaskFieldMappingService mappings,TaskPlanRelationStore relations,TaskFieldTargetSchema schema,
            PermissionSchema json,TaskFieldTargetPolicy policy,ResourceReadAuthority resources,
            SourceExactService exact,SourceExactMapper exactRows,TaskFieldTargetLedger ledger,
            TaskFieldTargetRegistration registrations,DataSource data,boolean transportAvailable) {
        this.transportAvailable=transportAvailable;
        this.access=access;this.basis=basis;this.mappings=mappings;this.relations=relations;this.schema=schema;
        this.json=json;this.policy=policy;this.resources=resources;this.exact=exact;this.exactRows=exactRows;
        this.ledger=ledger;this.registrations=registrations;this.db=new JdbcTemplate(data);db.setQueryTimeout(5);
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String taskId)throws Exception {
        WireValues.uuid(taskId);
        var initial=access.execute(subject,a->checked(()->snapshot(a,context,taskId)));
        var choices=json.object().arrayNode();
        for(var source:initial.sources()) {
            if(source.fieldRevision()==null)continue;
            var envelope=exact.currentFields(subject,context,source.source(),initial.project(),source.parse(),source.approval());
            var manifest=envelope.required("data");
            requireSourceIdentity(source,manifest);
            appendChoices(choices,manifest,schema,10000);
        }
        return access.execute(subject,a->checked(()->{
            var current=snapshot(a,context,taskId);
            if(!initial.authorityHash().equals(current.authorityHash()) || !initial.sources().equals(current.sources())
                    || !initial.response().equals(current.response()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var response=current.response().deepCopy();response.set("source_choices",choices);
            return schema.validate("Preparation",response);
        }));
    }
    private Snapshot snapshot(EnterpriseAccess.Admission a,String context,String taskId)throws Exception {
        if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var proof=basis.require(a,taskId,"task.read");var task=proof.task().head();
        var mapping=mappings.currentView(a,taskId);
        var projection=relations.read(a,task.projectId(),taskId,proof.plan().planRevision(),proof.execution().validation());
        var template=proof.execution().template().view().template().content();
        var selected=mapping==null||!mapping.path("is_current").asBoolean()?json.object().arrayNode():mapping.required("manifest").required("mappings");
        // The closed contract bound is a read ceiling, not an operational production default.
        var inventory=new TaskFieldInventory(json).derive(template,projection,proof.execution().template().view().conditions(),selected,10000);
        var response=proof.pins();response.put("schema_version","1.0.0").put("tenant_id",task.tenantId()).put("task_id",taskId);
        response.put("head_revision",mapping==null?"0":mapping.required("revision").asText());
        if(mapping==null)response.putNull("base_mapping_id");else response.set("base_mapping_id",mapping.required("manifest").required("assignment_id"));
        response.set("current_mapping",mapping==null?json.object().nullNode():mapping);
        response.set("inventory",inventory);response.set("sections",sections(projection,json));response.putArray("source_choices");
        response.set("template_nodes",json.tree(template.nodes()));response.set("repeat_definitions",json.tree(template.repeat_definitions()));
        response.set("attachment_requirements",json.tree(template.attachment_requirements()));
        response.set("condition_results",json.tree(proof.execution().template().view().conditions()));
        var actions=response.putArray("allowed_actions").add("READ");var prerequisites=response.putArray("prerequisites");
        boolean configured=true;try{policy.requireConfigured();policy.lineageLimits();if(!transportAvailable)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}catch(TenantAccessException denied){if(denied.code()!=ErrorCode.DEPENDENCY_UNAVAILABLE)throw denied;configured=false;prerequisites.add("CONFIGURATION_REQUIRED");}
        boolean write=allowed(a,task.projectId(),taskId,TaskFieldTargetProtocol.MAPPING_WRITE,TaskFieldTargetProtocol.PREPARATION_PURPOSE);
        boolean produce=allowed(a,task.projectId(),taskId,TaskFieldTargetProtocol.TARGET_PRODUCE,TaskFieldTargetProtocol.PREPARATION_PURPOSE);
        if(write&&configured)actions.add("WRITE_MAPPING");if(!write)prerequisites.add("MAPPING_WRITE_PERMISSION_REQUIRED");
        if(!produce)prerequisites.add("PRODUCER_PERMISSION_REQUIRED");
        if(mapping==null)prerequisites.add("MAPPING_REQUIRED");
        boolean unresolved=hasUnresolved(inventory);
        if(unresolved)prerequisites.add("UNRESOLVED_INVENTORY");
        if(produce&&configured&&mapping!=null&&mapping.path("is_current").asBoolean()&&!unresolved)actions.add("PRODUCE");
        if(allowed(a,task.projectId(),taskId,TaskFieldTargetProtocol.INPUT_WRITE,TaskFieldTargetProtocol.INPUT_PURPOSE))actions.add("AUTHOR_INPUT");
        response.set("production",production(a,taskId,task.projectId(),mapping,produce));
        var sources=new ArrayList<SourceHead>();
        for(var source:proof.execution().sources()) {
            var selection=Objects.requireNonNull(source.selection());
            var values=new HashMap<String,Object>();values.put("tenantId",task.tenantId());values.put("sourceVersion",selection.sourceVersion());
            values.put("parseVersion",selection.parseVersion());values.put("approvalRevision",selection.approvalRevision());
            var heads=exactRows.head(values);if(heads.size()>1)bad();
            String field=null,revision=null;if(!heads.isEmpty()){field=Objects.toString(heads.get(0).get("field_revision"),null);revision=Objects.toString(heads.get(0).get("revision"),null);WireValues.uuid(field);PermissionSchema.revision(revision);}
            sources.add(new SourceHead(selection.sourceVersion(),selection.parseVersion(),selection.approvalRevision(),field,revision));
        }
        sources.sort(Comparator.comparing(SourceHead::source));
        proof.requireUnchanged();return new Snapshot(task.projectId(),proof.authorizationsSha256(),response,List.copyOf(sources));
    }
    private JsonNode production(EnterpriseAccess.Admission a,String task,String project,JsonNode mapping,boolean produce)throws Exception {
        var status=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).put("project_id",project).put("task_id",task).put("state","NOT_STARTED");
        for(String field:List.of("assignment_id","assignment_sha256","write_id","artifact_id","receipt","reason"))status.putNull(field);
        if(mapping==null)return status;
        String assignment=mapping.required("manifest").required("assignment_id").asText(),hash=mapping.required("assignment_sha256").asText();
        status.put("assignment_id",assignment).put("assignment_sha256",hash);
        if(!mapping.path("is_current").asBoolean()){status.put("state","WAITING_INPUT").put("reason","The saved mapping belongs to an earlier task or plan revision.");return status;}
        var existing=ledger.findForMapping(a,task,assignment,hash);if(existing.isEmpty())return status;
        var row=existing.get();String write=Objects.toString(row.columns().get("write_id"));status.put("write_id",write);
        var artifacts=db.query("SELECT artifact_id FROM task_field_target_registration WHERE tenant_id=? AND project_id=? AND task_id=? AND write_id=?",(rs,n)->rs.getString(1),a.context().tenant_id(),project,task,write);
        if(artifacts.size()>1)bad();
        if(artifacts.isEmpty()){status.put("state","TRANSFER_UNCERTAIN").put("reason","The original attempt exists; recover its original key without allocating a replacement.");return status;}
        if(!produce){status.put("state","WAITING_INPUT").put("reason","Current producer permission is required to verify this registration receipt.");return status;}
        var registered=registrations.requireRegistered(a,artifacts.get(0),TaskFieldTargetProtocol.TARGET_PRODUCE);
        if(!assignment.equals(registered.mappingRevision())||!hash.equals(registered.mappingSha256()))bad();
        registered.requireUnchanged();status.put("state","REGISTERED").put("artifact_id",registered.artifactId());status.set("receipt",registered.receipt());return status;
    }
    private boolean allowed(EnterpriseAccess.Admission a,String project,String task,String action,String purpose)throws java.io.IOException{
        try{resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"TASK",task),action,purpose);return true;}
        catch(TenantAccessException denied){if(denied.code()!=ErrorCode.ACCESS_DENIED)throw denied;return false;}
    }
    static boolean hasUnresolved(JsonNode inventory){for(var item:inventory)if(!Set.of("MAPPED","NOT_APPLICABLE").contains(item.required("state").asText()))return true;return false;}
    static ArrayNode sections(TaskPlanProjection.Projection plan,PermissionSchema json){
        var result=json.object().arrayNode();for(var section:plan.sections()){
            var item=result.addObject().put("section_id",section.sectionId());var nodes=item.putArray("requirement_node_ids");
            plan.sectionNodes().stream().filter(n->n.sectionId().equals(section.sectionId())).forEach(n->nodes.add(n.nodeId()));
            var questions=item.putArray("question_ids");plan.questions().stream().filter(q->q.sectionId().equals(section.sectionId())).forEach(q->questions.add(q.questionId()));
        }return result;
    }
    static void appendChoices(ArrayNode choices,JsonNode manifest,TaskFieldTargetSchema schema,int maximum)throws Exception{
        var fields=manifest.required("request").required("fields");int ordinal=0;
        for(var field:fields){String kind=field.required("field_kind").asText();if(!TaskFieldTargetProtocol.SOURCE_EXACT_KINDS.contains(kind))bad();
            if(choices.size()>=maximum)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            var choice=choices.addObject();choice.put("raw_value",field.required("raw_value").asText());var ref=choice.putObject("source_exact");
            ref.set("source_version",manifest.required("source_version"));ref.set("field_revision",manifest.required("field_revision"));
            ref.put("manifest_sha256",schema.hash(manifest)).put("ordinal",ordinal++).put("field_kind",kind);
        }
    }
    private static void requireSourceIdentity(SourceHead source,JsonNode manifest){
        if(!source.source().equals(manifest.required("source_version").asText())||!source.fieldRevision().equals(manifest.required("field_revision").asText())
                ||!source.revision().equals(manifest.required("revision").asText())||!source.parse().equals(manifest.required("request").required("parse_version").asText())
                ||!source.approval().equals(manifest.required("request").required("approval_revision").asText()))bad();
    }
    private record SourceHead(String source,String parse,String approval,String fieldRevision,String revision){}
    private record Snapshot(String project,String authorityHash,ObjectNode response,List<SourceHead> sources){}
    private static <T>T checked(ThrowingSupplier<T> value){return value.get();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
