package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;import java.util.*;import java.time.Duration;import javax.sql.DataSource;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.beans.factory.ObjectProvider;import org.springframework.util.function.ThrowingSupplier;
/** Full published evidence and physical fixed manifest, prepared outside the consuming transaction. */
public final class AgentPublicationAuthority {
 private final EnterpriseAccess access;private final JdbcTemplate db;private final FormalAgentAcceptanceStore acceptances;private final FormalAgentPublicationAuthorization authorizations;private final FormalAgentPublicationObjectLedger ledger;private final FormalAgentPublicationRegistration registrations;private final ObjectProvider<PublicationEvidenceRevalidation> evidence;private final ObjectProvider<RuntimeObjectTransport> transport;private final ResourceReadAuthority resources;private final FormalAgentPublicationSchema schema;private final PermissionSchema json;private final FormalAgentPublicationLifecycle lifecycle;
 public AgentPublicationAuthority(EnterpriseAccess access,DataSource data,FormalAgentAcceptanceStore acceptances,FormalAgentPublicationAuthorization authorizations,FormalAgentPublicationObjectLedger ledger,FormalAgentPublicationRegistration registrations,ObjectProvider<PublicationEvidenceRevalidation> evidence,ObjectProvider<RuntimeObjectTransport> transport,ResourceReadAuthority resources,FormalAgentPublicationSchema schema,PermissionSchema json,FormalAgentPublicationLifecycle lifecycle){this.access=access;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.acceptances=acceptances;this.authorizations=authorizations;this.ledger=ledger;this.registrations=registrations;this.evidence=evidence;this.transport=transport;this.resources=resources;this.schema=schema;this.json=json;this.lifecycle=lifecycle;}
 public static final class PreparedPublication {
  private final AgentPublicationAuthority owner;private final String actor,tenant,context,release,purpose;private final boolean historical;private final PermissionProtocol.Basis acceptance;private final PublicationEvidenceRevalidation.Prepared evidence;private final FormalAgentPublicationObjectLedger.Attempt attempt;private final RuntimeObjectTransport.VerifiedTransfer transfer;
  private PreparedPublication(AgentPublicationAuthority owner,String actor,String tenant,String context,String release,String purpose,boolean historical,PermissionProtocol.Basis acceptance,PublicationEvidenceRevalidation.Prepared evidence,FormalAgentPublicationObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer){this.owner=owner;this.actor=actor;this.tenant=tenant;this.context=context;this.release=release;this.purpose=purpose;this.historical=historical;this.acceptance=acceptance;this.evidence=evidence;this.attempt=attempt;this.transfer=transfer;}
 }
 public PreparedPublication prepare(ApplicationSessionIdentity.Verified subject,String context,String releaseId,String purpose,boolean historical)throws Exception{return prepare(EvidenceReadAccess.human(access,subject),context,releaseId,purpose,historical);}
 /** Session-free exact pinned publication read; cannot disable or publish. */
 PreparedPublication prepareControl(FormalRunControlAccess.Reads reads,String releaseId,String purpose)throws Exception{reads.requirePublication(releaseId,purpose);return prepare(EvidenceReadAccess.control(reads),reads.contextRevision(),releaseId,purpose,false);}
 PreparedPublication preparePlanDraft(TaskPlanDraftControlAccess.Reads reads,String releaseId,String purpose)throws Exception{reads.requirePublication(releaseId,purpose);return prepare(EvidenceReadAccess.planDraft(reads),reads.contextRevision(),releaseId,purpose,false);}
 PreparedPublication prepareDocumentReview(DocumentReviewControlAccess.Reads reads,String releaseId,String purpose)throws Exception{reads.requirePublication(releaseId,purpose);return prepare(EvidenceReadAccess.documentReview(reads),reads.contextRevision(),releaseId,purpose,false);}
 private PreparedPublication prepare(EvidenceReadAccess subject,String context,String releaseId,String purpose,boolean historical)throws Exception{
  WireValues.uuid(releaseId);purpose(purpose);var initial=subject.execute(a->checked(()->{current(a,context);var ref=acceptance(a,releaseId);subject.requireVersion(ref.version_id(),ref.content_sha256());var actual=acceptances.metadata(a,ref,"AGENT_PUBLICATION");if(!releaseId.equals(actual.row().releaseId()))bad();return actual;}));
  var adapter=evidence.getIfAvailable();var writer=transport.getIfAvailable();if(adapter==null||writer==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var ref=new PermissionProtocol.Basis(initial.row().acceptanceId(),initial.row().contentSha256());var prepared=subject.prepareEvidence(adapter,context,initial.content().deepCopy(),historical);
  var attempt=subject.execute(a->checked(()->{current(a,context);var proof=authorizations.require(a,ref,false,historical,prepared);var value=ledger.readPublishedAttempt(a,releaseId,proof);readArtifact(a,value);return value;}));
  long deadline=System.nanoTime()+Duration.ofSeconds(60).toNanos();var guard=new S3ObjectReader.Admission(){public void requireCurrent(){if(System.nanoTime()>=deadline)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);subject.execute(a->checked(()->{current(a,context);var proof=authorizations.require(a,ref,false,historical,prepared);var fresh=ledger.readPublishedAttempt(a,releaseId,proof);if(!fresh.writeIntent().equals(attempt.writeIntent())||!fresh.plannedArtifactId().equals(attempt.plannedArtifactId()))bad();readArtifact(a,fresh);ledger.requireAttempt(a,attempt);return null;}));}public Duration remainingTime(){requireCurrent();return Duration.ofNanos(Math.max(1,deadline-System.nanoTime()));}};
  var transferred=writer.transferVerified(attempt,guard);return new PreparedPublication(this,subject.actorId(),initial.row().tenantId(),context,releaseId,purpose,historical,ref,prepared,attempt,transferred);
 }
 public JsonNode disable(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception{
  var request=schema.request("Disable",bytes).deepCopy();SimulationInputArtifactSchema.key(key);String release=request.required("release_id").asText();var prepared=prepare(subject,context,release,"AGENT_PUBLICATION",true);
  return access.execute(subject,a->checked(()->{var loaded=load(a,release,"AGENT_PUBLICATION",prepared);return lifecycle.disable(a,key,request,ledger.row(prepared.attempt),loaded.registration());}));
 }
 /** Reads only the original atomic disable; never calls disable, prepares a write, or finishes an operation. */
 public JsonNode recoverDisable(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes)throws Exception{
  var recoverySchema=new FormalPublicationDisableRecoverySchema(schema);
  var recovery=recoverySchema.recovery(bytes).deepCopy();
  var request=recovery.required("request");
  String key=recovery.required("original_key").asText(),release=request.required("release_id").asText();
  final PreparedPublication prepared;
  try{prepared=prepare(subject,context,release,"AGENT_PUBLICATION",true);}
  catch(TenantAccessException unavailable){
   if(unavailable.code()!=ErrorCode.OBJECT_UNAVAILABLE)throw unavailable;
   access.execute(subject,a->checked(()->{
    current(a,context);
    try{acceptance(a,release);}
    catch(TenantAccessException missing){
     if(missing.code()!=ErrorCode.OBJECT_UNAVAILABLE)throw missing;
     // Only an authorized original actor/key/body may learn that its publication join is damaged.
     lifecycle.requireOriginalRecoveryRead(a,key,request);bad();
    }
    return null;
   }));
   // A valid publication join with unavailable physical bytes remains unavailable, never absence/success.
   throw unavailable;
  }
  return access.execute(subject,a->checked(()->{
   var loaded=load(a,release,"AGENT_PUBLICATION",prepared);
   return recoverySchema.receipt(lifecycle.recoverDisable(a,key,request,ledger.row(prepared.attempt),loaded.registration()));
  }));
 }
 public VerifiedPublication require(EnterpriseAccess.Admission a,String release,String purpose,PreparedPublication prepared)throws Exception{if(prepared==null||prepared.historical)deny();var loaded=load(a,release,purpose,prepared);if(!"PUBLISHED".equals(loaded.authorization().candidateStatus()))throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);return new VerifiedPublication(this,a,release,purpose,prepared,loaded);}
 public HistoricalPublication requireHistoricalRead(EnterpriseAccess.Admission a,String release,String purpose,PreparedPublication prepared)throws Exception{if(prepared==null||!prepared.historical)deny();return new HistoricalPublication(this,a,release,purpose,prepared,load(a,release,purpose,prepared));}
 /** Legacy unprepared calls cannot perform original object I/O while holding the SQL transaction. */
 public VerifiedPublication require(EnterpriseAccess.Admission a,String release,String purpose){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
 public HistoricalPublication requireHistoricalRead(EnterpriseAccess.Admission a,String release,String purpose){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
 private record Loaded(FormalAgentPublicationAuthorization.Verified authorization,JsonNode registration,String fingerprint){}
 private Loaded load(EnterpriseAccess.Admission a,String release,String purpose,PreparedPublication p)throws Exception{
  if(p.owner!=this||!p.actor.equals(a.actorId())||!p.tenant.equals(a.context().tenant_id())||!p.release.equals(release)||!p.purpose.equals(purpose))deny();current(a,p.context);if(!p.acceptance.equals(acceptance(a,release)))bad();var auth=authorizations.require(a,p.acceptance,false,p.historical,p.evidence);var registered=registrations.read(a,p.attempt,p.transfer,auth);var identity=json.object();identity.set("registration",registered);identity.set("current_authorizations",auth.authorizations());identity.put("current_candidate_status",auth.candidateStatus());identity.put("current_candidate_revision",auth.candidateRevision());return new Loaded(auth,registered,schema.hash(identity));
 }
 public static final class VerifiedPublication {
  private final AgentPublicationAuthority owner;private final EnterpriseAccess.Admission admission;private final String release,purpose;private final PreparedPublication prepared;private final Loaded loaded;
  private VerifiedPublication(AgentPublicationAuthority owner,EnterpriseAccess.Admission admission,String release,String purpose,PreparedPublication prepared,Loaded loaded){this.owner=owner;this.admission=admission;this.release=release;this.purpose=purpose;this.prepared=prepared;this.loaded=loaded;}
  public void requireConsumer(EnterpriseAccess.Admission actual,String releaseId,String entryPurpose)throws Exception{if(actual!=admission||!release.equals(releaseId)||!purpose.equals(entryPurpose))deny();requireUnchanged();}
  public AgentReleaseProtocol.Validated manifest(){return loaded.authorization().manifest();}public String approvalSha256(){return prepared.acceptance.content_sha256();}public JsonNode authorizations(){return loaded.authorization().authorizations();}public List<String> dependencyVersions(){var ids=new TreeSet<String>(loaded.authorization().dependencyVersions());ids.add(prepared.attempt.plannedArtifactId());return List.copyOf(ids);}public String executionPolicyId(){return loaded.authorization().executionPolicy().version_id();}public String executionPolicySha256(){return loaded.authorization().executionPolicy().content_sha256();}
  public void requireUnchanged()throws Exception{var fresh=owner.require(admission,release,purpose,prepared);if(!loaded.fingerprint().equals(fresh.loaded.fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 public static final class HistoricalPublication {
  private final AgentPublicationAuthority owner;private final EnterpriseAccess.Admission admission;private final String release,purpose;private final PreparedPublication prepared;private final Loaded loaded;
  private HistoricalPublication(AgentPublicationAuthority owner,EnterpriseAccess.Admission admission,String release,String purpose,PreparedPublication prepared,Loaded loaded){this.owner=owner;this.admission=admission;this.release=release;this.purpose=purpose;this.prepared=prepared;this.loaded=loaded;}
  public AgentReleaseProtocol.Validated manifest(){return loaded.authorization().manifest();}public String executionPolicyId(){return loaded.authorization().executionPolicy().version_id();}public String executionPolicySha256(){return loaded.authorization().executionPolicy().content_sha256();}public JsonNode authorizations(){return loaded.authorization().authorizations();}public List<String> dependencyVersions(){var ids=new TreeSet<String>(loaded.authorization().dependencyVersions());ids.add(prepared.attempt.plannedArtifactId());return List.copyOf(ids);}
  public void requireUnchanged()throws Exception{var fresh=owner.requireHistoricalRead(admission,release,purpose,prepared);if(!loaded.fingerprint().equals(fresh.loaded.fingerprint()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 }
 /** Read-only policy identity for task history. This is not an executable/publication proof and performs no object I/O. */
 PermissionProtocol.Basis taskSourcePolicyReference(EnterpriseAccess.Admission a, AgentReleaseStore.Stored release,
   String purpose) throws java.io.IOException {
  purpose(purpose);
  if(release.admission()!=a||!release.purpose().equals(purpose))deny();
  var original=acceptances.metadata(a,acceptance(a,release.release().releaseId()),purpose);
  if(!original.row().releaseId().equals(release.release().releaseId())
    ||!original.manifest().equals(release.content())
    ||!original.row().candidateVersionId().equals(release.agent().agentVersionId()))bad();
  return new PermissionProtocol.Basis(original.candidate().binding().policyVersionId(),original.candidate().binding().policySha256());
 }
 private PermissionProtocol.Basis acceptance(EnterpriseAccess.Admission a,String release){var values=db.query("SELECT i.acceptance_id,i.acceptance_sha256 FROM formal_agent_publication_intent i JOIN formal_agent_publication_registration r ON r.tenant_id=i.tenant_id AND r.release_id=i.release_id AND r.write_id=i.write_id AND r.operation_id=i.operation_id AND r.artifact_id=i.artifact_id AND r.project_id <=> i.project_id WHERE i.tenant_id=? AND i.release_id=? LIMIT 2",(rs,n)->new PermissionProtocol.Basis(rs.getString(1),rs.getString(2)),a.context().tenant_id(),release);if(values.size()!=1)throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);return values.get(0);}
 private void readArtifact(EnterpriseAccess.Admission a,FormalAgentPublicationObjectLedger.Attempt i)throws java.io.IOException{resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),i.projectId(),"ARTIFACT",i.plannedArtifactId()),"artifact.read","AGENT_PUBLICATION");}
 private static void purpose(String purpose){if(!Set.of("TASK_CREATION","PLAN_MAINTENANCE","RUN_CREATION","AGENT_PUBLICATION").contains(purpose))deny();}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}private static <T>T checked(ThrowingSupplier<T> fn){return fn.get();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
