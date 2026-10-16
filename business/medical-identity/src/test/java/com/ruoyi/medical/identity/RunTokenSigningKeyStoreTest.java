package com.ruoyi.medical.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunTokenSigningKeyStoreTest {
    private record Actual(java.nio.file.Path directory,java.nio.file.Path keyPath,RunTokenPolicyStore policies,
            java.security.KeyPair pair,java.nio.file.attribute.UserPrincipal owner) { }
    private Actual actual() throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var work=root.resolve(".implementation-work/run-token-signing-key-checks"); java.nio.file.Files.createDirectories(work);
        var directory=java.nio.file.Files.createTempDirectory(work,"rsa-",java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
            java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        var generator=java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair=generator.generateKeyPair();
        var json=new com.fasterxml.jackson.databind.ObjectMapper(); var policy=json.createObjectNode();
        policy.put("schema_version","1.0.0"); policy.put("revision",java.util.UUID.randomUUID().toString());
        policy.put("issuer","urn:medical:local-signing-key-check:"+directory.getFileName()); policy.put("audience","local-signing-key-check");
        policy.put("public_key_der_base64",java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        policy.put("maximum_lifetime_seconds",30); policy.put("maximum_token_bytes",4096);
        privateFile(directory.resolve("run-token-policy.json"),json.writeValueAsBytes(policy));
        var owner=java.nio.file.Files.getOwner(directory); var policies=new RunTokenPolicyStore(directory,owner,65536); var current=policies.read();
        var key=json.createObjectNode(); key.put("schema_version","1.0.0"); key.put("revision",java.util.UUID.randomUUID().toString());
        key.put("policy_revision",current.revision()); key.put("policy_sha256",current.contentSha256());
        key.put("private_key_der_base64",java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        var keyPath=directory.resolve("run-token-signing-key.json"); privateFile(keyPath,json.writeValueAsBytes(key));
        return new Actual(directory,keyPath,policies,pair,owner);
    }
    private static void privateFile(java.nio.file.Path path,byte[] value) throws Exception {
        java.nio.file.Files.write(path,value,java.nio.file.StandardOpenOption.CREATE_NEW);
        java.nio.file.Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
    @Test
    void signingKeyStorageHasAnIndependentClosedSecretDefinition() throws Exception {
        var resource=getClass().getResourceAsStream("/run-token-signing-key.schema.json");
        assertNotNull(resource,"签发私钥必须使用独立受保护配置");
        try(resource) {
            var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(resource);
            assertFalse(node.required("additionalProperties").booleanValue());
            assertEquals(5,node.required("required").size());
            assertTrue(node.required("properties").has("private_key_der_base64"));
        }
    }
    @Test
    void actualProtectedPrivateKeySignsRegisteredOriginalBytesAndMatchesCurrentPublicPolicy() throws Exception {
        var actual=actual(); var store=new RunTokenSigningKeyStore(actual.directory(),actual.owner(),65536,actual.policies()); var key=store.read();
        assertEquals(actual.policies().read().revision(),key.policyRevision()); assertEquals(actual.policies().read().contentSha256(),key.policySha256());
        assertEquals(actual.policies().read().policy().keyId(),key.keyId()); assertEquals("CurrentRunTokenSigningKey",key.toString());
        var root=java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath(); var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var dataset=json.readTree(java.nio.file.Files.readAllBytes(root.resolve("data-design/sample_dataset.json")));
        var source=dataset.required("sources").get(0); byte[] original=java.nio.file.Files.readAllBytes(root.resolve(source.required("source_ref").textValue()));
        assertEquals(source.required("sha256").textValue(),org.apache.commons.codec.digest.DigestUtils.sha256Hex(original));
        var signer=java.security.Signature.getInstance("SHA256withRSA"); signer.initSign(key.signingKey()); signer.update(original); byte[] signature=signer.sign();
        var verifier=java.security.Signature.getInstance("SHA256withRSA"); verifier.initVerify(actual.pair().getPublic()); verifier.update(original);
        assertTrue(verifier.verify(signature));
        assertThrows(SecurityException.class,() -> store.sign(dataset));
    }
    @Test
    void actualKeyAndPolicyChangesPermissionsAndLinksAreRejected() throws Exception {
        var actual=actual(); var store=new RunTokenSigningKeyStore(actual.directory(),actual.owner(),65536,actual.policies());
        java.nio.file.Files.setPosixFilePermissions(actual.keyPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(SecurityException.class,store::read);
        java.nio.file.Files.setPosixFilePermissions(actual.keyPath(),java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        var link=actual.directory().resolve("extra-key-link.json"); java.nio.file.Files.createLink(link,actual.keyPath());
        assertThrows(SecurityException.class,store::read); java.nio.file.Files.delete(link); assertNotNull(store.read());
        var json=new com.fasterxml.jackson.databind.ObjectMapper(); var value=(com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(java.nio.file.Files.readAllBytes(actual.keyPath()));
        var generator=java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        value.put("private_key_der_base64",java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded()));
        java.nio.file.Files.write(actual.keyPath(),json.writeValueAsBytes(value)); assertThrows(SecurityException.class,store::read);
        value.put("private_key_der_base64",java.util.Base64.getEncoder().encodeToString(actual.pair().getPrivate().getEncoded()));
        java.nio.file.Files.write(actual.keyPath(),json.writeValueAsBytes(value)); assertNotNull(store.read());
        var policyPath=actual.directory().resolve("run-token-policy.json"); var policy=(com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(java.nio.file.Files.readAllBytes(policyPath)); policy.put("maximum_token_bytes",8192);
        java.nio.file.Files.write(policyPath,json.writeValueAsBytes(policy)); assertThrows(SecurityException.class,store::read);
    }
    @Test
    void malformedActualSecretConfigurationIsRejectedWithoutIncludingPrivateMaterialInDiagnostics() throws Exception {
        var actual=actual(); var store=new RunTokenSigningKeyStore(actual.directory(),actual.owner(),65536,actual.policies());
        var json=new com.fasterxml.jackson.databind.ObjectMapper(); byte[] original=java.nio.file.Files.readAllBytes(actual.keyPath());
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(original);
        String secret=value.required("private_key_der_base64").textValue();
        var unknown=value.deepCopy(); unknown.put("extra_key",secret); java.nio.file.Files.write(actual.keyPath(),json.writeValueAsBytes(unknown));
        assertThrows(SecurityException.class,store::read);
        String duplicate=new String(original,java.nio.charset.StandardCharsets.UTF_8);
        duplicate=duplicate.substring(0,duplicate.length()-1)+",\"private_key_der_base64\":\""+secret+"\"}";
        java.nio.file.Files.writeString(actual.keyPath(),duplicate,java.nio.charset.StandardCharsets.UTF_8);
        var rejected=assertThrows(java.io.IOException.class,store::read); assertFalse(rejected.getMessage().contains(secret));
        java.nio.file.Files.writeString(actual.keyPath(),new String(original,java.nio.charset.StandardCharsets.UTF_8)+"{}",java.nio.charset.StandardCharsets.UTF_8);
        var trailing=assertThrows(java.io.IOException.class,store::read); assertFalse(trailing.getMessage().contains(secret));
        var invalid=value.deepCopy(); invalid.put("private_key_der_base64",secret+"\n"); java.nio.file.Files.write(actual.keyPath(),json.writeValueAsBytes(invalid));
        assertThrows(IllegalArgumentException.class,store::read);
        var publicKey=value.deepCopy(); publicKey.put("private_key_der_base64",java.util.Base64.getEncoder().encodeToString(actual.pair().getPublic().getEncoded()));
        java.nio.file.Files.write(actual.keyPath(),json.writeValueAsBytes(publicKey)); assertThrows(java.security.GeneralSecurityException.class,store::read);
        java.nio.file.Files.write(actual.keyPath(),original); assertNotNull(store.read());
        assertThrows(SecurityException.class,() -> new RunTokenSigningKeyStore(actual.directory(),actual.owner(),32,actual.policies()));
    }
}
