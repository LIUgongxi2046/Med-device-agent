package com.ruoyi.medical.identity;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;

public interface FrameworkAccountOperations {
    void requirePermission(LocalAccountProtocol.Action action);
    AjaxResult create(SysUser user);
    AjaxResult changeStatus(SysUser user);
    AjaxResult changeProfile(long userId, String displayName);
    AjaxResult resetPassword(SysUser user);
    AjaxResult changeOwnPassword(String currentPassword, String password);
    AjaxResult readOwnProfile();
    AjaxResult changeOwnProfile(String nickName, String email, String phonenumber, String sex);
}
