package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.LocalAccountProtocol.Action;
import com.ruoyi.medical.identity.mapper.LocalAccountMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.framework.web.service.SysPasswordService;
import java.io.IOException;
import java.security.GeneralSecurityException;
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
public final class LocalAccountManagement {
    private static final String COLUMNS = "user_id,username,display_name,status,platform_role,revision,credential_revision";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ApplicationSessionIdentity identities;
    private final LocalAccountSchema schema;
    private final IdentityDirectoryPolicy policy;
    private final FrameworkAccountOperations framework;
    private final SysPasswordService passwordService;
    private final LocalAccountMapper accountReads;

    public LocalAccountManagement(@Qualifier("masterDataSource") DataSource master, ApplicationSessionIdentity identities,
            LocalAccountSchema schema, IdentityDirectoryPolicy policy,
            FrameworkAccountOperations framework, SysPasswordService passwordService, LocalAccountMapper accountReads) {
        jdbc = new JdbcTemplate(master);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.identities = identities; this.schema = schema; this.policy = policy;
        this.framework = framework;
        this.passwordService = passwordService;
        this.accountReads = accountReads;
    }

    JsonNode list(ApplicationSessionIdentity.Verified subject, int limit, String cursor) {
        independent();
        return Objects.requireNonNull(transaction.execute(status -> {
            identities.lockCurrent(subject); administrator(subject);
            var rows = PageHelper.startPage(1, limit + 1, false).<LocalAccountProtocol.AccountItem>doSelectPageInfo(() ->
                    accountReads.selectAccounts(cursor)).getList();
            String next = rows.size() > limit ? rows.get(limit - 1).user_id() : null;
            var page = new LocalAccountProtocol.Page("1.0.0", rows.subList(0, Math.min(limit, rows.size())), next);
            return schema.envelope("LocalAccountPage", page, UUID.randomUUID().toString(), null);
        }));
    }

    com.ruoyi.common.core.domain.AjaxResult ownProfile(ApplicationSessionIdentity.Verified subject) {
        independent();
        return Objects.requireNonNull(transaction.execute(status -> {
            identities.lockCurrent(subject);
            var current = read(subject.user().user_id(), true).account();
            var result = framework.readOwnProfile();
            requireFramework(result, Action.OWN_PROFILE);
            result.put("account_revision", current.revision()); result.put("business_user_id", current.user_id());
            return result;
        }));
    }

    Result mutate(Action action, String path, String target, ApplicationSessionIdentity.Verified subject,
            LocalAccountSchema.Request request, String key) throws IOException, GeneralSecurityException {
        String digest = schema.digest(path, request);
        independent();
        return Objects.requireNonNull(transaction.execute(status -> ThrowingSupplier.of(() -> {
            jdbc.queryForObject("SELECT coordination_key FROM platform_local_bootstrap WHERE coordination_key='LOCAL' FOR UPDATE", String.class);
            identities.lockCurrent(subject);
            if (action != Action.CREDENTIAL && action != Action.OWN_PROFILE) administrator(subject);
            if ((action == Action.CREDENTIAL || action == Action.OWN_PROFILE) && !subject.user().user_id().equals(target)) denied();
            if (action == Action.OWN_PROFILE && !subject.user().user_id().equals(request.text("user_id"))) denied();
            framework.requirePermission(action);
            if (action == Action.RESET && subject.user().user_id().equals(target)) denied();
            var operations = jdbc.query("""
                    SELECT operation_id,request_hmac_sha256,hmac_key_id,http_status,response_ref,target_id,schema_version,schema_manifest_sha256
                    FROM platform_local_operation WHERE subject_id=? AND operation_name=? AND idempotency_key=?
                    """, (row, ordinal) -> new Operation(row.getString(1), row.getString(2), row.getString(3), row.getInt(4),
                            row.getString(5), row.getString(6), row.getString(7), row.getString(8)), subject.user().user_id(), action.id, key);
            if (operations.size() > 1) throw new IllegalStateException("LOCAL_OPERATION_DUPLICATE");
            if (!operations.isEmpty()) {
                var saved = operations.get(0);
                if (!schema.keyId().equals(saved.keyId()) || !schema.manifest(action).equals(saved.manifest()) || !"1.0.0".equals(saved.version())
                        || saved.status() != (action == Action.CREATE ? 201 : 200)) throw new IllegalStateException("LOCAL_OPERATION_INVALID");
                if (!digest.equals(saved.digest())) conflict(ErrorCode.IDEMPOTENCY_CONFLICT);
                JsonNode result = schema.readMutation(saved.response());
                if (!saved.id().equals(result.required("data").required("operation_id").textValue())
                        || !saved.target().equals(result.required("data").required("account").required("user_id").textValue())
                        || (action != Action.CREATE && !saved.target().equals(target))) throw new IllegalStateException("LOCAL_OPERATION_INVALID");
                read(saved.target(), true);
                return new Result(saved.status(), result);
            }
            String accountId = target;
            boolean credentialChanged = false;
            if (action == Action.CREATE) {
                accountId = UUID.randomUUID().toString();
                var user = new SysUser(); user.setUserName(request.text("username")); user.setNickName(request.text("display_name"));
                user.setPassword(request.text("password")); user.setStatus("0");
                // LOCAL-02没有框架角色或岗位输入，明确保存空关联并采用原Controller校验。
                user.setRoleIds(new Long[0]); user.setPostIds(new Long[0]);
                requireFramework(framework.create(user), action);
                if (user.getUserId() == null) throw new IllegalStateException("RUOYI_ACCOUNT_ID_REQUIRED");
                requireOne(jdbc.update("""
                        INSERT INTO platform_local_identity_metadata (user_id,ruoyi_user_id,platform_role,failed_attempts,
                            locked_until,credential_revision,created_by,created_at,classification,revision,updated_at)
                        VALUES (?,?,?,0,NULL,1,?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                        """, accountId, user.getUserId(), request.text("platform_role"),
                        subject.user().user_id(), policy.classification()));
            } else {
                Snapshot current = read(Objects.requireNonNull(target), true);
                var account = current.account();
                if (!account.revision().equals(request.text("expected_revision"))) conflict(ErrorCode.REVISION_CONFLICT);
                String revision = LocalAccountSchema.increment(account.revision());
                String displayName = account.display_name(); String role = account.platform_role(); String state = account.status();
                if (action == Action.STATE) {
                    state = request.text("decision").equals("ACTIVATE") ? "ACTIVE" : "DISABLED";
                    if (state.equals(account.status())) conflict(ErrorCode.REVISION_CONFLICT);
                    credentialChanged = true;
                } else if (action == Action.PROFILE) {
                    displayName = request.text("display_name"); role = request.text("platform_role");
                    if (displayName.equals(account.display_name()) && role.equals(account.platform_role())) conflict(ErrorCode.REVISION_CONFLICT);
                    credentialChanged = !role.equals(account.platform_role());
                } else if (action != Action.OWN_PROFILE) {
                    credentialChanged = true;
                }
                if (account.status().equals("ACTIVE") && account.platform_role().equals("PLATFORM_ADMIN")
                        && (!state.equals("ACTIVE") || !role.equals("PLATFORM_ADMIN"))) {
                    Long administrators = jdbc.queryForObject("""
                            SELECT COUNT(*) FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN' AND user_id<>?
                            """, Long.class, target);
                    if (Objects.requireNonNull(administrators) == 0) conflict(ErrorCode.REVISION_CONFLICT);
                }
                String credentialRevision = credentialChanged ? LocalAccountSchema.increment(account.credential_revision()) : account.credential_revision();
                var user = new SysUser(current.ruoyiUserId());
                if (action == Action.STATE) {
                    user.setStatus(state.equals("ACTIVE") ? "0" : "1"); requireFramework(framework.changeStatus(user), action);
                } else if (action == Action.PROFILE) {
                    requireFramework(framework.changeProfile(current.ruoyiUserId(), displayName), action);
                } else if (action == Action.CREDENTIAL) {
                    requireFramework(framework.changeOwnPassword(request.text("current_password"), request.text("password")), action);
                } else if (action == Action.OWN_PROFILE) {
                    requireFramework(framework.changeOwnProfile(request.text("nickName"), request.text("email"),
                            request.text("phonenumber"), request.text("sex")), action);
                } else {
                    user.setPassword(request.text("password")); requireFramework(framework.resetPassword(user), action);
                }
                requireOne(jdbc.update("""
                        UPDATE platform_local_identity_metadata SET platform_role=?,credential_revision=?,
                            failed_attempts=IF(?,0,failed_attempts),locked_until=IF(?,NULL,locked_until),revision=?,updated_at=UTC_TIMESTAMP(6)
                        WHERE user_id=? AND revision=?
                        """, role, credentialRevision,
                        credentialChanged, credentialChanged, revision, target, account.revision()));
                if (credentialChanged) jdbc.update("""
                        UPDATE platform_local_session SET status='REVOKED',revoked_at=UTC_TIMESTAMP(6),revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                        WHERE user_id=? AND status='ACTIVE'
                        """, target);
                if (credentialChanged) passwordService.clearLoginRecordCache(account.username());
            }
            var account = read(accountId, true).account();
            String operationId = UUID.randomUUID().toString();
            var mutation = new LocalAccountProtocol.Mutation("1.0.0", operationId, account,
                    credentialChanged && accountId.equals(subject.user().user_id()));
            JsonNode result = schema.envelope("LocalAccountMutation", mutation, UUID.randomUUID().toString(), account.revision());
            int httpStatus = action == Action.CREATE ? 201 : 200;
            requireOne(jdbc.update("""
                    INSERT INTO platform_local_operation (operation_id,subject_id,operation_name,idempotency_key,request_hmac_sha256,hmac_key_id,
                        http_status,response_ref,target_id,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                    VALUES (?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
                    """, operationId, subject.user().user_id(), action.id, key, digest, schema.keyId(), httpStatus,
                    schema.canonical(result), accountId, schema.manifest(action), subject.user().user_id(), policy.classification()));
            requireOne(jdbc.update("""
                    INSERT INTO platform_local_audit (audit_id,operation_id,subject_id,target_id,action,resource_ref,schema_version,
                        schema_manifest_sha256,created_by,created_at,classification)
                    VALUES (?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),?)
                    """, UUID.randomUUID().toString(), operationId, subject.user().user_id(), accountId, action.id, schema.audit(account),
                    schema.manifest(action), subject.user().user_id(), policy.classification()));
            return new Result(httpStatus, result);
        }).get()));
    }

    private Snapshot read(String id, boolean lock) {
        WireValues.uuid(id);
        var rows = jdbc.query("SELECT " + COLUMNS + ",ruoyi_user_id FROM platform_local_account WHERE user_id=?" + (lock ? " FOR UPDATE" : ""),
                (row, ordinal) -> new Snapshot(item(row), row.getLong("ruoyi_user_id")), id);
        if (rows.isEmpty()) denied();
        if (rows.size() != 1) throw new IllegalStateException("LOCAL_ACCOUNT_DUPLICATE");
        return rows.get(0);
    }
    private static LocalAccountProtocol.AccountItem item(java.sql.ResultSet row) throws java.sql.SQLException {
        var item = new LocalAccountProtocol.AccountItem(row.getString("user_id"), row.getString("username"), row.getString("display_name"),
                row.getString("status"), row.getString("platform_role"), row.getString("revision"), row.getString("credential_revision"));
        WireValues.uuid(item.user_id()); WireValues.revision(item.revision()); WireValues.revision(item.credential_revision()); return item;
    }
    private static void administrator(ApplicationSessionIdentity.Verified subject) {
        if (!"PLATFORM_ADMIN".equals(subject.user().platform_role())) denied();
    }
    private static void independent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("LOCAL_MANAGEMENT_TRANSACTION_ACTIVE");
    }
    private static void denied() { throw new AccessDeniedException("ACCESS_DENIED"); }
    private static void conflict(ErrorCode code) { throw new LocalAccountConflict(code); }
    private static void requireFramework(com.ruoyi.common.core.domain.AjaxResult result, Action action) {
        if (!Objects.requireNonNull(result, "RUOYI_ACCOUNT_RESULT_REQUIRED").isSuccess()) {
            conflict(action == Action.CREDENTIAL ? ErrorCode.AUTHENTICATION_FAILED : ErrorCode.REVISION_CONFLICT);
        }
    }
    private static void requireOne(int count) { if (count != 1) conflict(ErrorCode.REVISION_CONFLICT); }
    private record Snapshot(LocalAccountProtocol.AccountItem account, long ruoyiUserId) { }
    private record Operation(String id, String digest, String keyId, int status, String response, String target, String version, String manifest) { }
    record Result(int status, JsonNode body) { }
}
