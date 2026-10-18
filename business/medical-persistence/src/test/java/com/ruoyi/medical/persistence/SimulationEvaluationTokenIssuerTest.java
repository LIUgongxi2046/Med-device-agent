package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.identity.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEvaluationTokenIssuerTest {
    @TempDir Path directory;
    static record Actual(SimulationEvaluationTokenIssuer issuer,RunTokenPolicyStore policies,Path key,Path policy){}
    static Actual actual(Path directory)throws Exception {
        Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();var json=new ObjectMapper();
        var policy=json.createObjectNode().put("schema_version","1.0.0").put("revision",UUID.randomUUID().toString()).put("issuer","urn:medical:simulation-test")
            .put("audience",SimulationRunTokenVerifier.AUDIENCE).put("public_key_der_base64",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()))
            .put("maximum_lifetime_seconds",300).put("maximum_token_bytes",16384);
        Path policyPath=directory.resolve("run-token-policy.json"),keyPath=directory.resolve("run-token-signing-key.json");write(policyPath,json.writeValueAsBytes(policy));
        var owner=Files.getOwner(directory);var policies=new RunTokenPolicyStore(directory,owner,65536);var current=policies.read();
        var key=json.createObjectNode().put("schema_version","1.0.0").put("revision",UUID.randomUUID().toString()).put("policy_revision",current.revision())
            .put("policy_sha256",current.contentSha256()).put("private_key_der_base64",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));write(keyPath,json.writeValueAsBytes(key));
        return new Actual(new SimulationEvaluationTokenIssuer(policies,new RunTokenSigningKeyStore(directory,owner,65536,policies)),policies,keyPath,policyPath);
    }
    private static void write(Path path,byte[] bytes)throws Exception{Files.write(path,bytes);Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));}
    @Test void protectedPrivateKeyProducesActualRestrictedJwsAndRejectsBroadenedAudienceAndKeyPermissions()throws Exception {
        var actual=actual(directory);var now=LocalDateTime.now(ZoneOffset.UTC);String tenant=UUID.randomUUID().toString(),actor=UUID.randomUUID().toString(),run=UUID.randomUUID().toString(),step=UUID.randomUUID().toString(),jti=UUID.randomUUID().toString();
        var issued=actual.issuer().issue(tenant,null,actor,run,step,jti,BigInteger.ONE,now,now.plusMinutes(2),Duration.ofSeconds(60));
        var verified=actual.policies().verify(issued.raw());assertEquals(jti,verified.jti());assertEquals(List.of("model.simulation.execute"),verified.actionScope());
        assertEquals(SimulationRunTokenVerifier.AUDIENCE,verified.audience());assertEquals(0,issued.issuedAt().getNano());assertFalse(issued.toString().contains(issued.raw()));
        actual.issuer().recheck(issued);assertThrows(SecurityException.class,()->actual.issuer().issue(tenant,null,actor,run,step,jti,BigInteger.ONE,now,now.plusSeconds(5),Duration.ofSeconds(60)));
        Files.setPosixFilePermissions(actual.key(),PosixFilePermissions.fromString("rw-r--r--"));assertThrows(SecurityException.class,()->actual.issuer().issue(tenant,null,actor,run,step,jti,BigInteger.ONE,now,now.plusMinutes(2),Duration.ofSeconds(60)));
        Files.setPosixFilePermissions(actual.key(),PosixFilePermissions.fromString("rw-------"));var json=new ObjectMapper();var policy=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(actual.policy()));policy.put("audience","urn:medical:production");write(actual.policy(),json.writeValueAsBytes(policy));
        assertThrows(SecurityException.class,()->actual.issuer().recheck(issued));
    }
}
