package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.persistence.erasure.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Explicit registered medical-external-erasure/1 authorities. Empty by default. A configured
 * provider asserts coverage of operation IDs even after erasure; surviving copies are separate.
 * SIMULATED bindings can be exercised but are never production scope authority. */
@Component
public final class ExternalErasureRegistry {
    public record Configuration(String scopeId,Kind kind,String revision,Proof proof,String endpoint,
                                String credentialProperty,String journalDirectory) {}
    public record Binding(Configuration configuration,Api api,Journal journal) {}
    private final Map<String,Binding> bindings;
    final ObjectMapper mapper=JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    @org.springframework.beans.factory.annotation.Autowired
    public ExternalErasureRegistry(Environment environment)throws Exception {
        Configuration[] configured=mapper.readValue(environment.getProperty("medical.external-erasure.authorities","[]"),Configuration[].class);
        var values=new LinkedHashMap<String,Binding>();
        if(configured.length>100)throw new IllegalArgumentException("AUTHORITY_LIMIT");
        for(Configuration c:configured) {
            new Scope("configuration","source",c.scopeId(),c.kind(),c.revision());
            Objects.requireNonNull(c.proof());
            if(c.credentialProperty()==null || !c.credentialProperty().startsWith("medical.external-erasure.credentials."))
                throw new IllegalArgumentException("DEDICATED_AUTHORITY_CREDENTIAL_REQUIRED");
            var api=new ExternalErasureHttpApi(URI.create(c.endpoint()),environment.getProperty(c.credentialProperty()),c.proof());
            var binding=new Binding(c,api,new ExternalErasureFileJournal(Path.of(c.journalDirectory())));
            if(values.putIfAbsent(c.scopeId(),binding)!=null)throw new IllegalArgumentException("DUPLICATE_AUTHORITY");
        }
        bindings=Map.copyOf(values);
    }
    /** Explicit dependency injection for isolated application fixtures; never automatic mock fallback. */
    public ExternalErasureRegistry(List<Binding> fixtures) {
        var values=new LinkedHashMap<String,Binding>();
        for(Binding b:fixtures) {
            if(b.configuration().proof()!=Proof.SIMULATED)throw new IllegalArgumentException("FIXTURE_MUST_BE_SIMULATED");
            if(values.putIfAbsent(b.configuration().scopeId(),b)!=null)throw new IllegalArgumentException("DUPLICATE_AUTHORITY");
        }bindings=Map.copyOf(values);
    }
    Binding binding(String scope) {Binding value=bindings.get(scope);if(value==null)throw new IllegalStateException("UNSUPPORTED_AUTHORITY");return value;}
    JsonNode topology() {
        var result=mapper.createArrayNode();bindings.values().stream().sorted(Comparator.comparing(b->b.configuration().scopeId())).forEach(b->{
            var c=b.configuration();result.addObject().put("scope_id",c.scopeId()).put("kind",c.kind().name()).put("revision",c.revision())
                .put("proof",c.proof().name()).put("protocol","medical-external-erasure/1").put("endpoint",c.endpoint());
        });return result;
    }
    Approval approval(String tenant,String source,String scope,String id,String snapshot,String declarer,String approver,Instant until) {
        var c=binding(scope).configuration();return new Approval(id,new Scope(tenant,source,scope,c.kind(),c.revision()),snapshot,declarer,approver,until,c.proof());
    }
    ExternalErasureAdapter adapter(Approval approval,Authority authority) {
        var b=binding(approval.scope().scopeId());
        if(b.configuration().proof()!=approval.proof())throw new IllegalStateException("PROOF_MISMATCH");
        return new ExternalErasureAdapter(b.configuration().kind(),b.api(),b.journal(),authority,10000);
    }
    ObjectNode observe(JsonNode scopes,String tenant,String source,String id,String declarer,String approver,
                       Instant until,Authority authority,Set<String> knownOperations,boolean requireAbsent) throws Exception {
        var result=mapper.createObjectNode().put("complete",true);var observations=result.putArray("scopes");
        var covered=new HashSet<String>();var declared=new HashSet<String>();
        for(JsonNode scope:scopes) {
            String scopeId=scope.path("scope_id").asText();declared.add(scopeId);
            Binding b=binding(scopeId);
            if(!b.configuration().kind().name().equals(scope.path("kind").asText()) || b.configuration().proof()!=Proof.AUTHORITATIVE)
                throw new IllegalStateException("REAL_AUTHORITY_REQUIRED");
            Approval approval=approval(tenant,source,scopeId,id,"unobserved",declarer,approver,until);
            Inventory inventory=adapter(approval,authority).inventory(approval);
            if(requireAbsent && !inventory.copies().isEmpty())throw new IllegalStateException("SURVIVING_EXTERNAL_COPIES");
            if(b.configuration().kind()==Kind.PROVIDER)covered.addAll(inventory.coveredOperations());
            var observed=observations.addObject().put("scope_id",scopeId).put("proof",Proof.AUTHORITATIVE.name());
            observed.set("inventory",mapper.valueToTree(inventory));
        }
        // Every registered deployment scope must be declared. A caller cannot omit a configured archive.
        if(!declared.equals(bindings.keySet()) || !covered.containsAll(knownOperations))
            throw new IllegalStateException("EXTERNAL_COVERAGE_INCOMPLETE");
        return result;
    }
}
