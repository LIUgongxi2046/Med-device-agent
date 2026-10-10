package com.ruoyi.medical.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class SimulationAccountData implements AutoCloseable {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final String roleKey = "simulation_member_" + UUID.randomUUID();
    private final List<Long> menuIds = new ArrayList<>();
    private final List<String> usernames = new ArrayList<>();
    private final long roleId;

    public SimulationAccountData(javax.sql.DataSource source, String owner) {
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        roleId = transaction.execute(status -> {
            // 本次明确建立原框架权限记录；后续账户创建使用正式 LOCAL-02。
            long role = Math.max(100L, Math.addExact(jdbc.queryForObject("SELECT COALESCE(MAX(role_id),0) FROM sys_role", Long.class), 1L));
            jdbc.update("INSERT INTO sys_role (role_id,role_name,role_key,role_sort,data_scope,status,create_by,create_time) VALUES (?,?,?,0,'1','0',?,UTC_TIMESTAMP())",
                role, "SIMULATION 成员检查", roleKey, owner);
            for (String permission : List.of("system:user:add")) {
                String name = "SIMULATION " + UUID.randomUUID().toString().substring(0, 8);
                jdbc.update("INSERT INTO sys_menu (menu_name,menu_type,status,perms,create_by,create_time) VALUES (?,'F','0',?,?,UTC_TIMESTAMP())",
                    name, permission, owner);
                long menu = jdbc.queryForObject("SELECT menu_id FROM sys_menu WHERE menu_name=?", Long.class, name);
                menuIds.add(menu);
                jdbc.update("INSERT INTO sys_role_menu (role_id,menu_id) VALUES (?,?)", role, menu);
            }
            long user = jdbc.queryForObject("SELECT ruoyi_user_id FROM platform_local_identity_metadata WHERE user_id=?", Long.class, owner);
            jdbc.update("INSERT INTO sys_user_role (user_id,role_id) VALUES (?,?)", user, role);
            return role;
        });
    }

    public String nextUsername() {
        String username = "sim" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        usernames.add(username);
        return username;
    }

    @Override public void close() {
        transaction.executeWithoutResult(status -> {
            // 清理范围只包含本检查登记的随机账户与权限记录。
            for (String username : usernames) {
                for (var account : jdbc.queryForList("SELECT user_id,ruoyi_user_id FROM platform_local_account WHERE username=?", username)) {
                    Object id = account.get("user_id"), nativeId = account.get("ruoyi_user_id");
                    jdbc.update("DELETE FROM platform_session_context WHERE user_id=?", id);
                    jdbc.update("DELETE FROM platform_local_session WHERE user_id=?", id);
                    jdbc.update("DELETE FROM platform_local_audit WHERE target_id=?", id);
                    jdbc.update("DELETE FROM platform_local_operation WHERE target_id=?", id);
                    jdbc.update("DELETE FROM platform_local_identity_metadata WHERE user_id=?", id);
                    jdbc.update("DELETE FROM sys_user_post WHERE user_id=?", nativeId);
                    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", nativeId);
                    jdbc.update("DELETE FROM sys_user WHERE user_id=?", nativeId);
                }
            }
            jdbc.update("DELETE FROM sys_user_role WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM sys_role_menu WHERE role_id=?", roleId);
            jdbc.update("DELETE FROM sys_role WHERE role_id=? AND role_key=?", roleId, roleKey);
            for (long menu : menuIds) jdbc.update("DELETE FROM sys_menu WHERE menu_id=?", menu);
        });
    }
}
