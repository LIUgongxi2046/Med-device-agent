package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;import java.math.BigInteger;import org.springframework.util.function.ThrowingSupplier;
/** Real whole ExactInput object reads with full fixed snapshot MODEL_SEND source authorization.
 * The published ExactInput data format is shared; no simulation fixture/provenance is accepted. */
public final class FormalInvocationInputs {
 private final EnterpriseAccess access;private final DocumentReadMapper snapshots;private final ScopeSnapshotSchema snapshotSchema;private final ArtifactRegistryMapper registry;private final ArtifactSourceAuthority authority;private final DependencyLineage lineage;private final SourceUploadTransport transport;private final SchemaCatalogue schemas;private final PermissionSchema json;private final DocumentUploadPolicy policy;
 public FormalInvocationInputs(EnterpriseAccess access,DocumentReadMapper snapshots,ScopeSnapshotSchema snapshotSchema,ArtifactRegistryMapper registry,ArtifactSourceAuthority authority,DependencyLineage lineage,SourceUploadTransport transport,PermissionSchema json,DocumentUploadPolicy policy)throws Exception{this.access=access;this.snapshots=snapshots;this.snapshotSchema=snapshotSchema;this.registry=registry;this.authority=authority;this.lineage=lineage;this.transport=transport;this.json=json;this.policy=policy;schemas=SchemaCatalogue.fromClasspath();}
 private record State(ContentReadProtocol.ReadIntent intent,ArtifactSourceAuthority.VerifiedSources sources,String digest,TaskProtocol.ScopeSnapshotManifest snapshot){}
 public final class Prepared {
  private final JsonNode request;private final String provider,region;private final State state;private final byte[] content;
  private Prepared(JsonNode request,String provider,String region,State state,byte[] content){this.request=request.deepCopy();this.provider=provider;this.region=region;this.state=state;this.content=content.clone();}
  public byte[] bytes(){return content.clone();}public String inputSha256(){return state.sources().registration().artifact().sha256();}public String authorizationSha256(){return state.digest();}public String classification(){return state.sources().registration().artifact().classification();}
  public ArtifactSourceAuthority.VerifiedSources registered(){return state.sources();}public TaskProtocol.ScopeSnapshotManifest snapshot(){return state.snapshot();}
  public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{if(!state.digest().equals(state(a,request,provider,region).digest()))fail(ErrorCode.SCOPE_STALE);}
 }
 public Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,JsonNode request,String provider,String region)throws Exception{
  State initial=tx(subject,a->{if(!context.equals(a.context().context_revision()))fail(ErrorCode.CONTEXT_CHANGED);return state(a,request,provider,region);});String tenant=initial.sources().registration().artifact().tenantId();
  S3ObjectReader.Admission current=()->tx(subject,a->{if(!context.equals(a.context().context_revision())||!initial.digest().equals(state(a,request,provider,region).digest()))fail(ErrorCode.SCOPE_STALE);return null;});
  var reference=initial.sources().registration().artifact().reference();int size=Integer.parseInt(reference.sizeBytes());
  byte[] bytes=transport.readSourceJob(tenant,subject.user().user_id(),policy.maximumBytes().intValueExact(),current,(reader,guard)->{try(var read=reader.readVerified(reference,guard)){if(!reference.equals(read.reference())||!reference.sha256().equals(read.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);byte[] b=new byte[size];for(int start=0;start<size;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);System.arraycopy(read.chunk(start,n),0,b,start,n);start+=n;}guard.requireCurrent();return b;}});
  var payload=schemas.bindPublished(initial.intent().artifact().schemaRef(),initial.intent().artifact().schemaDocumentSha256(),bytes);
  if(!reference.sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);current.requireCurrent();return new Prepared(request,provider,region,initial,bytes);
 }
 public Prepared prepareWorker(FormalWorkerAccess workers,jakarta.servlet.http.HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,JsonNode request,String provider,String region)throws Exception{
  State initial=workers.execute(http,token,action,a->ThrowingSupplier.of(()->state(a,request,provider,region)).get());
  String actor=workers.execute(http,token,action,EnterpriseAccess.Admission::actorId);String tenant=initial.sources().registration().artifact().tenantId();
  S3ObjectReader.Admission current=()->{try{workers.execute(http,token,action,a->ThrowingSupplier.of(()->{if(!initial.digest().equals(state(a,request,provider,region).digest()))fail(ErrorCode.SCOPE_STALE);return null;}).get());}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Worker input authorization unavailable",e);}};
  var reference=initial.sources().registration().artifact().reference();int size=Integer.parseInt(reference.sizeBytes());
  byte[] bytes=transport.readSourceJob(tenant,actor,policy.maximumBytes().intValueExact(),current,(reader,guard)->{try(var read=reader.readVerified(reference,guard)){if(!reference.equals(read.reference())||!reference.sha256().equals(read.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);byte[] b=new byte[size];for(int start=0;start<size;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);System.arraycopy(read.chunk(start,n),0,b,start,n);start+=n;}guard.requireCurrent();return b;}});
  schemas.bindPublished(initial.intent().artifact().schemaRef(),initial.intent().artifact().schemaDocumentSha256(),bytes);if(!reference.sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);current.requireCurrent();return new Prepared(request,provider,region,initial,bytes);
 }
 ArtifactSourceAuthority.VerifiedSources requireCurrentReferences(EnterpriseAccess.Admission a,JsonNode request,String provider,String region,SourceReadProtocol.Use use)throws Exception{return state(a,request,provider,region,use).sources();}
 private State state(EnterpriseAccess.Admission a,JsonNode request,String provider,String region)throws Exception{return state(a,request,provider,region,SourceReadProtocol.Use.MODEL_SEND);}
 private State state(EnterpriseAccess.Admission a,JsonNode request,String provider,String region,SourceReadProtocol.Use use)throws Exception{
  policy.requireConfigured();String tenant=a.context().tenant_id(),project=request.required("project_id").textValue(),purpose=request.required("business_purpose").textValue();if(!Set.of("DOCUMENT_BODY","MODEL_VERIFICATION").contains(purpose))fail(ErrorCode.SCHEMA_INVALID);if(project==null)fail(ErrorCode.MODEL_POLICY_INCOMPLETE);
  JsonNode fixed=request.required("scope_snapshot_basis");String snapshotId=fixed.required("version_id").textValue();var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",project);values.put("snapshotId",snapshotId);values.put("metadataLimit",(long)policy.maximumSources()+1);values.put("maximumMetadataBytes",policy.maximumBytes());
  BigInteger snapshotBytes=one(snapshots.metadataSnapshotBytes(values));if(snapshotBytes.signum()<=0||snapshotBytes.compareTo(policy.maximumBytes())>0)fail(ErrorCode.CONTEXT_LIMIT);
  var snapshot=one(snapshots.metadataSnapshot(values));long count=Objects.requireNonNull(snapshots.metadataSourceCount(values));if(count<0||count>policy.maximumSources())fail(ErrorCode.CONTEXT_LIMIT);var members=snapshots.metadataMembers(values);if(members.size()!=count||members.size()>policy.maximumSources())fail(ErrorCode.CONTENT_MISMATCH);
  if(!fixed.required("content_sha256").textValue().equals(snapshot.manifestHash())||!snapshot.manifestHash().equals(snapshot.registryHash()))fail(ErrorCode.CONTENT_MISMATCH);
  JsonNode snapshotDocument=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(snapshot.manifest());String profile=snapshotDocument.required("profile_revision").textValue();
  var manifest=snapshotSchema.require(snapshot.manifest(),snapshot.manifestHash(),tenant,project,snapshotId,profile,snapshot.authEpoch(),snapshot.governanceEpoch(),null,members);
  JsonNode input=request.required("exact_input");String artifactId=input.required("artifact_id").textValue();var artifact=one(registry.artifact(artifactId));var binding=one(registry.binding(artifactId));
  if(!project.equals(artifact.projectId())||!input.required("sha256").textValue().equals(artifact.sha256())||!"EXACT_INPUT".equals(artifact.artifactKind())||!"SOURCE".equals(binding.resourceKind()))fail(ErrorCode.CONTENT_MISMATCH);
  String ref=schemas.schemaRef("simulation-execution","ExactInput"),schemaHash=schemas.schemaDigest("simulation-execution");if(!ref.equals(input.required("schema_ref").textValue()))fail(ErrorCode.CONTENT_MISMATCH);
  var required=new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant,project,"SOURCE",binding.resourceId()),"source.read",purpose,ref,schemaHash,policy.artifactSchemaManifest(),policy.limits());
  var graph=lineage.inspect(artifactId,policy.limits());List<SourceReadProtocol.ReadRequirement> sourceRequirements=new ArrayList<>();
  for(var member:manifest.members())if(graph.versions().containsKey(member.source_version()))sourceRequirements.add(new SourceReadProtocol.ReadRequirement(member.source_version(),member.rights_revision(),project,snapshotId,member.parse_version(),null,use,purpose,policy.environment(),region,use==SourceReadProtocol.Use.MODEL_SEND?provider:null));
  // This first input profile supports a source-only ancestry; any upstream artifact needs its own explicitly supported profile.
  for(var version:graph.versions().values())if(!version.id().equals(artifactId)&&"ARTIFACT".equals(version.entityKind()))fail(ErrorCode.MODEL_POLICY_INCOMPLETE);
  var context=new VersionResourceProtocol.ReadContext(use,null);var verified=authority.require(a,artifactId,required,sourceRequirements,context,Map.of());
  var actual=verified.registration().artifact();var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int level=levels.indexOf(actual.classification());if(level<0)fail(ErrorCode.CONTENT_MISMATCH);for(var source:verified.sources().values())if(levels.indexOf(source.source().classification())<0||levels.indexOf(source.source().classification())>level)fail(ErrorCode.CONTENT_MISMATCH);if(actual.sizeBytes().signum()<=0||actual.sizeBytes().compareTo(policy.maximumBytes())>0)fail(ErrorCode.CONTEXT_LIMIT);
  requireCompleteCoverage(input.required("coverage"),actual.reference());
  var intent=new ContentReadProtocol.ReadIntent(artifactId,a.context().context_revision(),required,sourceRequirements,context,Map.of());return new State(intent,verified,ContentReadAuthorization.digest(a,intent,verified),manifest);
 }
 /** Exact frozen local Coverage names; the unrelated generic Coverage DTO uses different wire fields. */
 static void requireCompleteCoverage(JsonNode coverage,InterfaceProtocol.ObjectRef reference)throws Exception {
  String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(reference),0,new BigInteger(reference.sizeBytes()).longValueExact());
  var expected=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("selection_digest",range).put("delivered_digest",range).put("complete",true).putNull("next_cursor");
  if(!expected.equals(coverage))fail(ErrorCode.CONTENT_MISMATCH);
 }
 @FunctionalInterface interface Work<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}private <T>T tx(ApplicationSessionIdentity.Verified s,Work<T> work){return access.execute(s,a->ThrowingSupplier.of(()->work.apply(a)).get());}
 private static <T>T one(List<T> rows){if(rows.size()!=1||rows.get(0)==null)fail(ErrorCode.CONTENT_MISMATCH);return rows.get(0);}private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
