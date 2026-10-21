package com.ruoyi.medical.persistence;

import java.util.*;
import static com.ruoyi.medical.persistence.NativeImageQaProtocol.*;

/** Isolated simulation harness. No Spring registration, network, credentials or formal ledger writes.
 * Journal is caller-owned and can survive harness recreation, not process restart. */
public final class NativeImageQaSimulation {
 /** Must check current tenant/project/source rights, exact original bytes, provider/region and model.send.
  * Invoked for prepare, actual dispatch, recovery and independent review. */
 @FunctionalInterface public interface Authority {void requireCurrent(Context context)throws Exception;}
 public interface Provider {Receipt send(Context context)throws Exception;Optional<Receipt> lookup(String requestId)throws Exception;}
 public enum State {PREPARED, UNCERTAIN, SETTLED}
 private static final class Entry {final Context context;final String hash;final long units;State state=State.PREPARED;Receipt receipt;Entry(Context c,String h,long u){context=c;hash=h;units=u;}}
 public static final class Journal {
  private final Map<String,Entry> entries=new HashMap<>();private long reserved,settled;private final long limit;
  public Journal(long limit){require(limit>0,"BUDGET_INVALID");this.limit=limit;}
  public synchronized long reservedUnits(){return reserved;}public synchronized long settledUnits(){return settled;}
  public synchronized State state(String request){return Objects.requireNonNull(entries.get(request)).state;}
 }
 private final Authority authority;private final Provider provider;private final Journal journal;
 public NativeImageQaSimulation(Authority a,Provider p,Journal j){authority=Objects.requireNonNull(a);provider=Objects.requireNonNull(p);journal=Objects.requireNonNull(j);}
 public String prepare(Context c)throws Exception{
  String hash=hash(c);authority.requireCurrent(c);long units=units(c);require(units<=c.maximumUnits(),"REQUEST_BUDGET_EXCEEDED");
  synchronized(journal){Entry old=journal.entries.get(c.requestId());if(old!=null){require(old.hash.equals(hash),"IDEMPOTENCY_CONFLICT");return hash;}
   require(journal.entries.size()<128,"SIMULATION_REQUEST_CAPACITY_REACHED");require(units<=journal.limit-journal.reserved-journal.settled,"BUDGET_EXCEEDED");journal.reserved+=units;journal.entries.put(c.requestId(),new Entry(c,hash,units));return hash;}
 }
 public Receipt dispatch(Context c)throws Exception{
  prepare(c);
  synchronized(journal){Entry e=entry(c);authority.requireCurrent(c);if(e.state==State.SETTLED)return e.receipt;
   require(e.state==State.PREPARED,"OUTCOME_UNCERTAIN_RECOVER_FIRST");e.state=State.UNCERTAIN;
   // Any thrown exception leaves reserved units and UNCERTAIN. Never blindly send again.
   return settle(e,provider.send(c));
  }
 }
 public Optional<Receipt> recover(Context c)throws Exception{
  validate(c);authority.requireCurrent(c);
  synchronized(journal){Entry e=entry(c);if(e.state==State.SETTLED)return Optional.of(e.receipt);require(e.state==State.UNCERTAIN,"NOT_DISPATCHED");var receipt=provider.lookup(c.requestId());if(receipt.isEmpty())return Optional.empty();return Optional.of(settle(e,receipt.get()));}
 }
 public Review independentReview(Context answer,Context reviewer)throws Exception{
  validate(answer);validate(reviewer);authority.requireCurrent(answer);authority.requireCurrent(reviewer);
  require(!answer.sessionId().equals(reviewer.sessionId())&&!answer.requestId().equals(reviewer.requestId()),"INDEPENDENT_SESSION_REQUIRED");
  require(answer.tenantId().equals(reviewer.tenantId())&&answer.projectId().equals(reviewer.projectId())&&answer.question().equals(reviewer.question())&&answer.images().equals(reviewer.images()),"INDEPENDENT_SOURCE_MISMATCH");
  synchronized(journal){Entry a=entry(answer),r=entry(reviewer);require(a.state==State.SETTLED&&r.state==State.SETTLED,"UNSETTLED_REVIEW");verify(a,provider.lookup(answer.requestId()).orElseThrow());verify(r,provider.lookup(reviewer.requestId()).orElseThrow());return new Review(VERSION,"SIM",a.hash,r.hash,"SYNTHETIC_TRANSPORT_VERIFIED_NOT_SEMANTIC_SUPPORT",true,false);}
 }
 private Entry entry(Context c)throws Exception{Entry e=journal.entries.get(c.requestId());require(e!=null&&e.hash.equals(hash(c)),"CONTEXT_MISMATCH");return e;}
 private Receipt settle(Entry e,Receipt r){verify(e,r);require(e.state==State.UNCERTAIN,"INVALID_SETTLEMENT");journal.reserved-=e.units;journal.settled+=r.chargedUnits();e.receipt=r;e.state=State.SETTLED;return r;}
 private void verify(Entry e,Receipt r){require(r!=null&&VERSION.equals(r.schemaVersion())&&"SIM".equals(r.mode())&&PROVIDER.equals(r.provider())&&e.context.requestId().equals(r.requestId())&&e.hash.equals(r.contextSha256())&&e.context.sessionId().equals(r.sessionId())&&r.synthetic()&&!r.formalEvidenceEligible()&&r.imageBytes()==bytes(e.context)&&r.chargedUnits()==e.units&&r.result().equals(NativeImageQaMockProvider.result(e.context)),"PROVIDER_RECEIPT_MISMATCH");}
}
