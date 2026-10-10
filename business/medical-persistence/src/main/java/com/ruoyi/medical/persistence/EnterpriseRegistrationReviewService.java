package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.EnterpriseProvisioningMapper;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.function.ThrowingSupplier;

@Component
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class EnterpriseRegistrationReviewService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final EnterpriseRegistrationService registrations;
    private final EnterpriseRegistrationSchema applications;
    private final EnterpriseRegistrationReviewSchema schema;
    private final PermissionSchema permissions;
    private final EnterpriseProvisioningMapper enterprise;
    private final IdentityDirectoryPolicy policy;
    private static final String COLUMNS = "registration_id,operation_id,subject_id,request_sha256,approved_company_sha256,decision,"
            + "tenant_id,member_id,content_sha256,response_ref,schema_version,schema_manifest_sha256,created_at";

    public EnterpriseRegistrationReviewService(@Qualifier("masterDataSource") DataSource master, EnterpriseRegistrationService registrations,
            EnterpriseRegistrationSchema applications, EnterpriseRegistrationReviewSchema schema, PermissionSchema permissions,
            EnterpriseProvisioningMapper enterprise, IdentityDirectoryPolicy policy) {
        jdbc = new JdbcTemplate(master); transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.registrations = registrations; this.applications = applications; this.schema = schema;
        this.permissions = permissions; this.enterprise = enterprise; this.policy = policy;
    }

    JsonNode list(ApplicationSessionIdentity.Verified actor, int limit, String cursor, String state) {
        return execute(actor, () -> {
            String desired = state == null ? "PENDING_REVIEW" : state;
            if (limit < 1 || limit > 100 || !List.of("PENDING_REVIEW", "APPROVED", "REJECTED").contains(desired)) throw new IllegalArgumentException("REVIEW_QUERY_INVALID");
            String condition = desired.equals("PENDING_REVIEW") ? "d.registration_id IS NULL" : "d.decision=?";
            var values = new java.util.ArrayList<Object>();
            if (!desired.equals("PENDING_REVIEW")) values.add(desired.equals("APPROVED") ? "APPROVE" : "REJECT");
            if (cursor != null) { condition += " AND r.registration_id>?"; values.add(cursor); }
            values.add(limit + 1);
            var ids = jdbc.query("SELECT r.registration_id FROM platform_enterprise_registration r LEFT JOIN platform_enterprise_registration_decision d "
                    + "ON d.registration_id=r.registration_id WHERE " + condition + " ORDER BY r.registration_id LIMIT ?",
                    (row, ordinal) -> row.getString(1), values.toArray());
            var page = schema.object(); page.put("schema_version", "1.0.0"); var items = page.putArray("items");
            for (String id : ids.subList(0, Math.min(limit, ids.size()))) items.add(current(id));
            if (ids.size() > limit) page.put("next_cursor", ids.get(limit - 1)); else page.putNull("next_cursor");
            return schema.envelope("Page", page, uuid(), null);
        });
    }
    JsonNode detail(ApplicationSessionIdentity.Verified actor, String id) {
        return execute(actor, () -> { JsonNode data = current(id); return schema.envelope("Detail", data, uuid(), data.required("revision").textValue()); });
    }
    JsonNode decide(ApplicationSessionIdentity.Verified actor, String id, byte[] bytes, String key) {
        var observed = records("registration_id=?", id);
        if (observed.size() > 1) throw new IllegalStateException("REVIEW_DECISION_DUPLICATE");
        String lockedTenant = observed.isEmpty() ? null : observed.get(0).tenant();
        return execute(actor, lockedTenant, () -> {
            // 原申请和同步审计必须存在，才能处理请求正文及批准内容。
            JsonNode application = registrations.reviewApplication(id);
            JsonNode input = schema.request(bytes, key);
            String digest = schema.requestHash(input, id, actor.user().user_id());
            var previous = records("subject_id=? AND idempotency_key=?", actor.user().user_id(), key);
            if (previous.size() > 1) throw new IllegalStateException("REVIEW_REQUEST_DUPLICATE");
            if (!previous.isEmpty()) {
                var row = previous.get(0);
                if (!digest.equals(row.digest()) || !id.equals(row.id())) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
                JsonNode result = validate(row, application);
                if (row.decision().equals("APPROVE")) {
                    // 已有企业先锁定水位，再核查认证，保持撤权操作的相同顺序。
                    if (!row.tenant().equals(lockedTenant)) denied(ErrorCode.SCOPE_STALE);
                    verifyEnterprise(actor, result.required("data"));
                }
                return result;
            }
            if (!records("registration_id=?", id).isEmpty()) denied(ErrorCode.REVISION_CONFLICT);
            if (!input.required("expected_revision").textValue().equals("1")) denied(ErrorCode.REVISION_CONFLICT);
            String companyHash = applications.companyHash(application.required("application"));
            boolean approved = input.required("decision").textValue().equals("APPROVE");
            if (approved && !records("approved_company_sha256=?", companyHash).isEmpty()) denied(ErrorCode.REVISION_CONFLICT);
            if (approved && !registrations.administratorRevision(application.required("first_admin_binding").required("user_id").textValue())
                    .equals(application.required("first_admin_revision").textValue())) denied(ErrorCode.SCOPE_STALE);
            Timestamp timestamp = Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class));
            String now = timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
            var receipt = schema.object(); receipt.put("schema_version", "1.0.0"); receipt.put("registration_id", id);
            receipt.put("operation_id", uuid()); receipt.set("request", input); receipt.set("decision", input.required("decision"));
            receipt.put("status", approved ? "APPROVED" : "REJECTED"); receipt.put("revision", "2");
            receipt.set("registration_content_sha256", application.required("content_sha256"));
            receipt.put("decided_by", actor.user().user_id()); receipt.put("decided_at", now);
            if (approved) { receipt.put("tenant_id", uuid()); receipt.put("member_id", uuid()); } else { receipt.putNull("tenant_id"); receipt.putNull("member_id"); }
            var grants = receipt.putArray("governance_grants");
            for (JsonNode requested : input.required("governance_grants")) {
                JsonNode validTo = requested.required("valid_to");
                if (!validTo.isNull() && !PermissionAuthority.time(validTo.textValue()).isAfter(timestamp.toLocalDateTime())) denied(ErrorCode.SCOPE_STALE);
                var grant = requested.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) grant).put("grant_id", uuid());
                ((com.fasterxml.jackson.databind.node.ObjectNode) grant).put("valid_from", now);
                ((com.fasterxml.jackson.databind.node.ObjectNode) grant).put("revision", "1"); grants.add(grant);
            }
            if (approved) { var basis = receipt.putObject("basis"); basis.put("version_id", uuid()); basis.put("content_sha256", schema.hash(schema.basisContent(receipt))); }
            else receipt.putNull("basis");
            receipt.put("content_sha256", schema.hash(receipt));
            JsonNode result = schema.envelope("Receipt", receipt, uuid(), "2");
            if (approved) provision(actor, application, receipt, timestamp);
            one(jdbc.update("""
                    INSERT INTO platform_enterprise_registration_decision
                      (registration_id,operation_id,subject_id,idempotency_key,request_sha256,approved_company_sha256,decision,tenant_id,member_id,
                       content_sha256,response_ref,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)
                    """, id, receipt.required("operation_id").textValue(), actor.user().user_id(), key, digest, approved ? companyHash : null,
                    receipt.required("decision").textValue(), nullable(receipt, "tenant_id"), nullable(receipt, "member_id"), receipt.required("content_sha256").textValue(),
                    schema.canonical(result), schema.manifest(), actor.user().user_id(), timestamp, policy.classification()));
            one(jdbc.update("""
                    INSERT INTO platform_enterprise_registration_decision_audit
                      (audit_id,registration_id,operation_id,subject_id,resource_ref,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                    VALUES (?,?,?,?,?,'1.0.0',?,?,?,?)
                    """, uuid(), id, receipt.required("operation_id").textValue(), actor.user().user_id(), schema.canonical(receipt),
                    schema.manifest(), actor.user().user_id(), timestamp, policy.classification()));
            var saved = records("registration_id=?", id);
            if (saved.size() != 1 || !result.equals(validate(saved.get(0), application))) throw new IllegalStateException("REVIEW_PERSISTENCE_INCOMPLETE");
            if (approved) verifyEnterprise(actor, receipt);
            return result;
        });
    }

    private void provision(ApplicationSessionIdentity.Verified actor, JsonNode application, JsonNode receipt, Timestamp now) throws IOException {
        String tenant = receipt.required("tenant_id").textValue(), admin = application.required("first_admin_binding").required("user_id").textValue();
        var values = enterpriseValues(actor, receipt, admin); values.put("now", now.toLocalDateTime());
        values.put("identityId", uuid()); values.put("classification", policy.classification()); values.put("permissionManifest", permissions.manifestSha256());
        try (var scope = TenantScope.openProvisioning(actor, tenant)) {
            one(enterprise.insertTenant(values)); one(enterprise.insertBinding(values)); one(enterprise.insertMember(values)); one(enterprise.insertBasis(values));
            for (JsonNode grant : receipt.required("governance_grants")) {
                values.put("grantId", grant.required("grant_id").textValue()); values.put("action", grant.required("action").textValue());
                values.put("condition", schema.canonical(grant.required("condition")));
                values.put("validTo", grant.required("valid_to").isNull() ? null : PermissionAuthority.time(grant.required("valid_to").textValue()));
                one(enterprise.insertGrant(values));
            }
            values.put("eventId", uuid()); values.put("payload", schema.canonical(receipt)); one(enterprise.saveEvent(values));
        }
        one(jdbc.update("""
                INSERT INTO platform_member_route (user_id,tenant_id,member_id,created_by,created_at,classification,revision,updated_at)
                VALUES (?,?,?,?,?,?,1,?)
                """, admin, tenant, receipt.required("member_id").textValue(), actor.user().user_id(), now, policy.classification(), now));
    }
    private void verifyEnterprise(ApplicationSessionIdentity.Verified actor, JsonNode receipt) throws IOException {
        JsonNode application = registrations.reviewApplication(receipt.required("registration_id").textValue());
        String admin = application.required("first_admin_binding").required("user_id").textValue();
        registrations.administratorRevision(admin);
        var values = enterpriseValues(actor, receipt, admin);
        try (var scope = TenantScope.openProvisioning(actor, receipt.required("tenant_id").textValue())) {
            var state = enterprise.state(values);
            if (state.size() != 1) denied(ErrorCode.SCOPE_STALE);
            var member = state.get(0);
            if (!"ACTIVE".equals(member.get("member_status")) || !"ACTIVE".equals(member.get("binding_status"))
                    || !"local".equals(member.get("mode")) || !admin.equals(member.get("local_user_id")) || !admin.equals(member.get("user_id"))
                    || member.get("issuer") != null || member.get("subject") != null
                    || !values.get("basisHash").equals(member.get("content_sha256")) || !"ENTERPRISE_GOVERNANCE".equals(member.get("entity_kind"))) denied(ErrorCode.SCOPE_STALE);
            var grants = enterprise.grants(values);
            if (grants.size() != receipt.required("governance_grants").size()) denied(ErrorCode.SCOPE_STALE);
            Instant now = Objects.requireNonNull(jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class)).toLocalDateTime().toInstant(ZoneOffset.UTC);
            for (JsonNode expected : receipt.required("governance_grants")) {
                var matching = grants.stream().filter(grant -> grant.grantId().equals(expected.required("grant_id").textValue())).toList();
                if (matching.size() != 1) denied(ErrorCode.SCOPE_STALE);
                var grant = matching.get(0);
                if (!grant.status().equals("ACTIVE") || !grant.revision().toString().equals("1") || !grant.subjectId().equals(admin)
                        || grant.projectId() != null || !grant.resourceKind().equals("ENTERPRISE") || !grant.resourceId().equals(receipt.required("tenant_id").textValue())
                        || !grant.basisVersion().equals(values.get("basisId")) || !grant.action().equals(expected.required("action").textValue())
                        || !permissions.manifestSha256().equals(grant.manifest()) || !"1.0.0".equals(grant.schemaVersion())
                        || !schema.parse(grant.condition()).equals(expected.required("condition"))
                        || !grant.validFrom().equals(PermissionAuthority.time(expected.required("valid_from").textValue()))
                        || !Objects.equals(grant.validTo(), expected.required("valid_to").isNull() ? null : PermissionAuthority.time(expected.required("valid_to").textValue()))
                        || grant.validTo() != null && !grant.validTo().toInstant(ZoneOffset.UTC).isAfter(now)) denied(ErrorCode.SCOPE_STALE);
            }
        }
        var routes = jdbc.query("SELECT member_id FROM platform_member_route WHERE user_id=? AND tenant_id=?", (row, ordinal) -> row.getString(1), admin, receipt.required("tenant_id").textValue());
        if (routes.size() != 1 || !routes.get(0).equals(receipt.required("member_id").textValue())) denied(ErrorCode.SCOPE_STALE);
    }
    private Map<String,Object> enterpriseValues(ApplicationSessionIdentity.Verified actor, JsonNode receipt, String admin) {
        var values = new HashMap<String,Object>(); values.put("userId", actor.user().user_id()); values.put("adminId", admin);
        values.put("memberId", receipt.required("member_id").textValue()); values.put("enterpriseId", receipt.required("tenant_id").textValue());
        values.put("basisId", receipt.required("basis").required("version_id").textValue()); values.put("basisHash", receipt.required("basis").required("content_sha256").textValue());
        return values;
    }
    private JsonNode current(String id) throws IOException {
        JsonNode application = registrations.reviewApplication(id);
        var row = records("registration_id=?", id);
        if (row.size() > 1) throw new IllegalStateException("REVIEW_DECISION_DUPLICATE");
        var detail = schema.object(); detail.put("schema_version", "1.0.0"); detail.set("application", application);
        if (row.isEmpty()) { detail.putNull("review"); detail.put("status", "PENDING_REVIEW"); detail.put("revision", "1"); }
        else {
            JsonNode receipt = validate(row.get(0), application).required("data"); detail.set("review", receipt);
            detail.set("status", receipt.required("status")); detail.set("revision", receipt.required("revision"));
        }
        schema.verify("Detail", detail); return detail;
    }
    private JsonNode validate(Row row, JsonNode application) throws IOException {
        if (!row.version().equals("1.0.0") || !row.manifest().equals(schema.manifest())) throw new IllegalStateException("REVIEW_METADATA_INVALID");
        JsonNode result = schema.readEnvelope(row.response()), receipt = result.required("data");
        if (!row.id().equals(receipt.required("registration_id").textValue()) || !row.operation().equals(receipt.required("operation_id").textValue())
                || !row.subject().equals(receipt.required("decided_by").textValue()) || !row.decision().equals(receipt.required("decision").textValue())
                || !row.content().equals(receipt.required("content_sha256").textValue()) || !row.created().equals(receipt.required("decided_at").textValue())
                || !row.digest().equals(schema.requestHash(receipt.required("request"), row.id(), row.subject()))
                || !Objects.equals(row.tenant(), nullable(receipt, "tenant_id")) || !Objects.equals(row.member(), nullable(receipt, "member_id"))
                || !application.required("content_sha256").equals(receipt.required("registration_content_sha256"))
                || !Objects.equals(row.company(), row.decision().equals("APPROVE") ? applications.companyHash(application.required("application")) : null)) throw new IllegalStateException("REVIEW_CONTENT_INVALID");
        var audits = jdbc.query("""
                SELECT resource_ref FROM platform_enterprise_registration_decision_audit
                WHERE registration_id=? AND operation_id=? AND subject_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=?
                """, (record, ordinal) -> record.getString(1), row.id(), row.operation(), row.subject(), schema.manifest());
        if (audits.size() != 1 || !receipt.equals(schema.parse(audits.get(0)))) throw new IllegalStateException("REVIEW_AUDIT_INVALID");
        return result;
    }
    private List<Row> records(String condition, Object... values) {
        return jdbc.query("SELECT " + COLUMNS + " FROM platform_enterprise_registration_decision WHERE " + condition, (row, ordinal) ->
                new Row(row.getString("registration_id"),row.getString("operation_id"),row.getString("subject_id"),row.getString("request_sha256"),
                    row.getString("approved_company_sha256"),row.getString("decision"),row.getString("tenant_id"),row.getString("member_id"),
                    row.getString("content_sha256"),row.getString("response_ref"),row.getString("schema_version"),row.getString("schema_manifest_sha256"),
                    row.getTimestamp("created_at").toLocalDateTime().toInstant(ZoneOffset.UTC).toString()), values);
    }
    private JsonNode execute(ApplicationSessionIdentity.Verified actor, ThrowingSupplier<JsonNode> operation) {
        return execute(actor, null, operation);
    }
    private JsonNode execute(ApplicationSessionIdentity.Verified actor, String tenant, ThrowingSupplier<JsonNode> operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("REVIEW_TRANSACTION_ACTIVE");
        return Objects.requireNonNull(transaction.execute(status -> ThrowingSupplier.of(() -> {
            if (tenant != null) {
                try (var scope = TenantScope.openProvisioning(actor, tenant)) {
                    if (enterprise.lockTenant(Map.of("userId", actor.user().user_id())).size() != 1) denied(ErrorCode.SCOPE_STALE);
                }
            }
            registrations.lock(actor); return operation.getWithException();
        }).get()));
    }
    private static String nullable(JsonNode node, String field) { return node.required(field).isNull() ? null : node.required(field).textValue(); }
    private static String uuid() { return UUID.randomUUID().toString(); }
    private static void one(int count) { if (count != 1) denied(ErrorCode.REVISION_CONFLICT); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private record Row(String id,String operation,String subject,String digest,String company,String decision,String tenant,String member,
            String content,String response,String version,String manifest,String created) { @Override public String toString() { return "EnterpriseRegistrationDecision"; } }
}
