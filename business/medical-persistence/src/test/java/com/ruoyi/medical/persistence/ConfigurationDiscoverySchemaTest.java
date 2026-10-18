package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationDiscoverySchemaTest {
    private final PermissionSchema json;
    private final ConfigurationDiscoverySchema schema;
    ConfigurationDiscoverySchemaTest() throws Exception { json = new PermissionSchema(); schema = new ConfigurationDiscoverySchema(json); }

    @Test void strictQueryAllowsOnlyBoundedSingleValues() {
        assertEquals(new ConfigurationDiscoverySchema.Query(50, null, null), schema.query(Map.of()));
        assertEquals(1, schema.query(Map.of("limit", new String[]{"1"})).limit());
        assertEquals(100, schema.query(Map.of("limit", new String[]{"100"})).limit());
        for (String value : List.of("", "0", "-1", "101", "01", "+1", " 1", "1.0", "2147483648"))
            assertThrows(SchemaRejection.class, () -> schema.query(Map.of("limit", new String[]{value})), value);
        for (String field : List.of("tenant_id", "userId", "status", "sort", "offset", "cursor", "total"))
            assertThrows(SchemaRejection.class, () -> schema.query(Map.of(field, new String[]{"x"})), field);
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of("q", new String[]{"one", "two"})));
        assertThrows(SchemaRejection.class, () -> schema.query(Map.of("q", new String[]{})));
        for (String value : List.of("", " ", " leading", "trailing ", "new\nline", "a".repeat(257), "e\u0301"))
            assertThrows(SchemaRejection.class, () -> schema.query(Map.of("q", new String[]{value})));
        assertEquals("%_literal", schema.query(Map.of("q", new String[]{"%_literal"})).q());
        for (String value : List.of("", "a".repeat(2049), "a+b", "a/b", "abc="))
            assertThrows(SchemaRejection.class, () -> schema.query(Map.of("after", new String[]{value})));
    }

    @Test void emptyPagesCarryExactContextAndDoNotAllowHiddenCountOrContextMismatch() {
        var page = json.object(); page.put("schema_version", "1.0.0"); page.put("tenant_id", ConfigurationDiscoveryServiceTest.id(1));
        var context = page.putObject("current_context"); context.put("tenant_id", ConfigurationDiscoveryServiceTest.id(1));
        context.put("context_revision", "4"); context.put("auth_epoch", "2"); context.put("governance_epoch", "3");
        page.putArray("items"); page.putNull("next_after");
        for (String kind : List.of("ModelList", "SimulationModelList", "RuleList")) {
            var result = schema.envelope(kind, page); assertEquals("4", result.required("resource_revision").textValue());
            var total = page.deepCopy(); total.put("total", "1000"); assertThrows(SchemaRejection.class, () -> schema.verify(kind, total));
            var wrong = page.deepCopy(); ((ObjectNode) wrong.required("current_context")).put("tenant_id", ConfigurationDiscoveryServiceTest.id(2));
            assertThrows(SchemaRejection.class, () -> schema.verify(kind, wrong));
            var overflow = page.deepCopy(); ((ObjectNode) overflow.required("current_context")).put("auth_epoch", "18446744073709551616");
            assertThrows(SchemaRejection.class, () -> schema.verify(kind, overflow));
        }
    }

    @Test void absentAndMalformedPolicyFailClosedWithoutCreatingAKey() {
        var absent = new ConfigurationDiscoveryCursor(new ConfigurationDiscoveryPolicy(null, null, null));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class, () -> absent.after(null, "binding")).code());
        assertThrows(RuntimeException.class, () -> new ConfigurationDiscoveryPolicy(ConfigurationDiscoveryServiceTest.id(1), "bad", Duration.ofMinutes(30)));
        assertThrows(RuntimeException.class, () -> new ConfigurationDiscoveryPolicy(ConfigurationDiscoveryServiceTest.id(1),
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]), Duration.ZERO));
    }

    @Test void authenticatedOpaqueCursorRejectsTamperingWrongBindingExpiryAndRestart() {
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        var clock = Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC);
        var cursor = new ConfigurationDiscoveryCursor(key, clock); String id = ConfigurationDiscoveryServiceTest.id(5);
        String token = cursor.next(id, "a".repeat(64)); assertEquals(id, cursor.after(token, "a".repeat(64)));
        assertFalse(token.contains(id)); assertNull(cursor.after(null, "a".repeat(64)));
        assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, () -> cursor.after(token, "b".repeat(64))).code());
        byte[] bytes = java.util.Base64.getUrlDecoder().decode(token); bytes[20] ^= 1;
        String changed = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, () -> cursor.after(changed, "a".repeat(64))).code());
        var expired = new ConfigurationDiscoveryCursor(key, Clock.offset(clock, Duration.ofMinutes(30)));
        assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, () -> expired.after(token, "a".repeat(64))).code());
        var sameKeyReplica = new ConfigurationDiscoveryCursor(key, clock);
        assertEquals(id, sameKeyReplica.after(token, "a".repeat(64)));
        var restarted = new ConfigurationDiscoveryCursor(new byte[32], clock);
        assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, () -> restarted.after(token, "a".repeat(64))).code());
        for (String malformed : List.of("a", "a".repeat(2049), token + "=", "not_a_cursor"))
            assertEquals(ErrorCode.CURSOR_INVALID, assertThrows(TenantAccessException.class, () -> cursor.after(malformed, "a".repeat(64))).code());
    }
}
