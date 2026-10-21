package com.ruoyi.medical.persistence.erasure;

import java.time.Instant;
import java.util.*;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Isolated in-memory offline archive / provider service. Explicitly constructed only; never a bean,
 * fallback, network caller, real data reader or SOURCE authority. All identities must be sim-*.
 * No clinical/user content is stored; fixtures carry only invented digests and identifiers. */
public final class SyntheticErasureService implements Api {
    private final Scope scope;private final Instant now;private final Map<String,Copy> copies=new LinkedHashMap<>();
    private final Map<String,Receipt> receipts=new HashMap<>();private final Map<String,Delete> requests=new HashMap<>();
    private final Set<String> operations=new HashSet<>();private final int pageSize;
    private boolean complete=true,loseNextAcknowledgement=false;private int deleteCalls;
    public SyntheticErasureService(Scope scope,List<Copy> fixtures,Instant now,int pageSize) {
        fixture(scope.tenant());fixture(scope.source());fixture(scope.scopeId());
        this.scope=scope;this.now=Objects.requireNonNull(now);if(pageSize<1)throw new IllegalArgumentException("PAGE_SIZE");this.pageSize=pageSize;
        for(Copy c:fixtures) {
            fixture(c.copyId());if(c.providerOperation()!=null){fixture(c.providerOperation());operations.add(c.providerOperation());}
            if(!scope.equals(c.scope())||copies.putIfAbsent(c.identity(),c)!=null)throw new IllegalArgumentException("FIXTURE_SCOPE");
        }
    }
    public synchronized void authoritative(boolean value){complete=value;}
    public synchronized void loseNextAcknowledgement(){loseNextAcknowledgement=true;}
    public synchronized int deleteCalls(){return deleteCalls;}
    private void scope(Scope requested){if(!scope.equals(requested))throw new IllegalArgumentException("FIXTURE_SCOPE");}
    public synchronized Page inventory(Scope requested,String cursor) {
        scope(requested);int start=cursor==null?0:Integer.parseInt(cursor);var all=new ArrayList<>(copies.values());
        if(start<0||start>all.size())throw new IllegalArgumentException("CURSOR");int end=Math.min(start+pageSize,all.size());
        return new Page(VERSION,Proof.SIMULATED,scope,"sim-frozen-inventory",all.subList(start,end),List.copyOf(operations),end<all.size()?Integer.toString(end):null,complete,now);
    }
    public synchronized Copy inspect(Scope requested,String copyId,String version){scope(requested);return copies.get(copyId+"/"+version);}
    public synchronized Receipt delete(Delete request)throws Exception {
        scope(request.copy().scope());if(request.proof()!=Proof.SIMULATED)throw new IllegalArgumentException("SIMULATION_REQUIRED");
        Delete prior=requests.get(request.key());if(prior!=null){if(!prior.equals(request))throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");return receipts.get(request.key());}
        deleteCalls++;Copy requested=request.copy(),current=copies.get(requested.identity());
        Outcome outcome=current==null?Outcome.ALREADY_ABSENT:current.retained(now)?Outcome.RETAINED:Outcome.ACKNOWLEDGED;
        if(current!=null&&!current.equals(requested))throw new IllegalArgumentException("EXACT_COPY_MISMATCH");
        if(outcome==Outcome.ACKNOWLEDGED)copies.remove(current.identity());
        Receipt receipt=new Receipt(VERSION,Proof.SIMULATED,request.key(),scope,requested.copyId(),requested.version(),requested.sha256(),outcome,
            outcome==Outcome.ACKNOWLEDGED?"sim-receipt-"+deleteCalls:null,now);
        requests.put(request.key(),request);receipts.put(request.key(),receipt);
        if(loseNextAcknowledgement){loseNextAcknowledgement=false;throw new java.io.IOException("SIMULATED_ACKNOWLEDGEMENT_LOSS");}
        return receipt;
    }
    public synchronized Optional<Receipt> recover(Scope requested,String originalKey){scope(requested);return Optional.ofNullable(receipts.get(originalKey));}
}
