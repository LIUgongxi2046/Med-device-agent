package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual chunked input orchestration, explicit authority/transport doubles only.
 * No deployment, provider, isolation or successful research run is claimed. */
class TaskProfessionalHostAccessTest {
 final FormalWorkerAccess workers=mock(FormalWorkerAccess.class);final TaskNodeInputAuthority inputs=mock(TaskNodeInputAuthority.class);
 final TaskNodeInputAuthority.Verified input=mock(TaskNodeInputAuthority.Verified.class);final TaskNodeProducerAuthority producers=mock(TaskNodeProducerAuthority.class);final TaskNodeProducerAuthority.LeasedProduction producer=mock(TaskNodeProducerAuthority.LeasedProduction.class);
 final TaskProfessionalRuntimeService runtime=mock(TaskProfessionalRuntimeService.class);final SourceUploadTransport transport=mock(SourceUploadTransport.class);final TaskProfessionalRuntimeService.Deployment deployment=mock(TaskProfessionalRuntimeService.Deployment.class);
 final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class,RETURNS_DEEP_STUBS);final HttpServletRequest http=mock(HttpServletRequest.class);final TaskProfessionalRuntimeService.Envelope envelope=mock(TaskProfessionalRuntimeService.Envelope.class);
 final String tenant=id(1),project=id(2),actor=id(3),run=id(4),step=id(5),action=id(6),inputId=id(7),receipt="a".repeat(64),service="spiffe://test.invalid/worker";
 final byte[] bytes;final String hash;final InterfaceProtocol.ObjectRef reference;final AtomicInteger admissions=new AtomicInteger();final TaskProfessionalHostAccess access;
 TaskProfessionalHostAccessTest()throws Exception {
  var manifest=new JsonMapper().createObjectNode().put("question","Actual registered input").put("body","x".repeat(9000000));bytes=ContentReadAuthorization.canonicalBytes(manifest);hash=WireValues.sha256(bytes);reference=new InterfaceProtocol.ObjectRef("runtime/tenant/original-input","physical-v1",hash,Integer.toString(bytes.length),"application/json");
  when(deployment.maximumBytes()).thenReturn(16777216);access=new TaskProfessionalHostAccess(workers,inputs,producers,runtime,transport,deployment);
  var identity=admission.formalProof().identity();when(identity.tenant()).thenReturn(tenant);when(identity.project()).thenReturn(project);when(identity.actor()).thenReturn(actor);when(identity.run()).thenReturn(run);when(identity.step()).thenReturn(step);when(identity.action()).thenReturn(action);when(identity.generation()).thenReturn("1");when(identity.workerService()).thenReturn(service);
  when(inputs.require(admission,run,step)).thenReturn(input);when(producers.requireLeased(admission,input)).thenReturn(producer);when(input.inputId()).thenReturn(inputId);when(input.inputSha256()).thenReturn(hash);when(input.receiptSha256()).thenReturn(receipt);when(input.manifest()).thenReturn(manifest);when(input.objectReference()).thenReturn(reference);
  when(workers.executeAction(eq(http),eq("test-token"),any(),any())).thenAnswer(call->{admissions.incrementAndGet();return ((BiFunction<EnterpriseAccess.Admission,RunTokenAuthority.Admission,?>)call.getArgument(3)).apply(admission,null);});
  when(envelope.admission()).thenReturn(admission);when(envelope.runId()).thenReturn(run);when(envelope.stepId()).thenReturn(step);when(envelope.actionId()).thenReturn(action);when(envelope.leaseGeneration()).thenReturn("1");when(envelope.workerService()).thenReturn(service);when(envelope.inputId()).thenReturn(inputId);when(envelope.inputSha256()).thenReturn(hash);
 }
 RunTokenAuthority.ActionRequest action(String kind){return new RunTokenAuthority.ActionRequest(run,step,action,BigInteger.ONE,hash,"b".repeat(64),kind,"host-v1",new PermissionProtocol.Scope(tenant,project,"TASK",id(8)),"task.read","TASK_EXECUTION");}
 S3ObjectReader.VerifiedObject transport()throws Exception {
  var reader=mock(S3ObjectReader.class);var object=mock(S3ObjectReader.VerifiedObject.class);when(object.reference()).thenReturn(reference);when(object.fullSha256()).thenReturn(hash);when(reader.readVerified(eq(reference),any())).thenReturn(object);
  when(object.chunk(anyLong(),anyInt())).thenAnswer(call->{int start=Math.toIntExact((long)call.getArgument(0)),count=call.getArgument(1);return Arrays.copyOfRange(bytes,start,start+count);});
  when(transport.readTaskDocument(eq(tenant),eq(actor),eq(16777216),any(),any())).thenAnswer(call->{S3ObjectReader.Admission guard=call.getArgument(3);var timed=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){guard.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(10);}};return ((SourceUploadTransport.DocumentRead<?>)call.getArgument(4)).apply(reader,timed);});return object;
 }
 @Test void actualCanonicalInputGetRechecksEverySegmentAndConsumption()throws Exception {
  var object=transport();var handle=access.open(http,"test-token",action("HOST_CONTROL"));var read=access.consumeInput(handle);assertArrayEquals(bytes,read.bytes(envelope));assertTrue(admissions.get()>=7);verify(object,atLeast(2)).chunk(anyLong(),anyInt());verify(producer,atLeast(8)).requireHostInputConsume(input);verify(object).close();read.bytes(envelope)[0]=0;assertArrayEquals(bytes,read.bytes(envelope));
 }
 @Test void revocationDuringPhysicalReadReturnsNoContent()throws Exception {
  var object=transport();when(object.chunk(anyLong(),anyInt())).thenAnswer(call->{doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(producer).requireHostInputConsume(input);return Arrays.copyOfRange(bytes,Math.toIntExact((long)call.getArgument(0)),Math.toIntExact((long)call.getArgument(0))+(int)call.getArgument(1));});var handle=access.open(http,"test-token",action("HOST_CONTROL"));assertThrows(TenantAccessException.class,()->access.consumeInput(handle));verify(object).close();
 }
 @Test void siblingStepActionOrLeaseCannotConsumeDetachedBytes()throws Exception {
  transport();var read=access.consumeInput(access.open(http,"test-token",action("HOST_CONTROL")));when(envelope.actionId()).thenReturn(id(90));assertThrows(TenantAccessException.class,()->read.bytes(envelope));when(envelope.actionId()).thenReturn(action);when(envelope.leaseGeneration()).thenReturn("2");assertThrows(TenantAccessException.class,()->read.bytes(envelope));
 }
 @Test void changedOriginalReceiptOrObjectVersionDeniesBeforeGet()throws Exception {
  var handle=access.open(http,"test-token",action("HOST_CONTROL"));when(input.receiptSha256()).thenReturn("c".repeat(64));assertThrows(TenantAccessException.class,()->access.consumeInput(handle));verifyNoInteractions(transport);
 }
 @Test void hostProofCannotEscapeAfterAuthorityRevocation()throws Exception {
  transport();var read=access.consumeInput(access.open(http,"test-token",action("HOST_CONTROL")));doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(producer).requireHostInputConsume(input);assertThrows(TenantAccessException.class,()->read.bytes(envelope));
 }
 @Test void unrelatedToolActionNeverOpensHostHandle() {assertThrows(TenantAccessException.class,()->access.open(http,"test-token",action("TOOL")));verifyNoInteractions(workers,transport);}
 @Test void changedPhysicalBytesOrVersionCannotProduceContent()throws Exception {
  var object=transport();when(object.fullSha256()).thenReturn("c".repeat(64));var handle=access.open(http,"test-token",action("HOST_CONTROL"));assertThrows(TenantAccessException.class,()->access.consumeInput(handle));verify(object).close();
 }
 static String id(int n){return String.format("10000000-0000-4000-8000-%012d",n);}
}
