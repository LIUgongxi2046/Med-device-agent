package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** DC03 explicit human confirmation. The private capability binds the actual read
 * candidate/base and immutable pending action; it is not constructible from wire JSON. */
public final class DocumentCandidateAdoptionService {
    private final JdbcTemplate db;private final DocumentReviewJobService reviews;
    private final SchemaCatalogue schemas;private final String schemaHash;private final DocumentBodySemantics.Limits limits=new DocumentBodySemantics.Limits(10000,10000,100000);
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    public DocumentCandidateAdoptionService(DataSource data,DocumentReviewJobService reviews)throws Exception{this.reviews=reviews;db=new JdbcTemplate(data);db.setQueryTimeout(5);schemas=SchemaCatalogue.fromClasspath();try(var in=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("document-candidate-adoption.schema.json"))){schemaHash=WireValues.sha256(in.readAllBytes());}}

    JsonNode preview(EnterpriseAccess.Admission a,String context,String key,DocumentCandidateAdoptionAuthority.Verified source,
            DocumentCandidateAdoptionProtocol.PreviewInput input)throws Exception {
        headers(a,context,key);source.requireCurrent(a);same(input.documentId(),source.header().documentId());
        same(input.baseDocumentRevision(),source.header().documentRevision());same(input.expectedDocumentRevision(),source.header().revision().toString());same(input.candidateSha256(),source.candidateSha256());
        String hash=json.hash(Map.of("operation","DC-03-PREVIEW","candidate_id",source.candidateId(),"input",input));
        var prior=operations(a,"DC-03-PREVIEW",key);if(!prior.isEmpty()){var old=one(prior);same(hash,str(old,"request_sha256"));same("COMPLETED",str(old,"state"));return json.read(str(old,"response_ref"));}
        var selected=input.mode()==DocumentCandidateAdoptionProtocol.Mode.INITIAL?source.initialRoots():input.selectedNodeIds();
        if(input.mode()==DocumentCandidateAdoptionProtocol.Mode.INITIAL)source.requireInitialBlank(a);
        var body=DocumentCandidateAdoptionMerge.merge(schemas,source.baseBody(),source.body(),selected,source.targetNodes(),limits,source.bodySchemaRef(),source.bodySchemaSha256());
        String operation=id(),pending=id();var now=now();var expires=now.plusMinutes(10);
        var arguments=json.tree(Map.of("schema_version","1.0.0","candidate_id",source.candidateId(),"actor_id",a.actorId(),"input",input,"baseline",source.baseline(),"selected_node_ids",selected,"body_sha256",json.hash(body),"auth_epoch",a.context().auth_epoch(),"governance_epoch",a.context().governance_epoch()));String digest=json.hash(arguments);
        var oldNodes=new LinkedHashSet<String>();source.baseBody().required("nodes").forEach(n->oldNodes.add(n.required("node_id").asText()));
        var newNodes=new LinkedHashSet<String>();body.required("nodes").forEach(n->newNodes.add(n.required("node_id").asText()));
        var changed=new ArrayList<String>();for(var n:source.baseBody().required("nodes")){boolean same=false;for(var v:body.required("nodes"))if(v.equals(n)){same=true;break;}if(!same)changed.add(n.required("node_id").asText());}
        newNodes.removeAll(oldNodes);
        var result=new DocumentCandidateAdoptionProtocol.Preview("1.0.0",source.candidateId(),pending,"1",digest,expires.toInstant(java.time.ZoneOffset.UTC).toString(),input.mode(),input.documentId(),input.baseDocumentRevision(),input.expectedDocumentRevision(),input.candidateSha256(),json.hash(body),selected,changed,List.copyOf(newNodes),source.checkIds(),false);
        var receipt=json.tree(result);String audit=id(),event=id();
        insertOperation(a,operation,"DC-03-PREVIEW",key,hash,input.documentId(),source.header().classification(),now);
        var action=meta(a,source.header().projectId(),source.header().classification(),now);action.putAll(Map.of("action_id",pending,"target",json.canonical(Map.of("kind","DOCUMENT_CANDIDATE_ADOPTION","candidate_id",source.candidateId(),"document_id",input.documentId())),"arguments_sha256",digest,"expected_revision",input.expectedDocumentRevision(),"status","PENDING","expires_at",expires,"revision",1,"updated_at",now));insert("pending_action",action);
        var row=new LinkedHashMap<String,Object>();row.putAll(Map.of("tenant_id",a.context().tenant_id(),"project_id",source.header().projectId(),"pending_action_id",pending,"candidate_id",source.candidateId(),"document_id",input.documentId(),"base_document_revision",input.baseDocumentRevision(),"preview_operation_id",operation,"arguments_json",json.canonical(arguments),"arguments_sha256",digest));row.putAll(Map.of("canonical_body",json.canonical(body),"body_sha256",json.hash(body),"receipt_json",json.canonical(receipt),"receipt_sha256",json.hash(receipt),"audit_id",audit,"event_id",event,"created_by",a.actorId(),"created_at",now,"classification",source.header().classification()));row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",schemaHash);
        audit(a,source.header().projectId(),source.header().classification(),audit,event,operation,pending,"document.adoption.previewed",receipt,now);insert("document_adoption_preview",row);
        if(db.update("UPDATE operation_record SET state='COMPLETED',http_status=201,response_ref=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND operation_id=? AND state='PROCESSING'",json.canonical(receipt),now,a.context().tenant_id(),operation)!=1)stale();
        source.requireCurrent(a);return receipt;
    }
    Confirmed confirm(EnterpriseAccess.Admission a,String context,String key,DocumentCandidateAdoptionAuthority.Verified source,
            DocumentCandidateAdoptionProtocol.ConfirmInput input)throws Exception {
        headers(a,context,key);source.requireCurrent(a);
        var preview=one(db.queryForList("SELECT * FROM document_adoption_preview WHERE tenant_id=? AND pending_action_id=? AND candidate_id=? AND created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),input.pendingActionId(),source.candidateId(),a.actorId()));
        requireMetadata(preview,a,source.header().projectId(),source.header().classification());var arguments=json.read(str(preview,"arguments_json"));same(json.hash(arguments),str(preview,"arguments_sha256"));same(input.argumentsSha256(),str(preview,"arguments_sha256"));same(source.baseline(),arguments.required("baseline"));same(a.actorId(),arguments.required("actor_id").asText());
        same(a.context().auth_epoch(),arguments.required("auth_epoch").asText());same(a.context().governance_epoch(),arguments.required("governance_epoch").asText());
        var action=one(db.queryForList("SELECT * FROM pending_action WHERE tenant_id=? AND action_id=? AND created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),input.pendingActionId(),a.actorId()));same("PENDING",str(action,"status"));same(input.expectedActionRevision(),str(action,"revision"));same(input.argumentsSha256(),str(action,"arguments_sha256"));
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT expires_at>CURRENT_TIMESTAMP(6) FROM pending_action WHERE tenant_id=? AND action_id=?",Boolean.class,a.context().tenant_id(),input.pendingActionId())))stale();
        var body=json.read(str(preview,"canonical_body"));same(json.hash(body),str(preview,"body_sha256"));same(json.hash(body),arguments.required("body_sha256").asText());
        String hash=json.hash(Map.of("operation","DC-03","candidate_id",source.candidateId(),"input",input));
        var prior=operations(a,"DC-03",key);String operation,adoption;
        if(prior.isEmpty()){operation=id();adoption=id();var now=now();insertOperation(a,operation,"DC-03",key,hash,source.header().documentId(),source.header().classification(),now);
            var intentRow=meta(a,source.header().projectId(),source.header().classification(),now);intentRow.putAll(Map.of("operation_id",operation,"adoption_id",adoption,"pending_action_id",input.pendingActionId(),"candidate_id",source.candidateId(),"arguments_sha256",input.argumentsSha256(),"body_sha256",json.hash(body)));insert("document_adoption_intent",intentRow);
        }else{var op=one(prior);same(hash,str(op,"request_sha256"));same("PROCESSING",str(op,"state"));operation=str(op,"operation_id");var original=one(db.queryForList("SELECT adoption_id,pending_action_id FROM document_adoption_intent WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),operation));same(input.pendingActionId(),str(original,"pending_action_id"));adoption=str(original,"adoption_id");}
        var selected=new ArrayList<String>();arguments.required("selected_node_ids").forEach(n->selected.add(n.asText()));
        var result=new Confirmed(a,context,source,operation,adoption,input.pendingActionId(),input.argumentsSha256(),hash,key,body,selected,source.contentMembers(),source.contentRoots());result.requireUnchanged(a);return result;
    }
    private void headers(EnterpriseAccess.Admission a,String context,String key){if(!TransactionSynchronizationManager.isActualTransactionActive()||!a.context().context_revision().equals(context))deny();if(key==null||!key.matches("[!-~]{16,128}"))deny();}
    private List<Map<String,Object>> operations(EnterpriseAccess.Admission a,String name,String key){return db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND subject_id=? AND operation_name=? AND idempotency_key=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),a.actorId(),name,key);}
    private void insertOperation(EnterpriseAccess.Admission a,String id,String name,String key,String hash,String target,String classification,java.time.LocalDateTime now){var row=meta(a,null,classification,now);row.remove("project_id");row.putAll(Map.of("operation_id",id,"subject_id",a.actorId(),"operation_name",name,"idempotency_key",key,"request_sha256",hash,"state","PROCESSING","target_id",target,"revision",1,"updated_at",now));insert("operation_record",row);}
    private Map<String,Object> meta(EnterpriseAccess.Admission a,String project,String classification,java.time.LocalDateTime now){var row=new LinkedHashMap<String,Object>();row.putAll(Map.of("tenant_id",a.context().tenant_id(),"created_by",a.actorId(),"created_at",now,"classification",classification,"schema_version","1.0.0","schema_manifest_sha256",schemaHash));if(project!=null)row.put("project_id",project);return row;}
    private void audit(EnterpriseAccess.Admission a,String project,String classification,String audit,String event,String operation,String aggregate,String kind,JsonNode receipt,java.time.LocalDateTime now)throws Exception{var row=meta(a,project,classification,now);row.putAll(Map.of("audit_id",audit,"subject_id",a.actorId(),"action",kind,"resource_ref",json.canonical(receipt),"result","SUCCESS","request_id",operation,"trace_id",aggregate));insert("audit_event",row);insert("outbox_event",Map.of("tenant_id",a.context().tenant_id(),"event_id",event,"aggregate_id",aggregate,"aggregate_revision",1,"event_type",kind,"payload",json.canonical(receipt),"occurred_at",now));}
    private void insert(String table,Map<String,Object> row){db.update("INSERT INTO "+table+" ("+String.join(",",row.keySet())+") VALUES ("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray());}
    private java.time.LocalDateTime now(){return Objects.requireNonNull(db.queryForObject("SELECT CURRENT_TIMESTAMP(6)",java.time.LocalDateTime.class));}
    private void requireMetadata(Map<String,Object> row,EnterpriseAccess.Admission a,String project,String classification){same(a.context().tenant_id(),str(row,"tenant_id"));same(project,str(row,"project_id"));same(a.actorId(),str(row,"created_by"));same(classification,str(row,"classification"));same("1.0.0",str(row,"schema_version"));same(schemaHash,str(row,"schema_manifest_sha256"));}
    private static String id(){return UUID.randomUUID().toString();}

    JsonNode original(EnterpriseAccess.Admission a,String context,String key,String candidate,DocumentCandidateAdoptionProtocol.ConfirmInput input,
            DocumentReadService.FixedRead saved,DocumentReadService documents,ResourceReadAuthority resources)throws Exception {
        headers(a,context,key);var prior=operations(a,"DC-03",key);if(prior.isEmpty())return null;
        var operation=one(prior);same(json.hash(Map.of("operation","DC-03","candidate_id",candidate,"input",input)),str(operation,"request_sha256"));
        if(!"COMPLETED".equals(str(operation,"state")))return null;
        documents.requireCurrent(a,saved);var h=saved.header();resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"DOCUMENT",h.documentId()),"document.write","DOCUMENT_BODY");resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"CANDIDATE",candidate),"candidate.adopt","DOCUMENT_BODY");
        var response=json.read(str(operation,"response_ref"));same(h.documentRevision(),response.required("data").required("document_revision").asText());same(h.bodySha256(),response.required("data").required("body_sha256").asText());same(candidate,response.required("data").required("candidate_id").asText());same(input.pendingActionId(),response.required("data").required("pending_action_id").asText());
        var completion=one(db.queryForList("SELECT * FROM document_adoption_completion WHERE tenant_id=? AND operation_id=? AND created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(operation,"operation_id"),a.actorId()));requireMetadata(completion,a,h.projectId(),h.classification());var receipt=json.read(str(completion,"receipt_json"));same(json.hash(receipt),str(completion,"receipt_sha256"));same(response.required("data"),receipt.required("data"));same(input.argumentsSha256(),receipt.required("decision").required("arguments_sha256").asText());
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND action='document.candidate.adopted' AND result='SUCCESS' LIMIT 2",a.context().tenant_id(),str(completion,"audit_id"),a.actorId()));same(receipt,json.read(str(audit,"resource_ref")));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND event_type='document.candidate.adopted' LIMIT 2",a.context().tenant_id(),str(completion,"event_id")));same(receipt,json.read(str(event,"payload")));
        var adoption=one(db.queryForList("SELECT candidate_id,result_document_revision FROM candidate_adoption WHERE tenant_id=? AND project_id=? AND adoption_id=? AND operation_id=? LIMIT 2",h.tenantId(),h.projectId(),str(completion,"adoption_id"),str(operation,"operation_id")));same(candidate,str(adoption,"candidate_id"));same(h.documentRevision(),str(adoption,"result_document_revision"));
        var action=one(db.queryForList("SELECT status,arguments_sha256,decision_id FROM pending_action WHERE tenant_id=? AND action_id=? AND actor_id=? LIMIT 2",h.tenantId(),input.pendingActionId(),a.actorId()));same("CONFIRMED",str(action,"status"));same(input.argumentsSha256(),str(action,"arguments_sha256"));same(str(completion,"decision_id"),str(action,"decision_id"));
        same(h.documentRevision(),reviews.original(a,response.required("data").required("check_document_intent_id").asText()).required("document_revision").asText());return response;
    }

    JsonNode consume(EnterpriseAccess.Admission a,Confirmed c,DocumentCandidateAdoptionSaveService.Saved saved)throws Exception {
        saved.requireFor(a,c);sourcePermission(a,c);same(c.bodySha256(),saved.bodySha256());
        String decision=id(),audit=id(),event=id();var at=now();
        String intent=UUID.nameUUIDFromBytes(("N07/"+c.tenantId()+"/"+c.operationId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var data=json.tree(new DocumentCandidateAdoptionProtocol.Result("1.0.0",c.adoptionId(),c.candidateId(),c.documentId(),saved.documentRevision(),c.pendingActionId(),c.operationId(),c.bodySha256(),intent,false));
        var response=json.tree(new InterfaceProtocol.Envelope("1.0.0",c.operationId(),c.contextRevision(),data));
        var decisionBody=json.tree(Map.of("schema_version","1.0.0","pending_action_id",c.pendingActionId(),"arguments_sha256",c.arguments,"operation_id",c.operationId(),"actor_id",c.actorId(),"confirmed_at",at.toString(),"document_revision",saved.documentRevision()));
        insert("version_registry",Map.of("tenant_id",c.tenantId(),"owner_project_id",c.projectId(),"version_id",decision,"entity_kind","DOCUMENT_ADOPTION_DECISION","content_sha256",json.hash(decisionBody)));
        var adoption=meta(a,c.projectId(),c.classification(),at);adoption.putAll(Map.of("adoption_id",c.adoptionId(),"candidate_id",c.candidateId(),"candidate_kind","DOCUMENT","operation_id",c.operationId(),"selected_nodes",json.canonical(c.selectedNodeIds()),"result_document_revision",saved.documentRevision(),"actor_id",c.actorId()));insert("candidate_adoption",adoption);
        if(db.update("UPDATE pending_action SET status='CONFIRMED',decision_id=?,decided_at=?,actor_id=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND action_id=? AND status='PENDING' AND created_by=? AND arguments_sha256=? AND expires_at>?",decision,at,c.actorId(),at,c.tenantId(),c.pendingActionId(),c.actorId(),c.arguments,at)!=1)stale();
        if(db.update("UPDATE candidate SET status='ADOPTED',revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND candidate_id=? AND status='READY' AND content_sha256=?",at,c.tenantId(),c.projectId(),c.candidateId(),c.source.candidateSha256())!=1)stale();
        if(db.update("UPDATE operation_record SET state='COMPLETED',http_status=201,response_ref=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name='DC-03' AND state='PROCESSING' AND request_sha256=?",json.canonical(response),at,c.tenantId(),c.operationId(),c.actorId(),c.requestHash)!=1)stale();
        var receipt=(com.fasterxml.jackson.databind.node.ObjectNode)response.deepCopy();receipt.set("decision",decisionBody);receipt.put("audit_id",audit).put("event_id",event);
        audit(a,c.projectId(),c.classification(),audit,event,c.operationId(),c.adoptionId(),"document.candidate.adopted",receipt,at);
        var completionRow=meta(a,c.projectId(),c.classification(),at);completionRow.putAll(Map.ofEntries(Map.entry("tenant_id",c.tenantId()),Map.entry("project_id",c.projectId()),Map.entry("operation_id",c.operationId()),Map.entry("adoption_id",c.adoptionId()),Map.entry("document_revision",saved.documentRevision()),Map.entry("decision_id",decision),Map.entry("receipt_json",json.canonical(receipt)),Map.entry("receipt_sha256",json.hash(receipt)),Map.entry("audit_id",audit),Map.entry("event_id",event),Map.entry("created_by",c.actorId()),Map.entry("created_at",at)));insert("document_adoption_completion",completionRow);
        same(intent,reviews.enqueueSaved(a,c.documentId(),saved.documentRevision(),c.operationId()).required("intent_id").asText());
        c.completion=response.deepCopy();c.requireUnchanged(a);return response;
    }

    public final class Confirmed {
        private final DocumentCandidateAdoptionAuthority.Verified source;
        private final EnterpriseAccess.Admission admission;
        private final String context,operation,adoption,pending,arguments,actor,requestHash,key;
        private final JsonNode body;
        private final List<String> selected;
        private final List<ArtifactRegistryProtocol.MemberRow> members;
        private final List<String> roots;
        private JsonNode completion;
        private Confirmed(EnterpriseAccess.Admission a,String context,DocumentCandidateAdoptionAuthority.Verified source,
                String operation,String adoption,String pending,String arguments,String requestHash,String key,
                JsonNode body,List<String> selected,List<ArtifactRegistryProtocol.MemberRow> members,List<String> roots){
            this.admission=a;this.context=context;this.source=source;this.operation=operation;this.adoption=adoption;
            this.pending=pending;this.arguments=arguments;this.actor=a.actorId();this.requestHash=requestHash;this.key=key;
            this.body=body.deepCopy();this.selected=List.copyOf(selected);this.members=List.copyOf(members);this.roots=List.copyOf(roots);
        }
        public void requireUnchanged(EnterpriseAccess.Admission a)throws Exception {
            if(!TransactionSynchronizationManager.isActualTransactionActive()||!actor.equals(a.actorId())
                    ||!admission.context().tenant_id().equals(a.context().tenant_id())||!context.equals(a.context().context_revision()))deny();
            if(completion!=null){requireCompletion(a,this);return;}
            source.requireCurrent(a);
            var row=one(db.queryForList("SELECT * FROM pending_action WHERE tenant_id=? AND action_id=? AND created_by=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),pending,actor));
            same(arguments,str(row,"arguments_sha256"));same("PENDING",str(row,"status"));
            if(!Boolean.TRUE.equals(db.queryForObject("SELECT expires_at>CURRENT_TIMESTAMP(6) FROM pending_action WHERE tenant_id=? AND action_id=?",Boolean.class,a.context().tenant_id(),pending)))stale();
            var op=one(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),operation,actor));
            same("DC-03",str(op,"operation_name"));same("PROCESSING",str(op,"state"));same(requestHash,str(op,"request_sha256"));same(key,str(op,"idempotency_key"));same(documentId(),str(op,"target_id"));
            var intent=one(db.queryForList("SELECT * FROM document_adoption_intent WHERE tenant_id=? AND operation_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),operation));
            requireMetadata(intent,a,projectId(),classification());same(adoption,str(intent,"adoption_id"));same(pending,str(intent,"pending_action_id"));same(arguments,str(intent,"arguments_sha256"));same(bodySha256(),str(intent,"body_sha256"));
        }
        public EnterpriseAccess.Admission admission(){return admission;}
        public String contextRevision(){return context;}
        public String candidateId(){return source.candidateId();}
        public String documentId(){return source.header().documentId();}
        public String baseDocumentRevision(){return source.header().documentRevision();}
        public String expectedDocumentRevision(){return source.header().revision().toString();}
        public JsonNode body(){return body.deepCopy();}
        public byte[] bodyBytes()throws Exception{return json.bytes(body);}
        public String bodySha256()throws Exception{return json.hash(body);}
        public String bodySchemaRef(){return source.bodySchemaRef();}
        public String bodySchemaSha256(){return source.bodySchemaSha256();}
        public String bodySchemaRevision(){return source.header().bodySchemaRevision();}
        public String snapshotId(){return source.header().snapshotId();}
        public String taskId(){return source.header().taskId();}
        public String profileRevision(){return source.header().profileRevision();}
        public String taskRevision(){return source.header().taskRevision();}
        public String planRevision(){return source.header().planRevision();}
        public String templateVersion(){return source.header().templateVersion();}
        public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return members;}
        public List<String> contentRoots(){return roots;}
        public List<String> provenanceRoots(){return source.provenanceRoots();}
        public List<String> selectedNodeIds(){return selected;}
        public String operationId(){return operation;}
        public String adoptionId(){return adoption;}
        public String pendingActionId(){return pending;}
        public String actorId(){return actor;}
        public String classification(){return source.header().classification();}
        public String projectId(){return source.header().projectId();}
        public String tenantId(){return source.header().tenantId();}
        public JsonNode baseline(){return source.baseline();}
    }
    private void requireCompletion(EnterpriseAccess.Admission a,Confirmed proof)throws Exception {
        // The sealed transition only authenticates this transaction's exact completed original.
        sourcePermission(a,proof);
        var row=one(db.queryForList("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=? AND subject_id=? AND operation_name='DC-03' AND state='COMPLETED' AND request_sha256=? LIMIT 2 FOR UPDATE",proof.tenantId(),proof.operationId(),proof.actorId(),proof.requestHash));
        same(proof.completion,json.read(str(row,"response_ref")));
        var adoption=one(db.queryForList("SELECT result_document_revision FROM candidate_adoption WHERE tenant_id=? AND project_id=? AND adoption_id=? AND operation_id=? AND candidate_id=? LIMIT 2 FOR UPDATE",proof.tenantId(),proof.projectId(),proof.adoptionId(),proof.operationId(),proof.candidateId()));
        same(proof.completion.required("data").required("document_revision").asText(),str(adoption,"result_document_revision"));
    }
    private void sourcePermission(EnterpriseAccess.Admission a,Confirmed proof)throws Exception {proof.source.requirePermissions(a);}
    private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)stale();return rows.get(0);}
    private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))stale();}
    private static void stale(){throw new TenantAccessException(ErrorCode.CANDIDATE_STALE);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
