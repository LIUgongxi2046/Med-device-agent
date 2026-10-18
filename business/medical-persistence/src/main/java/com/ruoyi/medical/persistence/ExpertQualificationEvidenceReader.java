package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;
/** Bounded original credential retrieval. Bytes establish identity; only a named human decides their meaning. */
@Component @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.expert-qualification",name="enabled",havingValue="true") public final class ExpertQualificationEvidenceReader {
 public static final int MAXIMUM_BYTES=16*1024*1024,MAXIMUM_TOTAL_BYTES=32*1024*1024;
 private final EnterpriseAccess access;private final SourceUploadTransport transport;private final ExpertQualificationMapper mapper;
 private final SourceReadAuthority sources;private final SourceReadMapper sourceRows;private final ArtifactRegistryMapper artifacts;
 private final AgentPublicationDependencies dependencies;private final EvaluationReferences references;private final EvaluationDependencyPolicy limits;
 private final ExpertQualificationSchema schema;private final SchemaCatalogue catalogue;
 public ExpertQualificationEvidenceReader(EnterpriseAccess access,SourceUploadTransport transport,ExpertQualificationMapper mapper,SourceReadAuthority sources,
  SourceReadMapper sourceRows,ArtifactRegistryMapper artifacts,AgentPublicationDependencies dependencies,EvaluationReferences references,EvaluationDependencyPolicy limits,ExpertQualificationSchema schema)throws Exception{
  this.access=access;this.transport=transport;this.mapper=mapper;this.sources=sources;this.sourceRows=sourceRows;this.artifacts=artifacts;this.dependencies=dependencies;this.references=references;this.limits=limits;this.schema=schema;catalogue=SchemaCatalogue.fromClasspath();
 }
 record Current(String tenantId,JsonNode witness,String digest,String classification,List<InterfaceProtocol.ObjectRef> objects,List<String> schemaRefs,List<String> schemaHashes){Current{witness=witness.deepCopy();objects=List.copyOf(objects);schemaRefs=List.copyOf(schemaRefs);schemaHashes=List.copyOf(schemaHashes);}@Override public JsonNode witness(){return witness.deepCopy();}}
 @FunctionalInterface private interface Recheck{Current read(EnterpriseAccess.Admission a)throws IOException;}
 public static final class VerifiedOriginals{
  private final Current current;private final Recheck check;private VerifiedOriginals(Current current,Recheck check){this.current=current;this.check=check;}
  public void requireCurrent(EnterpriseAccess.Admission a)throws IOException{same(current.digest(),check.read(a).digest());}
  JsonNode witness(){return current.witness();}String classification(){return current.classification();}
 }
 public VerifiedOriginals read(ApplicationSessionIdentity.Verified subject,String context,Author author)throws Exception{
  DocumentUploadTransferService.requireOutsideTransaction(subject);Current initial=state(subject,context,author);
  S3ObjectReader.Admission guard=()->same(initial.digest(),state(subject,context,author).digest());
  for(int i=0;i<initial.objects().size();i++){
   var object=initial.objects().get(i);int index=i;
   byte[] bytes=transport.readDocument(subject,initial.tenantId(),MAXIMUM_BYTES,guard,(reader,current)->{
    try(var verified=reader.readVerified(object,current)){
     same(object,verified.reference());same(object.sha256(),verified.fullSha256());byte[] value=new byte[Integer.parseInt(object.sizeBytes())];
     for(int start=0;start<value.length;){int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,value.length-start);System.arraycopy(verified.chunk(start,count),0,value,start,count);start+=count;}current.requireCurrent();return value;
    }
   });
   same(object.sha256(),WireValues.sha256(bytes));if(!initial.schemaRefs().get(index).isEmpty())catalogue.bindPublished(initial.schemaRefs().get(index),initial.schemaHashes().get(index),bytes);
  }
  same(initial.digest(),state(subject,context,author).digest());return new VerifiedOriginals(initial,a->state(a,context,author));
 }
 private Current state(ApplicationSessionIdentity.Verified subject,String context,Author author){return access.execute(subject,a->ThrowingSupplier.of(()->state(a,context,author)).get());}
 Current current(EnterpriseAccess.Admission a,Author author)throws IOException{return state(a,a.context().context_revision(),author);}
 private Current state(EnterpriseAccess.Admission a,String context,Author author)throws IOException{
  ExpertQualificationSchema.semantics(author);if(a.simulationWorker())denied();if(!Objects.equals(context,a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);limits.requireConfigured();
  dependencies.requireByUse(a,author.project_id(),author.evidence().stream().map(e->new AgentPublicationDependencies.DependencyUse(e.basis(),author.purpose())).toList());
  var witness=schema.tree(new TreeMap<String,Object>());var objects=new ArrayList<InterfaceProtocol.ObjectRef>();var refs=new ArrayList<String>();var hashes=new ArrayList<String>();String classification="PUBLIC";long total=0;
  for(var evidence:author.evidence()){
   var root=one(artifacts.version(evidence.basis().version_id()));same(evidence.basis().content_sha256(),root.contentSha256());if(root.projectId()!=null&&!root.projectId().equals(author.project_id()))denied();
   var snapshot=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();InterfaceProtocol.ObjectRef object;String schemaRef="",schemaHash="",localClass;
   if("SOURCE".equals(evidence.kind())){
    same("SOURCE_VERSION",root.entityKind());var source=one(sourceRows.source(root.id()));same(root.contentSha256(),source.originalSha256());
    var proof=sources.require(a,new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),author.project_id(),null,null,null,SourceReadProtocol.Use.REVIEW,author.purpose(),limits.environment(),limits.region(),null));same(author.purpose(),proof.rights().purpose());
    var gates=sourceRows.gates(root.id());if(gates.isEmpty())notReady();for(var gate:gates)if(!"ACTIVE".equals(gate.lifecycle())||!"RECEIVED".equals(gate.uploadStatus())||!"APPROVED".equals(gate.approvalStatus()))notReady();
    var v=ExpertQualificationStore.values(a);v.put("sourceVersion",root.id());var original=one(mapper.original(v));same(a.context().tenant_id(),original.tenantId());same(root.id(),original.sourceVersion());same(root.contentSha256(),original.sha256());object=original.reference();localClass=source.classification();
    snapshot.set("source",schema.tree(proof));snapshot.set("gates",schema.tree(gates));snapshot.set("original",schema.tree(original));
   }else{
    same("ARTIFACT",root.entityKind());references.artifact(author.project_id(),evidence.basis());var artifact=one(artifacts.artifact(root.id()));var binding=one(artifacts.binding(root.id()));
    var profile=references.profile(binding.schemaRef(),author.purpose(),binding.resourceKind());same(profile.schemaDocumentSha256(),binding.schemaDocumentSha256());object=artifact.reference();localClass=artifact.classification();schemaRef=profile.schemaRef();schemaHash=profile.schemaDocumentSha256();catalogue.requirePublished(schemaRef,schemaHash);
    snapshot.set("artifact",schema.tree(artifact));snapshot.set("binding",schema.tree(binding));
   }
   var closure=dependencies.requireByUse(a,author.project_id(),List.of(new AgentPublicationDependencies.DependencyUse(evidence.basis(),author.purpose())));snapshot.set("closure",closure.authorizations());
   if(!object.objectKey().startsWith("tenants/"+a.context().tenant_id()+"/")||object.objectKey().contains(".."))denied();StoredObjectReferences.encode(object);same(root.contentSha256(),object.sha256());
   var size=new java.math.BigInteger(object.sizeBytes());if(size.signum()<=0||size.compareTo(java.math.BigInteger.valueOf(MAXIMUM_BYTES))>0||(total+=size.longValueExact())>MAXIMUM_TOTAL_BYTES)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
   classification=maxClassification(classification,localClass);for(var proof:closure.authorizations())if(proof.path("source").isObject())classification=maxClassification(classification,proof.path("source").path("source").path("classification").asText());
   ((com.fasterxml.jackson.databind.node.ObjectNode)witness).set(root.id(),snapshot);objects.add(object);refs.add(schemaRef);hashes.add(schemaHash);
  }
  var identity=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();identity.set("context",schema.tree(a.context()));identity.set("watermarks",schema.tree(a.watermarks()));identity.put("actor",a.actorId());identity.set("witness",witness);
  return new Current(a.context().tenant_id(),witness,schema.hash(identity),classification,objects,refs,hashes);
 }
 static String maxClassification(String a,String b){var values=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int x=values.indexOf(a),y=values.indexOf(b);if(x<0||y<0)bad();return values.get(Math.max(x,y));}
 private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
 private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void notReady(){throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
