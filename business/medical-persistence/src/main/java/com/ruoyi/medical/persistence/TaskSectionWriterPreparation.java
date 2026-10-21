package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** C04 -> N04 production chain. Every I/O occurs after immutable intent commit and outside
 * a SQL transaction. Each guard and registration obtains fresh current private authority. */
public final class TaskSectionWriterPreparation {
    private final JdbcTemplate db;private final TaskRunActivityPreparation runs;private final DocumentInitialRevisionService documents;
    private final TaskSectionWriterInputAuthority inputs;private final TaskSectionWriterProducerAuthority producers;private final TaskSectionWriterObjectLedger objects;
    private final TaskSectionWriterMaterializer nodes;private final RuntimeObjectTransport transport;private final TaskSectionWriterSchema json;
    private final TaskResearchCompletionAuthority research;private final TaskStrictFieldWriterReadAuthority fields;private final FormalCapabilityIdentityAuthority roles;
    private final FormalRuntimeCapabilityIdentityAuthority tools;private final RunTokenAuthoritySchema tokens;private final PermissionSchema permissions;
    public TaskSectionWriterPreparation(DataSource data,TaskRunActivityPreparation runs,DocumentInitialRevisionService documents,TaskSectionWriterInputAuthority inputs,
            TaskSectionWriterProducerAuthority producers,TaskSectionWriterObjectLedger objects,TaskSectionWriterMaterializer nodes,RuntimeObjectTransport transport,
            TaskSectionWriterSchema json,TaskResearchCompletionAuthority research,TaskStrictFieldWriterReadAuthority fields,FormalCapabilityIdentityAuthority roles,
            FormalRuntimeCapabilityIdentityAuthority tools,RunTokenAuthoritySchema tokens,PermissionSchema permissions){
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.runs=runs;this.documents=documents;this.inputs=inputs;this.producers=producers;this.objects=objects;this.nodes=nodes;this.transport=transport;this.json=json;this.research=research;this.fields=fields;this.roles=roles;this.tools=tools;this.tokens=tokens;this.permissions=permissions;
    }
    private org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> reuse;
    @org.springframework.beans.factory.annotation.Autowired void reuse(org.springframework.beans.factory.ObjectProvider<TaskRunSuccessReuse> reuse){this.reuse=reuse;}
    public List<JsonNode> materialize(HttpServletRequest http,Reference reference)throws Exception{
        // A caller cannot supply body bytes, target sections, field results, or producer pins.
        boolean originalDocument=runs.withCurrent(http,reference,c->documents.resumeInitial(c.production(),c.publication(),c.policy()).isPresent());
        if(!originalDocument){
        var attempts=runs.withCurrent(http,reference,c->documents.attempts(blank(c)));
        var physical=new ArrayList<RuntimeObjectTransport.VerifiedTransfer>();
        for(var attempt:attempts)physical.add(transport.transferVerified(attempt,()->org.springframework.util.function.ThrowingSupplier.of(()->runs.withCurrent(http,reference,c->{documents.requireTransfer(blank(c),attempt);return null;})).get()));
        runs.withCurrent(http,reference,c->{documents.register(blank(c),attempts,physical);return null;});
        }
        var sections=runs.withCurrent(http,reference,c->assignedSections(c));
        if(sections.isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var result=new ArrayList<JsonNode>();for(String section:sections){
            var attempt=runs.withCurrent(http,reference,c->{var fixed=input(c,section);return objects.prepareInput(fixed.input,fixed.producer);});
            var transfer=transport.transferVerified(attempt,()->org.springframework.util.function.ThrowingSupplier.of(()->runs.withCurrent(http,reference,c->{var fixed=input(c,section);objects.requireTransfer(fixed.input,fixed.producer,attempt);return null;})).get());
            result.add(runs.withCurrent(http,reference,c->{var fixed=input(c,section);var registered=objects.registerInput(fixed.input,fixed.producer,attempt,transfer);var made=nodes.materialize(registered);reuse.getObject().materializeStage(c,"N04",stepId(c.production().runId(),section));return made;}));
        }return List.copyOf(result);
    }
    private DocumentInitialRevisionService.Prepared blank(TaskRunActivityPreparation.Current c)throws Exception{
        var rule=rule(c,null);var binding=one(c.policy().definition().bindings().stream().filter(b->"N04".equals(b.nodeKind())).toList());
        return documents.prepare(c,producers.requireInitial(c.production(),c.publication(),c.policy(),binding.roleVersion(),str(rule,"rule_id"),str(rule,"content_sha256")));
    }
    private record Fixed(TaskSectionWriterInputAuthority.Prepared input,TaskSectionWriterProducerAuthority.Prepared producer){}
    private Fixed input(TaskRunActivityPreparation.Current c,String section)throws Exception{
        c.requireUnchanged();var p=c.production();var replay=p.replay().content();var resumed=documents.resumeInitial(p,c.publication(),c.policy());var initial=resumed.isPresent()?resumed.get():documents.requireRegistered(blank(c));
        var binding=one(c.policy().definition().bindings().stream().filter(b->"N04".equals(b.nodeKind())).toList());
        var output=one(db.queryForList("SELECT artifact_id,receipt_sha256 FROM task_strict_field_output_registration WHERE tenant_id=? AND project_id=? AND task_id=? AND (run_id=? OR EXISTS (SELECT 1 FROM task_run_success_reuse r WHERE r.tenant_id=task_strict_field_output_registration.tenant_id AND r.run_id=? AND r.stage_kind='N03' AND r.original_run_id=task_strict_field_output_registration.run_id AND r.original_step_id=task_strict_field_output_registration.step_id)) LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),p.runId(),p.runId()));
        var checked=fields.requireForWriter(p,str(output,"artifact_id"),str(output,"receipt_sha256"),c.publication(),c.policy());
        var predecessors=new ArrayList<TaskNodeInputProtocol.Predecessor>();for(var n:c.graph().nodes())if(section.equals(n.sectionId())){
            var proof=research.require(p.admission(),p.runId(),n.stepId());proof.requireUnchanged();predecessors.add(new TaskNodeInputProtocol.Predecessor(n.stepId(),proof.resultArtifactId(),proof.resultSha256(),proof.resultObjectVersion(),proof.resultReceiptSha256(),proof.evidenceArtifactId(),proof.evidenceSha256(),proof.evidenceObjectVersion(),proof.evidenceReceiptSha256(),proof.completionReceiptSha256()));
        }
        var sources=new ArrayList<TaskNodeInputProtocol.SourceSelection>();for(var m:replay.required("scope_snapshot").required("members"))sources.add(new TaskNodeInputProtocol.SourceSelection(text(m,"source_version"),text(m,"parse_version"),text(m,"index_version"),text(m,"approval_revision"),text(m,"binding_revision"),text(m,"rights_revision"),c.policy().definition().businessPurpose()));
        var role=roles.require(p.admission(),binding.roleVersion(),"ROLE",p.projectId(),"RUN_CREATION");var declared=tokens.permissions(tokens.read("RoleScope",permissions.canonical(role.canonicalDefinition().required("request").required("tool_scope"))));var principal=tokens.permissions(replay.required("principal_manifest"));
        var nodePermissions=declared.stream().filter(principal::contains).toList();String step=stepId(p.runId(),section),inputId=inputId(p.runId(),section);
        var input=new TaskSectionWriterInput("1.0.0","N04",p.tenantId(),p.projectId(),p.taskId(),p.runId(),step,inputId,c.graph().graphId(),c.graph().graphSha256(),p.replay().rawSha256(),text(replay,"plan_revision"),text(replay.required("plan"),"content_sha256"),text(replay,"snapshot_id"),binding.roleVersion(),new PermissionProtocol.Basis(c.policy().versionId(),c.policy().contentSha256()),Objects.requireNonNull(binding.modelVersion()),submitTool(c),initial.documentId(),initial.documentRevision(),initial.bodySchemaRevision(),TaskWriterEvidenceContract.require(role.canonicalDefinition().required("request"),SchemaCatalogue.fromClasspath()).bodySha(),text(replay.required("template"),"template_version"),List.of(section),checked.outputArtifactId(),checked.outputObject(),checked.outputReceiptSha256(),predecessors,sources,nodePermissions);
        var prepared=inputs.prepare(p,c.publication(),c.policy(),input,json.bytes(initial.body()),initial);var selected=rule(c,section);
        return new Fixed(prepared,producers.requireInput(p,c.publication(),c.policy(),input,str(selected,"rule_id"),str(selected,"content_sha256")));
    }
    private Map<String,Object> rule(TaskRunActivityPreparation.Current c,String section)throws Exception{
        var p=c.production();var original=section==null?db.queryForList("SELECT producer_pins_json FROM document_initial_intent WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId()):db.queryForList("SELECT producer_pins_json FROM task_section_writer_object_intent WHERE tenant_id=? AND run_id=? AND step_id=? AND kind='TASK_SECTION_WRITER_INPUT' LIMIT 2 FOR UPDATE",p.tenantId(),p.runId(),stepId(p.runId(),section));
        if(!original.isEmpty()){var pins=json.read(str(one(original),"producer_pins_json"));return Map.of("rule_id",text(pins,"producer_rule_id"),"content_sha256",text(pins,"producer_sha256"));}
        var replay=p.replay().content();var role=one(c.policy().definition().bindings().stream().filter(b->"N04".equals(b.nodeKind())).toList()).roleVersion();
        var rows=db.queryForList("SELECT r.rule_id,r.content_sha256,r.section_ids,r.fixed_json FROM task_section_writer_producer_rule r JOIN task_section_writer_producer_state s ON s.tenant_id=r.tenant_id AND s.project_id=r.project_id AND s.task_id=r.task_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.plan_revision=? AND r.plan_sha256=? AND r.scope_snapshot=? AND r.release_id=? AND r.publication_approval_sha256=? AND r.role_version=? AND r.role_sha256=? AND r.policy_version=? AND r.policy_sha256=? AND s.status='APPROVED' ORDER BY r.rule_id LIMIT 1025 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),text(replay,"plan_revision"),text(replay.required("plan"),"content_sha256"),text(replay,"snapshot_id"),text(replay,"agent_release_id"),text(replay,"publication_approval_sha256"),role.version_id(),role.content_sha256(),c.policy().versionId(),c.policy().contentSha256());
        if(rows.size()>1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);var candidates=new ArrayList<Map<String,Object>>();for(var r:rows){var ids=new HashSet<String>();json.read(str(r,"section_ids")).forEach(id->ids.add(id.asText()));var manifest=json.read(str(r,"fixed_json")).required("manifest");if(section==null){var kinds=new HashSet<String>();manifest.required("artifact_kinds").forEach(k->kinds.add(k.asText()));var assigned=new HashSet<>(assignedSections(c));if(kinds.contains("DOCUMENT_BODY")&&ids.containsAll(assigned))candidates.add(r);}else if(ids.contains(section))candidates.add(r);}return one(candidates);
    }
    private List<String> assignedSections(TaskRunActivityPreparation.Current c)throws Exception{
        c.requireUnchanged();var ids=new ArrayList<String>();for(var section:c.production().replay().content().required("plan").required("content").required("sections"))ids.add(text(section,"section_id"));
        if(ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return List.copyOf(ids);
    }
    private PermissionProtocol.Basis submitTool(TaskRunActivityPreparation.Current c)throws Exception{
        var result=new ArrayList<PermissionProtocol.Basis>();var catalogue=SchemaCatalogue.fromClasspath();for(var t:c.production().replay().content().required("agent_release").required("tool_versions")){var basis=permissions.basis(t);var proof=tools.require(c.production().admission(),basis,"TOOL",c.production().projectId(),"AGENT_PUBLICATION");var definition=proof.canonicalDefinition().required("request").required("input_schema_ref");if(catalogue.schemaRef("tool-inputs","CandidateArgs").equals(text(definition,"schema_ref"))&&catalogue.schemaDigest("tool-inputs").equals(text(definition,"schema_sha256")))result.add(basis);proof.requireUnchanged();}return one(result);
    }
    public static String stepId(String run,String section){WireValues.uuid(run);WireValues.uuid(section);return UUID.nameUUIDFromBytes(("N04:STEP:"+run+":"+section).getBytes(StandardCharsets.UTF_8)).toString();}
    public static String inputId(String run,String section){WireValues.uuid(run);WireValues.uuid(section);return UUID.nameUUIDFromBytes(("N04:INPUT:"+run+":"+section).getBytes(StandardCharsets.UTF_8)).toString();}
    private static <T>T one(List<T> rows){if(rows.size()!=1)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);return rows.get(0);}private static String str(Map<String,Object> r,String key){return Objects.requireNonNull(r.get(key)).toString();}private static String text(JsonNode n,String key){return n.required(key).asText();}
}
