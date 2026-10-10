package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.identity.InvitationCredential;
import com.ruoyi.medical.persistence.PermissionSchema;
import com.ruoyi.medical.persistence.SimulationAccountData;
import com.ruoyi.medical.persistence.SimulationTaskData;
import com.ruoyi.medical.persistence.TaskInputSchema;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

public final class MemberGovernanceSimulationHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";

    @Test void actualInvitationsAcceptedGrantsRevocationsAndMemberDisableReachConsumers() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var service = secret(root, "service-secrets"); var management = secret(root, "local-management-secret");
        var invitation = secret(root, "invitation-response-secret"); var token = secret(root, "ruoyi-token-secret");
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
            new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
                "MEDICAL_LOCAL_DATABASE_PASSWORD", service.required("database_password").textValue(),
                "MEDICAL_LOCAL_REDIS_PASSWORD", service.required("redis_password").textValue(),
                "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                "MEDICAL_RUOYI_TOKEN_SECRET", token.required("key").textValue()))
            .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                "--server.shutdown=immediate", "--spring.main.banner-mode=off", "--medical.member-governance.maximum-read-candidates=100"));
        var evidence = JSON.createObjectNode();
        try (application) {
            var source = application.getBean("masterDataSource", javax.sql.DataSource.class);
            var jdbc = new JdbcTemplate(source); var before = counts(jdbc);
            String base = "http://127.0.0.1:" + application.getWebServer().getPort();
            var owner = login(base, secret(root, "owner-credentials"));
            String actor = JSON.readTree(get(owner, base, "/auth/account", null).body()).required("user_id").textValue();
            try (var accounts = new SimulationAccountData(source, actor)) {
                var acceptedAccount = account(owner, base, accounts); var pendingAccount = account(owner, base, accounts);
                var emptyAccount = account(owner, base, accounts);
                assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE dept_id IS NULL AND user_name IN (?,?,?)", Integer.class,
                    acceptedAccount.required("username").textValue(), pendingAccount.required("username").textValue(), emptyAccount.required("username").textValue()));
                var acceptedHttp = login(base, acceptedAccount); var pendingHttp = login(base, pendingAccount);
                var emptyHttp = login(base, emptyAccount);
                try (var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                    data.enableRoleAdministration(); data.enableMemberAdministration();
                    select(owner, base, data.tenant);
                    var initialMembers = governance(owner, base, "/api/enterprise-members", null);
                    assertEquals(2, initialMembers.required("items").size());
                    assertTrue(initialMembers.required("next_after").isNull());
                    var firstMemberPage = governance(owner, base, "/api/enterprise-members?limit=1", null);
                    assertEquals(1, firstMemberPage.required("items").size());
                    String nextMember = firstMemberPage.required("next_after").textValue();
                    assertEquals(firstMemberPage.required("items").get(0).required("member_id").textValue(), nextMember);
                    var secondMemberPage = governance(owner, base, "/api/enterprise-members?limit=1&after=" + nextMember
                        + "&dataset_watermark=" + firstMemberPage.required("dataset_watermark").textValue(), null);
                    assertEquals(firstMemberPage.required("dataset_watermark"), secondMemberPage.required("dataset_watermark"));
                    assertEquals(initialMembers.required("items").get(1), secondMemberPage.required("items").get(0));
                    assertTrue(secondMemberPage.required("next_after").isNull());
                    assertEquals(422, get(owner, base, "/api/enterprise-members?limit=0", context(owner, base)).statusCode());
                    assertEquals(422, get(owner, base, "/api/enterprise-members?limit=1&limit=2", context(owner, base)).statusCode());
                    assertEquals(422, get(owner, base, "/api/enterprise-members?after=" + nextMember, context(owner, base)).statusCode());
                    assertEquals(422, get(owner, base, "/api/enterprise-members?tenant_id=" + data.otherTenant, context(owner, base)).statusCode());
                    assertEquals(422, get(owner, base, "/api/enterprise-members", null).statusCode());
                    var roleRequest = role(data, data.role, false, "0");
                    var published = success(post(owner, base, "/api/enterprise-roles/" + data.role + "/versions", context(owner, base), UUID.randomUUID().toString(), roleRequest), 201).required("role");
                    var emptyRole = success(post(owner, base, "/api/enterprise-roles/" + data.secondRole + "/versions", context(owner, base), UUID.randomUUID().toString(), role(data, data.secondRole, true, "0")), 201).required("role");
                    String acceptedUser = acceptedAccount.required("user_id").textValue(), pendingUser = pendingAccount.required("user_id").textValue();
                    var invitationRequest = invite(data, acceptedUser, published);
                    String invitationContext = context(owner, base), invitationKey = UUID.randomUUID().toString();
                    var createdResponse = post(owner, base, "/api/member-invitations", invitationContext, invitationKey, invitationRequest);
                    var created = success(createdResponse, 201);
                    var invitedMembers = governance(owner, base, "/api/enterprise-members?subject_id=" + acceptedUser, null);
                    var invitedMember = assertInstanceOf(ObjectNode.class, invitedMembers.required("items").get(0));
                    assertEquals(1, invitedMembers.required("items").size());
                    assertEquals("INVITED", invitedMember.required("status").textValue());
                    assertEquals("1", invitedMember.required("revision").textValue());
                    var invitationPage = governance(owner, base, "/api/member-invitations?subject_id=" + acceptedUser, null);
                    var invitationRead = listed(invitationPage, "invitation_id", created.required("invitation_id").textValue());
                    assertEquals("PENDING", invitationRead.required("status").textValue());
                    assertEquals(invitationRequest.required("roles"), invitationRead.required("roles"));
                    assertEquals(invitationRequest.required("scope"), invitationRead.required("scope"));
                    assertEquals(invitationRequest.required("basis"), invitationRead.required("basis"));
                    assertFalse(invitationPage.toString().contains(created.required("credential").textValue()));
                    assertFalse(invitationRead.has("credential")); assertFalse(invitationRead.has("credential_sha256"));
                    assertEquals(409, get(owner, base, "/api/enterprise-members?dataset_watermark="
                        + initialMembers.required("dataset_watermark").textValue(), context(owner, base)).statusCode());
                    assertTrue(governance(owner, base, "/api/enterprise-members?subject_id=" + data.otherOwner, null)
                        .required("items").get(0).required("user_id").textValue().equals(data.otherOwner));
                    assertTrue(governance(owner, base, "/api/enterprise-members?subject_id=" + UUID.randomUUID(), null).required("items").isEmpty());
                    var replayedCreation = post(owner, base, "/api/member-invitations", invitationContext, invitationKey, invitationRequest);
                    assertEquals(JSON.readTree(createdResponse.body()), JSON.readTree(replayedCreation.body()));
                    assertEquals(409, post(owner, base, "/api/member-invitations", invitationContext, invitationKey,
                        invitationRequest.deepCopy().put("expires_at", Instant.now().plusSeconds(259200).truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString())).statusCode());
                    var pending = success(post(owner, base, "/api/member-invitations", context(owner, base), UUID.randomUUID().toString(), invite(data, pendingUser, published)), 201);
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM platform_invitation_route WHERE user_id=? AND invitation_id=? AND tenant_id=?", Integer.class,
                        acceptedUser, created.required("invitation_id").textValue(), data.tenant));
                    String savedResponse = jdbc.queryForObject("SELECT response_ref FROM operation_record WHERE tenant_id=? AND operation_id=?", String.class,
                        data.tenant, created.required("operation_id").textValue());
                    assertEquals("AES-256-GCM", JSON.readTree(savedResponse).required("algorithm").textValue());
                    assertFalse(savedResponse.contains(created.required("credential").textValue()));
                    assertFalse(JSON.writeValueAsString(jdbc.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=?", data.tenant))
                        .contains(created.required("credential").textValue()));
                    assertTrue(success(get(acceptedHttp, base, "/api/session", null), 200).required("memberships").isEmpty());
                    var newerRole = success(post(owner, base, "/api/enterprise-roles/" + data.role + "/versions", context(owner, base), UUID.randomUUID().toString(),
                        roleRequest.deepCopy().put("expected_head_revision", "1").put("name", "SIMULATION 当前岗位")), 201).required("role");
                    assertEquals(409, post(owner, base, "/api/member-invitations", context(owner, base), UUID.randomUUID().toString(), invite(data, emptyAccount.required("user_id").textValue(), published)).statusCode());
                    String acceptPath = "/api/member-invitations/" + created.required("invitation_id").textValue() + "/acceptances";
                    var acceptBody = accept(created); String acceptanceKey = UUID.randomUUID().toString();
                    assertEquals(403, post(owner, base, acceptPath, null, UUID.randomUUID().toString(), acceptBody).statusCode());
                    assertEquals(403, post(acceptedHttp, base, acceptPath, null, UUID.randomUUID().toString(),
                        acceptBody.deepCopy().put("credential", InvitationCredential.issue().secret())).statusCode());
                    var acceptedResponse = post(acceptedHttp, base, acceptPath, null, acceptanceKey, acceptBody);
                    var accepted = success(acceptedResponse, 200);
                    assertEquals(JSON.readTree(acceptedResponse.body()), JSON.readTree(post(acceptedHttp, base, acceptPath, null, acceptanceKey, acceptBody).body()));
                    assertEquals(409, post(acceptedHttp, base, acceptPath, null, acceptanceKey, acceptBody.deepCopy().put("expected_revision", "2")).statusCode());
                    assertEquals(2, accepted.required("grants").size());
                    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM member_role_grant WHERE tenant_id=? AND invitation_id=? AND role_version=?", Integer.class,
                        data.tenant, created.required("invitation_id").textValue(), published.required("version_id").textValue()));
                    var acceptedSession = success(get(acceptedHttp, base, "/api/session", null), 200);
                    assertTrue(acceptedSession.required("current_context").isNull());
                    assertEquals(1, acceptedSession.required("memberships").size());
                    select(acceptedHttp, base, data.tenant);
                    assertEquals(200, get(acceptedHttp, base, "/api/tasks/" + data.first, context(acceptedHttp, base)).statusCode());
                    assertEquals(200, get(acceptedHttp, base, "/api/projects/" + data.project, context(acceptedHttp, base)).statusCode());
                    assertEquals(409, get(owner, base, "/api/session", null).statusCode());
                    select(owner, base, data.tenant);
                    assertEquals(409, get(owner, base, "/api/member-invitations", invitationContext).statusCode());
                    assertEquals("ACTIVE", listed(governance(owner, base, "/api/enterprise-members?subject_id=" + acceptedUser, null),
                        "member_id", accepted.required("member_id").textValue()).required("status").textValue());
                    assertEquals("ACCEPTED", listed(governance(owner, base, "/api/member-invitations?subject_id=" + acceptedUser, null),
                        "invitation_id", created.required("invitation_id").textValue()).required("status").textValue());
                    var roleGrants = governance(owner, base, "/api/permission-grants?subject_id=" + acceptedUser, null);
                    assertEquals(2, roleGrants.required("items").size());
                    for (var id : accepted.required("grants")) {
                        var storedGrant = listed(roleGrants, "grant_id", id.textValue());
                        assertEquals("ACTIVE", storedGrant.required("status").textValue());
                        assertEquals("1", storedGrant.required("revision").textValue());
                        assertEquals(scope(data), storedGrant.required("scope"));
                    }
                    for (String path : List.of("/api/enterprise-members", "/api/permission-grants", "/api/member-invitations"))
                        assertEquals(403, get(acceptedHttp, base, path, context(acceptedHttp, base)).statusCode());
                    var emptyInvitation = success(post(owner, base, "/api/member-invitations", context(owner, base), UUID.randomUUID().toString(), invite(data, emptyAccount.required("user_id").textValue(), emptyRole)), 201);
                    var emptyAccepted = success(post(emptyHttp, base, "/api/member-invitations/" + emptyInvitation.required("invitation_id").textValue() + "/acceptances",
                        null, UUID.randomUUID().toString(), accept(emptyInvitation)), 200);
                    assertTrue(emptyAccepted.required("grants").isEmpty());
                    select(emptyHttp, base, data.tenant);
                    assertEquals(403, get(emptyHttp, base, "/api/projects/" + data.project, context(emptyHttp, base)).statusCode());
                    select(owner, base, data.tenant);
                    var grantBody = permission(data, acceptedUser, "GRANT", null, "0");
                    String grantContext = context(owner, base), grantKey = UUID.randomUUID().toString();
                    var grantedResponse = post(owner, base, "/api/permission-decisions", grantContext, grantKey, grantBody);
                    var granted = success(grantedResponse, 200); String independent = granted.required("grant").required("id").textValue();
                    assertEquals(JSON.readTree(grantedResponse.body()), JSON.readTree(post(owner, base, "/api/permission-decisions", grantContext, grantKey, grantBody).body()));
                    var independentRead = listed(governance(owner, base, "/api/permission-grants?subject_id=" + acceptedUser, null), "grant_id", independent);
                    assertEquals(grantBody.required("condition"), independentRead.required("condition"));
                    assertEquals(grantBody.required("basis"), independentRead.required("basis"));
                    data.enableProjectGrantAdministration(emptyAccount.required("user_id").textValue());
                    select(emptyHttp, base, data.tenant);
                    var projectManaged = governance(emptyHttp, base, "/api/permission-grants?subject_id=" + acceptedUser, null);
                    assertEquals(2, projectManaged.required("items").size());
                    for (var item : projectManaged.required("items")) {
                        assertEquals("project.read", item.required("action").textValue()); assertEquals(scope(data), item.required("scope"));
                    }
                    assertEquals(403, get(emptyHttp, base, "/api/enterprise-members", context(emptyHttp, base)).statusCode());
                    assertEquals(403, get(emptyHttp, base, "/api/member-invitations", context(emptyHttp, base)).statusCode());
                    data.enableScopedMemberAdministration(emptyAccount.required("user_id").textValue(), accepted.required("member_id").textValue());
                    var scopedMembers = governance(emptyHttp, base, "/api/enterprise-members?limit=1", null);
                    assertEquals(1, scopedMembers.required("items").size()); assertTrue(scopedMembers.required("next_after").isNull());
                    assertEquals(acceptedUser, scopedMembers.required("items").get(0).required("user_id").textValue());
                    assertTrue(governance(emptyHttp, base, "/api/enterprise-members?subject_id=" + actor, null).required("items").isEmpty());
                    assertEquals(403, get(emptyHttp, base, "/api/member-invitations", context(emptyHttp, base)).statusCode());
                    var excessive = grantBody.deepCopy().put("action", "task.create");
                    assertEquals(403, post(owner, base, "/api/permission-decisions", context(owner, base), UUID.randomUUID().toString(), excessive).statusCode());
                    var otherTenant = grantBody.deepCopy(); assertInstanceOf(ObjectNode.class, otherTenant.required("scope")).put("tenant_id", data.otherTenant);
                    assertEquals(403, post(owner, base, "/api/permission-decisions", context(owner, base), UUID.randomUUID().toString(), otherTenant).statusCode());
                    select(acceptedHttp, base, data.tenant); String staleContext = context(acceptedHttp, base);
                    String revokePath = "/api/enterprise-roles/" + data.role + "/versions/" + published.required("version_id").textValue() + "/revocations";
                    var revokeBody = JSON.createObjectNode().put("schema_version", "1.0.0").put("expected_revision", "1")
                        .put("reason", "SIMULATION 撤销岗位传播").put("approval_attested", true);
                    String revokeContext = context(owner, base), revokeKey = UUID.randomUUID().toString();
                    var revokedResponse = post(owner, base, revokePath, revokeContext, revokeKey, revokeBody); var revoked = success(revokedResponse, 200);
                    assertEquals(2, revoked.required("revoked_grants").size()); assertEquals(1, revoked.required("terminated_invitations").size());
                    assertEquals(pending.required("invitation_id").textValue(), revoked.required("terminated_invitations").get(0).textValue());
                    assertEquals(JSON.readTree(revokedResponse.body()), JSON.readTree(post(owner, base, revokePath, revokeContext, revokeKey, revokeBody).body()));
                    assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM permission_grant WHERE tenant_id=? AND grant_id=?", String.class, data.tenant, independent));
                    var afterRoleRevoke = governance(owner, base, "/api/permission-grants?subject_id=" + acceptedUser, null);
                    assertEquals("ACTIVE", listed(afterRoleRevoke, "grant_id", independent).required("status").textValue());
                    for (var id : accepted.required("grants")) {
                        var storedGrant = listed(afterRoleRevoke, "grant_id", id.textValue());
                        assertEquals("REVOKED", storedGrant.required("status").textValue());
                        assertEquals("2", storedGrant.required("revision").textValue());
                    }
                    assertEquals("REVOKED", listed(governance(owner, base, "/api/member-invitations?subject_id=" + pendingUser, null),
                        "invitation_id", pending.required("invitation_id").textValue()).required("status").textValue());
                    assertEquals(409, get(acceptedHttp, base, "/api/tasks/" + data.first, staleContext).statusCode());
                    select(acceptedHttp, base, data.tenant);
                    assertEquals(403, get(acceptedHttp, base, "/api/tasks/" + data.first, context(acceptedHttp, base)).statusCode());
                    assertEquals(200, get(acceptedHttp, base, "/api/projects/" + data.project, context(acceptedHttp, base)).statusCode());
                    assertEquals(409, post(acceptedHttp, base, acceptPath, null, acceptanceKey, acceptBody).statusCode());
                    assertEquals(403, post(pendingHttp, base, "/api/member-invitations/" + pending.required("invitation_id").textValue() + "/acceptances",
                        null, UUID.randomUUID().toString(), accept(pending)).statusCode());
                    var removal = permission(data, acceptedUser, "REVOKE", independent, "1");
                    String removalContext = context(owner, base), removalKey = UUID.randomUUID().toString();
                    var removed = post(owner, base, "/api/permission-decisions", removalContext, removalKey, removal);
                    assertEquals("REVOKED", success(removed, 200).required("grant").required("status").textValue());
                    assertEquals(JSON.readTree(removed.body()), JSON.readTree(post(owner, base, "/api/permission-decisions", removalContext, removalKey, removal).body()));
                    assertEquals(409, post(owner, base, "/api/permission-decisions", context(owner, base), UUID.randomUUID().toString(), removal).statusCode());
                    select(acceptedHttp, base, data.tenant);
                    assertEquals(403, get(acceptedHttp, base, "/api/projects/" + data.project, context(acceptedHttp, base)).statusCode());
                    success(post(owner, base, "/api/permission-decisions", context(owner, base), UUID.randomUUID().toString(), grantBody), 200);
                    String memberId = accepted.required("member_id").textValue();
                    var disableBody = disable(data, acceptedUser, memberId, "2");
                    String disableContext = context(owner, base), disableKey = UUID.randomUUID().toString();
                    var disabledResponse = post(owner, base, "/api/permission-decisions", disableContext, disableKey, disableBody);
                    var disabled = success(disabledResponse, 200);
                    assertEquals("DISABLED", disabled.required("member").required("status").textValue());
                    assertEquals("1", disabled.required("revoked_grants").textValue());
                    var disabledRead = listed(governance(owner, base, "/api/enterprise-members?subject_id=" + acceptedUser, null), "member_id", memberId);
                    assertEquals("DISABLED", disabledRead.required("status").textValue()); assertEquals("3", disabledRead.required("revision").textValue());
                    assertEquals(JSON.readTree(disabledResponse.body()), JSON.readTree(post(owner, base, "/api/permission-decisions", disableContext, disableKey, disableBody).body()));
                    assertEquals(403, get(acceptedHttp, base, "/api/session", null).statusCode());
                    var renewedPending = success(post(owner, base, "/api/member-invitations", context(owner, base), UUID.randomUUID().toString(), invite(data, pendingUser, newerRole)), 201);
                    String pendingMember = jdbc.queryForObject("SELECT member_id FROM tenant_member WHERE tenant_id=? AND user_id=?", String.class, data.tenant, pendingUser);
                    var disabledPending = success(post(owner, base, "/api/permission-decisions", context(owner, base), UUID.randomUUID().toString(), disable(data, pendingUser, pendingMember, "1")), 200);
                    assertEquals("1", disabledPending.required("terminated_invitations").textValue());
                    assertEquals(403, post(pendingHttp, base, "/api/member-invitations/" + renewedPending.required("invitation_id").textValue() + "/acceptances",
                        null, UUID.randomUUID().toString(), accept(renewedPending)).statusCode());
                    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='ID-07'", Integer.class, data.tenant));
                    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='ID-08'", Integer.class, data.tenant));
                    assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='ID-09'", Integer.class, data.tenant));
                    assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action IN ('invitation.created','invitation.accepted','permission.grant','permission.revoke','member.disable')", Integer.class, data.tenant));
                    assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type IN ('invitation.created','invitation.accepted','permission.changed','member.disabled')", Integer.class, data.tenant));
                    data.addMembers(100);
                    var capacityFailure = get(owner, base, "/api/enterprise-members", context(owner, base));
                    assertEquals(422, capacityFailure.statusCode());
                    assertTrue(new String(capacityFailure.body(), StandardCharsets.UTF_8).contains("CONTEXT_LIMIT"));
                    assertEquals(1, governance(owner, base, "/api/enterprise-members?subject_id=" + acceptedUser, null).required("items").size());
                    evidence.put("formal_accounts_created", 3); evidence.put("invitations_created", 4); evidence.put("invitations_accepted", 2);
                    evidence.put("permission_decisions", 5); evidence.put("role_revoked_grants", 2); evidence.put("role_terminated_invitations", 1);
                    evidence.put("member_terminated_invitations", 1); evidence.put("consumer_authority_verified", true);
                    evidence.put("original_replay_and_current_revocation_verified", true); evidence.put("encrypted_credential_storage_verified", true);
                    evidence.put("current_governance_read_verified", true); evidence.put("authorized_pagination_and_watermark_verified", true);
                    evidence.put("project_grant_and_member_scope_filter_verified", true); evidence.put("read_capacity_fail_fast_verified", true);
                }
            }
            if (Boolean.getBoolean("medical.memberSimulationBrowser")) {
                try (var accounts = new SimulationAccountData(source, actor);
                     var data = new SimulationTaskData(source, root, actor, application.getBean(PermissionSchema.class), application.getBean(TaskInputSchema.class))) {
                    data.enableRoleAdministration(); data.enableMemberAdministration(); select(owner, base, data.tenant);
                    var published = success(post(owner, base, "/api/enterprise-roles/" + data.role + "/versions", context(owner, base),
                        UUID.randomUUID().toString(), role(data, data.role, false, "0")), 201).required("role");
                    var input = JSON.createObjectNode().put("tenant", data.tenant).put("project", data.project).put("task", data.first);
                    input.set("owner", secret(root, "owner-credentials")); input.put("owner_id", actor);
                    var people = input.putArray("accounts");
                    for (int index = 0; index < 2; index++) {
                        var person = account(owner, base, accounts);
                        var created = success(post(owner, base, "/api/member-invitations", context(owner, base), UUID.randomUUID().toString(),
                            invite(data, person.required("user_id").textValue(), published)), 201);
                        var item = people.addObject(); item.set("account", person); item.set("invitation", created);
                        var additional = account(owner, base, accounts);
                        item.set("new_account", additional);
                        item.set("invitation_request", invite(data, additional.required("user_id").textValue(), published));
                        item.set("grant_request", permission(data, person.required("user_id").textValue(), "GRANT", null, "0"));
                    }
                    Path output = Path.of(System.getProperty("medical.memberSimulationEvidence")).getParent();
                    var browser = new ProcessBuilder("/usr/local/bin/node", root.resolve("tests/browser/member-acceptance-checks.js").toString(),
                        output.toString(), base, System.getProperty("medical.memberSimulationFrontend"))
                        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("member-browser-output.log").toFile()).start();
                    try {
                        // 账户和邀请秘密只通过进程输入传递，不保存到检查文件。
                        try (var channel = browser.getOutputStream()) { channel.write(JSON.writeValueAsBytes(input)); }
                        assertTrue(browser.waitFor(360, java.util.concurrent.TimeUnit.SECONDS), "成员浏览器检查必须完成");
                        assertEquals(0, browser.exitValue(), "成员浏览器检查未通过，读取无秘密的独立输出");
                    } finally { if (browser.isAlive()) browser.destroyForcibly(); }
                    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='ID-07'", Integer.class, data.tenant));
                    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='ID-08'", Integer.class, data.tenant));
                    assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM operation_record WHERE tenant_id=? AND operation_name='ID-09'", Integer.class, data.tenant));
                    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action='invitation.accepted'", Integer.class, data.tenant));
                    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type='invitation.accepted'", Integer.class, data.tenant));
                    assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE tenant_id=? AND action IN ('invitation.created','permission.grant','permission.revoke','member.disable')", Integer.class, data.tenant));
                    assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE tenant_id=? AND event_type IN ('invitation.created','permission.changed','member.disabled')", Integer.class, data.tenant));
                    evidence.put("simulation_browser_verified", true);
                    evidence.put("manager_write_browser_verified", true);
                }
            }
            assertEquals(before, counts(jdbc), "生成账户、岗位、邀请和企业内容必须清理，原记录数量保持一致");
            assertEquals(200, get(owner, base, "/auth/account", null).statusCode());
            evidence.put("generated_data_cleaned", true); evidence.put("actual_http_port", application.getWebServer().getPort());
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        evidence.put("data_kind", "SIMULATION"); evidence.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.memberSimulationEvidence")), JSON.writeValueAsBytes(evidence));
    }

    private static ObjectNode role(SimulationTaskData data, String id, boolean empty, String expected) throws Exception {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("name", "SIMULATION 成员岗位")
            .put("valid_from", "2026-10-07T01:00:00Z").putNull("valid_to").put("reason", "SIMULATION 成员授权验证")
            .put("approval_attested", true).put("expected_head_revision", expected);
        var definition = value.putObject("definition").put("schema_version", "1.0.0").put("role_id", id);
        var actions = definition.putArray("actions"); if (!empty) { actions.add("project.read"); actions.add("task.read"); }
        definition.set("condition", condition()); value.set("approval_basis", data.projectBasis()); return value;
    }
    private static JsonNode governance(HttpClient http, String base, String path, String revision) throws Exception {
        String current = revision == null ? context(http, base) : revision;
        var result = success(get(http, base, path, current), 200);
        assertEquals("1.0.0", result.required("schema_version").textValue());
        assertEquals(current, result.required("current_context").required("context_revision").textValue());
        assertEquals(result.required("tenant_id"), result.required("current_context").required("tenant_id"));
        assertTrue(result.required("dataset_watermark").textValue().matches("[0-9a-f]{64}"));
        return result;
    }
    private static JsonNode listed(JsonNode page, String field, String id) {
        var matches = new java.util.ArrayList<JsonNode>();
        for (var item : page.required("items")) if (id.equals(item.required(field).textValue())) matches.add(item);
        assertEquals(1, matches.size(), "当前管理读取必须返回唯一指定对象"); return matches.get(0);
    }
    private static ObjectNode condition() {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").putNull("purposes");
        value.putArray("identity_modes").add("local"); var delegation = value.putObject("delegation");
        for (String name : List.of("actions", "role_ids", "resource_kinds", "project_ids", "resource_ids")) delegation.putArray(name);
        return value;
    }
    private static ObjectNode invite(SimulationTaskData data, String user, JsonNode role) throws Exception {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("expires_at", Instant.now().plusSeconds(172800).truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString());
        value.putObject("identity_binding").put("mode", "local").put("user_id", user).putNull("issuer").putNull("subject");
        value.putArray("roles").addObject().put("role_id", role.required("role_id").textValue()).put("version_id", role.required("version_id").textValue())
            .put("content_sha256", role.required("content_sha256").textValue());
        value.set("scope", scope(data)); value.set("basis", data.projectBasis()); return value;
    }
    private static ObjectNode scope(SimulationTaskData data) {
        return JSON.createObjectNode().put("tenant_id", data.tenant).put("project_id", data.project).put("resource_kind", "PROJECT").put("resource_id", data.project);
    }
    private static ObjectNode permission(SimulationTaskData data, String target, String decision, String grant, String expected) throws Exception {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("decision", decision).put("subject_id", target)
            .put("action", "project.read").put("expected_revision", expected).put("reason", "SIMULATION 独立授权验证").putNull("valid_from").putNull("valid_to");
        if (grant == null) value.putNull("grant_id"); else value.put("grant_id", grant);
        value.set("condition", decision.equals("GRANT") ? condition() : JSON.nullNode());
        value.set("scope", scope(data)); value.set("basis", data.projectBasis()); return value;
    }
    private static ObjectNode disable(SimulationTaskData data, String user, String member, String expected) throws Exception {
        var value = permission(data, user, "DISABLE_MEMBER", null, expected).putNull("action").putNull("condition");
        value.putObject("scope").put("tenant_id", data.tenant).putNull("project_id").put("resource_kind", "MEMBER").put("resource_id", member); return value;
    }
    private static ObjectNode accept(JsonNode invitation) {
        return JSON.createObjectNode().put("schema_version", "1.0.0").put("credential", invitation.required("credential").textValue()).put("expected_revision", "1");
    }
    private static ObjectNode account(HttpClient http, String base, SimulationAccountData data) throws Exception {
        String username = data.nextUsername(), password = InvitationCredential.issue().secret();
        var value = JSON.createObjectNode().put("username", username).put("display_name", "SIMULATION 成员")
            .put("password", password).put("platform_role", "USER");
        var created = success(post(http, base, "/api/local-accounts", null, UUID.randomUUID().toString(), value), 201).required("account");
        return JSON.createObjectNode().put("username", username).put("password", password).put("user_id", created.required("user_id").textValue());
    }
    private static HttpClient login(String base, JsonNode account) throws Exception {
        var http = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)).followRedirects(HttpClient.Redirect.NEVER).build();
        var csrf = JSON.readTree(get(http, base, "/auth/csrf", null).body());
        String body = "username=" + URLEncoder.encode(account.required("username").textValue(), StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(account.required("password").textValue(), StandardCharsets.UTF_8);
        assertEquals(302, http.send(HttpRequest.newBuilder(URI.create(base + "/auth/login")).header("Origin", ORIGIN)
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray()).statusCode()); return http;
    }
    private static void select(HttpClient http, String base, String tenant) throws Exception {
        var options = success(get(http, base, "/api/session/tenant-selection-options", null), 200);
        String revision = options.required("expected_context_revision").textValue();
        success(post(http, base, "/api/session/tenant-selections", revision, UUID.randomUUID().toString(),
            JSON.createObjectNode().put("tenant_id", tenant).put("expected_context_revision", revision)), 200);
    }
    private static String context(HttpClient http, String base) throws Exception {
        return success(get(http, base, "/api/session", null), 200).required("current_context").required("context_revision").textValue();
    }
    private static HttpResponse<byte[]> get(HttpClient http, String base, String path, String context) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("X-Schema-Version", "1.0.0");
        if (context != null) request.header("X-Context-Revision", context);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static HttpResponse<byte[]> post(HttpClient http, String base, String path, String context, String key, JsonNode body) throws Exception {
        var csrf = JSON.readTree(get(http, base, "/auth/csrf", null).body());
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Origin", ORIGIN).header("X-Schema-Version", "1.0.0")
            .header("Idempotency-Key", key).header("Content-Type", "application/json")
            .header(csrf.required("header_name").textValue(), csrf.required("token").textValue());
        if (context != null) request.header("X-Context-Revision", context);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static JsonNode success(HttpResponse<byte[]> response, int expected) throws Exception {
        // 失败消息只包含公开状态，邀请凭据与账户密码不进入检查输出。
        assertEquals(expected, response.statusCode()); var envelope = JSON.readTree(response.body());
        assertEquals(4, envelope.size()); assertEquals("1.0.0", envelope.required("schema_version").textValue());
        assertEquals("1.0.0", response.headers().firstValue("X-Schema-Version").orElseThrow());
        assertNotNull(envelope.required("resource_revision"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(envelope.required("request_id").textValue(), response.headers().firstValue("X-Request-Id").orElseThrow());
        return envelope.required("data");
    }
    private static JsonNode secret(Path root, String name) throws Exception {
        return JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/" + name + ".json")));
    }
    private static JsonNode counts(JdbcTemplate jdbc) {
        var value = JSON.createObjectNode();
        for (String table : List.of("tenant", "project", "tenant_member", "identity_binding", "permission_grant", "member_invitation", "member_role_grant",
                "platform_member_route", "platform_invitation_route", "enterprise_role_version", "platform_local_identity_metadata", "sys_user", "sys_role", "sys_menu", "sys_user_role", "sys_role_menu"))
            value.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        return value;
    }
}
