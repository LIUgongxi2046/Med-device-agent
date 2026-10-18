package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.IdentityBindingSchema;
import com.ruoyi.medical.identity.InvitationCredential;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvitationSecurityTest {
    @Test
    void allInvitationQueriesUsePublishedDatabaseFields() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        java.util.Map<String,String> statements = new java.util.TreeMap<>();
        for (String name : java.util.List.of("role", "currentInvitation", "pending", "inviterDelegations", "currentOperation", "insertBinding", "insertMember", "insertInvitation",
                "acceptInvitation", "activateMember", "expire", "saveOperation", "saveAudit", "saveEvent", "roleHead", "insertOrigin", "origins")) {
            var statement = configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.InvitationMapper." + name);
            String sql = statement.getBoundSql(new java.util.HashMap<>()).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql)); statements.put(name, sql);
        }
        var output = root.resolve(".implementation-work/invitation-query-columns.json");
        java.nio.file.Files.write(output, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(statements));
        var process = new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".lld-data-work/venv/bin/python").toString()), root.resolve("tests/check_invitation_sql_columns.py").toString(), output.toString())
                .directory(root.toFile()).inheritIO().start();
        assertEquals(0, process.waitFor());
    }

    @Test
    void authenticatedEncryptionProtectsActualIssuedCredentialAndFreshNonce() throws Exception {
        var json = new PermissionSchema(); var schema = new InvitationSchema(json, new IdentityBindingSchema());
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        var cipher = new InvitationResponseCipher(UUID.randomUUID().toString(), HexFormat.of().formatHex(key), json, schema);
        var credential = InvitationCredential.issue();
        var value = json.object(); value.put("credential", credential.secret());
        var aad = json.object(); aad.put("purpose", "INVITATION_RESPONSE"); aad.put("operation_id", UUID.randomUUID().toString());
        String first = cipher.encrypt(value, aad), second = cipher.encrypt(value, aad);
        assertFalse(first.contains(credential.secret())); assertFalse(first.contains(credential.sha256()));
        assertNotEquals(schema.encrypted(first).required("nonce"), schema.encrypted(second).required("nonce"));
        assertEquals(value, cipher.decrypt(first, aad));
        var changed = aad.deepCopy(); changed.put("operation_id", UUID.randomUUID().toString());
        assertThrows(AEADBadTagException.class, () -> cipher.decrypt(first, changed));
        var altered = schema.encrypted(first).deepCopy();
        String text = altered.required("ciphertext").textValue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) altered).put("ciphertext", (text.charAt(0) == 'A' ? "B" : "A") + text.substring(1));
        assertThrows(AEADBadTagException.class, () -> cipher.decrypt(json.canonical(altered), aad));
    }

    @Test
    void independentRequestHmacChangesWhenActualCredentialChanges() throws Exception {
        var json = new PermissionSchema(); var schema = new InvitationSchema(json, new IdentityBindingSchema());
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        var cipher = new InvitationResponseCipher(UUID.randomUUID().toString(), HexFormat.of().formatHex(key), json, schema);
        var input = json.object(); input.put("credential", InvitationCredential.issue().secret());
        String digest = cipher.requestHmac(input); assertEquals(digest, cipher.requestHmac(input));
        input.put("credential", InvitationCredential.issue().secret()); assertNotEquals(digest, cipher.requestHmac(input));
    }

    @Test
    void closedSchemasRejectAbsentUnknownAndRepeatedFields() throws Exception {
        var schema = new InvitationSchema(new PermissionSchema(), new IdentityBindingSchema());
        assertThrows(SchemaRejection.class, () -> schema.create("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class, () -> schema.accept("{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> schema.accept("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        var request = new InvitationProtocol.Accept("1.0.0", InvitationCredential.issue().secret(), "1");
        var json = new PermissionSchema();
        assertEquals(request, schema.accept(json.canonicalObject(request).getBytes(StandardCharsets.UTF_8)));
        assertEquals("InvitationAcceptance", request.toString());
        var input = json.tree(request).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) input).put("unknown", true);
        assertThrows(SchemaRejection.class, () -> schema.accept(json.canonical(input).getBytes(StandardCharsets.UTF_8)));
    }
}
