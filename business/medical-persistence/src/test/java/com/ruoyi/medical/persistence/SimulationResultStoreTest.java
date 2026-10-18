package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Actual store SQL/parameter and reread logic over JDBC doubles, not MySQL rollback/concurrency evidence. */
class SimulationResultStoreTest {
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();final SimulationResultSchema results=new SimulationResultSchema(schema);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);final SimulationInvocationStore ledger=mock(SimulationInvocationStore.class);
    final ArtifactRegistryMapper artifacts=mock(ArtifactRegistryMapper.class);final DependencyManifestSchema manifests=new DependencyManifestSchema();final DependencyLineage lineage=mock(DependencyLineage.class);
    final SimulationResultStore store=spy(new SimulationResultStore(jdbc,ledger,schema,results,artifacts,manifests,lineage));
    JsonNode request,payload,identity,proof;SimulationResultStore.Work work;SimulationResultSchema.Accounting accounting;
    Map<String,Object> invocation,action,receipt;String tenant,id,access;
    SimulationResultStoreTest()throws Exception{}
    @BeforeEach void setup()throws Exception {
        request=schema.request(SimulationInvocationSchemaTest.resource("admission"));identity=request.required("binding").required("identity");proof=request.required("authority_proof");
        payload=schema.receipt(request,SimulationInvocationSchemaTest.resource("result"),SimulationInvocationSchemaTest.resource("provenance"));accounting=results.accounting(request,payload);
        tenant=identity.required("tenant_id").textValue();id=identity.required("invocation_id").textValue();access=UUID.randomUUID().toString();doReturn(tenant).when(store).tenant();
        var command=results.create(request,payload);for(int n=0;n<2;n++){var i=command.items().get(n);command=results.verified(command,n,new ObjectRef(i.intent().objectKey(),"real-version-"+n,i.intent().sha256(),i.intent().sizeBytes(),i.intent().mediaType()));}
        var operation=new HashMap<String,Object>();operation.put("invocation_id",id);operation.put("fact_id",UUID.randomUUID().toString());operation.put("status","COMPLETED");operation.put("revision",5);
        work=new SimulationResultStore.Work(request,payload,operation,command,proof.required("actor_id").textValue(),"INTERNAL");
        var result=command.items().get(0);var provenance=command.items().get(1);
        invocation=new HashMap<>();invocation.put("invocation_id",id);invocation.put("status","SUCCEEDED");invocation.put("settlement_status","PENDING");invocation.put("result_artifact_id",result.artifactId());invocation.put("result_sha256",result.intent().sha256());invocation.put("dispatch_generation",1);invocation.put("revision",3);
        var ref=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("input_data_kind","SIMULATION").put("production_eligible",false)
            .put("result_artifact_id",result.artifactId()).put("result_sha256",result.intent().sha256()).put("provenance_artifact_id",provenance.artifactId()).put("provenance_sha256",provenance.intent().sha256());
        action=new HashMap<>();action.put("state","SUCCEEDED");action.put("result_artifact_id",result.artifactId());action.put("result_ref",schema.canonical(ref));
        receipt=new HashMap<>();receipt.put("tenant_id",tenant);receipt.put("project_id",project());receipt.put("invocation_id",id);receipt.put("execution_mode","SIMULATION");receipt.put("schema_version","1.0.0");receipt.put("production_eligible",false);receipt.put("fact_id",operation.get("fact_id"));receipt.put("receipt_id",payload.required("receipt_id").textValue());
        receipt.put("result_artifact_id",result.artifactId());receipt.put("result_sha256",result.intent().sha256());receipt.put("provenance_artifact_id",provenance.artifactId());receipt.put("provenance_sha256",provenance.intent().sha256());
        receipt.put("input_units",accounting.input());receipt.put("output_units",accounting.output());receipt.put("charged_units",accounting.charged());receipt.put("charged_calls",1);receipt.put("usage_origin","UNKNOWN");receipt.put("execution_status","SUCCEEDED");receipt.put("schema_manifest_sha256",results.manifest());receipt.put("counter",schema.canonical(proof.required("counter")));receipt.put("counter_sha256",schema.hash(proof.required("counter")));
        when(ledger.invocation(id)).thenReturn(invocation);when(ledger.action(identity)).thenReturn(action);when(ledger.context(anyString())).thenReturn(Map.of("dependency_access_set_id",access));
        var manifest=new ArtifactRegistryProtocol.ManifestRow(tenant,project(),access,"{}","a".repeat(64),"a".repeat(64),BigInteger.ZERO,"1.0.0",manifests.manifestSha256());when(artifacts.manifest(access)).thenReturn(List.of(manifest));
        for(var item:command.items()){
            var r=item.reference();when(artifacts.artifact(item.artifactId())).thenReturn(List.of(new ArtifactRegistryProtocol.ArtifactRow(tenant,project(),item.artifactId(),item.kind(),StoredObjectReferences.encode(r),r.objectKey(),r.objectVersion(),r.sha256(),new BigInteger(r.sizeBytes()),r.mediaType(),access,r.sha256(),"1.0.0",item.schemaHash(),"INTERNAL")));
            when(artifacts.binding(item.artifactId())).thenReturn(List.of(new ArtifactRegistryProtocol.BindingRow(tenant,project(),item.artifactId(),"EVALUATION",proof.required("evaluation_id").textValue(),item.schemaRef(),item.schemaHash(),access,manifest.manifestSha256())));
            when(artifacts.version(item.artifactId())).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(item.artifactId(),project(),"ARTIFACT",r.sha256())));
        }
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{
            String sql=call.getArgument(0);if(sql.contains("simulation_quota_settlement"))return List.of(receipt);
            if(sql.contains("simulation_quota_reservation"))return List.of(reservation("enterprise_account_id"),reservation("parent_account_id"));
            throw new AssertionError("unexpected query: "+sql);
        });
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
    }
    String project(){return identity.required("project_id").isNull()?null:identity.required("project_id").textValue();}
    Map<String,Object> reservation(String key){return Map.of("account_id",proof.required(key).textValue(),"status","CONSUMED","charged_units",accounting.charged(),"reserved_calls",1,"reserved_units",accounting.input()+proof.required("maximum_output_units").longValue());}
    Map<String,Object> account(String key){return Map.of("account_id",proof.required(key).textValue(),"revision",2,"reserved_units",6000,"reserved_calls",3,"spent_units",100,"spent_calls",4);}
    void accountAfter(Map<String,Object> before,long reserved,long charged){when(ledger.account(before.get("account_id").toString())).thenReturn(Map.of("account_id",before.get("account_id"),"reserved_units",6000-reserved,"reserved_calls",2,"spent_units",100+charged,"spent_calls",5));}
    @Test void actualCounterSqlConsumesBothAccountsAndBothHeldReservations() {
        long reserved=accounting.input()+proof.required("maximum_output_units").longValue();var enterprise=account("enterprise_account_id");var parent=account("parent_account_id");accountAfter(enterprise,reserved,accounting.charged());accountAfter(parent,reserved,accounting.charged());
        store.consume(enterprise,id,reserved,accounting.charged());store.consume(parent,id,reserved,accounting.charged());
        var calls=org.mockito.ArgumentCaptor.forClass(Object[].class);verify(jdbc,times(2)).update(startsWith("UPDATE simulation_quota_account SET"),calls.capture());
        assertEquals(List.of(enterprise.get("account_id"),parent.get("account_id")),calls.getAllValues().stream().map(a->a[3]).toList());
        for(var args:calls.getAllValues()){assertEquals(reserved,args[0]);assertEquals(accounting.charged(),args[1]);assertEquals(tenant,args[2]);assertEquals(2,args[4]);}
        verify(jdbc,times(2)).update(contains("status='CONSUMED'"),any(Object[].class));
    }
    @Test void reservationCasFailurePropagatesAndCannotReachSecondAccount() {
        var enterprise=account("enterprise_account_id");long reserved=accounting.input()+proof.required("maximum_output_units").longValue();
        when(jdbc.update(contains("status='CONSUMED'"),any(Object[].class))).thenReturn(0);
        assertThrows(TenantAccessException.class,()->{store.consume(enterprise,id,reserved,accounting.charged());store.consume(account("parent_account_id"),id,reserved,accounting.charged());});
        verify(jdbc,times(1)).update(startsWith("UPDATE simulation_quota_account SET"),any(Object[].class));verify(ledger,never()).account(anyString());
    }
    @Test void secondAccountCasFailureStopsBeforeAnySuccessfulTerminalReturn() {
        long reserved=accounting.input()+proof.required("maximum_output_units").longValue();var enterprise=account("enterprise_account_id");var parent=account("parent_account_id");accountAfter(enterprise,reserved,accounting.charged());
        doAnswer(call->{Object[] args=call.getArguments();return parent.get("account_id").equals(args[4])?0:1;}).when(jdbc).update(startsWith("UPDATE simulation_quota_account SET"),any(Object[].class));
        assertThrows(TenantAccessException.class,()->{store.consume(enterprise,id,reserved,accounting.charged());store.consume(parent,id,reserved,accounting.charged());});
        verify(jdbc,times(2)).update(startsWith("UPDATE simulation_quota_account SET"),any(Object[].class));verify(jdbc,times(1)).update(contains("status='CONSUMED'"),any(Object[].class));
        // JDBC doubles verify exception/order only. Rollback of the first update requires actual MySQL evidence.
    }
    @Test void corruptedAccountRereadIsRejectedAfterSql() {
        var enterprise=account("enterprise_account_id");when(ledger.account(anyString())).thenReturn(enterprise);
        assertThrows(TenantAccessException.class,()->store.consume(enterprise,id,4000,accounting.charged()));
    }
    @Test void completedReplayRereadsExactReceiptArtifactsAndNeverChargesAgain()throws Exception {
        assertEquals("SUCCEEDED",store.finish(work,invocation).status());verify(jdbc,never()).update(anyString(),any(Object[].class));verify(ledger,never()).reserveAccount(any(),anyLong());
        verify(artifacts).artifact(work.command().items().get(0).artifactId());verify(artifacts).artifact(work.command().items().get(1).artifactId());
    }
    @Test void alteredReceiptInputUnitsCounterUnknownUsageAndOriginalReceiptFailReread()throws Exception {
        for(String field:List.of("input_units","output_units","receipt_id","counter_sha256","fixture_usage")){
            Object before=receipt.get(field);receipt.put(field,field.endsWith("units")?999999:field.equals("fixture_usage")?"{}":"b".repeat(64));
            assertThrows(TenantAccessException.class,()->store.finish(work,invocation),field);
            if(before==null)receipt.remove(field);else receipt.put(field,before);
        }
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void wrongRegisteredObjectVersionOrActionBindingRejectsReplay()throws Exception {
        String artifact=work.command().items().get(0).artifactId();var row=artifacts.artifact(artifact).get(0);
        when(artifacts.artifact(artifact)).thenReturn(List.of(new ArtifactRegistryProtocol.ArtifactRow(row.tenantId(),row.projectId(),row.artifactId(),row.artifactKind(),row.storedObject(),row.objectKey(),"changed-version",row.sha256(),row.sizeBytes(),row.mediaType(),row.accessSetId(),row.contentSha256(),row.schemaVersion(),row.schemaManifest(),row.classification())));
        assertThrows(TenantAccessException.class,()->store.finish(work,invocation));when(artifacts.artifact(artifact)).thenReturn(List.of(row));
        action.put("result_artifact_id",UUID.randomUUID().toString());assertThrows(TenantAccessException.class,()->store.finish(work,invocation));
    }
    @Test void providerFinanceNeverAppearsOnSimulationReplay() {
        invocation.put("settled_amount","0.00000000");assertThrows(TenantAccessException.class,()->store.finish(work,invocation));
    }

    Object invoke(String name,Class<?>[] types,Object... arguments)throws Exception {
        var method=SimulationResultStore.class.getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(store,arguments);}catch(java.lang.reflect.InvocationTargetException failure){
            if(failure.getCause() instanceof Exception exception)throw exception;throw failure;
        }
    }
    @Test void actualArtifactRegistrationWritesVersionObjectBindingAndRereads()throws Exception {
        var item=work.command().items().get(0);
        invoke("register",new Class<?>[]{SimulationResultStore.Work.class,SimulationResultSchema.Item.class,String.class,String.class,List.class},work,item,access,"a".repeat(64),List.of());
        verify(jdbc).update(startsWith("INSERT INTO version_registry"),any(Object[].class));
        var values=org.mockito.ArgumentCaptor.forClass(Object[].class);verify(jdbc).update(contains("INSERT INTO artifact ("),values.capture());
        assertEquals(tenant,values.getValue()[0]);assertEquals(item.artifactId(),values.getValue()[2]);assertEquals(item.reference().objectVersion(),values.getValue()[7]);
        assertEquals(StoredObjectReferences.encode(item.reference()),values.getValue()[5]);verify(jdbc).update(contains("INSERT INTO artifact_content_binding"),any(Object[].class));
        verify(lineage).require(eq(item.artifactId()),eq(List.of()),any());
    }
    void parentFixture(String status)throws Exception {
        var job=new HashMap<String,Object>();job.put("status",status);job.put("revision",2);
        var step=new HashMap<String,Object>();step.put("status","RUNNING");step.put("revision",2);
        when(ledger.job(identity.required("run_id").textValue())).thenReturn(job);when(ledger.step(identity)).thenReturn(step);
        var cell=new HashMap<String,Object>();for(String key:List.of("run_id","step_id","action_id","invocation_id","context_manifest_id","model_config_revision"))cell.put(key,identity.required(key).textValue());
        cell.put("status","ISSUED");cell.put("revision",3);cell.put("job_id",UUID.randomUUID().toString());
        doAnswer(call->{String sql=call.getArgument(0);
            if(sql.startsWith("SELECT * FROM simulation_evaluation_job"))return List.of(cell);
            if(sql.startsWith("SELECT step_id FROM run_step"))return List.of(Map.of("step_id",identity.required("step_id").textValue()));
            if(sql.startsWith("SELECT step_id,action_id"))return List.of(Map.of("action_id",identity.required("action_id").textValue()));
            if(sql.startsWith("SELECT status,revision FROM simulation_evaluation_job"))return List.of(cell);
            throw new AssertionError("unexpected parent query: "+sql);
        }).when(jdbc).queryForList(anyString(),any(Object[].class));
        doAnswer(call->{String sql=call.getArgument(0);if(sql.startsWith("UPDATE run_step"))step.put("status","SUCCEEDED");
            if(sql.startsWith("UPDATE system_job_run")){job.put("status","SUCCEEDED");job.put("ended_at",java.time.LocalDateTime.now());}
            if(sql.startsWith("UPDATE simulation_evaluation_job")){cell.put("status","SUCCEEDED");cell.put("revision",4);}return 1;
        }).when(jdbc).update(anyString(),any(Object[].class));
    }
    @Test void singleOriginalCellCanTerminateOnlyAfterItsExactParentChecks()throws Exception {
        parentFixture("RUNNING");invoke("finishSingleCell",new Class<?>[]{SimulationResultStore.Work.class,String.class,String.class,boolean.class},work,"SUCCEEDED","{}",false);
        verify(jdbc).update(startsWith("UPDATE run_step"),any(Object[].class));verify(jdbc).update(startsWith("UPDATE system_job_run"),any(Object[].class));verify(jdbc).update(startsWith("UPDATE simulation_evaluation_job"),any(Object[].class));
    }
    @Test void cancelledInvocationOrNonrunningParentNeverRestoresParentSuccess()throws Exception {
        parentFixture("RUNNING");invoke("finishSingleCell",new Class<?>[]{SimulationResultStore.Work.class,String.class,String.class,boolean.class},work,"SUCCEEDED","{}",true);
        verify(jdbc,never()).update(anyString(),any(Object[].class));parentFixture("CANCEL_REQUESTED");
        invoke("finishSingleCell",new Class<?>[]{SimulationResultStore.Work.class,String.class,String.class,boolean.class},work,"SUCCEEDED","{}",false);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
