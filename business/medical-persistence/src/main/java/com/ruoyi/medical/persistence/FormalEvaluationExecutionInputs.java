package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import java.io.IOException;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;

/** Actual 079 original GET preparation outside the tenant transaction. Never accepts caller document bytes. */
public final class FormalEvaluationExecutionInputs {
 private final EnterpriseAccess access;private final FormalEvaluationExecutionAdmission authority;
 private final FormalEvaluationInputService inputs;private final PermissionSchema json;
 public FormalEvaluationExecutionInputs(EnterpriseAccess access,FormalEvaluationExecutionAdmission authority,FormalEvaluationInputService inputs,PermissionSchema json){this.access=access;this.authority=authority;this.inputs=inputs;this.json=json;}
 public Prepared prepare(ApplicationSessionIdentity.Verified actor,String context,Start request)throws Exception {
  if(TransactionSynchronizationManager.isActualTransactionActive())fail(ErrorCode.CONTEXT_CHANGED);
  var original=access.execute(actor,a->ThrowingSupplier.of(()->authority.require(a,context,request)).get());
  var values=new LinkedHashMap<String,FormalEvaluationInputService.VerifiedInput>();
  for(var sample:original.definition().request().samples()){
   var read=inputs.readVerified(actor,context,sample.input_ref().version_id());
   check(sample,read,original.policy().businessPurpose(),json);
   if(values.put(sample.sample_id(),read)!=null)fail(ErrorCode.CONTENT_MISMATCH);
  }
  var prepared=new Prepared(this,context,request,original.canonical(),values);
  access.execute(actor,a->ThrowingSupplier.of(()->{prepared.requireCurrent(a);return null;}).get());return prepared;
 }
 static void check(EvaluationProtocol.Sample sample,FormalEvaluationInputService.VerifiedInput input,String purpose,PermissionSchema json)throws IOException {
  var receipt=input.receipt();var basis=receipt.required("artifact");
  if(!sample.input_ref().version_id().equals(basis.required("version_id").asText())||!sample.input_ref().content_sha256().equals(basis.required("content_sha256").asText())
    ||!"FORMAL_EVALUATION_INPUT".equals(receipt.required("artifact_kind").asText())||!receipt.required("project_id").isNull()
    ||!purpose.equals(receipt.required("purpose").asText())||!FormalEvaluationInputSchema.REF.equals(receipt.required("input_schema_ref").asText())
    ||!sample.document_type().equals(input.document().required("document_type").asText())
    ||!sample.input_ref().content_sha256().equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(input.document()))))fail(ErrorCode.CONTENT_MISMATCH);
 }
 public static final class Prepared {
  private final FormalEvaluationExecutionInputs owner;private final String context,originalAdmission;private final Start request;
  private final Map<String,FormalEvaluationInputService.VerifiedInput> inputs;
  private Prepared(FormalEvaluationExecutionInputs owner,String context,Start request,String originalAdmission,Map<String,FormalEvaluationInputService.VerifiedInput> inputs){this.owner=owner;this.context=context;this.request=request;this.originalAdmission=originalAdmission;this.inputs=Map.copyOf(inputs);}
  public FormalEvaluationExecutionAdmission.Prepared requireCurrent(EnterpriseAccess.Admission a)throws IOException {
   var current=owner.authority.require(a,context,request);if(!originalAdmission.equals(current.canonical()))fail(ErrorCode.SCOPE_STALE);
   if(inputs.size()!=current.definition().request().samples().size())fail(ErrorCode.CONTENT_MISMATCH);
   for(var sample:current.definition().request().samples()){
    var read=inputs.get(sample.sample_id());if(read==null)fail(ErrorCode.CONTENT_MISMATCH);read.requireCurrent(a);check(sample,read,current.policy().businessPurpose(),owner.json);
   }return current;
  }
  public Start request(){return request;}
  /** Exact original bytes represented as a defensive JSON copy; not an external-processing authorization. */
  JsonNode document(EnterpriseAccess.Admission a,String sample)throws IOException{requireCurrent(a);var read=inputs.get(sample);if(read==null)fail(ErrorCode.ACCESS_DENIED);return read.document();}
  JsonNode receipt(EnterpriseAccess.Admission a,String sample)throws IOException{requireCurrent(a);var read=inputs.get(sample);if(read==null)fail(ErrorCode.ACCESS_DENIED);return read.receipt();}
 }
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}
