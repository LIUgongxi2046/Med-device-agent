package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.SimulationEvaluationStore.row;

/** Actual producer and RSA signer; source/grant/transaction doubles are explicit, not live DB/S3 evidence. */
class SimulationEvaluationServiceTest {
    @TempDir Path keys;
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();final SimulationEvaluationSchema api=new SimulationEvaluationSchema();
    final EnterpriseAccess access=mock(EnterpriseAccess.class);final SimulationWorkerAccess workers=mock(SimulationWorkerAccess.class);
    final SimulationEvaluationStore store=mock(SimulationEvaluationStore.class);final SimulationInvocationStore ledger=mock(SimulationInvocationStore.class);
    final SimulationContextProducer contexts=mock(SimulationContextProducer.class);final SimulationContextProducer.Prepared prepared=mock(SimulationContextProducer.Prepared.class);
    final EvaluationService evaluationDefinitions=mock(EvaluationService.class);
    final SimulationInputArtifactService inputArtifacts=mock(SimulationInputArtifactService.class);
    @SuppressWarnings("unchecked") final org.springframework.beans.factory.ObjectProvider<SimulationInputArtifactService> inputProvider=mock(org.springframework.beans.factory.ObjectProvider.class);
    final SimulationEvaluationCandidateAuthority candidates=mock(SimulationEvaluationCandidateAuthority.class);final EvaluationMapper evaluations=mock(EvaluationMapper.class);
    final EvaluationSchema evaluationsSchema=mock(EvaluationSchema.class);final SimulationExecutionPermissionPolicy execute=mock(SimulationExecutionPermissionPolicy.class);
    final SimulationQuotaPermissionPolicy quota=mock(SimulationQuotaPermissionPolicy.class);final SimulationRetentionPermissionPolicy retention=mock(SimulationRetentionPermissionPolicy.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);final EnterpriseAccess.Admission actor=mock(EnterpriseAccess.Admission.class);
    final ServiceCertificateIdentity services=mock(ServiceCertificateIdentity.class);final ServiceCertificateIdentity.Verified service=mock(ServiceCertificateIdentity.Verified.class);
    final ServiceCertificateTrustStore trust=mock(ServiceCertificateTrustStore.class);final HttpServletRequest http=mock(HttpServletRequest.class);
    final Map<String,List<Map<String,Object>>> rows=new HashMap<>();final LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC).withNano(0);
    final String tenant=id(2),user=id(5),project=id(1),serviceUri="spiffe://medical/simulation";
    SimulationEvaluationService target;SimulationEvaluationPolicyService policies;SimulationEvaluationTokenIssuerTest.Actual actual;
    ObjectNode start;JsonNode seed;boolean failCommit,failTokenCommit;
    SimulationEvaluationServiceTest()throws Exception{}
    static String id(int n){return EvaluationFixture.id(n);}
    @BeforeEach void setup()throws Exception {
        actual=SimulationEvaluationTokenIssuerTest.actual(keys);policies=new SimulationEvaluationPolicyService(access,store,schema,quota,retention);
        seed=schema.request(SimulationInvocationSchemaTest.resource("admission"));var proof=seed.required("authority_proof");
        when(actor.actorId()).thenReturn(user);when(actor.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","1","1"));when(actor.watermarks()).thenReturn(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        when(store.now()).thenReturn(now);when(services.require(http)).thenReturn(service);when(service.serviceUri()).thenReturn(serviceUri);
        var trusted=mock(ServiceCertificateIdentity.Trust.class);when(trust.read()).thenReturn(trusted);when(trusted.registeredCertificates()).thenReturn(Map.of(serviceUri,Set.of("a".repeat(64))));
        var grant=mock(PermissionProtocol.GrantRow.class);when(grant.grantId()).thenReturn(id(4));when(grant.revision()).thenReturn(BigInteger.ONE);
        when(execute.require(any(),any(),any())).thenReturn(grant);when(quota.requireEnterprise(any())).thenReturn(grant);when(quota.requireEvaluation(any(),any(),any())).thenReturn(grant);when(retention.requireEvaluation(any(),any(),any())).thenReturn(grant);
        var definition=EvaluationFixture.definition(new EvaluationSchema(new PermissionSchema()),List.of(EvaluationFixture.sample(60,"SIMULATION","ACCEPTANCE")),List.of("EVAL-01"),1);
        var originalRequest=definition.request();
        var simRequest=new EvaluationProtocol.Create(originalRequest.schema_version(),originalRequest.project_id(),originalRequest.dataset(),originalRequest.sample_manifest_artifact(),originalRequest.samples(),originalRequest.candidates(),originalRequest.rule_revision(),originalRequest.required_dimensions(),originalRequest.variance_policy(),originalRequest.authorized_use_evidence(),"SIMULATION",originalRequest.reason());
        definition=new EvaluationProtocol.Definition(definition.schema_version(),definition.tenant_id(),definition.evaluation_id(),simRequest,definition.candidate_snapshots(),definition.approved_expectation_samples(),definition.authority_grant_id(),definition.authority_revision(),definition.created_by(),definition.created_at());
        when(inputProvider.getIfAvailable()).thenReturn(inputArtifacts);
        when(evaluationDefinitions.requireSimulationDefinition(eq(actor),eq(id(3)),eq("a".repeat(64)))).thenReturn(definition);
        var stored=new EvaluationProtocol.Stored(tenant,project,id(3),"definition","a".repeat(64),"a".repeat(64),"{}","QUEUED",BigInteger.ONE,null,null,"1.0.0","a".repeat(64),user,now,"INTERNAL");
        when(evaluations.definition(any())).thenReturn(List.of(stored));when(evaluationsSchema.definition("definition")).thenReturn(definition);
        start=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("project_id",project).put("evaluation_id",id(3)).put("evaluation_definition_sha256","a".repeat(64)).put("sample_id",id(60)).put("agent_version_id",id(20))
            .put("role_version",id(61)).put("model_config_revision",proof.path("model_config_revision").asText()).put("prompt_revision",id(62)).put("case_id",proof.path("case_id").asText()).put("enterprise_account_id",id(63)).put("retention_policy_id",id(64)).put("repeat_index",0)
            .put("service_uri",serviceUri).put("maximum_input_units",100000).put("maximum_output_units",100000).put("maximum_calls",1).put("parent_maximum_units",200000);start.set("request_parameters",seed.required("request_parameters"));
        var configuration=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",project).put("config_id",id(65)).put("model_config_revision",proof.path("model_config_revision").asText())
            .putNull("based_on_revision").put("content_sha256",proof.path("configuration_sha256").asText()).put("created_by",user).put("created_at",now.toInstant(ZoneOffset.UTC).toString());configuration.putObject("definition");configuration.putArray("allowed_actions").add("model.configure");
        when(prepared.configuration()).thenReturn(configuration);when(prepared.fixture()).thenReturn(seed.required("simulation_case"));when(prepared.exactInput()).thenReturn(seed.required("exact_input"));when(prepared.settings()).thenReturn(seed.required("settings"));
        var registration=new SimulationInvocationSources.Registration(tenant,project,start.path("case_id").asText(),start.path("model_config_revision").asText(),id(66),id(67),id(1260),id(68),proof.path("case_sha256").asText(),proof.path("input_sha256").asText(),proof.path("settings_sha256").asText(),proof.path("parameters_sha256").asText(),seed.path("simulation_case").path("receipt_id").asText(),"1.0.0",schema.manifest(),user,now,"INTERNAL");
        when(prepared.registration()).thenReturn(registration);var sourceBinding=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(registration);when(prepared.sourceBinding()).thenReturn(sourceBinding);
        when(contexts.prepare(eq(subject),any())).thenReturn(prepared);when(contexts.prepare(eq(http),anyString(),anyString(),any())).thenReturn(prepared);
        when(contexts.persist(eq(actor),eq(prepared),anyString(),anyString(),anyString())).thenAnswer(c->new SimulationInvocationSources.Context(tenant,project,c.getArgument(2),"{}",id(69),"a".repeat(64),"a".repeat(64),"SIMULATION","[]",id(62),"1.0.0",schema.manifest(),user,now,"INTERNAL"));
        when(ledger.config(any())).thenReturn(row("classification","INTERNAL"));
        when(store.cell(any())).thenAnswer(c->rows.getOrDefault("simulation_evaluation_job",List.of()));when(store.job(any())).thenAnswer(c->new LinkedHashMap<>(rows.get("simulation_evaluation_job").get(0)));
        when(store.account(any())).thenAnswer(c->{String key=c.getArgument(0);return rows.getOrDefault("simulation_quota_account",List.of()).stream().filter(r->key.equals(r.get("account_id"))).toList();});
        when(store.retention(any())).thenAnswer(c->rows.get("simulation_retention_policy"));
        rows.put("simulation_quota_account",new ArrayList<>(List.of(policies.account(actor,id(63),null,null,400000,2,grant,"INTERNAL"))));
        rows.put("simulation_retention_policy",new ArrayList<>(List.of(row("policy_id",id(64),"project_id",project,"evaluation_id",id(3),"status","ACTIVE","schema_manifest_sha256",schema.manifest(),"approval_grant_id",id(4),"approval_grant_revision",1,"retain_until",now.plusDays(1),"maximum_record_bytes",1000000))));
        doAnswer(c->{String table=c.getArgument(0);Map<String,Object> value=c.getArgument(1);rows.computeIfAbsent(table,k->new ArrayList<>()).add(new LinkedHashMap<>(value));return null;}).when(store).insert(anyString(),any());
        when(access.execute(eq(subject),any())).thenAnswer(c->tx((Function<EnterpriseAccess.Admission,Object>)c.getArgument(1)));
        when(workers.execute(eq(http),anyString(),anyString(),any())).thenAnswer(c->tx((Function<EnterpriseAccess.Admission,Object>)c.getArgument(3)));
        doAnswer(c->{var job=rows.get("simulation_evaluation_job").get(0);job.put("status","CLAIMED");job.put("revision",2);var step=rows.get("run_step").get(0);step.put("status","RUNNING");step.put("lease_owner",serviceUri);step.put("lease_generation",1);step.put("lease_expires_at",c.getArgument(1));rows.get("step_action_binding").get(0).put("lease_generation",1);return null;}).when(store).claim(any(),any());
        when(ledger.step(any())).thenAnswer(c->rows.get("run_step").get(0));when(ledger.action(any())).thenAnswer(c->rows.get("step_action_binding").get(0));
        doAnswer(c->{var job=rows.get("simulation_evaluation_job").get(0);job.put("status","ISSUED");job.put("revision",3);job.put("authority_id",c.getArgument(1));job.put("admission_json",c.getArgument(2));return null;}).when(store).issued(any(),anyString(),anyString());
        target=new SimulationEvaluationService(access,workers,store,ledger,schema,api,contexts,candidates,evaluations,evaluationsSchema,new RunTokenAuthoritySchema(),execute,quota,retention,policies,actual.issuer(),services,trust,inputProvider,evaluationDefinitions);
    }
    Object tx(Function<EnterpriseAccess.Admission,Object> operation){var copy=new HashMap<String,List<Map<String,Object>>>();rows.forEach((k,v)->copy.put(k,new ArrayList<>(v.stream().map(r->(Map<String,Object>)new LinkedHashMap<>(r)).toList())));try{var value=operation.apply(actor);if(failCommit||failTokenCommit&&rows.containsKey("principal_token"))throw new IllegalStateException("COMMIT_FAILED");return value;}catch(RuntimeException e){rows.clear();rows.putAll(copy);throw e;}}
    byte[] bytes(JsonNode node)throws Exception{return schema.canonical(node).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    ObjectNode command(String run,String revision){return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("schema_version","1.0.0").put("tenant_id",tenant).put("run_id",run).put("expected_revision",revision);}
    String started()throws Exception{return target.start(subject,"1",bytes(start)).path("run_id").asText();}
    void claimed(String run)throws Exception{target.claim(http,bytes(command(run,"1").put("lease_seconds",180)));}
    @Test void actualProducerStartClaimIssueBuildsStrictAdmissionAndRealMatchingJws()throws Exception {
        String run=started();var again=target.start(subject,"1",bytes(start));assertEquals(run,again.path("run_id").asText());assertEquals(1,rows.get("execution_registry").size());
        assertNull(rows.get("system_job_run").get(0).get("workflow_id"));assertEquals("READY",rows.get("run_step").get(0).get("status"));
        ((ObjectNode)prepared.configuration()).remove("allowed_actions");claimed(run);
        var result=target.issue(http,bytes(command(run,"2").put("lease_generation","1").put("lifetime_seconds",60)));
        var request=schema.request(bytes(result.admission()));var verified=actual.policies().verify(result.token());var proof=request.required("authority_proof");
        assertEquals(verified.jti(),proof.path("token_jti").asText());assertEquals(verified.tokenSha256(),rows.get("principal_token").get(0).get("token_sha256"));
        assertEquals(request.path("binding").path("exact_input").path("kind").asText(),"EXACT_INPUT");assertEquals(proof.path("issued_at").asText(),((LocalDateTime)rows.get("simulation_execution_authority").get(0).get("created_at")).toInstant(ZoneOffset.UTC).toString());
        assertEquals("ISSUED",result.receipt().path("status").asText());assertFalse(result.toString().contains(result.token()));
        assertThrows(TenantAccessException.class,()->target.issue(http,bytes(command(run,"2").put("lease_generation","1").put("lifetime_seconds",60))));assertEquals(1,rows.get("principal_token").size());
        verify(evaluations,atLeastOnce()).definition(any());assertFalse(rows.containsKey("evaluation_batch"));
        verify(inputArtifacts,atLeast(4)).requireRegisteredInput(eq(actor),isNull(),any());
        verify(evaluationDefinitions,atLeast(4)).requireSimulationDefinition(actor,id(3),"a".repeat(64));
        verify(store,never()).versionHash(id(260));
    }
    @Test void changedCellRequestDeniedRatherThanInventingAlternateRun()throws Exception {started();start.put("maximum_output_units",99999);assertThrows(TenantAccessException.class,()->target.start(subject,"1",bytes(start)));assertEquals(1,rows.get("execution_registry").size());}
    @Test void fixedSampleAndRepeatAreRequiredBeforeRuntimeWrites()throws Exception {start.put("repeat_index",1);assertThrows(TenantAccessException.class,()->started());assertFalse(rows.containsKey("execution_registry"));}
    @Test void sourceOrCandidateRevocationRollsBackAndCannotClaim()throws Exception {String run=started();doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(contexts).require(eq(actor),eq(prepared),any());assertThrows(TenantAccessException.class,()->claimed(run));assertEquals("READY",rows.get("simulation_evaluation_job").get(0).get("status"));}
    @Test void expiryGrantAndCasFailuresProduceNoToken()throws Exception {String run=started();assertThrows(TenantAccessException.class,()->target.claim(http,bytes(command(run,"2").put("lease_seconds",180))));claimed(run);rows.get("run_step").get(0).put("lease_expires_at",now.minusSeconds(1));assertThrows(TenantAccessException.class,()->target.issue(http,bytes(command(run,"2").put("lease_generation","1").put("lifetime_seconds",60))));assertFalse(rows.containsKey("principal_token"));}
    @Test void faultInjectedCommitFailureReturnsNoSuccessfulStartAndRestoresDoubleState()throws Exception {failCommit=true;assertThrows(IllegalStateException.class,()->started());assertFalse(rows.containsKey("execution_registry"));assertFalse(rows.containsKey("simulation_evaluation_job"));}
    @Test void postWriteCandidateFailureRollsBackWholeStartInTransactionDouble()throws Exception {doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED)).when(candidates).require(any(),any(),anyString(),anyString());assertThrows(TenantAccessException.class,()->started());assertFalse(rows.containsKey("run_principal"));assertEquals(1,rows.get("simulation_quota_account").size());}
    @Test void changedParentQuotaGrantAndRetentionApprovalAreRejectedBeforeClaim()throws Exception {
        String run=started();rows.get("simulation_quota_account").get(1).put("approval_grant_revision",2);
        assertThrows(TenantAccessException.class,()->claimed(run));assertEquals("READY",rows.get("simulation_evaluation_job").get(0).get("status"));
        rows.get("simulation_quota_account").get(1).put("approval_grant_revision",1);rows.get("simulation_retention_policy").get(0).put("status","REVOKED");
        assertThrows(TenantAccessException.class,()->claimed(run));assertFalse(rows.containsKey("principal_token"));
    }
    @Test void signingThenCommitFailureLeaksNoTokenAndRestoresClaimedStateInTransactionDouble()throws Exception {
        String run=started();claimed(run);failTokenCommit=true;
        assertThrows(IllegalStateException.class,()->target.issue(http,bytes(command(run,"2").put("lease_generation","1").put("lifetime_seconds",60))));
        assertFalse(rows.containsKey("principal_token"));assertFalse(rows.containsKey("simulation_execution_authority"));assertEquals("CLAIMED",rows.get("simulation_evaluation_job").get(0).get("status"));
    }
    @Test void finiteIntegerAndClosedSchemaFailuresNeverCreateRuntimeRows()throws Exception {
        start.put("maximum_output_units",Long.MAX_VALUE);assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->started());
        start.put("maximum_output_units",100000).put("production_eligible",true);assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->started());assertFalse(rows.containsKey("execution_registry"));
    }
    @Test void ordinaryOrRealEvidenceCannotEnterRestrictedSimulationExecution()throws Exception {
        var real=EvaluationFixture.definition(new EvaluationSchema(new PermissionSchema()),List.of(EvaluationFixture.sample(60,"REAL","ACCEPTANCE")),List.of("EVAL-01"),1);
        when(evaluationDefinitions.requireSimulationDefinition(eq(actor),eq(id(3)),eq("a".repeat(64)))).thenReturn(real);
        assertThrows(TenantAccessException.class,()->started());
        verifyNoInteractions(inputArtifacts);assertFalse(rows.containsKey("execution_registry"));
    }
    @Test void disabledInputBridgeNeverFallsBackToSourceIdentityComparison()throws Exception {
        when(inputProvider.getIfAvailable()).thenReturn(null);
        assertThrows(TenantAccessException.class,()->started());assertFalse(rows.containsKey("execution_registry"));
    }
    @Test void inputArtifactAuthorityIsRevalidatedBeforeClaimAndIssue()throws Exception {
        String run=started();
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED))
            .when(inputArtifacts).requireRegisteredInput(eq(actor),isNull(),any());
        assertThrows(TenantAccessException.class,()->claimed(run));
        assertEquals("READY",rows.get("simulation_evaluation_job").get(0).get("status"));
        doNothing().when(inputArtifacts).requireRegisteredInput(eq(actor),isNull(),any());claimed(run);
        doThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH))
            .when(inputArtifacts).requireRegisteredInput(eq(actor),isNull(),any());
        assertThrows(TenantAccessException.class,()->target.issue(http,bytes(command(run,"2").put("lease_generation","1").put("lifetime_seconds",60))));
        assertFalse(rows.containsKey("principal_token"));
    }

    @Test void revokedEvaluationRuleOrDependencyPreventsWorkerClaimAndIssue()throws Exception {
        String run=started();var fixed=evaluationDefinitions.requireSimulationDefinition(actor,id(3),"a".repeat(64));
        when(evaluationDefinitions.requireSimulationDefinition(eq(actor),eq(id(3)),anyString()))
            .thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->claimed(run));assertEquals("READY",rows.get("simulation_evaluation_job").get(0).get("status"));
        doReturn(fixed).when(evaluationDefinitions).requireSimulationDefinition(eq(actor),eq(id(3)),anyString());claimed(run);
        when(evaluationDefinitions.requireSimulationDefinition(eq(actor),eq(id(3)),anyString()))
            .thenThrow(new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED));
        assertThrows(TenantAccessException.class,()->target.issue(http,bytes(command(run,"2").put("lease_generation","1").put("lifetime_seconds",60))));
        assertFalse(rows.containsKey("principal_token"));
    }

}
