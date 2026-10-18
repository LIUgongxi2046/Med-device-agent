package com.ruoyi.medical.persistence;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

/** Connects genuine ledger admission to one local dispatch and original-service fact ingestion. */
public final class SimulationEvaluationDispatchService {
    private final SimulationInvocationService ledger;
    private final SimulationLocalProcessBridge bridge;
    private final SimulationInvocationSchema schema;
    private final SimulationResultPersistenceService results;
    public SimulationEvaluationDispatchService(SimulationInvocationService ledger,
            SimulationLocalProcessBridge bridge, SimulationInvocationSchema schema) {
        this(ledger,bridge,schema,null);
    }
    public SimulationEvaluationDispatchService(SimulationInvocationService ledger,SimulationLocalProcessBridge bridge,SimulationInvocationSchema schema,SimulationResultPersistenceService results) {
        this.results=results;
        this.ledger = Objects.requireNonNull(ledger); this.bridge = Objects.requireNonNull(bridge);
        this.schema = Objects.requireNonNull(schema);
    }
    public Result execute(HttpServletRequest http, byte[] input) throws Exception {
        var request = schema.request(input);
        // Both ledger methods authenticate the current worker/token and actual source bytes.
        ledger.reserve(http, input);
        var dispatch = ledger.beginSend(http, input);
        if (dispatch.permit() == null) return new Result(dispatch.invocation(), null);
        SimulationLocalProcessBridge.LocalFact fact;
        try {
            fact = bridge.execute(dispatch.permit());
        } catch (SimulationLocalProcessBridge.BridgeFailure failure) {
            // Failure to observe (e.g. concurrent revocation) leaves DISPATCHING ambiguous, not retryable.
            try { ledger.observe(http, input, "UNKNOWN"); } catch (Exception observationFailure) { /* no reset/release */ }
            throw failure;
        }
        var identity = request.required("binding").required("identity");
        var stored = ledger.recordLateFact(http, identity.path("tenant_id").textValue(),
            identity.path("invocation_id").textValue(), fact.resultBytes(), fact.provenanceBytes());
        var current=results==null?dispatch.invocation():results.persist(http,identity.path("tenant_id").textValue(),identity.path("invocation_id").textValue());
        return new Result(current, results==null?stored:new SimulationInvocationService.Fact(stored.invocationId(),stored.receiptId(),stored.replay(),"RECORDED_SETTLED"));
    }
    /** A fact alone is unfinalized; enabled result persistence returns the reread canonical state. */
    public record Result(SimulationInvocationService.View invocation, SimulationInvocationService.Fact fact) { }
}
