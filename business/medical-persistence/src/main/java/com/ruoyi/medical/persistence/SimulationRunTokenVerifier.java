package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.RunTokenPolicyStore;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Verifies a restricted token; never issues a token or invokes production RunTokenAuthority. */
public final class SimulationRunTokenVerifier {
    public static final String AUDIENCE="urn:medical:simulation-evaluation";
    private final RunTokenPolicyStore policies;
    private final RunTokenAuthorityStore tokens;
    private final SimulationInvocationSchema schema;
    public SimulationRunTokenVerifier(DataSource data,RunTokenPolicyStore policies,SimulationInvocationSchema schema) throws Exception {
        this(new RunTokenAuthorityStore(data),policies,schema);
    }
    SimulationRunTokenVerifier(RunTokenAuthorityStore tokens,RunTokenPolicyStore policies,SimulationInvocationSchema schema) throws Exception {
        this.policies=Objects.requireNonNull(policies); this.tokens=tokens; this.schema=schema;
        if(!AUDIENCE.equals(policies.read().policy().audience())) throw new IllegalArgumentException("SIMULATION_TOKEN_AUDIENCE_REQUIRED");
    }
    void require(HttpServletRequest request,EnterpriseAccess.Admission admission,JsonNode proof,String principal,
            ServiceCertificateIdentity.Verified service) throws Exception {
        var headers=request.getHeaders("X-Simulation-Run-Token");
        if(headers==null||!headers.hasMoreElements()) denied();
        String raw=headers.nextElement(); if(headers.hasMoreElements()) denied();
        var verified=policies.verify(raw); var policy=policies.read();
        if(!AUDIENCE.equals(policy.policy().audience())||!AUDIENCE.equals(verified.audience())
            ||!verified.actionScope().equals(List.of(SimulationExecutionPermissionPolicy.ACTION))) denied();
        String actor=admission.actorId(); var token=tokens.token(actor,proof.path("token_jti").textValue());
        if(!verified.tenantId().equals(admission.context().tenant_id())||!verified.initiatorId().equals(actor)
            ||!Objects.equals(verified.projectId(),SimulationInvocationStore.nullable(proof,"project_id"))
            ||!verified.runId().equals(proof.path("parent_run_id").textValue())||!Objects.equals(verified.stepId(),proof.path("step_id").textValue())
            ||!verified.authEpoch().equals(admission.watermarks().authEpoch())||!verified.jti().equals(proof.path("token_jti").textValue())
            ||!verified.jti().equals(token.jti())||!principal.equals(token.principal())||!actor.equals(token.creator())
            ||!verified.runId().equals(token.run())||!Objects.equals(verified.stepId(),token.step())||!Objects.equals(verified.projectId(),token.project())
            ||!AUDIENCE.equals(token.audience())||!verified.authEpoch().equals(token.auth())||token.revoked()!=null
            ||!verified.tokenSha256().equals(token.sha256())||!service.serviceUri().equals(token.service())
            ||!policy.revision().equals(token.policyRevision())||!policy.contentSha256().equals(token.policySha256())
            ||!schema.manifest().equals(token.manifest())||!"1.0.0".equals(token.schema())
            ||token.generation()==null||!token.generation().toString().equals(proof.path("lease_generation").asText())
            ||!schema.canonical(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(verified.actionScope())).equals(token.actions())
            ||!LocalDateTime.ofInstant(verified.issuedAt(),ZoneOffset.UTC).equals(token.issued())
            ||!LocalDateTime.ofInstant(verified.expiresAt(),ZoneOffset.UTC).equals(token.expires())
            ||!token.expires().equals(PermissionAuthority.time(proof.path("expires_at").textValue()))
            ||!token.issued().equals(PermissionAuthority.time(proof.path("issued_at").textValue()))) denied();
    }
    private static void denied() { throw new TenantAccessException(com.ruoyi.medical.contracts.ErrorCode.ACCESS_DENIED); }
}
