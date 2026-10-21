package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import javax.sql.DataSource;import java.util.*;import java.math.BigInteger;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.util.function.ThrowingSupplier;
/** Bounded internal price/policy SOURCE originals. Never used to authorize model-send bytes. */
public final class FormalInvocationSourceOriginals {
 private final EnterpriseAccess access;private final SourceReadAuthority sources;private final SourceReadMapper sourceRows;private final VersionResourceAuthority versions;private final DependencyLineage lineage;private final SourceUploadTransport transport;private final JdbcTemplate jdbc;private final String environment,region;private final int maximumBytes;private final DependencyLineage.Limits limits;
 public FormalInvocationSourceOriginals(EnterpriseAccess access,SourceReadAuthority sources,SourceReadMapper sourceRows,VersionResourceAuthority versions,DependencyLineage lineage,SourceUploadTransport transport,DataSource data,String environment,String region,int maximumBytes,DependencyLineage.Limits limits){this.access=access;this.sources=sources;this.sourceRows=sourceRows;this.versions=versions;this.lineage=lineage;this.transport=transport;this.jdbc=new JdbcTemplate(data);jdbc.setQueryTimeout(5);this.environment=environment;this.region=region;if(maximumBytes<1)throw new IllegalArgumentException();this.maximumBytes=maximumBytes;this.limits=limits;}
 String requireCurrentBasis(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String purpose,String project)throws Exception{return state(a,basis,purpose,project).digest();}
 private record State(String digest,InterfaceProtocol.ObjectRef object,String classification,List<ArtifactRegistryProtocol.MemberRow> contentMembers){State{contentMembers=List.copyOf(contentMembers);}}
 public final class Read {
  private final PermissionProtocol.Basis basis;private final String purpose,project;private final State state;private final byte[] bytes;
  private Read(PermissionProtocol.Basis basis,String purpose,String project,State state,byte[] bytes){this.basis=basis;this.purpose=purpose;this.project=project;this.state=state;this.bytes=bytes.clone();}
  public byte[] bytes(){return bytes.clone();}public String sourceVersion(){return basis.version_id();}public String contentSha256(){return basis.content_sha256();}public String authorizationSha256(){return state.digest();}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return state.contentMembers();}
  public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{if(!state.digest().equals(state(a,basis,purpose,project).digest()))fail(ErrorCode.SCOPE_STALE);}
 }
 public Read read(ApplicationSessionIdentity.Verified subject,String context,PermissionProtocol.Basis basis,String purpose,String project)throws Exception {
  State initial=tx(subject,a->{if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);return state(a,basis,purpose,project);});
  String tenant=tx(subject,a->a.context().tenant_id());S3ObjectReader.Admission current=()->tx(subject,a->{if(!a.context().context_revision().equals(context)||!initial.digest().equals(state(a,basis,purpose,project).digest()))fail(ErrorCode.SCOPE_STALE);return null;});
  byte[] bytes=transport.readSourceJob(tenant,subject.user().user_id(),maximumBytes,current,(reader,guard)->{
   try(var object=reader.readVerified(initial.object(),guard)){if(!initial.object().equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);int size=Integer.parseInt(initial.object().sizeBytes());byte[] b=new byte[size];for(int start=0;start<size;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);System.arraycopy(object.chunk(start,n),0,b,start,n);start+=n;}guard.requireCurrent();return b;}});
  if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);current.requireCurrent();return new Read(basis,purpose,project,initial,bytes);
 }
 /** Actual fixed source originals under the existing exact TASK token intersection. No host read exception. */
 public Read readWorker(FormalWorkerAccess workers,jakarta.servlet.http.HttpServletRequest http,String token,
        RunTokenAuthority.ActionRequest action,PermissionProtocol.Basis basis,String purpose,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  if(!"MODEL_CONFIGURATION".equals(purpose))fail(ErrorCode.ACCESS_DENIED);
  record Captured(String tenant,String actor,State state){}
  var initial=workers.execute(http,token,action,a->ThrowingSupplier.of(()->{
   if(!"TASK".equals(a.formalProof().identity().executionKind())||!project.equals(a.formalProof().identity().project()))fail(ErrorCode.ACCESS_DENIED);
   return new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,purpose,project));
  }).get());
  S3ObjectReader.Admission current=()->ThrowingSupplier.of(()->workers.execute(http,token,action,a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())
     ||!initial.state().digest().equals(state(a,basis,purpose,project).digest()))fail(ErrorCode.SCOPE_STALE);
   return null;
  }).get())).get();
  var reference=initial.state().object();
  byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,current,(reader,guard)->{
   try(var object=reader.readVerified(reference,guard)){
    if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];
    for(int start=0;start<size;){guard.requireCurrent();int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);
     System.arraycopy(object.chunk(start,length),0,result,start,length);start+=length;guard.requireCurrent();}
    return result;
   }
  });
  if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);
  current.requireCurrent();return new Read(basis,purpose,project,initial.state(),bytes);
 }
 public Read readWorker(TaskProfessionalHostAccess hosts,TaskProfessionalHostAccess.Handle handle,PermissionProtocol.Basis basis,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  record Captured(String tenant,String actor,State state){}
  var initial=hosts.execute(handle,a->ThrowingSupplier.of(()->new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,"MODEL_CONFIGURATION",project))).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())||!initial.state().digest().equals(state(a,basis,"MODEL_CONFIGURATION",project).digest()))fail(ErrorCode.SCOPE_STALE);return null;
  }).get());return null;}).get();
  var reference=initial.state().object();byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,guard,(reader,current)->{
   try(var object=reader.readVerified(reference,current)){if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}return result;}
  });if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);guard.requireCurrent();return new Read(basis,"MODEL_CONFIGURATION",project,initial.state(),bytes);
 }
 public Read readWorker(TaskSectionWriterHostAccess hosts,TaskSectionWriterHostAccess.Handle handle,PermissionProtocol.Basis basis,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  record Captured(String tenant,String actor,State state){}
  var initial=hosts.execute(handle,a->ThrowingSupplier.of(()->new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,"MODEL_CONFIGURATION",project))).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())||!initial.state().digest().equals(state(a,basis,"MODEL_CONFIGURATION",project).digest()))fail(ErrorCode.SCOPE_STALE);return null;
  }).get());return null;}).get();
  var reference=initial.state().object();byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,guard,(reader,current)->{
   try(var object=reader.readVerified(reference,current)){if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}return result;}
  });if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);guard.requireCurrent();return new Read(basis,"MODEL_CONFIGURATION",project,initial.state(),bytes);
 }
 public Read readWorker(TaskSourceVerifierHostAccess hosts,TaskSourceVerifierHostAccess.Handle handle,PermissionProtocol.Basis basis,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  record Captured(String tenant,String actor,State state){}
  var initial=hosts.execute(handle,a->ThrowingSupplier.of(()->new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,"MODEL_CONFIGURATION",project))).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())||!initial.state().digest().equals(state(a,basis,"MODEL_CONFIGURATION",project).digest()))fail(ErrorCode.SCOPE_STALE);return null;
  }).get());return null;}).get();
  var reference=initial.state().object();byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,guard,(reader,current)->{
   try(var object=reader.readVerified(reference,current)){if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}return result;}
  });if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);guard.requireCurrent();return new Read(basis,"MODEL_CONFIGURATION",project,initial.state(),bytes);
 }
 public Read readWorker(DocumentReviewHostAccess hosts,DocumentReviewHostAccess.Handle handle,PermissionProtocol.Basis basis,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  record Captured(String tenant,String actor,State state){}
  var initial=hosts.execute(handle,a->ThrowingSupplier.of(()->new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,"MODEL_CONFIGURATION",project))).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{hosts.execute(handle,a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())||!initial.state().digest().equals(state(a,basis,"MODEL_CONFIGURATION",project).digest()))fail(ErrorCode.SCOPE_STALE);return null;
  }).get());return null;}).get();
  var reference=initial.state().object();byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,guard,(reader,current)->{
   try(var object=reader.readVerified(reference,current)){if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}return result;}
  });if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);guard.requireCurrent();return new Read(basis,"MODEL_CONFIGURATION",project,initial.state(),bytes);
 }
 public Read readControl(TaskPlanDraftControlAccess.Reads reads,PermissionProtocol.Basis basis,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  record Captured(String tenant,String actor,State state){}
  reads.requireVersion(basis.version_id(),basis.content_sha256());
  var initial=reads.execute(a->ThrowingSupplier.of(()->new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,"MODEL_CONFIGURATION",project))).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{reads.execute(a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())||!initial.state().digest().equals(state(a,basis,"MODEL_CONFIGURATION",project).digest()))fail(ErrorCode.SCOPE_STALE);return null;
  }).get());return null;}).get();
  var reference=initial.state().object();byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,guard,(reader,current)->{
   try(var object=reader.readVerified(reference,current)){if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}return result;}
  });if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);guard.requireCurrent();return new Read(basis,"MODEL_CONFIGURATION",project,initial.state(),bytes);
 }
 public Read readControl(FormalEvaluationControlAccess.Reads reads,PermissionProtocol.Basis basis,String project)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("EXTERNAL_IO_IN_TRANSACTION");
  record Captured(String tenant,String actor,State state){}
  // The price is fixed by the independently admitted model; current scoped source grants are still required.
  reads.requireActive();
  var initial=reads.execute(a->ThrowingSupplier.of(()->new Captured(a.context().tenant_id(),a.actorId(),state(a,basis,"MODEL_CONFIGURATION",project))).get());
  S3ObjectReader.Admission guard=()->ThrowingSupplier.of(()->{reads.execute(a->ThrowingSupplier.of(()->{
   if(!initial.tenant().equals(a.context().tenant_id())||!initial.actor().equals(a.actorId())||!initial.state().digest().equals(state(a,basis,"MODEL_CONFIGURATION",project).digest()))fail(ErrorCode.SCOPE_STALE);return null;
  }).get());return null;}).get();
  var reference=initial.state().object();byte[] bytes=transport.readSourceJob(initial.tenant(),initial.actor(),maximumBytes,guard,(reader,current)->{
   try(var object=reader.readVerified(reference,current)){if(!reference.equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);
    int size=Integer.parseInt(reference.sizeBytes());byte[] result=new byte[size];for(int offset=0;offset<size;){current.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);System.arraycopy(object.chunk(offset,count),0,result,offset,count);offset+=count;current.requireCurrent();}return result;}
  });if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);guard.requireCurrent();return new Read(basis,"MODEL_CONFIGURATION",project,initial.state(),bytes);
 }
 private State state(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String purpose,String project)throws Exception {
  String tenant=a.context().tenant_id();var graph=lineage.inspect(basis.version_id(),limits);var root=graph.versions().get(basis.version_id());if(root==null||!root.contentSha256().equals(basis.content_sha256())||root.projectId()!=null&&!Objects.equals(project,root.projectId()))fail(ErrorCode.CONTENT_MISMATCH);
  var verifiedVersions=versions.require(a,graph,purpose,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
  var selected=new TreeMap<String,SourceReadProtocol.VerifiedSource>();
  for(var version:graph.versions().values()) {var rows=sourceRows.source(version.id());if(rows.size()>1)fail(ErrorCode.CONTENT_MISMATCH);if(rows.size()==1){var source=rows.get(0);var verified=sources.require(a,new SourceReadProtocol.ReadRequirement(version.id(),source.rightsRevision(),project,null,null,null,SourceReadProtocol.Use.REVIEW,purpose,environment,region,null));if(!source.originalSha256().equals(version.contentSha256()))fail(ErrorCode.CONTENT_MISMATCH);selected.put(version.id(),verified);}}
  if(!selected.containsKey(basis.version_id()))fail(ErrorCode.CONTENT_MISMATCH);
  var objects=jdbc.query("SELECT object_key,object_version,original_sha256,CAST(size_bytes AS CHAR),media_type FROM source_version WHERE tenant_id=? AND source_version=? LIMIT 2",(r,n)->new InterfaceProtocol.ObjectRef(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),tenant,basis.version_id());
  if(objects.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);var object=objects.get(0);if(!object.sha256().equals(basis.content_sha256())||!object.objectKey().startsWith("tenants/"+tenant+"/")||object.objectKey().contains(".."))fail(ErrorCode.CONTENT_MISMATCH);
  var size=new BigInteger(object.sizeBytes());if(size.signum()<=0||size.compareTo(BigInteger.valueOf(maximumBytes))>0)fail(ErrorCode.CONTEXT_LIMIT);
  String digest=WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("object",object,"sources",selected,"versions",verifiedVersions,"actor",a.actorId(),"context",a.context())));
  var members=new TreeMap<String,ArtifactRegistryProtocol.MemberRow>();for(var entry:verifiedVersions.values()){var owner=entry.owner();if(owner.sourceVersion()!=null){var source=selected.get(owner.sourceVersion());if(source==null)fail(ErrorCode.CONTENT_MISMATCH);members.put(entry.version().id(),new ArtifactRegistryProtocol.MemberRow(entry.version().id(),source.rights().rightsRevision()));}}
  return new State(digest,object,selected.get(basis.version_id()).source().classification(),List.copyOf(members.values()));
 }
 @FunctionalInterface interface Work<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}
 private <T>T tx(ApplicationSessionIdentity.Verified s,Work<T> work){return access.execute(s,a->ThrowingSupplier.of(()->work.apply(a)).get());}
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
