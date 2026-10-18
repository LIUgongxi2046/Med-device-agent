package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real workflow/schema/history checks over explicit storage/closure fixtures. No DB or production grant claim. */
class FormalRuntimeCapabilityAuthoringServiceTest {
 final PermissionSchema json=new PermissionSchema();final FormalRuntimeCapabilitySchema schema=new FormalRuntimeCapabilitySchema(json);
 final FormalAgentCandidateMapper runtimeRows=mock(FormalAgentCandidateMapper.class);
 final AgentPublicationMapper m=mock(AgentPublicationMapper.class);final ConfigurationAuthoringMapper c=mock(ConfigurationAuthoringMapper.class);final PermissionMapper p=mock(PermissionMapper.class);
 final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);final AgentPublicationDependencies deps=mock(AgentPublicationDependencies.class);final EvaluationReferences refs=mock(EvaluationReferences.class);
 final AgentPublicationMapper guarded=(AgentPublicationMapper)java.lang.reflect.Proxy.newProxyInstance(AgentPublicationMapper.class.getClassLoader(),new Class[]{AgentPublicationMapper.class},(proxy,method,args)->{
  checkSubject(method.getName(),args);
  try{return method.invoke(m,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
 });
 final EnterpriseAccess.Admission a=mock(EnterpriseAccess.Admission.class);final AgentPublicationJournal journal=new AgentPublicationJournal(guarded,p,new AgentPublicationSchema(json),json);
 final FormalRuntimeCapabilityIdentityAuthority identities=new FormalRuntimeCapabilityIdentityAuthority(runtimeRows,c,guarded,journal,schema,json,deps,resources);
 final FormalRuntimeCapabilityAuthoringService capabilities=new FormalRuntimeCapabilityAuthoringService(guarded,journal,schema,json,deps,resources,refs,identities,new IdentityDirectoryPolicy("INTERNAL"));
 final Map<String,AgentPublicationMapper.Journal> records=new HashMap<>();final Map<String,SelectionOperationRow> operations=new HashMap<>();final Map<String,String> audits=new HashMap<>(),events=new HashMap<>();
 final Map<String,ArtifactRegistryProtocol.VersionRow> versions=new HashMap<>();final Map<String,ConfigurationAuthoringProtocol.Stored> caps=new HashMap<>();final Map<String,List<ConfigurationAuthoringProtocol.Parent>> parents=new HashMap<>();final Map<String,String> decisions=new HashMap<>();
 String actor=id(2);LocalDateTime now=LocalDateTime.of(2026,10,18,10,0);boolean revoked=false;
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}static String H="a".repeat(64);
 FormalRuntimeCapabilityAuthoringServiceTest()throws Exception {
  when(a.actorId()).thenAnswer(i->actor);when(a.context()).thenReturn(new BusinessSessionProtocol.Context(id(1),"1","1","1"));when(a.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",id(2),null,null));when(p.databaseTime()).thenAnswer(i->now);
  when(resources.require(eq(a),any(),anyString(),anyString())).thenAnswer(i->{if(revoked)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return grant(i.getArgument(2),i.getArgument(3));});
  when(deps.require(eq(a),nullable(String.class),anyList(),anyString())).thenReturn(new AgentPublicationDependencies.Checked(List.of(id(5)),json.object().put("fixture_current_dependency","readable")));
  when(m.journal(anyMap())).thenAnswer(i->optional(records.get(v(i).get("recordId"))));
  when(m.journalByKey(anyMap())).thenAnswer(i->records.values().stream().filter(r->r.createdBy().equals(v(i).get("creatorId"))&&r.kind().equals(v(i).get("kind"))&&r.idempotencyKey().equals(v(i).get("key"))).toList());
  when(m.operation(anyMap())).thenAnswer(i->optional(operations.get(opKey((String)v(i).get("creatorId"),(String)v(i).get("operationName"),(String)v(i).get("key")))));
  when(m.insertOperation(anyMap())).thenAnswer(i->{var r=(AgentPublicationMapper.Journal)v(i).get("row");operations.put(opKey(r.createdBy(),(String)v(i).get("operationName"),r.idempotencyKey()),new SelectionOperationRow(r.operationId(),r.requestSha256(),"COMPLETED",201,r.receiptJson(),r.recordId(),"1.0.0",r.schemaManifest()));return 1;});
  when(m.insertJournal(anyMap())).thenAnswer(i->{var r=(AgentPublicationMapper.Journal)v(i).get("row");records.put(r.recordId(),r);return 1;});when(m.version(anyMap())).thenAnswer(i->optional(versions.get(v(i).get("recordId"))));
  when(m.insertVersion(anyMap())).thenAnswer(i->{var v=v(i);String id=(String)v.get("versionId");versions.put(id,new ArtifactRegistryProtocol.VersionRow(id,(String)v.get("projectId"),(String)v.get("entityKind"),(String)v.get("contentHash")));return 1;});
  when(p.saveAudit(anyString(),nullable(String.class),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{audits.put(i.getArgument(2),i.getArgument(4));return 1;});when(p.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(i->{events.put(i.getArgument(1),i.getArgument(5));return 1;});
  when(m.audit(anyMap())).thenAnswer(i->optional(audits.get(((AgentPublicationMapper.Journal)v(i).get("row")).auditId())));when(m.event(anyMap())).thenAnswer(i->optional(events.get(((AgentPublicationMapper.Journal)v(i).get("row")).eventId())));
  when(m.policyDecision(anyMap())).thenAnswer(i->optional(records.get(decisions.get(v(i).get("draftId")))));when(m.insertPolicyDecision(anyMap())).thenAnswer(i->{var r=(AgentPublicationMapper.Journal)v(i).get("row");assertNull(decisions.putIfAbsent(r.resourceId(),r.recordId()));return 1;});
  when(m.insertHead(anyMap())).thenReturn(1);when(m.initializeHead(anyMap())).thenReturn(1);
  when(m.insertCapability(anyMap())).thenAnswer(i->{var v=v(i);var projection=json.object().put("artifact_digest",(String)v.get("artifactDigest"));for(var field:Map.of("input_schema_ref","inputSchema","output_schema_ref","outputSchema","tool_scope","toolScope").entrySet())projection.set(field.getKey(),schema.read((String)v.get(field.getValue())));String version=(String)v.get("versionId"),hash=(String)v.get("contentHash");caps.put(version,new ConfigurationAuthoringProtocol.Stored(id(1),(String)v.get("projectId"),(String)v.get("configId"),version,(String)v.get("kind"),null,schema.canonical(projection),hash,hash,"DRAFT","DRAFT",version,"1.0.0",schema.manifest(),actor,(LocalDateTime)v.get("now"),"INTERNAL"));return 1;});
  when(m.insertLineage(anyMap())).thenAnswer(i->{var v=v(i);parents.put((String)v.get("versionId"),List.of(new ConfigurationAuthoringProtocol.Parent((String)v.get("upstreamId"),(String)v.get("relation"),H)));return 1;});
  when(c.stored(anyMap())).thenAnswer(i->optional(caps.get(v(i).get("versionId"))));when(c.parents(anyMap())).thenAnswer(i->parents.getOrDefault(v(i).get("versionId"),List.of()));when(runtimeRows.runtimeAuthor(anyMap())).thenAnswer(i->records.values().stream().filter(r->r.kind().equals("AUTHOR")&&r.resourceId().equals(v(i).get("versionId"))).toList());
 }
 Map<String,Object> v(org.mockito.invocation.InvocationOnMock i){return i.getArgument(0);}static <T>List<T>optional(T x){return x==null?List.of():List.of(x);}static String opKey(String actor,String name,String key){return actor+"/"+name+"/"+key;}
 PermissionProtocol.GrantRow grant(String action,String purpose)throws Exception {var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of(purpose),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null));return new PermissionProtocol.GrantRow(id(8),actor,null,"ENTERPRISE",id(1),action,json.canonicalObject(condition),"ACTIVE",now.minusDays(1),now.plusDays(1),id(9),"1.0.0",json.manifestSha256(),BigInteger.ONE);}
 ObjectNode capability()throws Exception {var n=json.object().put("schema_version","1.0.0").put("execution_mode","PRODUCTION").putNull("project_id").put("kind","TOOL").put("reason","Fixed real package metadata");n.set("source_artifact",json.tree(new PermissionProtocol.Basis(id(5),H)));byte[] bytes;try(var in=getClass().getResourceAsStream("/agent-release.schema.json")){bytes=in.readAllBytes();}var binding=json.object().put("schema_ref","urn:medical:agent-release:1.0.0").put("schema_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes));n.set("input_schema_ref",binding);n.set("output_schema_ref",binding.deepCopy());var role=json.object().put("schema_version","1.0.0");var permission=json.object().put("action","configuration.read").put("purpose","AGENT_PUBLICATION");permission.set("scope",json.tree(new PermissionProtocol.Scope(id(1),null,"ENTERPRISE",id(1))));role.putArray("permissions").add(permission);n.set("tool_scope",role);return n;}
 ObjectNode policy() {var n=json.object().put("schema_version","1.0.0").putNull("project_id").put("runtime_manifest_sha256",H).put("scope_sha256",H).put("familywise_confidence","0.95").put("reason","Actual caller specified thresholds");for(String f:List.of("rule_revision","dataset","sample_manifest_artifact","sampling_design_evidence"))n.set(f,json.tree(new PermissionProtocol.Basis(id(5),H)));n.putArray("candidate_refs").add(json.tree(new PermissionProtocol.Basis(id(6),H)));n.putArray("dimensions").add(json.object().put("dimension","EVAL-01").put("method","SOURCE_FAMILY_HOEFFDING_V1").put("direction","MIN").put("threshold","0.8").put("repeat_count",2).put("minimum_families",20));return n;}
 ObjectNode decision(JsonNode draft,String choice){return json.object().put("schema_version","1.0.0").put("draft_id",draft.path("resource_id").asText()).put("draft_sha256",draft.path("content_sha256").asText()).put("decision",choice).put("reason","Independent approved policy input");}
 @Test void actualFormalAuthorReadbackAndOriginalKeyReplay()throws Exception{var request=capability();var receipt=capabilities.author(a,"formal-capability-001",request);var row=records.get(receipt.path("resource_id").asText());var proof=identities.require(a,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"TOOL",null,"MODEL_VERIFICATION");assertEquals("PRODUCTION",proof.canonicalDefinition().path("request").path("execution_mode").asText());assertEquals(receipt,capabilities.author(a,"formal-capability-001",request));assertEquals(1,caps.size());verify(resources,atLeastOnce()).require(eq(a),any(),eq("capability.configure"),eq("AGENT_PUBLICATION"));}
 @Test void simulationRequestCannotBecomeFormalIdentity()throws Exception{var request=capability();request.put("execution_mode","SIMULATION");assertThrows(SchemaRejection.class,()->capabilities.author(a,"formal-capability-001",request));assertTrue(caps.isEmpty());}
 @Test void kindSubstitutionDenied()throws Exception{var receipt=capabilities.author(a,"formal-capability-001",capability());var row=records.get(receipt.path("resource_id").asText());assertThrows(TenantAccessException.class,()->identities.require(a,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"SKILL",null,"MODEL_VERIFICATION"));}
 @Test void missingAuthorSidecarDenied()throws Exception{var receipt=capabilities.author(a,"formal-capability-001",capability());var row=records.remove(receipt.path("resource_id").asText());assertThrows(TenantAccessException.class,()->identities.require(a,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"TOOL",null,"MODEL_VERIFICATION"));}
 @Test void changedRequestSameKeyDenied()throws Exception{var request=capability();capabilities.author(a,"formal-capability-001",request);request.put("reason","altered reason");assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT,assertThrows(TenantAccessException.class,()->capabilities.author(a,"formal-capability-001",request)).code());}
 @Test void revokedAuthorityCannotReplayWrite()throws Exception{var request=capability();capabilities.author(a,"formal-capability-001",request);revoked=true;assertThrows(TenantAccessException.class,()->capabilities.author(a,"formal-capability-001",request));}






 void packageMetadata()throws Exception {
  var ref=new InterfaceProtocol.ObjectRef("tenants/"+id(1)+"/capability-package","version-1",H,"12","application/json");
  versions.put(id(5),new ArtifactRegistryProtocol.VersionRow(id(5),null,"ARTIFACT",H));
  when(m.packageArtifact(anyMap())).thenReturn(List.of(new ArtifactRegistryProtocol.ArtifactRow(id(1),null,id(5),"PACKAGE",StoredObjectReferences.encode(ref),ref.objectKey(),ref.objectVersion(),H,BigInteger.valueOf(12),ref.mediaType(),id(16),H,"1.0.0",H,"INTERNAL")));
  when(m.packageBinding(anyMap())).thenReturn(List.of(new ArtifactRegistryProtocol.BindingRow(id(1),null,id(5),"SOURCE",id(17),"urn:medical:agent-release:1.0.0",H,id(16),H)));
 }
 interface Read<T>{T get()throws Exception;}
 <T>T scoped(Read<T> call)throws Exception {org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);try(var scope=TenantScope.open(new TenantScope.Identity(id(1),actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))){scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));try{return call.get();}finally{scope.finishTransaction();}}finally{org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}}
 @Test void metadataClaimProofIsDifferentAndDoesNotCreateAdmission()throws Exception{var receipt=capabilities.author(a,"formal-capability-001",capability());var row=records.get(receipt.path("resource_id").asText());packageMetadata();clearInvocations(a,resources,deps);var identity=scoped(()->identities.requireRegisteredIdentity(id(1),actor,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"TOOL",null));assertEquals("TOOL",identity.kind());assertNotEquals(FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability.class,identity.getClass());verifyNoInteractions(a,resources,deps);}
 @Test void metadataClaimCannotCrossCurrentActor()throws Exception{var receipt=capabilities.author(a,"formal-capability-001",capability());var row=records.get(receipt.path("resource_id").asText());packageMetadata();assertThrows(TenantAccessException.class,()->scoped(()->identities.requireRegisteredIdentity(id(1),id(88),new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"TOOL",null)));}
 @Test void metadataClaimRejectsMissingRealArtifact()throws Exception{var receipt=capabilities.author(a,"formal-capability-001",capability());var row=records.get(receipt.path("resource_id").asText());packageMetadata();when(m.packageArtifact(anyMap())).thenReturn(List.of());assertThrows(TenantAccessException.class,()->scoped(()->identities.requireRegisteredIdentity(id(1),actor,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"TOOL",null)));}
 @Test void sourcePackageGovernancePurposeDoesNotBecomeConsumerPurpose()throws Exception{var receipt=capabilities.author(a,"formal-capability-001",capability());var row=records.get(receipt.path("resource_id").asText());clearInvocations(resources,deps);identities.require(a,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),"TOOL",null,"MODEL_VERIFICATION");verify(resources).require(eq(a),any(),eq("configuration.read"),eq("MODEL_VERIFICATION"));verify(deps).require(eq(a),isNull(),anyList(),eq("AGENT_PUBLICATION"));verify(deps,never()).require(eq(a),isNull(),anyList(),eq("MODEL_VERIFICATION"));}



 @Test void roleRequiresActualNonemptyRuntimePermissions()throws Exception{var request=capability();request.putArray("tool_scope");assertThrows(SchemaRejection.class,()->capabilities.author(a,"role-empty-001-test-key",request));assertTrue(records.isEmpty());}
 @Test void roleScopeRequiresActualActorGrantBeforePersistence()throws Exception{var request=capability();when(resources.require(eq(a),any(),eq("configuration.read"),eq("AGENT_PUBLICATION"))).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,()->capabilities.author(a,"role-denied-001-test-key",request));assertTrue(records.isEmpty());}
 @Test void roleCannotCaptureCrossTenantPermission()throws Exception{var request=capability();((ObjectNode)request.path("tool_scope").path("permissions").get(0).path("scope")).put("tenant_id",id(99));assertThrows(TenantAccessException.class,()->capabilities.author(a,"role-cross-001-test-key",request));assertTrue(records.isEmpty());}
 @Test void skillUsesTheSameRealPermissionWitnessBoundary()throws Exception{var request=capability();request.put("kind","SKILL");assertNotNull(capabilities.author(a,"skill-real-001-test-key",request));request.putArray("tool_scope");assertThrows(SchemaRejection.class,()->capabilities.author(a,"skill-empty-001-test-key",request));}



 void checkSubject(String method,Object[] args)throws Exception{
  if(args==null||args.length!=1||!(args[0] instanceof Map<?,?>))throw new AssertionError("Expected mapper values");
  TenantScope existing;try{existing=TenantScope.requireCurrent();}catch(TenantAccessException absent){existing=null;}
  String statement="com.ruoyi.medical.persistence.mapper.AgentPublicationMapper."+method;
  if(existing==null)scoped(()->{TenantScope.requireSubjectParameter(statement,args[0]);return null;});else TenantScope.requireSubjectParameter(statement,args[0]);
 }
 @Test void realMapperGuardRejectsMissingCurrentActorAndCreatorSubstitution()throws Exception{scoped(()->{assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.AgentPublicationMapper.journalByKey",Map.of("creatorId",actor)));assertThrows(TenantAccessException.class,()->TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.AgentPublicationMapper.journalByKey",Map.of("userId",id(99),"creatorId",actor)));return null;});}

}
