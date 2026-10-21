package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Immutable family journals. Definition bytes never carry mutable approval state. */
final class StrictComparisonStore {
    enum Kind {
        POLICY("strict_comparison_policy_revision", "STRICT_COMPARISON_POLICY", "strict_comparison_policy.authored"),
        DECISION("strict_comparison_policy_decision", "STRICT_COMPARISON_DECISION", "strict_comparison_policy.decided"),
        DECIMAL("strict_decimal_evidence_revision", "STRICT_DECIMAL_EVIDENCE", "strict_decimal_evidence.produced");
        final String table, entity, event;
        Kind(String table,String entity,String event) { this.table=table;this.entity=entity;this.event=event; }
    }
    record Row(String id, String parentId, String digest, JsonNode request, JsonNode body, JsonNode authority, JsonNode receipt) { }
    final JdbcTemplate db;
    private final StrictComparisonCodec codec;
    private final IdentityDirectoryPolicy identity;
    private final String manifest;
    StrictComparisonStore(DataSource data,StrictComparisonCodec codec,IdentityDirectoryPolicy identity) throws Exception {
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.codec=codec;this.identity=identity;
        manifest=codec.hash(Map.of("protocol",StrictComparisonCodec.implementation(StrictComparisonProtocol.class,"STRICT_COMPARISON_PROTOCOL_V1"),"definition",StrictComparisonCodec.implementation(StrictComparisonPolicyDefinition.class,"STRICT_COMPARISON_DEFINITION_V1")));
    }
    Row replay(EnterpriseAccess.Admission a,String project,Kind kind,String key,JsonNode request) throws Exception {
        active(a);TaskFieldMappingService.key(key);
        var rows=db.queryForList("SELECT revision_id FROM "+kind.table+" WHERE tenant_id=? AND created_by=? AND idempotency_key=?",a.context().tenant_id(),a.actorId(),key);
        if(rows.size()>1)mismatch();if(rows.isEmpty())return null;
        var row=read(a,project,kind,text(rows.get(0),"revision_id"));
        if(!row.request().equals(request))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);return row;
    }
    Row append(EnterpriseAccess.Admission a,String project,Kind kind,String parent,String key,JsonNode request,JsonNode body,JsonNode authority) throws Exception {
        active(a);WireValues.uuid(project);String id=UUID.randomUUID().toString(),op=UUID.randomUUID().toString(),audit=UUID.randomUUID().toString(),event=UUID.randomUUID().toString();
        String digest=codec.hash(body),requestHash=codec.hash(request);var now=Objects.requireNonNull(db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));
        var receipt=(ObjectNode)codec.tree(Map.of("schema_version","1.0.0","tenant_id",a.context().tenant_id(),"project_id",project,"revision_id",id,"content_sha256",digest,"operation_id",op,"audit_id",audit,"event_id",event,"created_by",a.actorId(),"created_at",now.toInstant(ZoneOffset.UTC).toString()));
        var witness=(ObjectNode)codec.tree(Map.of("request",request,"body",body,"authority",authority,"receipt",receipt));
        one(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,?,?)",a.context().tenant_id(),project,id,kind.entity,digest));
        for(String upstream:parents(codec,kind,parent,request,body)) {
            single(db.queryForList("SELECT version_id FROM version_registry WHERE tenant_id=? AND version_id=? FOR UPDATE",a.context().tenant_id(),upstream));
            one(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,decision_ref,created_at) VALUES(?,?,?,?,?,NULL,?)",a.context().tenant_id(),id,upstream,kind.entity+"_INPUT",a.actorId(),now));
        }
        one(db.update("INSERT INTO operation_record(tenant_id,operation_id,subject_id,operation_name,idempotency_key,request_sha256,state,http_status,response_ref,error_code,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'COMPLETED',201,?,NULL,?,'1.0.0',?,?,?,?,1,?)",a.context().tenant_id(),op,a.actorId(),kind.entity,key,requestHash,codec.canonical(receipt),id,manifest,a.actorId(),now,identity.classification(),now));
        one(db.update("INSERT INTO audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'SUCCESS',?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),project,audit,a.actorId(),kind.event,codec.canonical(witness),op,op,manifest,a.actorId(),now,identity.classification()));
        one(db.update("INSERT INTO outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at) VALUES(?,?,?,1,?,?,?,NULL)",a.context().tenant_id(),event,id,kind.event,codec.canonical(witness),now));
        one(db.update("INSERT INTO "+kind.table+"(tenant_id,project_id,revision_id,parent_id,content_json,content_sha256,request_json,request_sha256,authority_json,receipt_json,idempotency_key,operation_id,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),project,id,parent,codec.canonical(body),digest,codec.canonical(request),requestHash,codec.canonical(authority),codec.canonical(receipt),key,op,audit,event,manifest,a.actorId(),now,identity.classification()));
        var result=read(a,project,kind,id);if(!result.body().equals(body)||!result.receipt().equals(receipt))mismatch();return result;
    }
    Row read(EnterpriseAccess.Admission a,String project,Kind kind,String id) throws Exception {
        active(a);WireValues.uuid(id);WireValues.uuid(project);
        var r=single(db.queryForList("SELECT r.*,v.entity_kind,v.content_sha256 AS registry_hash,v.owner_project_id AS registry_project FROM "+kind.table+" r JOIN version_registry v ON v.tenant_id=r.tenant_id AND v.version_id=r.revision_id WHERE r.tenant_id=? AND r.project_id=? AND r.revision_id=? FOR UPDATE",a.context().tenant_id(),project,id));
        var body=codec.read(text(r,"content_json"));var request=codec.read(text(r,"request_json"));var authority=codec.read(text(r,"authority_json"));var receipt=codec.read(text(r,"receipt_json"));
        if(!kind.entity.equals(text(r,"entity_kind"))||!project.equals(text(r,"registry_project"))||!codec.hash(body).equals(text(r,"content_sha256"))||!text(r,"content_sha256").equals(text(r,"registry_hash"))||!codec.hash(request).equals(text(r,"request_sha256"))||!manifest.equals(text(r,"schema_manifest_sha256"))||!"1.0.0".equals(text(r,"schema_version")))mismatch();
        for(String name:List.of("tenant_id","project_id","revision_id","content_sha256","operation_id","audit_id","event_id","created_by"))if(!text(r,name).equals(receipt.required(name).asText()))mismatch();
        if(!time(r,"created_at").toInstant(ZoneOffset.UTC).toString().equals(receipt.required("created_at").asText()))mismatch();
        var op=single(db.queryForList("SELECT * FROM operation_record WHERE tenant_id=? AND operation_id=?",a.context().tenant_id(),text(r,"operation_id")));
        if(!kind.entity.equals(text(op,"operation_name"))||!id.equals(text(op,"target_id"))||!"COMPLETED".equals(text(op,"state"))||!"201".equals(text(op,"http_status"))||!receipt.equals(codec.read(text(op,"response_ref")))||!text(r,"created_by").equals(text(op,"subject_id"))||!text(r,"idempotency_key").equals(text(op,"idempotency_key")))mismatch();
        for(String name:List.of("request_sha256","schema_manifest_sha256","created_by","classification","schema_version"))if(!text(r,name).equals(text(op,name)))mismatch();
        var audit=single(db.queryForList("SELECT * FROM audit_event WHERE tenant_id=? AND audit_id=?",a.context().tenant_id(),text(r,"audit_id")));
        var event=single(db.queryForList("SELECT * FROM outbox_event WHERE tenant_id=? AND event_id=?",a.context().tenant_id(),text(r,"event_id")));
        var expected=codec.tree(Map.of("request",request,"body",body,"authority",authority,"receipt",receipt));
        if(!expected.equals(codec.read(text(audit,"resource_ref")))||!expected.equals(codec.read(text(event,"payload")))||!kind.event.equals(text(audit,"action"))||!kind.event.equals(text(event,"event_type"))||!"SUCCESS".equals(text(audit,"result"))||!project.equals(text(audit,"project_id"))||!id.equals(text(event,"aggregate_id"))||!"1".equals(text(event,"aggregate_revision")))mismatch();
        for(String name:List.of("created_by","classification","schema_version","schema_manifest_sha256"))if(!text(r,name).equals(text(audit,name)))mismatch();
        if(!text(r,"created_by").equals(text(audit,"subject_id"))||!text(r,"operation_id").equals(text(audit,"request_id"))||!text(r,"operation_id").equals(text(audit,"trace_id"))||!time(r,"created_at").equals(time(audit,"created_at"))||!time(r,"created_at").equals(time(event,"occurred_at"))||!time(r,"created_at").equals(time(op,"created_at"))||!time(r,"created_at").equals(time(op,"updated_at")))mismatch();
        var expectedParents=parents(codec,kind,(String)r.get("parent_id"),request,body);
        var actualParents=new TreeSet<String>();
        for(var edge:db.queryForList("SELECT upstream_version_id,relation_kind,actor_id,decision_ref,created_at FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? FOR UPDATE",a.context().tenant_id(),id)) {
            if(!actualParents.add(text(edge,"upstream_version_id"))||!(kind.entity+"_INPUT").equals(text(edge,"relation_kind"))
                    ||!text(r,"created_by").equals(text(edge,"actor_id"))||edge.get("decision_ref")!=null||!time(r,"created_at").equals(time(edge,"created_at")))mismatch();
        }
        if(!expectedParents.equals(actualParents))mismatch();
        return new Row(id,(String)r.get("parent_id"),text(r,"content_sha256"),request,body,authority,receipt);
    }
    /** Only persisted business versions are graph nodes; parser/implementation fingerprints are not versions. */
    static Set<String> parents(StrictComparisonCodec codec,Kind kind,String parent,JsonNode request,JsonNode body)throws Exception {
        var ids=new TreeSet<String>();
        switch(kind) {
            case POLICY -> {
                if(parent!=null||!request.equals(body))mismatch();
                var author=codec.bind(request,StrictComparisonProtocol.Author.class);
                scopeParents(ids,author.scope());
                ids.add(author.definition().applicability().scope().revision());
                if(author.definition().applicability().rule()!=null)ids.add(author.definition().applicability().rule().revision());
                author.definition().basis().forEach(version->ids.add(version.revision()));
            }
            case DECISION -> {
                if(parent==null||!parent.equals(request.required("policy_revision").textValue())||!parent.equals(body.required("policy_revision").textValue()))mismatch();
                ids.add(parent);
                var previous=body.required("baseline").required("state").required("decision_id");
                if(!previous.isNull())ids.add(previous.textValue());
            }
            case DECIMAL -> {
                if(parent!=null||!request.equals(body.required("request")))mismatch();
                var parse=codec.bind(request,StrictComparisonProtocol.Parse.class);
                scopeParents(ids,parse.scope());ids.add(parse.evidence().sourceVersion());ids.add(parse.evidence().parseVersion());
                ids.add(parse.evidence().approvalRevision());ids.add(parse.rightsRevision());
            }
        }
        ids.forEach(WireValues::uuid);return Set.copyOf(ids);
    }
    private static void scopeParents(Set<String> ids,StrictComparisonProtocol.Scope scope) {
        ids.add(scope.taskRevision());ids.add(scope.planRevision());ids.add(scope.templateVersion());ids.add(scope.scopeSnapshot());
    }
    void advance(EnterpriseAccess.Admission a) { one(db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=? AND auth_epoch=? AND governance_epoch=?",a.context().tenant_id(),a.watermarks().authEpoch(),a.watermarks().governanceEpoch())); }
    private static void active(EnterpriseAccess.Admission a) { if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(a.context().tenant_id()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    static Map<String,Object> single(List<Map<String,Object>> rows) { if(rows.size()!=1)mismatch();return rows.get(0); }
    static String text(Map<String,Object> row,String key) { return Objects.requireNonNull(row.get(key)).toString(); }
    static LocalDateTime time(Map<String,Object> row,String key) { var value=row.get(key);return value instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)value; }
    static void one(int count) { if(count!=1)mismatch(); }
    static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
