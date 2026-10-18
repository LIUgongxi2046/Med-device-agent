package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.identity.RunTokenPolicyStore;
import com.ruoyi.medical.identity.RunTokenSigningKeyStore;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Signs only the separate simulation audience using its protected, policy-bound private key. */
final class SimulationEvaluationTokenIssuer {
    private final RunTokenPolicyStore policies;
    private final RunTokenSigningKeyStore signing;
    SimulationEvaluationTokenIssuer(RunTokenPolicyStore policies,RunTokenSigningKeyStore signing) throws Exception {
        this.policies=Objects.requireNonNull(policies);this.signing=Objects.requireNonNull(signing);requireSimulation(policies.read());
    }
    Issued issue(String tenant,String project,String actor,String run,String step,String jti,BigInteger auth,
            LocalDateTime databaseTime,LocalDateTime leaseExpiry,Duration lifetime) throws Exception {
        var policy=policies.read();requireSimulation(policy);
        if(lifetime==null||lifetime.isZero()||lifetime.isNegative()||lifetime.getNano()!=0
                ||lifetime.compareTo(policy.policy().maximumLifetime())>0) denied();
        var issued=databaseTime.withNano(0);var expires=issued.plus(lifetime);
        if(!expires.isAfter(databaseTime)||expires.isAfter(leaseExpiry)) denied();
        var claims=new ObjectMapper().createObjectNode();
        claims.put("iss",policy.policy().issuer()).put("aud",SimulationRunTokenVerifier.AUDIENCE)
            .put("iat",issued.toEpochSecond(ZoneOffset.UTC)).put("exp",expires.toEpochSecond(ZoneOffset.UTC))
            .put("tenant_id",tenant).put("project_id",project).put("initiator_id",actor).put("run_id",run)
            .put("step_id",step).put("jti",jti).put("auth_epoch",auth.toString());
        claims.putArray("action_scope").add(SimulationExecutionPermissionPolicy.ACTION);
        String raw=signing.sign(claims);var verified=policies.verify(raw);current(policy);
        if(!verified.tenantId().equals(tenant)||!Objects.equals(verified.projectId(),project)||!verified.initiatorId().equals(actor)
            ||!verified.runId().equals(run)||!Objects.equals(verified.stepId(),step)||!verified.jti().equals(jti)
            ||!verified.authEpoch().equals(auth)||!verified.actionScope().equals(List.of(SimulationExecutionPermissionPolicy.ACTION))
            ||!verified.audience().equals(SimulationRunTokenVerifier.AUDIENCE)
            ||!verified.issuedAt().equals(issued.toInstant(ZoneOffset.UTC))||!verified.expiresAt().equals(expires.toInstant(ZoneOffset.UTC)))denied();
        return new Issued(raw,jti,verified.tokenSha256(),issued,expires,policy);
    }
    void recheck(Issued issued) throws Exception { current(issued.policy());policies.verify(issued.raw()); }
    Duration maximumLifetime() throws Exception {var policy=policies.read();requireSimulation(policy);return policy.policy().maximumLifetime();}
    private void current(RunTokenPolicyStore.Current expected) throws Exception {
        var actual=policies.read();requireSimulation(actual);
        if(!expected.revision().equals(actual.revision())||!expected.contentSha256().equals(actual.contentSha256()))denied();
    }
    private static void requireSimulation(RunTokenPolicyStore.Current current) {
        if(!SimulationRunTokenVerifier.AUDIENCE.equals(current.policy().audience()))denied();
    }
    private static void denied(){throw new SecurityException("SIMULATION_TOKEN_ISSUANCE_DENIED");}
    record Issued(String raw,String jti,String tokenSha256,LocalDateTime issuedAt,LocalDateTime expiresAt,RunTokenPolicyStore.Current policy) {
        @Override public String toString(){return "IssuedSimulationToken[redacted]";}
    }
}
