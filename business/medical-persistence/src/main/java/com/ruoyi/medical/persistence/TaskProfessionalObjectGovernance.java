package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses only the existing 075 governance journal. The typed v2 intent and private original
 * 097/098 authorities are required together; neither a row nor a kind confers read authority. */
public final class TaskProfessionalObjectGovernance {
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskProfessionalSchema schema;
    private final DependencyLineage lineage;private final DependencyLineage.Limits limits;
    public TaskProfessionalObjectGovernance(DataSource data,PermissionSchema json,TaskProfessionalSchema schema,
            DependencyLineage lineage,DependencyLineage.Limits limits){this(new JdbcTemplate(data),json,schema,lineage,limits);}
    TaskProfessionalObjectGovernance(JdbcTemplate db,PermissionSchema json,TaskProfessionalSchema schema,
            DependencyLineage lineage,DependencyLineage.Limits limits){this.db=db;db.setQueryTimeout(5);this.json=json;this.schema=schema;this.lineage=lineage;this.limits=limits;}
    Stored prepare(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,TaskNodeProducerAuthority.LeasedProduction producer,
            TaskProfessionalObjectLedger.Intent intent,JsonNode provenance)throws Exception {
        var expected=expected(a,input,producer,intent,provenance);var rows=rows(a,intent);
        if(!rows.isEmpty())return verify(a,intent,expected,one(rows));
        var i=a.formalProof().identity();var now=now();var value=expected.value();
        var manifest=metadata(i,intent,now);manifest.put("write_id",intent.writeIntent().writeId());manifest.put("principal_id",i.principal());
        manifest.put("replay_sha256",text(value,"replay_sha256"));manifest.put("canonical_manifest",json.canonical(value));manifest.put("manifest_sha256",expected.sha256());
        insert("runtime_governance_manifest",manifest);
        int n=0;for(var v:value.required("versions")){
            var row=metadata(i,intent,now);row.put("write_id",intent.writeIntent().writeId());row.put("member_ordinal",n++);
            row.put("upstream_version_id",text(v,"version_id"));row.put("upstream_project_id",v.required("project_id").isNull()?null:text(v,"project_id"));
            row.put("entity_kind",text(v,"entity_kind"));row.put("content_sha256",text(v,"content_sha256"));insert("runtime_governance_member",row);
        }
        n=0;for(var e:value.required("edges")){
            var row=metadata(i,intent,now);row.put("write_id",intent.writeIntent().writeId());row.put("edge_ordinal",n++);
            for(String field:List.of("consumer_version_id","upstream_version_id","relation_kind"))row.put(field,text(e,field));insert("runtime_governance_edge",row);
        }
        return verify(a,intent,expected,one(rows(a,intent)));
    }
    Stored requireStored(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,TaskNodeProducerAuthority.LeasedProduction producer,
            TaskProfessionalObjectLedger.Intent intent,JsonNode provenance)throws Exception {
        return verify(a,intent,expected(a,input,producer,intent,provenance),one(rows(a,intent)));
    }
    void requireCurrent(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,TaskNodeProducerAuthority.LeasedProduction producer,
            TaskProfessionalObjectLedger.Intent intent,JsonNode provenance)throws Exception {expected(a,input,producer,intent,provenance);}
    private Stored expected(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,TaskNodeProducerAuthority.LeasedProduction producer,
            TaskProfessionalObjectLedger.Intent intent,JsonNode provenance)throws Exception {
        TaskProfessionalRuntimeService.current(a);producer.requireUnchanged();input.requireUnchanged();var i=a.formalProof().identity();
        if(!input.runId().equals(i.run())||!input.stepId().equals(i.step())||!producer.runId().equals(i.run())||!producer.stepId().equals(i.step())
                ||!producer.principalId().equals(i.principal())||!producer.leaseGeneration().equals(i.generation())||!producer.workerService().equals(i.workerService()))bad();
        var run=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND task_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),input.taskId(),i.actor()));
        var replay=schema.parse(str(run,"replay_manifest"));String replayHash=hash(replay);
        if(!replayHash.equals(text(input.manifest(),"replay_sha256"))||!intent.classification().equals(str(run,"classification")))bad();
        requireGraph(replay.required("dependency_versions"),replay.required("dependency_edges"));
        var value=json.object().put("schema_version","2.0.0").put("tenant_id",i.tenant()).put("project_id",i.project()).put("run_id",i.run())
                .put("step_id",i.step()).put("task_id",input.taskId()).put("principal_id",i.principal()).put("action_id",i.action())
                .put("lease_generation",i.generation()).put("worker_service_uri",i.workerService()).put("session_id",intent.sessionId())
                .put("write_id",intent.writeIntent().writeId()).put("write_key",intent.key()).put("artifact_id",intent.artifactId()).put("kind",intent.kind())
                .put("schema_ref",intent.schemaRef()).put("payload_schema_sha256",intent.schemaSha256()).put("replay_sha256",replayHash)
                .put("input_artifact_id",input.inputId()).put("input_sha256",input.inputSha256()).put("input_receipt_sha256",input.receiptSha256())
                .put("producer_rule_id",producer.ruleId()).put("producer_sha256",producer.ruleSha256())
                .put("producer_decision_id",producer.decisionId()).put("producer_decision_sha256",producer.decisionSha256());
        value.set("write_intent",json.tree(intent.writeIntent()));value.set("producer_authorizations",producer.authorizations());
        value.set("provenance",provenance.deepCopy());value.set("versions",replay.required("dependency_versions").deepCopy());value.set("edges",replay.required("dependency_edges").deepCopy());
        producer.requireUnchanged();input.requireUnchanged();return new Stored(value,hash(value));
    }
    /** Exact complete pinned graph, including configuration-only ancestors and all relations. */
    void requireGraph(JsonNode declared,JsonNode declaredEdges)throws Exception {
        var expected=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var expectedEdges=new HashSet<SourceReadProtocol.ParentRow>();
        if(!declared.isArray()||declared.isEmpty()||declared.size()>limits.maximumVersions()||!declaredEdges.isArray()||declaredEdges.size()>limits.maximumRelations())bad();
        for(var v:declared){String id=text(v,"version_id");var row=new ArtifactRegistryProtocol.VersionRow(id,v.required("project_id").isNull()?null:text(v,"project_id"),text(v,"entity_kind"),text(v,"content_sha256"));if(expected.put(id,row)!=null)bad();}
        for(var e:declaredEdges)if(!expectedEdges.add(new SourceReadProtocol.ParentRow(text(e,"consumer_version_id"),text(e,"upstream_version_id"),text(e,"relation_kind"))))bad();
        var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var edges=new HashSet<SourceReadProtocol.ParentRow>();
        for(String id:expected.keySet()) {
            if(versions.containsKey(id))continue;var graph=lineage.inspect(id,limits);
            for(var v:graph.versions().entrySet()){var previous=versions.putIfAbsent(v.getKey(),v.getValue());if(previous!=null&&!previous.equals(v.getValue()))bad();}
            edges.addAll(graph.graph().edgeSet());if(versions.size()>limits.maximumVersions()||edges.size()>limits.maximumRelations())bad();
        }
        if(!expected.equals(versions)||!expectedEdges.equals(edges))bad();
    }
    private Stored verify(EnterpriseAccess.Admission a,TaskProfessionalObjectLedger.Intent intent,Stored expected,Map<String,Object> actual)throws Exception {
        var i=a.formalProof().identity();var now=time(actual,"created_at");if(now.isAfter(now()))bad();
        var wanted=metadata(i,intent,now);wanted.put("write_id",intent.writeIntent().writeId());wanted.put("principal_id",i.principal());wanted.put("replay_sha256",text(expected.value(),"replay_sha256"));wanted.put("manifest_sha256",expected.sha256());same(wanted,actual);
        if(!FormalTaskNodeAuthority.sameEvidence(expected.value(),schema.parse(str(actual,"canonical_manifest"))))bad();
        var members=db.queryForList("SELECT * FROM runtime_governance_member WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? ORDER BY member_ordinal FOR UPDATE",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId());
        var edges=db.queryForList("SELECT * FROM runtime_governance_edge WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? ORDER BY edge_ordinal FOR UPDATE",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId());
        var v=expected.value().required("versions");var e=expected.value().required("edges");if(v.size()!=members.size()||e.size()!=edges.size())bad();
        for(int n=0;n<v.size();n++){var item=v.get(n);var row=metadata(i,intent,now);row.put("write_id",intent.writeIntent().writeId());row.put("member_ordinal",n);row.put("upstream_version_id",text(item,"version_id"));row.put("upstream_project_id",item.required("project_id").isNull()?null:text(item,"project_id"));row.put("entity_kind",text(item,"entity_kind"));row.put("content_sha256",text(item,"content_sha256"));same(row,members.get(n));}
        for(int n=0;n<e.size();n++){var item=e.get(n);var row=metadata(i,intent,now);row.put("write_id",intent.writeIntent().writeId());row.put("edge_ordinal",n);for(String f:List.of("consumer_version_id","upstream_version_id","relation_kind"))row.put(f,text(item,f));same(row,edges.get(n));}
        return expected;
    }
    private List<Map<String,Object>> rows(EnterpriseAccess.Admission a,TaskProfessionalObjectLedger.Intent intent){var i=a.formalProof().identity();return db.queryForList("SELECT * FROM runtime_governance_manifest WHERE tenant_id=? AND project_id=? AND run_id=? AND write_id=? LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),intent.writeIntent().writeId());}
    static Map<String,Object> metadata(FormalWorkerAccess.Identity i,TaskProfessionalObjectLedger.Intent intent,LocalDateTime now){var row=new LinkedHashMap<String,Object>();row.put("tenant_id",i.tenant());row.put("project_id",i.project());row.put("run_id",i.run());row.put("schema_version","1.0.0");row.put("schema_manifest_sha256",intent.schemaSha256());row.put("created_by",i.actor());row.put("created_at",now);row.put("classification",intent.classification());return row;}
    void insert(String table,Map<String,Object> values){var columns=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+"("+String.join(",",columns)+") VALUES("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",columns.stream().map(values::get).toArray())!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
    LocalDateTime now(){return one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));}
    static void same(Map<String,Object> expected,Map<String,Object> actual){TaskNodeInputRegistration.sameColumns(expected,actual);}
    static LocalDateTime time(Map<String,Object> row,String key){Object v=row.get(key);if(v instanceof LocalDateTime t)return t;if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();bad();return null;}
    static String str(Map<String,Object> row,String key){var value=row.get(key);if(value==null)bad();return value.toString();}
    static String text(JsonNode value,String key){var node=value.required(key);if(!node.isTextual())bad();return node.textValue();}
    static String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
    static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    static final class Stored {private final JsonNode value;private final String hash;private Stored(JsonNode value,String hash){this.value=value.deepCopy();this.hash=hash;}JsonNode value(){return value.deepCopy();}String sha256(){return hash;}}
}
