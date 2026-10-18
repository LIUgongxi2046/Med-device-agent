package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;

/** Original-service reconciliation of actual facts, never a dispatch or application-user impersonation. */
public final class SimulationResultPersistenceService {
    private final SimulationInvocationService ledger;private final SimulationResultStore store;
    private final SimulationResultSchema schema;private final SimulationResultTransport transport;
    SimulationResultPersistenceService(SimulationInvocationService ledger,SimulationResultStore store,SimulationResultSchema schema,SimulationResultTransport transport) {
        this.ledger=Objects.requireNonNull(ledger);this.store=Objects.requireNonNull(store);this.schema=Objects.requireNonNull(schema);this.transport=Objects.requireNonNull(transport);
    }
    public SimulationInvocationService.View persist(HttpServletRequest http,String tenant,String invocation)throws Exception {
        var work=transaction(http,tenant,invocation);
        if("COMPLETED".equals(str(work.operation(),"status")))return ledger.originalFactTransaction(http,tenant,invocation,(request,row)->store.finish(store.prepare(request,row),row));
        for(int index=0;index<2;index++) {
            final int position=index;
            var started=ledger.originalFactTransaction(http,tenant,invocation,(request,row)->{
                var current=store.prepare(request,row);if("COMPLETED".equals(str(current.operation(),"status")))return new Attempt(current,true);
                boolean recover=current.command().items().get(position).writing();
                return new Attempt(store.save(current,schema.writing(current.command(),position),"WRITING"),recover);
            });
            if("COMPLETED".equals(str(started.work().operation(),"status")))break;
            var item=started.work().command().items().get(position);
            // The command is committed before I/O; a crash after marking WRITING can only recover, never write again.
            var reference=transport.write(tenant,started.work().actor(),item.intent(),schema.bytes(started.work().payload(),position==0?"result":"provenance"),started.recover(),
                ()->ThrowingSupplier.of(()->{var current=transaction(http,tenant,invocation);requireSame(started.work(),current,position);return true;}).get());
            SimulationResultSchema.requireReference(item.intent(),reference);
            ledger.originalFactTransaction(http,tenant,invocation,(request,row)->{
                var current=store.prepare(request,row);requireSame(started.work(),current,position);
                if("COMPLETED".equals(str(current.operation(),"status"))){store.verifyCompleted(current);return null;}
                var saved=current.command().items().get(position).reference();
                if(saved!=null&&!saved.equals(reference))SimulationResultSchema.mismatch();
                var command=schema.verified(current.command(),position,reference);
                store.save(current,command,command.items().stream().allMatch(i->i.reference()!=null)?"VERIFIED":"WRITING");return null;
            });
        }
        return ledger.originalFactTransaction(http,tenant,invocation,(request,row)->store.finish(store.prepare(request,row),row));
    }
    private SimulationResultStore.Work transaction(HttpServletRequest http,String tenant,String invocation)throws Exception {
        return ledger.originalFactTransaction(http,tenant,invocation,store::prepare);
    }
    private void requireSame(SimulationResultStore.Work expected,SimulationResultStore.Work current,int position)throws Exception {
        if(!str(expected.operation(),"fact_id").equals(str(current.operation(),"fact_id"))||!str(expected.operation(),"payload_sha256").equals(str(current.operation(),"payload_sha256"))
            ||!expected.command().items().get(position).intent().equals(current.command().items().get(position).intent())
            ||!expected.command().items().get(position).artifactId().equals(current.command().items().get(position).artifactId()))SimulationResultSchema.mismatch();
    }
    private record Attempt(SimulationResultStore.Work work,boolean recover) { }
}
