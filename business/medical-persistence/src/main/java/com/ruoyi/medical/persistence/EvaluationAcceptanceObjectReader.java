package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Actual bounded object GET outside a transaction, with current immutable input/read proof on both sides.
 * It produces no statistical approval, source permission, or production eligibility. */
@Component public final class EvaluationAcceptanceObjectReader {
 private final EnterpriseAccess access;private final SourceUploadTransport transport;private final SourceReadAuthority sources;
 private final SourceReadMapper sourceRows;private final SimulationFixtureMapper originals;private final ArtifactRegistryMapper artifacts;
 private final AgentPublicationDependencies dependencies;private final EvaluationReferences references;private final EvaluationDependencyPolicy limits;
 private final AgentPublicationSchema json;
 private final com.fasterxml.jackson.databind.json.JsonMapper byteReader=com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 public EvaluationAcceptanceObjectReader(EnterpriseAccess access,SourceUploadTransport transport,SourceReadAuthority sources,
  SourceReadMapper sourceRows,SimulationFixtureMapper originals,ArtifactRegistryMapper artifacts,AgentPublicationDependencies dependencies,
  EvaluationReferences references,EvaluationDependencyPolicy limits,AgentPublicationSchema json){
  this.access=access;this.transport=transport;this.sources=sources;this.sourceRows=sourceRows;this.originals=originals;this.artifacts=artifacts;
  this.dependencies=dependencies;this.references=references;this.limits=limits;this.json=json;
 }
 private enum Kind{SOURCE,ARTIFACT}
 private record Request(Kind kind,String project,PermissionProtocol.Basis basis,String purpose,String schemaRef,List<AgentPublicationDependencies.TypedUse> children){private Request{children=List.copyOf(children);}}
 private record State(String actor,String tenant,String context,InterfaceProtocol.ObjectRef object,String identity){}
 @FunctionalInterface private interface Recheck{void require(EnterpriseAccess.Admission admission)throws IOException;}
 public static final class VerifiedDocument {
  private final PermissionProtocol.Basis basis;private final InterfaceProtocol.ObjectRef object;private final JsonNode document;private final String canonical;private final Recheck recheck;
  private VerifiedDocument(PermissionProtocol.Basis basis,State state,JsonNode document,String canonical,Recheck recheck){this.basis=basis;object=state.object();this.document=document.deepCopy();this.canonical=canonical;this.recheck=recheck;}
  public PermissionProtocol.Basis basis(){return basis;}public InterfaceProtocol.ObjectRef object(){return object;}public JsonNode document(){return document.deepCopy();}public String canonical(){return canonical;}
  public void requireCurrent(EnterpriseAccess.Admission admission)throws IOException{recheck.require(admission);}
 }
 public VerifiedDocument source(ApplicationSessionIdentity.Verified subject,String context,String project,PermissionProtocol.Basis ref,String purpose)throws Exception{
  return read(subject,context,new Request(Kind.SOURCE,project,ref,purpose,null,List.of()));
 }
 /** The caller must derive child roles from previously verified fixed dataset/expected-item content. */
 public VerifiedDocument artifact(ApplicationSessionIdentity.Verified subject,String context,String project,PermissionProtocol.Basis ref,
   String purpose,String requiredSchema,List<AgentPublicationDependencies.TypedUse> children)throws Exception{
  if(!Set.of(EvaluationSchema.SAMPLE_MANIFEST_REF,EvaluationSchema.EXPECTED_ITEMS_REF).contains(requiredSchema))bad();
  return read(subject,context,new Request(Kind.ARTIFACT,project,ref,purpose,requiredSchema,children));
 }
 private VerifiedDocument read(ApplicationSessionIdentity.Verified subject,String context,Request request)throws Exception{
  DocumentUploadTransferService.requireOutsideTransaction(subject);
  State initial=state(subject,context,request);S3ObjectReader.Admission guard=()->same(initial,state(subject,context,request));
  byte[] bytes=transport.readDocument(subject,initial.tenant(),EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES,guard,(reader,current)->{
   try(var fixed=reader.readVerified(initial.object(),current)){
    if(!initial.object().equals(fixed.reference())||!request.basis().content_sha256().equals(fixed.fullSha256()))bad();
    int size=Integer.parseInt(initial.object().sizeBytes());byte[] result=new byte[size];
    for(int start=0;start<size;){int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);System.arraycopy(fixed.chunk(start,count),0,result,start,count);start+=count;}
    current.requireCurrent();return result;
   }
  });
  if(!org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes).equals(request.basis().content_sha256()))bad();
  String utf8=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
  var document=byteReader.readTree(utf8);if(document==null||!document.isContainerNode())bad();String canonical=json.canonical(document);
  if(request.kind()==Kind.ARTIFACT&&!Arrays.equals(bytes,canonical.getBytes(StandardCharsets.UTF_8)))bad();same(initial,state(subject,context,request));
  return new VerifiedDocument(request.basis(),initial,document,canonical,a->same(initial,state(a,context,request)));
 }
 private State state(ApplicationSessionIdentity.Verified subject,String context,Request request){return access.execute(subject,a->ThrowingSupplier.of(()->state(a,context,request)).get());}
 private State state(EnterpriseAccess.Admission a,String context,Request request)throws IOException{
  if(a.simulationWorker()||!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  WireValues.uuid(request.basis().version_id());WireValues.hash(request.basis().content_sha256());limits.requireConfigured();
  InterfaceProtocol.ObjectRef object;Object metadata;AgentPublicationDependencies.Checked closure;
  if(request.kind()==Kind.SOURCE){
   var version=one(artifacts.version(request.basis().version_id()));if(!"SOURCE_VERSION".equals(version.entityKind())||!request.basis().content_sha256().equals(version.contentSha256()))bad();
   var source=one(sourceRows.source(request.basis().version_id()));if(!source.originalSha256().equals(request.basis().content_sha256())||!source.registeredSha256().equals(request.basis().content_sha256()))bad();
   var proof=sources.require(a,new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),request.project(),null,null,null,SourceReadProtocol.Use.REVIEW,request.purpose(),limits.environment(),limits.region(),null));
   var gates=sourceRows.gates(source.sourceVersion());if(gates.isEmpty())throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
   var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("sourceVersion",source.sourceVersion());var original=one(originals.original(values));
   if(!a.context().tenant_id().equals(original.tenantId())||!source.sourceVersion().equals(original.sourceVersion())||!source.originalSha256().equals(original.sha256()))bad();
   object=original.reference();closure=dependencies.requireByUse(a,request.project(),List.of(new AgentPublicationDependencies.DependencyUse(request.basis(),request.purpose())));
   metadata=Map.of("source",AgentPublicationWitness.tree(proof),"gates",AgentPublicationWitness.tree(gates),"original",AgentPublicationWitness.tree(original));
  }else{
   references.artifact(request.project(),request.basis(),request.schemaRef());var artifact=one(artifacts.artifact(request.basis().version_id()));var binding=one(artifacts.binding(request.basis().version_id()));
   String kind=EvaluationSchema.SAMPLE_MANIFEST_REF.equals(request.schemaRef())?"SAMPLE_MANIFEST":"EXPECTED_ITEMS";
   if(!kind.equals(artifact.artifactKind())||!"SOURCE".equals(binding.resourceKind()))bad();object=artifact.reference();
   closure=request.children().isEmpty()?dependencies.requireByUse(a,request.project(),List.of(new AgentPublicationDependencies.DependencyUse(request.basis(),request.purpose()))):dependencies.requireTypedTree(a,request.project(),new AgentPublicationDependencies.TypedUse(request.basis(),request.purpose(),request.children(),true));
   metadata=Map.of("artifact",AgentPublicationWitness.tree(artifact),"binding",AgentPublicationWitness.tree(binding));
  }
  if(!object.sha256().equals(request.basis().content_sha256())||!Set.of("application/json","text/plain").contains(object.mediaType())||!object.objectKey().startsWith("tenants/"+a.context().tenant_id()+"/")||object.objectKey().contains(".."))bad();
  var size=new java.math.BigInteger(object.sizeBytes());if(size.signum()<=0||size.compareTo(java.math.BigInteger.valueOf(EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES))>0)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
  var snapshot=Map.of("context",a.context(),"watermarks",a.watermarks(),"actor",a.actorId(),"object",object,"metadata",metadata,"closure",closure.authorizations());
  return new State(a.actorId(),a.context().tenant_id(),context,object,json.hash(snapshot));
 }
 private static void same(State expected,State actual){if(!expected.equals(actual))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
