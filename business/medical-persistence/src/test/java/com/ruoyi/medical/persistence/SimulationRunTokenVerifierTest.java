package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.RunTokenPolicyStore;
import com.ruoyi.medical.identity.RunTokenSignature;
import com.ruoyi.medical.identity.ServiceCertificateIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationRunTokenVerifierTest {
    final DataSource data=mock(DataSource.class);
    final RunTokenPolicyStore policies=mock(RunTokenPolicyStore.class);
    final RunTokenPolicyStore.Current current=mock(RunTokenPolicyStore.Current.class);
    final RunTokenSignature.Policy policy=mock(RunTokenSignature.Policy.class);
    final SimulationInvocationSchema schema=new SimulationInvocationSchema();
    SimulationRunTokenVerifierTest()throws Exception { when(policies.read()).thenReturn(current);when(current.policy()).thenReturn(policy); }
    @Test void productionAudienceCannotActivateRestrictedVerifier() {
        when(policy.audience()).thenReturn("urn:medical:production");
        assertThrows(IllegalArgumentException.class,()->new SimulationRunTokenVerifier(data,policies,schema));verifyNoInteractions(data);
    }
    @Test void missingOrRepeatedTokenNeverFallsBackToSessionOrProductionToken()throws Exception {
        when(policy.audience()).thenReturn(SimulationRunTokenVerifier.AUDIENCE);var verifier=new SimulationRunTokenVerifier(data,policies,schema);
        var request=mock(HttpServletRequest.class);
        assertThrows(TenantAccessException.class,()->verifier.require(request,null,null,null,null));
        when(request.getHeaders("X-Simulation-Run-Token")).thenReturn(Collections.enumeration(List.of("one","two")));
        assertThrows(TenantAccessException.class,()->verifier.require(request,null,null,null,null));verify(policies,never()).verify(anyString());verifyNoInteractions(data);
    }
    @Test void broaderOrCrudScopesAreRejectedBeforeAnyDatabaseLookup()throws Exception {
        when(policy.audience()).thenReturn(SimulationRunTokenVerifier.AUDIENCE);var verifier=new SimulationRunTokenVerifier(data,policies,schema);
        var request=mock(HttpServletRequest.class);var token=mock(RunTokenSignature.Verified.class);
        when(policies.verify("token")).thenReturn(token);when(token.audience()).thenReturn(SimulationRunTokenVerifier.AUDIENCE);
        for(var scope:List.of(List.of("evaluation.create"),List.of("model.simulation.execute","task.run"),List.<String>of())) {
            when(request.getHeaders("X-Simulation-Run-Token")).thenReturn(Collections.enumeration(List.of("token")));when(token.actionScope()).thenReturn(scope);
            assertThrows(TenantAccessException.class,()->verifier.require(request,null,null,null,null));
        }
        verifyNoInteractions(data);
    }
    @Test void rotatedPolicyWithWrongAudienceFailsEvenAfterConstruction()throws Exception {
        when(policy.audience()).thenReturn(SimulationRunTokenVerifier.AUDIENCE);var verifier=new SimulationRunTokenVerifier(data,policies,schema);
        when(policy.audience()).thenReturn("urn:medical:production");var request=mock(HttpServletRequest.class);var token=mock(RunTokenSignature.Verified.class);
        when(request.getHeaders("X-Simulation-Run-Token")).thenReturn(Collections.enumeration(List.of("token")));when(policies.verify("token")).thenReturn(token);
        assertThrows(TenantAccessException.class,()->verifier.require(request,null,null,null,null));verifyNoInteractions(data);
    }
    @Test void issuerOrSigningMethodsAreNotExposed() {
        assertTrue(java.util.Arrays.stream(SimulationRunTokenVerifier.class.getDeclaredMethods()).noneMatch(method->List.of("issue","sign","claimStep","renewStep").contains(method.getName())));
    }
    @Test void realRsaSignatureAndMatchingPersistedTokenAdmitOnlyExactRestrictedIdentity()throws Exception {
        var keys=java.security.KeyPairGenerator.getInstance("RSA");keys.initialize(2048);var pair=keys.generateKeyPair();
        var actualPolicy=new RunTokenSignature.Policy("urn:medical:test:simulation",SimulationRunTokenVerifier.AUDIENCE,
            (java.security.interfaces.RSAPublicKey)pair.getPublic(),java.time.Duration.ofMinutes(5),16384);
        when(current.policy()).thenReturn(actualPolicy);when(current.revision()).thenReturn("test-policy");when(current.contentSha256()).thenReturn("c".repeat(64));
        var proof=(com.fasterxml.jackson.databind.node.ObjectNode)schema.parse(SimulationInvocationSchemaTest.resource("admission")).required("authority_proof");
        var now=java.time.Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);var expiry=now.plusSeconds(180);
        proof.put("issued_at",now.toString()).put("expires_at",expiry.toString());
        var claims=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();claims.put("iss",actualPolicy.issuer()).put("aud",actualPolicy.audience());
        claims.put("iat",now.getEpochSecond()).put("exp",expiry.getEpochSecond()).put("jti",proof.path("token_jti").asText());
        for(String field:List.of("tenant_id","project_id","step_id"))claims.set(field,proof.required(field));
        claims.set("initiator_id",proof.required("actor_id"));claims.set("run_id",proof.required("parent_run_id"));claims.put("auth_epoch","1");
        claims.putArray("action_scope").add(SimulationExecutionPermissionPolicy.ACTION);
        var signed=new com.nimbusds.jose.JWSObject(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
            .type(new com.nimbusds.jose.JOSEObjectType("medical-run+jwt")).keyID(actualPolicy.keyId()).build(),new com.nimbusds.jose.Payload(claims.toString()));
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner((java.security.interfaces.RSAPrivateKey)pair.getPrivate()));String raw=signed.serialize();
        when(policies.verify(raw)).thenAnswer(call->RunTokenSignature.verify(raw,actualPolicy));
        var tokens=mock(RunTokenAuthorityStore.class);String actor=proof.path("actor_id").asText(),principal=java.util.UUID.randomUUID().toString();
        var token=new RunTokenAuthorityStore.Token(proof.path("token_jti").asText(),principal,proof.path("project_id").asText(),proof.path("parent_run_id").asText(),proof.path("step_id").asText(),
            actualPolicy.audience(),"[\"model.simulation.execute\"]",java.math.BigInteger.ONE,java.time.LocalDateTime.ofInstant(now,java.time.ZoneOffset.UTC),java.time.LocalDateTime.ofInstant(expiry,java.time.ZoneOffset.UTC),
            null,org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw),"spiffe://medical/simulation","test-policy","c".repeat(64),java.math.BigInteger.ONE,"1.0.0",schema.manifest(),actor);
        when(tokens.token(actor,token.jti())).thenReturn(token);var verifier=new SimulationRunTokenVerifier(tokens,policies,schema);
        var admission=mock(EnterpriseAccess.Admission.class);var subject=mock(com.ruoyi.medical.identity.ApplicationSessionIdentity.Verified.class);
        when(admission.subject()).thenReturn(subject);when(admission.actorId()).thenReturn(actor);when(subject.user()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.User(actor,"test","test","USER"));
        when(admission.context()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(proof.path("tenant_id").asText(),"1","1","1"));
        when(admission.watermarks()).thenReturn(new TenantWatermarks(java.math.BigInteger.ONE,java.math.BigInteger.ONE));
        var service=mock(ServiceCertificateIdentity.Verified.class);when(service.serviceUri()).thenReturn("spiffe://medical/simulation");
        var http=mock(HttpServletRequest.class);when(http.getHeaders("X-Simulation-Run-Token")).thenAnswer(call->Collections.enumeration(List.of(raw)));
        assertDoesNotThrow(()->verifier.require(http,admission,proof,principal,service));
        when(service.serviceUri()).thenReturn("spiffe://medical/other");assertThrows(TenantAccessException.class,()->verifier.require(http,admission,proof,principal,service));
        when(service.serviceUri()).thenReturn("spiffe://medical/simulation");proof.put("lease_generation",2);assertThrows(TenantAccessException.class,()->verifier.require(http,admission,proof,principal,service));
        proof.put("lease_generation",1);when(current.contentSha256()).thenReturn("d".repeat(64));assertThrows(TenantAccessException.class,()->verifier.require(http,admission,proof,principal,service));
        when(current.contentSha256()).thenReturn("c".repeat(64));
        var revoked=new RunTokenAuthorityStore.Token(token.jti(),token.principal(),token.project(),token.run(),token.step(),token.audience(),token.actions(),token.auth(),token.issued(),token.expires(),token.issued(),token.sha256(),token.service(),token.policyRevision(),token.policySha256(),token.generation(),token.schema(),token.manifest(),token.creator());
        when(tokens.token(actor,token.jti())).thenReturn(revoked);assertThrows(TenantAccessException.class,()->verifier.require(http,admission,proof,principal,service));
    }
    @Test void disabledExecutorConfigurationNeedsNoTokenOrStorageDependencies() {
        for(String enabled:List.of("", "false")) try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            if(!enabled.isEmpty())context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",java.util.Map.of("medical.simulation-execution.enabled",enabled)));
            context.register(SimulationInvocationConfiguration.class);assertDoesNotThrow(context::refresh);
            assertTrue(context.getBeansOfType(SimulationInvocationService.class).isEmpty());assertTrue(context.getBeansOfType(SimulationInvocationSchema.class).isEmpty());
        }
    }
}
