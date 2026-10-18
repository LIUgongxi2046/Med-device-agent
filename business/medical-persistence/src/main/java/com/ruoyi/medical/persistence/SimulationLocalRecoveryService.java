package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original-service receipt recovery, never a lease renewal, dispatch or user-content read endpoint. */
public final class SimulationLocalRecoveryService {
    private final SimulationInvocationService ledger;
    private final SimulationLocalProcessBridge bridge;
    private final SimulationInvocationSchema schema;
    private final SimulationResultPersistenceService results;
    public SimulationLocalRecoveryService(SimulationInvocationService ledger, SimulationLocalProcessBridge bridge,
            SimulationInvocationSchema schema) {
        this(ledger, bridge, schema, null);
    }
    public SimulationLocalRecoveryService(SimulationInvocationService ledger, SimulationLocalProcessBridge bridge,
            SimulationInvocationSchema schema, SimulationResultPersistenceService results) {
        this.ledger = Objects.requireNonNull(ledger); this.bridge = Objects.requireNonNull(bridge);
        this.schema = Objects.requireNonNull(schema);
        this.results = results;
    }
    public SimulationInvocationService.Fact reconcile(HttpServletRequest http, String tenant, String invocation) throws Exception {
        WireValues.uuid(tenant); WireValues.uuid(invocation);
        String original = ledger.originalFactTransaction(http, tenant, invocation,
            (request, stored) -> schema.canonical(request));
        // Only successful completion of the original-service transaction can create this private permit.
        var recovered = bridge.reconcile(new RecoveryPermit(original));
        var fact = ledger.recordLateFact(http, tenant, invocation, recovered.resultBytes(), recovered.provenanceBytes());
        if (results == null) return fact;
        results.persist(http, tenant, invocation);
        // Exact terminal receipt replay proves settlement; never infer it merely from a local journal.
        return ledger.recordLateFact(http, tenant, invocation, recovered.resultBytes(), recovered.provenanceBytes());
    }
    public static final class RecoveryPermit {
        private final String canonical; private final AtomicBoolean used = new AtomicBoolean();
        private RecoveryPermit(String canonical) { this.canonical = canonical; }
        String consume() {
            if (!used.compareAndSet(false, true)) throw new IllegalStateException("SIMULATION_RECOVERY_ALREADY_CONSUMED");
            return canonical;
        }
        @Override public String toString() { return "SimulationRecoveryPermit[redacted,read-only]"; }
    }
}
