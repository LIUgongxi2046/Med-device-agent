package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.function.ThrowingSupplier;

@Component
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class EnterpriseRegistrationService {
    private static final String COLUMNS = "registration_id,operation_id,subject_id,request_sha256,company_sha256,request_body,"
            + "first_admin_user_id,first_admin_revision,content_sha256,response_ref,schema_version,schema_manifest_sha256,status,revision,created_at";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ApplicationSessionIdentity identities;
    private final EnterpriseRegistrationSchema schema;
    private final IdentityDirectoryPolicy policy;

    public EnterpriseRegistrationService(@Qualifier("masterDataSource") DataSource master, ApplicationSessionIdentity identities,
            EnterpriseRegistrationSchema schema, IdentityDirectoryPolicy policy) {
        jdbc = new JdbcTemplate(master);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.identities = identities; this.schema = schema; this.policy = policy;
    }

    JsonNode create(ApplicationSessionIdentity.Verified subject, byte[] bytes, String key) throws IOException {
        independent();
        return Objects.requireNonNull(transaction.execute(status -> ThrowingSupplier.of(() -> {
            lock(subject);
            JsonNode application = schema.request(bytes, key);
            String requestedAdmin = application.required("first_admin_user_id").textValue();
            String digest = schema.requestHash(application, subject.user().user_id());
            String company = schema.companyHash(application);
            String adminRevision = administratorRevision(requestedAdmin);
            var existing = rows("subject_id=? AND idempotency_key=?", subject.user().user_id(), key);
            if (existing.size() > 1) throw new IllegalStateException("REGISTRATION_OPERATION_DUPLICATE");
            if (!existing.isEmpty()) {
                Row saved = existing.get(0);
                if (!digest.equals(saved.requestSha())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
                JsonNode result = validate(saved, subject.user().user_id());
                if (!requestedAdmin.equals(saved.admin()) || !adminRevision.equals(saved.adminRevision())) denied(ErrorCode.SCOPE_STALE);
                return result;
            }
            if (!rows("subject_id=? AND company_sha256=?", subject.user().user_id(), company).isEmpty()) denied(ErrorCode.REVISION_CONFLICT);
            String registrationId = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString();
            String created = Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", java.sql.Timestamp.class))
                    .toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
            var detail = schema.object();
            detail.put("schema_version", "1.0.0"); detail.put("registration_id", registrationId); detail.put("operation_id", operationId);
            detail.set("application", application);
            var binding = schema.object(); binding.put("mode", "local"); binding.put("user_id", requestedAdmin);
            binding.putNull("issuer"); binding.putNull("subject"); detail.set("first_admin_binding", binding);
            detail.put("first_admin_revision", adminRevision); detail.put("submitted_by", subject.user().user_id());
            detail.put("created_at", created); detail.put("status", "PENDING_REVIEW"); detail.put("revision", "1");
            String contentHash = schema.hash(detail); detail.put("content_sha256", contentHash); schema.detail(detail);
            JsonNode result = schema.envelope("Detail", detail, UUID.randomUUID().toString(), "1");
            requireOne(jdbc.update("""
                    INSERT INTO platform_enterprise_registration (registration_id,operation_id,subject_id,idempotency_key,request_sha256,
                      company_sha256,request_body,first_admin_user_id,first_admin_revision,content_sha256,response_ref,schema_version,
                      schema_manifest_sha256,status,revision,created_by,created_at,classification)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,'PENDING_REVIEW',1,?,?,?)
                    """, registrationId, operationId, subject.user().user_id(), key, digest, company, schema.canonical(application),
                    requestedAdmin, adminRevision, contentHash, schema.canonical(result), schema.manifest(), subject.user().user_id(),
                    java.sql.Timestamp.from(java.time.Instant.parse(created)), policy.classification()));
            var audit = schema.object();
            audit.put("schema_version", "1.0.0"); audit.put("registration_id", registrationId); audit.put("operation_id", operationId);
            audit.put("submitted_by", subject.user().user_id()); audit.put("first_admin_user_id", requestedAdmin);
            audit.put("request_sha256", digest); audit.put("content_sha256", contentHash); audit.put("status", "PENDING_REVIEW"); audit.put("revision", "1");
            schema.validate("Audit", audit);
            requireOne(jdbc.update("""
                    INSERT INTO platform_enterprise_registration_audit (audit_id,registration_id,operation_id,subject_id,first_admin_user_id,
                      action,resource_ref,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                    VALUES (?,?,?,?,?,'LOCAL-REG-01',?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
                    """, UUID.randomUUID().toString(), registrationId, operationId, subject.user().user_id(), requestedAdmin,
                    schema.canonical(audit), schema.manifest(), subject.user().user_id(), policy.classification()));
            // 重读持久状态，确保响应和同步审计属于同一个已保存申请。
            var saved = rows("subject_id=? AND registration_id=?", subject.user().user_id(), registrationId);
            if (saved.size() != 1) throw new IllegalStateException("REGISTRATION_WRITE_INCOMPLETE");
            JsonNode persisted = validate(saved.get(0), subject.user().user_id());
            if (!result.equals(persisted)) throw new IllegalStateException("REGISTRATION_RESPONSE_CHANGED");
            return persisted;
        }).get()));
    }

    JsonNode list(ApplicationSessionIdentity.Verified subject, int limit, String cursor) {
        independent();
        return Objects.requireNonNull(transaction.execute(status -> ThrowingSupplier.of(() -> {
            lock(subject);
            if (limit < 1 || limit > 100) throw new IllegalArgumentException("REGISTRATION_LIMIT_INVALID");
            var records = cursor == null
                    ? jdbc.query("SELECT " + COLUMNS + " FROM platform_enterprise_registration WHERE subject_id=? ORDER BY registration_id LIMIT ?",
                        (row, ordinal) -> row(row), subject.user().user_id(), limit + 1)
                    : jdbc.query("SELECT " + COLUMNS + " FROM platform_enterprise_registration WHERE subject_id=? AND registration_id>? ORDER BY registration_id LIMIT ?",
                        (row, ordinal) -> row(row), subject.user().user_id(), cursor, limit + 1);
            var page = schema.object(); page.put("schema_version", "1.0.0"); var items = page.putArray("items");
            for (var record : records.subList(0, Math.min(limit, records.size()))) items.add(validate(record, subject.user().user_id()).required("data"));
            if (records.size() > limit) page.put("next_cursor", records.get(limit - 1).id()); else page.putNull("next_cursor");
            return schema.envelope("Page", page, UUID.randomUUID().toString(), null);
        }).get()));
    }

    JsonNode detail(ApplicationSessionIdentity.Verified subject, String id) {
        independent();
        return Objects.requireNonNull(transaction.execute(status -> ThrowingSupplier.of(() -> {
            lock(subject);
            var records = rows("subject_id=? AND registration_id=?", subject.user().user_id(), id);
            if (records.isEmpty()) throw new AccessDeniedException("ACCESS_DENIED");
            if (records.size() != 1) throw new IllegalStateException("REGISTRATION_ID_DUPLICATE");
            JsonNode data = validate(records.get(0), subject.user().user_id()).required("data");
            return schema.envelope("Detail", data, UUID.randomUUID().toString(), data.required("revision").textValue());
        }).get()));
    }

    void lock(ApplicationSessionIdentity.Verified subject) {
        if (!"local".equals(subject.binding().view().mode())) throw new AccessDeniedException("ACCESS_DENIED");
        String key = jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE", String.class);
        if (!"LOCAL".equals(key)) throw new AccessDeniedException("ACCESS_DENIED");
        identities.lockCurrent(subject);
        if (!"PLATFORM_ADMIN".equals(subject.user().platform_role())) throw new AccessDeniedException("ACCESS_DENIED");
    }
    String administratorRevision(String id) {
        var accounts = jdbc.query("SELECT revision FROM platform_local_account WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                (row, ordinal) -> row.getString(1), id);
        if (accounts.size() != 1) throw new AccessDeniedException("ACCESS_DENIED");
        com.ruoyi.medical.contracts.WireValues.revision(accounts.get(0)); return accounts.get(0);
    }
    private List<Row> rows(String condition, Object... args) {
        return jdbc.query("SELECT " + COLUMNS + " FROM platform_enterprise_registration WHERE " + condition, (row, ordinal) -> row(row), args);
    }
    JsonNode reviewApplication(String id) throws IOException {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("REGISTRATION_REVIEW_TRANSACTION_REQUIRED");
        var records = rows("registration_id=?", id);
        if (records.isEmpty()) throw new AccessDeniedException("ACCESS_DENIED");
        if (records.size() != 1) throw new IllegalStateException("REGISTRATION_ID_DUPLICATE");
        return validate(records.get(0), records.get(0).subject()).required("data");
    }
    private JsonNode validate(Row row, String subjectId) throws IOException {
        if (!row.subject().equals(subjectId)) throw new AccessDeniedException("ACCESS_DENIED");
        if (!row.version().equals("1.0.0") || !row.manifest().equals(schema.manifest()) || !row.status().equals("PENDING_REVIEW")
                || !row.revision().equals("1")) throw new IllegalStateException("REGISTRATION_METADATA_INVALID");
        JsonNode result = schema.readEnvelope(row.response());
        JsonNode detail = result.required("data"), application = detail.required("application");
        if (!row.id().equals(detail.required("registration_id").textValue()) || !row.operation().equals(detail.required("operation_id").textValue())
                || !row.subject().equals(detail.required("submitted_by").textValue()) || !row.admin().equals(application.required("first_admin_user_id").textValue())
                || !row.adminRevision().equals(detail.required("first_admin_revision").textValue()) || !row.contentHash().equals(detail.required("content_sha256").textValue())
                || !row.created().equals(detail.required("created_at").textValue())
                || !row.requestSha().equals(schema.requestHash(application, row.subject())) || !row.companySha().equals(schema.companyHash(application))
                || !schema.canonicalStoredApplication(row.application()).equals(schema.canonical(application))) throw new IllegalStateException("REGISTRATION_CONTENT_INVALID");
        var audits = jdbc.query("""
                SELECT resource_ref,schema_version,schema_manifest_sha256 FROM platform_enterprise_registration_audit
                WHERE registration_id=? AND operation_id=? AND subject_id=? AND first_admin_user_id=?
                """, (record, ordinal) -> {
                    if (!"1.0.0".equals(record.getString(2)) || !schema.manifest().equals(record.getString(3))) {
                        throw new IllegalStateException("REGISTRATION_AUDIT_METADATA_INVALID");
                    }
                    return record.getString(1);
                }, row.id(), row.operation(), row.subject(), row.admin());
        if (audits.size() != 1) throw new IllegalStateException("REGISTRATION_AUDIT_MISSING");
        JsonNode audit = schema.readAudit(audits.get(0));
        for (String field : java.util.List.of("registration_id", "operation_id", "content_sha256", "status", "revision", "submitted_by")) {
            if (!audit.required(field).equals(detail.required(field))) throw new IllegalStateException("REGISTRATION_AUDIT_CONTENT_INVALID");
        }
        if (!audit.required("request_sha256").textValue().equals(row.requestSha()) || !audit.required("first_admin_user_id").textValue().equals(row.admin())) {
            throw new IllegalStateException("REGISTRATION_AUDIT_IDENTITY_INVALID");
        }
        return result;
    }
    private static Row row(java.sql.ResultSet row) throws java.sql.SQLException {
        // MySQL JSON 会重新排版；正文使用成熟 JSON 解析器进行规范校验。
        return new Row(row.getString("registration_id"), row.getString("operation_id"), row.getString("subject_id"),
                row.getString("request_sha256"), row.getString("company_sha256"), row.getString("request_body"),
                row.getString("first_admin_user_id"), row.getString("first_admin_revision"), row.getString("content_sha256"),
                row.getString("response_ref"), row.getString("schema_version"), row.getString("schema_manifest_sha256"), row.getString("status"), row.getString("revision"),
                row.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC).toString());
    }
    private static void independent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("REGISTRATION_TRANSACTION_ACTIVE");
    }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void requireOne(int count) { if (count != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private record Row(String id, String operation, String subject, String requestSha, String companySha, String application,
            String admin, String adminRevision, String contentHash, String response, String version, String manifest, String status, String revision, String created) {
        @Override public String toString() { return "EnterpriseRegistrationRecord"; }
    }
}
