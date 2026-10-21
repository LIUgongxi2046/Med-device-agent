package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.NativeImageQaProtocol.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

class NativeImageQaSimulationTest {
 static Context context()throws Exception{return new Context(VERSION,"SIM",PROVIDER,CAPABILITY,UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),"What is shown?",List.of(NativeImageQaSimulationController.fixture()),100);}
 static Context reviewer(Context c){return new Context(c.schemaVersion(),c.mode(),c.provider(),c.capability(),c.tenantId(),c.projectId(),UUID.randomUUID().toString(),UUID.randomUUID().toString(),c.question(),c.images(),c.maximumUnits());}
 static NativeImageQaSimulation sim(NativeImageQaMockProvider p,NativeImageQaSimulation.Journal j){return new NativeImageQaSimulation(c->{},p,j);}
 @Test void nativeBytesRoundTripAndStrictProtocol()throws Exception{
  Context c=context();assertEquals(c,read(write(c)));assertThrows(Exception.class,()->read(write(c).replace("\"mode\":\"SIM\"","\"mode\":\"FORMAL\"")));assertThrows(Exception.class,()->read(write(c).replace("local-synthetic-image-v1","deepseek")));assertThrows(Exception.class,()->read(write(c).replace("NATIVE_IMAGE_TRANSPORT_SIM","IMAGE_OCR_TEXT")));assertThrows(Exception.class,()->read(write(c).replaceFirst("\\{","{\"unknown\":1,")));assertThrows(Exception.class,()->read(write(c)+" {}"));assertThrows(Exception.class,()->read(write(c).replaceFirst("\\{","{\"mode\":\"SIM\",")));
 }
 @Test void rejectsOcrMasqueradingAsImageAndTamperedOriginal()throws Exception{
  Context c=context();String raw=write(c);assertThrows(Exception.class,()->read(raw.replace(c.images().get(0).base64(),Base64.getEncoder().encodeToString("OCR: red square".getBytes()))));assertThrows(Exception.class,()->read(raw.replace(c.images().get(0).sha256(),"0".repeat(64))));assertThrows(Exception.class,()->read(raw.replace("image/png","image/jpeg")));assertThrows(Exception.class,()->read(raw.replace("\"width\":2","\"width\":99999")));assertThrows(Exception.class,()->read(raw.replace("fixture/b19-transport-grid.png","https://example.com/image.png")));
 }
 @Test void chargesExactlyOnceAndRechecksCurrentPermissionOnRecovery()throws Exception{
  Context c=context();var p=new NativeImageQaMockProvider();var j=new NativeImageQaSimulation.Journal(100);var allowed=new AtomicBoolean(true);var s=new NativeImageQaSimulation(x->{if(!allowed.get())throw new SecurityException("REVOKED");},p,j);
  s.prepare(c);assertEquals(2,j.reservedUnits());var r=s.dispatch(c);assertTrue(r.synthetic());assertFalse(r.formalEvidenceEligible());assertTrue(r.result().startsWith("SYNTHETIC"));assertEquals(2,j.settledUnits());assertEquals(0,j.reservedUnits());assertEquals(r,s.dispatch(c));assertEquals(r,s.recover(c).orElseThrow());assertEquals(1,p.calls());allowed.set(false);assertThrows(SecurityException.class,()->s.recover(c));assertThrows(SecurityException.class,()->s.dispatch(c));
 }
 @Test void withdrawalBeforeSendBlocksActualProvider()throws Exception{
  Context c=context();var p=new NativeImageQaMockProvider();var j=new NativeImageQaSimulation.Journal(100);var allowed=new AtomicBoolean(true);var s=new NativeImageQaSimulation(x->{if(!allowed.get())throw new SecurityException();},p,j);s.prepare(c);allowed.set(false);assertThrows(SecurityException.class,()->s.dispatch(c));assertEquals(0,p.calls());assertEquals(NativeImageQaSimulation.State.PREPARED,j.state(c.requestId()));
 }
 @Test void uncertainOutcomeRecoversWithoutResendEvenAcrossHarnessRecreation()throws Exception{
  Context c=context();var actual=new NativeImageQaMockProvider();var j=new NativeImageQaSimulation.Journal(100);
  var interrupted=new NativeImageQaSimulation.Provider(){public Receipt send(Context x)throws Exception{actual.send(x);throw new java.io.IOException("connection lost after acceptance");}public Optional<Receipt> lookup(String id){return actual.lookup(id);}};
  var first=new NativeImageQaSimulation(x->{},interrupted,j);assertThrows(java.io.IOException.class,()->first.dispatch(c));assertEquals(NativeImageQaSimulation.State.UNCERTAIN,j.state(c.requestId()));assertThrows(IllegalArgumentException.class,()->first.dispatch(c));assertEquals(2,j.reservedUnits());
  var recovered=new NativeImageQaSimulation(x->{},interrupted,j);assertTrue(recovered.recover(c).isPresent());assertEquals(1,actual.calls());assertEquals(2,j.settledUnits());assertEquals(0,j.reservedUnits());
 }
 @Test void missingProviderOutcomeRemainsUncertainWithReservedBudget()throws Exception{
  Context c=context();var j=new NativeImageQaSimulation.Journal(100);var provider=new NativeImageQaSimulation.Provider(){public Receipt send(Context x){throw new IllegalStateException("lost");}public Optional<Receipt> lookup(String id){return Optional.empty();}};var s=new NativeImageQaSimulation(x->{},provider,j);assertThrows(Exception.class,()->s.dispatch(c));assertTrue(s.recover(c).isEmpty());assertEquals(2,j.reservedUnits());assertEquals(0,j.settledUnits());assertThrows(Exception.class,()->s.dispatch(c));
 }
 @Test void cannotPromoteSyntheticReceiptOrAlterUsage()throws Exception{
  Context c=context();var p=new NativeImageQaMockProvider();var j=new NativeImageQaSimulation.Journal(100);var forged=new NativeImageQaSimulation.Provider(){public Receipt send(Context x)throws Exception{var r=p.send(x);return new Receipt(r.schemaVersion(),"FORMAL",r.provider(),r.requestId(),r.contextSha256(),r.sessionId(),r.result(),r.imageBytes(),0,false,true);}public Optional<Receipt> lookup(String id){return Optional.empty();}};assertThrows(IllegalArgumentException.class,()->new NativeImageQaSimulation(x->{},forged,j).dispatch(c));assertEquals(0,j.settledUnits());assertEquals(2,j.reservedUnits());
 }
 @Test void budgetAndIdempotencyCannotBeExpanded()throws Exception{
  Context c=context();assertThrows(IllegalArgumentException.class,()->sim(new NativeImageQaMockProvider(),new NativeImageQaSimulation.Journal(1)).dispatch(c));var p=new NativeImageQaMockProvider();var j=new NativeImageQaSimulation.Journal(2);var s=sim(p,j);s.dispatch(c);assertThrows(IllegalArgumentException.class,()->s.dispatch(reviewer(c)));Context changed=read(write(c).replace("What is shown?","Changed question"));assertThrows(IllegalArgumentException.class,()->s.prepare(changed));assertThrows(IllegalArgumentException.class,()->s.recover(changed));assertEquals(1,p.calls());
 }
 @Test void independentReviewRequiresIndependentSessionsAndSameOriginals()throws Exception{
  Context c=context(),r=reviewer(c);var p=new NativeImageQaMockProvider();var s=sim(p,new NativeImageQaSimulation.Journal(100));s.dispatch(c);assertThrows(IllegalArgumentException.class,()->s.independentReview(c,c));assertThrows(IllegalArgumentException.class,()->s.independentReview(c,r));s.dispatch(r);Review review=s.independentReview(c,r);assertTrue(review.synthetic());assertFalse(review.formalEvidenceEligible());assertEquals("SYNTHETIC_TRANSPORT_VERIFIED_NOT_SEMANTIC_SUPPORT",review.verdict());Context changed=read(write(r).replace("What is shown?","Different question"));assertThrows(IllegalArgumentException.class,()->s.independentReview(c,changed));
 }
}
