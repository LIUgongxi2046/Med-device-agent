package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import javax.sql.DataSource;import java.util.*;import java.math.BigInteger;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.util.function.ThrowingSupplier;
/** Bounded internal price/policy SOURCE originals. Never used to authorize model-send bytes. */
public final class FormalInvocationSourceOriginals {
 private final EnterpriseAccess access;private final SourceReadAuthority sources;private final SourceReadMapper sourceRows;private final VersionResourceAuthority versions;private final DependencyLineage lineage;private final SourceUploadTransport transport;private final JdbcTemplate jdbc;private final String environment,region;private final int maximumBytes;private final DependencyLineage.Limits limits;
 public FormalInvocationSourceOriginals(EnterpriseAccess access,SourceReadAuthority sources,SourceReadMapper sourceRows,VersionResourceAuthority versions,DependencyLineage lineage,SourceUploadTransport transport,DataSource data,String environment,String region,int maximumBytes,DependencyLineage.Limits limits){this.access=access;this.sources=sources;this.sourceRows=sourceRows;this.versions=versions;this.lineage=lineage;this.transport=transport;this.jdbc=new JdbcTemplate(data);jdbc.setQueryTimeout(5);this.environment=environment;this.region=region;if(maximumBytes<1)throw new IllegalArgumentException();this.maximumBytes=maximumBytes;this.limits=limits;}
 String requireCurrentBasis(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String purpose,String project)throws Exception{return state(a,basis,purpose,project).digest();}
 private record State(String digest,InterfaceProtocol.ObjectRef object,String classification){}
 public final class Read {
  private final PermissionProtocol.Basis basis;private final String purpose,project;private final State state;private final byte[] bytes;
  private Read(PermissionProtocol.Basis basis,String purpose,String project,State state,byte[] bytes){this.basis=basis;this.purpose=purpose;this.project=project;this.state=state;this.bytes=bytes.clone();}
  public byte[] bytes(){return bytes.clone();}public String sourceVersion(){return basis.version_id();}public String contentSha256(){return basis.content_sha256();}public String authorizationSha256(){return state.digest();}
  public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{if(!state.digest().equals(state(a,basis,purpose,project).digest()))fail(ErrorCode.SCOPE_STALE);}
 }
 public Read read(ApplicationSessionIdentity.Verified subject,String context,PermissionProtocol.Basis basis,String purpose,String project)throws Exception {
  State initial=tx(subject,a->{if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);return state(a,basis,purpose,project);});
  String tenant=tx(subject,a->a.context().tenant_id());S3ObjectReader.Admission current=()->tx(subject,a->{if(!a.context().context_revision().equals(context)||!initial.digest().equals(state(a,basis,purpose,project).digest()))fail(ErrorCode.SCOPE_STALE);return null;});
  byte[] bytes=transport.readSourceJob(tenant,subject.user().user_id(),maximumBytes,current,(reader,guard)->{
   try(var object=reader.readVerified(initial.object(),guard)){if(!initial.object().equals(object.reference())||!basis.content_sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);int size=Integer.parseInt(initial.object().sizeBytes());byte[] b=new byte[size];for(int start=0;start<size;){int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start);System.arraycopy(object.chunk(start,n),0,b,start,n);start+=n;}guard.requireCurrent();return b;}});
  if(!basis.content_sha256().equals(WireValues.sha256(bytes)))fail(ErrorCode.CONTENT_MISMATCH);current.requireCurrent();return new Read(basis,purpose,project,initial,bytes);
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
  return new State(digest,object,selected.get(basis.version_id()).source().classification());
 }
 @FunctionalInterface interface Work<T>{T apply(EnterpriseAccess.Admission a)throws Exception;}
 private <T>T tx(ApplicationSessionIdentity.Verified s,Work<T> work){return access.execute(s,a->ThrowingSupplier.of(()->work.apply(a)).get());}
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
