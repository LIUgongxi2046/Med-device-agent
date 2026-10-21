package com.ruoyi.medical.persistence.erasure;

import java.time.Duration;
import java.util.*;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Transport-independent exact-copy adapter. No real network client, credential, Spring registration,
 * authority bypass or SOURCE lifecycle transition exists here. */
public final class ExternalErasureAdapter {
    private final Api api;
    private final Journal journal;
    private final Authority authority;
    private final Kind kind;
    private final int limit;
    public ExternalErasureAdapter(Kind kind, Api api, Journal journal, Authority authority, int limit) {
        this.kind=Objects.requireNonNull(kind); this.api=Objects.requireNonNull(api);
        this.journal=Objects.requireNonNull(journal); this.authority=Objects.requireNonNull(authority);
        if(limit<1 || limit>10000) throw new IllegalArgumentException("INVALID_LIMIT"); this.limit=limit;
    }
    private <T> T call(Approval approval, Action<T> action) throws Exception {
        if(approval.scope().kind()!=kind || !approval.validUntil().isAfter(authority.now()))
            throw new IllegalStateException("SCOPE_STALE");
        return authority.current(approval,action);
    }
    public Inventory inventory(Approval approval) throws Exception {
        String cursor=null, snapshot=null; var cursors=new HashSet<String>();
        var copies=new LinkedHashMap<String,Copy>(); var covered=new HashSet<String>(); java.time.Instant observed=null;
        int pages=0;
        do {
            if(++pages>limit) throw new IllegalStateException("INVENTORY_CAPACITY");
            String next=cursor;
            Page page=call(approval,()->api.inventory(approval.scope(),next));
            if(page==null || !page.authoritative() || page.proof()!=approval.proof() || !approval.scope().equals(page.scope()))
                throw new IllegalStateException("INCOMPLETE_INVENTORY");
            if(snapshot==null) {snapshot=page.snapshot();observed=page.observedAt();}
            if(!snapshot.equals(page.snapshot()) || !observed.equals(page.observedAt()))
                throw new IllegalStateException("SNAPSHOT_CHANGED");
            if(observed.isAfter(authority.now()) || observed.isBefore(authority.now().minus(Duration.ofMinutes(5))))
                throw new IllegalStateException("STALE_OBSERVATION");
            for(Copy copy:page.copies()) {
                if(!approval.scope().equals(copy.scope()) || copies.putIfAbsent(copy.identity(),copy)!=null || copies.size()>limit)
                    throw new IllegalStateException("INVALID_INVENTORY_COPY");
            }
            covered.addAll(page.coveredOperations()); if(covered.size()>limit) throw new IllegalStateException("COVERAGE_CAPACITY");
            cursor=page.nextCursor();
            if(cursor!=null && !cursors.add(cursor)) throw new IllegalStateException("CURSOR_LOOP");
        } while(cursor!=null);
        return new Inventory(approval.scope(),snapshot,List.copyOf(copies.values()),covered,observed);
    }
    public Receipt erase(Delete request) throws Exception {
        Approval approval=request.approval();
        // Inspect persisted request before any provider interaction. UNKNOWN/DISPATCHING are recovery-only.
        Entry prior=journal.read(request.key());
        if(prior!=null) {
            if(!prior.request().equals(request)) throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
            if(prior.phase()!=Phase.PREPARED) return recover(approval,request.key());
        }
        Inventory inventory=inventory(approval);
        if(!approval.snapshot().equals(inventory.snapshot())) throw new IllegalStateException("APPROVAL_INVENTORY_STALE");
        if(!inventory.copies().contains(request.copy())) throw new IllegalStateException("COPY_NOT_AUTHORIZED");
        Copy current=call(approval,()->api.inspect(approval.scope(),request.copy().copyId(),request.copy().version()));
        if(!request.copy().equals(current)) throw new IllegalStateException("COPY_CHANGED");
        if(current.retained(authority.now())) throw new IllegalStateException("RETAINED_HOLD");
        Entry prepared=journal.prepare(request);
        if(prepared.phase()!=Phase.PREPARED) return recover(approval,request.key());
        // The durable DISPATCHING marker is committed before the one and only send attempt.
        journal.transition(request.key(),Phase.PREPARED,Phase.DISPATCHING,null);
        try {
            Receipt result=call(approval,()->api.delete(request)); validate(request,result);
            if(result.outcome()==Outcome.UNKNOWN) {
                journal.transition(request.key(),Phase.DISPATCHING,Phase.UNKNOWN,result); return result;
            }
            journal.transition(request.key(),Phase.DISPATCHING,Phase.COMPLETE,result); return result;
        } catch(Exception uncertain) {
            // A failed journal write may itself be uncertain; never overwrite a completed receipt.
            Entry entry=journal.read(request.key());
            if(entry!=null && entry.phase()==Phase.DISPATCHING)
                journal.transition(request.key(),Phase.DISPATCHING,Phase.UNKNOWN,null);
            throw uncertain;
        }
    }
    /** Original-key read-only recovery. A missing provider receipt does not trigger another delete. */
    public Receipt recover(Approval approval, String key) throws Exception {
        token(key); Entry entry=journal.read(key);
        if(entry==null || !entry.request().approval().equals(approval)) throw new IllegalStateException("RECOVERY_NOT_FOUND");
        return call(approval,()->{
            if(entry.phase()==Phase.COMPLETE) return entry.receipt();
            Optional<Receipt> found=api.recover(approval.scope(),key);
            if(found.isEmpty()) return unknown(entry.request());
            Receipt result=found.get(); validate(entry.request(),result);
            if(result.outcome()!=Outcome.UNKNOWN)
                journal.transition(key,entry.phase(),Phase.COMPLETE,result);
            return result;
        });
    }
    public Absence finalAbsence(Approval approval) throws Exception {
        Inventory inventory=inventory(approval);
        if(!inventory.copies().isEmpty()) throw new IllegalStateException("SURVIVING_COPIES");
        return new Absence(VERSION,approval.proof(),approval.scope(),inventory.snapshot(),inventory.observedAt(),approval.proof()==Proof.AUTHORITATIVE);
    }
    private Receipt unknown(Delete request) {
        Copy c=request.copy();return new Receipt(VERSION,request.proof(),request.key(),c.scope(),c.copyId(),c.version(),c.sha256(),Outcome.UNKNOWN,null,authority.now());
    }
    private void validate(Delete request, Receipt result) {
        Copy c=request.copy();
        if(result==null || result.proof()!=request.proof() || !request.key().equals(result.key()) || !c.scope().equals(result.scope()) ||
            !c.copyId().equals(result.copyId()) || !c.version().equals(result.copyVersion()) || !c.sha256().equals(result.sha256()) ||
            result.observedAt().isAfter(authority.now()) || result.observedAt().isBefore(authority.now().minus(Duration.ofMinutes(5))))
            throw new IllegalStateException("RECEIPT_MISMATCH");
    }
}
