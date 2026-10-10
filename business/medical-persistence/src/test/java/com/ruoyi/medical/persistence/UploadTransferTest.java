package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class UploadTransferTest {
    private static Connection database;
    private static String owner;
    private static final String PREFIX = "com.ruoyi.medical.persistence.mapper.UploadTransferMapper.";
    private static final List<String> READS = List.of("session", "part", "parts", "binding", "grant", "source");
    private static final List<String> WRITES = List.of("saveSession", "savePart", "saveBinding", "saveGrant",
        "beginPart", "storePart", "failPart", "receive", "beginCompletion", "complete", "cancel", "expire");

    @BeforeAll static void actualDatabase() throws Exception {
        database = openDatabase();
        database.setReadOnly(true); database.setAutoCommit(false);
        try (var query = database.createStatement(); var rows = query.executeQuery("SELECT user_id FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'")) {
            assertTrue(rows.next()); owner = rows.getString(1); assertFalse(rows.next());
        }
    }

    private static Connection openDatabase() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", secrets.required("database_password").textValue());
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
    }

    private org.apache.ibatis.session.Configuration configuration() throws Exception {
        return new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
    }

    private Map<String,Object> values() {
        var values = new java.util.HashMap<String,Object>(); values.put("userId", owner); values.put("uploadId", owner);
        values.put("partNo", 1L); values.put("afterPart", 0L); values.put("limit", 100); values.put("lock", false);
        values.put("expected", "1"); values.put("row", Map.of()); values.put("writeId", owner); values.put("reference", Map.of());
        return values;
    }

    @Test void completeOriginalSessionPartBindingGrantAndSourceAreActuallyReadable() throws Exception {
        var configuration = configuration();
        for (String name : READS) {
            assertTrue(configuration.hasStatement(PREFIX + name), "完整上传必须读取原许可、来源、会话与固定分片");
            var statement = configuration.getMappedStatement(PREFIX + name);
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var parameters = values(); var bound = statement.getBoundSql(parameters);
            try (var actual = database.prepareStatement(bound.getSql())) {
                new DefaultParameterHandler(statement, parameters, bound).setParameters(actual);
                try (var rows = actual.executeQuery()) {
                    assertFalse(rows.next());
                    var arguments = statement.getResultMaps().get(0).getConstructorResultMappings();
                    var components = statement.getResultMaps().get(0).getType().getRecordComponents();
                    assertEquals(components.length, arguments.size(), name);
                    assertEquals(components.length, rows.getMetaData().getColumnCount(), name);
                    for (int index = 0; index < components.length; index++) {
                        assertEquals(components[index].getType(), arguments.get(index).getJavaType(), name);
                        assertEquals(arguments.get(index).getColumn(), rows.getMetaData().getColumnLabel(index + 1), name);
                    }
                }
            }
        }
    }

    @Test void allUploadReadsAndWritesRequireActualSubjectAndEnterpriseTransaction() {
        var names = new java.util.ArrayList<>(READS); names.addAll(WRITES); names.addAll(List.of("databaseTime", "storedSize"));
        for (String name : names) {
            var failure = assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(PREFIX + name, values()));
            assertEquals(ErrorCode.ACCESS_DENIED, failure.code(), name);
            var transaction = assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(PREFIX + name));
            assertEquals(ErrorCode.ACCESS_DENIED, transaction.code(), name);
        }
    }

    @Test void repeatedPartLookupRequiresCurrentActualAdmission() throws Exception {
        var methods = java.util.Arrays.stream(UploadTransferStore.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("partIfPresent")).toList();
        assertEquals(1, methods.size(), "相同分片需要读取原记录，再判断相同内容和当前状态");
        var configuration = configuration();
        var factory = new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession()) {
            var store = new UploadTransferStore(session.getMapper(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class), new UploadAuthorizationSchema());
            var denied = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> methods.get(0).invoke(store, null, null, 1L));
            assertEquals(ErrorCode.ACCESS_DENIED, ((TenantAccessException) denied.getCause()).code());
        }
    }

    @Test void uploadCreationPreservesEveryPhysicalColumnAndServerIdentity() throws Exception {
        var configuration = configuration();
        for (var mapping : Map.of("saveSession", "object_upload_session", "savePart", "object_upload_part",
                "saveBinding", "artifact_upload_binding", "saveGrant", "artifact_upload_grant").entrySet()) {
            assertTrue(configuration.hasStatement(PREFIX + mapping.getKey()), "许可和上传保存必须保留全部固定字段");
            var statement = configuration.getMappedStatement(PREFIX + mapping.getKey());
            var insert = (net.sf.jsqlparser.statement.insert.Insert) CCJSqlParserUtil.parse(statement.getBoundSql(values()).getSql());
            assertEquals(mapping.getValue(), insert.getTable().getName());
            var columns = insert.getColumns().stream().map(net.sf.jsqlparser.schema.Column::getColumnName).collect(java.util.stream.Collectors.toSet());
            assertFalse(columns.contains("tenant_id")); assertFalse(columns.contains("project_key"));
            var expected = new java.util.HashSet<String>();
            try (var query = database.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name NOT IN ('tenant_id','project_key')")) {
                query.setString(1, mapping.getValue());
                try (var rows = query.executeQuery()) { while (rows.next()) expected.add(rows.getString(1)); }
            }
            assertEquals(expected, columns, mapping.getKey());
        }
    }

    @Test void everyMutableTransferRequiresOriginalRevisionAndOwnerBeforeUpdate() throws Exception {
        var configuration = configuration();
        for (String name : List.of("beginPart", "storePart", "failPart", "receive", "beginCompletion", "complete", "cancel", "expire")) {
            assertTrue(configuration.hasStatement(PREFIX + name), "并发和中断恢复必须以原版本更新当前上传");
            var statement = configuration.getMappedStatement(PREFIX + name); var bound = statement.getBoundSql(values());
            var update = (net.sf.jsqlparser.statement.update.Update) CCJSqlParserUtil.parse(bound.getSql());
            assertNotNull(update.getWhere());
            assertNull(update.getFromItem()); assertNull(update.getSelect());
            assertTrue(update.getJoins() == null || update.getJoins().isEmpty());
            assertTrue(update.getStartJoins() == null || update.getStartJoins().isEmpty());
            var parameters = bound.getParameterMappings().stream().map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList();
            assertTrue(parameters.contains("expected"), name); assertTrue(parameters.contains("userId"), name);
            assertTrue(parameters.contains("uploadId"), name);
            assertTrue(update.getUpdateSets().stream().flatMap(set -> set.getColumns().stream()).anyMatch(column -> column.getColumnName().equals("revision")), name);
        }
    }

    @Test void actualDatabasePlansEveryGuardedUpdateWithoutWritingCustomerRows() throws Exception {
        var configuration = configuration();
        try (var plans = openDatabase()) {
            for (String name : List.of("beginPart", "storePart", "failPart", "receive", "beginCompletion", "complete", "cancel", "expire")) {
                var statement = configuration.getMappedStatement(PREFIX + name); var parameters = values();
                parameters.put("receivedSize", BigInteger.ZERO); var bound = statement.getBoundSql(parameters);
                try (var query = plans.prepareStatement("EXPLAIN " + bound.getSql())) {
                    new DefaultParameterHandler(statement, parameters, bound).setParameters(query);
                    try (var result = query.executeQuery()) { assertTrue(result.next(), name); }
                }
            }
        }
        for (String name : List.of("databaseTime", "storedSize")) {
            var statement = configuration.getMappedStatement(PREFIX + name); var parameters = values();
            assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
            var bound = statement.getBoundSql(parameters);
            try (var query = database.prepareStatement(bound.getSql())) {
                new DefaultParameterHandler(statement, parameters, bound).setParameters(query);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    if (name.equals("storedSize")) assertEquals(BigInteger.ZERO, result.getBigDecimal(1).toBigIntegerExact());
                    else assertNotNull(result.getObject(1, java.time.LocalDateTime.class));
                    assertFalse(result.next());
                }
            }
        }
    }

    @Test void uploadStorageCannotCreateCustomerRecordsDuringReadonlyChecks() throws Exception {
        for (String table : List.of("tenant", "project", "upload_item", "upload_part", "artifact_upload_grant", "artifact",
                "object_upload_session", "object_upload_part", "artifact_upload_binding")) {
            try (var query = database.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                assertTrue(rows.next()); assertEquals(0, rows.getLong(1)); assertFalse(rows.next());
            }
        }
    }

    @AfterAll static void closeDatabase() throws Exception { if (database != null) database.close(); }

    @Test void actualPublicOriginalsRoundtripThroughUploadJsonAndRejectConflictingVersions() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath(); var json = new ObjectMapper();
        var objects = json.readTree(Files.readAllBytes(root.resolve(
            ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json"))).required("objects");
        assertEquals(2, objects.size());
        for (var object : objects) {
            var original = root.resolve(object.required("source_ref").textValue()).toRealPath(); var bytes = Files.readAllBytes(original);
            assertEquals(object.required("size_bytes").textValue(), Integer.toString(bytes.length));
            assertEquals(object.required("sha256").textValue(), org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes));
            var reference = new ObjectRef(object.required("object_key").textValue(), object.required("object_version").textValue(),
                object.required("sha256").textValue(), object.required("size_bytes").textValue(), object.required("media_type").textValue());
            var stored = UploadTransferObjects.encode(reference); assertEquals(reference, UploadTransferObjects.decode(stored, reference));
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(stored);
            assertEquals(java.util.Set.of("key", "version_id", "sha256", "size_bytes", "media_type"),
                java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
                    .collect(java.util.stream.Collectors.toSet()));
            node.put("version_id", objects.get(object == objects.get(0) ? 1 : 0).required("object_version").textValue());
            assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
                () -> UploadTransferObjects.decode(json.writeValueAsString(node), reference)).code());
            node.put("version_id", reference.objectVersion()); node.put("size_bytes", reference.sizeBytes());
            assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
                () -> UploadTransferObjects.decode(json.writeValueAsString(node), reference)).code());
        }
    }

    @Test void fixedPartRangesCoverActualOriginalSizesAndUnsignedCapacityWithoutTruncation() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var objects = new ObjectMapper().readTree(Files.readAllBytes(root.resolve(
            ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json"))).required("objects");
        for (var object : objects) {
            var total = new BigInteger(object.required("size_bytes").textValue());
            assertEquals(BigInteger.ONE, UploadTransferStore.partCount(total));
            assertEquals(new UploadTransferStore.PartRange(BigInteger.ZERO, total, total), UploadTransferStore.range(total, 1));
            assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class,
                () -> UploadTransferStore.range(total, 2)).code());
        }
        var block = UploadTransferProtocol.PART_BYTES; var total = block.multiply(BigInteger.TWO).add(BigInteger.ONE);
        assertEquals(BigInteger.valueOf(3), UploadTransferStore.partCount(total));
        assertEquals(new UploadTransferStore.PartRange(BigInteger.ZERO, block, block), UploadTransferStore.range(total, 1));
        assertEquals(new UploadTransferStore.PartRange(block, block.multiply(BigInteger.TWO), block), UploadTransferStore.range(total, 2));
        assertEquals(new UploadTransferStore.PartRange(total.subtract(BigInteger.ONE), total, BigInteger.ONE), UploadTransferStore.range(total, 3));
        var largestPart = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE); var largestTotal = largestPart.multiply(block);
        assertEquals(largestPart, UploadTransferStore.partCount(largestTotal));
        assertEquals(new UploadTransferStore.PartRange(largestTotal.subtract(block), largestTotal, block),
            UploadTransferStore.range(largestTotal, largestPart.longValueExact()));
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
            () -> UploadTransferStore.range(largestTotal.add(BigInteger.ONE), largestPart.longValueExact())).code());
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(TenantAccessException.class,
            () -> UploadTransferStore.range(total, 0)).code());
    }

    @Test void storeOperationsCannotUseMissingEnterpriseAdmission() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        try (var session = factory.openSession()) {
            var store = new UploadTransferStore(session.getMapper(com.ruoyi.medical.persistence.mapper.UploadTransferMapper.class), new UploadAuthorizationSchema());
            var actions = List.<org.junit.jupiter.api.function.Executable>of(
                () -> store.session(null, owner), () -> store.binding(null, owner), () -> store.part(null, owner, 1),
                () -> store.parts(null, owner, 0, 100), () -> store.beginPart(null, owner, 1, "1", owner),
                () -> store.storePart(null, owner, 1, "1", owner, null), () -> store.failPart(null, owner, 1, "1", owner),
                () -> store.beginCompletion(null, owner, "1"), () -> store.complete(null, owner, "1", null),
                () -> store.cancel(null, owner, "1"), () -> store.expire(null, owner, "1"));
            for (var action : actions) assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, action).code());
        }
    }
}
