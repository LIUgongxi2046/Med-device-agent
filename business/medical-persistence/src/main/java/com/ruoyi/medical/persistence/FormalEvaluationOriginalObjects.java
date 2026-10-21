package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;

/** Concrete bounded original-object GET and existing content-read receipts. No supplied bytes or booleans. */
public final class FormalEvaluationOriginalObjects {
 private final EnterpriseAccess access;private final ArtifactContentService content;private final SourceUploadTransport transport;
 private final ArtifactRegistryMapper artifacts;private final SourceReadMapper sources;private final DependencyLineage lineage;
 private final EvaluationReferences references;private final EvaluationDependencyPolicy policy;
 private final int maximumBytes;
 public FormalEvaluationOriginalObjects(EnterpriseAccess access,ArtifactContentService content,SourceUploadTransport transport,
   ArtifactRegistryMapper artifacts,SourceReadMapper sources,DependencyLineage lineage,EvaluationReferences references,
   EvaluationDependencyPolicy policy,int maximumBytes){this.access=access;this.content=content;this.transport=transport;this.artifacts=artifacts;this.sources=sources;this.lineage=lineage;this.references=references;this.policy=policy;if(maximumBytes<1||maximumBytes>16777216)throw new IllegalArgumentException("EVALUATION_OBJECT_LIMIT_INVALID");this.maximumBytes=maximumBytes;}
 Verified read(ApplicationSessionIdentity.Verified actor,String context,String project,Artifact expected)throws Exception {return read(EvidenceReadAccess.human(access,actor),context,project,expected);}
 Verified read(EvidenceReadAccess actor,String context,String project,Artifact expected)throws Exception {
  actor.requireVersion(expected.artifactId(),expected.sha256());
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  var intent=actor.execute(a->ThrowingSupplier.of(()->intent(a,context,project,expected)).get());
  S3ObjectReader.Admission guard=()->actor.execute(a->ThrowingSupplier.of(()->{if(!intent.equals(intent(a,context,project,expected)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);return null;}).get());
  String tenant=actor.execute(a->a.context().tenant_id());
  var read=actor.readDocument(transport,tenant,maximumBytes,guard,(reader,current)->content.readVerifiedComplete(actor,intent,reader,current,maximumBytes,UUID.randomUUID().toString()));
  var verified=new Verified(expected,read);verified.requireCurrent();return verified;
 }
 private ContentReadProtocol.ReadIntent intent(EnterpriseAccess.Admission a,String context,String project,Artifact expected)throws Exception {
  if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  policy.requireConfigured();references.artifact(project,new PermissionProtocol.Basis(expected.artifactId(),expected.sha256()),expected.schemaRef());
  var row=one(artifacts.artifact(expected.artifactId()));if(!expected.objectVersion().equals(row.objectVersion())||!expected.sha256().equals(row.sha256())||!a.context().tenant_id().equals(row.tenantId()))bad();
  var graph=lineage.inspect(expected.artifactId(),policy.limits());var requirements=new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();var sourceRequirements=new ArrayList<SourceReadProtocol.ReadRequirement>();
  for(var version:graph.versions().values()){
   if(version.projectId()!=null&&!version.projectId().equals(project))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
   var source=sources.source(version.id());if(source.size()>1)bad();if(!source.isEmpty()){var s=source.get(0);sourceRequirements.add(new SourceReadProtocol.ReadRequirement(s.sourceVersion(),s.rightsRevision(),project,null,null,null,SourceReadProtocol.Use.REVIEW,"EVALUATION_RESULTS",policy.environment(),policy.region(),null));}
   if("ARTIFACT".equals(version.entityKind())){var binding=one(artifacts.binding(version.id()));var profile=references.profile(binding.schemaRef(),"EVALUATION_RESULTS",binding.resourceKind());requirements.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(a.context().tenant_id(),binding.projectId(),profile.resourceKind(),binding.resourceId()),profile.action(),"EVALUATION_RESULTS",profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),policy.limits()));}
  }
  var root=requirements.remove(expected.artifactId());if(root==null)bad();sourceRequirements.sort(java.util.Comparator.comparing(SourceReadProtocol.ReadRequirement::sourceVersion));
  return new ContentReadProtocol.ReadIntent(expected.artifactId(),context,root,sourceRequirements,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),requirements);
 }
 static final class Verified {
  private final Artifact expected;private final ArtifactContentService.CompleteRead read;
  private Verified(Artifact expected,ArtifactContentService.CompleteRead read){this.expected=expected;this.read=read;}
  Artifact reference(){return expected;}
  void requireCurrent(){read.consume(bytes->{if(!expected.sha256().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))bad();});if(read.receiptIds().isEmpty())bad();}
  byte[] bytes(){var value=new java.util.concurrent.atomic.AtomicReference<byte[]>();read.consume(bytes->{if(!expected.sha256().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))bad();value.set(bytes);});return value.get();}
  List<String> dependencyVersions(){return read.dependencyVersions();}
  List<String> receiptIds(){requireCurrent();return List.copyOf(read.receiptIds());}
 }
 private static <T>T one(List<T> values){if(values.size()!=1)bad();return values.get(0);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
