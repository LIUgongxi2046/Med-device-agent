package com.ruoyi.medical.runtime;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.medical.identity.FrameworkAccountOperations;
import com.ruoyi.medical.identity.LocalAccountProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.service.ISysPostService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.web.controller.system.SysProfileController;
import com.ruoyi.web.controller.system.SysUserController;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

@Component
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public final class RuoYiAccountOperations implements FrameworkAccountOperations {
    private final SysUserController users;
    private final SysProfileController profiles;
    private final ISysUserService directory;
    private final ISysPostService posts;
    private final jakarta.validation.Validator validation;
    private final PermissionService permissions;

    public RuoYiAccountOperations(SysUserController users, ISysUserService directory, ISysPostService posts,
            AutowireCapableBeanFactory beans, jakarta.validation.Validator validation, PermissionService permissions) {
        this.users = users; this.directory = directory; this.posts = posts;
        this.validation = validation;
        this.permissions = permissions;
        // 原个人信息组件由Spring注入和代理，业务扩展入口保持当前版本、幂等及会话要求。
        profiles = beans.createBean(SysProfileController.class);
    }

    @Override public void requirePermission(LocalAccountProtocol.Action action) {
        // 医疗动作绑定原方法权限，原PermissionService在重放与状态判断以前核验当前身份。
        String permission = switch (action) {
            case CREATE -> "system:user:add";
            case STATE, PROFILE -> "system:user:edit";
            case RESET -> "system:user:resetPwd";
            case CREDENTIAL, OWN_PROFILE -> null;
        };
        if (permission != null && !permissions.hasPermi(permission)) throw new AccessDeniedException("ACCESS_DENIED");
    }

    @Override public AjaxResult create(SysUser user) { validate(user); return users.add(user); }
    @Override public AjaxResult changeStatus(SysUser user) { return users.changeStatus(user); }
    @Override public AjaxResult resetPassword(SysUser user) { return users.resetPwd(user); }

    @Override public AjaxResult changeProfile(long userId, String displayName) {
        var user = Objects.requireNonNull(directory.selectUserById(userId), "RUOYI_ACCOUNT_REQUIRED");
        // 原用户编辑包含角色和岗位更新，提交当前真实关联，保留已有授权与资料。
        user.setRoleIds(Objects.requireNonNull(user.getRoles()).stream().map(SysRole::getRoleId).toArray(Long[]::new));
        user.setPostIds(posts.selectPostListByUserId(userId).toArray(Long[]::new));
        user.setPassword(null); user.setNickName(displayName);
        validate(user);
        return users.edit(user);
    }

    @Override public AjaxResult changeOwnPassword(String currentPassword, String password) {
        return profiles.updatePwd(Map.of("oldPassword", currentPassword, "newPassword", password));
    }

    @Override public AjaxResult readOwnProfile() {
        refreshOwnUser();
        return profiles.profile();
    }

    @Override public AjaxResult changeOwnProfile(String nickName, String email, String phonenumber, String sex) {
        var current = refreshOwnUser();
        var input = new SysUser(); input.setUserName(current.getUserName());
        input.setNickName(nickName); input.setEmail(email); input.setPhonenumber(phonenumber); input.setSex(sex);
        validate(input);
        return profiles.updateProfile(input);
    }

    private SysUser refreshOwnUser() {
        var current = Objects.requireNonNull(directory.selectUserById(SecurityUtils.getUserId()), "RUOYI_ACCOUNT_REQUIRED");
        SecurityUtils.getLoginUser().setUser(current);
        return current;
    }

    private void validate(SysUser user) {
        if (!validation.validate(user).isEmpty()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
}
