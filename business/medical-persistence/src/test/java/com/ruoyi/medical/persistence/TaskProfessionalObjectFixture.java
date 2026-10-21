package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.RowMapper;
import static org.mockito.Mockito.*;

/** Real 075 v2 preparation, attempt, governance and registration with explicit JDBC/current
 * authority doubles. This is not a deployed database, mTLS, source-rights or isolation test. */
class TaskProfessionalObjectFixture extends TaskNodeInputFixture {
    final TaskProfessionalSchema professionalSchema=new TaskProfessionalSchema();
    final TaskNodeInputAuthority.Verified professionalInput=mock(TaskNodeInputAuthority.Verified.class);
    final TaskNodeProducerAuthority.LeasedProduction leasedProducer=mock(TaskNodeProducerAuthority.LeasedProduction.class);
    final TaskProfessionalRuntimeService.Envelope envelope=mock(TaskProfessionalRuntimeService.Envelope.class);
    final TaskProfessionalRuntimeService.Deployment deployment=mock(TaskProfessionalRuntimeService.Deployment.class);
    final DependencyLineage professionalLineage=mock(DependencyLineage.class);
    final TaskProfessionalContentAuthority professionalContents=mock(TaskProfessionalContentAuthority.class);
    final TaskProfessionalContentAuthority.Verified professionalContent=mock(TaskProfessionalContentAuthority.Verified.class);
    final TaskProfessionalObjectLedger professionalLedger=new TaskProfessionalObjectLedger(db,json,professionalSchema);
    final TaskProfessionalObjectGovernance governance=new TaskProfessionalObjectGovernance(db,json,professionalSchema,professionalLineage,new DependencyLineage.Limits(512,2048));
    final TaskProfessionalObjectRegistration professionalRegistrations=new TaskProfessionalObjectRegistration(db,json,professionalSchema,professionalLedger,governance,professionalContents,new DependencyManifestSchema(),professionalLineage,new DependencyLineage.Limits(512,2048));
    final String session=id(80),principal=id(81),action=id(82);final byte[] envelopeBytes;
    TaskProfessionalObjectFixture()throws Exception {
        var identity=new FormalWorkerAccess.Identity(tenant,project,run,principal,actor,"TASK",step,action,"1","spiffe://test.invalid/worker","{}","{}",List.of("task.read"),null);
        when(admission.controlWorker()).thenReturn(false);when(admission.formalWorker()).thenReturn(true);var formal=mock(FormalWorkerAccess.Proof.class);when(admission.formalProof()).thenReturn(formal);when(formal.identity()).thenReturn(identity);
        when(professionalInput.tenantId()).thenReturn(tenant);when(professionalInput.projectId()).thenReturn(project);when(professionalInput.taskId()).thenReturn(task);when(professionalInput.runId()).thenReturn(run);when(professionalInput.stepId()).thenReturn(step);when(professionalInput.inputId()).thenReturn(id(83));when(professionalInput.inputSha256()).thenReturn(hash('a'));when(professionalInput.receiptSha256()).thenReturn(hash('b'));
        var replay=json.object();replay.putArray("dependency_versions").addObject().put("version_id",id(90)).put("project_id",project).put("entity_kind","CONFIGURATION").put("content_sha256",hash('c'));replay.putArray("dependency_edges");
        rows("task_run").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"project_id",project,"run_id",run,"task_id",task,"created_by",actor,"execution_kind","TASK","classification","INTERNAL","replay_manifest",json.canonical(replay))));
        when(professionalInput.manifest()).thenReturn(json.object().put("replay_sha256",TaskProfessionalObjectGovernance.hash(replay)));
        var graph=new org.jgrapht.graph.DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);graph.addVertex(id(90));
        when(professionalLineage.inspect(eq(id(90)),any())).thenReturn(new DependencyLineage.VerifiedLineage(id(90),graph,Map.of(id(90),new ArtifactRegistryProtocol.VersionRow(id(90),project,"CONFIGURATION",hash('c')))));
        when(leasedProducer.ruleId()).thenReturn(id(84));when(leasedProducer.ruleSha256()).thenReturn(hash('c'));when(leasedProducer.decisionId()).thenReturn(id(85));when(leasedProducer.decisionSha256()).thenReturn(hash('d'));
        when(leasedProducer.runId()).thenReturn(run);when(leasedProducer.stepId()).thenReturn(step);when(leasedProducer.principalId()).thenReturn(principal);when(leasedProducer.leaseGeneration()).thenReturn("1");when(leasedProducer.workerService()).thenReturn(identity.workerService());when(leasedProducer.authorizations()).thenReturn(json.object().put("explicit_boundary","current_098_authority"));
        when(professionalContents.require(professionalInput)).thenReturn(professionalContent);when(professionalContent.roots()).thenReturn(List.of(id(20)));when(professionalContent.members()).thenReturn(List.of(new ArtifactRegistryProtocol.MemberRow(id(20),id(25))));
        var value=json.object().put("schema_version","1.0.0").put("execution_kind","TASK").put("node_kind","N02").put("tenant_id",tenant).put("project_id",project).put("task_id",task).put("run_id",run).put("step_id",step).put("action_id",action).put("session_id",session).put("question_id",id(86)).put("input_artifact_id",id(83)).put("agent_release_id",id(87)).put("deployment_revision",id(88)).put("lease_generation",1).put("worker_service_uri",identity.workerService());
        value.put("compiled_prompt","{} ").put("prompt_sha256",WireValues.sha256("{} ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));value.putObject("prompt_provenance").put("launch_sha256",value.required("prompt_sha256").asText());
        for(String f:List.of("input_sha256","input_receipt_sha256","runtime_manifest_sha256","publication_approval_sha256","deployment_sha256"))value.put(f,hash('a'));
        value.set("role_version",json.tree(basis(13)));value.set("execution_policy_version",json.tree(basis(14)));value.putArray("action_scope").add("task.read");envelopeBytes=ContentReadAuthorization.canonicalBytes(value);
        when(envelope.admission()).thenReturn(admission);when(envelope.input()).thenReturn(professionalInput);when(envelope.bytes()).thenReturn(envelopeBytes);when(envelope.payloadSha256()).thenReturn(WireValues.sha256(envelopeBytes));when(envelope.sessionId()).thenReturn(session);when(envelope.deployment()).thenReturn(deployment);when(deployment.sha256()).thenReturn(hash('a'));
        when(db.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(c->{String sql=c.getArgument(0);if(sql.contains("SELECT classification FROM task_run"))return List.of("INTERNAL");throw new AssertionError(sql);});
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->selectProfessional(c.getArgument(0),arguments(c.getArguments())));
        when(db.update(anyString(),any(Object[].class))).thenAnswer(c->writeProfessional(c.getArgument(0),arguments(c.getArguments())));
    }
    TaskProfessionalObjectLedger.Attempt initial()throws Exception{return tx(()->{var intent=professionalLedger.prepare(leasedProducer,envelope);return professionalLedger.attempt(leasedProducer,envelope,intent);});}
    TaskProfessionalObjectLedger.Attempt recovered()throws Exception{return tx(()->{var intent=professionalLedger.prepare(leasedProducer,envelope);return professionalLedger.requireAttempt(leasedProducer,envelope,intent);});}
    private List<Map<String,Object>> selectProfessional(String sql,Object[] args){
        executedSql.add(sql);
        if(sql.startsWith("SELECT a.resource_ref"))return rows("audit_event").stream().filter(r->"runtime.professional.prepared".equals(r.get("action"))).map(LinkedHashMap::new).map(r->(Map<String,Object>)r).toList();
        if(sql.startsWith("SELECT e.payload"))return rows("outbox_event").stream().filter(r->"runtime.professional.prepared".equals(r.get("event_type"))).map(LinkedHashMap::new).map(r->(Map<String,Object>)r).toList();
        var from=Pattern.compile("FROM ([a-z_]+)").matcher(sql);if(!from.find())throw new AssertionError(sql);String table=from.group(1);
        var result=new ArrayList<Map<String,Object>>();for(var row:rows(table)){
            var matches=Pattern.compile("(?:[a-z]\\.)?([a-z_]+)=\\?").matcher(sql.substring(sql.indexOf(" WHERE ")));boolean match=true;int n=0;
            while(matches.find()){Object expected=args[n++];if(!Objects.equals(String.valueOf(expected),String.valueOf(row.get(matches.group(1))))){match=false;break;}}
            if(match){if(sql.contains("action='runtime.professional.registered'")&&!"runtime.professional.registered".equals(row.get("action")))continue;result.add(new LinkedHashMap<>(row));}
        }
        if(sql.contains("ORDER BY upstream_version_id"))result.sort(Comparator.comparing(r->r.get("upstream_version_id").toString()));return result;
    }
    private int writeProfessional(String sql,Object[] args){
        executedSql.add(sql);
        if(sql.startsWith("UPDATE runtime_object_state")){var row=only("runtime_object_state");row.put("stage","ATTEMPTED");row.put("revision",2);row.put("updated_at",now);return 1;}
        if(sql.contains("INSERT INTO runtime_object_state")){var row=journalMeta();row.put("stage","PREPARED");row.put("revision",1);row.put("updated_at",now);rows("runtime_object_state").add(row);return 1;}
        if(sql.contains("INSERT INTO runtime_object_attempt")){rows("runtime_object_attempt").add(journalMeta());return 1;}
        if(sql.contains("INSERT INTO audit_event")&&sql.contains("SELECT tenant_id")){var row=journalMeta();row.remove("run_id");row.remove("write_id");row.put("audit_id",args[0]);row.put("resource_ref",args[1]);row.put("subject_id",actor);row.put("action","runtime.professional.prepared");row.put("result","SUCCESS");row.put("reason_code",null);row.put("request_id",only("runtime_object_intent").get("write_id"));row.put("trace_id",run);rows("audit_event").add(row);return 1;}
        if(sql.contains("INSERT INTO outbox_event")&&sql.contains("SELECT tenant_id")){rows("outbox_event").add(new LinkedHashMap<>(Map.of("tenant_id",tenant,"event_id",only("runtime_object_intent").get("write_id"),"aggregate_id",run,"aggregate_revision",1,"event_type","runtime.professional.prepared","payload",args[0],"occurred_at",now)));return 1;}
        String normalized=sql.replace("CAST(? AS JSON)","?").replace("UTC_TIMESTAMP(6)","__NOW__");
        var insert=Pattern.compile("INSERT INTO ([a-z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)",Pattern.DOTALL).matcher(normalized);if(!insert.find())throw new AssertionError(sql);
        String[] fields=insert.group(2).split(","),values=insert.group(3).split(",");if(fields.length!=values.length)throw new AssertionError(sql);int n=0;var row=new LinkedHashMap<String,Object>();
        for(int x=0;x<fields.length;x++){String v=values[x].trim();row.put(fields[x].trim(),v.equals("?")?args[n++]:v.equals("__NOW__")?now:v.startsWith("'")?v.substring(1,v.length()-1):Integer.valueOf(v));}
        if(n!=args.length)throw new AssertionError(sql);if(insert.group(1).equals("audit_event"))row.put("reason_code",null);if(insert.group(1).equals("lineage_edge"))row.put("decision_ref",null);rows(insert.group(1)).add(row);return 1;
    }
    private Map<String,Object> journalMeta(){var original=only("runtime_object_intent");var row=new LinkedHashMap<String,Object>();for(String f:List.of("tenant_id","project_id","run_id","write_id","schema_manifest_sha256","created_by","created_at","classification"))row.put(f,original.get(f));row.put("schema_version","1.0.0");return row;}
    private static Object[] arguments(Object[] args){var result=Arrays.copyOfRange(args,1,args.length);return result.length==1&&result[0] instanceof Object[] array?array:result;}
}
