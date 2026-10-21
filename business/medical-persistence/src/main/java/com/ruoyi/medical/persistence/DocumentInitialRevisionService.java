package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;

/** C04 initial document producer. Initial UUIDs are only intent identities until ALL exact
 * objects are physically read back and the real revision, schema, body, nodes and sections
 * are committed together. A retry recovers the original key; it never issues a second PUT. */
public final class DocumentInitialRevisionService {
    private final JdbcTemplate db;private final TaskSectionWriterSchema json;private final SchemaCatalogue schemas;
    private final TaskSectionWriterProducerAuthority producers;private final TemplateVersionStore templates;private final TaskRunReplayContentAuthority contents;private final DependencyManifestSchema dependencies;
    public DocumentInitialRevisionService(DataSource data,TaskSectionWriterSchema json,TemplateVersionStore templates,TaskRunReplayContentAuthority contents,DependencyManifestSchema dependencies,TaskSectionWriterProducerAuthority producers)throws Exception{
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.templates=templates;this.contents=contents;this.dependencies=dependencies;this.producers=producers;schemas=SchemaCatalogue.fromClasspath();
    }
    private TaskRunResumeOriginalAuthority resumeOriginals;
    private FormalRuntimeCapabilityIdentityAuthority roles;
    @org.springframework.beans.factory.annotation.Autowired void rolePublications(FormalRuntimeCapabilityIdentityAuthority roles){this.roles=roles;}
    private TaskWriterEvidenceContract selectedContract(TaskNodeProductionAuthority.Verified p,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception{
        if(roles==null)deny();var binding=one(policy.definition().bindings().stream().filter(x->"N04".equals(x.nodeKind())).toList());
        var role=roles.require(p.admission(),binding.roleVersion(),"ROLE",p.projectId(),"RUN_CREATION");role.requireUnchanged();
        return TaskWriterEvidenceContract.require(role.canonicalDefinition().required("request"),schemas);
    }
    /** Immutable initial row digest selects its original publication; never the successor payload.
     * Initial intent/receipt and artifact registry metadata remain their existing v1 envelopes.
     * The body bytes, schema source/version and artifact_content_binding carry the actual body publication. */
    private TaskWriterEvidenceContract savedContract(Map<String,Object> row)throws Exception{
        String hash=str(row,"schema_sha256");if(schemas.schemaDigest("interfaces").equals(hash))return new TaskWriterEvidenceContract(false);
        if(schemas.schemaDigest("document-body-v2").equals(hash))return new TaskWriterEvidenceContract(true);bad();return null;
    }
    @org.springframework.beans.factory.annotation.Autowired void originalReaders(TaskRunResumeOriginalAuthority originals){this.resumeOriginals=originals;}
    /** Reuse the actual parent initial document only through a verified successful N03 alias. */
    public Optional<Verified> resumeInitial(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        p.requireUnchanged();var aliases=db.queryForList("SELECT step_id FROM task_run_success_reuse WHERE tenant_id=? AND run_id=? AND stage_kind='N03' LIMIT 2 FOR UPDATE",p.tenantId(),p.runId());
        if(aliases.isEmpty())return Optional.empty();if(resumeOriginals==null)deny();var source=resumeOriginals.requireSaved(p,str(one(aliases),"step_id"));
        if(originalDocumentRun(source)==null)return Optional.empty();return Optional.of(requireRegistered(originalPrepared(source,publication,policy)));
    }
    private String originalDocumentRun(TaskRunResumeOriginalAuthority.Source source)throws Exception {
        source.requireUnchanged();var p=source.successor();var runs=new LinkedHashSet<String>();runs.add(text(source.pins(),"direct_parent_run_id"));for(var link:source.pins().required("reuse_chain"))runs.add(text(link,"run_id"));runs.add(source.originalRunId());
        for(String run:runs){var rows=db.queryForList("SELECT run_id FROM document_initial_intent WHERE tenant_id=? AND project_id=? AND task_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),run);if(!rows.isEmpty()){one(rows);return run;}}return null;
    }
    private Prepared originalPrepared(TaskRunResumeOriginalAuthority.Source source,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy)throws Exception {
        source.requireUnchanged();var p=source.successor();p.requireUnchanged();publication.requireUnchanged();policy.requireUnchanged();
        String originalRun=originalDocumentRun(source);if(originalRun==null)bad();var row=one(db.queryForList("SELECT * FROM document_initial_intent WHERE tenant_id=? AND project_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),originalRun));
        requireInitialMetadata(p,row,originalRun);var pins=json.read(str(row,"producer_pins_json"));
        var role=one(policy.definition().bindings().stream().filter(x->"N04".equals(x.nodeKind())).toList()).roleVersion();
        var producer=producers.requireOriginalInitial(source,publication,policy,role,text(pins,"producer_rule_id"),text(pins,"producer_sha256"));if(!pins.equals(producer.canonicalPins()))stale();
        var replay=p.replay().content();var template=templates.applicationTarget(p.admission(),text(replay.required("template"),"template_version"),"DOCUMENT_BODY");
        if(!json.tree(template.content()).equals(replay.required("template").required("content")))bad();var closure=contents.require(p);var sections=sections(p);
        if(!new HashSet<>(producer.sectionIds()).containsAll(sections.stream().map(x->str(x,"section_id")).toList()))deny();
        var contract=savedContract(row);var body=json.read(str(row,"canonical_body"));var intent=json.read(str(row,"intent_json"));
        if(!json.hash(body).equals(str(row,"body_sha256"))||!json.hash(intent).equals(str(row,"intent_sha256"))||!blank(json,template.content(),text(replay.required("template"),"template_version"),str(row,"document_id"),contract.version()).equals(body)||!contract.bodySha().equals(str(row,"schema_sha256"))||!WireValues.sha256(str(row,"schema_source").getBytes(StandardCharsets.UTF_8)).equals(str(row,"schema_sha256")))bad();
        for(String key:List.of("document_id","document_revision","body_schema_revision","body_sha256","schema_sha256","authority_sha256"))if(!str(row,key).equals(text(intent,key)))bad();
        DocumentBodySemantics.verify(schemas,body,new DocumentBodySemantics.Limits(10000,10000,100000),contract.bodyRef(),contract.bodySha());producer.requireBytes("DOCUMENT_BODY",json.bytes(body).length);closure.requireUnchanged();source.requireUnchanged();
        var prepared=new Prepared(p,publication,policy,producer,closure,row,sections);prepared.originalSource=source;return prepared;
    }
    public Prepared prepare(TaskRunActivityPreparation.Current current,TaskSectionWriterProducerAuthority.Initial producer)throws Exception{
        return prepare(current.production(),current.publication(),current.policy(),producer);
    }
    private Prepared prepare(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSectionWriterProducerAuthority.Initial producer)throws Exception{
        p.requireUnchanged();publication.requireUnchanged();policy.requireUnchanged();producer.requireFor(p);var contract=selectedContract(p,policy);var replay=p.replay().content();
        var template=templates.applicationTarget(p.admission(),text(replay.required("template"),"template_version"),"DOCUMENT_BODY");
        if(!json.tree(template.content()).equals(replay.required("template").required("content")))bad();
        var closure=contents.require(p);var sections=sections(p);var allowed=new HashSet<>(producer.sectionIds());
        if(!allowed.containsAll(sections.stream().map(r->str(r,"section_id")).toList()))deny();
        var rows=db.queryForList("SELECT * FROM document_initial_intent WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId());
        String authority=json.hash(json.tree(Map.of("production",p.authorizationsSha256(),"producer",producer.canonicalPins(),"template",replay.required("template"),"sections",sections,"source_members",closure.members())));
        if(rows.isEmpty()){
            String document=id(),revision=id(),schema=id();var body=blank(json,template.content(),text(replay.required("template"),"template_version"),document,contract.version());
            DocumentBodySemantics.verify(schemas,body,new DocumentBodySemantics.Limits(10000,10000,100000),contract.bodyRef(),contract.bodySha());
            byte[] source;try(var in=Objects.requireNonNull(SchemaCatalogue.class.getClassLoader().getResourceAsStream(contract.v2()?"backend-design/document-body-v2.schema.json":"backend-design/interfaces.schema.yaml"))){source=in.readAllBytes();}
            if(!WireValues.sha256(source).equals(contract.bodySha()))bad();
            var intent=json.tree(Map.of("document_id",document,"document_revision",revision,"body_schema_revision",schema,"body_sha256",json.hash(body),"schema_sha256",WireValues.sha256(source),"authority_sha256",authority));
            var row=meta(p);row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",schemas.schemaDigest("interfaces"));row.putAll(Map.of("run_id",p.runId(),"task_id",p.taskId(),"document_id",document,"document_revision",revision,"body_schema_revision",schema,"schema_sha256",WireValues.sha256(source),"schema_source",new String(source,StandardCharsets.UTF_8),"canonical_body",json.canonical(body),"body_sha256",json.hash(body)));
            row.put("producer_pins_json",json.canonical(producer.canonicalPins()));row.put("authority_sha256",authority);row.put("intent_json",json.canonical(intent));row.put("intent_sha256",json.hash(intent));insert("document_initial_intent",row);
        }
        var row=one(db.queryForList("SELECT * FROM document_initial_intent WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId()));
        requireInitialMetadata(p,row);if(!authority.equals(str(row,"authority_sha256"))||!json.canonical(producer.canonicalPins()).equals(str(row,"producer_pins_json"))||!p.actorId().equals(str(row,"created_by")))stale();
        var heads=db.queryForList("SELECT current_document_revision FROM document_head WHERE tenant_id=? AND project_id=? AND document_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),str(row,"document_id"));
        if(!heads.isEmpty()){var original=one(db.queryForList("SELECT document_revision FROM document_initial_registration WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.tenantId(),p.runId()));if(!str(row,"document_revision").equals(str(original,"document_revision")))bad();}
        var body=json.read(str(row,"canonical_body"));if(!json.hash(body).equals(str(row,"body_sha256"))||!contract.bodySha().equals(str(row,"schema_sha256"))||!WireValues.sha256(str(row,"schema_source").getBytes(StandardCharsets.UTF_8)).equals(str(row,"schema_sha256"))||!json.hash(json.read(str(row,"intent_json"))).equals(str(row,"intent_sha256")))bad();
        if(!blank(json,template.content(),text(replay.required("template"),"template_version"),str(row,"document_id"),contract.version()).equals(body))bad();
        producer.requireBytes("DOCUMENT_BODY",json.bytes(body).length);p.requireUnchanged();closure.requireUnchanged();return new Prepared(p,publication,policy,producer,closure,row,sections);
    }
    public Optional<Verified> requireForInput(TaskNodeProductionAuthority.Verified p,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,String document,String revision)throws Exception{
        var resumed=resumeInitial(p,publication,policy);if(resumed.isPresent()){resumed.get().requireInput(p,document,revision);return resumed;}
        p.requireUnchanged();var rows=db.queryForList("SELECT i.producer_pins_json FROM document_initial_intent i WHERE i.tenant_id=? AND i.project_id=? AND i.run_id=? AND i.document_id=? AND i.document_revision=? LIMIT 2 FOR UPDATE",p.tenantId(),p.projectId(),p.runId(),document,revision);if(rows.isEmpty())return Optional.empty();var row=one(rows);
        var pins=json.read(str(row,"producer_pins_json"));var role=one(policy.definition().bindings().stream().filter(b->"N04".equals(b.nodeKind())).toList()).roleVersion();
        var initial=producers.requireInitial(p,publication,policy,role,text(pins,"producer_rule_id"),text(pins,"producer_sha256"));
        if(!pins.equals(initial.canonicalPins()))stale();var proof=requireRegistered(prepare(p,publication,policy,initial));proof.requireInput(p,document,revision);return Optional.of(proof);
    }
    /** Empty scaffolding is not a factual FieldValue candidate or satisfied requirement. */
    static JsonNode blank(TaskSectionWriterSchema json,TemplateProtocol.Content template,String version,String document){
        return blank(json,template,version,document,"1.0.0");
    }
    static JsonNode blank(TaskSectionWriterSchema json,TemplateProtocol.Content template,String version,String document,String bodyVersion){
        var nodes=new ArrayList<InterfaceProtocol.Node>();var fields=new ArrayList<InterfaceProtocol.FieldValue>();
        nodes.add(new InterfaceProtocol.Node(document,null,0,List.of(),List.of(),new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT,template.document_type())));
        var remaining=new ArrayList<>(template.nodes());var emitted=new HashSet<String>();
        while(!remaining.isEmpty()){
            int before=remaining.size();for(var it=remaining.iterator();it.hasNext();){var n=it.next();if(n.parent_id()!=null&&!emitted.contains(n.parent_id()))continue;
                var ids=template.field_definitions().stream().filter(f->f.node_id().equals(n.node_id())).map(TemplateProtocol.Field::field_id).toList();
                nodes.add(new InterfaceProtocol.Node(n.node_id(),n.parent_id()==null?document:n.parent_id(),n.ordinal(),ids,List.of(),new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.SECTION,n.title())));
                for(var f:template.field_definitions())if(f.node_id().equals(n.node_id()))fields.add(new InterfaceProtocol.FieldValue(f.field_id(),n.node_id(),f.field_id(),"",null,f.constraints().unit(),null,null,List.of()));
                emitted.add(n.node_id());it.remove();}
            if(before==remaining.size())bad();
        }
        return json.tree(new InterfaceProtocol.DocumentBody(bodyVersion,version,nodes,fields,List.of(),List.of()));
    }
    public List<Attempt> attempts(Prepared p)throws Exception{
        if(p.originalSource!=null)deny();p.requireUnchanged();var result=new ArrayList<Attempt>();for(String kind:List.of("DOCUMENT_BODY","DOCUMENT_FIELDS","DOCUMENT_EVIDENCE")){
            var rows=db.queryForList("SELECT * FROM document_initial_object WHERE tenant_id=? AND run_id=? AND kind=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().runId(),kind);boolean prior=!rows.isEmpty();
            byte[] bytes=payload(p,kind);p.producer.requireBytes("DOCUMENT_BODY",bytes.length);
            if(!prior){var row=new LinkedHashMap<String,Object>();String write=id(),artifact=id();row.putAll(Map.of("tenant_id",p.production().tenantId(),"run_id",p.production().runId(),"kind",kind,"write_id",write,"artifact_id",artifact,"object_key","tenants/"+p.production().tenantId()+"/document-initial/"+p.production().runId()+"/"+write+"/"+artifact+".json","canonical_payload",new String(bytes,StandardCharsets.UTF_8),"payload_sha256",WireValues.sha256(bytes),"intent_sha256",str(p.row,"intent_sha256"),"created_by",p.production().actorId()));row.put("created_at",now());row.put("project_id",p.production().projectId());row.put("classification",p.production().classification());row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",schemas.schemaDigest("interfaces"));insert("document_initial_object",row);}
            var row=one(db.queryForList("SELECT * FROM document_initial_object WHERE tenant_id=? AND run_id=? AND kind=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().runId(),kind));verifyObject(p,row,bytes);result.add(new Attempt(row,prior));
        }p.requireUnchanged();return List.copyOf(result);
    }
    public void requireTransfer(Prepared p,Attempt a)throws Exception{if(p.originalSource!=null)deny();p.requireUnchanged();if(a.owner!=this||!a.committed||a.thread!=Thread.currentThread())deny();var r=one(db.queryForList("SELECT * FROM document_initial_object WHERE tenant_id=? AND run_id=? AND kind=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().runId(),a.kind()));verifyObject(p,r,payload(p,a.kind()));if(!str(r,"write_id").equals(a.writeIntent().writeId()))bad();}
    private byte[] payload(Prepared p,String kind)throws Exception{if("DOCUMENT_BODY".equals(kind))return json.bytes(p.body());var contract=savedContract(p.row);var body=DocumentEvidenceBindings.bind(schemas,InterfaceBindings.DOCUMENT_BODY,p.body(),contract.bodyRef(),contract.bodySha()).read();var value="DOCUMENT_FIELDS".equals(kind)?json.tree(new InterfaceProtocol.DocumentFieldProjection(contract.version(),p.documentRevision(),str(p.row,"body_sha256"),body.fields())):json.tree(new InterfaceProtocol.DocumentEvidenceProjection(contract.version(),p.documentRevision(),str(p.row,"body_sha256"),List.of(),List.of()));return schemas.bind(contract.body(),value,"DOCUMENT_FIELDS".equals(kind)?"DocumentFieldProjection":"DocumentEvidenceProjection").canonicalBytes();}
    private void verifyObject(Prepared p,Map<String,Object> r,byte[] bytes)throws Exception{requireInitialMetadata(p.production(),r);if(!p.production().actorId().equals(str(r,"created_by"))||!str(p.row,"intent_sha256").equals(str(r,"intent_sha256"))||!WireValues.sha256(bytes).equals(str(r,"payload_sha256"))||!Arrays.equals(bytes,str(r,"canonical_payload").getBytes(StandardCharsets.UTF_8)))bad();}
    public Verified register(Prepared p,List<Attempt> attempts,List<RuntimeObjectTransport.VerifiedTransfer> transfers)throws Exception{
        if(p.originalSource!=null)deny();p.requireUnchanged();if(attempts.size()!=3||transfers.size()!=3)bad();var objects=new LinkedHashMap<String,RuntimeObjectTransport.VerifiedTransfer>();
        for(int x=0;x<attempts.size();x++){var a=attempts.get(x);var transfer=transfers.get(x);requireTransfer(p,a);if(transfer.originalAttempt()!=a||!transfer.rawSha256().equals(a.writeIntent().sha256())||objects.putIfAbsent(a.kind(),transfer)!=null)bad();}
        var prior=db.queryForList("SELECT * FROM document_initial_registration WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().runId());
        if(prior.isEmpty())persist(p,attempts,objects);
        var verified=requireRegistered(p);for(var a:attempts)if(!json.tree(objects.get(a.kind()).reference()).equals(verified.receipt.required("objects").required(a.kind()).required("object_ref")))bad();return verified;
    }
    public Verified requireRegistered(Prepared p)throws Exception{
        p.requireUnchanged();var contract=savedContract(p.row);var row=one(db.queryForList("SELECT * FROM document_initial_registration WHERE tenant_id=? AND run_id=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.originalSource==null?p.production().runId():str(p.row,"run_id")));var receipt=json.read(str(row,"receipt_json"));requireInitialMetadata(p.production(),row,p.originalSource==null?p.production().runId():str(p.row,"run_id"));
        if(!json.hash(receipt).equals(str(row,"receipt_sha256"))||!p.documentId().equals(str(row,"document_id"))||!p.documentRevision().equals(str(row,"document_revision"))||!str(p.row,"intent_sha256").equals(text(receipt,"intent_sha256")))bad();
        var head=one(db.queryForList("SELECT m.document_revision AS selected_document_revision,m.body_sha256,m.body_schema_revision,m.schema_revision,m.body_artifact_id,m.template_version,m.plan_revision,m.snapshot_id FROM document_head h JOIN document_revision_meta m ON m.tenant_id=h.tenant_id AND m.project_id=h.project_id AND m.document_id=h.document_id AND m.document_revision=? WHERE h.tenant_id=? AND h.project_id=? AND h.document_id=? AND h.task_id=? LIMIT 2 FOR UPDATE",p.documentRevision(),p.production().tenantId(),p.production().projectId(),p.documentId(),p.production().taskId()));
        if(!contract.version().equals(str(head,"schema_revision"))||!p.documentRevision().equals(str(head,"selected_document_revision"))||!str(p.row,"body_sha256").equals(str(head,"body_sha256"))||!str(p.row,"body_schema_revision").equals(str(head,"body_schema_revision")))bad();
        for(String kind:List.of("DOCUMENT_BODY","DOCUMENT_FIELDS","DOCUMENT_EVIDENCE")){
            var ref=receipt.required("objects").required(kind);var artifact=one(db.queryForList("SELECT * FROM artifact WHERE tenant_id=? AND project_id=? AND artifact_id=? AND document_revision=? AND artifact_kind=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().projectId(),text(ref,"artifact_id"),p.documentRevision(),kind));
            for(String field:List.of("object_key","object_version","sha256","size_bytes","media_type"))if(!str(artifact,field).equals(text(ref.required("object_ref"),field)))bad();
            if(!WireValues.sha256(payload(p,kind)).equals(str(artifact,"sha256")))bad();
            var object=ref.required("object_ref");StoredObjectReferences.decode(str(artifact,"object_ref"),new InterfaceProtocol.ObjectRef(text(object,"object_key"),text(object,"object_version"),text(object,"sha256"),text(object,"size_bytes"),text(object,"media_type")));
            var binding=one(db.queryForList("SELECT * FROM artifact_content_binding WHERE tenant_id=? AND project_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().projectId(),text(ref,"artifact_id")));
            String definition="DOCUMENT_BODY".equals(kind)?"DocumentBody":"DOCUMENT_FIELDS".equals(kind)?"DocumentFieldProjection":"DocumentEvidenceProjection";
            if(!p.documentId().equals(str(binding,"resource_id"))||!"DOCUMENT".equals(str(binding,"resource_kind"))||!schemas.schemaRef(contract.body(),definition).equals(str(binding,"schema_ref"))||!contract.bodySha().equals(str(binding,"schema_document_sha256"))||!str(artifact,"dependency_access_set_id").equals(str(binding,"dependency_access_set_id"))||!json.hash(receipt.required("dependency_manifest")).equals(str(binding,"dependency_manifest_sha256")))bad();
            var version=one(db.queryForList("SELECT content_sha256,entity_kind FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2 FOR UPDATE",p.production().tenantId(),text(ref,"artifact_id")));if(!str(artifact,"sha256").equals(str(version,"content_sha256"))||!"ARTIFACT".equals(str(version,"entity_kind")))bad();

        }
        var dependency=receipt.required("dependency_manifest");var storedDependency=one(db.queryForList("SELECT manifest_json,manifest_sha256 FROM dependency_access_manifest WHERE tenant_id=? AND project_id=? AND access_set_id=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.production().projectId(),text(dependency,"access_set_id")));if(!dependency.equals(json.read(str(storedDependency,"manifest_json")))||!json.hash(dependency).equals(str(storedDependency,"manifest_sha256")))bad();
        var actualMembers=db.query("SELECT upstream_version_id,rights_revision FROM dependency_access_member WHERE tenant_id=? AND access_set_id=? FOR UPDATE",(rs,n)->new ArtifactRegistryProtocol.MemberRow(rs.getString(1),rs.getString(2)),p.production().tenantId(),text(dependency,"access_set_id"));if(!new HashSet<>(actualMembers).equals(new HashSet<>(p.closure.members())))bad();
        var schema=one(db.queryForList("SELECT content_sha256,entity_kind,owner_project_id FROM version_registry WHERE tenant_id=? AND version_id=? LIMIT 2 FOR UPDATE",p.production().tenantId(),p.bodySchemaRevision()));
        if(!str(p.row,"schema_sha256").equals(str(schema,"content_sha256"))||!"DOCUMENT_BODY_SCHEMA".equals(str(schema,"entity_kind"))||!p.production().projectId().equals(str(schema,"owner_project_id")))bad();
        var nodes=db.queryForList("SELECT node_id,node_kind,parent_id,ordinal,node_sha256 FROM document_node WHERE tenant_id=? AND project_id=? AND document_revision=? FOR UPDATE",p.production().tenantId(),p.production().projectId(),p.documentRevision());
        if(nodes.size()!=p.body().required("nodes").size())bad();for(var n:p.body().required("nodes")){var saved=one(nodes.stream().filter(x->text(n,"node_id").equals(x.get("node_id"))).toList());if(!json.hash(n).equals(str(saved,"node_sha256"))||!text(n.required("content"),"kind").equals(str(saved,"node_kind"))||!Objects.equals(n.required("parent_id").isNull()?null:text(n,"parent_id"),saved.get("parent_id"))||n.required("ordinal").longValue()!=((Number)saved.get("ordinal")).longValue())bad();}
        var fields=db.queryForList("SELECT field_id,value_json FROM field_value WHERE tenant_id=? AND project_id=? AND document_revision=? FOR UPDATE",p.production().tenantId(),p.production().projectId(),p.documentRevision());if(fields.size()!=p.body().required("fields").size())bad();for(var f:p.body().required("fields")){var saved=one(fields.stream().filter(x->text(f,"field_id").equals(x.get("field_id"))).toList());if(!f.equals(json.read(str(saved,"value_json"))))bad();}
        var sections=db.queryForList("SELECT d.section_id,d.ordinal,s.body_ref,s.content_sha256,s.plan_revision FROM document_section d JOIN section_revision s ON s.tenant_id=d.tenant_id AND s.project_id=d.project_id AND s.section_revision=d.section_revision AND s.section_id=d.section_id WHERE d.tenant_id=? AND d.project_id=? AND d.document_revision=? ORDER BY d.ordinal FOR UPDATE",p.production().tenantId(),p.production().projectId(),p.documentRevision());if(sections.size()!=p.sections.size())bad();
        for(int x=0;x<sections.size();x++){var saved=sections.get(x);if(!str(p.sections.get(x),"section_id").equals(str(saved,"section_id"))||((Number)saved.get("ordinal")).longValue()!=x||!sectionBody(p.body(),json.read(str(p.sections.get(x),"node_ids"))).equals(json.read(str(saved,"body_ref")))||!json.hash(json.read(str(saved,"body_ref"))).equals(str(saved,"content_sha256"))||!text(p.production().replay().content(),"plan_revision").equals(str(saved,"plan_revision")))bad();}
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND action='document.initial.created' AND result='SUCCESS' LIMIT 2 FOR UPDATE",p.production().tenantId(),text(receipt,"audit_id"),p.production().actorId()));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND event_type='document.initial.created' LIMIT 2 FOR UPDATE",p.production().tenantId(),text(receipt,"event_id")));if(!receipt.equals(json.read(str(audit,"resource_ref")))||!receipt.equals(json.read(str(event,"payload"))))bad();return new Verified(p,receipt,str(row,"receipt_sha256"));
    }
    private void persist(Prepared p,List<Attempt> attempts,Map<String,RuntimeObjectTransport.VerifiedTransfer> physical)throws Exception{
        var production=p.production();var contract=savedContract(p.row);String access=id();var at=now();var members=p.closure.members().stream().sorted(Comparator.comparing(ArtifactRegistryProtocol.MemberRow::upstreamVersionId)).toList();
        var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",production.tenantId(),production.projectId(),access,members);String dependencyHash=json.hash(manifest);
        version(p,access,"DEPENDENCY_ACCESS_SET",dependencyHash);version(p,str(p.row,"body_schema_revision"),"DOCUMENT_BODY_SCHEMA",str(p.row,"schema_sha256"));version(p,p.documentRevision(),"DOCUMENT_REVISION",str(p.row,"body_sha256"));
        var set=meta(production);set.putAll(Map.of("access_set_id",access,"manifest_sha256",dependencyHash,"content_sha256",dependencyHash));insert("dependency_access_set",set);
        var full=meta(production);full.putAll(Map.of("access_set_id",access,"manifest_json",json.canonical(manifest),"manifest_sha256",dependencyHash,"content_sha256",dependencyHash,"member_count",members.size(),"schema_version","1.0.0","schema_manifest_sha256",dependencies.manifestSha256()));insert("dependency_access_manifest",full);
        for(var member:members){var row=meta(production);row.remove("project_id");row.putAll(Map.of("access_set_id",access,"upstream_version_id",member.upstreamVersionId(),"rights_revision",member.rightsRevision()));insert("dependency_access_member",row);}
        var head=meta(production);head.putAll(Map.of("document_id",p.documentId(),"task_id",production.taskId(),"status","DRAFT","revision",1,"updated_at",at));head.put("current_document_revision",null);insert("document_head",head);
        var revision=new LinkedHashMap<String,Object>();revision.putAll(Map.of("tenant_id",production.tenantId(),"project_id",production.projectId(),"document_revision",p.documentRevision(),"document_id",p.documentId(),"snapshot_id",text(production.replay().content(),"snapshot_id"),"body_sha256",str(p.row,"body_sha256"),"body_object_key",physical.get("DOCUMENT_BODY").reference().objectKey()));insert("document_revision",revision);
        var objectRefs=JsonNodeFactory.instance.objectNode();String bodyArtifact=null;
        for(var attempt:attempts){String artifact=attempt.artifactId(),kind=attempt.kind();var object=physical.get(kind).reference();version(p,artifact,"ARTIFACT",object.sha256());
            var row=meta(production);row.putAll(Map.of("artifact_id",artifact,"run_id",production.runId(),"document_revision",p.documentRevision(),"artifact_kind",kind,"object_ref",StoredObjectReferences.encode(object),"object_key",object.objectKey(),"object_version",object.objectVersion(),"sha256",object.sha256(),"size_bytes",object.sizeBytes(),"media_type",object.mediaType()));row.putAll(Map.of("dependency_access_set_id",access,"content_sha256",object.sha256(),"schema_version","1.0.0","schema_manifest_sha256",schemas.schemaDigest("interfaces")));insert("artifact",row);
            String definition="DOCUMENT_BODY".equals(kind)?"DocumentBody":"DOCUMENT_FIELDS".equals(kind)?"DocumentFieldProjection":"DocumentEvidenceProjection";
            var binding=meta(production);binding.putAll(Map.of("artifact_id",artifact,"resource_kind","DOCUMENT","resource_id",p.documentId(),"schema_ref",schemas.schemaRef(contract.body(),definition),"schema_document_sha256",contract.bodySha(),"dependency_access_set_id",access,"dependency_manifest_sha256",dependencyHash));insert("artifact_content_binding",binding);
            for(String root:p.closure.roots())insert("lineage_edge",Map.of("tenant_id",production.tenantId(),"consumer_version_id",artifact,"upstream_version_id",root,"relation_kind","DOCUMENT_INITIAL_SOURCE","actor_id",production.actorId(),"created_at",at));
            var ref=objectRefs.putObject(kind);ref.put("artifact_id",artifact).put("write_id",attempt.writeIntent().writeId());ref.set("object_ref",json.tree(object));if("DOCUMENT_BODY".equals(kind))bodyArtifact=artifact;
        }
        var replay=production.replay().content();var meta=meta(production);meta.putAll(Map.of("document_revision",p.documentRevision(),"document_id",p.documentId(),"task_id",production.taskId(),"task_revision",text(replay,"task_revision"),"snapshot_id",text(replay,"snapshot_id"),"body_object_version",physical.get("DOCUMENT_BODY").reference().objectVersion(),"schema_revision",contract.version(),"body_schema_revision",str(p.row,"body_schema_revision"),"body_artifact_id",Objects.requireNonNull(bodyArtifact),"body_sha256",str(p.row,"body_sha256")));
        meta.putAll(Map.of("plan_revision",text(replay,"plan_revision"),"template_version",text(replay.required("template"),"template_version"),"issue_watermark",0,"dependency_access_set_id",access,"content_sha256",str(p.row,"body_sha256")));insert("document_revision_meta",meta);insert("lineage_edge",Map.of("tenant_id",production.tenantId(),"consumer_version_id",p.documentRevision(),"upstream_version_id",bodyArtifact,"relation_kind","DOCUMENT_BODY","actor_id",production.actorId(),"created_at",at));
        for(var n:p.body().required("nodes")){var node=meta(production);node.putAll(Map.of("document_revision",p.documentRevision(),"node_id",text(n,"node_id"),"node_kind",text(n.required("content"),"kind"),"ordinal",n.required("ordinal").longValue(),"node_sha256",json.hash(n)));node.put("parent_id",n.required("parent_id").isNull()?null:text(n,"parent_id"));insert("document_node",node);}
        for(var f:p.body().required("fields")){var field=meta(production);field.putAll(Map.of("document_revision",p.documentRevision(),"template_version",text(replay.required("template"),"template_version"),"field_id",text(f,"field_id"),"field_definition_id",text(f,"field_definition_id"),"node_id",text(f,"node_id"),"value_json",json.canonical(f),"raw_value","","status","DRAFT","source_refs","[]","model_ids","[]"));field.put("row_id",null);field.put("fact_version",null);field.put("condition_id",null);field.put("empty_reason",null);field.put("schema_version","1.0.0");field.put("schema_manifest_sha256",schemas.schemaDigest("interfaces"));insert("field_value",field);}
        long ordinal=0;for(var section:p.sections){String sectionId=str(section,"section_id"),sectionRevision=id();var nodeIds=json.read(str(section,"node_ids"));var sectionBody=sectionBody(p.body(),nodeIds);String digest=json.hash(sectionBody);version(p,sectionRevision,"SECTION_REVISION",digest);
            var s=meta(production);s.putAll(Map.of("section_revision",sectionRevision,"section_id",sectionId,"plan_revision",text(replay,"plan_revision"),"body_ref",json.canonical(sectionBody),"evidence_manifest","[]","status","DRAFT","content_sha256",digest,"schema_version","1.0.0","schema_manifest_sha256",schemas.schemaDigest("interfaces")));insert("section_revision",s);
            var link=meta(production);link.putAll(Map.of("document_revision",p.documentRevision(),"section_id",sectionId,"section_revision",sectionRevision,"ordinal",ordinal++));insert("document_section",link);insert("lineage_edge",Map.of("tenant_id",production.tenantId(),"consumer_version_id",sectionRevision,"upstream_version_id",p.documentRevision(),"relation_kind","SECTION_DOCUMENT","actor_id",production.actorId(),"created_at",at));
        }
        var state=meta(production);state.putAll(Map.of("document_revision",p.documentRevision(),"issue_watermark",0,"evidence_watermark",0,"revision",1,"updated_at",at));insert("document_revision_state",state);
        if(db.update("UPDATE document_head SET current_document_revision=?,updated_at=? WHERE tenant_id=? AND project_id=? AND document_id=? AND current_document_revision IS NULL AND revision=1",p.documentRevision(),at,production.tenantId(),production.projectId(),p.documentId())!=1)bad();
        String audit=id(),event=id();var receipt=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("kind","DOCUMENT_INITIAL_REVISION").put("tenant_id",production.tenantId()).put("project_id",production.projectId()).put("run_id",production.runId()).put("task_id",production.taskId()).put("document_id",p.documentId()).put("document_revision",p.documentRevision()).put("body_schema_revision",str(p.row,"body_schema_revision")).put("body_sha256",str(p.row,"body_sha256")).put("intent_sha256",str(p.row,"intent_sha256")).put("audit_id",audit).put("event_id",event);
        receipt.set("objects",objectRefs);receipt.set("dependency_manifest",json.tree(manifest));receipt.set("producer_pins",p.producer.canonicalPins());String raw=json.canonical(receipt);
        var a=meta(production);a.putAll(Map.of("audit_id",audit,"subject_id",production.actorId(),"action","document.initial.created","resource_ref",raw,"result","SUCCESS","request_id",p.documentId(),"trace_id",production.runId(),"schema_version","1.0.0","schema_manifest_sha256",schemas.schemaDigest("interfaces")));insert("audit_event",a);
        insert("outbox_event",Map.of("tenant_id",production.tenantId(),"event_id",event,"aggregate_id",p.documentId(),"aggregate_revision",1,"event_type","document.initial.created","payload",raw,"occurred_at",at));
        var registration=meta(production);registration.putAll(Map.of("run_id",production.runId(),"document_id",p.documentId(),"document_revision",p.documentRevision(),"receipt_json",raw,"receipt_sha256",json.hash(receipt),"created_at",at,"schema_version","1.0.0","schema_manifest_sha256",schemas.schemaDigest("interfaces")));insert("document_initial_registration",registration);
    }
    public final class Prepared {
        private TaskRunResumeOriginalAuthority.Source originalSource;
        private final TaskNodeProductionAuthority.Verified production;private final AgentPublicationAuthority.VerifiedPublication publication;private final AgentExecutionPolicyAuthority.VerifiedPolicy policy;private final TaskSectionWriterProducerAuthority.Initial producer;private final TaskRunReplayContentAuthority.Verified closure;private final Map<String,Object> row;private final List<Map<String,Object>> sections;
        private Prepared(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication,AgentExecutionPolicyAuthority.VerifiedPolicy policy,TaskSectionWriterProducerAuthority.Initial producer,TaskRunReplayContentAuthority.Verified closure,Map<String,Object> row,List<Map<String,Object>> sections){this.production=production;this.publication=publication;this.policy=policy;this.producer=producer;this.closure=closure;this.row=Collections.unmodifiableMap(new LinkedHashMap<>(row));this.sections=List.copyOf(sections);}
        public void requireUnchanged()throws Exception{if(originalSource!=null){var fresh=originalPrepared(originalSource,publication,policy);if(!row.equals(fresh.row))stale();return;}var fresh=prepare(production,publication,policy,producer);if(!str(row,"intent_sha256").equals(str(fresh.row,"intent_sha256")))stale();}
        public TaskNodeProductionAuthority.Verified production(){production.admission();return production;}
        public String documentId(){return str(row,"document_id");}public String documentRevision(){return str(row,"document_revision");}public String bodySchemaRevision(){return str(row,"body_schema_revision");}public JsonNode body()throws Exception{return json.read(str(row,"canonical_body"));}
    }
    /** Cannot be constructed from JSON, ids, or a caller-owned receipt. */
    public final class Verified {
        private final Prepared prepared;private final JsonNode receipt;private final String receiptHash;
        private Verified(Prepared prepared,JsonNode receipt,String hash){this.prepared=prepared;this.receipt=receipt.deepCopy();receiptHash=hash;}
        public void requireUnchanged()throws Exception{var fresh=requireRegistered(prepared);if(!receiptHash.equals(fresh.receiptHash))stale();}
        public TaskNodeProductionAuthority.Verified production(){return prepared.production();}public String documentId(){return prepared.documentId();}public String documentRevision(){return prepared.documentRevision();}public String bodySchemaRevision(){return prepared.bodySchemaRevision();}public JsonNode body()throws Exception{return prepared.body();}public String receiptSha256(){return receiptHash;}public JsonNode receipt(){return receipt.deepCopy();}
        public void requireInput(TaskNodeProductionAuthority.Verified p,String document,String revision)throws Exception{requireUnchanged();if(p!=production()||!documentId().equals(document)||!documentRevision().equals(revision))deny();}
    }
    public final class Attempt implements RuntimeObjectAttempt {
        private final DocumentInitialRevisionService owner=DocumentInitialRevisionService.this;private final Map<String,Object> row;private final boolean prior;private final Thread thread=Thread.currentThread();private boolean committed;private final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
        private Attempt(Map<String,Object> row,boolean prior){this.row=Collections.unmodifiableMap(new LinkedHashMap<>(row));this.prior=prior;if(!TransactionSynchronizationManager.isSynchronizationActive())deny();TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){committed=true;}});}
        public String kind(){return str(row,"kind");}public String artifactId(){return str(row,"artifact_id");}public String tenantId(){return str(row,"tenant_id");}public String actorId(){return str(row,"created_by");}public String expectedObjectKey(){return str(row,"object_key");}public byte[] bytes(){return str(row,"canonical_payload").getBytes(StandardCharsets.UTF_8);}public boolean priorAttempt(){return prior;}
        public S3ObjectWriter.WriteIntent writeIntent(){return new S3ObjectWriter.WriteIntent(str(row,"write_id"),expectedObjectKey(),str(row,"payload_sha256"),Integer.toString(bytes().length),"application/json");}
        public TransferMode claimTransferMode(){if(!committed||thread!=Thread.currentThread()||TransactionSynchronizationManager.isActualTransactionActive())deny();return prior||!claimed.compareAndSet(false,true)?TransferMode.RECOVER_ONLY:TransferMode.INITIAL_WRITE;}
    }
    private static JsonNode sectionBody(JsonNode body,JsonNode roots){
        var descendants=new HashSet<String>();roots.forEach(n->descendants.add(n.asText()));boolean changed;
        do{changed=false;for(var node:body.required("nodes"))if(!node.required("parent_id").isNull()&&descendants.contains(text(node,"parent_id")))changed|=descendants.add(text(node,"node_id"));}while(changed);
        var result=JsonNodeFactory.instance.objectNode();var nodes=result.putArray("nodes");for(var node:body.required("nodes"))if(descendants.contains(text(node,"node_id")))nodes.add(node);
        var fields=result.putArray("fields");for(var field:body.required("fields"))if(descendants.contains(text(field,"node_id")))fields.add(field);
        result.putArray("evidence_links");result.putArray("fact_usages");return result;
    }
    private List<Map<String,Object>> sections(TaskNodeProductionAuthority.Verified p)throws Exception{
        var rows=db.queryForList("SELECT s.section_id,s.position,n.node_id,n.position node_position FROM plan_section s JOIN plan_section_node n ON n.tenant_id=s.tenant_id AND n.project_id=s.project_id AND n.plan_revision=s.plan_revision AND n.section_id=s.section_id WHERE s.tenant_id=? AND s.project_id=? AND s.plan_revision=? ORDER BY s.position,n.position FOR UPDATE",p.tenantId(),p.projectId(),text(p.replay().content(),"plan_revision"));
        var grouped=new LinkedHashMap<String,List<String>>();for(var row:rows)grouped.computeIfAbsent(str(row,"section_id"),k->new ArrayList<>()).add(str(row,"node_id"));
        if(grouped.isEmpty())bad();var result=new ArrayList<Map<String,Object>>();for(var entry:grouped.entrySet())result.add(Map.of("section_id",entry.getKey(),"node_ids",json.canonical(entry.getValue())));return List.copyOf(result);
    }

    private void requireInitialMetadata(TaskNodeProductionAuthority.Verified p,Map<String,Object> row)throws Exception{requireInitialMetadata(p,row,p.runId());}
    private void requireInitialMetadata(TaskNodeProductionAuthority.Verified p,Map<String,Object> row,String originalRun)throws Exception{if(!p.tenantId().equals(str(row,"tenant_id"))||!p.projectId().equals(str(row,"project_id"))||!originalRun.equals(str(row,"run_id"))||!p.actorId().equals(str(row,"created_by"))||!p.classification().equals(str(row,"classification"))||!"1.0.0".equals(str(row,"schema_version"))||!schemas.schemaDigest("interfaces").equals(str(row,"schema_manifest_sha256"))||row.get("created_at")==null)bad();}
    private Map<String,Object> meta(TaskNodeProductionAuthority.Verified p){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",p.tenantId());m.put("project_id",p.projectId());m.put("created_by",p.actorId());m.put("created_at",now());m.put("classification",p.classification());return m;}
    private void version(Prepared p,String id,String kind,String hash){insert("version_registry",Map.of("tenant_id",p.production().tenantId(),"version_id",id,"owner_project_id",p.production().projectId(),"entity_kind",kind,"content_sha256",hash));}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private LocalDateTime now(){return db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);}private static String id(){return UUID.randomUUID().toString();}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static String str(Map<String,Object> r,String key){var v=r.get(key);if(v==null)bad();return v.toString();}private static String text(JsonNode n,String key){return n.required(key).asText();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}
