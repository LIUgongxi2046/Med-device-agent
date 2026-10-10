package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.system.service.ISysMenuService;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public final class FrameworkNavigation {
    private final ApplicationSessionIdentity identities;
    private final ISysMenuService menus;
    private final SysPermissionService permissions;
    private final TransactionTemplate transaction;
    private final JsonMapper json = JsonMapper.builder().build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;

    public FrameworkNavigation(ApplicationSessionIdentity identities, ISysMenuService menus, SysPermissionService permissions,
            @Qualifier("masterDataSource") DataSource master) throws IOException, NoSuchAlgorithmException {
        this.identities = identities; this.menus = menus; this.permissions = permissions;
        transaction = new TransactionTemplate(new DataSourceTransactionManager(master));
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("framework-navigation.schema.json"))) {
            schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(input),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators(); shared = SchemaCatalogue.fromClasspath();
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject) {
        return Objects.requireNonNull(transaction.execute(status -> {
            // 当前会话重新读取原角色和菜单权限，导航沿用原服务的完整结果。
            identities.lockCurrent(subject);
            var principal = SecurityUtils.getLoginUser(); var user = Objects.requireNonNull(principal.getUser());
            long id = Objects.requireNonNull(principal.getUserId());
            if (id <= 0 || !user.getUserId().equals(id)) throw new IllegalStateException("FRAMEWORK_IDENTITY_CHANGED");
            WireValues.uuid(subject.user().user_id());
            var value = json.createObjectNode(); value.put("schema_version", "1.0.0"); value.put("framework_revision", "ruoyi-3.9.2");
            value.put("user_id", subject.user().user_id()); value.put("framework_user_id", Long.toString(id));
            value.set("roles", json.valueToTree(permissions.getRolePermission(user).stream().sorted().toList()));
            value.set("permissions", json.valueToTree(permissions.getMenuPermission(user).stream().sorted().toList()));
            value.set("routers", json.valueToTree(menus.buildMenus(menus.selectMenuTreeByUserId(id))));
            if (!schema.validate(value).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            var result = json.createObjectNode(); result.put("schema_version", "1.0.0"); result.put("request_id", UUID.randomUUID().toString());
            result.putNull("resource_revision"); result.set("data", value); shared.validate("interfaces", result, "Envelope");
            return result;
        }));
    }
}
