package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.util.function.ThrowingSupplier;

/** Durable admission and dispatch ownership. No transport or result artifact is fabricated here. */
public final class SimulationInvocationService {
    private final EnterpriseAccess access;
    private final TenantTransaction transactions;
    private final RunTokenAuthorityStore epochs;
    private final SimulationInvocationStore store;
    private final SimulationInvocationSchema schema;
    private final SimulationInvocationAuthority authority;
    private final SimulationInvocationSources sources;
    private final ServiceCertificateIdentity services;
    private SimulationWorkerAccess workers;
    public SimulationInvocationService(DataSource data,EnterpriseAccess access,TenantTransaction transactions,
            SimulationInvocationSchema schema,SimulationExecutionPermissionPolicy policy,PermissionMapper permissions,
            PermissionAuthority grants,SimulationInvocationSources sources,ServiceCertificateIdentity services,SimulationRunTokenVerifier tokens,SimulationEvaluationCandidateAuthority candidates) {
        this.access=Objects.requireNonNull(access); this.transactions=Objects.requireNonNull(transactions); this.schema=Objects.requireNonNull(schema);
        this.sources=Objects.requireNonNull(sources); this.services=Objects.requireNonNull(services);
        store=new SimulationInvocationStore(data,schema); epochs=new RunTokenAuthorityStore(data);
        authority=new SimulationInvocationAuthority(store,schema,policy,permissions,grants,tokens,candidates);
    }
    public SimulationInvocationService(DataSource data,EnterpriseAccess access,TenantTransaction transactions,
            SimulationInvocationSchema schema,SimulationExecutionPermissionPolicy policy,PermissionMapper permissions,
            PermissionAuthority grants,SimulationInvocationSources sources,ServiceCertificateIdentity services,SimulationRunTokenVerifier tokens,
            SimulationEvaluationCandidateAuthority candidates,SimulationWorkerAccess workers) {
        this(data,access,transactions,schema,policy,permissions,grants,sources,services,tokens,candidates);
        this.workers=Objects.requireNonNull(workers);
    }
    private final class Actor {
        private final ApplicationSessionIdentity.Verified subject;
        private final HttpServletRequest http;
        private final String tenant,run;
        Actor(ApplicationSessionIdentity.Verified subject){this.subject=Objects.requireNonNull(subject);http=null;tenant=null;run=null;}
        Actor(HttpServletRequest http,byte[] bytes) throws Exception {
            if(workers==null) denied(); this.subject=null;this.http=http;
            var proof=schema.request(bytes).required("authority_proof"); tenant=text(proof,"tenant_id");run=text(proof,"parent_run_id");
        }
        <T>T execute(java.util.function.Function<EnterpriseAccess.Admission,T> operation){
            return subject!=null?access.execute(subject,operation):workers.execute(http,tenant,run,operation);
        }
        SimulationInvocationSources.VerifiedSources prepare(HttpServletRequest request,JsonNode body,ServiceCertificateIdentity.Verified service) throws Exception {
            if(subject!=null)return sources.prepare(subject,body);
            execute(admission -> ThrowingSupplier.of(() -> {authority.require(request,admission,body,service);return true;}).get());
            return sources.prepare(workers,http,tenant,run,body,admission -> ThrowingSupplier.of(() -> {authority.require(request,admission,body,service);return true;}).get());
        }
    }
    SimulationInvocationService(EnterpriseAccess access,SimulationInvocationStore store,SimulationInvocationSchema schema,
            SimulationInvocationAuthority authority,SimulationInvocationSources sources,ServiceCertificateIdentity services) {
        this.access=access; this.store=store; this.schema=schema; this.authority=authority; this.sources=sources; this.services=services;
        this.transactions=null; this.epochs=null;
    }
    SimulationInvocationService(EnterpriseAccess access,SimulationInvocationStore store,SimulationInvocationSchema schema,
            SimulationInvocationAuthority authority,SimulationInvocationSources sources,ServiceCertificateIdentity services,SimulationWorkerAccess workers) {
        this(access,store,schema,authority,sources,services);this.workers=Objects.requireNonNull(workers);
    }
    public View reserve(HttpServletRequest http,ApplicationSessionIdentity.Verified subject,byte[] bytes) throws Exception {
        return reserve(http,new Actor(subject),bytes);
    }
    public View reserve(HttpServletRequest http,byte[] bytes) throws Exception {
        return reserve(http,new Actor(http,bytes),bytes);
    }
    private View reserve(HttpServletRequest http,Actor actor,byte[] bytes) throws Exception {
        var service=services.require(http); JsonNode request=schema.request(bytes);
        var verified=actor.prepare(http,request,service);
        return actor.execute(admission -> ThrowingSupplier.of(() -> {
            var checked=authority.require(http,admission,request,service); sources.require(admission,request,request.required("authority_proof"),verified);
            var id=request.required("binding").required("identity"); var prior=store.invocationForAction(id);
            if(!prior.isEmpty()) { var stored=single(prior); replay(stored,request); recheck(http,service); return view(stored); }
            if(!"BOUND".equals(str(checked.action(),"state"))||checked.action().get("invocation_id")!=null) throw new TenantAccessException(ErrorCode.ACTION_STALE);
            long units=units(request); var proof=request.required("authority_proof");
            // Fixed lock order serializes enterprise accounting across distinct parent runs.
            var enterprise=store.account(text(proof,"enterprise_account_id")); var parent=store.account(text(proof,"parent_account_id"));
            authority.account(admission,proof,enterprise,true,units); authority.account(admission,proof,parent,false,units);
            store.reserveAccount(enterprise,units); store.reserveAccount(parent,units);
            store.reserve(request,checked.configId(),admission.actorId(),checked.classification(),units);
            authority.require(http,admission,request,service); sources.require(admission,request,proof,verified); recheck(http,service);
            return view(store.invocation(text(id,"invocation_id")));
        }).get());
    }
    /** Permission is constructed only after TransactionTemplate has committed successfully. */
    public Dispatch beginSend(HttpServletRequest http,ApplicationSessionIdentity.Verified subject,byte[] bytes) throws Exception {
        return beginSend(http,new Actor(subject),bytes);
    }
    public Dispatch beginSend(HttpServletRequest http,byte[] bytes) throws Exception {
        return beginSend(http,new Actor(http,bytes),bytes);
    }
    private Dispatch beginSend(HttpServletRequest http,Actor actor,byte[] bytes) throws Exception {
        var service=services.require(http); JsonNode request=schema.request(bytes); var verified=actor.prepare(http,request,service);
        Committed committed=actor.execute(admission -> ThrowingSupplier.of(() -> {
            var checked=authority.require(http,admission,request,service); var proof=request.required("authority_proof");
            sources.require(admission,request,proof,verified);
            var stored=store.invocation(text(request.required("binding").required("identity"),"invocation_id")); replay(stored,request);
            // A reader of DISPATCHING/UNKNOWN never acquires permission, even after a lost response.
            if(!"RESERVED".equals(str(stored,"status"))) { recheck(http,service); return new Committed(view(stored),false); }
            var enterprise=store.account(text(proof,"enterprise_account_id")); var parent=store.account(text(proof,"parent_account_id"));
            authority.account(admission,proof,enterprise,true,0); authority.account(admission,proof,parent,false,0);
            for(var account:List.of(enterprise,parent)) if(count(account,"reserved_units")<units(request)||count(account,"reserved_calls")<1) denied();
            requireHeld(request);
            boolean won=store.dispatch(stored,request,admission.actorId(),checked.classification());
            authority.require(http,admission,request,service); sources.require(admission,request,proof,verified); recheck(http,service);
            return new Committed(view(store.invocation(str(stored,"invocation_id"))),won);
        }).get());
        return new Dispatch(committed.view(),committed.winner()?new DispatchPermit(schema.canonical(request)):null);
    }
    public View cancel(HttpServletRequest http,ApplicationSessionIdentity.Verified subject,byte[] bytes) throws Exception {
        return cancel(http,new Actor(subject),bytes);
    }
    public View cancel(HttpServletRequest http,byte[] bytes) throws Exception {
        return cancel(http,new Actor(http,bytes),bytes);
    }
    private View cancel(HttpServletRequest http,Actor actor,byte[] bytes) throws Exception {
        var service=services.require(http); var request=schema.request(bytes);
        return actor.execute(admission -> ThrowingSupplier.of(() -> {
            var checked=authority.require(http,admission,request,service); var proof=request.required("authority_proof");
            var stored=store.invocation(text(request.required("binding").required("identity"),"invocation_id")); replay(stored,request);
            String state=str(stored,"status");
            if("RESERVED".equals(state)) {
                store.account(text(proof,"enterprise_account_id")); store.account(text(proof,"parent_account_id")); requireHeld(request);
                store.release(request,units(request)); store.observe(stored,request,"CANCELLED",admission.actorId(),checked.classification());
            } else if(List.of("DISPATCHING","IN_FLIGHT","UNKNOWN").contains(state)) {
                store.observe(stored,request,"CANCEL_REQUESTED",admission.actorId(),checked.classification());
            }
            recheck(http,service); return view(store.invocation(str(stored,"invocation_id")));
        }).get());
    }
    public View read(HttpServletRequest http,ApplicationSessionIdentity.Verified subject,byte[] bytes) throws Exception {
        return read(http,new Actor(subject),bytes);
    }
    public View read(HttpServletRequest http,byte[] bytes) throws Exception {
        return read(http,new Actor(http,bytes),bytes);
    }
    private View read(HttpServletRequest http,Actor actor,byte[] bytes) throws Exception {
        var service=services.require(http); var request=schema.request(bytes);
        return actor.execute(admission -> ThrowingSupplier.of(() -> {
            authority.require(http,admission,request,service);
            var stored=store.invocation(text(request.required("binding").required("identity"),"invocation_id")); replay(stored,request);
            recheck(http,service); return view(stored);
        }).get());
    }
    /** An observation can only preserve a dispatch; it can never create/reset one. */
    public View observe(HttpServletRequest http,ApplicationSessionIdentity.Verified subject,byte[] bytes,String state) throws Exception {
        return observe(http,new Actor(subject),bytes,state);
    }
    public View observe(HttpServletRequest http,byte[] bytes,String state) throws Exception {
        return observe(http,new Actor(http,bytes),bytes,state);
    }
    private View observe(HttpServletRequest http,Actor actor,byte[] bytes,String state) throws Exception {
        if(!List.of("IN_FLIGHT","UNKNOWN").contains(state)) throw new IllegalArgumentException("SIMULATION_OBSERVATION_INVALID");
        var service=services.require(http); var request=schema.request(bytes);
        return actor.execute(admission -> ThrowingSupplier.of(() -> {
            var checked=authority.require(http,admission,request,service);
            var stored=store.invocation(text(request.required("binding").required("identity"),"invocation_id")); replay(stored,request);
            String current=str(stored,"status");
            if("DISPATCHING".equals(current)||"IN_FLIGHT".equals(current)&&"UNKNOWN".equals(state))
                store.observe(stored,request,state,admission.actorId(),checked.classification());
            recheck(http,service); return view(store.invocation(str(stored,"invocation_id")));
        }).get());
    }
    /** Independent original-service fact ingestion remains possible after user grant/lease revocation.
     * New facts remain unsettled. Terminal replay additionally requires the actual settlement receipt. */
    public Fact recordLateFact(HttpServletRequest http,String tenant,String invocation,byte[] result,byte[] provenance) throws Exception {
        return originalFactTransaction(http,tenant,invocation,(request,stored) -> {
            var payload=schema.receipt(request,result,provenance);
            authority.requireFactRetention(request,schema.canonical(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            boolean terminal=List.of("SUCCEEDED","FAILED").contains(str(stored,"status"));
            if(terminal) {store.requireFact(request,payload);store.requireSettledFact(request,payload,stored);}
            boolean inserted=terminal?false:store.fact(request,payload,str(stored,"created_by"),str(stored,"classification"));
            return new Fact(invocation,payload.path("receipt_id").textValue(),!inserted,
                terminal?"RECORDED_SETTLED":"RECORDED_UNSETTLED");
        });
    }
    /** Original service only. Does not grant dispatch, source reading, or user artifact access. */
    <T>T originalFactTransaction(HttpServletRequest http,String tenant,String invocation,FactOperation<T> operation) throws Exception {
        var service=services.require(http); var owner=store.factOwner(tenant,invocation);
        if(!service.serviceUri().equals(str(owner,"service_uri"))) denied();
        var watermark=epochs.epochs(tenant); String actor=str(owner,"created_by");
        try(var scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ZERO,watermark.authEpoch(),watermark.governanceEpoch()))) {
            return transactions.execute(current -> ThrowingSupplier.of(() -> {
                var stored=store.invocation(invocation); var request=storedRequest(invocation); var proof=request.required("authority_proof");
                if(!actor.equals(text(proof,"actor_id"))||!service.serviceUri().equals(text(proof,"service_id"))
                    ||!tenant.equals(text(proof,"tenant_id"))||count(stored,"dispatch_generation")!=1
                    ||!List.of("DISPATCHING","IN_FLIGHT","UNKNOWN","CANCEL_REQUESTED","SUCCEEDED","FAILED").contains(str(stored,"status"))) denied();
                replay(stored,request); requireOriginalAction(stored,request);
                authority.requireFactRetention(request,store.retainedFactBytes(invocation));
                T result=operation.apply(request,stored); authority.requireFactRetention(request,store.retainedFactBytes(invocation)); recheck(http,service); return result;
            }).get());
        }
    }
    private void requireOriginalAction(Map<String,Object> invocation,JsonNode request) {
        var id=request.required("binding").required("identity");var action=store.action(id);var proof=request.required("authority_proof");
        for(String field:List.of("run_id","step_id","action_id")) if(!text(id,field).equals(str(action,field))||!text(id,field).equals(str(action,"origin_"+field))) denied();
        if(!Objects.equals(nullable(id,"project_id"),str(action,"project_id"))||!text(id,"invocation_id").equals(str(action,"invocation_id"))
            ||!"MODEL".equals(str(action,"kind"))||!text(proof,"input_sha256").equals(str(action,"input_sha256"))
            ||!text(proof,"parameters_sha256").equals(str(action,"arguments_sha256"))||!text(id,"model_config_revision").equals(str(action,"tool_version"))
            ||count(action,"lease_generation")!=proof.path("lease_generation").longValue()||!str(invocation,"status").equals(str(action,"state"))) denied();
    }
    @FunctionalInterface interface FactOperation<T> { T apply(JsonNode request,Map<String,Object> invocation) throws Exception; }
    private JsonNode storedRequest(String invocation) throws Exception {
        var stored=store.provenance(invocation); var authority=store.authority(str(stored,"authority_id"));
        ObjectNode request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(); request.put("schema_version","1.0.0");
        request.set("authority_proof",schema.parse(str(authority,"proof")));
        for(String field:List.of("binding","exact_input","request_parameters","simulation_case","settings")) request.set(field,schema.parse(str(stored,field)));
        if(!schema.hash(request.required("authority_proof")).equals(str(authority,"proof_sha256"))) SimulationInvocationSchema.mismatch();
        return schema.request(schema.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void replay(Map<String,Object> stored,JsonNode request) throws Exception {
        var id=request.required("binding").required("identity");
        if(!"SIMULATION".equals(str(stored,"execution_mode"))||!Objects.equals(nullable(id,"project_id"),str(stored,"project_id"))) denied();
        for(String field:List.of("invocation_id","run_id","step_id","action_id","context_manifest_id")) if(!text(id,field).equals(str(stored,field))) SimulationInvocationSchema.mismatch();
        if(!text(id,"model_config_revision").equals(str(stored,"config_revision"))||!request.required("authority_proof").path("input_sha256").textValue().equals(str(stored,"input_sha256"))) SimulationInvocationSchema.mismatch();
        var fixed=store.provenance(str(stored,"invocation_id"));
        if(!schema.hash(request.required("binding")).equals(str(fixed,"binding_sha256"))||!schema.manifest().equals(str(fixed,"schema_manifest_sha256"))) SimulationInvocationSchema.mismatch();
        if(!schema.canonical(request).equals(schema.canonical(storedRequest(str(stored,"invocation_id"))))) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
    private void requireHeld(JsonNode request) { store.requireHeld(request,units(request)); }
    private static long units(JsonNode request) { return SimulationInvocationSchema.sum(request.required("binding").required("exact_input").path("units").longValue(),request.required("authority_proof").path("maximum_output_units").longValue()); }
    private void recheck(HttpServletRequest http,ServiceCertificateIdentity.Verified expected) throws Exception {
        var actual=services.require(http); if(!expected.serviceUri().equals(actual.serviceUri())||!expected.certificateSha256().equals(actual.certificateSha256())||!expected.trustRevision().equals(actual.trustRevision())) denied();
    }
    static View view(Map<String,Object> row) { return new View(str(row,"invocation_id"),str(row,"status"),count(row,"dispatch_generation"),str(row,"revision")); }
    public record View(String invocationId,String status,long dispatchGeneration,String revision) { }
    public record Fact(String invocationId,String receiptId,boolean replay,String status) { }
    public record Dispatch(View invocation,DispatchPermit permit) { }
    private record Committed(View view,boolean winner) { }
    /** Process-local and nonserializable: no token can be copied to regain dispatch permission. */
    public static final class DispatchPermit {
        private final String canonicalRequest; private final AtomicBoolean used=new AtomicBoolean();
        private DispatchPermit(String canonicalRequest) { this.canonicalRequest=canonicalRequest; }
        public String consume() { if(!used.compareAndSet(false,true)) throw new IllegalStateException("SIMULATION_DISPATCH_ALREADY_CONSUMED"); return canonicalRequest; }
        @Override public String toString() { return "SimulationDispatchPermit[redacted]"; }
    }
}
