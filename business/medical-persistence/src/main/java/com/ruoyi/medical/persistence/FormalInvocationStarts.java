package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;import java.io.IOException;import java.util.*;import javax.sql.DataSource;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.util.function.ThrowingSupplier;
/** Actual two-phase fixed-input preparation and atomic system-job start. No model request is performed here. */
public final class FormalInvocationStarts {
 private final EnterpriseAccess access;private final FormalVerificationPreparation preparation;private final FormalVerificationJobs jobs;private final FormalInvocationCurrent current;private final JdbcTemplate db;
 public FormalInvocationStarts(EnterpriseAccess access,FormalVerificationPreparation preparation,FormalVerificationJobs jobs,FormalInvocationCurrent current,DataSource data){this.access=access;this.preparation=preparation;this.jobs=jobs;this.current=current;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 public static final class StartProof {
  private final String verification,run,step,action,invocation,requestHash,inputHash,model,credential,credentialRevision;
  private StartProof(FormalVerificationJobs.Job j,String invocation,String credential,String credentialRevision){verification=j.verification();run=j.run();step=j.step();action=j.action();this.invocation=invocation;requestHash=j.requestHash();inputHash=j.inputHash();model=j.model();this.credential=credential;this.credentialRevision=credentialRevision;}
  public String verificationId(){return verification;}public String runId(){return run;}public String stepId(){return step;}public String actionId(){return action;}public String invocationId(){return invocation;}public String requestBodySha256(){return requestHash;}public String inputSha256(){return inputHash;}public String modelRevision(){return model;}public String credentialReferenceId(){return credential;}public String credentialRevision(){return credentialRevision;}
 }
 public StartProof startVerification(ApplicationSessionIdentity.Verified actor,String context,ConnectionVerificationIntentProof intent)throws IOException{
  try{
   var existing=access.execute(actor,a->ThrowingSupplier.of(()->{context(a,context);intent.requireCurrent(a);return readVerificationStart(a,intent.verificationId());}).get());if(existing.isPresent())return existing.get();
   var prepared=preparation.prepare(actor,context,intent);
   return access.execute(actor,a->ThrowingSupplier.of(()->{context(a,context);intent.requireCurrent(a);jobs.create(a,prepared);return readVerificationStart(a,intent.verificationId()).orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));}).get());
  }catch(IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("Formal verification start unavailable",e);}
 }
 public Optional<StartProof> readVerificationStart(EnterpriseAccess.Admission a,String verification)throws IOException{return readStart(a,verification,true);}
 public Optional<StartProof> readAuthorizedVerificationStart(EnterpriseAccess.Admission a,String verification)throws IOException{return readStart(a,verification,false);}
 private Optional<StartProof> readStart(EnterpriseAccess.Admission a,String verification,boolean ownerOnly)throws IOException{
  try{WireValues.uuid(verification);var existing=jobs.read(a.context().tenant_id(),verification);if(existing.isEmpty())return Optional.empty();var j=existing.get();if(ownerOnly&&!a.actorId().equals(j.actor()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);var checked=current.require(a,j,false);
   var rows=db.query("SELECT invocation_id FROM invocation WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? AND verification_id=? AND created_by=? LIMIT 2",(r,n)->r.getString(1),j.tenant(),j.run(),j.step(),j.action(),j.verification(),j.actor());if(rows.size()>1)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);String invocation=rows.isEmpty()?null:rows.get(0);
   var bound=db.query("SELECT invocation_id FROM step_action_binding WHERE tenant_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2",(r,n)->Optional.ofNullable(r.getString(1)),j.tenant(),j.run(),j.step(),j.action());if(bound.size()!=1||!Objects.equals(invocation,bound.get(0).orElse(null)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
   checked.requireCurrent();return Optional.of(new StartProof(j,invocation,checked.credentialReferenceId(),checked.credentialRevision()));
  }catch(IOException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IOException("Formal verification start read unavailable",e);}
 }
 private static void context(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
}
