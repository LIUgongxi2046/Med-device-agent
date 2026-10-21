package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.mockito.Mockito.*;

/** Explicit JDBC in-memory row double; no transaction isolation or live database claim.
 * Production schema, ledger, registration and transport methods execute unchanged.
 * The private079/088 compiler authority is the named mock boundary in these persistence tests. */
class ExactInputObjectFixture {
    final PermissionSchema json = new PermissionSchema();
    final FormalEvaluationExactInputObjectSchema schema = new FormalEvaluationExactInputObjectSchema(json);
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class);
    final FormalEvaluationExactInputObjectAuthority.Prepared proof = mock(FormalEvaluationExactInputObjectAuthority.Prepared.class);
    final FormalEvaluationExactInputObjectLedger ledger = new FormalEvaluationExactInputObjectLedger(db, schema, json);
    final FormalEvaluationExactInputObjectRegistration registrations = new FormalEvaluationExactInputObjectRegistration(
            db, ledger, schema, new DependencyManifestSchema(), json);
    final Map<String, List<Map<String, Object>>> tables = new HashMap<>();
    final List<String> executedSql = new ArrayList<>();
    final String tenant = id(1), actor = id(2), project = id(3), execution = id(4), evaluation = id(5);
    final String key = "original-exact-input-object-key";
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 22, 0);
    FormalEvaluationExactInputObjectAuthority.Checked checked;
    TenantScope scope;

    ExactInputObjectFixture() throws Exception {
        when(admission.context()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(tenant, "1", "1", "1"));
        when(admission.actorId()).thenReturn(actor);
        var start = new FormalEvaluationExecutionProtocol.Start("1.0.0", evaluation, hash('a'), "1",
                basis(10), basis(11), basis(12), basis(13));
        var request = new FormalEvaluationExactInputObjectSchema.Request("1.0.0", execution, start, id(6), 0, "N01");
        var original = schema.request(schema.canonical(request).getBytes(StandardCharsets.UTF_8));
        var cell = new FormalEvaluationExecutionProtocol.Cell(evaluation, hash('a'), basis(10), basis(11), id(6),
                id(7), "ACCEPTANCE", 0, basis(20), basis(21), hash('b'));
        var node = new FormalEvaluationExecutionProtocol.Node(cell, "N01", basis(22), basis(23), basis(24), hash('c'));
        byte[] payload = "{\"messages\":[{\"content\":\"original document\",\"role\":\"user\"}],\"tools\":[]}".getBytes(StandardCharsets.UTF_8);
        SchemaCatalogue.fromClasspath().bindPublished(BuiltinEvaluationDocumentContextCompiler.OUTPUT,
                SchemaCatalogue.fromClasspath().schemaDigest("simulation-execution"), payload);
        var evidence = evidence(WireValues.sha256(payload));
        var ancestry = new FormalEvaluationExactInputObjectDependencies.Checked(List.of(basis(20), basis(30), basis(31)),
                List.of(new ArtifactRegistryProtocol.MemberRow(id(20), id(50)),
                        new ArtifactRegistryProtocol.MemberRow(id(30), id(50)),
                        new ArtifactRegistryProtocol.MemberRow(id(31), id(50))),
                "RESTRICTED", json.object().put("actual_authorization_boundary", "mock"));
        var stable = json.object();
        stable.set("compiler", FormalEvaluationExactInputObjectDependencies.stableEvidence(evidence));
        stable.set("members", json.tree(ancestry.members()));
        stable.set("roots", json.tree(ancestry.roots()));
        checked = new FormalEvaluationExactInputObjectAuthority.Checked(tenant, actor, "1", project, original,
                evaluation, node, basis(40), payload, evidence, schema.canonical(stable), ancestry);
        when(proof.current(admission)).thenAnswer(invocation -> checked);
        when(db.query(eq("SELECT UTC_TIMESTAMP(6)"), any(RowMapper.class))).thenAnswer(invocation -> List.of(now));
        when(db.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> select(
                invocation.getArgument(0), arguments(invocation.getArguments())));
        when(db.update(anyString(), any(Object[].class))).thenAnswer(invocation -> update(
                invocation.getArgument(0), arguments(invocation.getArguments())));
    }

    static String id(int n) { return String.format("00000000-0000-4000-8000-%012d", n); }
    static String hash(char c) { return String.valueOf(c).repeat(64); }
    static PermissionProtocol.Basis basis(int n) { return new PermissionProtocol.Basis(id(n), hash('a')); }

    ObjectNode evidence(String inputHash) throws Exception {
        var value = json.object().put("exact_input_sha256", inputHash)
                .put("exact_input_schema_sha256", SchemaCatalogue.fromClasspath().schemaDigest("simulation-execution"))
                .put("authorization_sha256", hash('e')).put("context_revision", "1");
        int n = 30;
        for (String field : List.of("tool_original", "prompt_original", "tokenizer_artifact_original")) {
            var original = value.putObject(field).put("artifact_id", id(n++))
                    .put("sha256", hash('a')).put("authorization_sha256", hash('b'));
            original.putObject("object").put("object_key", "original/" + field)
                    .put("object_version", "original-version").put("sha256", hash('a'))
                    .put("size_bytes", "3").put("media_type", "application/json");
            original.putArray("receipt_ids").add(id(n + 100));
        }
        return value;
    }

    void begin() {
        scope = TenantScope.open(new TenantScope.Identity(tenant, actor, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
    }
    void end() {
        if (scope != null) { scope.finishTransaction(); scope.close(); scope = null; }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    <T> T tx(CheckedSupplier<T> work) throws Exception {
        begin();
        try { return work.get(); } finally { end(); }
    }
    interface CheckedSupplier<T> { T get() throws Exception; }
    List<Map<String, Object>> rows(String table) { return tables.computeIfAbsent(table, ignored -> new ArrayList<>()); }
    Map<String, Object> only(String table) {
        if (rows(table).size() != 1) throw new AssertionError(table + " rows=" + rows(table).size());
        return rows(table).get(0);
    }
    FormalEvaluationExactInputObjectLedger.Attempt prepare() throws Exception {
        return tx(() -> ledger.prepare(admission, key, proof));
    }
    FormalEvaluationExactInputObjectLedger.Attempt recover() throws Exception {
        return tx(() -> ledger.recover(admission, key, proof));
    }

    private List<Map<String, Object>> select(String sql, Object[] args) {
        executedSql.add(sql);
        if (sql.startsWith("SELECT user_id FROM tenant_member")) return List.of(Map.of("user_id", actor));
        var name = Pattern.compile("FROM ([a-z_]+)").matcher(sql);
        if (!name.find()) throw new AssertionError(sql);
        String table = name.group(1);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows(table)) {
            var condition = Pattern.compile("([a-z_]+)=\\?").matcher(sql);
            int index = 0;
            boolean match = true;
            while (condition.find()) {
                Object expected = args[index++], actual = row.get(condition.group(1));
                if (!Objects.equals(expected, actual)) { match = false; break; }
            }
            if (match) result.add(new LinkedHashMap<>(row));
        }
        if (sql.contains("ORDER BY upstream_version_id")) result.sort(Comparator.comparing(row -> (String) row.get("upstream_version_id")));
        return result;
    }

    private int update(String sql, Object[] args) {
        executedSql.add(sql);
        if (sql.startsWith("INSERT INTO ")) {
            var match = Pattern.compile("INSERT INTO ([a-z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)").matcher(sql);
            if (!match.find()) throw new AssertionError(sql);
            String[] columns = match.group(2).split(","), values = match.group(3).split(",");
            if (columns.length != values.length) throw new AssertionError(sql);
            var row = new LinkedHashMap<String, Object>();
            int index = 0;
            for (int i = 0; i < columns.length; i++) {
                String value = values[i].trim();
                Object parsed = value.equals("?") ? args[index++] : value.equals("NULL") ? null
                        : value.startsWith("'") ? value.substring(1, value.length() - 1) : Integer.parseInt(value);
                row.put(columns[i].trim(), parsed);
            }
            if (index != args.length) throw new AssertionError("Bind count: " + sql);
            if (match.group(1).equals("operation_record")) row.put("error_code", null);
            if (match.group(1).equals("audit_event")) row.put("reason_code", null);
            if (match.group(1).equals("lineage_edge")) row.put("decision_ref", null);
            rows(match.group(1)).add(row);
            return 1;
        }
        if (sql.startsWith("UPDATE operation_record SET revision=2")) {
            var row = only("operation_record");
            if (!Integer.valueOf(1).equals(row.get("revision"))) return 0;
            row.put("revision", 2); row.put("updated_at", args[0]); return 1;
        }
        if (sql.startsWith("UPDATE operation_record SET state='COMPLETED'")) {
            var row = only("operation_record");
            if (!Integer.valueOf(2).equals(row.get("revision"))) return 0;
            row.put("state", "COMPLETED"); row.put("http_status", 201); row.put("response_ref", args[0]);
            row.put("revision", 3); row.put("updated_at", args[1]); return 1;
        }
        throw new AssertionError(sql);
    }
    private static Object[] arguments(Object[] all) {
        var result = Arrays.copyOfRange(all, 1, all.length);
        return result.length == 1 && result[0] instanceof Object[] array ? array : result;
    }
}
