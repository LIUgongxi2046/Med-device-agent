package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** SQL half of result persistence. Object I/O is never executed from this class. */
final class SimulationResultStore {
    private final JdbcTemplate jdbc; private final SimulationInvocationStore ledger; private final SimulationInvocationSchema schema;
    private final SimulationResultSchema results;private final ArtifactRegistryMapper artifacts;private final DependencyManifestSchema manifests;private final DependencyLineage lineage;
    SimulationResultStore(DataSource data,SimulationInvocationSchema schema,SimulationResultSchema results,ArtifactRegistryMapper artifacts,DependencyManifestSchema manifests,DependencyLineage lineage){
        this(new JdbcTemplate(data),new SimulationInvocationStore(data,schema),schema,results,artifacts,manifests,lineage);
    }
    SimulationResultStore(JdbcTemplate jdbc,SimulationInvocationStore ledger,SimulationInvocationSchema schema,SimulationResultSchema results,ArtifactRegistryMapper artifacts,DependencyManifestSchema manifests,DependencyLineage lineage){
        this.jdbc=jdbc;this.ledger=ledger;this.schema=schema;this.results=results;this.artifacts=artifacts;this.manifests=manifests;this.lineage=lineage;
    }
    String tenant(){TenantScope.requireSqlAccess("SimulationResultStore");return TenantScope.requireCurrent().tenantId();}
    record Work(JsonNode request,JsonNode payload,Map<String,Object> operation,SimulationResultSchema.Command command,String actor,String classification) { }
    Work prepare(JsonNode request,Map<String,Object> invocation)throws Exception {
        String id=text(request.required("binding").required("identity"),"invocation_id");var fact=ledger.factRow(id);var payload=schema.parse(str(fact,"payload"));
        if(!schema.hash(payload).equals(str(fact,"payload_sha256"))||!schema.manifest().equals(str(fact,"schema_manifest_sha256")))SimulationResultSchema.mismatch();
        schema.receipt(request,results.bytes(payload,"result"),results.bytes(payload,"provenance"));
        var existing=jdbc.queryForList("SELECT * FROM simulation_result_persistence WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),id);
        if(existing.isEmpty()) {
            if(List.of("SUCCEEDED","FAILED").contains(str(invocation,"status")))denied();
            var proof=request.required("authority_proof");var command=results.create(request,payload);
            one(jdbc.update("""
                INSERT INTO simulation_result_persistence (tenant_id,project_id,invocation_id,fact_id,payload_sha256,binding_sha256,command_json,
                status,revision,updated_at,retention_policy_id,retention_deadline,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                VALUES (?,?,?,?,?,?,?,'PREPARED',1,UTC_TIMESTAMP(6),?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
                """,tenant(),nullable(proof,"project_id"),id,str(fact,"fact_id"),str(fact,"payload_sha256"),schema.hash(request.required("binding")),results.encode(command),
                text(proof,"retention_policy_id"),PermissionAuthority.time(text(proof,"retention_deadline")),results.manifest(),str(invocation,"created_by"),str(invocation,"classification")));
        }
        var row=operation(id);var command=results.decode(str(row,"command_json"));results.require(command,request,payload);
        if(!str(fact,"fact_id").equals(str(row,"fact_id"))||!str(fact,"payload_sha256").equals(str(row,"payload_sha256"))
            ||!schema.hash(request.required("binding")).equals(str(row,"binding_sha256"))||!results.manifest().equals(str(row,"schema_manifest_sha256"))
            ||!Objects.equals(nullable(request.required("authority_proof"),"project_id"),str(row,"project_id"))
            ||!text(request.required("authority_proof"),"retention_policy_id").equals(str(row,"retention_policy_id"))
            ||!PermissionAuthority.time(text(request.required("authority_proof"),"retention_deadline")).equals(SimulationInvocationAuthority.time(row,"retention_deadline"))
            ||!"SIMULATION".equals(str(row,"execution_mode"))||!"1.0.0".equals(str(row,"schema_version"))
            ||!str(invocation,"created_by").equals(str(row,"created_by"))||!str(invocation,"classification").equals(str(row,"classification")))SimulationResultSchema.mismatch();
        return new Work(request,payload,row,command,str(invocation,"created_by"),str(invocation,"classification"));
    }
    private Map<String,Object> operation(String id){return single(jdbc.queryForList("SELECT * FROM simulation_result_persistence WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),id));}
    Work save(Work work,SimulationResultSchema.Command command,String status)throws Exception {
        results.require(command,work.request(),work.payload());
        one(jdbc.update("UPDATE simulation_result_persistence SET command_json=?,status=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND invocation_id=? AND revision=? AND revision<9007199254740991 AND status<>'COMPLETED'",
            results.encode(command),status,tenant(),str(work.operation(),"invocation_id"),work.operation().get("revision")));
        var saved=operation(str(work.operation(),"invocation_id"));
        if(!results.encode(command).equals(results.encode(results.decode(str(saved,"command_json"))))||!status.equals(str(saved,"status")))SimulationResultSchema.mismatch();
        return new Work(work.request(),work.payload(),saved,command,work.actor(),work.classification());
    }
    SimulationInvocationService.View finish(Work work,Map<String,Object> invocation)throws Exception {
        var request=work.request();var id=request.required("binding").required("identity");var proof=request.required("authority_proof");String invocationId=text(id,"invocation_id");
        if("COMPLETED".equals(str(work.operation(),"status"))){verifyCompleted(work);return SimulationInvocationService.view(ledger.invocation(invocationId));}
        if(!"VERIFIED".equals(str(work.operation(),"status"))||work.command().items().stream().anyMatch(i->i.reference()==null))denied();
        var accounting=results.accounting(request,work.payload());long reserved=SimulationInvocationSchema.sum(accounting.input(),proof.required("maximum_output_units").longValue());
        var enterprise=ledger.account(text(proof,"enterprise_account_id"));var parent=ledger.account(text(proof,"parent_account_id"));
        requireAccount(enterprise,proof,true);requireAccount(parent,proof,false);ledger.requireHeld(request,reserved);
        var context=ledger.context(text(id,"context_manifest_id"));String access=str(context,"dependency_access_set_id");
        if(!"SIMULATION".equals(str(context,"execution_mode"))||!Objects.equals(nullable(id,"project_id"),str(context,"project_id")))denied();
        var manifestRow=single(artifacts.manifest(access));var dependencies=manifests.verify(manifestRow,artifacts.members(access));
        if(!tenant().equals(manifestRow.tenantId())||!Objects.equals(nullable(id,"project_id"),manifestRow.projectId()))denied();
        // This is the SOURCE access graph. Candidate/configuration provenance remains separately frozen in the context manifest.
        requireContextGraph(request);
        for(var item:work.command().items())register(work,item,access,manifestRow.manifestSha256(),dependencies.members());
        for(var account:List.of(enterprise,parent))consume(account,invocationId,reserved,accounting.charged());
        var result=work.command().items().get(0);var provenance=work.command().items().get(1);
        one(jdbc.update("""
            INSERT INTO simulation_quota_settlement (tenant_id,project_id,invocation_id,fact_id,receipt_id,result_artifact_id,result_sha256,
            provenance_artifact_id,provenance_sha256,input_units,output_units,charged_units,charged_calls,counter,counter_sha256,usage_origin,
            fixture_usage,execution_status,production_eligible,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?,false,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
            """,tenant(),nullable(id,"project_id"),invocationId,str(work.operation(),"fact_id"),text(work.payload(),"receipt_id"),result.artifactId(),result.intent().sha256(),
            provenance.artifactId(),provenance.intent().sha256(),accounting.input(),accounting.output(),accounting.charged(),schema.canonical(proof.required("counter")),schema.hash(proof.required("counter")),
            accounting.usageOrigin(),accounting.fixtureUsage().isNull()?null:schema.canonical(accounting.fixtureUsage()),accounting.status(),results.manifest(),work.actor(),work.classification()));
        String resultRef=resultReference(work);
        one(jdbc.update("""
            UPDATE invocation SET status=?,result_artifact_id=?,result_sha256=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND invocation_id=? AND revision=? AND revision<9007199254740991 AND execution_mode='SIMULATION'
            AND dispatch_generation=1 AND status IN ('DISPATCHING','IN_FLIGHT','UNKNOWN','CANCEL_REQUESTED') AND result_artifact_id IS NULL
            """,accounting.status(),result.artifactId(),result.intent().sha256(),tenant(),invocationId,invocation.get("revision")));
        one(jdbc.update("""
            UPDATE step_action_binding SET state=?,result_artifact_id=?,result_ref=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND invocation_id=? AND revision<9007199254740991
            AND state IN ('DISPATCHING','IN_FLIGHT','UNKNOWN','CANCEL_REQUESTED') AND result_artifact_id IS NULL
            """,accounting.status(),result.artifactId(),resultRef,tenant(),text(id,"run_id"),text(id,"step_id"),text(id,"action_id"),invocationId));
        finishSingleCell(work,accounting.status(),resultRef,"CANCEL_REQUESTED".equals(str(invocation,"status")));
        var completed=save(work,work.command(),"COMPLETED");ledger.event(request,"simulation.invocation.result_settled",work.actor(),work.classification());
        verifyCompleted(completed);return SimulationInvocationService.view(ledger.invocation(invocationId));
    }
    private void requireAccount(Map<String,Object> account,JsonNode proof,boolean enterprise)throws Exception {
        if(!schema.canonical(schema.parse(str(account,"counter"))).equals(schema.canonical(proof.required("counter")))
            ||!schema.hash(proof.required("counter")).equals(str(account,"counter_sha256"))||!schema.manifest().equals(str(account,"schema_manifest_sha256")))denied();
        if(enterprise?!"ENTERPRISE".equals(str(account,"scope"))||account.get("project_id")!=null||account.get("parent_run_id")!=null:
            !"PARENT_RUN".equals(str(account,"scope"))||!text(proof,"parent_run_id").equals(str(account,"parent_run_id"))||!Objects.equals(nullable(proof,"project_id"),str(account,"project_id")))denied();
        // Disabled quota blocks new reservations, but cannot erase already dispatched resource consumption.
    }
    void consume(Map<String,Object> account,String invocation,long reserved,long charge) {
        one(jdbc.update("""
            UPDATE simulation_quota_account SET reserved_units=reserved_units-?,reserved_calls=reserved_calls-1,
            spent_units=spent_units+?,spent_calls=spent_calls+1,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
            WHERE tenant_id=? AND account_id=? AND revision=? AND revision<9007199254740991 AND reserved_units>=? AND reserved_calls>=1
            AND spent_units<=9007199254740991-? AND spent_calls<9007199254740991
            """,reserved,charge,tenant(),str(account,"account_id"),account.get("revision"),reserved,charge));
        one(jdbc.update("UPDATE simulation_quota_reservation SET status='CONSUMED',charged_units=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND invocation_id=? AND account_id=? AND status='HELD' AND reserved_units=? AND reserved_calls=1 AND revision<9007199254740991",
            charge,tenant(),invocation,str(account,"account_id"),reserved));
        var actual=ledger.account(str(account,"account_id"));
        if(count(actual,"reserved_units")!=count(account,"reserved_units")-reserved||count(actual,"reserved_calls")!=count(account,"reserved_calls")-1
            ||count(actual,"spent_units")!=SimulationInvocationSchema.sum(count(account,"spent_units"),charge)||count(actual,"spent_calls")!=count(account,"spent_calls")+1)SimulationResultSchema.mismatch();
    }
    private void register(Work work,SimulationResultSchema.Item item,String access,String dependencyHash,List<ArtifactRegistryProtocol.MemberRow> members)throws Exception {
        var id=work.request().required("binding").required("identity");var ref=item.reference();SimulationResultSchema.requireReference(item.intent(),ref);
        one(jdbc.update("INSERT INTO version_registry (tenant_id,version_id,owner_project_id,entity_kind,content_sha256) VALUES (?,?,?,'ARTIFACT',?)",tenant(),item.artifactId(),nullable(id,"project_id"),ref.sha256()));
        one(jdbc.update("""
            INSERT INTO artifact (tenant_id,project_id,artifact_id,run_id,artifact_kind,object_ref,object_key,object_version,sha256,size_bytes,media_type,
            dependency_access_set_id,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
            """,tenant(),nullable(id,"project_id"),item.artifactId(),text(id,"run_id"),item.kind(),StoredObjectReferences.encode(ref),ref.objectKey(),ref.objectVersion(),ref.sha256(),ref.sizeBytes(),ref.mediaType(),access,ref.sha256(),item.schemaHash(),work.actor(),work.classification()));
        one(jdbc.update("""
            INSERT INTO artifact_content_binding (tenant_id,project_id,artifact_id,resource_kind,resource_id,schema_ref,schema_document_sha256,
            dependency_access_set_id,dependency_manifest_sha256,created_by,created_at,classification) VALUES (?,?,?,'EVALUATION',?,?,?,?,?,?,UTC_TIMESTAMP(6),?)
            """,tenant(),nullable(id,"project_id"),item.artifactId(),text(work.request().required("authority_proof"),"evaluation_id"),item.schemaRef(),item.schemaHash(),access,dependencyHash,work.actor(),work.classification()));
        for(var member:members)one(jdbc.update("INSERT INTO lineage_edge (tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,decision_ref,created_at) VALUES (?,?,?,'SIMULATION_RESULT_SOURCE',?,NULL,UTC_TIMESTAMP(6))",tenant(),item.artifactId(),member.upstreamVersionId(),work.actor()));
        verifyArtifact(work,item,access,dependencyHash);lineage.require(item.artifactId(),members,new DependencyLineage.Limits(512,2048));
    }
    private void verifyArtifact(Work work,SimulationResultSchema.Item item,String access,String dependencyHash)throws Exception {
        var row=single(artifacts.artifact(item.artifactId()));var binding=single(artifacts.binding(item.artifactId()));var version=single(artifacts.version(item.artifactId()));
        if(!item.reference().equals(StoredObjectReferences.decode(row.storedObject(),row.reference()))||!item.kind().equals(row.artifactKind())
            ||!tenant().equals(row.tenantId())||!Objects.equals(nullable(work.request().required("authority_proof"),"project_id"),row.projectId())
            ||!item.schemaHash().equals(row.schemaManifest())||!access.equals(row.accessSetId())||!row.sha256().equals(row.contentSha256())
            ||!"EVALUATION".equals(binding.resourceKind())||!text(work.request().required("authority_proof"),"evaluation_id").equals(binding.resourceId())
            ||!item.schemaRef().equals(binding.schemaRef())||!item.schemaHash().equals(binding.schemaDocumentSha256())||!access.equals(binding.accessSetId())||!dependencyHash.equals(binding.dependencyManifestSha256())
            ||!"ARTIFACT".equals(version.entityKind())||!item.intent().sha256().equals(version.contentSha256())||!Objects.equals(row.projectId(),version.projectId()))SimulationResultSchema.mismatch();
    }
    private void requireContextGraph(JsonNode request)throws Exception {
        var id=request.required("binding").required("identity");var row=single(jdbc.queryForList("SELECT * FROM simulation_context_dependency_manifest WHERE tenant_id=? AND context_manifest_id=? FOR UPDATE",tenant(),text(id,"context_manifest_id")));
        var graph=schema.parse(str(row,"manifest_json"));
        if(!schema.hash(graph).equals(str(row,"content_sha256"))||!Objects.equals(nullable(id,"project_id"),str(row,"project_id"))
            ||!text(id,"context_manifest_id").equals(graph.required("context_manifest_id").textValue()))SimulationResultSchema.mismatch();
        for(var expected:graph.required("versions")) {
            var version=single(artifacts.version(text(expected,"id")));
            if(!version.contentSha256().equals(text(expected,"content_sha256"))||!version.entityKind().equals(text(expected,"entity_kind"))||!Objects.equals(version.projectId(),nullable(expected,"project_id")))SimulationResultSchema.mismatch();
        }
    }
    private String resultReference(Work work)throws Exception {
        var node=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();node.put("schema_version","1.0.0");node.put("input_data_kind","SIMULATION");node.put("production_eligible",false);
        node.put("result_artifact_id",work.command().items().get(0).artifactId());node.put("result_sha256",work.command().items().get(0).intent().sha256());
        node.put("provenance_artifact_id",work.command().items().get(1).artifactId());node.put("provenance_sha256",work.command().items().get(1).intent().sha256());return schema.canonical(node);
    }
    private void finishSingleCell(Work work,String status,String resultRef,boolean cancelRequested)throws Exception {
        var id=work.request().required("binding").required("identity");String run=text(id,"run_id");var job=ledger.job(run);var step=ledger.step(id);
        var cells=jdbc.queryForList("SELECT * FROM simulation_evaluation_job WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(),run);
        if(cells.size()!=1)return;var cell=cells.get(0);
        for(String key:List.of("run_id","step_id","action_id","invocation_id","context_manifest_id","model_config_revision"))if(!text(id,key).equals(str(cell,key)))SimulationResultSchema.mismatch();
        if(cancelRequested||!"RUNNING".equals(str(job,"status"))||job.get("ended_at")!=null||!"RUNNING".equals(str(step,"status"))||!"ISSUED".equals(str(cell,"status")))return;
        var steps=jdbc.queryForList("SELECT step_id FROM run_step WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(),run);
        var actions=jdbc.queryForList("SELECT step_id,action_id FROM step_action_binding WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(),run);
        if(steps.size()!=1||actions.size()!=1)return;
        one(jdbc.update("UPDATE run_step SET status=?,output_ref=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND step_id=? AND status='RUNNING' AND revision=? AND revision<9007199254740991",status,resultRef,tenant(),run,text(id,"step_id"),step.get("revision")));
        one(jdbc.update("UPDATE system_job_run SET status=?,ended_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND run_id=? AND status='RUNNING' AND ended_at IS NULL AND revision=? AND revision<9007199254740991",status,tenant(),run,job.get("revision")));
        one(jdbc.update("UPDATE simulation_evaluation_job SET status=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND job_id=? AND status='ISSUED' AND revision=3",status,tenant(),str(cell,"job_id")));
        if(!status.equals(str(ledger.step(id),"status"))||!status.equals(str(ledger.job(run),"status"))||ledger.job(run).get("ended_at")==null)SimulationResultSchema.mismatch();
        var saved=single(jdbc.queryForList("SELECT status,revision FROM simulation_evaluation_job WHERE tenant_id=? AND job_id=? FOR UPDATE",tenant(),str(cell,"job_id")));
        if(!status.equals(str(saved,"status"))||count(saved,"revision")!=4)SimulationResultSchema.mismatch();
    }
    void verifyCompleted(Work work)throws Exception {
        String id=str(work.operation(),"invocation_id");var receipt=single(jdbc.queryForList("SELECT * FROM simulation_quota_settlement WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),id));
        var accounting=results.accounting(work.request(),work.payload());var result=work.command().items().get(0);var provenance=work.command().items().get(1);var invocation=ledger.invocation(id);
        var action=ledger.action(work.request().required("binding").required("identity"));
        if(!accounting.status().equals(str(invocation,"status"))||!result.artifactId().equals(str(invocation,"result_artifact_id"))||!result.intent().sha256().equals(str(invocation,"result_sha256"))
            ||!accounting.status().equals(str(action,"state"))||!result.artifactId().equals(str(action,"result_artifact_id"))||!schema.canonical(schema.parse(str(action,"result_ref"))).equals(resultReference(work))
            ||!result.artifactId().equals(str(receipt,"result_artifact_id"))||!provenance.artifactId().equals(str(receipt,"provenance_artifact_id"))
            ||!result.intent().sha256().equals(str(receipt,"result_sha256"))||!provenance.intent().sha256().equals(str(receipt,"provenance_sha256"))
            ||!str(work.operation(),"fact_id").equals(str(receipt,"fact_id"))||count(receipt,"charged_units")!=accounting.charged()||count(receipt,"charged_calls")!=1
            ||!tenant().equals(str(receipt,"tenant_id"))||!id.equals(str(receipt,"invocation_id"))
            ||!Objects.equals(nullable(work.request().required("authority_proof"),"project_id"),str(receipt,"project_id"))
            ||!"SIMULATION".equals(str(receipt,"execution_mode"))||!"1.0.0".equals(str(receipt,"schema_version"))
            ||!Set.of("false","0").contains(String.valueOf(receipt.get("production_eligible")))
            ||count(receipt,"input_units")!=accounting.input()||count(receipt,"output_units")!=accounting.output()
            ||!text(work.payload(),"receipt_id").equals(str(receipt,"receipt_id"))||!results.manifest().equals(str(receipt,"schema_manifest_sha256"))
            ||!accounting.usageOrigin().equals(str(receipt,"usage_origin"))||!accounting.status().equals(str(receipt,"execution_status")))SimulationResultSchema.mismatch();
        if(accounting.fixtureUsage().isNull()?receipt.get("fixture_usage")!=null:receipt.get("fixture_usage")==null||!accounting.fixtureUsage().equals(schema.parse(str(receipt,"fixture_usage"))))SimulationResultSchema.mismatch();
        var counter=work.request().required("authority_proof").required("counter");
        if(!schema.hash(counter).equals(str(receipt,"counter_sha256"))||!schema.canonical(counter).equals(schema.canonical(schema.parse(str(receipt,"counter")))))SimulationResultSchema.mismatch();
        if(!"PENDING".equals(str(invocation,"settlement_status")))SimulationResultSchema.mismatch();
        for(String key:List.of("provider_request_id","usage_receipt_id","settled_amount","total_tokens","currency","price_version"))if(invocation.get(key)!=null)SimulationResultSchema.mismatch();
        var proof=work.request().required("authority_proof");var reservations=jdbc.queryForList("SELECT * FROM simulation_quota_reservation WHERE tenant_id=? AND invocation_id=? FOR UPDATE",tenant(),id);
        if(reservations.size()!=2||!reservations.stream().map(r->str(r,"account_id")).collect(java.util.stream.Collectors.toSet()).equals(Set.of(text(proof,"enterprise_account_id"),text(proof,"parent_account_id"))))SimulationResultSchema.mismatch();
        for(var row:reservations)if(!"CONSUMED".equals(str(row,"status"))||count(row,"charged_units")!=accounting.charged()||count(row,"reserved_calls")!=1
            ||count(row,"reserved_units")!=SimulationInvocationSchema.sum(accounting.input(),proof.required("maximum_output_units").longValue()))SimulationResultSchema.mismatch();
        var context=ledger.context(text(work.request().required("binding").required("identity"),"context_manifest_id"));String access=str(context,"dependency_access_set_id");var manifest=single(artifacts.manifest(access));
        for(var item:work.command().items())verifyArtifact(work,item,access,manifest.manifestSha256());
    }
}
