package com.ruoyi.medical.persistence;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MyBatis/tenant SQL compilation and migration contracts; no database or model calls. */
class GruMapperTest {
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.GruMapper.";
    private static final String TENANT = id(1), ACTOR = id(2), OTHER = id(3);
    private static final List<String> READS = List.of("lockHead", "recoveryOperation", "messageCounts", "version", "parents", "listed", "head", "context", "messages",
            "operation", "operationById", "audit", "event");
    private static final List<String> INSERTS = List.of("insertConversation", "insertContext", "insertMessage",
            "insertMessageRecord", "insertVersion", "insertLineage", "saveOperation");
    private static final List<String> ALL;
    static { var all = new ArrayList<>(READS); all.addAll(INSERTS); all.add("advanceConversation"); ALL = List.copyOf(all); }

    @Test void allStatementsRequireVerifiedTenantTransactionAndExactCurrentSubject() throws Exception {
        var configuration = configuration(); var values = values();
        for (String name : ALL) {
            assertTrue(configuration.hasStatement(PREFIX + name), name);
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name), name);
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, values), name);
        }
        try (var scope = TenantScope.open(identity())) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                for (String name : ALL)
                    assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name), "unverified " + name);
                scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE, BigInteger.ONE));
                for (String name : ALL) {
                    TenantScope.requireSqlAccess(PREFIX + name); TenantScope.requireSubjectParameter(PREFIX + name, values);
                    assertThrows(TenantAccessException.class,
                            () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of("userId", OTHER)), name);
                    assertThrows(TenantAccessException.class,
                            () -> TenantScope.requireSubjectParameter(PREFIX + name, Map.of()), name + ": missing subject");
                }
            } finally { scope.finishTransaction(); TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }

    @Test void everyQueryIsUncachedBoundAndTenantRewrittenIncludingJoinsAndInserts() throws Exception {
        var configuration = configuration(); var values = values();
        try (var ignored = TenantScope.open(identity())) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String name : ALL) {
                var statement = configuration.getMappedStatement(PREFIX + name);
                if (READS.contains(name)) { assertFalse(statement.isUseCache(), name); assertTrue(statement.isFlushCacheRequired(), name); }
                String raw = statement.getBoundSql(values).getSql();
                assertFalse(raw.contains("private value"), name); assertFalse(raw.contains("${"), name);
                String scoped = interceptor.parserSingle(raw, null);
                assertTrue(scoped.contains(TENANT), name); assertDoesNotThrow(() -> CCJSqlParserUtil.parse(scoped), name);
                if (INSERTS.contains(name)) {
                    var rawInsert = assertInstanceOf(Insert.class, CCJSqlParserUtil.parse(raw));
                    assertFalse(rawInsert.getColumns().stream().anyMatch(c -> c.getColumnName().equalsIgnoreCase("tenant_id")), name);
                    var scopedInsert = assertInstanceOf(Insert.class, CCJSqlParserUtil.parse(scoped));
                    assertTrue(scopedInsert.getColumns().stream().anyMatch(c -> c.getColumnName().equalsIgnoreCase("tenant_id")), name);
                }
            }
            for (var entry : Map.of("listed", List.of("c", "g"), "context", List.of("g", "c"),
                    "messages", List.of("m", "r", "c")).entrySet()) {
                String scoped = compact(interceptor.parserSingle(sql(configuration, entry.getKey()), null));
                for (String alias : entry.getValue()) assertTrue(scoped.contains(alias + ".tenant_id='" + TENANT + "'"), entry.getKey() + ": " + alias);
            }
        }
    }

    @Test void allThreeRecordMappingsKeepEveryFieldInExactConstructorOrderAndType() throws Exception {
        var configuration = configuration();
        recordMapping(configuration, "head", GruProtocol.Head.class, List.of("tenant_id", "project_id", "conversation_id", "owner_id",
                "document_id", "status", "revision", "created_by", "created_at", "classification"));
        recordMapping(configuration, "context", GruProtocol.Context.class, List.of("tenant_id", "conversation_id", "project_id", "document_id",
                "document_revision", "scope_snapshot_id", "scope_json", "scope_manifest_sha256", "dependency_access_set_id", "canonical_request_json",
                "request_body_sha256", "operation_request_sha256", "operation_id", "receipt_json", "schema_version", "schema_manifest_sha256",
                "created_by", "created_at", "classification"));
        recordMapping(configuration, "messages", GruProtocol.Message.class, List.of("tenant_id", "project_id", "conversation_id", "message_id",
                "scope_snapshot_id", "document_revision", "selection", "content_ref", "content_sha256", "dependency_access_set_id", "operation_id",
                "candidate_ids", "sender_kind", "schema_version", "schema_manifest_sha256", "created_by", "created_at", "classification",
                "message_sequence", "canonical_request_json", "request_body_sha256", "operation_request_sha256", "receipt_json", "intent", "execution_state"));
    }

    @Test void readsBindTheRealConversationOwnerAndFixedTenantJoinsWithOverflowSentinels() throws Exception {
        var configuration = configuration();
        for (String name : List.of("listed", "head", "context", "messages"))
            assertTrue(compact(sql(configuration, name)).contains("owner_id=?"), name);
        for (String name : List.of("head", "context", "messages"))
            assertTrue(compact(sql(configuration, name)).contains("conversation_id=?"), name);
        String listed = compact(sql(configuration, "listed"));
        assertTrue(listed.contains("c.project_id=?")); assertTrue(listed.contains("c.status='active'"));
        assertTrue(listed.contains("g.tenant_id=c.tenant_id")); assertTrue(listed.contains("g.conversation_id=c.conversation_id"));
        assertTrue(listed.contains("orderbyc.created_at,c.conversation_idlimit101"));
        String context = compact(sql(configuration, "context"));
        assertTrue(context.contains("c.tenant_id=g.tenant_id")); assertTrue(context.contains("c.conversation_id=g.conversation_id"));
        String messages = compact(sql(configuration, "messages"));
        assertTrue(messages.contains("r.tenant_id=m.tenant_id")); assertTrue(messages.contains("r.message_id=m.message_id"));
        assertTrue(messages.contains("c.tenant_id=m.tenant_id")); assertTrue(messages.contains("c.conversation_id=m.conversation_id"));
        assertTrue(messages.contains("orderbyr.message_sequencelimit501")); assertFalse(messages.contains("offset"));
        assertEquals(500, GruSchema.MAX_MESSAGES);
    }

    @Test void theOnlyUpdateIsOwnerAndRevisionCasAndCannotRetargetTheConversation() throws Exception {
        var configuration = configuration();
        for (String name : READS) assertEquals(SqlCommandType.SELECT, configuration.getMappedStatement(PREFIX + name).getSqlCommandType(), name);
        for (String name : INSERTS) assertEquals(SqlCommandType.INSERT, configuration.getMappedStatement(PREFIX + name).getSqlCommandType(), name);
        assertEquals(SqlCommandType.UPDATE, configuration.getMappedStatement(PREFIX + "advanceConversation").getSqlCommandType());
        var update = assertInstanceOf(Update.class, CCJSqlParserUtil.parse(sql(configuration, "advanceConversation")));
        assertEquals("conversation", update.getTable().getName()); assertNotNull(update.getWhere());
        var columns = update.getUpdateSets().stream().flatMap(set -> set.getColumns().stream())
                .map(column -> column.getColumnName().toLowerCase(Locale.ROOT)).toList();
        assertEquals(List.of("revision", "updated_at"), columns);
        String cas = compact(update.getWhere().toString());
        for (String clause : List.of("conversation_id=?", "owner_id=?", "status='active'", "revision=?")) assertTrue(cas.contains(clause), clause);
        var properties = configuration.getMappedStatement(PREFIX + "advanceConversation").getBoundSql(values()).getParameterMappings()
                .stream().map(mapping -> mapping.getProperty()).toList();
        assertEquals(List.of("nextRevision", "now", "conversationId", "userId", "expectedRevision"), properties);
    }

    @Test void persistenceOperationAndOriginalEvidenceRemainBoundToTheirIdentities() throws Exception {
        var configuration = configuration();
        String operation = compact(sql(configuration, "operation")), byId = compact(sql(configuration, "operationById"));
        assertTrue(operation.contains("operation_name=?andsubject_id=?andidempotency_key=?"));
        assertTrue(byId.contains("operation_name=?andsubject_id=?andoperation_id=?"));
        assertEquals(List.of("operationName", "userId", "key"), properties(configuration, "operation"));
        assertEquals(List.of("operationName", "creatorId", "operationId"), properties(configuration, "operationById"));
        for (String name : List.of("operation", "operationById")) {
            String lookup = compact(sql(configuration, name));
            for (String column : List.of("request_sha256", "state", "http_status", "response_ref", "target_id", "schema_version", "schema_manifest_sha256"))
                assertTrue(lookup.contains(column), name + ": " + column);
        }
        String save = compact(sql(configuration, "saveOperation"));
        assertTrue(save.startsWith("insertintooperation_record(")); assertTrue(save.contains("'completed',201"));
        assertTrue(properties(configuration, "saveOperation").containsAll(List.of("operationName", "key", "requestHash", "response", "targetId", "manifest", "userId")));
        String audit = compact(sql(configuration, "audit"));
        for (String clause : List.of("project_id=?", "subject_id=?", "created_by=?", "request_id=?", "trace_id=?", "action=?",
                "result='success'", "reason_codeisnull", "schema_version='1.0.0'", "schema_manifest_sha256=?", "classification=?"))
            assertTrue(audit.contains(clause), clause);
        String event = compact(sql(configuration, "event"));
        for (String clause : List.of("event_id=?", "aggregate_id=?", "aggregate_revision=1", "event_type=?")) assertTrue(event.contains(clause), clause);
    }

    @Test void recordWritesCannotScheduleExecutionAndRunIdentityIsLiteralNull() throws Exception {
        var configuration = configuration();
        for (String name : ALL) {
            String sql = compact(sql(configuration, name));
            for (String forbidden : List.of("execution_registry", "model_invocation", "model_result", "pending_action",
                    "candidate_version", "permission_grant", "signature_record", "approval_decision"))
                assertFalse(sql.contains(forbidden), name + ": " + forbidden);
            assertNotEquals(SqlCommandType.DELETE, configuration.getMappedStatement(PREFIX + name).getSqlCommandType(), name);
        }
        String message = compact(sql(configuration, "insertMessage"));
        assertTrue(message.contains("classification,run_id)values(")); assertTrue(message.endsWith(",null)"));
        assertFalse(properties(configuration, "insertMessage").stream().anyMatch(property -> property.toLowerCase(Locale.ROOT).contains("run")));
        assertTrue(compact(sql(configuration, "insertVersion")).contains("'conversation_message'"));
        assertTrue(compact(sql(configuration, "insertLineage")).contains("'message_context'"));
        String version = compact(sql(configuration, "version"));
        assertTrue(version.contains("version_id=?andowner_project_id=?andentity_kind='conversation_message'"));
        String parents = compact(sql(configuration, "parents"));
        assertTrue(parents.contains("casewhenrelation_kind='message_context'andactor_id=?anddecision_refisnull"));
        assertTrue(parents.contains("else'invalid_lineage'")); assertTrue(parents.contains("whereconsumer_version_id=?orderbyupstream_version_id"));
    }

    @Test void recoveryReadsCurrentOwnerHeadAndOriginalActorKeyUnderLocks() throws Exception {
        String head = compact(sql(configuration(), "lockHead")), operation = compact(sql(configuration(), "recoveryOperation"));
        assertTrue(head.contains("whereowner_id=?andconversation_id=?forupdate"));
        assertTrue(operation.contains("subject_id=?andoperation_name='gru-record-append'andidempotency_key=?forupdate"));
        assertFalse(operation.contains("limit1"));
    }

    @Test void directoryCountsBothTablesUnderOwnerWithoutSelectingMessageContent() throws Exception {
        String text = compact(sql(configuration(), "messageCounts"));
        assertTrue(text.contains("count(m.message_id)asmessage_count"));
        assertTrue(text.contains("count(r.message_id)asrecord_count"));
        assertTrue(text.contains("leftjoinconversation_messagem"));
        assertTrue(text.contains("leftjoingru_message_recordr"));
        assertTrue(text.contains("c.owner_id=?andc.conversation_id=?"));
        assertTrue(text.contains("r.tenant_id=m.tenant_id"));
        assertTrue(text.contains("r.created_by=m.created_by"));
        assertFalse(text.contains("content_ref"));
        assertFalse(text.contains("canonical_request_json"));
        assertFalse(text.contains("operation_record"));
    }

    @Test void immutableGruRecordsCannotBeUpdatedOrDeletedThroughTenantInterceptor() {
        try (var ignored = TenantScope.open(identity())) {
            var interceptor = new ProtectedTenantInterceptor();
            for (String table : List.of("gru_conversation_context", "gru_message_record")) {
                assertThrows(TenantAccessException.class, () -> interceptor.parserSingle("UPDATE " + table + " SET classification='PUBLIC'", null), table);
                assertThrows(TenantAccessException.class, () -> interceptor.parserSingle("DELETE FROM " + table, null), table);
            }
        }
    }

    @Test void migrationRetainsTenantKeysExactDocumentIdentitySequenceUniquenessAndRecordOnlyChecks() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Path migration = root.resolve("business/medical-data/src/main/resources/db/migration/066-gru-records.sql");
        String original = Files.readString(migration), ddl = compact(original);
        assertEquals(2, original.split("CREATE TABLE ", -1).length - 1);
        for (String clause : List.of("primarykey(tenant_id,conversation_id)", "primarykey(tenant_id,message_id)",
                "uniquekeyuq_gru_context_operation(tenant_id,operation_id)",
                "uniquekeyuq_gru_message_sequence(tenant_id,conversation_id,message_sequence)",
                "foreignkey(tenant_id,project_id,conversation_id)referencesconversation(tenant_id,project_id,conversation_id)",
                "foreignkey(tenant_id,project_id,document_id,document_revision)referencesdocument_revision_meta(tenant_id,project_id,document_id,document_revision)",
                "foreignkey(tenant_id,project_id,scope_snapshot_id)referencesscope_snapshot(tenant_id,project_id,snapshot_id)",
                "foreignkey(tenant_id,project_id,conversation_id,message_id)referencesconversation_message(tenant_id,project_id,conversation_id,message_id)",
                "foreignkey(tenant_id,created_by)referencestenant_member(tenant_id,user_id)",
                "check(message_sequencebetween2and501)", "check(execution_state='not_executed')",
                "check(intentin('query','retrieve','check','propose_edit'))", "check(schema_version='1.0.0')",
                "json_valid(canonical_request_json)", "json_valid(receipt_json)", "json_valid(scope_json)"))
            assertTrue(ddl.contains(clause), clause);
        for (String field : List.of("scope_manifest_sha256", "request_body_sha256", "operation_request_sha256", "schema_manifest_sha256"))
            assertTrue(ddl.contains(field + "char(64)charactersetasciinotnull"), field);
        for (String forbidden : List.of("droptable", "truncatetable", "updatemodel", "insertintoexecution_registry", "insertintopermission_grant"))
            assertFalse(ddl.contains(forbidden), forbidden);
        String changelog = Files.readString(root.resolve("business/medical-data/src/main/resources/db/changelog/master.xml"));
        assertEquals(1, changelog.split("db/migration/066-gru-records\\.sql", -1).length - 1);
    }

    private static void recordMapping(Configuration configuration, String statement, Class<?> type, List<String> columns) {
        var map = configuration.getMappedStatement(PREFIX + statement).getResultMaps().get(0);
        assertEquals(type, map.getType()); var mappings = map.getConstructorResultMappings(); var components = type.getRecordComponents();
        assertEquals(components.length, mappings.size()); assertEquals(columns, mappings.stream().map(mapping -> mapping.getColumn()).toList());
        for (int index = 0; index < components.length; index++)
            assertEquals(components[index].getType(), mappings.get(index).getJavaType(), statement + ": " + components[index].getName());
    }
    private static Configuration configuration() throws Exception {
        return new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
    }
    private static Map<String,Object> values() {
        var values = new HashMap<String,Object>(); values.put("userId", ACTOR); values.put("creatorId", ACTOR);
        values.put("operationName", "private value ' OR 1=1"); values.put("key", "private value ' OR 1=1"); return values;
    }
    private static String sql(Configuration configuration, String statement) {
        return configuration.getMappedStatement(PREFIX + statement).getBoundSql(values()).getSql();
    }
    private static List<String> properties(Configuration configuration, String statement) {
        return configuration.getMappedStatement(PREFIX + statement).getBoundSql(values()).getParameterMappings()
                .stream().map(mapping -> mapping.getProperty()).toList();
    }
    private static String compact(String value) { return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private static TenantScope.Identity identity() { return new TenantScope.Identity(TENANT, ACTOR, BigInteger.ONE, BigInteger.ONE, BigInteger.ONE); }
    private static String id(int value) { return String.format("00000000-0000-4000-8000-%012d", value); }
}
