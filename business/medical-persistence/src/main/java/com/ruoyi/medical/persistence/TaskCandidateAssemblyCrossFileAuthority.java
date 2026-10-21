package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskCandidateAssemblyCrossFileProtocol.*;

/** Closed explicit N06 external-document selectors, current rights, physical complete
 * reads and the existing approved boolean RuleVersion engine. Never searches for an
 * approximate field, changes the replay, or treats an external model as a check. */
public final class TaskCandidateAssemblyCrossFileAuthority {
    private final JdbcTemplate db;private final TaskSourceVerifierSchema json;private final TaskNodeProducerService producers;
    private final TaskRunActivityPreparation runs;private final FormalRunControlAccess control;private final DocumentReadService documents;
    private final TaskSourceVerifierRules rules;private final RuleExpression engine;
    private final JsonMapper parser=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).build();
    public TaskCandidateAssemblyCrossFileAuthority(DataSource data,TaskSourceVerifierSchema json,TaskNodeProducerService producers,
            TaskRunActivityPreparation runs,FormalRunControlAccess control,DocumentReadService documents,TaskSourceVerifierRules rules,RuleExpression engine){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.producers=producers;this.runs=runs;this.control=control;this.documents=documents;this.rules=rules;this.engine=engine;}

    /** Selection is independently approved with the N06 producer manifest. References
     * absent from the accepted replay require a new task input/revision, never auto-add. */
    public Selection select(TaskRunActivityPreparation.Current c)throws Exception {
        c.requireUnchanged();var p=c.production();var required=c.production().replay().content().required("template").required("content").required("cross_document_rules");
        var role=one(c.policy().definition().bindings().stream().filter(b->"N06".equals(b.nodeKind())).toList()).roleVersion();
        var rows=db.queryForList("SELECT r.rule_id,r.content_sha256 FROM task_candidate_assembly_producer_rule r JOIN task_candidate_assembly_producer_state s ON s.tenant_id=r.tenant_id AND s.rule_id=r.rule_id WHERE r.tenant_id=? AND r.project_id=? AND r.task_id=? AND r.plan_revision=? AND s.status='APPROVED' LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),text(p.replay().content(),"plan_revision"));
        if(rows.isEmpty()&&required.isEmpty())return new Selection(new Input("1.0.0",List.of(),List.of()),json.tree(Map.of("not_required",true)),List.of());
        if(rows.isEmpty())return new Selection(new Input("1.0.0",List.of(),List.of()),json.tree(Map.of("missing","N06_PRODUCER_APPROVAL_REQUIRED")),List.of("N06_PRODUCER_APPROVAL_REQUIRED"));
        var row=one(rows);var producer=producers.requireCurrent(p.admission(),c.publication(),c.policy(),role,str(row,"rule_id"),str(row,"content_sha256"),p.taskId(),null,null);producers.requireReplayPins(producer.manifest(),p.replay().content());
        var value=producer.manifest().get("cross_file_inputs");var blocked=new ArrayList<String>();
        var input=value==null?new Input("1.0.0",List.of(),List.of()):parser.treeToValue(value,Input.class);
        if(value==null&&!required.isEmpty())blocked.add("CROSS_FILE_MAPPING_REQUIRED");
        var byId=new HashMap<String,Rule>();for(var r:input.rules())if(byId.putIfAbsent(r.ruleId(),r)!=null)bad();
        var expected=new HashSet<String>();var types=new HashSet<String>();
        for(var rule:required){String id=text(rule,"rule_id");expected.add(id);rule.required("related_document_types").forEach(v->types.add(v.asText()));var mapped=byId.get(id);if(mapped==null)blocked.add("CROSS_FILE_MAPPING_REQUIRED:"+id);else same(rule.required("rule_revision"),json.tree(mapped.ruleRevision()));}
        if(!expected.containsAll(byId.keySet()))bad();
        var declared=new HashMap<String,JsonNode>();p.replay().content().required("dependency_versions").forEach(v->{if(declared.put(text(v,"version_id"),v)!=null)bad();});
        pin(declared,new PermissionProtocol.Basis(producer.capabilityId(),producer.capabilityHash()),"TASK_NODE_PRODUCER_RULE",blocked);
        var docs=new HashSet<String>();var revisions=new HashSet<String>();var seenTypes=new HashSet<String>();
        for(var d:input.documents()){
            if(!docs.add(d.documentId())||!revisions.add(d.documentRevision().version_id()))bad();seenTypes.add(d.documentType());
            pin(declared,d.documentRevision(),"DOCUMENT_REVISION",blocked);pin(declared,d.bodyArtifact(),"ARTIFACT",blocked);pin(declared,d.templateVersion(),"TEMPLATE_VERSION",blocked);
            for(var fact:d.factVersions())pin(declared,fact,"SHARED_FACT",blocked);
            var storedTemplate=one(db.queryForList("SELECT t.document_type,v.content_sha256 FROM template_version t JOIN version_registry v ON v.tenant_id=t.tenant_id AND v.version_id=t.template_version WHERE t.tenant_id=? AND t.template_version=? LIMIT 2 FOR UPDATE",p.tenantId(),d.templateVersion().version_id()));same(d.documentType(),str(storedTemplate,"document_type"));same(d.templateVersion().content_sha256(),str(storedTemplate,"content_sha256"));
        }
        if(!seenTypes.containsAll(types))blocked.add("RELATED_DOCUMENT_SELECTION_INCOMPLETE");
        // Complete impact scope includes all current project documents of required types,
        // not merely whichever documents an author happened to provide.
        var current=db.queryForList("SELECT h.document_id,h.current_document_revision,t.document_type FROM document_head h JOIN document_revision_meta m ON m.tenant_id=h.tenant_id AND m.project_id=h.project_id AND m.document_id=h.document_id AND m.document_revision=h.current_document_revision JOIN template_version t ON t.tenant_id=m.tenant_id AND t.template_version=m.template_version WHERE h.tenant_id=? AND h.project_id=? ORDER BY h.document_id LIMIT 1025 FOR UPDATE",p.tenantId(),p.projectId());
        if(current.size()>1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        for(var d:current)if(types.contains(str(d,"document_type"))){var selected=input.documents().stream().filter(x->x.documentId().equals(str(d,"document_id"))).toList();if(selected.size()!=1||!selected.get(0).documentRevision().version_id().equals(str(d,"current_document_revision")))blocked.add("CURRENT_RELATED_DOCUMENT_REQUIRED:"+str(d,"document_type"));}
        for(var r:input.rules())for(var selector:r.inputs()){
            if("DOCUMENT".equals(selector.sourceKind())&&!revisions.contains(selector.documentRevision()))bad();
            if(selector.factVersion()!=null)pin(declared,selector.factVersion(),"SHARED_FACT",blocked);
        }
        var pins=JsonNodeFactory.instance.objectNode().put("producer_rule_id",producer.capabilityId()).put("producer_sha256",producer.capabilityHash()).put("producer_decision_id",producer.decisionId()).put("producer_decision_sha256",producer.decisionHash()).put("producer_authorizations_sha256",producer.fingerprint());pins.set("selection",json.tree(input));pins.put("current_related_documents_sha256",json.hash(current));pins.set("blockers",json.tree(blocked));c.requireUnchanged();return new Selection(input,pins,List.copyOf(blocked));
    }
    public Verified read(HttpServletRequest http,Reference reference)throws Exception {
        var selected=runs.withCurrent(http,reference,this::select);
        if(!selected.blockers.isEmpty()||selected.input.documents().isEmpty())return new Verified(selected,Map.of());
        return control.withReads(http,reference.tenantId(),reference.runId(),runs.withCurrent(http,reference,c->text(c.acceptedStart(),"command_id")),reads->ThrowingSupplier.of(()->{
            var loaded=new LinkedHashMap<String,DocumentReadService.FixedRead>();var access=EvidenceReadAccess.control(reads);
            for(var d:selected.input.documents()){
                reads.requireVersion(d.documentRevision().version_id(),d.documentRevision().content_sha256());reads.requireVersion(d.bodyArtifact().version_id(),d.bodyArtifact().content_sha256());
                var physical=documents.readFixed(access,reads.contextRevision(),d.documentId(),d.documentRevision().version_id());validate(d,physical);loaded.put(d.documentRevision().version_id(),physical);
            }
            var result=new Verified(selected,loaded);runs.withCurrent(http,reference,c->{result.requireUnchanged(c);return null;});return result;
        }).get());
    }
    public final class Selection {
        private final Input input;private final JsonNode pins;private final List<String> blockers;
        private Selection(Input input,JsonNode pins,List<String> blockers){this.input=input;this.pins=pins.deepCopy();this.blockers=List.copyOf(blockers);}
        public JsonNode preflight(){var result=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("status",blockers.isEmpty()?"READY_FOR_FIXED_READ":"BLOCKED").put("scope_expanded",false);result.set("selection",json.tree(input));result.set("blockers",json.tree(blockers));return result;}
    }
    public final class Verified {
        private final Selection selection;private final Map<String,DocumentReadService.FixedRead> loaded;
        private Verified(Selection s,Map<String,DocumentReadService.FixedRead> loaded){selection=s;this.loaded=Map.copyOf(loaded);}
        public void requireUnchanged(TaskRunActivityPreparation.Current c)throws Exception{
            var fresh=select(c);same(selection.pins,fresh.pins);for(var d:selection.input.documents())if(selection.blockers.isEmpty()){var read=loaded.get(d.documentRevision().version_id());if(read==null)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);documents.requireCurrent(c.admission(),read);validate(d,read);}c.requireUnchanged();
        }
        public JsonNode pins(){var p=selection.preflight();((ObjectNode)p).set("producer_selection",selection.pins.deepCopy());var reads=((ObjectNode)p).putArray("physical_reads");for(var d:selection.input.documents()){var r=loaded.get(d.documentRevision().version_id());if(r!=null){var pin=reads.addObject().put("document_revision",d.documentRevision().version_id()).put("body_sha256",r.header().bodySha256()).put("read_digest",r.digest());pin.set("watermarks",json.tree(r.watermarks()));}}return p;}
        public List<String> impactBlockers(TaskRunActivityPreparation.Current c,JsonNode body)throws Exception {var result=new ArrayList<String>();impactReport(c,body).required("blockers").forEach(v->result.add(v.asText()));return List.copyOf(result);}
        public JsonNode impactReport(TaskRunActivityPreparation.Current c,JsonNode candidateBody)throws Exception {
            requireUnchanged(c);var blocked=new LinkedHashSet<String>();var facts=new TreeSet<String>();candidateBody.required("fact_usages").forEach(f->facts.add(text(f,"fact_version_id")));for(var d:selection.input.documents())d.factVersions().forEach(f->facts.add(f.version_id()));
            if(facts.size()>512)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            String target=null;for(var node:candidateBody.required("nodes"))if("DOCUMENT".equals(text(node.required("content"),"kind")))target=text(node,"node_id");
            var selected=new HashSet<>(loaded.keySet());var report=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0");var entries=report.putArray("facts");
            for(String fact:facts){
                var impacts=db.queryForList("SELECT DISTINCT h.document_id,h.current_document_revision FROM fact_usage f JOIN document_revision_meta m ON m.tenant_id=f.tenant_id AND m.project_id=f.project_id AND m.document_revision=f.document_revision JOIN document_head h ON h.tenant_id=m.tenant_id AND h.project_id=m.project_id AND h.document_id=m.document_id AND h.current_document_revision=m.document_revision WHERE f.tenant_id=? AND f.project_id=? AND f.fact_version=? ORDER BY h.document_id,h.current_document_revision LIMIT 1025 FOR UPDATE",c.production().tenantId(),c.production().projectId(),fact);
                if(impacts.size()>1024)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
                var entry=entries.addObject().put("fact_version",fact).put("relationship_count",impacts.size()).put("relationships_sha256",json.hash(impacts));var authorized=entry.putArray("selected_document_revisions");int missing=0;
                for(var impact:impacts)if(!str(impact,"document_id").equals(target)){if(selected.contains(str(impact,"current_document_revision")))authorized.add(str(impact,"current_document_revision"));else missing++;}
                entry.put("unselected_document_count",missing);if(missing>0)blocked.add("FACT_IMPACT_SELECTION_INCOMPLETE");
            }
            report.put("complete",blocked.isEmpty());report.set("blockers",json.tree(blocked));return report;
        }
        public Map<String,JsonNode> evaluate(TaskRunActivityPreparation.Current c,JsonNode candidateBody)throws Exception{
            requireUnchanged(c);var currentRules=rules.require(c.production());var all=new HashMap<String,TaskSourceVerifierRules.Rule>();currentRules.rules().forEach(r->all.put(r.ruleId(),r));
            var impactBlockers=impactBlockers(c,candidateBody);
            var required=c.production().replay().content().required("template").required("content").required("cross_document_rules");var results=new LinkedHashMap<String,JsonNode>();
            for(var requiredRule:required){String id=text(requiredRule,"rule_id");var out=JsonNodeFactory.instance.objectNode().put("rule_id",id).put("status","BLOCKED");var map=selection.input.rules().stream().filter(r->r.ruleId().equals(id)).toList();
                if(!selection.blockers.isEmpty()||!impactBlockers.isEmpty()||map.size()!=1){var blocked=new ArrayList<>(selection.blockers);blocked.addAll(impactBlockers);if(map.size()!=1)blocked.add("CROSS_FILE_MAPPING_REQUIRED:"+id);out.set("blockers",json.tree(blocked));results.put(id,out);continue;}
                var binding=map.get(0);var actual=all.get(binding.ruleRevision().version_id());if(actual==null||!actual.sha256().equals(binding.ruleRevision().content_sha256()))bad();
                var allowedLocal=new HashSet<String>();requiredRule.required("field_ids").forEach(f->allowedLocal.add(f.asText()));var keys=new HashSet<String>();var usedLocal=new HashSet<String>();var usedTypes=new HashSet<String>();var attributes=new ArrayList<ProjectProtocol.Attribute>();var inputs=out.putArray("inputs");
                for(var selector:binding.inputs()){
                    if(!keys.add(selector.inputKey()))bad();JsonNode source;DocumentReadService.FixedRead physical=null;
                    if("CANDIDATE".equals(selector.sourceKind()))source=candidateBody;
                    else {physical=loaded.get(selector.documentRevision());if(physical==null)bad();source=json.tree(physical.body());var selectedDocument=one(selection.input.documents().stream().filter(d->d.documentRevision().version_id().equals(selector.documentRevision())).toList());usedTypes.add(selectedDocument.documentType());}
                    var found=new ArrayList<JsonNode>();for(var f:source.required("fields"))if(selector.fieldId().equals(text(f,"field_id")))found.add(f);
                    JsonNode value=NullNode.instance;String status="UNKNOWN";
                    if(found.size()>1)bad();if(found.size()==1){var field=found.get(0);if(physical==null){if(!allowedLocal.contains(text(field,"field_definition_id")))bad();usedLocal.add(text(field,"field_definition_id"));}
                        if(selector.factVersion()!=null){boolean bound=false;for(var fact:source.required("fact_usages"))if(selector.factVersion().version_id().equals(text(fact,"fact_version_id")))for(var fieldId:fact.required("field_ids"))if(selector.fieldId().equals(fieldId.asText()))bound=true;if(!bound)bad();}
                        value=field.required(selector.valueSlot().toLowerCase(Locale.ROOT));if(!value.isNull()){if("DECIMAL_VALUE".equals(selector.valueSlot()))value=DecimalNode.valueOf(new BigDecimal(value.asText()));status="KNOWN";}
                    }
                    var refs=selector.factVersion()==null?List.<PermissionProtocol.Basis>of():List.of(selector.factVersion());attributes.add(new ProjectProtocol.Attribute(selector.inputKey(),status,value,refs,"Exact approved N06 field selector"));var input=inputs.addObject().put("input_key",selector.inputKey()).put("source_kind",selector.sourceKind()).put("field_id",selector.fieldId()).put("value_slot",selector.valueSlot()).put("status",status);input.set("document_revision",json.tree(selector.documentRevision()));input.set("value",value);
                }
                same(new HashSet<>(actual.content().expression().input_keys()),keys);
                var expectedTypes=new HashSet<String>();requiredRule.required("related_document_types").forEach(t->expectedTypes.add(t.asText()));
                if(!usedLocal.equals(allowedLocal)||!usedTypes.containsAll(expectedTypes)){out.put("status","BLOCKED");out.set("blockers",json.tree(List.of("RULE_INPUT_COVERAGE_INCOMPLETE:"+id)));results.put(id,out);continue;}
                var result=engine.evaluate(engine.compile(actual.content().expression()),attributes);out.put("status",switch(result.status()){case "APPLICABLE"->"PASS";case "NOT_APPLICABLE"->"FAIL";default->"BLOCKED";});out.set("rule_revision",json.tree(binding.ruleRevision()));out.set("unknown_input_keys",json.tree(result.unknown_input_keys()));out.set("source_pins",pins());results.put(id,out);
            }
            currentRules.requireUnchanged();requireUnchanged(c);return Collections.unmodifiableMap(results);
        }
    }
    private void validate(Document d,DocumentReadService.FixedRead r)throws Exception {
        var h=r.header();same(d.documentId(),h.documentId());same(d.documentRevision().version_id(),h.documentRevision());same(h.documentRevision(),h.currentDocumentRevision());same(d.documentRevision().content_sha256(),h.contentSha256());same(d.bodyArtifact().version_id(),h.bodyArtifactId());same(d.bodyArtifact().content_sha256(),h.bodySha256());same(d.templateVersion().version_id(),h.templateVersion());same(h.bodySha256(),WireValues.sha256(r.bodyBytes()));
        var actual=new HashSet<String>();r.body().factUsages().forEach(f->actual.add(f.factVersionId()));same(actual,new HashSet<>(d.factVersions().stream().map(PermissionProtocol.Basis::version_id).toList()));
    }
    private static void pin(Map<String,JsonNode> pins,PermissionProtocol.Basis basis,String kind,List<String> blockers){var pin=pins.get(basis.version_id());if(pin==null){blockers.add("NEW_TASK_INPUT_REQUIRED:"+basis.version_id());return;}same(kind,text(pin,"entity_kind"));same(basis.content_sha256(),text(pin,"content_sha256"));}
    private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> row,String k){return Objects.requireNonNull(row.get(k)).toString();}private static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).asText();}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
